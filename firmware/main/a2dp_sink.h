#pragma once

#include <stdbool.h>
#include <stdint.h>

#define SC_A2DP_DEVICE_NAME "Silent Caption"

typedef struct {
    bool connected;
    bool audio_started;
    uint32_t sample_rate_hz;
    uint8_t channels;
} sc_a2dp_status_t;

void sc_a2dp_sink_init(void);
sc_a2dp_status_t sc_a2dp_sink_status(void);
