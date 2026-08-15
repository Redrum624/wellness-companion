# Signing the Android app for release

The phone build that ships in every installer today is **debug-signed** — built with
`assembleDebug` and Android's universally-known debug key. That's fine for your own device, but a
debug build is `debuggable`: anyone with USB access to the phone while it's unlocked can `run-as`
the package and read its data directory straight off disk. A release build closes that off.

This guide walks through creating your own signing key and pointing the build at it. **You run
this yourself** — nobody else should hold your signing key or its passwords, so nothing here is
automated. Once `android/keystore.properties` exists, `installer/build_installer.bat` picks it up
automatically and switches from `assembleDebug` to `assembleRelease` on its own; see
[README.md → Updating](../README.md#updating) if you're moving an existing phone from a debug
install to a release one.

## 1. Generate the keystore

Run this once, from anywhere outside the repo (the keystore is a personal secret, not a project
file):

```bat
keytool -genkeypair -v -keystore wellness-release.jks -alias wellness ^
        -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` ships with the JDK. It will prompt for:

- **A keystore password** — protects the `.jks` file itself.
- **Your name / org / locale** — cosmetic, goes into the certificate; anything is fine.
- **A key password** — protects the `wellness` key inside the keystore. You can reuse the keystore
  password when prompted, or set a different one.

`-validity 10000` gives the certificate ~27 years, so you are not forced to re-sign the app (and
therefore force every phone through the debug→release migration again) partway through its life.

## 2. Back the keystore up — losing it is unrecoverable

**If you lose `wellness-release.jks` or its passwords, you cannot release an update that Android
will accept as the same app.** Android identifies "the same app" by signing key, not by
`applicationId` or version. A new keystore produces a different signature, which means every
existing install must be *uninstalled* (wiping its data) before the new build can go on — there is
no update path across a lost key.

Treat it like your desktop database's recovery key:

- Copy `wellness-release.jks` to at least one other location you control (an encrypted USB drive,
  a password manager's file storage, offline backup media). Do not park the only copy on the same
  disk as the repo.
- Store the store/key passwords in a password manager, not in a text file next to the keystore.
- If you ever *do* lose it, the only way forward is a new keystore and a full reinstall on every
  phone — plan for that being expensive enough that the backup above is worth doing now.

## 3. Write `android/keystore.properties`

Create `android/keystore.properties` (already gitignored — verify below) with all four keys:

```properties
storeFile=C:/path/to/wellness-release.jks
storePassword=your-keystore-password
keyAlias=wellness
keyPassword=your-key-password
```

- `storeFile` — absolute path to the `.jks` from step 1. Forward slashes work fine in this file
  even on Windows.
- `storePassword` / `keyPassword` — from step 1. They may be identical; both keys are required
  either way, since `android/app/build.gradle.kts` reads all four unconditionally once the file
  exists.
- `keyAlias` — `wellness`, matching the `-alias` passed to `keytool` above. Change it here too if
  you used a different alias.

## 4. Verify it's gitignored

Signing material must never enter version control — anyone with the keystore and its passwords can
produce an update Android will accept as coming from you. Confirm the repo already ignores it:

```bat
git check-ignore -v android\keystore.properties
git check-ignore -v wellness-release.jks
```

Both should print a match against `.gitignore` (`android/keystore.properties`, `*.jks`,
`*.keystore`). If either prints nothing, **stop** — do not build or commit until the ignore rule is
fixed.

## 5. Build

```bat
cd android
gradlew.bat assembleRelease
```

or just run `installer\build_installer.bat` — it detects `android\keystore.properties` and builds
`assembleRelease` automatically, falling back to `assembleDebug` (with a warning) when the file is
absent. The installer's `[Files]` section already prefers `app-release.apk` over the debug build
whenever both exist.

## Moving an existing phone across

A release build has a different signature than the debug build, so Android will refuse to install
it over an existing debug install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) — the two are, as far as
the OS is concerned, different apps. `install_phone_app.bat` detects this and walks you through the
sync-first migration before touching anything; see
[README.md → Migrating an existing phone to a release-signed build](../README.md#migrating-an-existing-phone-to-a-release-signed-build)
for the five steps. The short version: **sync the phone to the desktop before you let anything
uninstall it** — uninstalling the old app deletes its local data.
