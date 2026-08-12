# Wellness Companion

Wellness Companion is an **offline-first daily wellness tracker** in two halves that talk to each
other over your own Wi-Fi: a phone app you log things into during the day, and a Windows desktop app
that stores the history and turns it into charts and AI-written summaries.

Nothing leaves your network. There is no account, no cloud service, and no telemetry — the phone
keeps a local database, the desktop keeps a local database, and syncing is a direct connection
between the two devices. The AI that writes your insights is a 4-billion-parameter model that runs
on your own PC.

## Installing

Download **`Wellness Companion Setup <version>.exe`** and run it. That single file contains
everything: the desktop app, the Visual C++ runtime it needs, and the Android app package.

The installer asks you three things:

1. **Create a desktop shortcut** — on by default.
2. **Install the app on your phone now** — off by default. Tick it if your phone is plugged in by
   USB with USB Debugging enabled (Settings → Developer Options → USB Debugging). You can always do
   this later from the *Install the phone app* shortcut in the Start Menu.
3. **Download the AI model now** — on by default. This pulls a ~2.5 GB model file from Hugging Face
   and needs an internet connection. Untick it to finish setup quickly; everything except the AI
   insights works without it, and re-running the installer later will fetch it.

Installation needs administrator rights, because the Visual C++ runtime is installed machine-wide.

**Where things end up:**

| What | Where |
|---|---|
| The app | `C:\Program Files\Wellness Companion\` |
| Your data | `%APPDATA%\wellness-companion\wellness.db` |
| The AI model | `%LOCALAPPDATA%\wellness-companion\model\` |
| Install log | `C:\Program Files\Wellness Companion\setup.log` |

Uninstalling removes the program but **deliberately keeps your database and the downloaded model**,
so reinstalling does not cost you your history or another 2.5 GB download. Delete those two folders
by hand if you want a truly clean slate.

**If the model download fails** (no connection, interrupted transfer), setup still completes. Open
`setup.log` in the install folder to see exactly what happened, then either re-run the installer or
drop the model folder into `%LOCALAPPDATA%\wellness-companion\model\` yourself.

## Syncing the phone and the PC

Open the desktop app first — it advertises itself on the local network as `wellness-companion-sync`
over mDNS and listens on port 9847. On the phone, pull to sync; it discovers the PC automatically as
long as both are on the same Wi-Fi. If discovery fails (some networks block multicast), the phone
lets you type the PC's IP address instead.

The first run may raise a Windows Firewall prompt for port 9847 — allow it on private networks.

## Modules

Each module is a screen you log one kind of thing into. The phone and the desktop carry the same
set, except where noted.

- **Dashboard** — today at a glance: every category's progress against its daily goal, plus streaks.
- **Water** — hydration logging with an animated bottle that fills as you drink.
- **Food** — meals and snacks with time, description and rating.
- **Sleep** — bed/wake times, duration, and a sleep-quality bar.
- **Emotions** — mood entries across the day, drawn as an arc bar from morning to night.
- **Health** — symptoms, medication, weight and general health notes.
- **Bathroom** — bathroom visits, for anyone tracking a gut or urinary condition.
- **Cycle** — menstrual cycle tracking with phase prediction.
- **Chores** — recurring household tasks from reusable templates.
- **Hobbies** — time spent on each hobby, with a filling-bowl visual per activity.
- **Ideas** — quick capture for thoughts you want to keep.
- **Interactions** — social contact logging, per person.
- **Bad Habits** — the things you are trying to do less of, counted rather than judged.
- **Insights** *(desktop only)* — AI-written summaries of your week, generated locally.

## Features

- **Local-network sync** — mDNS discovery plus a WebSocket channel on port 9847; no server, no account.
- **Offline AI insights** — Qwen3-4B-Instruct running locally through `node-llama-cpp`; prompts and entries never leave the PC.
- **Manual IP fallback** — sync still works when the network blocks multicast discovery.
- **Reminder notifications** — hydration, meals, evening check-in, and refill reminders, scheduled on the phone.
- **Streak tracking** — consecutive-day streaks per category.
- **Weekly trend charts** — per-category trend lines on both platforms.
- **Calendar heatmap** *(desktop)* — a year of activity density at a glance.
- **Daily goals** — per-category targets that drive the dashboard's progress rings.
- **Timeline view** *(desktop)* — the day's entries across all categories in one chronological column.
- **Quick buttons** — one-tap logging of common amounts, so routine entries take a second.
- **Star ratings and sliders** — consistent input controls shared across modules.
- **Tag input** — free-form tags on entries, with recall of tags you have used before.
- **Unit conversion** — metric/imperial handling for volumes and weights.
- **Celebration overlay** — a small animation when a daily goal is met.
- **Pastel theming** — a per-category color system used consistently across both apps.

## Building from source

**Prerequisites:** Node.js, [Inno Setup 6](https://jrsoftware.org/isdl.php), Python with Pillow (to
generate the installer icon once), and — only if you need to rebuild the phone app — the Android SDK
and a JDK. The Windows SDK's `signtool.exe` is optional and enables code signing.

```bat
cd windows
npm install
cd ..
build_setup.bat            :: prompts for lean or offline
build_setup.bat lean       :: model downloaded at install time
build_setup.bat offline    :: model embedded in the installer (~2.8 GB)
```

Both outputs land in `installer\output\`: the version-stamped Setup exe and a matching
`README.txt`. The version comes from `windows/package.json` and is passed through to Inno Setup, so
that file is the single place to bump it.

Running the desktop app in development:

```bat
cd windows
npm run dev
```

Building the phone app on its own:

```bat
cd android
gradlew.bat assembleDebug
```

## Architecture

```
wellness_companion/
  android/      Kotlin + Jetpack Compose app; Room database, Hilt DI, WorkManager reminders
  windows/      Electron + React + TypeScript desktop app; better-sqlite3, node-llama-cpp
  installer/    Inno Setup script, build orchestrator, model provisioning, ADB sideload
  model/        The GGUF model (not in version control — fetched or downloaded)
  docs/         Project spec, health guidelines, design notes
```

The two apps share no code, but they share a schema: entries are rows keyed by date and category,
which is what makes the sync protocol simple enough to be a handful of WebSocket messages.

## License

MIT
