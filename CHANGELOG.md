# Changelog

All notable changes to Wellness Companion are documented here.

## [1.1.0] - 2026-08-12

### Added — sync is now authenticated

- **Pairing code.** The desktop shows an eight-character code in its sidebar and refuses every
  request — reads *and* writes — until a client presents it. The phone refuses to transmit anything
  until a code is stored. Five wrong attempts drop the connection. Why: the sync server previously
  accepted any connection on the LAN, so any device on the network could pull the entire database
  (cycle, health, emotions, bathroom entries) or push rows into it, and the phone volunteered its
  whole database to whatever answered the mDNS query. How to use: open the desktop app, read the
  code from the sidebar, enter it once on the phone under 🔄 Sync → Pair.
  Affects: `windows/src/main/sync-server.ts`, `android/.../sync/SyncManager.kt`,
  `android/.../sync/SyncViewModel.kt`, `android/.../ui/dashboard/DashboardScreen.kt`.
- **Incremental sync.** Only entries changed since the last successful sync are sent, capped at
  2000 per run, and `full_sync` takes a `since` cursor. Why: both sides previously serialised the
  entire history on every sync, so peak memory grew without bound as the log filled.
- Connection limits, a 16 MB frame cap, a ping/pong heartbeat that drops half-open sockets, and
  schema validation on every incoming entry (known category, date shape, bounded payload).

### Changed — the installer

- Replaced the electron-builder/NSIS installer with an Inno Setup 6 installer under `installer\`.
  Why: the NSIS template owned the wizard and only exposed a macro hook, so opt-in task checkboxes,
  custom pages and a bundled phone-app install were not reachable. electron-builder is now a `dir`
  target that only emits `dist\win-unpacked\`; `wellness_setup.iss` wraps it.
  How to use: `installer\build_installer.bat` (lean) or `installer\build_installer.bat offline`.
  Affects: `installer\*`, `build_setup.bat`, `windows\package.json`.
- The installer is now a single self-contained file. The VC++ redistributable and the Android APK
  are embedded, so there is no longer a "ship these two files together" step.
- Installer artifacts are version-stamped: `Wellness Companion Setup <version>.exe` plus a matching
  `Wellness Companion <version> README.txt`, both in `installer\output\`. Why: `WellnessCompanion-Setup.exe`
  made two builds indistinguishable on disk. The version comes from `windows\package.json` and is
  passed to Inno Setup as `/DAppVersion`, so there is one source of truth.
- Lean/offline is now one compile-time flag (`/DINCLUDE_MODEL`) on a single script, replacing the
  separate `electron-builder.offline.js` config.
- Model provisioning moved from inline NSIS into `installer\setup_model.ps1`, which writes a full
  transcript to `<install dir>\setup.log`. Why: the NSIS hook reported failures in a MessageBox that
  left no trace to diagnose afterwards. The four-tier resolution ladder and the byte-length
  corruption check were carried over unchanged.

### Added

- Wizard tasks: create a desktop shortcut, install the phone app now, and download the AI model now.
  The model download can be declined so an offline machine can finish setup and supply it later.
- The Android APK ships inside the installer and can be sideloaded during setup or later from the
  *Install the phone app* Start-Menu shortcut. `installer\install_phone_app.bat` resolves ADB from the
  Android SDK, then a cached copy, then downloads Google's platform-tools; it force-stops the app
  before `install -r` and grants `POST_NOTIFICATIONS` so reminders work on first run.
- Uninstall now closes a running instance first (`taskkill`), so the Electron exe, the native
  better-sqlite3/llama DLLs and port 9847 are released before files are removed.
- Authenticode code signing of both the app exe and the setup exe, using a self-signed certificate
  created on first build by `installer\make_signing_cert.ps1`. Signing is skipped with a warning when
  `signtool.exe` is absent, so the build still succeeds.
- `README.md`, and a build step that flattens it into the shipped version-stamped `README.txt`.
- `LICENSE` (PolyForm Noncommercial 1.0.0), `THIRD-PARTY-LICENSES.md` generated from the real
  dependency trees, `CONTRIBUTING.md`, `SECURITY.md`, issue/PR templates, and a daily GitHub Action
  that samples release download counts into a self-hosted chart and badges.
- Android release signing is wired to an untracked `android/keystore.properties`, with
  `isMinifyEnabled`/`isShrinkResources` on for release builds. Until a keystore exists the installer
  keeps shipping the debug APK and prints a build warning — see the note under Known limitations.

### Security

- **Removed the automatic firewall rule.** The app used to run `netsh advfirewall firewall add rule`
  at every startup, with no profile restriction, opening port 9847 on *all* network profiles
  including public and guest Wi-Fi, without ever asking. Windows raises its own prompt on first
  bind; that prompt is the consent. Affects: `windows\src\main\sync-server.ts`.
- **Electron hardening.** The renderer now runs sandboxed under a strict Content-Security-Policy,
  navigation is pinned to the packaged app (`will-navigate`), webviews are refused, and
  `shell.openExternal` only accepts `http:`/`https:` — previously any URL reached the OS protocol
  handlers, which matters because entry data arrives over the LAN.
- **The app no longer makes any outbound request at runtime.** Nunito was being fetched from
  `fonts.googleapis.com` on every launch, contradicting the project's own no-telemetry claim. The
  latin and latin-ext subsets are vendored under `windows/src/renderer/src/assets/fonts/` with their
  OFL licence.
- **Android backups disabled.** `allowBackup=false` plus explicit backup and data-extraction rules,
  because the Room database holds cycle, health and mood data and was extractable via `adb backup`
  and copied into cloud backup.
- **Model integrity.** The 2.5 GB download is verified against a pinned SHA-256 taken from Hugging
  Face's published LFS digest, not just a byte count — `curl` follows redirects, so a substituted
  file of identical size would previously have been accepted and loaded.
- The auxiliary-table SQL helper now uses a fixed allowlist of table and column names instead of
  interpolating caller-supplied strings, and the sync server reuses one database connection rather
  than opening a fresh handle per inbound message.

### Fixed

- The AI model was installed where the app never looked. Cause: the installer wrote to
  `%LOCALAPPDATA%\wellness-companion\model\`, but `getModelPath()` checked `app.getPath('userData')`,
  which resolves to `%APPDATA%` (Roaming) on Windows — so a lean install downloaded 2.5 GB that was
  then reported as missing. Fix: `%LOCALAPPDATA%` is now the first candidate, with the Roaming path
  kept as a legacy fallback for existing installs. Affects: `windows\src\main\llm.ts`.
- Uninstalling deleted the 2.5 GB model. Cause: `customUnInstall` ran `RMDir /r` on the model folder,
  forcing a full re-download after any reinstall. Fix: uninstall removes only install-generated
  content; the model and `wellness.db` are deliberately preserved.
- Broken image in the sidebar header. Cause: `<img src="./public/favicon.png">` is a runtime string
  Vite does not rewrite, and `publicDir` is copied to the output root — so `public/` does not exist in
  the build. Fix: reference `./favicon.png` in `Sidebar.tsx` and `index.html`.
- Fourteen Kotlin data classes were missing from version control. Cause: an unanchored `model/`
  rule in `.gitignore` — written for the 2.5 GB GGUF folder at the repo root — also matched
  `android/.../data/model/`, so a fresh clone could not compile. Fix: anchored to `/model/` (and
  `/logs/`) and added the files.
- Saving a chore rewrote the same database row indefinitely. Cause: `return@collect` returns from
  the collector lambda, not from `collect`, so the Room Flow stayed subscribed — and because
  `updateEntry` writes to that table, Room re-emitted and the collector wrote again, in a loop. Each
  call also leaked a suspended coroutine. Fix: a one-shot `.first()` read.
  Affects: `android/.../ui/chores/ChoresViewModel.kt`.
- A failed model load wedged the Insights feature permanently. Cause: `loadModel()` guarded on
  `model` alone, so if `createContext()` threw, `model` stayed assigned with `context` null and every
  retry short-circuited while the multi-GB allocation stayed resident. Fix: guard on both, and
  release the whole runtime on failure. Affects: `windows\src\main\llm.ts`.
- Aux tables only ever synced phone → desktop. Cause: the phone processed only `entries` from
  `full_sync_response` and silently dropped `hobbies`, `people` and `chore_templates`, so a freshly
  installed phone showed hobby entries with an empty hobby list. Fix: ingest all three.
  Affects: `android/.../sync/SyncManager.kt`.
- Memory and lifetime fixes across both apps: the LLM session and its 4096-token KV cache are now
  released when generation ends rather than at the start of the next one; `before-quit` no longer
  loses its teardown to Electron's non-awaited async listeners; the sync socket is cancelled when
  the coroutine is; a raw `Thread { sleep(5000) }` discovery timeout that outlived cancellation was
  replaced with `withTimeoutOrNull`; `SyncManager` and `OkHttpClient` are singletons instead of one
  per ViewModel; and NSD callback races are guarded with `AtomicBoolean`.

### Fixed — building from source

Building from a clean clone by following the README did not work. Found by doing exactly that:

- The README said `npm install`, but the repo ships `pnpm-lock.yaml`; npm ignores it and, on
  Python 3.12+, its bundled node-gyp 9 aborts with `No module named 'distutils'` (removed from the
  standard library in 3.12). Measured: `npm install` exit 1, `pnpm install` exit 0.
- `pnpm-workspace.yaml` left every `allowBuilds` entry at pnpm's placeholder text
  `"set this to true or false"` — not a boolean, so native build scripts were skipped.
- `installer\build_installer.bat` called `gradlew.bat` by bare name after `pushd`, which does not
  resolve. That branch only runs when the APK is absent, so every incremental build printed "APK
  already present" and skipped it — it would have failed for everyone cloning the repo.
- `packageManager` and `engines` are now declared, and the README documents that a deeply-nested
  clone breaks the installer step (Inno Setup is not manifested for long paths).

### Changed — AI insight prompts

The weekly portrait asked for warmth four times ("gentle", "supportive", "warm", "encouraging") and
for substance once, so the model produced praise rather than observation. The rewritten prompts keep
the warmth — this is someone reading about her own body — and make specificity the way it is
expressed: cite real numbers and dates, name a hard day plainly instead of reframing it, surface one
cross-category connection, and close with a single concrete suggestion drawn from an observed
pattern. Affects: `windows/src/renderer/src/lib/prompts.ts`.

### Known limitations

- Sync traffic is unencrypted `ws://` on the local network, and neither database is encrypted at
  rest. The pairing code is access control, not confidentiality.
- The shipped phone build is **debug-signed** unless a release keystore is supplied, so anyone with
  USB access to an unlocked device can read the app's data directory.

Both are documented in [SECURITY.md](SECURITY.md).

## [1.0.0] - 2026-06-16

Initial release: Jetpack Compose Android app and Electron desktop hub with local-network mDNS sync
and offline AI insights.
