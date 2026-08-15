#include <assert.h>
#include <stdint.h>
#include <string.h>

#include "audio_queue.h"

static void test_fifo_and_metrics(void)
{
    sc_audio_queue_t queue;
    sc_audio_queue_reset(&queue);
    const uint8_t first[] = {1U, 2U, 3U};
    const uint8_t second[] = {4U, 5U};
    assert(sc_audio_queue_push(&queue, first, sizeof(first), 100U));
    assert(sc_audio_queue_push(&queue, second, sizeof(second), 112U));
    assert(sc_audio_queue_depth(&queue) == 2U);

    const sc_audio_metrics_t metrics = sc_audio_queue_get_metrics(&queue);
    assert(metrics.callbacks == 2U);
    assert(metrics.callback_bytes == 5U);
    assert(metrics.last_callback_interval_ms == 12U);
    assert(metrics.max_callback_interval_ms == 12U);
    assert(metrics.high_water_blocks == 2U);

    sc_audio_block_t block;
    assert(sc_audio_queue_pop(&queue, &block));
    assert(block.length == sizeof(first));
    assert(block.timestamp_ms == 100U);
    assert(memcmp(block.data, first, sizeof(first)) == 0);
    assert(sc_audio_queue_pop(&queue, &block));
    assert(memcmp(block.data, second, sizeof(second)) == 0);
    assert(!sc_audio_queue_pop(&queue, &block));
}

static void test_full_queue_drops_without_overwrite(void)
{
    sc_audio_queue_t queue;
    sc_audio_queue_reset(&queue);
    const uint8_t byte = 0x5aU;
    for (size_t index = 0; index < SC_AUDIO_QUEUE_SLOT_COUNT; ++index) {
        assert(sc_audio_queue_push(&queue, &byte, 1U, (uint32_t)(index + 1U)));
    }
    assert(!sc_audio_queue_push(&queue, &byte, 1U, 99U));
    assert(sc_audio_queue_depth(&queue) == SC_AUDIO_QUEUE_SLOT_COUNT);
    const sc_audio_metrics_t metrics = sc_audio_queue_get_metrics(&queue);
    assert(metrics.dropped_blocks == 1U);
    assert(metrics.dropped_bytes == 1U);
    assert(metrics.high_water_blocks == SC_AUDIO_QUEUE_SLOT_COUNT);
}

static void test_invalid_blocks_are_bounded_drops(void)
{
    sc_audio_queue_t queue;
    sc_audio_queue_reset(&queue);
    uint8_t oversized[SC_AUDIO_QUEUE_SLOT_BYTES + 1U] = {0};
    assert(!sc_audio_queue_push(&queue, NULL, 4U, 1U));
    assert(!sc_audio_queue_push(&queue, oversized, sizeof(oversized), 2U));
    assert(sc_audio_queue_depth(&queue) == 0U);
    const sc_audio_metrics_t metrics = sc_audio_queue_get_metrics(&queue);
    assert(metrics.dropped_blocks == 2U);
    assert(metrics.dropped_bytes == 4U + sizeof(oversized));
}

int main(void)
{
    test_fifo_and_metrics();
    test_full_queue_drops_without_overwrite();
    test_invalid_blocks_are_bounded_drops();
    return 0;
}
