# SMS bridge design (fork-only)

This fork adds SMS send/receive to Hermes Mobile. Rule: **every line of SMS code lives in one
new package**; upstream files get at most the two contact points listed below so `git merge
upstream/main` stays near-zero-conflict. Reference for SMS mechanics: OpenClaw's
`apps/android/.../thirdParty/SmsManager.kt` (MIT) — ContentResolver queries, multipart send,
permission gating. OpenClaw is pull-only (`sms.search`); inbound push is ours.

## Transport decision

**Yes: the product architecture is the OpenClaw approach.** SMS is executed on the Android phone
itself through native `SmsManager`/`ContentResolver`; the agent gets explicit SMS tools and requests
work from the paired phone. We are not routing texts through sms-gate.app or a third-party cloud
relay. OpenClaw's source is our reference for the phone-side manager, permission checks, query
semantics, and command/tool boundary.

The Hermes integration still needs a small gateway plugin: Hermes Mobile is currently a remote
client, not an OpenClaw-style node that can advertise `sms.send` / `sms.search` directly to the
agent. So `hermes-sms-bridge` is the *thin Hermes-side adapter* that exposes those tools, pairs a
phone, queues outbound requests, and receives inbound events/status. The Android app polls/acks
that queue over an authenticated direct connection. It does not implement SMS itself on the server.

The existing push transport (`push/PushClient.kt` → `/p/<profile>/api/platforms/hermes_android/events`)
has a **closed RPC allowlist** server-side (`hermes_android/adapter.py::_dispatch_http_event`):
unknown `type` → 404. We do not extend or fork that plugin; the bridge has its own endpoint, RPC
allowlist, and bearer secret. Use the same versioned envelope style:
`{"ok":true,"protocol_version":1,"result":{...}}`.

This is plugin + native-node execution, not a cloud SMS relay. It keeps texts on the device and
works on a reachable private network using the app's existing HTTPS-or-private-host URL policy.

## Phone side (this repo)

New package `com.nousresearch.hermesmobile.sms`:

| File | Responsibility |
|---|---|
| `SmsBridge.kt` | Module init: registers receiver, starts poller/work, owns lifecycle. Called once from `HermesApplication.onCreate`. |
| `SmsTransport.kt` | HTTP client to the gateway plugin endpoint. Mirrors `PushClient` shape (OkHttp, bearer secret, no-redirect, size cap, `GatewayUrlPolicy` check). |
| `SmsStore.kt` | Per-connection settings + device creds in `GatewayConfigStore.readPrivate/writePrivate` (same encrypted store `PushStore` uses). |
| `SmsPairing.kt` | Pair with gateway: reuse the `command.dispatch` slash-command pattern from `push/PushSetup.kt` (`/sms-pair`, `/sms-credentials`). |
| `InboundWatcher.kt` | `BroadcastReceiver` on `SMS_RECEIVED_ACTION` (requires default-sms-app or the permission tradeoff — see Security) + ContentResolver reconciliation poll so nothing is missed while asleep. Dedup by `_id`. |
| `SmsSender.kt` | Outbound queue → `SmsManager` multipart send + delivery intents; reports status back. Ported/adapted from OpenClaw `SmsManager.kt` send paths. |
| `SmsSettingsScreen.kt` | Compose screen built from `HermesPrimitives` (`HermesTopBar`, `FlatRow`, `StatusPill`, ...). |

**Upstream contact points (the only edits to upstream files, per section):**
1. `HermesApplication.kt` — one line: `com.nousresearch.hermesmobile.sms.SmsBridge.init(this)` at the end of `onCreate`.
2. `HermesViewModel.kt` + `HermesMobileApp.kt` + `HermesDrawer.kt` — nav registration: add `MainSection.SMS` to the enum, one `DrawerItem` line, one `when` arm. ~3 lines, all additive.

Manifest: SMS entries (`RECEIVE_SMS`, `SEND_SMS`, `READ_SMS`, receiver declaration) live in
`app/src/sms/AndroidManifest.xml` under an **`sms` product flavor dimension** so upstream-style
`debug`/`release` builds with the flavor off are byte-identical in permissions. (Check: upstream
has no flavors today — we add a `legacy` no-op flavor so CI/flavor graph stays honest. If
upstream ever adds flavors this is the one seam that may conflict; accept it.)

Strings: `values/strings_sms.xml` + `values-es/strings_sms.xml`, real names (`sms_*`) — the opaque
key convention exists to protect generated files we never touch; new hand-written keys use plain
names and are outside `verify_source.py`'s checks.

## Server side (new repo: hermes-sms-bridge plugin)

Hermes gateway plugin, `kind: platform`, structured after hermes-android-plugin:
- `adapter.py` — `SmsBridgeAdapter`: registers `dispatch_http_event` ops: `device.register`,
  `sms.inbound` (phone → agent), `sms.status` (delivery receipts), `sms.outbound.fetch`
  (phone pulls queued sends), `sms.ack`.
- `tools.py` — agent tools `sms_send` (to E.164 number) and `sms_search` (recent threads), plus
  slash commands `/sms-pair`, `/sms-credentials`, `/sms-test`.
- `store.py` — SQLite: devices, queued outbound, inbound log with retention.
- Security model copied from OpenClaw's insight: **incoming SMS are tool results (data), never
  operator commands**; untrusted third parties can't escalate via reply text.

## Security notes

- Pairing secret ≥32 chars, dedicated per plugin, `Authorization: Bearer`, never through chat
  attachments (same rule as AGENTS.md: `@file:` expansion).
- `SMS_RECEIVED_ACTION` delivery broadcast requires `RECEIVE_SMS`; being default SMS app grants
  it + read but takes over Messages. MVP: request `RECEIVE_SMS` + `SEND_SMS` directly (Play
  policies don't apply — sideloaded APK), reconciliation poll over `content://sms` with `READ_SMS`
  kept optional.
- Outbound send respects the gateway's existing approval surface: plugin marks `sms_send` as a
  tool that can require approval (approval event already flows to this app via push).

## Build & CI

- Local build here needs `ANDROID_HOME` (see repo AGENTS.md; this machine bootstraps under `~/.local`).
- Our CI: copy upstream workflow, add a `smsDebug` flavor job; keep upstream `verify_source.py` green (we add no generated-file churn).
- Upstream sync: `git fetch upstream && git merge upstream/main` on `sms-bridge`; never rebase.
