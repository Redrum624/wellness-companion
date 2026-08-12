# Wellness Companion

[![Downloads](https://img.shields.io/github/downloads/Redrum624/wellness-companion/total?label=downloads)](https://github.com/Redrum624/wellness-companion/releases)
[![Latest release](https://img.shields.io/github/v/release/Redrum624/wellness-companion)](https://github.com/Redrum624/wellness-companion/releases/latest)
[![License: PolyForm Noncommercial 1.0.0](https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-blue)](LICENSE)

**A phone-first daily wellness tracker that keeps your data on your own devices.**

<p align="center">
  <img src="docs/images/android-dashboard.png" alt="Dashboard with twelve category cards" width="24%">
  <img src="docs/images/android-water.png" alt="Water screen with a draggable bottle" width="24%">
  <img src="docs/images/android-hobbies.png" alt="Hobbies screen with a bowl of origami cranes" width="24%">
  <img src="docs/images/android-sync.png" alt="Sync panel showing the pairing code field" width="24%">
</p>

You log things on your **phone** during the day — water, meals, sleep, mood, symptoms, chores,
hobbies — across twelve categories, each with its own screen and its own illustrated visual. The
water bottle empties as you drink from it. The hobby bowl fills with origami cranes as you spend
time on something you care about.

An optional **Windows companion app** pulls that history over your own Wi-Fi and turns it into
charts and AI-written summaries, using a language model that runs entirely on your PC.

Nothing leaves your network. There is no account, no cloud service, no analytics, and no telemetry.
The phone keeps a local database, the desktop keeps a local database, and syncing is a direct
device-to-device connection gated by a pairing code.

---

## Installing the phone app

The app is not on Google Play. There are two ways to get it onto a device:

**Option A — with the desktop installer (easiest).** Run
`Wellness Companion Setup <version>.exe` from the
[Releases page](https://github.com/Redrum624/wellness-companion/releases/latest); it bundles the
Android package and offers to sideload it. Tick *"Install the app on my phone now"*, with your
phone connected by USB and USB Debugging on (Settings → Developer Options → USB Debugging). You can
also do it later from the *Install the phone app* Start-Menu shortcut. The script finds or
downloads ADB itself, force-stops any old copy, installs, grants the notification permission and
launches the app.

**Option B — build it yourself.** See [Building from source](#building-from-source); the phone app
builds on its own with `gradlew.bat assembleDebug` and installs with `adb install -r`.

The phone app is fully usable on its own. Everything below about the desktop app is optional.

## Installing the desktop app

Download **`Wellness Companion Setup <version>.exe`** from the
[Releases page](https://github.com/Redrum624/wellness-companion/releases/latest) and run it. That
single file contains the desktop app, the Visual C++ runtime it needs, and the Android package.

The installer asks you three things:

1. **Create a desktop shortcut** — on by default.
2. **Install the app on my phone now** — off by default (see above).
3. **Download the AI model now** — on by default. Pulls a ~2.5 GB model from Hugging Face and needs
   an internet connection. Untick it to finish quickly; everything except the AI insights works
   without it, and re-running the installer later will fetch it.

Installation needs administrator rights, because the Visual C++ runtime is installed machine-wide.

**Where things end up:**

| What | Where |
|---|---|
| The app | `C:\Program Files\Wellness Companion\` |
| Your data | `%APPDATA%\wellness-companion\wellness.db` |
| The AI model | `%LOCALAPPDATA%\wellness-companion\model\` |
| Downloaded ADB tools | `%LOCALAPPDATA%\wellness-companion\tools\` |
| Install log | `C:\Program Files\Wellness Companion\setup.log` |

Uninstalling removes the program but **deliberately keeps your database, the downloaded model, and
the ADB tools**, so reinstalling costs you neither your history nor another 2.5 GB download. Delete
those three folders by hand for a truly clean slate.

**If the model download fails**, setup still completes — open `setup.log` in the install folder to
see why, then re-run the installer or place the file yourself at:

```
%LOCALAPPDATA%\wellness-companion\model\Qwen3-4B-Instruct-2507-GGUF\Qwen3-4B-Instruct-2507-Q4_K_M.gguf
```

It must be the `Q4_K_M` quantisation, exactly 2,497,280,448 bytes, from
[lmstudio-community/Qwen3-4B-Instruct-2507-GGUF](https://huggingface.co/lmstudio-community/Qwen3-4B-Instruct-2507-GGUF).
The installer verifies both the size and the SHA-256 and rejects anything that does not match.

## Pairing the phone with the PC

<p align="center">
  <img src="docs/images/desktop.png" alt="The desktop app dashboard, with the pairing code in the sidebar" width="85%">
</p>

Open the desktop app first. It advertises itself on the local network as `wellness-companion-sync`
over mDNS, listens on port 9847, and shows an eight-character **pairing code** in its sidebar.

On the phone: open the dashboard, tap **🔄 Sync**, type that code into the pairing field, tap
**Pair**, then tap **Sync**. The phone stores the code, so you do this once. If mDNS discovery
fails — some networks block multicast — type the PC's address into the field below instead
(`192.168.1.42:9847`).

The desktop app tries to add its own Windows Firewall rule for port 9847 on first run. That attempt
silently fails unless the app is elevated, in which case Windows raises its own firewall prompt —
allow it on private networks.

### What the pairing code does, and what it does not

The desktop refuses every request — reads *and* writes — until a client presents the correct code,
and the phone refuses to transmit anything until you have stored one. Five wrong attempts drop the
connection.

Be clear about the limits: **sync traffic is unencrypted** (`ws://`, not `wss://`), and neither
database is encrypted at rest. The pairing code stops other devices on your network from reading or
writing your data; it does not protect the contents from someone who can already observe your LAN
traffic. Use it on networks you trust.

## Modules

Twelve categories, each with a dedicated screen on the phone and an equivalent page on the desktop.

- **Dashboard** — today at a glance: every category's progress against its daily goal, plus streaks.
- **Water** — hydration logging with a bottle you drag to drink from; it empties as you go.
- **Food** — meals and snacks with time, description and rating.
- **Sleep** — bed/wake times, duration, and a sleep-quality bar.
- **Emotions** — mood entries across the day, drawn as an arc from morning to night.
- **Health** — symptoms, medication, weight and general health notes.
- **Bathroom** — bathroom visits, for anyone tracking a gut or urinary condition.
- **Cycle** — menstrual cycle tracking with phase prediction.
- **Chores** — recurring household tasks from reusable templates.
- **Hobbies** — time spent per hobby, shown as a bowl that fills with origami cranes.
- **Ideas** — quick capture for thoughts you want to keep.
- **Journal** — free-text diary entries tagged with the people you saw (internally `interactions`).
- **Bad Habits** — the things you are trying to do less of, counted rather than judged.
- **Insights** *(desktop only)* — AI-written summaries of your week, generated locally.

## Features

- **Local-network sync** — mDNS discovery plus a WebSocket channel on port 9847, gated by a pairing code; no server, no account.
- **Incremental sync** — only entries changed since the last successful sync are sent, in batches.
- **Manual IP fallback** — sync still works when the network blocks multicast discovery.
- **Offline AI insights** *(desktop)* — Qwen3-4B-Instruct via `node-llama-cpp`; prompts and entries never leave the PC.
- **Reminder notifications** *(phone)* — hydration, meals, evening check-in and refill reminders.
- **Streak tracking** — consecutive-day streaks per category.
- **Weekly trend charts** *(phone)* — per-category trend lines.
- **Calendar heatmap** *(desktop)* — a year of activity density at a glance.
- **Daily goals** — per-category targets shown as `X/Y` progress summaries on the dashboard.
- **Timeline view** *(desktop)* — the day's entries across all categories in one chronological column.
- **Quick buttons** — one-tap logging of common amounts, so routine entries take a second.
- **Star ratings and sliders** — consistent input controls shared across modules.
- **Tag input** — free-form tags on entries, with recall of tags you have used before.
- **Unit conversion** *(phone)* — metric/imperial handling for volumes and weights.
- **Celebration overlay** *(phone)* — a small animation when a daily goal is met.
- **Pastel theming** — a per-category colour system used consistently across both apps.

## Building from source

**Prerequisites**

- **Android SDK + JDK 17** — required. The installer bundles the phone app, and `android/app/build/`
  is gitignored, so a clean clone always builds the APK from source.
- **Node.js 18+ and [pnpm](https://pnpm.io/installation)** for the desktop app. Use pnpm, not npm —
  the repo ships `pnpm-lock.yaml`, and `npm install` both ignores it and fails on Python 3.12+
  (npm's bundled node-gyp 9 imports `distutils`, removed from the standard library in 3.12). pnpm
  installs a prebuilt `better-sqlite3` binary and never invokes node-gyp.
- **A C++ toolchain** — Visual Studio Build Tools with "Desktop development with C++". Only needed
  if a native dependency has no prebuilt binary for your platform; the normal path uses prebuilds.
- **[Inno Setup 6](https://jrsoftware.org/isdl.php)** to compile the installer.
- **Python 3** — required: the build generates the shipped `README.txt` from this file. Pillow is
  needed only to regenerate `installer/icon/wellness.ico`, which is already committed.
- **Windows SDK** (optional) — provides `signtool.exe` for Authenticode signing. Without it the
  build succeeds and the binaries are simply unsigned.

> **Clone somewhere short**, e.g. `C:\dev\wellness-companion`. The Electron output nests
> `node_modules` several levels deep inside `dist\win-unpacked\resources\app.asar.unpacked\`, and
> Inno Setup is not manifested for long paths — so from a deeply-nested clone the installer step
> aborts partway through with `The system cannot find the path specified`. Enabling Windows'
> `LongPathsEnabled` does **not** help here. Measured: a clone at a 120-character path produced
> 290-character file paths and failed; the same build at `C:\wcv` succeeded.

**Downloads the build fetches for you**

- `vc_redist.x64.exe` (~25 MB) from `aka.ms`, downloaded automatically if missing and embedded in
  the installer.
- The 2.5 GB GGUF model — only for an `offline` build, and only if you place it at
  `model/Qwen3-4B-Instruct-2507-GGUF/` at the repo root first. A `lean` build does not need it.

**The phone app on its own**

```bat
cd android
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**The full installer**

```bat
cd windows
pnpm install
cd ..
installer\build_installer.bat            :: lean — the model is downloaded at install time
installer\build_installer.bat offline    :: offline — the model is embedded (~2.8 GB installer)
```

Both outputs land in `installer\output\`: the version-stamped Setup exe and a matching
`README.txt`. The version comes from `windows/package.json` and is passed through to Inno Setup, so
that file is the single place to bump it.

To ship a release-signed phone app rather than the debug build, create a keystore and an untracked
`android/keystore.properties` — see the comments at the top of `android/app/build.gradle.kts`.

**Desktop app in development**

```bat
cd windows
pnpm dev
```

## Architecture

```
wellness_companion/
  android/      Kotlin + Jetpack Compose app; Room database, Hilt DI, WorkManager reminders
  windows/      Electron + React + TypeScript desktop app; better-sqlite3, node-llama-cpp
  installer/    Inno Setup script, build orchestrator, model provisioning, ADB sideload
  model/        The GGUF model (not in version control — fetched or downloaded)
  docs/         Project spec, health guidelines, screenshots, and prototypes in docs/demo/
```

The two apps share no code, but they share a schema: entries are rows keyed by date and category,
which is what keeps the sync protocol down to a handful of WebSocket messages.

Further reading: [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md) is the original design document
(pre-v1.0, see the note at its head), and `docs/demo/` holds two standalone HTML prototypes —
[the phone app mock](docs/demo/android_app_mock_v5_full.html) and
[the water-bottle animation study](docs/demo/pastel_water_bottle.html) — that predate the
implementation.

## Downloads

![Downloads over time](.github/badges/downloads.svg)

<sub>Built daily by a GitHub Action from the Releases API. The GitHub API keeps no historical
download data, so the curve starts on publish day.</sub>

## License

[PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0) —
see [LICENSE](LICENSE). You may use, modify and share this software freely for any **noncommercial**
purpose, including personal projects, research, education, and use by nonprofit or government
organizations. Commercial use is not granted by this license.

Bundled third-party components (npm packages, native libraries, Android dependencies, the AI model
and the Nunito font) remain under their own licenses; see
[THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md). Nothing in this project's license restricts the
rights those licenses grant.
