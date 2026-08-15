#include "protocol_v1.h"

#include <string.h>

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

static uint32_t get_u32(const uint8_t *p)
{
    uint32_t value = 0U;
    for (unsigned i = 0; i < 4U; ++i) {
        value |= (uint32_t)p[i] << (8U * i);
    }
    return value;
}

static uint64_t get_u64(const uint8_t *p)
{
    uint64_t value = 0U;
    for (unsigned i = 0; i < 8U; ++i) {
        value |= (uint64_t)p[i] << (8U * i);
    }
    return value;
}

static bool valid_type(uint8_t type)
{
    switch (type) {
    case SC_MSG_HELLO:
    case SC_MSG_START_SESSION:
    case SC_MSG_STOP_SESSION:
    case SC_MSG_SET_POWER_STATE:
    case SC_MSG_HEARTBEAT:
    case SC_MSG_AUDIO_FORMAT:
    case SC_MSG_AUDIO_DATA:
    case SC_MSG_STATUS:
    case SC_MSG_DIAGNOSTICS:
    case SC_MSG_ERROR:
        return true;
    default:
        return false;
    }
}

static bool valid_length(sc_message_type_t type, size_t length)
{
    switch (type) {
    case SC_MSG_HELLO:
        return length == 20U;
    case SC_MSG_START_SESSION:
    case SC_MSG_STATUS:
        return length == 8U;
    case SC_MSG_STOP_SESSION:
    case SC_MSG_HEARTBEAT:
        return length == 0U;
    case SC_MSG_SET_POWER_STATE:
        return length == 1U;
    case SC_MSG_AUDIO_FORMAT:
        return length == 12U;
    case SC_MSG_AUDIO_DATA:
        return length > 0U;
    case SC_MSG_DIAGNOSTICS:
        return length == 32U;
    case SC_MSG_ERROR:
        return (length >= 4U) && (length <= 164U);
    default:
        return false;
    }
}

uint32_t sc_protocol_crc32(const uint8_t *data, size_t length, uint32_t crc)
{
    for (size_t index = 0; index < length; ++index) {
        crc ^= data[index];
        for (unsigned bit = 0; bit < 8U; ++bit) {
            crc = (crc & 1U) != 0U ? (crc >> 1U) ^ 0xedb88320U : crc >> 1U;
        }
    }
    return crc;
}

sc_protocol_result_t sc_protocol_encode(const sc_protocol_frame_t *frame, uint8_t *output,
                                        size_t capacity, size_t *written)
{
    if ((frame == NULL) || (output == NULL) || (written == NULL) ||
        (frame->payload_length > SC_PROTOCOL_MAX_PAYLOAD) ||
        ((frame->flags & (uint8_t)~0x03U) != 0U) ||
        !valid_length(frame->type, frame->payload_length) ||
        ((frame->payload_length > 0U) && (frame->payload == NULL))) {
        return SC_PROTOCOL_INVALID;
    }
    const size_t total = SC_PROTOCOL_HEADER_BYTES + frame->payload_length;
    if (capacity < total) {
        return SC_PROTOCOL_NEED_MORE;
    }
    memcpy(output, SC_PROTOCOL_MAGIC, 4U);
    output[4] = SC_PROTOCOL_MAJOR;
    output[5] = SC_PROTOCOL_MINOR;
    output[6] = (uint8_t)frame->type;
    output[7] = frame->flags;
    put_u32(&output[8], (uint32_t)frame->payload_length);
    put_u32(&output[12], frame->sequence);
    put_u64(&output[16], frame->session_id);
    put_u32(&output[24], frame->timestamp_ms);
    if (frame->payload_length > 0U) {
        memcpy(&output[SC_PROTOCOL_HEADER_BYTES], frame->payload, frame->payload_length);
    }
    uint32_t crc = sc_protocol_crc32(output, 28U, 0xffffffffU);
    crc = sc_protocol_crc32(&output[SC_PROTOCOL_HEADER_BYTES], frame->payload_length, crc);
    put_u32(&output[28], ~crc);
    *written = total;
    return SC_PROTOCOL_OK;
}

sc_protocol_result_t sc_protocol_decode(const uint8_t *data, size_t length,
                                        sc_protocol_frame_t *frame)
{
    if ((data == NULL) || (frame == NULL) || (length < SC_PROTOCOL_HEADER_BYTES)) {
        return SC_PROTOCOL_NEED_MORE;
    }
    if ((memcmp(data, SC_PROTOCOL_MAGIC, 4U) != 0) || (data[4] != SC_PROTOCOL_MAJOR) ||
        !valid_type(data[6]) || ((data[7] & (uint8_t)~0x03U) != 0U)) {
        return SC_PROTOCOL_INVALID;
    }
    const uint32_t payload_length = get_u32(&data[8]);
    if (payload_length > SC_PROTOCOL_MAX_PAYLOAD) {
        return SC_PROTOCOL_OVERSIZED;
    }
    const size_t total = SC_PROTOCOL_HEADER_BYTES + (size_t)payload_length;
    if (length < total) {
        return SC_PROTOCOL_NEED_MORE;
    }
    if ((length != total) || !valid_length((sc_message_type_t)data[6], payload_length)) {
        return SC_PROTOCOL_INVALID;
    }
    uint32_t crc = sc_protocol_crc32(data, 28U, 0xffffffffU);
    crc = sc_protocol_crc32(&data[SC_PROTOCOL_HEADER_BYTES], payload_length, crc);
    if (~crc != get_u32(&data[28])) {
        return SC_PROTOCOL_INTEGRITY;
    }
    frame->type = (sc_message_type_t)data[6];
    frame->flags = data[7];
    frame->sequence = get_u32(&data[12]);
    frame->session_id = get_u64(&data[16]);
    frame->timestamp_ms = get_u32(&data[24]);
    frame->payload = &data[SC_PROTOCOL_HEADER_BYTES];
    frame->payload_length = payload_length;
    return SC_PROTOCOL_OK;
}
