# Security Policy

## Reporting a vulnerability

Report vulnerabilities **privately** via GitHub Security Advisories — the "Report a vulnerability"
button on the Security tab — not through public issues. You'll get a response within 7 days.
Please include reproduction steps and the version you tested.

## Known and accepted limitations

These are design trade-offs, documented so nobody has to rediscover them. They are not
vulnerabilities in themselves, but you should understand them before trusting the app with
sensitive data.

- **Sync traffic is unencrypted.** The phone and desktop talk over plain `ws://` on the local
  network, not `wss://`. Anyone able to observe your LAN traffic can read entries in transit.
- **Neither database is encrypted at rest.** `%APPDATA%\wellness-companion\wellness.db` on the PC
  and the Room database on the phone are plain SQLite. Anyone with file access to the device can
  read them.
- **Access control is a single shared pairing code.** Eight characters from a 31-character
  alphabet, required in both directions, with five attempts per connection before the socket is
  dropped. This stops other devices on the network from reading or writing your data; it is not a
  defence against an attacker who can already observe the traffic.
- **The desktop sync server listens on all interfaces** on port 9847 while the app is running, by
  design — that is how the phone reaches it. It serves nothing until a client authenticates.
- **The shipped phone build is debug-signed** unless the maintainer supplies a release keystore.
  A debug-signed APK is `debuggable`, so anyone with USB access to an unlocked device can read the
  app's data directory. Release signing is wired up in `android/app/build.gradle.kts`; see the
  comment at the top of that file.

## What the project deliberately does not do

- No account, no cloud service, no server component.
- No analytics, telemetry, crash reporting or update pings.
- No third-party network requests at runtime. The single outbound request in the entire project is
  the one-time AI model download during installation, which is verified against a pinned SHA-256.

If you find something that contradicts any of the above, that *is* a vulnerability — please report
it.
