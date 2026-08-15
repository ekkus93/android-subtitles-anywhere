---
name: lint-n-test
description: Lint every component of this monorepo and run all of its tests, unconditionally — not just the parts that changed. Use this whenever the user says "lint the files and run all tests", "/lint-n-test", "lint everything", "run all the linters and tests", or otherwise asks for a full lint+test sweep of the whole repo rather than just the checks for their current diff.
---

Run lint + tests for every component in this monorepo, regardless of what's currently dirty in git. This is the "check absolutely everything" sibling of the `/verify` skill, which only checks components touched by the current change — reach for `/lint-n-test` instead when the user wants full-repo coverage (e.g. before a release, after a rebase, or just to be sure nothing's silently broken elsewhere).

The actual check scripts (`scripts/check-*.sh`) are lint+test bundled together per component — there's no separate lint-only or test-only entry point, so "lint" and "test" here mean running the whole script.

## Why delegate to a Haiku subagent

This is mechanical, deterministic work — run some scripts, read pass/fail, paste failing output. It doesn't need the session's main model. Delegate it to a Haiku subagent via the `Agent` tool so the main session stays cheap and free to keep working on whatever the user actually asked for next.

Spawn one subagent with `model: "haiku"` and a prompt along these lines (adjust paths/commands only if the scripts have moved):

```
Run these three scripts from the repo root, in order, and report the exit code and full output of each:

1. bash scripts/check-rust.sh
2. bash scripts/check-android.sh
3. bash scripts/check-firmware-host.sh

check-android.sh needs a `gradle` binary. It is often NOT on PATH by default even though a
matching Gradle distribution may already be cached locally (CI installs it via setup-gradle;
a local checkout usually doesn't have it on PATH at all). Before running check-android.sh:
first try plain `gradle -v`. If that fails with "command not found", search for a cached
wrapper distribution instead of giving up:

    find ~/.gradle/wrapper/dists -path '*/gradle-*/bin/gradle' 2>/dev/null

If that finds a binary, export GRADLE_CMD to its path before invoking the script, e.g.:

    export GRADLE_CMD=/home/user/.gradle/wrapper/dists/gradle-9.5.0-bin/<hash>/gradle-9.5.0/bin/gradle
    bash scripts/check-android.sh

(check-android.sh reads GRADLE_CMD and falls back to plain `gradle` if it's unset.) Only report
check-android.sh as a genuine FAIL if gradle is truly unavailable after this search — a bare
"gradle: command not found" with no attempt to locate a cached distribution is not a real
result, it's an environment-setup miss.

Don't stop early if one script fails — run all three regardless, then report on all of them.

For each script, report:
- PASS or FAIL
- If FAIL, the actual failing output (not a paraphrase or summary of it)

Do not attempt scripts/check-firmware.sh (the full ESP-IDF firmware build) — it requires an
activated ESP-IDF environment that may not be present; check-firmware-host.sh already covers
the host-testable subset without that dependency.
```

## Reporting back

Once the subagent returns, relay its per-component pass/fail plainly to the user, with the actual failing output pasted in full for anything that failed — not summarized. If everything passed, a short confirmation naming all three scripts is enough; don't pad it out.

Don't mark any `docs/SILENT_CAPTION_V01_TODO.md` checkboxes or evidence docs as complete based on a passing run alone — that still needs the evidence described in `docs/TRACEABILITY.md` (see the `/sc-evidence` skill).
