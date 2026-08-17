# Changelog

All notable changes to Wellness Companion are documented here.

## [1.3.1] - 2026-08-17

A maintenance release. The desktop build packages its local model runtime again, the settings
channel can no longer be used to reach sync credentials, and the desktop runtime moves to a Chromium
line that still receives security backports. No new features, no data migration, no re-pairing.

### Fixed

- **The desktop build shipped a local model that could not load.** Cause: electron-builder 26
  (pulled in by the Electron 43 bump below) reads the `packageManager` field, discards it, and
  re-derives the answer from a `pnpm --workspace-root exec pwd` probe. Under `cmd`/PowerShell `pwd`
  is not a command, the probe fails, and the correct answer survives; under any POSIX shell it is a
  builtin, the probe returns an MSYS path (`/c/...`) that Node cannot resolve a `package.json` at,
  and the builder falls back to guessing from `npm_config_user_agent` — which says npm whenever the
  build was launched through `npx`, as `installer\build_installer.bat` does. An npm-shaped walk of a
  pnpm virtual store never reaches the `@node-llama-cpp/<platform>` packages that sit beside
  `node-llama-cpp` inside `.pnpm/`, so they were dropped from the production dependency graph and
  the packaged app shipped only the `bins/*.moved.txt` placeholders: local AI generation could not
  work, and the installer came out 175 MB short. Every installer built between the builder bump and
  this fix is affected. Fix: `nodeLinker: hoisted` gives every collector a layout it understands, so
  packaging no longer depends on which shell started the build; the setting lives in
  `pnpm-workspace.yaml` because pnpm 11 no longer reads `node-linker` from `.npmrc`. Affects:
  `windows/pnpm-workspace.yaml`.
- **Sync credentials were reachable over the settings IPC.** Cause: `db:getSetting`/`db:setSetting`
  accepted any renderer-supplied key, including the `sync.*` keys that hold device pairing secrets,
  pending pairings and pairing backoff state — a compromised renderer could exfiltrate or implant
  them directly without going near the `sync:*` channels. Fix: both handlers now take an allowlist
  of the exact keys the renderer legitimately uses, so `sync.*`, `app.last_run_version` and any
  future sensitive key fail closed by construction instead of depending on a denylist entry; a
  call-site survey confirmed the dedicated `sync:*` channels already cover every renderer need.
  Affects: `windows/src/main/database.ts`, new `windows/test/settings-ipc-guard.test.ts`.
- **Build and diagnostic tooling ran with more privilege than it needed.** Cause: the keyreader
  smoke tool ran caller-supplied SQL against any given database path with no read-only guard (and
  only honoured `--allow-write` when the flag trailed the other arguments);
  `make_third_party_licenses.py` used `subprocess(shell=True)` for a static command and let
  `shutil.which()` resolve `npx` from the current directory ahead of `PATH`; and
  `install_phone_app.bat` fetched Google's platform-tools zip with no integrity check. Fix:
  keyreader opens read-only unless `--allow-write` is passed, and filters that flag out of argv
  before positional parsing; the license generator drops `shell=True` and passes an argument list;
  the phone installer clears `ZIP_SHA256` before computing it so a caller-inherited value cannot
  pass verification unchecked, checks the computed hash against an optional
  `WC_PLATFORM_TOOLS_SHA256` override, and otherwise warns with the hash and how to pin it — the
  upstream URL is a rolling "latest" build, so a baked-in hash would go stale on the next
  platform-tools release. Affects: `tools/security-smoke/keyreader/main.js`,
  `installer/make_third_party_licenses.py`, `installer/install_phone_app.bat`.

### Changed

- **Electron 33.2.1 → 43.4.0 (Chromium 130 → 150, Node 24.18.1).** Why: Electron 33 no longer
  receives Chromium security backports, and the renderer parses sync JSON that arrives from the LAN.
  electron-builder 25.1.8 → 26.15.3 came with it out of necessity, not tidiness: 25.x pulls node-gyp
  9.4.1, whose vendored gyp imports Python `distutils` (removed in 3.12+), so the native rebuild
  against Electron 43 failed outright — Electron 33 had masked that by resolving a prebuilt binary
  instead of compiling. `better-sqlite3-multiple-ciphers` stays at 12.11.1: Node-API is forward
  compatible, so the NAPI-9 module loads on Electron 43's higher level, and the at-rest path was
  verified end to end against a real encrypted database (same entry count before and after, file
  byte-identical, key not rewrapped). Affects: `windows/package.json`, `windows/pnpm-lock.yaml`,
  `windows/pnpm-workspace.yaml`.
- **The packaged app no longer carries its own sources.** A `files` allowlist keeps `src/`, `test/`
  and the tsconfigs out of `app.asar`. Affects: `windows/package.json`.
- **Documentation now matches the shipped state.** The README leads with the health-data notice, the
  self-signed-installer disclosure and download-verification steps, and says how Gradle locates the
  Android SDK; `docs/PROJECT_SPEC.md` no longer asserts the pre-1.3.0 plaintext-sync posture; and
  `THIRD-PARTY-LICENSES.md` was regenerated after SQLCipher was found to have no attribution.
  Affects: `README.md`, `SECURITY.md`, `docs/PROJECT_SPEC.md`, `THIRD-PARTY-LICENSES.md`,
  `CONTRIBUTING.md`, `tools/security-smoke/README.md`.

## [1.3.0] - 2026-08-15

The security release: sync traffic and both local databases are now encrypted, and the phone build
has a guided path to real release signing. **Upgrading users must re-pair their phone once.**

### Added

- **Encrypted, mutually authenticated LAN sync (`wc-sync/4`).** Every sync connection now runs a
  fresh P-256 ECDH key exchange, HKDF-SHA256, and AES-256-GCM record encryption, authenticated by a
  128-bit pairing secret that becomes a persisted per-device key; forward secrecy holds, so stealing
  a device key later does not decrypt sessions recorded earlier. Cause: v3 synced over plain `ws://`
  with an ~40-bit access code — anyone who captured one handshake could brute-force that code
  offline in minutes, and the traffic itself was never hidden from anyone on the LAN. Fix: the v3
  plaintext data path is physically deleted (a legacy peer gets a tombstone reply and zero rows
  move — there is no fallback to negotiate down to), and every application message after the
  handshake is a binary AES-256-GCM record. Affects: `windows/src/main/sync-server.ts`, new
  `windows/src/main/sync-crypto.ts`, `windows/src/main/database.ts`,
  `android/.../sync/SyncManager.kt`, new `android/.../sync/SyncCrypto.kt`.
- **Single-code device pairing, with per-device management.** Cause: the old code was a shared
  ~40-bit access password, typed the same way on every device, with no way to revoke just one
  phone short of changing the password for all of them. Fix: pairing now delivers one 33-character
  code (e.g. `CPR38-KAHBY-4EBBB-QPG9N-B4WFR-HXTY9-NNT`) that becomes that phone's own 128-bit key,
  and the desktop sidebar lists every paired device with a last-seen time and its own **Remove**,
  so revoking one phone doesn't mean forgetting the rest. A **Forget all devices** button (below
  the paired-devices list, destructive styling, two-click confirmation) revokes every phone at once
  via `regeneratePairingToken`, for a broader-compromise last resort. **Upgrading users must
  re-pair once**: the old access code is incompatible with `wc-sync/4` by design, so after
  updating both apps, open the desktop sidebar, click **Pair a device**, and type the code into
  the phone — same as first-time setup.
  Affects: `windows/src/renderer/src/components/Sidebar.tsx`, `windows/src/preload/index.ts`,
  `windows/src/main/sync-server.ts`, `windows/src/main/database.ts`.
- **Encrypted local databases, on both platforms, with non-bricking migration.** The desktop
  database (`better-sqlite3-multiple-ciphers`, key wrapped by Electron `safeStorage`/DPAPI in a
  separate `wellness.key`) and the phone's Room database (SQLCipher, key wrapped by a
  non-exportable AndroidKeyStore AES-GCM key) are now ciphertext on disk. An existing plaintext
  database migrates on a copy, is verified under the new key, then swaps in — keeping
  `wellness.db.plaintext.bak` — rather than ever starting from an empty database. Desktop fails
  closed with an on-screen dialog, naming the recovery files only when `wellness.db` itself is
  missing — a key-only failure instead names `wellness.key` and the backups folder; Android's
  key-loss path differs (no in-app message yet, and it silently rolls back to the pre-migration
  backup when one survives) — see SECURITY.md for the accurate per-platform behavior. Affects:
  `windows/src/main/database.ts`, `windows/package.json`, `android/app/build.gradle.kts`,
  `android/.../di/AppModule.kt`, new `android/.../security/DbKeyManager.kt`, new
  `android/.../data/local/DbEncryptionMigrator.kt`.

### Fixed

- **A failed migration swap could leave the app starting empty.** Cause: the plaintext-to-encrypted
  swap is two renames with a window where the live database file doesn't exist; if the process died
  there, or the second rename failed and the restore rename also failed, the next launch saw no
  database, skipped every guard that assumed one had existed, and let the driver create a fresh
  empty one over data that was still perfectly intact in the pre-migration copy. Fix: both platforms
  now check for the database file before ever opening it and recover from the surviving
  pre-migration copy or plaintext backup first; recovery re-runs on every launch until it succeeds,
  and cleanup never deletes a survivor while the live database is missing. Affects:
  `windows/src/main/database.ts`, `android/.../data/local/DbEncryptionMigrator.kt`,
  `android/.../di/AppModule.kt`.
- **A global pairing lockout could be tripped by any LAN host.** Cause: the exponential backoff
  that throttles repeated failed key checks (wrong pairing code, or a stale device key hitting
  `repair_required`) is one shared counter, not scoped per attempt — so a noisy or hostile device on
  the LAN could lock the owner out of pairing new devices too, with no way back in. Fix: starting a
  new pairing is an explicit user action that resets the lockout, so the owner is never permanently
  blocked, only inconvenienced. Affects: `windows/src/main/sync-server.ts`,
  `windows/src/main/database.ts`.

## [1.2.0] - 2026-08-15

Three pieces of user feedback, plus the update path they exposed.

### Added

- **Delete people from the journal's suggestion list.** The desktop Journal page gains a *manage
  people* panel where a saved person can be removed; the removal is a tombstone that survives sync in
  both directions, and past journal entries are untouched (they store names, not references).
  Cause: the delete path existed in the database layer but was dead code — the page's own UI rendered
  people as plain strings and discarded the row ids needed to call it — and aux-table sync was
  add-only, so any peer that still knew the row re-inserted it on the next sync. Fix: a `deleted_at`
  column on both platforms (desktop migration, Room v2→v3), exchanged through the existing `people`
  aux payload as a grow-only tombstone that no code path may clear. Re-adding the same name creates a
  new row with a new uuid — intended. How to use: 💬 Journal → *manage people* → ✕ beside a name.
  Affects: `windows/src/main/database.ts`, `windows/src/main/sync-server.ts`,
  `windows/src/renderer/src/pages/InteractionsPage.tsx`, `android/.../data/local/entity/PersonEntity.kt`,
  `android/.../data/local/dao/PersonDao.kt`, `android/.../data/local/WellnessDatabase.kt`,
  `android/.../sync/SyncManager.kt`.
- **"Earlier ideas" on the phone.** The Ideas screen now shows previous ideas below today's, grouped
  by day, newest first. Cause: nothing was missing from the data — ideas have always synced both ways
  — the screen simply queried today's entries only, so a phone showed an empty list for anything
  written yesterday while the desktop showed the whole history. Fix: a history query beside the
  today query, rendered as dated groups. Affects: `android/.../ui/ideas/IdeasScreen.kt`,
  `android/.../ui/ideas/IdeasViewModel.kt`, `android/.../data/local/dao/EntryDao.kt`.
- **Sleep saves in two halves, on both apps.** Save a bedtime in the evening and it persists — kill
  the app, reopen it, the bedtime is still there instead of the default 23:00. Save the wake-up in
  the morning and it completes the *same* row rather than creating a second one, and the night is
  re-dated to the wake-up day, matching how one-shot morning saves have always been dated. From the
  completed state, *"Save tonight's bedtime"* starts the next night. A single full save still works
  exactly as before. Cause: sleep was one atomic save assembled in memory from hardcoded defaults —
  there was no partial row to come back to, so closing the app lost the bedtime, and a second save
  the next morning wrote a duplicate entry for the day. Fix: `wakeTime` became nullable in the sleep
  payload, saves upsert against the latest sleep entry, and partial entries are guarded everywhere
  they are rendered so a missing wake-up never reaches the duration maths as `NaN`.
  Affects: `android/.../ui/sleep/SleepScreen.kt`, `android/.../ui/sleep/SleepViewModel.kt`,
  `android/.../data/model/SleepData.kt`, `android/.../ui/dashboard/DashboardViewModel.kt`,
  `windows/src/renderer/src/pages/SleepPage.tsx`, `windows/src/renderer/src/lib/summary.ts`.
- **Update-safe installs.** Installing over an existing install is now an explicit, stated path
  rather than an assumption. The Windows installer detects an existing installation, closes the
  running app before replacing files, and says on screen that your data is kept. The desktop app
  snapshots its database on the first launch after any version change, keeping the last five
  snapshots under `%APPDATA%\wellness-companion\backups\` (written to a temp file and renamed, and
  the quit sequence waits for an in-flight snapshot before closing the database, so a half-written
  backup cannot be left behind). The phone installer states that the APK updates in place and warns
  to sync before any uninstall. Cause: updating had always *happened* to work, but nothing verified
  it — no backup existed if a migration went wrong, the installer never mentioned data at all, and
  an Android uninstall silently takes the database with it. Affects: `installer/wellness_setup.iss`,
  `installer/install_phone_app.bat`, `windows/src/main/database.ts`, `windows/src/main/index.ts`.

### Changed

- **The Android launcher icon renders the same art as the Windows icon** — the same gradients, leaf
  veins and heart, ported into the adaptive icon's vector drawables. Why: the two apps shipped
  visibly different marks. The status-bar notification icon stays flat, which Android requires.
  Affects: `android/app/src/main/res/drawable/ic_launcher_background.xml`,
  `android/app/src/main/res/drawable/ic_launcher_foreground.xml`.

### Fixed

- **Sync updates never applied.** Cause: the last-write-wins guard on the desktop's ingest UPDATE
  compared the stored row's `modified_at` against *itself* rather than against the incoming
  timestamp, so the condition could never be true and every update to an existing row was silently
  discarded — the phone's counter still reported them as applied. Nothing arriving from the phone
  could change a row the desktop already had: idea edits and sleep completions replicated as
  inserts-that-weren't. Fix: bind the incoming `modified_at`. Affects: `windows/src/main/sync-server.ts`.
- Sync updates now carry the entry's `date`, so a night re-dated to the wake-up day replicates as the
  same row on the other device instead of staying on the evening it started.
- Desktop bedtime-only saves omit the `wakeTime` key entirely instead of sending an explicit `null`.
  Cause: a phone still running the 1.1.0 APK parses the sleep payload with Gson into a non-null
  field; an explicit `null` would have crashed it. An absent key is the safe wire shape.
- Editing or deleting an idea on the phone did nothing. Cause: `IdeasViewModel` read the day's
  entries from a `stateIn(WhileSubscribed)` flow that no collector ever subscribed to, so `.value`
  was permanently the empty initial list and both actions matched nothing. Fix: read the entries
  on demand inside the action, following the one-shot `.first()` pattern already used by
  `ChoresViewModel`. Affects: `android/.../ui/ideas/IdeasViewModel.kt`.
- Navigating to a date with a bedtime-only entry no longer leaves the previous date's wake-up time
  in the picker. Affects: `windows/src/renderer/src/pages/SleepPage.tsx`.

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

### Changed — icons, motion, and the crane bowl

- **The hobby crane bowl was redrawn.** Cause of the old look: cranes drew at a fixed
  127-pixel scale over a fill almost identical to the page pink, so the bowl read as a faint
  outline, the cranes as a muddy wall, and bottom-row cranes hung below the bowl's edge. The
  bowl is now glass — visible silhouette, rim and highlight — cranes are sized to the canvas,
  heap up from the centre, crest above the rim into a pyramid, and tumble onto the ground
  beside a full bowl; a clip keeps every crane inside the glass, so nothing can poke through
  the bottom. New cranes drop in with a soft bounce, and only for time logged while the
  screen is open. Affects: `android/.../ui/hobbies/CraneBowlCanvas.kt`.
- **Chrome glyphs are real icons now.** The `←` `→` `✕` `✓` `★` text glyphs and the 🔄 sync
  emoji became Material Rounded icons on Android and lucide icons on the desktop app; the
  category emoji stay — they are the brand. Notifications show a sprout instead of Android's
  stock compass icon. Why: text glyphs render inconsistently across devices and read as
  unfinished next to proper vector icons.
- **Motion pass on both apps.** Category screens slide with the swipe direction and the
  active nav dot morphs between pages; the water bottle's level, big number and goal bar
  animate (the level tracks the finger raw while dragging — only the settle springs); charts
  grow in on first view; chore checkmarks bounce; mood tiles scale when selected; dashboard
  cards enter staggered, with a subtle streak-badge pulse from a three-day streak up; the
  celebration overlay pops in with a spring and now also fires when every chore is done, all
  four meals are logged, or a sleep goal is met. On the desktop: pages fade in, buttons and
  stars respond to hover and press, the activity heatmap reacts under the cursor, and the
  Insights status pulses while the model is thinking.

### Known limitations

- Sync traffic is unencrypted `ws://` on the local network, and neither database is encrypted at
  rest. The pairing code is access control, not confidentiality.
- The shipped phone build is **debug-signed** unless a release keystore is supplied, so anyone with
  USB access to an unlocked device can read the app's data directory.

Both are documented in [SECURITY.md](SECURITY.md).

## [1.0.0] - 2026-06-16

Initial release: Jetpack Compose Android app and Electron desktop hub with local-network mDNS sync
and offline AI insights.
