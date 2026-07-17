# Marmaton — Privacy Policy (DRAFT)

_Last updated: 2026-07-17 · This is a draft. Have it reviewed by a qualified professional before
publishing; it is not legal advice._

Marmaton is an autonomous on-device assistant. It uses Android's Accessibility service to read
what is on your screen and to tap, type, and swipe on your behalf to complete goals you give it.
This policy explains what Marmaton does and does not do with your data.

## The short version
- **Your screen content and goals are processed to run the agent, and by default stay on your
  device.** Marmaton is local-first.
- **You choose where reasoning runs.** If you point Marmaton at a remote or cloud backend, the
  text it needs is sent to that server — which you selected.
- **Usage analytics are optional and off by default.** If you turn them on, we collect only
  anonymous, non-identifying information about which kind of backend and model you use.
- **We never sell your data and there are no ads.**

## What Marmaton processes to work
To act on your behalf, Marmaton reads the on-screen UI (text, element descriptions, and layout)
and the goals you type. This is required for the core feature. Screenshots/pixel captures are not
transmitted off the device.

Where this data goes depends on the **backend** you choose:

- **On-device model (default).** Reasoning runs entirely on your phone. Your goals and screen
  content are **not sent anywhere**.
- **Ollama on your network.** Your goals and a minimized text description of the current screen
  are sent over your local network to the server you configured (your own machine). We do not
  operate that server and do not receive that data.
- **Cloud API.** Your goals and a minimized text description of the current screen are sent to
  the third-party endpoint you configured (for example, your chosen model provider). That
  provider's own privacy policy governs what they do with it. Your API key is stored on your
  device and is used only to authenticate to that endpoint.

## Optional anonymous usage analytics
If — and only if — you enable **"Share anonymous usage data"** (off by default), Marmaton sends a
small amount of anonymous product-analytics data to help us understand how the app is used. This
uses **PostHog**.

**What is collected (only when enabled):**
- A random, app-generated identifier that is not linked to you, your device, or any account.
- App version.
- Which backend type is active: on-device, Ollama, or cloud.
- The model name/family in use (e.g. `gemma-3n-e4b`, `llama3.1`, `claude-4.5-sonnet`), and for
  cloud backends the provider host (e.g. `api.anthropic.com`).
- Basic run outcomes (success/error, step count, duration).

**What is never collected:**
- Your API keys.
- Your goals/prompts or any screen content.
- The agent's run log or any accessibility data.
- Your local network (Ollama) IP address or server address.
- Your precise location or any device/advertising identifiers.

You can turn analytics off at any time in **Settings → Privacy & data**. Turning it off stops all
collection immediately and resets the anonymous identifier.

## Data we do not collect
No ads, no data sales, no cross-app tracking, no advertising IDs, no contact/location harvesting.

## Permissions
- **Accessibility service** — required to read the screen and perform actions on your behalf.
  Marmaton uses it only to run the agent for the goals you give it.
- **Internet** — used only for the backend you configure (Ollama/cloud) and, if enabled, for
  anonymous analytics. The on-device backend needs no network access.

## Your choices
- Choose an on-device backend to keep everything local.
- Leave analytics off (the default), or turn it off any time.
- Disable the Accessibility service in Android Settings to stop Marmaton from acting.

## Contact
Questions about this policy: <add contact email>.
