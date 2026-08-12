# Changelog

All notable changes to Wellness Companion are documented here.

## [1.1.0] - 2026-08-12

### Changed

- Replaced the electron-builder/NSIS installer with an Inno Setup 6 installer under `installer\`.
  Why: the NSIS template owned the wizard and only exposed a macro hook, so opt-in task checkboxes,
  custom pages and a bundled phone-app install were not reachable. electron-builder is now a `dir`
  target that only emits `dist\win-unpacked\`; `wellness_setup.iss` wraps it.
  How to use: `build_setup.bat` (prompts) or `build_setup.bat lean|offline`.
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

## [1.0.0] - 2026-06-16

Initial release: Jetpack Compose Android app and Electron desktop hub with local-network mDNS sync
and offline AI insights.
