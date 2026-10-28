# Yaad Reminder AI — Offline Persian Voice Reminder & Android MVP Studio

**Yaad (یاد)** is an ultra-minimalist, offline-first Persian reminder and note-taking Android MVP built with Kotlin, MVVM, Room, Vosk offline speech recognition, and Jetpack Compose. This workspace delivers the complete production-ready Android Kotlin modules alongside a live interactive RTL device preview and rule-based Persian NLP test bench.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX decisions have been confirmed from your clarifications and govern the implementation:

- **Confirmed Decision 1 — Presentation & Interactive Preview**: Build a two-column desktop workspace pairing an interactive RTL Android device simulator (with live Persian NLP categorization, voice/text expandable FAB, and local Room-equivalent timeline) alongside a structured Kotlin Source Explorer & NLP Test Bench.
- **Confirmed Decision 2 — Staged Execution Flow (Phase 1 Active, Phase 2 Ready)**: Focus the primary Kotlin architecture view on **Phase 1** (Room Database layer: `ItemEntity`, `Category`, `Converters`, `ItemDao`, `YaadDatabase`, and the rule-based Persian `TextProcessor`) while providing an interactive confirmation gate in the workspace to unlock and inspect **Phase 2** (Repository, `TimelineViewModel`, Jetpack Compose UI, Vosk offline speech recognizer, and Gradle asset automation).
- **Confirmed Decision 3 — Android UI Toolkit**: Implement the Android presentation layer in **Jetpack Compose** using a clean borderless `LazyColumn` timeline and a smooth horizontally expanding Floating Action Button (`AnimatedContent` / `animateContentSize`) with microphone and borderless text input states.
- **Confirmed Decision 4 — Persian Relative Time to Epoch Millis**: `TextProcessor` normalizes Persian (`۰-۹`), Arabic (`٠-٩`), and Latin (`0-9`) numerals and resolves Persian temporal patterns (such as `فردا`, `پس‌فردا`, `امروز`, `ساعت ۸`, `ساعت ۱۴:۳۰`, `۸ شب`, `۹ صبح`) into concrete `Long?` epoch millisecond timestamps relative to the current clock time.

---

### 1. Overview & Core Concept

- **What It Does**: Captures spoken or typed Persian thoughts in a single tap, automatically classifies them offline using deterministic Persian keyword matching (`REMINDER`, `OCCASION`, `IDEA`, or `UNCATEGORIZED`), extracts relative date/time expressions into concrete epoch timestamps, and displays them in a distraction-free vertical timeline ordered by creation time.
- **Target Audience / Persona**: Persian-speaking users who want an instant, privacy-preserving, zero-latency voice and text capture tool without cloud dependencies, nested menus, or visual clutter, as well as Android engineers inspecting and exporting the modular Kotlin MVVM codebase.
- **Key Value**: Bridges a functional interactive simulation of the exact Android experience with copy-ready, modular Kotlin code and automated Vosk Persian model (`vosk-model-small-fa-0.4`) build scripts.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Minimalist Timeline & Expandable FAB Flow**: On the simulated Android screen, the user views a clean RTL vertical timeline of saved items. Tapping the bottom Floating Action Button (default microphone state) or beginning to type smoothly expands the control horizontally into a borderless Persian input field (`"بنویسید یا صحبت کنید..."`) with instant live voice dictation simulation or text submission.
  2. **Live Rule-Based Persian NLP Inspection**: Typing or selecting a sample Persian phrase (e.g., `"یادم باشه فردا ساعت ۸ صبح بیدار شم"` or `"ایده برای طراحی رابط کاربری مینیمال"`) immediately highlights matched category keywords, extracted Persian time tokens, and the computed epoch timestamp in real time.
  3. **Staged Kotlin Codebase Review & Phase Confirmation**: Engineers start in **Stage 1 (Room & TextProcessor)** to inspect, test, and copy the database entities, DAO (`Flow<List<ItemEntity>>`), and regex processor. Clicking **"Confirm Stage 1 & Unlock Stage 2 (UI + Vosk + Gradle)"** reveals the full MVVM Repository, `TimelineViewModel`, Jetpack Compose screen, `VoskSpeechHelper`, and `build.gradle.kts` / terminal automation scripts.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Ultra-minimalist editorial precision paired with authentic Persian RTL typography. No card-within-card nesting, no decorative status dots, and no pill badge clutter.
  - *Color Palette & Mood*:
    - **60% Dominant Neutral Canvas**: Crisp warm alabaster (`#FAF9F6` / `bg-stone-50`) for the studio canvas and pure paper white (`#FFFFFF`) inside the minimalist Android viewport.
    - **30% Structural Surfaces**: Deep charcoal ink (`#111827`) for primary typography, muted stone (`#6B7280`) for unboxed inline metadata separated by `·`, and subtle hairline borders (`#E5E7EB`).
    - **10% Accent Budget**: Deep Persian Cobalt (`#1D4ED8`) for primary interactive focus and active tab states, paired with semantic text indicators for categories (`یادآوری · REMINDER`, `مناسبت · OCCASION`, `ایده · IDEA`, `عمومی · UNCATEGORIZED`).
  - *Typography & Hierarchy*:
    - **Persian UI & Prose**: `Vazirmatn` (loaded via Google Fonts) for crisp, authentic RTL Persian legibility across headlines, timeline items, and the borderless input field.
    - **Latin Display & Workspace UI**: `Plus Jakarta Sans` for workspace navigation and architectural documentation.
    - **Code & Timestamps**: `JetBrains Mono` with `tabular-nums` for Kotlin source code, epoch timestamps, and regex match indices.
  - *Component Styling & Layout*:
    - Full 1440px desktop layout structured as a 12-column grid: a 5-column interactive Android device preview on the left/right and a 7-column Kotlin Architecture & NLP Workbench alongside it.
    - Strictly adheres to the **Top Bar Contract**: Zone 1 brand title (`Yaad reminder AI`), Zone 2 concise navigation links (`Stage 1: Room & NLP`, `Stage 2: UI & Vosk`, `NLP Test Bench`, `Model Automation`), and Zone 3 primary action (`Export Kotlin MVP`).
    - Zero-pill metadata discipline: timeline rows display category, extracted Persian time, and creation time as quiet unboxed inline text separated by `·` (`یادآوری · فردا ۰۸:۰۰ · ۱۴:۳۰`).

- **Interactive Feedback & Motion**:
  - Smooth horizontal expansion of the bottom FAB (`cubic-bezier(0.16, 1, 0.3, 1)`, $\le 200\text{ms}$) transitioning between the compact microphone button and the full-width borderless input bar with voice waveform feedback and submit trigger.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Staged Delivery with Interactive Stage Gate**:
  - *Chosen Approach*: Present Stage 1 (`ItemEntity`, `ItemDao`, `YaadDatabase`, `TextProcessor`) upfront and allow immediate interactive unlocking of Stage 2 (`ItemRepository`, `TimelineViewModel`, Jetpack Compose `TimelineScreen`, `VoskVoiceManager`, and Gradle/terminal model automation).
  - *Why*: Directly honors your execution instruction to inspect the Room database layer and `TextProcessor` first while ensuring the live preview and Stage 2 code are immediately accessible on confirmation without waiting for another build cycle.
  - *Alternatives Considered*: Hiding Stage 2 behind a static placeholder (rejected because every control must be functional and self-contained).

- **Decision 2 — Deterministic Persian Numeral & Relative Time Resolution**:
  - *Chosen Approach*: `TextProcessor` normalizes all Eastern Arabic/Persian digits (`۰۱۲۳۴۵۶۷۸۹` and `٠١٢٣٤٥٦٧٨٩`) to standard integers before running regex extraction for day offsets (`امروز`, `فردا`, `پس‌فردا`) and clock patterns (`ساعت ۸`, `ساعت ۲۰:۱۵`, plus `صبح`/`عصر`/`شب` meridiem modifiers), outputting a deterministic `Long?` epoch timestamp via `java.util.Calendar`.
  - *Why*: Runs 100% offline with zero external NLP model overhead, microsecond latency, and full unit-test predictability.
  - *Alternatives Considered*: Third-party Persian date libraries or LLM parsing (violates strict offline MVP simplicity).

- **Decision 3 — Dual Vosk Model Automation (Gradle Task + Copy-Paste Terminal Script)**:
  - *Chosen Approach*: Provide both a self-contained Gradle task (`downloadVoskPersianModel` wired to `preBuild`) in `app/build.gradle.kts` and a one-click copyable `curl` + `unzip` shell command for Android Studio Terminal that downloads `vosk-model-small-fa-0.4.zip`, extracts it, and moves its contents into `app/src/main/assets/model-fa`.
  - *Why*: Gives complete reliability whether building via Gradle CI or manually initializing assets in Android Studio.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     YAAD REMINDER AI — MVVM ARCHITECTURE                     │
└──────────────────────────────────────────────────────────────────────────────┘

  ┌───────────────────────────────┐      ┌───────────────────────────────────┐
  │     Jetpack Compose UI        │      │     Vosk Offline Speech Engine    │
  │  • Minimalist LazyColumn      │      │  • AudioRecord (16kHz Mono PCM)   │
  │  • Expandable Voice/Text FAB  │◄────►│  • Model: assets/model-fa         │
  │  • RTL Borderless TextField   │      │  • Emits Partial & Final Persian  │
  └──────────────┬────────────────┘      └─────────────────┬─────────────────┘
                 │                                         │
                 │ Submit Raw Persian String               │ Final Recognized Text
                 ▼                                         ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │                        TimelineViewModel (StateFlow)                     │
  │  • Exposes timelineItems: StateFlow<List<ItemEntity>>                    │
  │  • Exposes fabExpanded: StateFlow<Boolean> & isListening: StateFlow      │
  │  • Coordinates TextProcessor classification + Repository persistence     │
  └──────────────┬─────────────────────────────────────────┬─────────────────┘
                 │                                         │
                 │ 1. process(rawText, nowMillis)          │ 2. insert(itemEntity)
                 ▼                                         ▼
  ┌──────────────────────────────────────┐  ┌────────────────────────────────┐
  │     TextProcessor (Static NLP)       │  │     Room Local Persistence     │
  │  • Normalizes ۰-۹ / ٠-٩ digits       │  │  • ItemDao:                    │
  │  • Keyword Categorizer:              │  │    - insertItem(ItemEntity)    │
  │    - REMINDER (یادم باشه, بیدار...)   │  │    - getAllItems():            │
  │    - OCCASION (تولد, سالگرد...)      │  │      Flow<List<ItemEntity>>    │
  │    - IDEA (ایده, فکر, طرح...)        │  │      ORDER BY createdAt DESC   │
  │    - UNCATEGORIZED (Fallback)        │  │  • ItemEntity:                 │
  │  • Regex Time Extractor -> Long?     │  │    id, originalText, category, │
  └──────────────────────────────────────┘  │    extractedTime, createdAt    │
                                            └────────────────────────────────┘
```

- **Data Model & State**:
  - `ItemCategory` (Kotlin `enum class`): `REMINDER`, `OCCASION`, `IDEA`, `UNCATEGORIZED`.
  - `ProcessedTextResult` (Kotlin `data class`): Holds `cleanedText: String`, `category: ItemCategory`, `extractedTimeMillis: Long?`, `matchedKeywords: List<String>`, and `matchedTimeExpression: String?`.
  - `ItemEntity` (Room `@Entity(tableName = "items")`):
    - `id: Long = 0` (`@PrimaryKey(autoGenerate = true)`)
    - `originalText: String`
    - `category: ItemCategory` (persisted via Room `@TypeConverter`)
    - `extractedTime: Long?` (nullable epoch timestamp in milliseconds)
    - `createdAt: Long` (epoch timestamp in milliseconds, indexed for `ORDER BY createdAt DESC`)

- **Interactive Component & State Mapping**:
  - **Interactive Android Simulator (RTL)**:
    - *Timeline Feed*: Renders saved items in reverse chronological order (`createdAt DESC`) with clean unboxed metadata (`category · extractedTime · createdAt`) and a subtle delete action on hover/tap.
    - *Expandable FAB*: Tapping the microphone FAB toggles voice recognition simulation (using Web Speech API `fa-IR` when supported in the browser plus realistic Persian voice presets) or expands smoothly into the borderless Persian text input (`"بنویسید یا صحبت کنید..."`). Submitting runs the exact TypeScript port of `TextProcessor` and persists to `localStorage`.
  - **Persian NLP Test Bench**:
    - Allows testing custom Persian sentences or one-tap preset phrases across all 4 categories and time formats, showing live token extraction, regex group matches, and resulting `ItemEntity` JSON.
  - **Modular Kotlin Source Explorer**:
    - Organizes the Android source files into **Stage 1 (Room Database + TextProcessor)** and **Stage 2 (Repository + ViewModel + Compose UI + Vosk + Gradle Automation)** with syntax-highlighted code views, copy-to-clipboard buttons, and single-click bundle download.
