#include <stdio.h>
#include <stdbool.h>
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
#define MOTOR_COMMAND_TIMEOUT_MS 700
#define MOTOR_WATCHDOG_POLL_MS 50
#define UART_PORT UART_NUM_0

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

static const int MOTOR_PINS[MOTOR_COUNT] = {4, 13, 14, 18, 19, 21, 22, 23};
static uint8_t own_address_type;
static SemaphoreHandle_t motor_mutex;
static TickType_t last_motor_command_tick;
static bool motor_active;

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

static void turn_on_motor(int index)
{
    if (index < 0 || index >= MOTOR_COUNT) {
        return;
    }

    xSemaphoreTake(motor_mutex, portMAX_DELAY);
    for (int i = 0; i < MOTOR_COUNT; ++i) {
        uint32_t duty = i == index ? MOTOR_DUTY : 0;
        ledc_set_duty(LEDC_LOW_SPEED_MODE, (ledc_channel_t)i, duty);
        ledc_update_duty(LEDC_LOW_SPEED_MODE, (ledc_channel_t)i);
    }
    last_motor_command_tick = xTaskGetTickCount();
    motor_active = true;
    xSemaphoreGive(motor_mutex);
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
    if (OS_MBUF_PKTLEN(context->om) != 1) {
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }

    uint8_t command = 0;
    if (os_mbuf_copydata(context->om, 0, 1, &command) != 0) {
        return BLE_ATT_ERR_UNLIKELY;
    }
    return handle_motor_command(command) ? 0 : BLE_ATT_ERR_UNLIKELY;
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
                ESP_LOGI(TAG, "BLE client connected");
            } else {
                ESP_LOGW(TAG, "BLE connection failed: %d", event->connect.status);
                start_advertising();
            }
            return 0;

        case BLE_GAP_EVENT_DISCONNECT:
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

void app_main(void)
{
    init_uart();
    init_all_motors();
    init_ble();

    ESP_LOGI(
        TAG,
        "Ready: write ASCII 1-8 over UART or BLE; write 0 to stop; watchdog %d ms",
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
