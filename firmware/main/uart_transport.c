#include "uart_transport.h"

#include <inttypes.h>

#include "driver/uart.h"
#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define SC_UART_PORT UART_NUM_0
#define SC_UART_RX_BUFFER_BYTES (SC_PROTOCOL_MAX_FRAME * 2U)
#define SC_UART_TASK_STACK 4096U

static const char *TAG = "sc_uart";
static sc_transport_state_t s_state;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000);
}

static void tx_task(void *arg)
{
    (void)arg;
    sc_tx_slot_t slot;
    for (;;) {
        if (sc_transport_dequeue(&s_state, &slot)) {
            const int written = uart_write_bytes(SC_UART_PORT, slot.data, slot.length);
            if (written < 0) {
                ESP_LOGW(TAG, "UART TX failed");
            }
        } else {
            vTaskDelay(pdMS_TO_TICKS(1));
        }
    }
}

static void rx_task(void *arg)
{
    (void)arg;
    static uint8_t buffer[SC_PROTOCOL_MAX_FRAME];
    size_t used = 0U;
    for (;;) {
        const int count = uart_read_bytes(SC_UART_PORT, &buffer[used], sizeof(buffer) - used,
                                          pdMS_TO_TICKS(20));
        if (count > 0) {
            used += (size_t)count;
        }
        while (used >= SC_PROTOCOL_HEADER_BYTES) {
            if (memcmp(buffer, SC_PROTOCOL_MAGIC, 4U) != 0) {
                memmove(buffer, &buffer[1], --used);
                continue;
            }
            const uint32_t payload_length = (uint32_t)buffer[8] | ((uint32_t)buffer[9] << 8U) |
                                            ((uint32_t)buffer[10] << 16U) |
                                            ((uint32_t)buffer[11] << 24U);
            if (payload_length > SC_PROTOCOL_MAX_PAYLOAD) {
                memmove(buffer, &buffer[1], --used);
                continue;
            }
            const size_t frame_length = SC_PROTOCOL_HEADER_BYTES + payload_length;
            if (used < frame_length) {
                break;
            }
            (void)sc_transport_handle_control(&s_state, buffer, frame_length, now_ms());
            used -= frame_length;
            memmove(buffer, &buffer[frame_length], used);
        }
        if (sc_transport_heartbeat_expired(&s_state, now_ms())) {
            ESP_LOGW(TAG, "session heartbeat expired");
        }
    }
}

void sc_uart_transport_init(void)
{
    const uint64_t boot_id = ((uint64_t)esp_random() << 32U) | esp_random();
    sc_transport_state_reset(&s_state, boot_id);

    const uart_config_t config = {
        .baud_rate = (int)SC_UART_BAUD_RATE,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    ESP_ERROR_CHECK(uart_driver_install(SC_UART_PORT, SC_UART_RX_BUFFER_BYTES,
                                        SC_PROTOCOL_MAX_FRAME, 0, NULL, 0));
    ESP_ERROR_CHECK(uart_param_config(SC_UART_PORT, &config));
    ESP_LOGI(TAG, "protocol v1 UART ready at %u baud boot=%" PRIu64, SC_UART_BAUD_RATE, boot_id);

    xTaskCreate(rx_task, "sc_uart_rx", SC_UART_TASK_STACK, NULL, 8, NULL);
    xTaskCreate(tx_task, "sc_uart_tx", SC_UART_TASK_STACK, NULL, 7, NULL);
}

sc_transport_state_t *sc_uart_transport_state(void)
{
    return &s_state;
}
