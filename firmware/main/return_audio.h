#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
    SC_RETURN_AUDIO_SBC = 1,
    SC_RETURN_AUDIO_PCM_S16LE = 2,
} sc_return_audio_kind_t;

typedef struct {
    sc_return_audio_kind_t kind;
    const uint8_t *data;
    size_t length;
    uint32_t timestamp_ms;
    uint16_t frame_count;
    uint32_t sample_rate_hz;
    uint8_t channels;
} sc_return_audio_block_t;

typedef struct {
    uint32_t baud;
    uint8_t wire_bits_per_byte;
    uint32_t payload_bytes_per_second;
} sc_uart_budget_t;

sc_uart_budget_t sc_uart_8n1_budget(uint32_t baud);
uint64_t sc_pcm_bytes_per_second(uint32_t sample_rate_hz, uint8_t channels,
                                 uint8_t bytes_per_sample);
bool sc_stream_fits_uart(uint64_t payload_bytes_per_second, sc_uart_budget_t budget,
                         uint8_t utilization_percent);
