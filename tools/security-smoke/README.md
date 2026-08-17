# Security interop smoke instruments

Test tooling used to execute spec §9, the cross-platform interop release gate for `wc-sync/4`.
They are re-runnable scripts, not shipped app code — nothing here is bundled into the packaged
desktop build (they live outside `windows/`, which is electron-builder's app root) and nothing
here reads or writes a database by default.

No secrets, keys, or captured user data are committed. Every script takes its
targets (URL, port, file paths) as CLI arguments; nothing is hardcoded to a
particular machine or clone location — `require()`s resolve relative to the
script's own `__dirname` into `windows/node_modules`, so they work from any
checkout as long as `pnpm install` has been run in `windows/`.

## Prerequisites

```bash
cd windows
pnpm install   # provides ws and better-sqlite3-multiple-ciphers, used below
```

The desktop app (built or `pnpm dev`) must be running with its sync server
listening (default `127.0.0.1:9847`) for any of these to have something to
talk to.

## Tools

| File | Role |
|---|---|
| `wcx.js` | `wc-sync/4` crypto **re-implemented from the spec** (§2.3–2.7) in plain JS — ECDH / HKDF / transcript hash / GCM record seal-open / base31 pairing-code decode. Not a copy of `windows/src/main/sync-crypto.ts`: any handshake that succeeds using this module is an independent, cross-implementation conformance result, not a tautology. Imported by `hostile.js`; has no side effects on its own. |
| `proxy.js` | MITM WebSocket relay, phone → `proxy.js` → desktop. Logs every frame (direction, TEXT/BINARY channel, size, record counter) — the direct evidence for "no text frame after `hs3`" and "two syncs never reuse a session key." Can also tamper: flip one ciphertext byte in the *n*-th record either direction, or rewrite the desktop's `hello` in flight (`hello-drop-crypto`, or `hello-patch-file` + a `hello_patch.json` you supply next to this file, e.g. `{"minVersion":3,"crypto":["x1","x0"]}`). |
| `hostile.js` | Scripted peer for states no real UI can produce: legacy v3 plaintext frames (`v3-auth`, `v3-fullsync`, `v3-push`, `v3-auth-then-fullsync`) against a v4 desktop, and a full `x1` handshake as a second device (`pair <code> <deviceId>`, optionally `--badmac` to present the right `keyId` with a corrupted secret). Depends on `wcx.js` (same directory). |
| `fakedesktop.js` | Hostile "desktop" that answers a well-formed v4 `hello` then forges an **unauthenticated** plaintext error (`unknown_device` / `pairing_changed`) right after the phone's `hs1` — proves a forged, unsigned frame cannot make the phone destroy its device key. |
| `keyreader/` | Standalone Electron app that unwraps `wellness.key` via `safeStorage` (Windows DPAPI) and opens a **copy** of the encrypted desktop DB to report table counts / run a read-only query. Never point it at the app's live `userData` directory directly — see below. |

## Running them

All of the following are non-destructive against a real desktop instance:
the v3/plaintext/tamper paths are designed to move **zero rows** (that's the
assertion being tested), and `proxy.js log` mode only observes traffic. The
`pair` and `--badmac` modes under `hostile.js` DO create/attempt a device
pairing — use a disposable `deviceId` and remove the resulting entry from the
desktop's device list afterward if you don't want it kept.

```bash
# Passive MITM: point the phone at ws://<pc-ip>:9848 instead of :9847
node tools/security-smoke/proxy.js log 9848 ws://127.0.0.1:9847

# Tamper the 2nd phone->PC record's ciphertext (expect AEADBadTag / close 1008)
node tools/security-smoke/proxy.js tamper-c2s:2 9848 ws://127.0.0.1:9847

# Legacy v3 plaintext auth against a v4 desktop (expect update_required / 4005)
node tools/security-smoke/hostile.js v3-auth

# Text full_sync carrying a poison entry (expect zero rows ingested)
node tools/security-smoke/hostile.js v3-fullsync

# A second device pairing with a real code minted on the desktop
node tools/security-smoke/hostile.js pair <33-char-code> <a-throwaway-uuid> --label=SmokeTestPeer

# Same, but with a deliberately wrong secret (expect 4006 repair_required)
node tools/security-smoke/hostile.js pair <33-char-code> <a-throwaway-uuid> --badmac

# Forged unauthenticated unknown_device error to a paired phone
node tools/security-smoke/fakedesktop.js unknown_device 9848
```

Redirect stdout to a log file to archive a run, e.g.
`node tools/security-smoke/hostile.js v3-auth > logs/v3-auth.log 2>&1`
(the desktop's own `logs/` directory is already gitignored).

### `keyreader`

Reads a **copy** of the live encrypted DB plus a shadow `userData` directory
that carries the same `Local State` file (Chromium's OSCrypt key lives
there), so it never touches the running app's own directory:

```bash
"windows/node_modules/electron/dist/electron.exe" tools/security-smoke/keyreader \
  <path-to-wellness.key> <path-to-copy-of-wellness.db> "<optional SQL>" <path-to-shadow-userData-dir>
```

Prints the unwrapped key length, the DB's first 16 header bytes (ciphertext
has no `SQLite format 3\0` header), confirms an unkeyed open is rejected, then
opens it keyed and reports `SELECT COUNT(*)` for each table (and runs the
optional SQL if given).

**Opens the database read-only by default.** The README above says to point
this at a *copy* of `wellness.db`, but nothing used to stop someone aiming it
at the live `%APPDATA%\wellness-companion\wellness.db` and running a
destructive optional-SQL statement against it. By default the keyed handle is
opened with `readonly: true`, so any `UPDATE`/`DELETE`/`INSERT`/DDL in the
optional SQL argument fails instead of mutating the file. Pass `--allow-write`
as an **extra, trailing** argument (after the shadow-userData-dir) to open
read/write when you deliberately need to mutate a disposable copy:

```bash
"windows/node_modules/electron/dist/electron.exe" tools/security-smoke/keyreader \
  <path-to-wellness.key> <path-to-copy-of-wellness.db> "<SQL>" <path-to-shadow-userData-dir> --allow-write
```

Only ever pass `--allow-write` against a copy you are willing to lose.
