#include <assert.h>
#include <stdint.h>
#include <string.h>

#include "protocol_v1.h"
#include "transport_state.h"

static void put_u64(uint8_t *p, uint64_t value)
{
    for (unsigned i = 0; i < 8U; ++i) {
        p[i] = (uint8_t)(value >> (8U * i));
    }
}

static size_t encode_control(sc_message_type_t type, uint64_t session, const uint8_t *payload,
                             size_t payload_length, uint8_t *output)
{
    const sc_protocol_frame_t frame = {
        .type = type,
        .sequence = 1U,
        .session_id = session,
        .payload = payload,
        .payload_length = payload_length,
    };
    size_t written = 0U;
    assert(sc_protocol_encode(&frame, output, SC_PROTOCOL_MAX_FRAME, &written) == SC_PROTOCOL_OK);
    return written;
}

static void test_codec_integrity(void)
{
    uint8_t payload[20] = {0};
    uint8_t encoded[SC_PROTOCOL_MAX_FRAME];
    const sc_protocol_frame_t input = {
        .type = SC_MSG_HELLO,
        .sequence = 77U,
        .session_id = 9U,
        .timestamp_ms = 123U,
        .payload = payload,
        .payload_length = sizeof(payload),
    };
    size_t written = 0U;
    assert(sc_protocol_encode(&input, encoded, sizeof(encoded), &written) == SC_PROTOCOL_OK);
    sc_protocol_frame_t decoded;
    assert(sc_protocol_decode(encoded, written, &decoded) == SC_PROTOCOL_OK);
    assert(decoded.type == input.type);
    assert(decoded.sequence == 77U);
    assert(decoded.session_id == 9U);
    encoded[SC_PROTOCOL_HEADER_BYTES] ^= 1U;
    assert(sc_protocol_decode(encoded, written, &decoded) == SC_PROTOCOL_INTEGRITY);
    assert(sc_protocol_decode(encoded, SC_PROTOCOL_HEADER_BYTES - 1U, &decoded) ==
           SC_PROTOCOL_NEED_MORE);
}

static void test_session_and_watchdog(void)
{
    sc_transport_state_t state;
    sc_transport_state_reset(&state, 0x1234U);
    uint8_t session_payload[8];
    put_u64(session_payload, 42U);
    uint8_t frame[SC_PROTOCOL_MAX_FRAME];
    size_t length = encode_control(SC_MSG_START_SESSION, 42U, session_payload,
                                   sizeof(session_payload), frame);
    assert(sc_transport_handle_control(&state, frame, length, 100U) == SC_PROTOCOL_OK);
    assert(state.session_active && state.active_session == 42U);

    length = encode_control(SC_MSG_HEARTBEAT, 42U, NULL, 0U, frame);
    assert(sc_transport_handle_control(&state, frame, length, 200U) == SC_PROTOCOL_OK);
    assert(!sc_transport_heartbeat_expired(&state, 200U + SC_HEARTBEAT_TIMEOUT_MS));
    assert(sc_transport_heartbeat_expired(&state, 201U + SC_HEARTBEAT_TIMEOUT_MS));
    assert(!state.session_active);
}

static void test_stale_session_and_bounded_tx(void)
{
    sc_transport_state_t state;
    sc_transport_state_reset(&state, 1U);
    uint8_t payload[8];
    put_u64(payload, 7U);
    uint8_t encoded[SC_PROTOCOL_MAX_FRAME];
    size_t length = encode_control(SC_MSG_START_SESSION, 7U, payload, sizeof(payload), encoded);
    assert(sc_transport_handle_control(&state, encoded, length, 1U) == SC_PROTOCOL_OK);
    length = encode_control(SC_MSG_STOP_SESSION, 8U, NULL, 0U, encoded);
    assert(sc_transport_handle_control(&state, encoded, length, 2U) == SC_PROTOCOL_INVALID);
    assert(state.metrics.stale_session_frames == 1U);

    uint8_t hello[20];
    assert(sc_transport_make_hello_payload(&state, hello) == sizeof(hello));
    const sc_protocol_frame_t frame = {
        .type = SC_MSG_HELLO,
        .payload = hello,
        .payload_length = sizeof(hello),
    };
    for (size_t i = 0; i < SC_TX_SLOT_COUNT; ++i) {
        assert(sc_transport_enqueue(&state, &frame));
    }
    assert(!sc_transport_enqueue(&state, &frame));
    assert(state.metrics.frames_dropped == 1U);
    assert(state.metrics.tx_high_water == SC_TX_SLOT_COUNT);
}

static void test_diagnostics_shape(void)
{
    sc_transport_state_t state;
    sc_transport_state_reset(&state, 5U);
    uint8_t diagnostics[32];
    assert(sc_transport_make_diagnostics_payload(&state, diagnostics) == sizeof(diagnostics));
    assert(SC_UART_BAUD_RATE == 921600U);
}

int main(void)
{
    test_codec_integrity();
    test_session_and_watchdog();
    test_stale_session_and_bounded_tx();
    test_diagnostics_shape();
    return 0;
}
