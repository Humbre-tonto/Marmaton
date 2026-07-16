# Project Context
This repository contains a fully autonomous Android device agent. The application parses screen states, reasons about the user's goal, and executes system-wide actions autonomously. Reasoning is performed by a **user-selectable LLM backend**, so the user decides where inference runs:

1. **On-device local model file** — the user points the app at a model file stored on the phone (e.g. a GGUF / `.task` / `.litertlm` for Qwen, DeepSeek, Gemma, etc.) and it runs fully offline on the device.
2. **Remote self-hosted server** — the app connects to a local **Ollama** (or other OpenAI-compatible) server running on another machine on the LAN, addressed by IP.
3. **Cloud API (optional)** — the user may connect to a cloud LLM provider (OpenAI-compatible endpoint) when they choose to.

Privacy-preserving, fully-offline operation (backend 1) is the default and must always remain possible; the remote and cloud backends are opt-in and configured by the user. See `docs/LLM_BACKENDS.md` for the full backend design.

# Tech Stack & Standards
- **Language**: Kotlin.
- **UI Framework**: Jetpack Compose (Material 3).
- **Asynchronous Execution**: Kotlin Coroutines.
- **LLM Integration**: A pluggable backend abstraction (`LlmBackend`). On-device execution via an embedded inference engine (MediaPipe LLM Inference / Google AI Edge, or a llama.cpp GGUF engine); remote execution via HTTP to Ollama / OpenAI-compatible endpoints. No single provider may be hardcoded into the reasoning loop.

# Architectural Directives
1. **AccessibilityService Constraints**: 
   - Never block the AccessibilityService thread. Push all UI tree parsing and LLM inference to `Dispatchers.Default` or `Dispatchers.IO`.
   - The service must gracefully handle rapidly changing screen states without memory leaks.

2. **Context Window Management (Strict)**: 
   - Node traversal must be hyper-optimized. Aggressively filter out invisible nodes, scrollbars, and decorative layouts without content descriptions.
   - The serialized representation of the screen must be minimal to ensure fast time-to-first-token. Use shortened keys for the LLM prompt (e.g., `txt` for text, `id` for node_id, `clk` for clickable).

3. **Action Dispatching**: 
   - Implement a robust mapping layer between the LLM's structured output and Android's `AccessibilityNodeInfo.performAction()`.
   - Support fallback gestures (swipe/scroll) for targets that do not expose standard click actions.

4. **Separation of Concerns**: 
   - Keep the LLM reasoning engine, the UI tree serializer, and the AccessibilityService lifecycle strictly decoupled. Ensure the LLM engine can be unit-tested independently of the Android framework by passing in mock UI JSON.

5. **Pluggable Inference Backends**:
   - The reasoning loop must depend only on a backend-agnostic interface (`LlmBackend`) that takes a prompt string and returns generated text. Prompt construction (`buildSystemPrompt`) and response parsing (`parseAction`) stay shared and backend-independent.
   - Ship at minimum three backends: (a) on-device local model file selected by the user via a file picker, (b) remote Ollama / OpenAI-compatible server addressed by host:port, (c) optional cloud API. Any existing AICore/Gemini Nano path becomes just one more optional backend, never the hardcoded default.
   - Backend selection and its configuration (model file URI, server URL/IP, model name, API key) are chosen at runtime by the user and persisted. API keys must be stored securely (e.g. EncryptedSharedPreferences / encrypted DataStore), never logged.
   - Networking requires the `INTERNET` permission. Cleartext HTTP is permitted **only** for private/LAN addresses (via a scoped network-security-config), never globally.
   - The agent must degrade gracefully: if the selected backend is unavailable or misconfigured, surface a clear status and never crash the AccessibilityService or the agent loop.
