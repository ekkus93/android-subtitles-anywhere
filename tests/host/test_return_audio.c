#include <assert.h>
#include <stdint.h>

#include "return_audio.h"

static void test_921600_uart_budget(void)
{
    const sc_uart_budget_t budget = sc_uart_8n1_budget(921600U);
    assert(budget.wire_bits_per_byte == 10U);
    assert(budget.payload_bytes_per_second == 92160U);
}

static void test_native_stereo_pcm_does_not_fit(void)
{
    const sc_uart_budget_t budget = sc_uart_8n1_budget(921600U);
    assert(sc_pcm_bytes_per_second(48000U, 2U, 2U) == 192000U);
    assert(sc_pcm_bytes_per_second(44100U, 2U, 2U) == 176400U);
    assert(!sc_stream_fits_uart(sc_pcm_bytes_per_second(48000U, 2U, 2U), budget, 90U));
    assert(!sc_stream_fits_uart(sc_pcm_bytes_per_second(44100U, 2U, 2U), budget, 90U));
}

static void test_canonical_pcm_has_margin(void)
{
    const sc_uart_budget_t budget = sc_uart_8n1_budget(921600U);
    const uint64_t canonical = sc_pcm_bytes_per_second(16000U, 1U, 2U);
    assert(canonical == 32000U);
    assert(sc_stream_fits_uart(canonical, budget, 80U));
}

static void test_44100_mono_is_too_close_for_policy_margin(void)
{
    const sc_uart_budget_t budget = sc_uart_8n1_budget(921600U);
    const uint64_t mono = sc_pcm_bytes_per_second(44100U, 1U, 2U);
    assert(mono == 88200U);
    assert(sc_stream_fits_uart(mono, budget, 100U));
    assert(!sc_stream_fits_uart(mono, budget, 90U));
}

static void test_invalid_utilization_is_rejected(void)
{
    const sc_uart_budget_t budget = sc_uart_8n1_budget(921600U);
    assert(!sc_stream_fits_uart(1U, budget, 0U));
    assert(!sc_stream_fits_uart(1U, budget, 101U));
}

int main(void)
{
    test_921600_uart_budget();
    test_native_stereo_pcm_does_not_fit();
    test_canonical_pcm_has_margin();
    test_44100_mono_is_too_close_for_policy_margin();
    test_invalid_utilization_is_rejected();
    return 0;
}
