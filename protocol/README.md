# Silent Caption Wire Protocol

This directory owns the transport-neutral, versioned protocol between the caption dongle firmware and mobile transport adapters.

- `PROTOCOL_V1.md` — normative protocol v1 specification.
- `test_vectors_v1.json` — canonical machine-readable positive and negative vectors shared by Rust, firmware host tests, Android fixtures, and later device tests.

Platform-specific USB/UART driver behavior does not belong in the wire format. Implementations must not extend v1 by silently repurposing reserved fields; compatible extensions require documented semantics and test vectors.
