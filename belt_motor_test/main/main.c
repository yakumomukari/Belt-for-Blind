#include <stdio.h>
#include <stdbool.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "driver/ledc.h"
#include "driver/uart.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "nvs_flash.h"
#include "os/os_mbuf.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#define MOTOR_COUNT 8
#define MOTOR_DUTY 128
#define MOTOR_VECTOR_COMMAND_HEADER 0xA1
#define MOTOR_VECTOR_COMMAND_SIZE (MOTOR_COUNT + 1)
#define MOTOR_COMMAND_TIMEOUT_MS 700
#define MOTOR_WATCHDOG_POLL_MS 50
#define UART_PORT UART_NUM_0
#define GPS_UART_PORT UART_NUM_2
#define GPS_UART_BAUD 9600
#define GPS_RX_GPIO 16
#define GPS_TX_GPIO 17
#define GPS_PACKET_SIZE 18

static const char *TAG = "belt_motor";
static const char *DEVICE_NAME = "BeltMotor";

// Canonical UUID: 8f8a0001-8f4b-4f5b-9f2b-5e7a1f000001
static const ble_uuid128_t MOTOR_SERVICE_UUID = BLE_UUID128_INIT(
    0x01, 0x00, 0x00, 0x1f, 0x7a, 0x5e, 0x2b, 0x9f,
    0x5b, 0x4f, 0x4b, 0x8f, 0x01, 0x00, 0x8a, 0x8f);

// Canonical UUID: 8f8a0002-8f4b-4f5b-9f2b-5e7a1f000001
static const ble_uuid128_t MOTOR_COMMAND_UUID = BLE_UUID128_INIT(
    0x01, 0x00, 0x00, 0x1f, 0x7a, 0x5e, 0x2b, 0x9f,
    0x5b, 0x4f, 0x4b, 0x8f, 0x02, 0x00, 0x8a, 0x8f);

// Canonical UUID: 8f8a0003-8f4b-4f5b-9f2b-5e7a1f000001
static const ble_uuid128_t GPS_DATA_UUID = BLE_UUID128_INIT(
    0x01, 0x00, 0x00, 0x1f, 0x7a, 0x5e, 0x2b, 0x9f,
    0x5b, 0x4f, 0x4b, 0x8f, 0x03, 0x00, 0x8a, 0x8f);

static const int MOTOR_PINS[MOTOR_COUNT] = {4, 13, 14, 18, 19, 21, 22, 23};
static uint8_t own_address_type;
static SemaphoreHandle_t motor_mutex;
static TickType_t last_motor_command_tick;
static bool motor_active;
static uint16_t ble_connection_handle = BLE_HS_CONN_HANDLE_NONE;
static uint16_t gps_characteristic_handle;
static uint16_t gps_sequence;
static uint16_t latest_speed_cm_per_second = UINT16_MAX;

static void start_advertising(void);

static void stop_all_motors_locked(void)
{
    for (int i = 0; i < MOTOR_COUNT; ++i) {
        ledc_set_duty(LEDC_LOW_SPEED_MODE, (ledc_channel_t)i, 0);
        ledc_update_duty(LEDC_LOW_SPEED_MODE, (ledc_channel_t)i);
    }
    motor_active = false;
}

static void stop_all_motors(void)
{
    xSemaphoreTake(motor_mutex, portMAX_DELAY);
    stop_all_motors_locked();
    xSemaphoreGive(motor_mutex);
}

static void set_motor_duties(const uint8_t duties[MOTOR_COUNT])
{
    bool any_active = false;

    xSemaphoreTake(motor_mutex, portMAX_DELAY);
    for (int i = 0; i < MOTOR_COUNT; ++i) {
        ledc_set_duty(LEDC_LOW_SPEED_MODE, (ledc_channel_t)i, duties[i]);
        ledc_update_duty(LEDC_LOW_SPEED_MODE, (ledc_channel_t)i);
        any_active = any_active || duties[i] > 0;
    }
    if (any_active) {
        last_motor_command_tick = xTaskGetTickCount();
    }
    motor_active = any_active;
    xSemaphoreGive(motor_mutex);
}

static void turn_on_motor(int index)
{
    if (index < 0 || index >= MOTOR_COUNT) {
        return;
    }

    uint8_t duties[MOTOR_COUNT] = {0};
    for (int i = 0; i < MOTOR_COUNT; ++i) {
        duties[i] = i == index ? MOTOR_DUTY : 0;
    }
    set_motor_duties(duties);
}

static void motor_watchdog_task(void *argument)
{
    (void)argument;
    const TickType_t timeout_ticks = pdMS_TO_TICKS(MOTOR_COMMAND_TIMEOUT_MS);

    while (true) {
        bool timed_out = false;
        xSemaphoreTake(motor_mutex, portMAX_DELAY);
        if (motor_active) {
            TickType_t elapsed_ticks = xTaskGetTickCount() - last_motor_command_tick;
            if (elapsed_ticks >= timeout_ticks) {
                stop_all_motors_locked();
                timed_out = true;
            }
        }
        xSemaphoreGive(motor_mutex);

        if (timed_out) {
            ESP_LOGW(TAG, "Motor command timed out; all motors stopped");
        }
        vTaskDelay(pdMS_TO_TICKS(MOTOR_WATCHDOG_POLL_MS));
    }
}

static bool handle_motor_command(uint8_t command)
{
    if (command == '0') {
        stop_all_motors();
        ESP_LOGI(TAG, "All motors stopped");
        return true;
    }
    if (command >= '1' && command <= '8') {
        int motor_index = command - '1';
        turn_on_motor(motor_index);
        ESP_LOGI(TAG, "Motor %d active", motor_index + 1);
        return true;
    }
    return false;
}

static bool handle_motor_payload(const uint8_t *payload, uint16_t length)
{
    if (length == 1) {
        return handle_motor_command(payload[0]);
    }
    if (length != MOTOR_VECTOR_COMMAND_SIZE ||
        payload[0] != MOTOR_VECTOR_COMMAND_HEADER) {
        return false;
    }

    set_motor_duties(&payload[1]);
    ESP_LOGD(
        TAG,
        "Motor vector: %u %u %u %u %u %u %u %u",
        payload[1],
        payload[2],
        payload[3],
        payload[4],
        payload[5],
        payload[6],
        payload[7],
        payload[8]);
    return true;
}

static void init_all_motors(void)
{
    ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_8_BIT,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = 1000,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    ESP_ERROR_CHECK(ledc_timer_config(&timer));

    for (int i = 0; i < MOTOR_COUNT; ++i) {
        ledc_channel_config_t channel = {
            .gpio_num = MOTOR_PINS[i],
            .speed_mode = LEDC_LOW_SPEED_MODE,
            .channel = (ledc_channel_t)i,
            .intr_type = LEDC_INTR_DISABLE,
            .timer_sel = LEDC_TIMER_0,
            .duty = 0,
            .hpoint = 0,
        };
        ESP_ERROR_CHECK(ledc_channel_config(&channel));
    }

    motor_mutex = xSemaphoreCreateMutex();
    if (motor_mutex == NULL) {
        ESP_LOGE(TAG, "Failed to create motor mutex");
        abort();
    }

    BaseType_t task_result = xTaskCreate(
        motor_watchdog_task,
        "motor_watchdog",
        2048,
        NULL,
        5,
        NULL);
    if (task_result != pdPASS) {
        ESP_LOGE(TAG, "Failed to create motor watchdog task");
        abort();
    }
}

static int motor_command_access(
    uint16_t connection_handle,
    uint16_t attribute_handle,
    struct ble_gatt_access_ctxt *context,
    void *argument)
{
    (void)connection_handle;
    (void)attribute_handle;
    (void)argument;

    if (context->op != BLE_GATT_ACCESS_OP_WRITE_CHR) {
        return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
    }
    uint16_t payload_length = OS_MBUF_PKTLEN(context->om);
    if (payload_length != 1 && payload_length != MOTOR_VECTOR_COMMAND_SIZE) {
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }

    uint8_t payload[MOTOR_VECTOR_COMMAND_SIZE] = {0};
    if (os_mbuf_copydata(context->om, 0, payload_length, payload) != 0) {
        return BLE_ATT_ERR_UNLIKELY;
    }
    return handle_motor_payload(payload, payload_length)
        ? 0
        : BLE_ATT_ERR_UNLIKELY;
}

static int gps_data_access(
    uint16_t connection_handle,
    uint16_t attribute_handle,
    struct ble_gatt_access_ctxt *context,
    void *argument)
{
    (void)connection_handle;
    (void)attribute_handle;
    (void)argument;

    return context->op == BLE_GATT_ACCESS_OP_READ_CHR
        ? BLE_ATT_ERR_READ_NOT_PERMITTED
        : BLE_ATT_ERR_WRITE_NOT_PERMITTED;
}

static int hex_value(char value)
{
    if (value >= '0' && value <= '9') {
        return value - '0';
    }
    if (value >= 'A' && value <= 'F') {
        return value - 'A' + 10;
    }
    if (value >= 'a' && value <= 'f') {
        return value - 'a' + 10;
    }
    return -1;
}

static bool has_valid_nmea_checksum(const char *line)
{
    if (line == NULL || line[0] != '$') {
        return false;
    }
    const char *asterisk = strrchr(line, '*');
    if (asterisk == NULL || asterisk[1] == '\0' || asterisk[2] == '\0') {
        return false;
    }

    uint8_t checksum = 0;
    for (const char *cursor = line + 1; cursor < asterisk; ++cursor) {
        checksum ^= (uint8_t)*cursor;
    }
    int high = hex_value(asterisk[1]);
    int low = hex_value(asterisk[2]);
    return high >= 0 && low >= 0 && checksum == (uint8_t)((high << 4) | low);
}

static int split_nmea_fields(char *line, char **fields, int maximum_fields)
{
    int count = 0;
    char *cursor = line;
    if (maximum_fields <= 0) {
        return 0;
    }
    fields[count++] = cursor;
    while (*cursor != '\0' && *cursor != '*' && count < maximum_fields) {
        if (*cursor == ',') {
            *cursor = '\0';
            fields[count++] = cursor + 1;
        }
        ++cursor;
    }
    if (*cursor == '*') {
        *cursor = '\0';
    }
    return count;
}

static bool sentence_type_is(const char *sentence, const char *type)
{
    size_t length = strlen(sentence);
    return length >= 3 && strcmp(sentence + length - 3, type) == 0;
}

static bool parse_nmea_coordinate(
    const char *value,
    const char *hemisphere,
    double *coordinate)
{
    if (value == NULL || value[0] == '\0' ||
        hemisphere == NULL || hemisphere[0] == '\0') {
        return false;
    }
    char *end = NULL;
    double raw = strtod(value, &end);
    if (end == value || !isfinite(raw)) {
        return false;
    }
    int degrees = (int)(raw / 100.0);
    double minutes = raw - degrees * 100.0;
    double result = degrees + minutes / 60.0;
    if (hemisphere[0] == 'S' || hemisphere[0] == 'W') {
        result = -result;
    }
    *coordinate = result;
    return true;
}

static void put_uint16_le(uint8_t *target, uint16_t value)
{
    target[0] = (uint8_t)(value & 0xff);
    target[1] = (uint8_t)(value >> 8);
}

static void put_int32_le(uint8_t *target, int32_t value)
{
    uint32_t bits = (uint32_t)value;
    target[0] = (uint8_t)(bits & 0xff);
    target[1] = (uint8_t)((bits >> 8) & 0xff);
    target[2] = (uint8_t)((bits >> 16) & 0xff);
    target[3] = (uint8_t)((bits >> 24) & 0xff);
}

static uint16_t scaled_uint16(double value, double scale)
{
    if (!isfinite(value) || value < 0.0) {
        return UINT16_MAX;
    }
    double scaled = value * scale;
    if (scaled >= UINT16_MAX) {
        return UINT16_MAX - 1;
    }
    return (uint16_t)lround(scaled);
}

static void notify_gps_fix(
    bool valid,
    double latitude,
    double longitude,
    double hdop,
    int satellites,
    int fix_quality)
{
    if (ble_connection_handle == BLE_HS_CONN_HANDLE_NONE ||
        gps_characteristic_handle == 0) {
        return;
    }

    uint8_t packet[GPS_PACKET_SIZE] = {0};
    packet[0] = 1;
    packet[1] = valid ? 1 : 0;
    put_uint16_le(packet + 2, gps_sequence++);
    put_int32_le(packet + 4, valid ? (int32_t)llround(latitude * 10000000.0) : 0);
    put_int32_le(packet + 8, valid ? (int32_t)llround(longitude * 10000000.0) : 0);
    put_uint16_le(packet + 12, latest_speed_cm_per_second);
    put_uint16_le(packet + 14, scaled_uint16(hdop, 100.0));
    packet[16] = (uint8_t)(satellites < 0 ? 0 : (satellites > 255 ? 255 : satellites));
    packet[17] = (uint8_t)(fix_quality < 0 ? 0 : (fix_quality > 255 ? 255 : fix_quality));

    struct os_mbuf *buffer = ble_hs_mbuf_from_flat(packet, sizeof(packet));
    if (buffer == NULL) {
        ESP_LOGW(TAG, "Unable to allocate GPS notification buffer");
        return;
    }
    int result = ble_gatts_notify_custom(
        ble_connection_handle,
        gps_characteristic_handle,
        buffer);
    if (result != 0 && result != BLE_HS_ENOTCONN) {
        ESP_LOGD(TAG, "GPS notification skipped: %d", result);
    }
}

static void parse_nmea_line(char *line)
{
    if (!has_valid_nmea_checksum(line)) {
        return;
    }

    char *fields[20] = {0};
    int field_count = split_nmea_fields(line, fields, 20);
    if (field_count < 1) {
        return;
    }

    if (sentence_type_is(fields[0], "RMC")) {
        if (field_count > 7 && fields[2][0] == 'A') {
            double speed_knots = strtod(fields[7], NULL);
            latest_speed_cm_per_second = scaled_uint16(speed_knots * 0.514444, 100.0);
        } else {
            latest_speed_cm_per_second = UINT16_MAX;
        }
        return;
    }
    if (!sentence_type_is(fields[0], "GGA") || field_count <= 8) {
        return;
    }

    int fix_quality = atoi(fields[6]);
    int satellites = atoi(fields[7]);
    double hdop = fields[8][0] == '\0' ? NAN : strtod(fields[8], NULL);
    double latitude = 0.0;
    double longitude = 0.0;
    bool valid = fix_quality > 0 &&
        parse_nmea_coordinate(fields[2], fields[3], &latitude) &&
        parse_nmea_coordinate(fields[4], fields[5], &longitude);
    notify_gps_fix(valid, latitude, longitude, hdop, satellites, fix_quality);
    if (valid) {
        ESP_LOGI(
            TAG,
            "GPS fix: %.7f, %.7f sats=%d hdop=%.2f",
            latitude,
            longitude,
            satellites,
            hdop);
    }
}

static void gps_uart_task(void *argument)
{
    (void)argument;
    char line[160];
    size_t line_length = 0;
    uint8_t value = 0;

    while (true) {
        int length = uart_read_bytes(
            GPS_UART_PORT,
            &value,
            1,
            pdMS_TO_TICKS(200));
        if (length != 1) {
            continue;
        }
        if (value == '\n') {
            if (line_length > 0) {
                line[line_length] = '\0';
                parse_nmea_line(line);
                line_length = 0;
            }
        } else if (value != '\r') {
            if (line_length < sizeof(line) - 1) {
                line[line_length++] = (char)value;
            } else {
                line_length = 0;
            }
        }
    }
}

static const struct ble_gatt_svc_def GATT_SERVICES[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &MOTOR_SERVICE_UUID.u,
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid = &MOTOR_COMMAND_UUID.u,
                .access_cb = motor_command_access,
                .flags = BLE_GATT_CHR_F_WRITE,
            },
            {
                .uuid = &GPS_DATA_UUID.u,
                .access_cb = gps_data_access,
                .val_handle = &gps_characteristic_handle,
                .flags = BLE_GATT_CHR_F_NOTIFY,
            },
            {0},
        },
    },
    {0},
};

static int gap_event(struct ble_gap_event *event, void *argument)
{
    (void)argument;

    switch (event->type) {
        case BLE_GAP_EVENT_CONNECT:
            if (event->connect.status == 0) {
                ble_connection_handle = event->connect.conn_handle;
                ESP_LOGI(TAG, "BLE client connected");
            } else {
                ESP_LOGW(TAG, "BLE connection failed: %d", event->connect.status);
                start_advertising();
            }
            return 0;

        case BLE_GAP_EVENT_DISCONNECT:
            ble_connection_handle = BLE_HS_CONN_HANDLE_NONE;
            ESP_LOGI(TAG, "BLE client disconnected");
            stop_all_motors();
            start_advertising();
            return 0;

        case BLE_GAP_EVENT_ADV_COMPLETE:
            start_advertising();
            return 0;

        default:
            return 0;
    }
}

static void start_advertising(void)
{
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = (ble_uuid128_t *)&MOTOR_SERVICE_UUID;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;

    int result = ble_gap_adv_set_fields(&fields);
    if (result != 0) {
        ESP_LOGE(TAG, "Failed to set advertising fields: %d", result);
        return;
    }

    struct ble_gap_adv_params parameters = {0};
    parameters.conn_mode = BLE_GAP_CONN_MODE_UND;
    parameters.disc_mode = BLE_GAP_DISC_MODE_GEN;
    result = ble_gap_adv_start(
        own_address_type,
        NULL,
        BLE_HS_FOREVER,
        &parameters,
        gap_event,
        NULL);
    if (result != 0) {
        ESP_LOGE(TAG, "Failed to start advertising: %d", result);
    }
}

static void on_ble_sync(void)
{
    int result = ble_hs_id_infer_auto(0, &own_address_type);
    if (result != 0) {
        ESP_LOGE(TAG, "Failed to infer BLE address type: %d", result);
        return;
    }
    start_advertising();
}

static void ble_host_task(void *argument)
{
    (void)argument;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

static void init_ble(void)
{
    esp_err_t result = nvs_flash_init();
    if (result == ESP_ERR_NVS_NO_FREE_PAGES || result == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        result = nvs_flash_init();
    }
    ESP_ERROR_CHECK(result);
    ESP_ERROR_CHECK(nimble_port_init());

    ble_svc_gap_init();
    ble_svc_gatt_init();
    ESP_ERROR_CHECK(ble_svc_gap_device_name_set(DEVICE_NAME));

    int ble_result = ble_gatts_count_cfg(GATT_SERVICES);
    if (ble_result != 0) {
        ESP_LOGE(TAG, "Failed to count GATT services: %d", ble_result);
        abort();
    }
    ble_result = ble_gatts_add_svcs(GATT_SERVICES);
    if (ble_result != 0) {
        ESP_LOGE(TAG, "Failed to add GATT services: %d", ble_result);
        abort();
    }

    ble_hs_cfg.sync_cb = on_ble_sync;
    nimble_port_freertos_init(ble_host_task);
}

static void init_uart(void)
{
    uart_config_t uart = {
        .baud_rate = 115200,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    ESP_ERROR_CHECK(uart_param_config(UART_PORT, &uart));
    ESP_ERROR_CHECK(uart_driver_install(UART_PORT, 1024, 0, 0, NULL, 0));
}

static void init_gps_uart(void)
{
    uart_config_t uart = {
        .baud_rate = GPS_UART_BAUD,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    ESP_ERROR_CHECK(uart_param_config(GPS_UART_PORT, &uart));
    ESP_ERROR_CHECK(uart_set_pin(
        GPS_UART_PORT,
        GPS_TX_GPIO,
        GPS_RX_GPIO,
        UART_PIN_NO_CHANGE,
        UART_PIN_NO_CHANGE));
    ESP_ERROR_CHECK(uart_driver_install(GPS_UART_PORT, 2048, 0, 0, NULL, 0));
    BaseType_t task_result = xTaskCreate(
        gps_uart_task,
        "gps_uart",
        4096,
        NULL,
        4,
        NULL);
    if (task_result != pdPASS) {
        ESP_LOGE(TAG, "Failed to create GPS UART task");
        abort();
    }
}

void app_main(void)
{
    init_uart();
    init_gps_uart();
    init_all_motors();
    init_ble();

    ESP_LOGI(
        TAG,
        "Ready: motors on UART0/BLE; GPS UART2 RX=%d TX=%d at %d baud; watchdog %d ms",
        GPS_RX_GPIO,
        GPS_TX_GPIO,
        GPS_UART_BAUD,
        MOTOR_COMMAND_TIMEOUT_MS);
    uint8_t command = 0;
    while (true) {
        int length = uart_read_bytes(UART_PORT, &command, 1, pdMS_TO_TICKS(20));
        if (length == 1 && !handle_motor_command(command)) {
            ESP_LOGW(TAG, "Ignored command: 0x%02x", command);
        }
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}
