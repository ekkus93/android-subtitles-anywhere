#include "return_audio.h"

sc_uart_budget_t sc_uart_8n1_budget(uint32_t baud)
{
    const sc_uart_budget_t budget = {
        .baud = baud,
        .wire_bits_per_byte = 10U,
        .payload_bytes_per_second = baud / 10U,
    };
    return budget;
}

uint64_t sc_pcm_bytes_per_second(uint32_t sample_rate_hz, uint8_t channels,
                                 uint8_t bytes_per_sample)
{
    return (uint64_t)sample_rate_hz * (uint64_t)channels * (uint64_t)bytes_per_sample;
}

bool sc_stream_fits_uart(uint64_t payload_bytes_per_second, sc_uart_budget_t budget,
                         uint8_t utilization_percent)
{
    if ((budget.wire_bits_per_byte == 0U) || (utilization_percent == 0U) ||
        (utilization_percent > 100U)) {
        return false;
    }
    const uint64_t allowed =
        ((uint64_t)budget.payload_bytes_per_second * (uint64_t)utilization_percent) / 100U;
    return payload_bytes_per_second <= allowed;
}
