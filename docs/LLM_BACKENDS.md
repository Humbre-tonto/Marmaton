# Marmaton — Pluggable LLM Backends Design

This document specifies how Marmaton must support **user-selectable LLM backends** so the
user decides where reasoning runs: a local model file on the phone, a self-hosted server on
the LAN, or an optional cloud API.

## Goals

1. **On-device local model file.** The user picks a model file already on the phone (GGUF /
   `.task` / `.litertlm`, e.g. Qwen, DeepSeek, Gemma) and the agent runs fully offline.
2. **Remote LAN server (Ollama).** The user enters the IP/host and port of a machine running
   Ollama (or any OpenAI-compatible server) and the agent calls it over HTTP.
3. **Cloud API (optional).** The user optionally configures a cloud provider (OpenAI-compatible
   endpoint + API key + model).

Offline operation (goal 1) must always remain possible and is the privacy-preserving default.
The remote and cloud backends are opt-in.

## Current state (what exists today)

- Reasoning is hardcoded to **ML Kit GenAI / AICore (Gemini Nano)** inside
  `com.marmaton.agent.llm.GemmaAgentEngine` (an `object` singleton).
- `GemmaAgentEngine` already exposes two **backend-agnostic** pure functions that must be
  reused: `buildSystemPrompt(userGoal, serializedScreen): String` and
  `parseAction(rawText): AgentAction?`.
- The agent loop (`AgentForegroundService.startAutonomousLoop`) calls
  `GemmaAgentEngine.reasonAction(goal, serializedScreen)` exactly once per iteration.
- `DashboardScreen` reads `GemmaAgentEngine` state flows (`modelStatus`, `isDownloading`) and
  triggers `initModel()` / `startDownload()`.
- **The manifest has no `INTERNET` permission** — only `FOREGROUND_SERVICE*`. Remote and cloud
  backends cannot work until this is added.

## Target architecture

### 1. The backend interface

Introduce a single abstraction that the reasoning path depends on:

```kotlin
package com.marmaton.agent.llm

/** Backend-agnostic text generation. Implementations must be safe to call from
 *  Dispatchers.Default/IO and must never touch the AccessibilityService thread. */
interface LlmBackend {
    /** Human-readable id for the UI / logs (e.g. "Local file", "Ollama", "Cloud"). */
    val displayName: String

    /** Cheap readiness/health check used to render status and to fail fast. */
    suspend fun status(): BackendStatus

    /** Send a fully-built prompt, return raw model text (may include markdown/JSON fences). */
    suspend fun generate(prompt: String): String
}

sealed interface BackendStatus {
    data object Ready : BackendStatus
    data class NotReady(val reason: String) : BackendStatus     // e.g. no file selected
    data class Unavailable(val reason: String) : BackendStatus  // e.g. device unsupported
}
```

### 2. The reasoner (backend-agnostic)

Refactor reasoning so it delegates only the raw generation step to the selected backend, while
reusing the existing prompt builder and parser:

```kotlin
class AgentReasoner(private val backend: LlmBackend) {
    suspend fun reason(userGoal: String, serializedScreen: String): AgentAction? {
        val prompt = buildSystemPrompt(userGoal, serializedScreen) // existing pure fn
        val raw = backend.generate(prompt)
        return parseAction(raw)                                    // existing pure fn
    }
}
```

`AgentForegroundService` obtains the currently-selected backend from a provider/factory and
uses `AgentReasoner`. It must not reference any concrete backend directly.

### 3. Required backend implementations

**a. `LocalFileBackend` (on-device, offline)**
- The user selects a model file via the Storage Access Framework (`ACTION_OPEN_DOCUMENT`), and
  the app persists the URI permission (`takePersistableUriPermission`).
- Load and run the model with an embedded inference engine. **Recommended primary:** MediaPipe
  LLM Inference (`com.google.mediapipe:tasks-genai`) which loads `.task` / `.litertlm` bundles
  and supports Gemma and several other models with CPU/GPU acceleration. **For arbitrary GGUF
  (Qwen/DeepSeek/etc.):** a llama.cpp-based engine (JNI/`llama.android`) is the flexible path;
  it may be added as a second local engine. Document clearly which model formats are supported.
- Because large model files may need to be copied to app-private storage for the engine to
  `mmap`, handle a one-time import with progress and clear errors on OOM / unsupported format.

**b. `OllamaBackend` (remote LAN)**
- Config: scheme (`http`/`https`), host/IP, port (default `11434`), model name, optional
  timeouts. Build base URL from these.
- Call Ollama's `POST /api/chat` (or `/api/generate`) with `stream=false`, or its
  OpenAI-compatible `POST /v1/chat/completions`. Use OkHttp/Retrofit or Ktor.
- `status()` should hit `GET /api/tags` (or `/`) to verify reachability and that the model
  exists.

**c. `CloudBackend` (optional)**
- Config: base URL (default OpenAI-compatible), API key, model name. Treat OpenAI-compatible
  `POST /v1/chat/completions` as the common shape; this also covers many providers.
- The API key must be stored with EncryptedSharedPreferences (or encrypted DataStore) and never
  written to logs or the run-log.

**d. `AICoreBackend` (optional, keep existing)**
- Wrap the current ML Kit GenAI / Gemini Nano code as one more optional backend. It is no longer
  the default.

### 4. Selection, configuration & persistence

- Add a `BackendType` enum and a settings model persisted with **DataStore** (Preferences or
  Proto). Store: selected type, local file URI, Ollama scheme/host/port/model, cloud base
  URL/model, and (encrypted) API key.
- Add a `BackendFactory`/provider that reads settings and returns the configured `LlmBackend`.
- Add a **Settings screen** (Compose) to choose the backend and edit its config, reachable from
  the dashboard. The dashboard should show the active backend and its `status()`.

### 5. Manifest, permissions & network security

- Add `<uses-permission android:name="android.permission.INTERNET" />`.
- Do **not** enable global cleartext. Add a `res/xml/network_security_config.xml` that permits
  cleartext only for private ranges the user targets (e.g. `10.0.0.0/8`, `172.16.0.0/12`,
  `192.168.0.0/16`) or make cleartext scoped/opt-in, and reference it via
  `android:networkSecurityConfig` on `<application>`. Cloud calls must stay HTTPS.
- The `SET_TEXT`/gesture accessibility flow is unchanged.

### 6. Backwards compatibility & safety

- Keep `buildSystemPrompt` and `parseAction` as the single source of prompt/JSON logic — do not
  duplicate them per backend.
- If the selected backend's `status()` is not `Ready`, the dashboard must show why and the
  **Start Agent** button should be disabled (mirroring the existing accessibility-enabled gate).
- A backend failure (network error, model load failure, timeout) must be caught and surfaced in
  the run-log; it must never crash `AgentForegroundService` or the AccessibilityService.

## Testing / acceptance criteria

- **Unit (JVM, framework-free):**
  - `AgentReasoner.reason` with a fake `LlmBackend` returning canned text produces the correct
    `AgentAction` (reuses `parseAction`); a backend returning garbage yields `null` gracefully.
  - `OllamaBackend` request/response mapping tested against a mock HTTP server (e.g. OkHttp
    `MockWebServer`): correct URL, body, model, and parsing of the reply.
  - Settings persistence round-trips through DataStore.
- **Manual/instrumented:**
  - Local: select a model file → status `Ready` → agent completes a simple goal offline.
  - Ollama: enter a LAN IP:port + model → status `Ready` → agent runs against the remote server.
  - Cloud: configure endpoint + key → agent runs; key is not present in logs.
- Existing `ParserSerializerTest` continues to pass.

## Out of scope (for this change)

- Streaming token UI, multi-model routing, and RAG. Keep the loop's single-shot
  `generate(prompt) -> text` contract.
