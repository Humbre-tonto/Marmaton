# Project Context
This repository contains a fully autonomous Android device agent. The application relies entirely on on-device LLM inference (Gemma 4) to parse screen states, reason about the user's goal, and execute system-wide actions autonomously. No cloud LLM APIs are used for the core reasoning loop.

# Tech Stack & Standards
- **Language**: Kotlin.
- **UI Framework**: Jetpack Compose (Material 3).
- **Asynchronous Execution**: Kotlin Coroutines. 
- **LLM Integration**: Google AI Edge / ML Kit for local Gemma 4 execution.

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
