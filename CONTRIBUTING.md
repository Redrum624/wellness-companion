# Contributing

Thanks for your interest. This is a personal project shared under a noncommercial license, so
please read [LICENSE](LICENSE) before building on it.

## Before opening a PR

1. **Open an issue first** describing the change, so we don't both solve the same problem
   differently.
2. **One logical change per PR.** Follow the style already in the file you're editing.
3. **Build both halves** — a change to the sync protocol touches both apps:
   ```bat
   cd android && gradlew.bat assembleDebug && cd ..
   cd windows && pnpm build && cd ..
   ```
4. **Run the automated tests, then exercise the change end-to-end.** There are Jest suites in
   `windows/test/` (`cd windows && pnpm test`) and JUnit tests in `android/app/src/test/`
   (`cd android && gradlew.bat test`), but no end-to-end or instrumentation suite yet — so a PR
   should also say what you ran by hand. For anything touching sync, that means: launch the
   desktop app, pair a phone or emulator, sync, and confirm the entries land on both sides.

## Things worth knowing

- **The sync protocol is versioned by hand.** `hello` carries a `version` field; if you change the
  message shape, bump it and handle the older value on both sides.
- **The pairing code is the only access control.** Any new message type must arrive as an
  encrypted `wc-sync/4` record, decoded only after `hs3` establishes the channel — never add a
  handler in `handleFrame` (`windows/src/main/sync-server.ts`) that runs on a plaintext frame.
- **The renderer is sandboxed with a strict CSP.** No remote scripts, styles, fonts or images; if
  you need an asset, vendor it into `windows/src/renderer/src/assets/`.
- **Nothing may phone home.** No analytics, no update pings, no CDN. The only outbound request the
  project makes is the one-time model download in `installer/setup_model.ps1`.
- **The README's module and feature lists are derived from the code.** If you add or remove a
  screen, update them in the same PR.

## Reporting bugs

Use the issue templates. For sync problems, include the desktop app's console output and the
status line shown under the phone's Sync button — those two together usually identify the failure.
