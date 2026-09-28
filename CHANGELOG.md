# Changelog

All notable changes to OpenFluxAndroid. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- Clients and nodes built from different trees now connect: bumps `OpenFlux`
  to [`f8f3476`](https://github.com/p1neappleXpress/OpenFlux/commit/f8f34767a5732febd5965ac6cf95bad51b70cf99).
  - A classic profile with a key runs the Session and falls back to the
    classic layering for a classic or older node, on the same carrier; a
    node set up as classic serves both kinds of client. Nodes set up for
    the Session stay Session-only.
  - The encryption context follows one rule everywhere, and a client whose
    context differs from the node's finds the node's instead of timing out
    (classic cups.online was the common case).
  - The classic codec (batched or legacy) is no longer a hard requirement:
    both are accepted and the client switches when the node does not answer.
  - Share links are read the same way as on every client: wrapped lines,
    padding and the standard base64 alphabet are accepted, and the key
    length is counted in characters.
  - boards no longer drops the connection every 20 seconds; yandex and
    mailru reconnect when their socket dies.
  - The log explains a failed handshake: wrong key, a node in the other
    mode, the codec or context picked, a connection taken over by another
    client.

## [2.0.1] - 2026-09-27

### Fixed

- An exit node deployed by the node wizard never actually connected: a
  `.conf`-only `Role = exit` (every node-wizard deployment) left the core's
  internal exit/client flag stuck at its pre-config value, so the exit
  never answered the handshake and crash-looped instead. Bumps `OpenFlux`
  to [`e8f735a`](https://github.com/p1neappleXpress/OpenFlux/commit/e8f735a98c1ba9e091956416fde5c5ef92d3cd66).
- The startup log always printed `Transport: yandex` for session/multi-transport
  profiles regardless of which transports were actually configured (a stale
  flag default, not a functional bug — the correct transports ran either
  way). Now prints the actual list, e.g. `Transport: boards, direct (session)`.
- Cups.online profiles with no room codes couldn't be saved or connected;
  the node generates its own rooms, so an empty value is valid for this
  transport only.
- An exit node always listened for Direct (TCP on `0.0.0.0:<port>`) and put
  it into the clients' link, even when the profile had no Direct transport.
  It now listens only when the profile has Direct, at that transport's
  priority ([#11](https://github.com/p1neappleXpress/OpenFluxAndroid/pull/11)).
- A phone exit on cups.online without room codes put cups.online into its
  link with no rooms, so clients could not join; the link now carries the
  rooms the node created ([OpenFlux#123](https://github.com/p1neappleXpress/OpenFlux/pull/123)).

### Added

- Settings → Ядро OpenFlux: a core log-level picker (Выкл / -d / -dd /
  -ddd, the core's `--debug=N`) in place of the "Подробный журнал ядра"
  switch. The embedded core used to run at -dd on every connect whatever
  the switch said — formatting a log line for every packet — and the
  switch only hid those lines from the log view; the level now reaches the
  core itself ([OpenFlux#121](https://github.com/p1neappleXpress/OpenFlux/pull/121)).
  The default is Выкл: choose -dd to see transport errors, handshakes and
  the encryption (KDF) context.
- Node-wizard deployment logging: every SSH/RPC call and the wizard's own
  step narration now goes to the Logs tab, so a stuck deployment is
  diagnosable without a debugger.
- Home → Подключение: a "Сейчас через" row for profiles with several
  transports, and carriers named as in the app ("Board 2", not `boards-2`)
  there and in the "через …" badge.

## [2.0.0] - 2026-09-27

First release of this app. Replaces the previous single-transport native
app (tun2socks + pdnsd JNI, preserved at the
[`legacy-native-app`](../../tree/legacy-native-app) tag) with the Compose
Multiplatform app originally built by [@meepo161](https://github.com/meepo161)
in [OpenFluxClient](https://github.com/meepo161/OpenFluxClient), moved here
with his agreement.

### Added

- System VPN or local SOCKS5.
- Multi-transport sessions with automatic failover and priority-based
  switching (including `direct`).
- AES-256-GCM session encryption.
- SmartCaptcha and login handling in a built-in browser, including a
  transport check on the exit node, passed through the tunnel from its
  own address.
- A node-deployment wizard: install an exit node on your own VPS over SSH
  from the app.
- `shared/` and `OpenFlux/` as git submodules ([OpenFluxClientShared](https://github.com/p1neappleXpress/OpenFluxClientShared)
  and the [OpenFlux](https://github.com/p1neappleXpress/OpenFlux) core), so
  this app always builds against one pinned, single copy of each instead of
  a vendored one.
- `.github/workflows/release.yml`: a `v*` tag builds and signs a release
  APK and publishes it here.

### Credits

- [@meepo161](https://github.com/meepo161) — this app's UI and logic.
- [@p1neappleXpress](https://github.com/p1neappleXpress) — the OpenFlux
  core it embeds.
- [@damnurmum](https://github.com/damnurmum) — `openflux://` links/QR codes
  and the cups.online transport in the core, which this app's share and
  scan screens build on.
