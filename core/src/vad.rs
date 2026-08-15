//! Backend-neutral VAD, caption stabilization, and discontinuity semantics.

use crate::captions::CaptionEvent;

/// Deterministic energy VAD configuration for canonical 16 kHz PCM.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VadConfig {
    pub speech_threshold: i16,
    pub pre_roll_samples: usize,
    pub post_roll_samples: usize,
}

impl Default for VadConfig {
    fn default() -> Self {
        Self {
            speech_threshold: 500,
            pre_roll_samples: 3_200,
            post_roll_samples: 4_800,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SpeechState {
    Silence,
    Speech,
    PostRoll,
}

/// Bounded deterministic energy detector. Backends may use native endpointing instead,
/// but must expose equivalent speech/finalization semantics to the caption stabilizer.
pub struct EnergyVad {
    config: VadConfig,
    state: SpeechState,
    pre_roll: Vec<i16>,
    post_roll_remaining: usize,
}

impl EnergyVad {
    #[must_use]
    pub fn new(config: VadConfig) -> Self {
        Self {
            config,
            state: SpeechState::Silence,
            pre_roll: Vec::with_capacity(config.pre_roll_samples),
            post_roll_remaining: 0,
        }
    }

    #[must_use]
    pub const fn state(&self) -> SpeechState {
        self.state
    }

    /// Observes canonical PCM and returns whether it belongs to an active speech region.
    pub fn observe(&mut self, samples: &[i16]) -> bool {
        let speech = samples
            .iter()
            .any(|sample| sample.saturating_abs() >= self.config.speech_threshold);
        if speech {
            self.state = SpeechState::Speech;
            self.post_roll_remaining = self.config.post_roll_samples;
            return true;
        }
        if (self.state == SpeechState::Speech || self.state == SpeechState::PostRoll)
            && self.post_roll_remaining > 0
        {
            self.post_roll_remaining = self.post_roll_remaining.saturating_sub(samples.len());
            self.state = SpeechState::PostRoll;
            return true;
        }
        self.state = SpeechState::Silence;
        self.remember_pre_roll(samples);
        false
    }

    #[must_use]
    pub fn pre_roll(&self) -> &[i16] {
        &self.pre_roll
    }

    pub fn reset(&mut self) {
        self.state = SpeechState::Silence;
        self.pre_roll.clear();
        self.post_roll_remaining = 0;
    }

    fn remember_pre_roll(&mut self, samples: &[i16]) {
        if self.config.pre_roll_samples == 0 {
            return;
        }
        self.pre_roll.extend_from_slice(samples);
        if self.pre_roll.len() > self.config.pre_roll_samples {
            let excess = self.pre_roll.len() - self.config.pre_roll_samples;
            self.pre_roll.drain(..excess);
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiscontinuityReason {
    UsbGap,
    BluetoothGap,
    MediaRouteChange,
    PauseResume,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum CaptionUpdate {
    ReplacePartial { text: String },
    CommitFinal { text: String },
    ClearPartial,
    Discontinuity(DiscontinuityReason),
}

/// Converts backend caption events into replacement/commit semantics.
#[derive(Default)]
pub struct CaptionStabilizer {
    session_id: Option<u64>,
    partial: Option<String>,
    last_final_sequence: u64,
}

impl CaptionStabilizer {
    pub fn start_session(&mut self, session_id: u64) {
        self.session_id = Some(session_id);
        self.partial = None;
        self.last_final_sequence = 0;
    }

    #[must_use]
    pub fn accept(&mut self, event: &CaptionEvent) -> Option<CaptionUpdate> {
        if self.session_id != Some(event.session_id) {
            return None;
        }
        let text = normalize_display_text(&event.text);
        if text.is_empty() {
            return None;
        }
        if event.is_final {
            if event.sequence <= self.last_final_sequence {
                return None;
            }
            self.last_final_sequence = event.sequence;
            self.partial = None;
            return Some(CaptionUpdate::CommitFinal { text });
        }
        if self.partial.as_deref() == Some(text.as_str()) {
            return None;
        }
        self.partial = Some(text.clone());
        Some(CaptionUpdate::ReplacePartial { text })
    }

    pub fn discontinuity(&mut self, reason: DiscontinuityReason) -> Vec<CaptionUpdate> {
        let mut updates = Vec::new();
        if self.partial.take().is_some() {
            updates.push(CaptionUpdate::ClearPartial);
        }
        updates.push(CaptionUpdate::Discontinuity(reason));
        updates
    }

    pub fn stop_session(&mut self) {
        self.session_id = None;
        self.partial = None;
        self.last_final_sequence = 0;
    }
}

/// Applies only whitespace normalization. Punctuation, casing, and confidence remain
/// backend-owned so the portable core never fabricates linguistic information.
#[must_use]
pub fn normalize_display_text(text: &str) -> String {
    text.split_whitespace().collect::<Vec<_>>().join(" ")
}
