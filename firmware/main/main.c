#include "a2dp_sink.h"
#include "esp_log.h"
#include "uart_transport.h"

static const char *TAG = "silent_caption";

void app_main(void)
{
    ESP_LOGI(TAG, "Silent Caption firmware bootstrap");
    sc_uart_transport_init();
    sc_a2dp_sink_init();
}
