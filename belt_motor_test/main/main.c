#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/uart.h"
#include "driver/ledc.h"

// 根据你手写的纸条，8个电机的 GPIO 引脚映射
// 电机编号对应关系：1->4, 2->13, 3->14, 4->18, 5->19, 6->21, 7->22, 8->23
int motor_pins[8] = {
    4, 13, 14, 18, 19, 21, 22, 23
};

// 初始化 8 个 PWM 通道（编号 0~7）
void init_all_motors(void) {
    ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .timer_num = LEDC_TIMER_0,
        .duty_resolution = LEDC_TIMER_8_BIT, // 强度 0~255
        .freq_hz = 1000,
        .clk_cfg = LEDC_AUTO_CLK
    };
    ledc_timer_config(&timer);

    // 0~7 号通道分别控制 8 个电机
    for (int i = 0; i < 8; i++) {
        ledc_channel_config_t channel = {
            .speed_mode = LEDC_LOW_SPEED_MODE,
            .channel = i,           // 使用 0~7 通道
            .timer_sel = LEDC_TIMER_0,
            .gpio_num = motor_pins[i],
            .duty = 0,              // 初始默认全部关闭
            .hpoint = 0
        };
        ledc_channel_config(&channel);
    }
}

// 开启指定的电机（强制关闭其他电机，保证只有一个在振）
void turn_on_motor(int index) {
    for (int i = 0; i < 8; i++) {
        if (i == index) {
            ledc_set_duty(LEDC_LOW_SPEED_MODE, i, 128); // 128为 50% 强度，可调
        } else {
            ledc_set_duty(LEDC_LOW_SPEED_MODE, i, 0);   // 其他全部关闭
        }
        ledc_update_duty(LEDC_LOW_SPEED_MODE, i);
    }
}

void app_main(void) {
    // 初始化串口（115200 波特率）
    uart_config_t uart = { .baud_rate = 115200, .data_bits = UART_DATA_8_BITS, .parity = UART_PARITY_DISABLE, .stop_bits = UART_STOP_BITS_1, .flow_ctrl = UART_HW_FLOWCTRL_DISABLE };
    uart_param_config(UART_NUM_0, &uart);
    uart_driver_install(UART_NUM_0, 1024, 0, 0, NULL, 0);

    // 初始化 8 个电机
    init_all_motors();

    // 打印提示
    printf("测试开始！输入 1~8 震动对应电机，输入 0 全部停止。\n");

    uint8_t cmd[1];
    while(1) {
        // 检测串口指令
        int len = uart_read_bytes(UART_NUM_0, cmd, 1, 20 / portTICK_PERIOD_MS);
        if(len > 0) {
            // 数字 0（ASCII码 48） - 停止所有电机
            if (cmd[0] == '0') {
                for (int i = 0; i < 8; i++) {
                    ledc_set_duty(LEDC_LOW_SPEED_MODE, i, 0);
                    ledc_update_duty(LEDC_LOW_SPEED_MODE, i);
                }
                printf("已停止所有电机。\n");
            } 
            // 数字 1 ~ 8（ASCII码 49 ~ 56） - 分别开启 1~8 号电机
            else if (cmd[0] >= '1' && cmd[0] <= '8') {
                int motor_idx = cmd[0] - '1'; // 字符转数字索引 (0~7)
                turn_on_motor(motor_idx);
                printf("正在震动第 %d 号电机\n", motor_idx + 1);
            }
        }
        vTaskDelay(10 / portTICK_PERIOD_MS);
    }
}