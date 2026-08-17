# Daily Wellness Companion — Original Design Specification

> **Status: pre-v1.0 design document, retained for context. Not as-built documentation.**
>
> This was written before implementation and describes the app as originally designed. The shipped
> product diverges from it in several places, and **[README.md](../README.md) is the accurate
> description of what exists**. Known divergences:
>
> - It describes **9 categories**; the app ships **12** (Ideas, Cycle and Bad Habits were added
>   after this was written and have no sections below).
> - It names **Llama 3.1 8B Q8_0** as the model; the app ships **Qwen3-4B-Instruct-2507-Q4_K_M**
>   (~2.5 GB, not ~9 GB of VRAM).
> - Several sync-layer and analysis features below are **not implemented** — see the inline
>   "not implemented" markers.
> - **The security design below is superseded and partly inaccurate.** It describes an
>   eight-character pairing code, a plaintext `ws://` transport, and an unencrypted database —
>   none of which shipped. The real design (`wc-sync/4`: per-connection ECDH + AES-256-GCM
>   transport, a 33-character pairing code, and databases encrypted at rest on both platforms) is
>   documented in **[SECURITY.md](../SECURITY.md)**, which is authoritative for the app's
>   security posture.
> - The roadmap at the end is written as forward-looking estimates; all six phases shipped in
>   v1.0.0 / v1.1.0.

## Overview

A two-app ecosystem for tracking daily wellness across 9 categories. The **Android app** is the primary input device — always in your pocket, interactive and visual. The **Windows app** is the analysis hub — pulls all data, runs a local LLM for deep insights, and can also input data that syncs back to the phone.

Both apps share a pastel illustrated design language with Japanese-inspired decorative backgrounds (cherry blossoms, bamboo, zen gardens, origami cranes, torii gates).

---

## Architecture

```
┌─────────────────┐       Bidirectional Sync       ┌─────────────────────┐
│   Android App   │ ◄──── JSON delta / WebSocket ──►│    Windows App      │
│  (Kotlin/Compose)│       mDNS auto-discovery      │  (Electron/React)   │
│                 │                                  │                     │
│  Local SQLite   │                                  │  Master SQLite DB   │
│  (Room)         │                                  │  (better-sqlite3)   │
│                 │                                  │                     │
│  Notifications  │                                  │  Local LLM          │
│  (WorkManager)  │                                  │  (node-llama-cpp)   │
│                 │                                  │  CUDA enabled       │
└─────────────────┘                                  └─────────────────────┘
```

---

## Tech Stack

### Android App
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Database:** Room (SQLite)
- **Animations/Visuals:** Compose Canvas (custom interactive drawings)
- **Notifications:** WorkManager (scheduled, survives restarts)
- **Sync Client:** ~~Ktor~~ — **shipped instead:** OkHttp (WebSocket client)
- **Font:** Nunito (Google Fonts) or similar rounded sans-serif

### Windows App
- **Shell:** Electron
- **UI:** React + TypeScript
- **Database:** better-sqlite3 (native binding, synchronous reads)
- **Charts:** ~~Recharts + D3~~ — **not implemented.** Neither library is used; the desktop
  visuals (calendar heatmap, backgrounds) are hand-rolled SVG.
- **LLM:** node-llama-cpp with CUDA enabled, falling back to CPU
- **Model:** ~~Llama 3.1 8B Instruct Q8_0 GGUF (~9GB VRAM on 16GB GPU)~~ — **shipped instead:**
  Qwen3-4B-Instruct-2507-Q4_K_M GGUF (~2.5 GB)
- **Sync Server:** ws (WebSocket, runs inside Electron)
- **Font:** Nunito (same as Android for visual consistency)

### Sync Layer

> **Security note.** This section describes the original design intent and does not reflect the
> shipped security posture. As of v1.3.0, sync runs over `wc-sync/4` — a fresh per-connection
> P-256 ECDH key exchange, HKDF-SHA256, and AES-256-GCM records, authenticated by a single
> 33-character pairing code (a 128-bit secret) with no plaintext fallback — and both the desktop
> and phone databases are encrypted at rest. [SECURITY.md](../SECURITY.md) is the authoritative,
> current description; do not rely on this document for the app's security posture.

- **Protocol:** WebSocket. ~~HTTP polling fallback~~ — **not implemented.**
- **Discovery:** mDNS/Bonjour (zero-config local network). Manual IP fallback. *(implemented)*
- **Data Format:** JSON *(implemented)*
- **Sync Strategy:** Delta sync — only changed entries sent, each entry has version number + last-modified timestamp *(implemented)*
- **Conflict Resolution:** Last-write-wins on `modified_at`. *(implemented; per-field merge was not)*
- **Authentication:** ~~eight-character pairing code~~ — **shipped differently:** a single
  33-character pairing code delivering a 128-bit secret, required in both directions. See
  [SECURITY.md](../SECURITY.md).
- **Compression:** ~~gzip~~ — **not implemented.**
- **Encryption:** ~~TLS for transport. Optional at-rest encryption with user-set passphrase.~~ —
  **implemented differently** — see [SECURITY.md](../SECURITY.md): transport is ECDH + AES-256-GCM
  (not TLS), at-rest encryption is mandatory and platform-key-wrapped (not an optional user
  passphrase).
- **Schema Versioning:** Room migrations on the phone; `CREATE TABLE IF NOT EXISTS` on the desktop. *(partially implemented)*
- **Optional Cloud Relay:** ~~For syncing when not on the same network. End-to-end encrypted.~~ — **not implemented.**

---

## The 9 Categories (as designed)

> The shipped app has **12**: Ideas, Cycle and Bad Habits were added later and are not
> described below. See README.md for the full module list.

Every entry is timestamped and belongs to one of these categories. The Android app has a dedicated interactive screen for each. The Windows app has equivalent input forms (keyboard-optimized).

### 1. Water Intake
- **Input (Android):** Full-screen pastel water bottle with vertical drag gesture. Drag up = fill (refill), drag down = lower (drink). Shows ml consumed/added as a pill notification.
- **Input (Windows):** Numeric input or slider.
- **Data stored:** `{ timestamp, ml_amount, type: "drink" | "refill", bottle_capacity_ml }`
- **Visuals:** Animated wave on water surface. Tick marks at 25/50/75%. Capacity picker (200ml–2000ml, default 900ml).
- **Background:** Blue sky, waves at bottom, koi fish shapes, cherry blossom petals, bamboo stalk.
- **Dashboard card:** Shows current ml / goal ml.
- **Notifications:** Configurable hydration reminders (e.g., every 2 hours). Gentle pastel notification: "Time for a sip?"

### 2. Food Intake
- **Input (Android):** Four meal slots (Breakfast, Lunch, Dinner, Snacks). Tap to log. Each slot has a text description field and optional photo.
- **Input (Windows):** Same slots, keyboard-optimized text fields.
- **Data stored:** `{ timestamp, meal_type, description, photo_path? }` — note: `photo_path` is unused; **meal photo attachments are not implemented.**
- **Visuals:** Meal slot cards with icons (sun, cloud-sun, moon, cookie). Green left-border when logged.
- **Background:** Bamboo tree, noren curtain, chopstick/bowl shapes, cherry blossom petals.
- **Dashboard card:** Shows X/4 meals logged.
- **Notifications:** Around typical meal times, nudge to log. Learns user's schedule over time.
- **NOT calorie counting** — this is a "what did I eat" journal, not a diet tracker.

### 3. Bathroom Breaks
- **Input (Android):** Quick-tap "Log now" button. Auto-timestamps. Optional type selector (simple icons, no text needed). Optional short note.
- **Input (Windows):** Same quick-log button.
- **Data stored:** `{ timestamp, type?, note? }`
- **Visuals:** Vertical timeline of today's entries. Minimal friction — this needs to be a 1-tap action.
- **Background:** Zen garden ripple circles, stones, bamboo.
- **Dashboard card:** Shows count of breaks today.
- **Notifications:** None by default.

### 4. General Health
- **Input (Android):**
  - **Energy level:** Slider or tappable 1–10 scale. Can be logged multiple times per day.
  - **Daily rating:** 1–10 overall score, typically logged at end of day.
  - **Symptom tags:** Tap common symptoms (headache, fatigue, nausea, cramps, dizziness) or add custom. Toggleable on/off.
  - **Notes:** Free-text field for anything health-related.
- **Input (Windows):** Same controls, keyboard shortcuts for common symptoms.
- **Data stored:** `{ timestamp, energy_level?, daily_rating?, symptoms: string[], note? }`
- **Visuals:** Energy slider with teal fill. Rating dots. Symptom tags as togglable pills.
- **Background:** Large bonsai tree, lotus, health check circle.
- **Dashboard card:** Shows current energy level.

### 5. Sleep
- **Input (Android):**
  - **Bedtime + wake time:** Clock-style input or simple time pickers.
  - **Wake-ups:** Tap to mark interruptions on a night timeline. Each wake-up is a red segment on a sleep quality bar.
  - **Total hours:** Auto-computed from bedtime and wake time.
  - **Quality score:** Derived from total hours minus wake-up penalties.
- **Input (Windows):** Time pickers, wake-up count input.
- **Data stored:** `{ date, bedtime, wake_time, wake_ups: timestamp[], total_hours, quality_score }`
- **Visuals:** Night-sky themed. Sleep quality bar showing purple (sleeping) and coral (wake-up) segments. Moon and stars in background.
- **Background:** Crescent moon, stars, layered cloud-mountains, bamboo.
- **Dashboard card:** Shows total hours (e.g., "7h 20m").

### 6. Emotions
- **Input (Android):**
  - **Mood picker:** 6 illustrated emoji faces: Happy, Calm, Sad, Angry, Anxious, Tired. Each with a distinct pastel background color.
  - **Multi-log:** Can log emotions at different times throughout the day.
  - **Optional note:** Short text to add context.
- **Input (Windows):** Same mood picker, text field.
- **Data stored:** `{ timestamp, emotion: string, note? }`
- **Visuals:** Mood faces in a grid. Day arc — a color gradient bar showing emotional flow over the day (each emotion maps to a color).
- **Background:** Sakura branch in full bloom with scattered petals, paper lantern.
- **Dashboard card:** Shows current/last logged emotion.

### 7. Interactions with Others (Journal/Diary)
- **Input (Android):**
  - **Tag people:** Create recurring contact tags (e.g., "Mom", "Alex"). Tap to add.
  - **Interaction quality:** Quick 1–5 star rating.
  - **Journal entry:** Free-text diary about interactions. This is the most writing-heavy section.
  - **Reflection prompts:** Optional gentle prompts to guide journaling (can be disabled).
- **Input (Windows):** Full keyboard for longer journal entries. This screen is better on PC.
- **Data stored:** `{ timestamp, people: string[], quality_rating, journal_text, prompt_used? }`
- **Visuals:** Person tags as pills, star rating, text area.
- **Background:** Japanese bridge, pagoda, bamboo, cherry blossoms.
- **Dashboard card:** Shows count of entries today.
- **Notifications:** Evening check-in prompt.

### 8. Chores
- **Input (Android):**
  - **Checklist:** Add tasks, check them off. Grouped by category.
  - **Time tracking:** Start/stop timer per chore, total time logged.
  - **Roadmap:** Weekly/monthly planner view of recurring chores.
  - **Accomplishments:** Streak counter, total time, completion rate.
- **Input (Windows):** Same checklist, bulk editing, drag-to-reorder.
- **Data stored:** `{ date, tasks: [{ name, category?, completed, time_spent_min?, completed_at? }] }`
- **Visuals:** Progress bar (done/total). Checklist with green checkmarks. Time displayed per chore.
- **Background:** Zen rock garden with rake patterns, stones, bamboo.
- **Dashboard card:** Shows X/Y done.

### 9. Hobbies (Quality Time)
- **Input (Android):**
  - **Log activity:** What hobby, how long (in 5-minute increments).
  - **Categories:** Tag hobbies (Reading, Guitar, Sketching, etc.). Each has an assigned color.
  - **"+5 min" quick buttons** per hobby for fast logging.
  - **Weekly goal:** Set a target for hobby hours per week (default: 2h/day = 120 min).
- **Input (Windows):** Same, with keyboard shortcuts.
- **Data stored:** `{ timestamp, hobby_name, duration_min, color }`
- **Visual centerpiece — Origami Crane Bowl:**
  - A large, shallow, see-through glass bowl in the center of the screen.
  - **1 origami paper crane appears for every 5 minutes of hobby time logged.**
  - Cranes are color-coded by hobby (e.g., purple = Reading, coral = Guitar, teal = Sketching).
  - **Crane design:** Proper origami crane silhouette with faceted polygons — two prominent peaked wings (each with lighter front facet and darker back facet), angular body, thin neck with head, small forked tail. Multiple opacity levels create the "folded paper" look. Reference: classic side-view origami crane.
  - **Animation:** When a crane is added, it falls from above the bowl, spinning/rotating, and bounces 2–3 times off the other cranes before settling into its final position.
  - **Three visual stages:**
    1. **Filling the bowl (cranes 1–19):** Cranes settle inside the bowl, layering from bottom to top, filling the full width of the bowl at each level.
    2. **The mountain (cranes 20–36):** Once the bowl is full, cranes pile on top above the rim. The mountain starts wide (matching bowl width) and gradually narrows to a peak. Pyramid shape, not a column.
    3. **Overflow (crane 37+):** Past 3 hours (36 cranes), new cranes fall toward the mountain peak, bounce off, and get launched sideways — alternating left and right — landing beside the bowl. They literally get rejected because the bowl is too full.
  - **Bowl rendering:** The bowl is drawn with an opaque background matching the page color behind the cranes, then the glass outline on top. This prevents cranes from clipping through the bottom. No clipPath needed.
- **Background:** Origami crane silhouettes, koi pond, bamboo, cherry blossom petals.
- **Dashboard card:** Shows total minutes today.

---

## Android App — Navigation & UX

### Home Dashboard
- **Layout:** 3×3 grid of category cards. Each card shows: icon, category name, today's quick summary value.
- **Cards fill most of the screen** — they should be tall enough to be easily tappable.
- **Tap** any card to open its detail screen.
- **Background:** Japanese-inspired — Mt. Fuji silhouette, torii gate, cherry blossoms, bamboo, gentle wave forms. Subtle, behind the cards.

### Category Detail Screens
- **Navigation:** Swipe left/right (click-and-drag on desktop, touch swipe on mobile) to move between category screens. Navigation dots at the bottom.
- **Home dot:** A small square dot on the far left of the nav bar is ALWAYS visible. Tap it to return to the dashboard. It's styled differently (square vs round dots) so it reads as "home."
- **Back arrow:** Top-left corner, tapping returns to dashboard.
- **Each screen has:** A unique pastel background color, Japanese-inspired decorative SVG elements, and interactive input elements specific to that category.

### Notifications
- **Hydration reminders:** Configurable interval (e.g., every 2h).
- **Meal logging:** Around typical meal times.
- **Evening check-in:** End-of-day prompt to log sleep, emotions, interactions, and review chores.
- **All optional and silenceable.** Per-category notification timing.

### On-Device Analytics (Simple)
- **Daily summary:** End-of-day recap card showing all categories at a glance.
- **Weekly trends:** Line/bar charts per category. Kept simple — deep analysis lives on Windows.
- **Streaks + goals:** Streak counters for consistent logging, goal progress rings.

---

## Windows App (Electron + React)

### Data Dashboard
- **Timeline browser:** Scroll through days, weeks, months. See all 9 categories overlaid on a single timeline. Filter by category.
- **Correlation explorer:** **— not implemented.** Interactive scatter/heatmap showing how categories relate (e.g., does sleep quality affect mood? Do chores relate to energy?).
- **Calendar heatmap:** GitHub-style contribution grid for wellness. Color intensity = how active that day was across all categories.

### Input Capability
- All 9 categories available for input on Windows. **Keyboard-optimized** (text fields, dropdowns, hotkeys).
- **Bulk entry:** Forgot to log yesterday? Backfill multiple entries at once with date pickers.
- **Diary expansion:** The Interactions journal is better on PC — full keyboard for longer entries.
- **Bidirectional sync:** Entries created on Windows push to Android on next sync. Same conflict resolution.

### Local LLM Integration

**Model:** Llama 3.1 8B Instruct Q8_0 GGUF (~9GB VRAM). User has 16GB VRAM (NVIDIA GPU).

**Runtime:** node-llama-cpp with CUDA. Native N-API addon, loads GGUF models directly. No Ollama middleman, no HTTP overhead.

**Pipeline:**
```
User data (SQLite) → Context builder (TS) → Prompt template → node-llama-cpp → Structured output → UI render
```

1. **Context builder:** A TypeScript module that queries the SQLite DB, aggregates data for the requested time window, and formats it into a structured prompt.
2. **Prompt templates:** Different templates for different analysis types:
   - **Weekly portrait:** Every Sunday, generates a narrative summary. Example: "This week you slept better but skipped hydration on work days. Your mood dipped on Wednesday — the same day you logged no hobbies."
   - **Pattern detection:** Identifies recurring correlations. Example: "You tend to feel anxious on days you skip hobbies."
   - **Conversational Q&A:** Ask questions about your data. Example: "How was my sleep last month?" "When am I most productive?"
   - **Monthly deep dive:** *(implemented — Insights page "Monthly Deep Dive" button, 30-day window)*. Longer narrative analysis; charts, trends and PDF export remain **not implemented.**
3. **Streaming responses:** node-llama-cpp supports token streaming, so portrait text appears word-by-word in the UI.

### Storage & Export
- **Master SQLite DB:** Windows is the source of truth for long-term storage. Full history, never pruned.
- **Backup + export:** **— not implemented.** One-click backup to local folder. Export as CSV, JSON, or PDF reports. (The database file itself can be copied manually; see README.md for its location.)
- **Data retention:** Android can optionally prune old entries (> 3 months) to save space. Windows keeps everything.

---

## Design Language

### Visual Style
- **Pastel illustrated** throughout both apps. Soft, warm colors. No hard borders.
- **Font:** Nunito (rounded sans-serif from Google Fonts). Weights: 400 (regular), 500 (medium).
- **No harsh contrasts.** Text colors are muted versions of the background (e.g., #2A5A80 on light blue, #72243E on pink).

### Color Palette (Per Category)
| Category      | Card Background | Screen Background | Text Color |
|---------------|----------------|-------------------|------------|
| Water         | #D4E9F7        | #C8DDF0           | #2A5A80    |
| Food          | #D6EDCC        | #D2E8C8           | #27500A    |
| Bathroom      | #F5D6E3        | #F0D0DC           | #72243E    |
| Health        | #CCE8DD        | #C4E2D6           | #085041    |
| Sleep         | #DEDCF7        | #CCC8EE           | #3C3489    |
| Emotions      | #F5E5C4        | #F0DFB8           | #633806    |
| Interactions  | #F0D6CA        | #EDD0C2           | #712B13    |
| Chores        | #E2E0D8        | #DDD8CE           | #444441    |
| Hobbies       | #F0D2E6        | #EACCE0           | #72243E    |

### Japanese-Inspired Backgrounds
Each category screen has subtle SVG decorative elements:
- **Cherry blossom petals** (small circles in rgba(237,147,177,.2–.35)) scattered in corners
- **Bamboo stalks** (thin strokes growing from bottom edge with leaf branches)
- **Zen garden patterns** (concentric circles, raked lines) for Bathroom and Chores
- **Moon and stars** for Sleep
- **Sakura branches** (branching paths with petal clusters) for Emotions
- **Origami crane silhouettes** for Hobbies
- **Wave patterns** (seigaiha-style curves) for Water
- **Torii gate** and **Mt. Fuji silhouette** for the Dashboard
- **Japanese bridge** for Interactions
- **Noren curtain** shapes for Food

All backgrounds are very subtle (opacity 0.03–0.12) so they don't compete with the UI.

### Input Elements
- **Cards:** `background: rgba(255,255,255, 0.4)`, rounded corners (14px), no borders.
- **Text inputs:** Small font (10px), background tinted to match the page color (e.g., rgba of the page bg at ~0.25 opacity), text color matches the category's text color (#2A5A80 etc.).
- **Buttons/pills:** Rounded (20px radius), semi-transparent backgrounds, category-colored text.
- **Sliders:** Thin track (6px) with rounded fill, white background.

---

## Shared Data Model (SQLite)

Both apps use the same SQLite schema. The sync layer ensures both copies stay in sync.

### Tables

```sql
-- Every entry across all categories
CREATE TABLE entries (
  id TEXT PRIMARY KEY,           -- UUID
  category TEXT NOT NULL,        -- water|food|bathroom|health|sleep|emotions|interactions|chores|hobbies
  timestamp INTEGER NOT NULL,    -- Unix ms
  date TEXT NOT NULL,            -- YYYY-MM-DD (for daily grouping)
  data TEXT NOT NULL,            -- JSON blob (category-specific)
  version INTEGER DEFAULT 1,     -- Incremented on edit
  modified_at INTEGER NOT NULL,  -- Unix ms, used for sync
  synced INTEGER DEFAULT 0       -- 0 = needs sync, 1 = synced
);

-- Recurring chore templates
CREATE TABLE chore_templates (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  category TEXT,
  recurrence TEXT,               -- daily|weekdays|weekly|monthly
  created_at INTEGER NOT NULL
);

-- Hobby definitions
CREATE TABLE hobbies (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  color TEXT NOT NULL,            -- Hex color
  created_at INTEGER NOT NULL
);

-- People tags for interactions
CREATE TABLE people (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

-- User settings
CREATE TABLE settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- Sync metadata
CREATE TABLE sync_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  synced_at INTEGER NOT NULL,
  entries_count INTEGER
);
```

### Category-Specific Data (JSON in `entries.data`)

```jsonc
// Water
{ "ml": 250, "type": "drink", "bottle_capacity": 900 }

// Food
{ "meal_type": "breakfast", "description": "Oatmeal with berries", "photo_path": null }

// Bathroom
{ "type": "normal", "note": null }

// Health
{ "energy_level": 7, "daily_rating": null, "symptoms": ["fatigue"], "note": null }

// Sleep
{ "bedtime": "23:30", "wake_time": "06:50", "wake_ups": ["02:15", "04:40"], "total_hours": 7.33, "quality_score": 6 }

// Emotions
{ "emotion": "calm", "note": "Feeling good after morning walk" }

// Interactions
{ "people": ["Mom", "Alex"], "quality_rating": 4, "journal_text": "Great call with Mom...", "prompt_used": null }

// Chores
{ "tasks": [{ "name": "Dishes", "completed": true, "time_spent_min": 12, "completed_at": 1711972800000 }] }

// Hobbies
{ "hobby_name": "Reading", "duration_min": 20, "color": "#AFA9EC" }
```

---

## Build Roadmap

### Phase 1 — Android Core (4–6 weeks)
- Dashboard home screen with 9 category cards
- Water intake screen (already prototyped — pastel bottle with drag)
- Sleep screen (time pickers, wake-up markers, quality bar)
- Emotions screen (mood picker, notes, day arc)
- Food screen (meal slots, tap to log)
- Basic local SQLite storage with Room
- No sync yet

### Phase 2 — Android Complete (4–6 weeks)
- Remaining 5 categories: Bathroom, Health, Interactions, Chores, Hobbies (with crane bowl)
- Notification system (WorkManager)
- Weekly trend charts (simple bar/line charts)
- Streaks and goal tracking
- Android home screen widgets (water, bathroom, mood — log without opening app) **— not implemented.**

### Phase 3 — Windows App v1 (4–5 weeks)
- Electron shell with React + TypeScript
- Data dashboard: timeline browser, calendar heatmap
- Full input capability for all 9 categories (keyboard-optimized)
- SQLite master DB with better-sqlite3
- Bulk entry / backfill with date pickers

### Phase 4 — Sync Layer (3–4 weeks)
- mDNS auto-discovery on local network
- WebSocket server in Electron, client in Android (OkHttp)
- JSON delta sync implementation
- Per-field conflict resolution
- Thorough testing with simultaneous edits

### Phase 5 — LLM Integration (3–4 weeks)
- node-llama-cpp setup with CUDA
- Download and configure Llama 3.1 8B Instruct Q8_0
- Context builder module (query SQLite → format prompt)
- Prompt templates: weekly portrait, pattern detection, conversational Q&A
- Streaming response UI in React
- Monthly deep dive report generation

### Phase 6 — Polish & Extras (3–4 weeks)
- Correlation explorer (scatter/heatmap)
- PDF export for monthly reports
- Optional cloud relay for remote sync
- Android home screen widgets
- Backup system (one-click local backup)
- Data retention settings (Android prune vs. Windows keep-all)
- Performance optimization and testing

---

## Prototyping Notes

Interactive prototypes of 9 of the Android screens (including the crane bowl with physics animation) were built as HTML/CSS/JS widgets during planning. These can serve as direct references for the Jetpack Compose implementations (Android) and React components (Windows). The visual language, colors, spacing, and interaction patterns are all defined in these prototypes.

Key prototype behaviors to preserve:
- Water bottle: vertical drag gesture, wave animation, capacity picker
- Hobbies crane bowl: falling/bouncing physics, three overflow stages, faceted origami crane polygons
- Swipe navigation between screens with momentum detection
- All backgrounds: subtle Japanese-inspired SVG overlays at low opacity
- Dashboard: 3×3 grid, tap to navigate, home dot always visible in nav
