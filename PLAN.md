# PopTV — Implementation Plan

IPTV player for Android TV / Google TV (target: TCL Google TV). Kotlin, Compose for TV, minSdk 28.

## Starting point

The Android Studio "Television > Empty Activity" template (AGP 9.4, built-in Kotlin, Compose for TV,
`tv-material`). It does not use Leanback, so nothing needs migrating; the template's theme, manifest and
Gradle files are adapted in place.

## Architecture

Single-activity app, MVVM, layered:

```
pm.koc.poptv
├── core/            pure helpers: text normalization (Turkish-aware), hashing, url utils, time formatting
├── domain/          pure models + logic: ContentType, StableKey, ResumePolicy, ReconnectPolicy, ...
├── data/
│   ├── m3u/         streaming M3U parser (line-by-line, BOM/CRLF/charset fallback)
│   ├── classify/    data-driven classifier, title/series parser, display-name cleaner
│   ├── epg/         XMLTV streaming parser, timestamp parsing, source resolver, channel matcher
│   ├── db/          Room database, entities, DAOs (Paging 3 sources)
│   ├── net/         OkHttp client (custom UA, redirects), playlist/EPG downloaders, error mapping
│   ├── prefs/       DataStore settings + Android-Keystore-encrypted secrets (playlist URL, TMDB key)
│   ├── tmdb/        optional TMDB client + Room cache
│   ├── repo/        repositories that the UI talks to
│   └── work/        WorkManager workers (playlist 24 h, EPG 12 h, configurable)
├── player/          Media3 factory (renderers, decoder selection, load control, data sources),
│                    reconnect handling, MediaSession
├── di/              Hilt modules
└── ui/              Compose for TV screens + ViewModels (StateFlow), navigation, theme, components
```

Everything that can be tested on the JVM lives in `core/`, `domain/`, `data/m3u`, `data/classify`,
`data/epg` and has no Android dependencies (XML parsing takes an injected `XmlPullParser`; tests use kxml2).

## Dependencies (latest stable as of 2026-10, checked against Maven Central / Google Maven)

Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, tv-material 1.1.0,
Navigation Compose 2.10.2, Lifecycle 2.11.0, Room 2.8.5, Paging 3.5.3, WorkManager 2.12.0, DataStore 1.2.1,
Media3 1.11.1, Hilt 2.60.1 (+ androidx.hilt 1.4.0), OkHttp 5.5.0, Coil 3.6.3, kotlinx-serialization 1.11.0,
coroutines 1.11.0. Tests: JUnit 4.13.2, Truth 1.4.5, kxml2 2.3.0, coroutines-test, MockWebServer 5.5.0.

## Key decisions

- **Secure storage**: `security-crypto`'s EncryptedSharedPreferences is deprecated, so secrets are
  encrypted with an AES-256-GCM key held in the Android Keystore and the ciphertext is stored in DataStore.
- **Stable identifiers**: live channels use `tvg-id` when present and unique in the playlist; otherwise (and
  for duplicates) a SHA-1 of the normalized stream URL path with host, query string and credentials removed.
  Favorites, history and resume positions live in separate tables keyed by this id, so refreshes never wipe
  them. Shows are keyed by a normalized show name (including the language prefix, so "EN - X" and "TR - X"
  stay separate).
- **Import**: streaming parse → batches of 1,000 rows upserted in transactions, tagged with an import
  generation; rows from older generations are deleted only after a successful import (a failed refresh keeps
  the old library). Categories and shows are materialized tables rebuilt after each import.
- **Classification** (highest priority first): category override → `tvg-type` → URL path → group-title
  keywords (EN/TR) → file extension → title patterns. A VOD decision is upgraded to "series" when the title
  has an episode pattern. Rules live in `ClassificationRules` (data) and are unit-tested.
- **Category overrides** update `streams.type`/`hidden` in place (indexed by group-title) and rebuild the
  derived tables, so queries stay simple and fast.
- **Search**: `searchName` column = Turkish-aware fold (İ/I/ı/i → i, diacritics stripped, lowercase ROOT),
  matched with LIKE through Paging.
- **EPG**: candidates = header `url-tvg`/`x-tvg-url` (comma-separated allowed) → derived Xtream
  `xmltv.php` → manual URL. Gzip detected by magic bytes. Window: now −6 h … now +3 days. Matching: tvg-id
  (case-insensitive) first, then normalized channel name. Result stored in `streams.epgChannelId`, recomputed
  after both playlist and EPG imports. "No guide" is reported once per playlist.
- **Player**: Media3 ExoPlayer in a ViewModel (survives recomposition, released in `onCleared`), PlayerView
  (SurfaceView) without its controller, custom Compose overlay. Decoder modes via custom `MediaCodecSelector`
  (hardware-first / software-first / platform default) + `setEnableDecoderFallback(true)`. Optional
  tunneling. Per-stream User-Agent/Referer via a per-item data source factory. Live: TS extractor flags for
  IPTV, larger buffers, retries with exponential backoff, stall watchdog, HLS fallback if a URL without an
  extension is not a progressive stream.
- **Text input on TV**: a focusable surface that opens the IME only on click (avoids focus traps).
- **PiP**: skipped (not straightforward with the single-activity + Compose overlay design on TV).
- **Multiple playlists**: not implemented (single playlist requirement); the schema leaves room for it.
- **Debug convenience**: the login screen can be pre-filled via
  `adb shell am start -n pm.koc.poptv/.MainActivity --es playlist_url "<url>"` (pre-fill only, never auto-login).

## Decisions made during implementation

- **Stable keys are type-independent** (not "tvg-id for live, URL for VOD"), so overriding a category's type
  never orphans favorites or resume positions.
- **Shrink guard**: background/manual refreshes that return < 20% of the previous entry count (with ≥ 500
  before) are rejected. This catches typical "account expired" playlists. Logging in again bypasses it.
- **Guide source order follows the spec** (header → Xtream → manual). The manual URL is therefore a fallback,
  not an override; this is documented in the README. Flipping it is a one-line change in `EpgSources`.
- **Guide replace is transactional**: the XMLTV file goes to a temp file first, then programmes are replaced
  in one transaction, so a failed download or empty file keeps the old guide.
- **Decoder errors** retry once with software-preferred decoders for that stream only. Unrecognized containers
  on extension-less URLs retry once as HLS.
- **Zapping is debounced (350 ms)**: the banner updates immediately and the stream tunes when the user stops
  pressing. This avoids hammering providers with one-connection limits.
- **Channel direction**: D-pad down = next channel by default, with a Settings toggle to invert. Channel ±
  keys always follow channel order.
- **Search** uses folded `searchName` columns and an in-order multi-word LIKE pattern ("kanal d" →
  `%kanal%d%`) instead of FTS, so substring matches work.
- **TMDB** was implemented with the detail screens (milestone 5) rather than milestone 7, because those
  screens are designed around it. It accepts a v3 key or a v4 token, and misses are cached for 3 days.
- **Release builds** are R8-optimized for all packages (the template limited optimization to libraries).
  Enum names are kept because DataStore persists them. They are signed with the debug key for sideloading.
- **Backups are disabled**: the Keystore key cannot be restored on another device anyway.
- **User CAs** are trusted only in debug builds (`debug-overrides`). Cleartext HTTP is allowed everywhere
  because providers need it.

## Milestones

1. Project setup: dependencies, theme, navigation, EN/TR scaffolding, manifest/banner, network security config.
2. M3U parser, Room schema, secure storage, login + import with progress, tests.
3. Classification, series grouping, category management, tests.
4. Live TV browsing, player (hardware decoding, HEVC, fallback), zapping UI.
5. Movies/Shows browsing, detail screens, resume/continue watching, next-episode flow.
6. EPG discovery/download/parse/match, now/next UI, full guide.
7. Search, favorites, recents, Settings, optional TMDB.
8. Polish: focus/performance, error handling, R8 release build, README.

Each milestone: `./gradlew assembleDebug testDebugUnitTest`, fix, commit.
