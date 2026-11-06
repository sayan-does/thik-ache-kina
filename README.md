# 📱 Agent Remote — CLI Coding Agent Controller for Android

An industrial-grade, 100% zero-budget native Android application and host daemon to monitor, triage, and control CLI coding agents (**AGY/Antigravity, OpenCode, and Claude Code**) running inside Windows PowerShell from anywhere over the internet.

Designed adhering to:
- **`@multi-agent-brainstorming`**: Peer-reviewed and hardened across 12 architectural decisions (`D-01` through `D-12`).
- **`design-taste-frontend`**: Anti-slop, Figma/x.ai quality bar, pitch-dark aesthetic, and ergonomic split-thumb mobile layout.
- **Permanent Zero Budget**: Built entirely on free tools (Tailscale Free Mesh, ntfy.sh Open-Source Push, Android Sideloading, GitHub Actions CI/CD).

---

## 🏗️ Architecture

```
┌─────────────────────────────────┐           ┌───────────────────────────────────┐
│     💻 Windows Host Laptop      │           │        📱 Android Client          │
│                                 │           │                                   │
│  CLI Agent (AGY/Claude/OpenCode)│           │  Triage Action Card (3s Decisions)│
│               ↕                 │           │  Live Status Pill (Pulse Indicator│
│  pywinpty ConPTY (2D Grid)      │           │  xterm.js Viewport (Full Colors)  │
│               ↕                 │           │  Split-Thumb Toolbar (▲/▼, ⎵/⏎)   │
│  bridge.py Host Daemon          │           │  Protected Ctrl+C (400ms Latch)   │
│  - Win32 Keep-Awake Lock        │           └─────────────────┬─────────────────┘
│  - 256-bit Bearer Token Gated   │                             │
│  - CSWSH Origin Defense         │                             │
│  - 1,000-Line Replay Buffer     │◄── Authenticated WebSocket ─┘
│  - ntfy.sh UUID Push Dispatcher │    over Encrypted Tailscale WireGuard
└────────────────┬────────────────┘    (ws://100.x.y.z:8765/ws)
                 │
                 ▼
       ntfy.sh Cloud Relay (Opaque Metadata Downlink)
                 │
                 ▼ (Instant Wakeup Push)
       Android Notification ──► Deep Link into Session
```

---

## 🚀 Quick Start Guide

### 1. Prerequisites (All Free)
1. **Tailscale**: Install Tailscale on your Windows laptop and on your Android phone (free personal account). They will connect securely on the same virtual network (`100.x.y.z`).
2. **Python 3.10+**: Available on your Windows laptop.

### 2. Start the Host Daemon
On your laptop, navigate to `agent-bridge/` and launch the bridge wrapping your desired CLI agent:

```powershell
cd agent-bridge
pip install -r requirements.txt

# Run wrapping AGY
python bridge.py -- agy "refactor login system"

# Or run wrapping OpenCode
python bridge.py -- opencode

# Or run wrapping Claude Code
python bridge.py -- claude
```

The daemon will print your connection parameters:
```
=================================================================
  🤖 CLI AGENT REMOTE CONTROL - BRIDGE DAEMON ACTIVE
=================================================================
  Session ID : sess-8f3e2b1a
  Command    : agy
  WebSocket  : ws://localhost:8765/ws
  Auth Token : a4f18c2d9e... (256-bit token)
  ntfy Topic : agent-remote-b3d9... (https://ntfy.sh/agent-remote-b3d9...)
=================================================================
```

> **Note on Windows Sleep:** The bridge automatically invokes Win32 `SetThreadExecutionState` on startup. You can close your laptop lid, and the process will continue running and streaming.

---

### 3. Automated APK Build via GitHub Actions
You do not need to install Android Studio or configure heavy build tools locally. The repository includes a GitHub Actions CI/CD workflow (`.github/workflows/android-build.yml`):

1. Push this repository to your GitHub account (or fork it).
2. Go to the **Actions** tab on GitHub.
3. Select **Build Android APK** and click **Run workflow** (or simply push a commit to `main`).
4. Once completed (approx. 2-3 minutes), download `agent-remote-debug-apk` from the **Artifacts** section directly on your phone and install it.
5. If you push a tag (e.g. `git tag v1.0.0 && git push --tags`), GitHub Actions automatically publishes a GitHub Release with the APK attached.

---

### 4. Connect Your Phone
1. Open **Agent Remote** on your phone.
2. Tap the **Settings ⚙️** icon in the top right.
3. Enter:
   - **Laptop Tailscale IP**: Your laptop's `100.x.y.z` address shown in your Tailscale app.
   - **Port**: `8765`
   - **Auth Token**: The token printed in your terminal or saved in `agent-bridge/config.yaml`.
4. Tap **Save & Connect**.
5. Tap **Open Terminal Control →** to view your live terminal, respond to prompts, and send commands.

---

## 🧪 Testing & Verification

Run the automated test suite for the host bridge daemon:

```powershell
cd agent-bridge
python -m pytest tests
```

Tests verify:
- `test_ring_buffer_capacity`: Circular replay buffer retention.
- `test_ring_buffer_state_transitions`: Reconnect state machine.
- `test_ansi_stripping`: Escape sequence cleanup.
- `test_prompt_detection_numbered`: AGY numbered options `[1] [2]`.
- `test_prompt_detection_yn_confirm`: Binary `(y/N)` confirmation.
- `test_prompt_detection_ink_question`: Claude Code / OpenCode Ink.js prompts.
- `test_cswsh_browser_origin_rejection`: Cross-Site WebSocket Hijacking defense.
- `test_unauthorized_token_rejection`: 401 on missing or incorrect Bearer token.
- `test_valid_token_connection`: Instant snapshot hydration.

---

## 🔒 Security & Privacy

1. **Zero-Trust Token Gating**: The WebSocket server strictly requires a 256-bit CSPRNG Bearer Token on handshake. Unauthenticated connection attempts are immediately dropped with HTTP 401.
2. **CSWSH Defense**: Any connection containing a browser `Origin` header (such as a malicious website attempting to hijack localhost from a browser tab) is rejected with HTTP 403.
3. **Privacy on Push**: Notifications sent via public `ntfy.sh` relays contain strictly opaque metadata (event type, session ID, timestamp). No proprietary code, diffs, API keys, or directory structures are ever transmitted through third-party relays.
