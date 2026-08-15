# Security Policy

## Reporting a vulnerability

Report vulnerabilities **privately** via GitHub Security Advisories — the "Report a vulnerability"
button on the Security tab — not through public issues. You'll get a response within 7 days.
Please include reproduction steps and the version you tested.

## Known and accepted limitations

These are design trade-offs, documented so nobody has to rediscover them. They are not
vulnerabilities in themselves, but you should understand them before trusting the app with
sensitive data.

### Sync transport

- **Sync traffic is encrypted, mutually authenticated, and forward-secret.** The phone and desktop
  speak `wc-sync/4`: a fresh P-256 ECDH key exchange every connection, HKDF-SHA256, and
  AES-256-GCM records, authenticated by a 128-bit pairing secret that becomes a persisted
  per-device key. Stealing a device key later does **not** decrypt sessions recorded earlier.
  There is no plaintext fallback in either direction — a peer that speaks the old protocol gets a
  tombstone reply and the connection is closed before any row moves.
- **Residual — first-pairing shoulder-surf.** Anyone who can read the pairing code off your screen
  while you pair a device can pair a device of their own. This is a physical-presence trust
  ceremony, like Bluetooth pairing: one-time per device, and revocable afterwards. Pair out of
  sight of others.
- **Residual — a stolen device key allows a future MITM until you re-pair.** This is inherent to
  symmetric-key authentication: whoever holds the key can impersonate that device until the key is
  invalidated. Mitigated by per-device revocation (desktop sidebar, **Paired devices → Remove**).
  A "forget every device at once" capability exists underneath (`regeneratePairingToken`) as a
  last resort for a broader compromise, but as of this writing it is not yet exposed by any button
  in the desktop UI — Remove is the only control you can actually reach today.
- **Residual — LAN availability is not defended.** An attacker already on your LAN (mDNS spoofing,
  ARP tricks) can deny sync — block it, delay it, make it fail. They cannot read or forge your
  data, and they cannot force your phone to drop its pairing: forgetting a device only ever
  happens through explicit user action, never through an unauthenticated network frame.
- **Residual — clock skew affects conflict resolution, not the handshake.** Last-write-wins
  ordering and the sync cursor compare timestamps across devices; a badly-wrong device clock can
  still mis-order or skip entries. This predates transport encryption and is unrelated to it — the
  handshake itself carries no timestamps and is immune. Re-pairing forces one full reconciling
  sync, which partially heals a skew-related gap.
- **Residual — the phone's device name is visible before authentication completes.** The phone
  sends its model name in the first handshake message to any LAN peer that answers with a
  plausible greeting, including one that never completes the cryptographic handshake. Only the
  device name is exposed this way; no entry data or credentials are, and nothing is served until
  the handshake finishes.

### Pairing

- **Pairing is one 33-character code**, not a shared password: a public lookup id plus a 128-bit
  secret, typed once per device (e.g. `CPR38-KAHBY-4EBBB-QPG9N-B4WFR-HXTY9-NNT`). The desktop
  sidebar lists every paired device with a last-seen time and its own **Remove**, so revoking one
  phone doesn't require forgetting the rest.

### At-rest encryption

- **Both databases are encrypted at rest.** `%APPDATA%\wellness-companion\wellness.db` on the PC
  (`better-sqlite3-multiple-ciphers`, SQLCipher-compatible) and the Room database on the phone
  (SQLCipher) are ciphertext on disk. Each platform generates its own random key on first run and
  wraps it with a platform key store — Electron `safeStorage` (Windows DPAPI) on the desktop, a
  non-exportable AndroidKeyStore AES-GCM key on the phone — never a derived or hardcoded key. An
  existing plaintext database is migrated in place on first launch and verified under the new key
  before the plaintext copy is touched.
- **What this defends: a cold copy.** A lost or stolen device imaged offline, a lost backup, an
  `adb pull` run without a live unlocked app — in those cases, what's on disk is ciphertext.
- **What this does NOT defend: malware or a shell running as you.** At-rest encryption does not
  protect against code running as your own Windows account (DPAPI unwraps for that account by
  design) or as the app's own UID on Android — including `run-as` access on a **debug-signed**
  build, which is what ships today (see "Signing" below). Only release-signing the Android build
  (`debuggable=false`) closes the `run-as` gap; encryption at rest cannot compensate for a
  debuggable build.
- **Desktop backups are tied to your Windows account.** The wrapped key only unwraps for the
  Windows user account that created it, so `wellness.db` backups are not portable across accounts
  or machines without separate export tooling.
- **Key-loss recovery is manual.** If the wrapped key ever becomes unusable (a corrupted DPAPI
  profile, a `wellness.key` copied to another machine, an AndroidKeyStore key invalidated by the
  OS), both apps fail closed with an on-screen message rather than silently starting from an empty
  database, naming the file that still holds your data — `wellness.db.premigration.tmp` or
  `wellness.db.plaintext.bak` on the desktop, and on Android a preserved `wellness.db.keylost*.bak`
  copy if the key is lost after migration already completed. There is no in-app recovery screen
  yet: getting the data back is a manual file operation, not a button.

### Signing

- **The desktop sync server listens on all interfaces** on port 9847 while the app is running, by
  design — that is how the phone reaches it. It serves nothing until a peer completes the
  authenticated handshake above.
- **The shipped phone build is debug-signed** unless the maintainer supplies a release keystore. A
  debug-signed APK is `debuggable`, which is what makes the `run-as` gap above possible for anyone
  with USB access to an unlocked device. Release signing is wired up in
  `android/app/build.gradle.kts`; see the comment at the top of that file.

## What the project deliberately does not do

- No account, no cloud service, no server component.
- No analytics, telemetry, crash reporting or update pings.
- No third-party network requests at runtime. The single outbound request in the entire project is
  the one-time AI model download during installation, which is verified against a pinned SHA-256.

If you find something that contradicts any of the above, that *is* a vulnerability — please report
it.
