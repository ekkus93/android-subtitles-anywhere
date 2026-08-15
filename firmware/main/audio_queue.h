#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define SC_AUDIO_QUEUE_SLOT_BYTES 1024U
#define SC_AUDIO_QUEUE_SLOT_COUNT 12U

typedef struct {
    uint8_t data[SC_AUDIO_QUEUE_SLOT_BYTES];
    size_t length;
    uint32_t timestamp_ms;
} sc_audio_block_t;

typedef struct {
    uint64_t callbacks;
    uint64_t callback_bytes;
    uint64_t dropped_blocks;
    uint64_t dropped_bytes;
    uint32_t last_callback_interval_ms;
    uint32_t max_callback_interval_ms;
    size_t high_water_blocks;
} sc_audio_metrics_t;

void sc_audio_queue_init(void);
bool sc_audio_queue_try_push(const uint8_t *data, size_t length, uint32_t timestamp_ms);
bool sc_audio_queue_try_pop(sc_audio_block_t *block);
size_t sc_audio_queue_count(void);
sc_audio_metrics_t sc_audio_queue_metrics(void);
