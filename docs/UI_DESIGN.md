# Marmaton UI — "Ion Violet" Design Spec

The approved visual direction for the Marmaton app. Implement in Jetpack Compose + Material 3.
Dark is the default theme; a full light theme is required. This spec is the source of truth for
layout, states, tokens, and the Compose mapping. It is the front-end for the pluggable
`LlmBackend` layer (see `docs/LLM_BACKENDS.md`) — the Backends and onboarding screens bind to the
same DataStore-backed settings and `LlmBackend`/`AgentReasoner` abstraction, not a parallel one.

## Design tokens

### Color roles — Dark (default)
| Role | Hex |
|------|-----|
| background | `#08080D` |
| surfaceContainer | `#181824` |
| primary | `#A7A3FF` |
| primaryContainer | `#2C2A52` |
| error | `#93000A` |
| onErrorContainer | `#FFDAD6` |
| successContainer | `#123420` |
| warnContainer | `#3A2E10` |
| text (muted / strong) | `#9A98AE` / `#E4E3EE` |

### Color roles — Light
| Role | Hex |
|------|-----|
| background | `#FAF9FF` |
| surfaceContainer | `#F1EEFB` |
| primary | `#5B54C7` |
| primaryContainer | `#E4DFFF` |
| error | `#BA1A1A` |
| errorContainer | `#FFDAD6` |
| successContainer | `#D7F2DE` |
| warnContainer | `#FFE8C2` |

### Type scale
| Token | Size/line, weight | Use |
|-------|-------------------|-----|
| display | 28/36 700 | hero headlines |
| headline | 22/28 700 | screen/section titles |
| title | 17/24 600 | card titles |
| body | 16/24 400 | primary copy |
| body-sm | 14/20 400 | secondary copy |
| label | 13/18 500 | chips, buttons |
| mono | 12–13/18 (JetBrains Mono / `FontFamily.Monospace`) | technical values, logs |

### Spacing / radii / targets
- Spacing scale: `4 · 8 · 12 · 16 · 20 · 24 · 32 · 40`.
- Radii: sm `12` · md `16` · lg `20` · xl `24` · pill `100`.
- Min touch target `56.dp`; agent action buttons `56–64.dp`.

### Compose / Material 3 mapping
- Cards → `ElevatedCard`, `RoundedCornerShape(20–24.dp)`.
- Status chip → `AssistChip` or `Surface`+`Row`; status dot = `Box(CircleShape)`; pulsing dot via
  `rememberInfiniteTransition` + alpha.
- Start / Run → `Button`, `ButtonDefaults.buttonColors(primary)`.
- Emergency Stop → custom composable using `colorScheme.error`, pinned in the `Scaffold`
  `bottomBar` whenever the agent is running.
- Bottom nav → `NavigationBar` + `NavigationBarItem` (Home · Activity · Backends); badge via
  `BadgedBox`.
- Timeline → `LazyColumn` of step cards; current step border animates via `animateColorAsState`.
- Timeline/Raw toggle → `SegmentedButton`.
- Log text → `Text(fontFamily = FontFamily.Monospace)`.
- Backend select → selectable `Card` + `RadioButton`.
- Host/Port/API-key → `OutlinedTextField`; API key uses `PasswordVisualTransformation`.
- Progress → `LinearProgressIndicator`.
- Onboarding → `HorizontalPager` + indicator, or step-based `NavHost`.

## Screens

### First-run onboarding (4 steps)
1. **Welcome & Safety** — "An autonomous agent that sees your screen and taps, types, and swipes
   for you." Three points: **Always visible** (you see every action before/as it happens),
   **One tap to stop** (Emergency Stop always on screen while running), **Local-first** (run
   fully on-device, or choose your own backend). CTA "Get started".
2. **Accessibility (BLOCKING)** — "Enable Accessibility". Explains the service is required;
   deep-link "Open Accessibility Settings"; status "Not enabled"; **Continue disabled until
   enabled**.
3. **Choose a backend** — "Where should reasoning run? You can change this any time." Three
   selectable cards: **On-device model** (fully offline, slower, most private), **Ollama on your
   network** (point at an IP + port on your LAN), **Cloud API** (fastest, requires an API key).
4. **Ready** — "You're all set." Checklist: Accessibility enabled · Backend ready. CTA "Start
   using Marmaton".

### Home / Control — 5 states (driven by real agent/service/backend state)
- **IDLE** — goal field ("e.g. Turn on Battery Saver") + Start; Backend status row (e.g. "Local
  model · ready"); Accessibility status row ("Connected · service on"); Recent runs.
- **RUNNING** — header "Running · m:ss" + STOP; current step card ("STEP 3 · TAP", target,
  action e.g. "Tapping 'Battery saver'"); mini step list; **persistent Emergency Stop bottom
  bar**.
- **FINISHED** — success card "Done — Battery Saver on"; "Completed in m:ss · N steps";
  verification line; steps with timestamps; "Run again" / "New objective".
- **ERROR** — error card "Couldn't find toggle"; "Stopped after step N · m:ss"; expected/found
  detail ("expected: toggle 'Battery saver'", "found: no matching element after 3 attempts");
  "View details" / "Retry".
- **BLOCKING · Accessibility off** — "Blocked" card, "Marmaton can't act without it. Re-enable to
  continue.", "Open Accessibility Settings".

### Live Activity — Timeline (default) + Raw log
- **Timeline (default)** — running header; step cards with timestamps and reasoning/sees lines
  (e.g. "sees: toggle 'Battery saver', state=off · reasoning: this is the control the goal
  requires"); current step highlighted; Emergency Stop bar.
- **Raw (power user)** — monospaced, color-coded log reusing existing `[Parser]`/`[Gemma]`/
  `[Action]` lines and the run-log `StateFlow`; Emergency Stop bar.

### Backends — list + detail
- **List** — On-device model (**ACTIVE**, "gemma-3n-e4b · 2.1 GB", ready) / Ollama (LAN)
  ("192.168.1.42:11434 · llama3.1", unreachable) / Cloud API ("Not configured"). Each with a
  status chip.
- **Local · downloading** — "Downloading gemma-3n-e4b", "1.3 GB / 2.1 GB · 64%", "Cancel
  download"; "Model file" row ("gemma-3n-e4b-it-int4.task") + "Choose a different file…" (SAF
  picker).
- **Ollama · unreachable** — Host/IP, Port (11434), Model (llama3.1) fields; error banner
  "Connection timed out. Check the server is running and on the same network."; "Test
  connection".
- **Cloud · ready** — Endpoint ("api.anthropic.com/v1"), API key (masked, "••••••••7f3a"), Model
  ("claude-4.5-sonnet"); privacy note: **"Goals and screen text are sent to this endpoint.
  Screenshots stay on-device."**

## Behavior & integration rules
- Replace the current single `DashboardScreen`; keep `MainActivity` as entry point. Use a
  `NavHost`: Onboarding → main (Home / Activity / Backends) → detail screens.
- Bind to existing state: `AgentForegroundService.isRunning` / `userGoal` / `runLog`, the
  accessibility-enabled check, and the selected `LlmBackend.status()`.
- **Start** is enabled only when accessibility is on **and** the selected backend status is
  `Ready`. **Emergency Stop** uses the existing stop path and is always reachable while running.
- Honor all `AGENTS.md` directives (thread safety, no node-recycling leaks, minimal
  serialization, separation of concerns).
- UI accessibility: WCAG AA contrast in both themes, content descriptions, visible focus,
  `56.dp` targets, and respect reduced-motion for animated/pulsing elements. User-facing strings
  live in `strings.xml`.
