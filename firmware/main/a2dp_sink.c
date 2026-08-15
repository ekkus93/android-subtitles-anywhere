#include "a2dp_sink.h"

#include <inttypes.h>
#include <string.h>

#include "audio_queue.h"
#include "esp_a2dp_api.h"
#include "esp_bt.h"
#include "esp_bt_device.h"
#include "esp_bt_main.h"
#include "esp_gap_bt_api.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "nvs_flash.h"

static const char *TAG = "sc_a2dp";
static sc_a2dp_status_t s_status;
static sc_audio_queue_t s_audio_queue;

static uint32_t now_ms(void)
{
    return (uint32_t)(esp_timer_get_time() / 1000);
}

static void audio_data_callback(const uint8_t *data, uint32_t length)
{
    if (!sc_audio_queue_push(&s_audio_queue, data, (size_t)length, now_ms())) {
        ESP_LOGW(TAG, "A2DP audio block dropped: %" PRIu32 " bytes", length);
    }
}

static void update_sbc_format(const esp_a2d_cie_sbc_t *sbc)
{
    if ((sbc->samp_freq & ESP_A2D_SBC_CIE_SF_32K) != 0U) {
        s_status.sample_rate_hz = 32000U;
    } else if ((sbc->samp_freq & ESP_A2D_SBC_CIE_SF_44K) != 0U) {
        s_status.sample_rate_hz = 44100U;
    } else if ((sbc->samp_freq & ESP_A2D_SBC_CIE_SF_48K) != 0U) {
        s_status.sample_rate_hz = 48000U;
    } else if ((sbc->samp_freq & ESP_A2D_SBC_CIE_SF_16K) != 0U) {
        s_status.sample_rate_hz = 16000U;
    } else {
        s_status.sample_rate_hz = 0U;
    }

    s_status.channels = (sbc->ch_mode == ESP_A2D_SBC_CIE_CH_MODE_MONO) ? 1U : 2U;
}

static void a2dp_event_callback(esp_a2d_cb_event_t event, esp_a2d_cb_param_t *param)
{
    switch (event) {
    case ESP_A2D_CONNECTION_STATE_EVT:
        s_status.connected = param->conn_stat.state == ESP_A2D_CONNECTION_STATE_CONNECTED;
        if (!s_status.connected) {
            s_status.audio_started = false;
        }
        ESP_LOGI(TAG, "A2DP connection state=%d", param->conn_stat.state);
        break;
    case ESP_A2D_AUDIO_STATE_EVT:
        s_status.audio_started = param->audio_stat.state == ESP_A2D_AUDIO_STATE_STARTED;
        ESP_LOGI(TAG, "A2DP audio state=%d", param->audio_stat.state);
        break;
    case ESP_A2D_AUDIO_CFG_EVT:
        if (param->audio_cfg.mcc.type == ESP_A2D_MCT_SBC) {
            update_sbc_format(&param->audio_cfg.mcc.cie.sbc_info);
            ESP_LOGI(TAG, "A2DP SBC format=%" PRIu32 " Hz, %u channels",
                     s_status.sample_rate_hz, s_status.channels);
        }
        break;
    default:
        break;
    }
}

void sc_a2dp_sink_init(void)
{
    memset(&s_status, 0, sizeof(s_status));
    sc_audio_queue_reset(&s_audio_queue);

    esp_err_t err = nvs_flash_init();
    if ((err == ESP_ERR_NVS_NO_FREE_PAGES) || (err == ESP_ERR_NVS_NEW_VERSION_FOUND)) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);

    ESP_ERROR_CHECK(esp_bt_controller_mem_release(ESP_BT_MODE_BLE));

    esp_bt_controller_config_t controller_config = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_bt_controller_init(&controller_config));
    ESP_ERROR_CHECK(esp_bt_controller_enable(ESP_BT_MODE_CLASSIC_BT));
    ESP_ERROR_CHECK(esp_bluedroid_init());
    ESP_ERROR_CHECK(esp_bluedroid_enable());

    ESP_ERROR_CHECK(esp_bt_gap_set_device_name(SC_A2DP_DEVICE_NAME));
    ESP_ERROR_CHECK(esp_a2d_register_callback(a2dp_event_callback));
    ESP_ERROR_CHECK(esp_a2d_sink_register_data_callback(audio_data_callback));
    ESP_ERROR_CHECK(esp_a2d_sink_init());
    ESP_ERROR_CHECK(esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE));

    ESP_LOGI(TAG, "silent A2DP sink ready as '%s'; no DAC/I2S playback path is configured",
             SC_A2DP_DEVICE_NAME);
}

sc_a2dp_status_t sc_a2dp_sink_status(void)
{
    return s_status;
}
