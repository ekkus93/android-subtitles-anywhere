#include "audio_queue.h"

#include <string.h>

void sc_audio_queue_reset(sc_audio_queue_t *queue)
{
    if (queue != NULL) {
        memset(queue, 0, sizeof(*queue));
    }
}

bool sc_audio_queue_push(sc_audio_queue_t *queue, const uint8_t *data, size_t length,
                         uint32_t timestamp_ms)
{
    if (queue == NULL) {
        return false;
    }

    queue->metrics.callbacks++;
    queue->metrics.callback_bytes += length;
    if (queue->last_callback_ms != 0U) {
        const uint32_t interval = timestamp_ms - queue->last_callback_ms;
        queue->metrics.last_callback_interval_ms = interval;
        if (interval > queue->metrics.max_callback_interval_ms) {
            queue->metrics.max_callback_interval_ms = interval;
        }
    }
    queue->last_callback_ms = timestamp_ms;

    if ((data == NULL) || (length == 0U) || (length > SC_AUDIO_QUEUE_SLOT_BYTES) ||
        (queue->count == SC_AUDIO_QUEUE_SLOT_COUNT)) {
        queue->metrics.dropped_blocks++;
        queue->metrics.dropped_bytes += length;
        return false;
    }

    sc_audio_block_t *block = &queue->slots[queue->tail];
    memcpy(block->data, data, length);
    block->length = length;
    block->timestamp_ms = timestamp_ms;
    queue->tail = (queue->tail + 1U) % SC_AUDIO_QUEUE_SLOT_COUNT;
    queue->count++;
    if (queue->count > queue->metrics.high_water_blocks) {
        queue->metrics.high_water_blocks = queue->count;
    }
    return true;
}

bool sc_audio_queue_pop(sc_audio_queue_t *queue, sc_audio_block_t *block)
{
    if ((queue == NULL) || (block == NULL) || (queue->count == 0U)) {
        return false;
    }
    *block = queue->slots[queue->head];
    queue->head = (queue->head + 1U) % SC_AUDIO_QUEUE_SLOT_COUNT;
    queue->count--;
    return true;
}

size_t sc_audio_queue_depth(const sc_audio_queue_t *queue)
{
    return queue == NULL ? 0U : queue->count;
}

sc_audio_metrics_t sc_audio_queue_get_metrics(const sc_audio_queue_t *queue)
{
    const sc_audio_metrics_t empty = {0};
    return queue == NULL ? empty : queue->metrics;
}
