#include "audio_queue.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"

static QueueHandle_t s_queue;
static sc_audio_metrics_t s_metrics;
static uint32_t s_last_callback_ms;

void sc_audio_queue_init(void)
{
    memset(&s_metrics, 0, sizeof(s_metrics));
    s_last_callback_ms = 0;
    s_queue = xQueueCreate(SC_AUDIO_QUEUE_SLOT_COUNT, sizeof(sc_audio_block_t));
}

bool sc_audio_queue_try_push(const uint8_t *data, size_t length, uint32_t timestamp_ms)
{
    s_metrics.callbacks++;
    s_metrics.callback_bytes += length;

    if (s_last_callback_ms != 0U) {
        const uint32_t interval = timestamp_ms - s_last_callback_ms;
        s_metrics.last_callback_interval_ms = interval;
        if (interval > s_metrics.max_callback_interval_ms) {
            s_metrics.max_callback_interval_ms = interval;
        }
    }
    s_last_callback_ms = timestamp_ms;

    if ((s_queue == NULL) || (data == NULL) || (length == 0U) ||
        (length > SC_AUDIO_QUEUE_SLOT_BYTES)) {
        s_metrics.dropped_blocks++;
        s_metrics.dropped_bytes += length;
        return false;
    }

    sc_audio_block_t block = {
        .length = length,
        .timestamp_ms = timestamp_ms,
    };
    memcpy(block.data, data, length);

    if (xQueueSend(s_queue, &block, 0) != pdPASS) {
        s_metrics.dropped_blocks++;
        s_metrics.dropped_bytes += length;
        return false;
    }

    const UBaseType_t occupancy = uxQueueMessagesWaiting(s_queue);
    if ((size_t)occupancy > s_metrics.high_water_blocks) {
        s_metrics.high_water_blocks = (size_t)occupancy;
    }
    return true;
}

bool sc_audio_queue_try_pop(sc_audio_block_t *block)
{
    if ((s_queue == NULL) || (block == NULL)) {
        return false;
    }
    return xQueueReceive(s_queue, block, 0) == pdPASS;
}

size_t sc_audio_queue_count(void)
{
    if (s_queue == NULL) {
        return 0U;
    }
    return (size_t)uxQueueMessagesWaiting(s_queue);
}

sc_audio_metrics_t sc_audio_queue_metrics(void)
{
    return s_metrics;
}
