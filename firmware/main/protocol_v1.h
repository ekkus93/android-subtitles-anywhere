#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define SC_PROTOCOL_MAGIC "SCAP"
#define SC_PROTOCOL_HEADER_BYTES 32U
#define SC_PROTOCOL_MAX_PAYLOAD 4096U
#define SC_PROTOCOL_MAX_FRAME (SC_PROTOCOL_HEADER_BYTES + SC_PROTOCOL_MAX_PAYLOAD)
#define SC_PROTOCOL_MAJOR 1U
#define SC_PROTOCOL_MINOR 0U

typedef enum {
    SC_MSG_HELLO = 0x01,
    SC_MSG_START_SESSION = 0x02,
    SC_MSG_STOP_SESSION = 0x03,
    SC_MSG_SET_POWER_STATE = 0x04,
    SC_MSG_HEARTBEAT = 0x05,
    SC_MSG_AUDIO_FORMAT = 0x10,
    SC_MSG_AUDIO_DATA = 0x11,
    SC_MSG_STATUS = 0x20,
    SC_MSG_DIAGNOSTICS = 0x21,
    SC_MSG_ERROR = 0x7f,
} sc_message_type_t;

typedef struct {
    sc_message_type_t type;
    uint8_t flags;
    uint32_t sequence;
    uint64_t session_id;
    uint32_t timestamp_ms;
    const uint8_t *payload;
    size_t payload_length;
} sc_protocol_frame_t;

typedef enum {
    SC_PROTOCOL_OK = 0,
    SC_PROTOCOL_NEED_MORE,
    SC_PROTOCOL_INVALID,
    SC_PROTOCOL_OVERSIZED,
    SC_PROTOCOL_INTEGRITY,
} sc_protocol_result_t;

uint32_t sc_protocol_crc32(const uint8_t *data, size_t length, uint32_t crc);
sc_protocol_result_t sc_protocol_encode(const sc_protocol_frame_t *frame, uint8_t *output,
                                        size_t capacity, size_t *written);
sc_protocol_result_t sc_protocol_decode(const uint8_t *data, size_t length,
                                        sc_protocol_frame_t *frame);
