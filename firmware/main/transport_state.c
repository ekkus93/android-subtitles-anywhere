#include "transport_state.h"

#include <string.h>

static uint64_t get_u64(const uint8_t *p)
{
    uint64_t value = 0U;
    for (unsigned i = 0; i < 8U; ++i) {
        value |= (uint64_t)p[i] << (8U * i);
    }
    return value;
}

static void put_u32(uint8_t *p, uint32_t value)
{
    for (unsigned i = 0; i < 4U; ++i) {
        p[i] = (uint8_t)(value >> (8U * i));
    }
}

static void put_u64(uint8_t *p, uint64_t value)
{
    for (unsigned i = 0; i < 8U; ++i) {
        p[i] = (uint8_t)(value >> (8U * i));
    }
}

void sc_transport_state_reset(sc_transport_state_t *state, uint64_t boot_id)
{
    if (state != NULL) {
        memset(state, 0, sizeof(*state));
        state->boot_id = boot_id;
    }
}

bool sc_transport_enqueue(sc_transport_state_t *state, const sc_protocol_frame_t *frame)
{
    if ((state == NULL) || (frame == NULL) || (state->count == SC_TX_SLOT_COUNT)) {
        if (state != NULL) {
            state->metrics.frames_dropped++;
        }
        return false;
    }
    sc_tx_slot_t *slot = &state->slots[state->tail];
    size_t written = 0U;
    if (sc_protocol_encode(frame, slot->data, sizeof(slot->data), &written) != SC_PROTOCOL_OK) {
        state->metrics.frames_dropped++;
        return false;
    }
    slot->length = written;
    state->tail = (state->tail + 1U) % SC_TX_SLOT_COUNT;
    state->count++;
    state->metrics.frames_queued++;
    if (state->count > state->metrics.tx_high_water) {
        state->metrics.tx_high_water = state->count;
    }
    return true;
}

bool sc_transport_dequeue(sc_transport_state_t *state, sc_tx_slot_t *slot)
{
    if ((state == NULL) || (slot == NULL) || (state->count == 0U)) {
        return false;
    }
    *slot = state->slots[state->head];
    state->head = (state->head + 1U) % SC_TX_SLOT_COUNT;
    state->count--;
    state->metrics.frames_sent++;
    state->metrics.bytes_sent += slot->length;
    return true;
}

sc_protocol_result_t sc_transport_handle_control(sc_transport_state_t *state, const uint8_t *data,
                                                 size_t length, uint32_t now_ms)
{
    if (state == NULL) {
        return SC_PROTOCOL_INVALID;
    }
    sc_protocol_frame_t frame;
    const sc_protocol_result_t result = sc_protocol_decode(data, length, &frame);
    if (result != SC_PROTOCOL_OK) {
        state->metrics.rx_errors++;
        return result;
    }
    state->metrics.rx_frames++;
    switch (frame.type) {
    case SC_MSG_START_SESSION: {
        const uint64_t requested = get_u64(frame.payload);
        if ((requested == 0U) || (frame.session_id != requested)) {
            state->metrics.rx_errors++;
            return SC_PROTOCOL_INVALID;
        }
        state->active_session = requested;
        state->session_active = true;
        state->last_heartbeat_ms = now_ms;
        return SC_PROTOCOL_OK;
    }
    case SC_MSG_STOP_SESSION:
        if (!state->session_active || (frame.session_id != state->active_session)) {
            state->metrics.stale_session_frames++;
            return SC_PROTOCOL_INVALID;
        }
        state->session_active = false;
        state->active_session = 0U;
        return SC_PROTOCOL_OK;
    case SC_MSG_HEARTBEAT:
        if (!state->session_active || (frame.session_id != state->active_session)) {
            state->metrics.stale_session_frames++;
            return SC_PROTOCOL_INVALID;
        }
        state->last_heartbeat_ms = now_ms;
        return SC_PROTOCOL_OK;
    default:
        state->metrics.rx_errors++;
        return SC_PROTOCOL_INVALID;
    }
}

bool sc_transport_heartbeat_expired(sc_transport_state_t *state, uint32_t now_ms)
{
    if ((state == NULL) || !state->session_active) {
        return false;
    }
    if ((uint32_t)(now_ms - state->last_heartbeat_ms) <= SC_HEARTBEAT_TIMEOUT_MS) {
        return false;
    }
    state->session_active = false;
    state->active_session = 0U;
    return true;
}

size_t sc_transport_make_hello_payload(const sc_transport_state_t *state, uint8_t payload[20])
{
    if ((state == NULL) || (payload == NULL)) {
        return 0U;
    }
    memset(payload, 0, 20U);
    put_u64(payload, state->boot_id);
    put_u32(&payload[8], SC_UART_BAUD_RATE);
    payload[12] = SC_PROTOCOL_MAJOR;
    payload[13] = SC_PROTOCOL_MINOR;
    payload[14] = 1U; /* encoded SBC supported */
    payload[15] = 1U; /* PCM supported for experiments */
    put_u32(&payload[16], SC_PROTOCOL_MAX_PAYLOAD);
    return 20U;
}

size_t sc_transport_make_diagnostics_payload(const sc_transport_state_t *state,
                                             uint8_t payload[32])
{
    if ((state == NULL) || (payload == NULL)) {
        return 0U;
    }
    memset(payload, 0, 32U);
    put_u64(payload, state->metrics.frames_queued);
    put_u64(&payload[8], state->metrics.frames_sent);
    put_u64(&payload[16], state->metrics.frames_dropped);
    put_u32(&payload[24], (uint32_t)state->metrics.tx_high_water);
    put_u32(&payload[28], SC_UART_BAUD_RATE);
    return 32U;
}
