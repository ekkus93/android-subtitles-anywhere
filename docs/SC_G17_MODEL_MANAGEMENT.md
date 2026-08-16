# SC-G17 Model Management

This document records the software contract and acceptance evidence for SC-321 through SC-328.

## Manifest contract (SC-321, SC-327)

Every model entry carries backend/model IDs, version, HTTPS URL, SHA-256, installed/download size, license/source, language metadata, compatibility constraints, and performance guidance. The application must not accept a non-HTTPS artifact URL. Production manifests must be distributed through an authenticated release channel; artifact integrity is independently enforced by the pinned SHA-256.

## Installation transaction (SC-322-SC-325)

`ModelManager` downloads to a `.part` file, reports byte progress, supports cooperative cancellation, checks available storage before opening the network source, rejects oversized/truncated content, verifies SHA-256, and promotes the temporary file only after validation. A failed install or upgrade cannot overwrite a previously installed version.

The storage preflight reserves twice the artifact size so the existing model and temporary download can coexist during an upgrade. This is intentionally conservative.

## Deletion and activation (SC-326)

Deletion refuses to remove the active model. Installed-state discovery revalidates both size and SHA-256, so corrupt files are not reported as usable.

## Tests (SC-328)

`ModelManagementTest` covers successful promotion, interrupted/cancelled downloads, wrong size, corrupt hash, insufficient storage, failed upgrade/rollback preservation, active-model deletion protection, and rejection of insecure artifact URLs.

## Gate status

SC-G17 software implementation is complete when Android unit tests, ktlint, detekt, lint, and assembleDebug pass with this implementation. Real production model URLs/manifests remain release-input data rather than a reason to weaken integrity checks.
