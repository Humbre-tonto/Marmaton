# Marmaton — Pre-release QA & Usability Pass

This document is the testing companion for the release that makes Marmaton usable by people
with **no AI background**. It has four parts:

1. [Bugs found & fixed in this pass](#1-bugs-found--fixed)
2. [On-device QA checklist](#2-on-device-qa-checklist) — run this on a real phone
3. [Usability audit for non-technical users](#3-usability-audit-for-non-technical-users) — what to fix in the next update
4. [Automated test coverage](#4-automated-test-coverage)

---

## 1. Bugs found & fixed

| # | Severity | Area | Problem | Status |
|---|----------|------|---------|--------|
| B1 | High | `GgufBackend` | No synchronization on the native model handle. The Home tab polls `status()` every 15s while the agent/chat loop calls `generate()` on the **same cached instance** — two threads could both native-`load()`, or `close()` (on Stop) could `free()` the handle mid-load → native use-after-free crash. | **Fixed** — `load`/`free` now guarded by a lock. |

### Still to verify on-device (not yet changed)
- **POST_NOTIFICATIONS (Android 13+):** the agent/download foreground services show a notification. On Android 13+ this permission is runtime-granted; if it was never requested, the service still runs but the notification (and the Emergency-Stop action on it) may be hidden. Confirm the notification appears — see QA step P3.
- **Runaway agent:** the agent loop has no maximum number of steps. If a small model never emits `FINISHED`, it runs until the user taps Emergency Stop (battery drain). Recommended fix in [U6](#u6-runaway-agent--no-step-limit).

---

## 2. On-device QA checklist

Install the debug APK from the latest `Build APK` run (or `./gradlew assembleDebug`). Work top to
bottom; each row is **Steps → Expected**. Mark Pass/Fail.

### A. First run / onboarding
| Step | Do | Expected |
|------|----|----------|
| A1 | Fresh install, open app | Onboarding step 1 (Welcome + safety cards) shows |
| A2 | Tap Get Started → step 2 | Accessibility step; Continue is disabled until enabled |
| A3 | Open settings, enable Marmaton accessibility, return | Status flips to "enabled" within ~1s; Continue enables |
| A4 | Step 3, pick "On this phone", Continue | Selection persists |
| A5 | Step 4 "Ready", tap Start | Lands on Home. **Known gap:** no model is downloaded yet — see [U1](#u1-installno-model-dead-end) |

### B. Backends & model download
| Step | Do | Expected |
|------|----|----------|
| B1 | Backends tab → pick "On device" | Model catalog is listed |
| B2 | Download **Qwen 2.5 1.5B (Recommended)** | Progress notification; completes; becomes active model |
| B3 | Kill app mid-download, reopen, re-tap download | Resumes from partial (`.part`), doesn't restart from 0 |
| B4 | Try a **gated Gemma** without a token | Clear message: accept license + add HF token (not a silent failure) |
| B5 | After download, Home backend status | Shows "Ready" with the model name |
| B6 | Export model (Diagnostics) then uninstall/reinstall | Exported file re-importable |

### C. Chat
| Step | Do | Expected |
|------|----|----------|
| C1 | Chat tab with a **Qwen Instruct** model selected | Empty-state hint shows |
| C2 | Send "Write a Kotlin function to reverse a string" | "Thinking…" then a fenced code block reply |
| C3 | Send a follow-up ("now make it uppercase") | Reply reflects prior turn (multi-turn memory works) |
| C4 | Switch to a **Coder** model, ask for code | Reply quality noticeably better for code |
| C5 | Tap the trash icon | Transcript clears |
| C6 | Send with **no model** selected | Friendly ⚠️ message, no crash |
| C7 | Switch tabs mid-reply and back | Transcript intact (survives tab switch) |

### D. Workflows
| Step | Do | Expected |
|------|----|----------|
| D1 | Workflows tab → + | Editor opens with one empty step |
| D2 | Name it, add 2–3 steps, Save | Card appears with step count + preview |
| D3 | Edit it, remove a step, Save | Change persists |
| D4 | Force-quit and reopen | Workflow still there (persisted) |
| D5 | Tap **Run** (accessibility ON) | Agent runs step 1; on FINISHED advances to step 2 (watch Activity log) |
| D6 | Tap Run while the agent is already running | Run disabled + "agent is running" banner |
| D7 | Delete a workflow | Removed immediately |

### E. Agent (Home)
| Step | Do | Expected |
|------|----|----------|
| E1 | Home, type "Turn on battery saver", Start | Agent traverses screen, acts; log updates live |
| E2 | Tap Emergency Stop | Stops within a step; notification clears |
| E3 | Start with accessibility OFF | Blocking card prompts to enable it |
| E4 | Let a goal finish | "Goal achieved" state + Run again / New objective |

### F. Voice, permissions, misc
| Step | Do | Expected |
|------|----|----------|
| P1 | Enable voice, run agent | Narrates start/steps/finish |
| P2 | Mic button on goal input | Speech-to-text fills the field |
| P3 | Android 13+: first agent run | Notification permission requested; Emergency-Stop notification visible |
| P4 | Analytics consent toggle off | No events sent (verify in logs); toggling on tracks |
| P5 | Rotate device on each screen | No crash, state preserved |

---

## 3. Usability audit for non-technical users

Ranked by how badly each one loses a non-AI user. These are recommendations for the **next update**,
not yet implemented.

### U1. Install→no-model dead end  *(Critical)*
Onboarding finishes and step 4 "Ready" shows the backend as ✓ (`isChecked = true`, hard-coded), but a
fresh install has **no model file**. The user taps Start and the agent says "backend not ready." They
have no idea they must go to Backends → download a model.
**Fix:** add a model step to onboarding with a single **"Download recommended model (Qwen 2.5 1.5B)"**
button + progress, and make the Ready checklist reflect the *real* model status. On Home, when no model
is present, show a prominent "Download a model to get started" card instead of a disabled Start button.

### U2. Jargon everywhere  *(High)*
"Backend", "GGUF", "MediaPipe", "q4_k_m", "Ollama", "Cloud API base URL", "Hugging Face token" mean
nothing to a normal person.
**Fix:** rename in the UI — "AI model" not "backend"; the three choices become **"On this phone"**,
**"On my computer (advanced)"**, **"Online service (advanced)"**. Drop quant suffixes from the primary
label (keep in a details line). Hide Ollama/Cloud/token fields under an "Advanced" expander.

### U3. Too many model choices  *(High)*
The catalog lists 8 models with size/quant labels. A non-AI user won't know which to pick.
**Fix:** show **one recommended model** with a big Download button; put the rest behind "More models".
Pick the default by device RAM (0.5B / 1.5B / 3B).

### U4. Gated-model flow is expert-only  *(Medium — mostly mitigated)*
The Gemma `.task` models need an HF account, license acceptance, and a pasted token — impossible for a
normal user. Already mitigated by making ungated **Qwen** models the top recommendations.
**Fix:** move gated Gemma models under "Advanced models" so a normal user never hits the token wall.

### U5. Agent capability vs. expectation  *(Medium)*
A workflow like "send a message to Mom" needs many precise steps from a small model and may stall or
loop. Non-AI users will read that as "broken."
**Fix:** ship 3–4 **example workflows/templates**, set expectations in copy ("works best for simple,
common tasks"), and keep steps small and concrete.

### U6. Runaway agent / no step limit  *(Medium)*
If a model never emits `FINISHED`, the agent loops indefinitely (battery). The Emergency Stop exists but
a normal user may not notice.
**Fix:** cap iterations per goal (e.g. 25–30) and stop with a plain message: "Couldn't finish this one —
try rephrasing." (Small, low-risk change.)

### U7. Chat model = agent model  *(Low)*
Selecting a Coder model for Chat also changes the model the on-screen agent uses. A normal user won't
connect those.
**Fix (later):** separate a "chat model" from the "agent model", or surface a one-line note when a
Coder model is active ("great for Chat; use a Qwen Instruct model for on-screen tasks").

### U8. Five bottom-nav tabs, mixed icons  *(Low)*
Home / Chat / Workflows / Activity / Backends is dense, and Chat/Workflows use emoji while others use
icons.
**Fix:** consider grouping (e.g. Activity + Backends under a "Settings" area) and use consistent vector
icons.

---

## 4. Automated test coverage

Existing JVM unit tests: `LlmBackendTest`, `AnalyticsTest`, `ParserSerializerTest`.

Added in this pass (`ChatAndWorkflowTest`):
- Workflow JSON round-trip (persistence format is stable).
- `WorkflowRepository` upsert-replaces-by-id, delete-only-matching, empty-store-is-empty (through a real DataStore).
- `ChatSession.buildPrompt` includes the system prompt + role labels and truncates to the recent-history cap.
- `extractHost` URL parsing for analytics host extraction.

Not covered by JVM tests (need instrumentation or a device — see the on-device checklist): Compose UI,
the AccessibilityService gesture dispatch, native GGUF/MediaPipe loading, and the foreground services.
