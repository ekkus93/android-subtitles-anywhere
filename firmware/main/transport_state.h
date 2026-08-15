#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "protocol_v1.h"

#define SC_UART_BAUD_RATE 921600U
#define SC_TX_SLOT_COUNT 8U
#define SC_TX_SLOT_BYTES SC_PROTOCOL_MAX_FRAME
#define SC_HEARTBEAT_TIMEOUT_MS 5000U
#define SC_FIRMWARE_VERSION "0.1.0-dev"

typedef struct {
    uint8_t data[SC_TX_SLOT_BYTES];
    size_t length;
} sc_tx_slot_t;

typedef struct {
    uint64_t frames_queued;
    uint64_t frames_sent;
    uint64_t frames_dropped;
    uint64_t bytes_sent;
    uint64_t rx_frames;
    uint64_t rx_errors;
    uint64_t stale_session_frames;
    size_t tx_high_water;
} sc_transport_metrics_t;

typedef struct {
    sc_tx_slot_t slots[SC_TX_SLOT_COUNT];
    size_t head;
    size_t tail;
    size_t count;
    uint32_t tx_sequence;
    uint64_t boot_id;
    uint64_t active_session;
    uint32_t last_heartbeat_ms;
    bool session_active;
    sc_transport_metrics_t metrics;
} sc_transport_state_t;

void sc_transport_state_reset(sc_transport_state_t *state, uint64_t boot_id);
bool sc_transport_enqueue(sc_transport_state_t *state, const sc_protocol_frame_t *frame);
bool sc_transport_dequeue(sc_transport_state_t *state, sc_tx_slot_t *slot);
sc_protocol_result_t sc_transport_handle_control(sc_transport_state_t *state, const uint8_t *data,
                                                 size_t length, uint32_t now_ms);
bool sc_transport_heartbeat_expired(sc_transport_state_t *state, uint32_t now_ms);
size_t sc_transport_make_hello_payload(const sc_transport_state_t *state, uint8_t payload[20]);
size_t sc_transport_make_diagnostics_payload(const sc_transport_state_t *state,
                                             uint8_t payload[32]);
