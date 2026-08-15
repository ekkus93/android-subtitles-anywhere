use silent_caption_core::audio::{
    AudioRingBuffer, CANONICAL_SAMPLE_RATE_HZ, OverflowPolicy, PcmError, canonicalize_s16,
    pcm_s16le, resample_mono_linear, stereo_to_mono,
};
use silent_caption_core::diagnostics::Diagnostics;
use silent_caption_core::transport::{AudioInput, PcmFormat};

#[test]
fn drop_newest_preserves_oldest_audio_and_counts_overflow() {
    let mut buffer = AudioRingBuffer::new(4, OverflowPolicy::DropNewest);
    buffer.push(&[1, 2, 3, 4, 5, 6]);
    let mut output = [0; 4];
    assert_eq!(buffer.pop_into(&mut output), 4);
    assert_eq!(output, [1, 2, 3, 4]);
    assert_eq!(buffer.metrics().dropped_samples, 2);
    assert_eq!(buffer.metrics().high_water_samples, 4);
}

#[test]
fn drop_oldest_preserves_newest_audio() {
    let mut buffer = AudioRingBuffer::new(4, OverflowPolicy::DropOldest);
    buffer.push(&[1, 2, 3, 4, 5, 6]);
    let mut output = [0; 4];
    assert_eq!(buffer.pop_into(&mut output), 4);
    assert_eq!(output, [3, 4, 5, 6]);
    assert_eq!(buffer.metrics().dropped_samples, 2);
}

#[test]
fn underrun_is_explicitly_counted() {
    let mut buffer = AudioRingBuffer::new(4, OverflowPolicy::DropNewest);
    buffer.push(&[10, 20]);
    let mut output = [0; 4];
    assert_eq!(buffer.pop_into(&mut output), 2);
    assert_eq!(buffer.metrics().underrun_samples, 2);
}

#[test]
fn s16le_fixture_decodes_exactly() {
    let bytes = [0x00, 0x80, 0xff, 0xff, 0x00, 0x00, 0xff, 0x7f];
    assert_eq!(pcm_s16le(&bytes).unwrap(), [-32_768, -1, 0, 32_767]);
    assert_eq!(pcm_s16le(&bytes[..7]), Err(PcmError::IncompleteSample));
}

#[test]
fn stereo_fixture_downmixes_without_overflow() {
    let stereo = [32_767, 32_767, -32_768, -32_768, 10_000, -10_000];
    assert_eq!(stereo_to_mono(&stereo).unwrap(), [32_767, -32_768, 0]);
    assert_eq!(
        stereo_to_mono(&stereo[..5]),
        Err(PcmError::IncompleteStereoFrame)
    );
}

#[test]
fn deterministic_resample_fixture_has_expected_values() {
    let input = [0, 1_000, 2_000, 3_000];
    assert_eq!(
        resample_mono_linear(&input, 8_000, 16_000).unwrap(),
        [0, 500, 1_000, 1_500, 2_000, 2_500, 3_000, 3_000]
    );
}

#[test]
fn canonicalizer_produces_16khz_mono_fixture() {
    let stereo_8khz = [0, 0, 1_000, 1_000, 2_000, 2_000, 3_000, 3_000];
    let canonical = canonicalize_s16(&stereo_8khz, 2, 8_000).unwrap();
    assert_eq!(CANONICAL_SAMPLE_RATE_HZ, 16_000);
    assert_eq!(
        canonical,
        [0, 500, 1_000, 1_500, 2_000, 2_500, 3_000, 3_000]
    );
}

#[test]
fn diagnostics_track_level_timing_and_counts() {
    let mut diagnostics = Diagnostics::default();
    diagnostics.observe_audio(&[-32_768, 100, 32_767], 55);
    diagnostics.observe_emitted(2);
    assert_eq!(diagnostics.audio_samples_received, 3);
    assert_eq!(diagnostics.audio_samples_emitted, 2);
    assert_eq!(diagnostics.peak_abs_sample, 32_768);
    assert_eq!(diagnostics.last_source_timestamp_ms, Some(55));
}

#[test]
fn transport_audio_input_is_platform_neutral_data() {
    let samples = [1, 2, 3];
    let input = AudioInput {
        session_id: 7,
        sequence: 9,
        timestamp_ms: 11,
        format: PcmFormat {
            sample_rate_hz: 48_000,
            channels: 1,
        },
        samples: &samples,
        discontinuity: false,
    };
    assert_eq!(input.samples, samples);
    assert_eq!(input.format.sample_rate_hz, 48_000);
}
