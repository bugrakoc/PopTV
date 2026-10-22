# PopTV

An IPTV player for Android TV / Google TV and Android phones, built with Kotlin, Compose for TV, Material 3 and
Media3 (ExoPlayer).
You sign in with a single M3U playlist URL from your provider. PopTV sorts the playlist into **Live TV**,
**Movies** and **TV Shows**, shows a **program guide**, and remembers where you stopped watching.

PopTV does not include any content or playlists. It plays what your own provider gives you.

## Features

**Playlist**
- One M3U URL (the username and password are part of the URL). It is checked, downloaded with progress, and
  you get a clear message on errors: invalid URL, network failure, HTTP 401/403/404, an empty playlist, or a
  response that isn't a playlist.
- The URL is stored encrypted with an Android Keystore AES-GCM key. "Log out / change playlist" in Settings
  deletes everything.
- Refresh on demand, plus a background refresh every 24 h by default (configurable). Favorites, history and
  resume positions survive refreshes. They are keyed by `tvg-id`, or by a hash of the stream URL path with
  host, credentials and query removed.
- A background refresh that suddenly returns far fewer entries is rejected; this is typical of "account
  expired" or "max connections" responses. Your library stays as it was.
- Streaming parser for very large playlists (hundreds of thousands of entries). It handles `url-tvg`/
  `x-tvg-url`, all common `#EXTINF` attributes, `#EXTGRP`, `#EXTVLCOPT` user agent and referrer,
  `#EXTHTTP`, Kodi-style `url|User-Agent=…`, quoted and unquoted attributes, commas in titles, CRLF, BOM,
  UTF-16, and non-UTF-8 lines (Windows-1254 fallback).
- Configurable User-Agent (VLC by default), cleartext HTTP, and redirects in both directions between HTTP
  and HTTPS.

**Classification**
- Layered, data-driven heuristics, highest priority first: `tvg-type` → URL path (`/live/`, `/movie/`,
  `/series/`, …) → group-title keywords in English and Turkish → file extension → title patterns (`S01E03`,
  `1x03`, `Season 1 Episode 3`, `Sezon 1 Bölüm 3`, `1. Sezon 3. Bölüm`, `S1 B3`, a year like `(2019)`).
- Episodes are grouped into Show › Season › Episode with natural sorting. The language prefix is kept, so
  "EN - X" and "TR - X" stay separate shows.
- Settings › Categories lets you override a category's type (Live / Movies / Shows / Hidden).
- Display names drop country prefixes, quality tags and brackets. The original name stays searchable, and a
  setting shows original names instead.

**Live TV**
- Category sidebar (Favorites, Recently watched, All channels, then the playlist groups), channel logos,
  channel numbers (`tvg-chno`, otherwise list order), and now/next with a progress bar.
- Hold OK on a channel, movie, show or episode for a menu (play, favorite, mark watched, hide its category,
  ...). Hold OK on a live category to hide it.
- Adult categories ("FOR ADULTS", "XXX", "+18", ...) are hidden by default; Settings › Content › Hide adult
  content turns this off.
- While watching:
  - **Up/Down** or **Channel ±** zaps between channels (the direction can be inverted in Settings).
  - **OK/Left/Right** opens the channel list overlay.
  - **Number keys** jump to a channel number.
  - **Last channel** comes from the remote key or the overlay button.
  - An info banner with now/next appears on every zap.
- Automatic reconnect with exponential backoff on errors and stalls, with a status message instead of a
  black screen. If decoding fails, playback retries automatically with software decoders.

**Program guide (EPG)**
- Sources, tried in this order:
  1. `url-tvg` / `x-tvg-url` from the playlist header.
  2. The Xtream Codes `xmltv.php` URL derived from a `get.php` playlist URL.
  3. A manual XMLTV URL from Settings.
- Supports gzip (`.xml.gz`) and plain XML. Uses a streaming parser and keeps only 6 h of past and 3 days of
  future programmes.
- Matches guide channels by `tvg-id`, then by normalized channel name. Name matches respect the channel's
  country (from its name prefix, group title or `tvg-id` suffix), so "DE: Eurosport 1" never gets the
  Turkish Eurosport guide; a channel without a guide from its own country shows none. A `tvg-id` that many
  unrelated channels share is treated as a provider placeholder and ignored. Time offset is configurable.
- A full-screen grid you can move around with the D-pad, plus a programme detail dialog. The guide refreshes
  every 12 h by default.
- If no guide is found, PopTV says so once and works normally.

**Movies & TV Shows**
- Poster grids with categories and sort options. Detail pages offer Play / Resume / Start over, favorites and
  a watched toggle; shows add a season selector and an episode list with progress and watched marks.
- Resume positions are saved every 10 s and on pause, stop, back and when the app goes to the background.
  Content counts as watched at about 93%.
- The home screen has a **Continue watching** row and an **Up next** countdown with autoplay, which can be
  turned off in Settings.
- Optional TMDB metadata (description, backdrop, rating, genres, cast) in English or Turkish, used only if you
  enter an API key.

**Player**
- Media3 ExoPlayer with HLS, DASH, progressive and MPEG-TS support (IPTV-friendly TS extractor flags).
- Decoder mode: **Hardware (default) / Prefer software / Auto**. It works through a custom
  `MediaCodecSelector` together with `setEnableDecoderFallback(true)`. Tunneled playback is an option and is
  off by default.
- Default audio for movies and episodes is the **original language**: TMDB's original language when a key
  is set (using the TMDB id from the Xtream API when the provider has one, else a title search), else a
  track labelled "original", else the only language besides a dub in the device language (Turkish +
  Japanese → Japanese), else English.
- Default subtitles depend on the audio that plays: English audio → English, Turkish audio → off, any other
  language → Turkish. A missing language falls back to the "other languages" choice, then English. Full
  subtitles are preferred over SDH and forced tracks. All of this is adjustable in Settings; live TV keeps
  the device language without subtitles.
- Audio track, subtitle track (embedded or sidecar) and subtitle style; speed, aspect/zoom; a stream info
  panel showing resolution, codec, HDR type, bitrate, decoder name and whether it is hardware or software.
- Audio passthrough (AC-3/E-AC-3) when the TV/AVR reports support.
- MediaSession (remote media keys), audio focus, screen kept on.

**UI**
- Dark Compose for TV theme with visible focus states, focus restored when you return to a screen, paging
  for big lists, and downsampled images.
- Global search across channels, movies and shows. It ignores accents and handles Turkish İ/ı/I/i. Voice
  input is offered when the device supports it.
- English and Turkish UI, following the device language.

**Phones**
- On phones and tablets PopTV uses a touch UI with a bottom bar (Home, Live TV, Movies, TV Shows, Guide).
  Library, favorites, history and settings work the same as on TV.
- Press and hold an item for its menu; categories are picked from a list at the top of each screen.
- The guide scrolls sideways; the arrows move it two hours earlier or later.
- The player goes fullscreen in landscape. Tap to show the controls; on videos, double-tap the left or right
  half to skip 10 s; on live TV, swipe up or down to change channel or open the channel list.

## Building

Requirements: Android Studio (recent) or the command line with JDK 17+ and the Android SDK (compileSdk 37).
The Gradle wrapper downloads Gradle 9.8.1.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests (parsers, classifier, EPG, resume logic, …)
./gradlew assembleRelease        # R8-minified, signed with the debug key for sideloading
```

Optional build-time TMDB key: add `tmdb.apiKey=YOUR_KEY` to `local.properties`, which is never committed.
You can also enter a key in Settings › Metadata at any time.

## Installing on the TV (sideload over adb)

1. On the TV, enable developer options: *Settings › System › About*, then press *Android TV OS build* 7
   times.
2. In *Developer options*, enable **USB debugging** / **Network debugging** (the wording varies on TCL
   models).
3. Find the TV's IP address under *Settings › Network & Internet*.
4. From your computer:

```bash
adb connect <TV-IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or the smaller, faster release build:
adb install -r app/build/outputs/apk/release/app-release.apk
```

Accept the debugging prompt on the TV the first time. PopTV then appears in the Google TV apps row; it may
take a minute or you may need to look under *Apps*.

### Entering the playlist URL without typing on the remote

Typing a long URL with the remote is slow. You can pre-fill the login field from your computer. It only fills
the field; nothing is submitted until you press **Sign in**:

```bash
adb shell am start -n pm.koc.poptv/.MainActivity --es playlist_url "'http://provider.example:8080/get.php?username=USER&password=PASS&type=m3u_plus&output=ts'"
```

Keep the inner single quotes so the shell on the TV doesn't cut the URL at `&`.

### Useful logs

```bash
adb logcat -s PopTV-Player PopTV-Decoder PlaylistImporter EpgRepository LibraryIndexer
```

`PopTV-Decoder` lists the decoders available for each codec and their order. `PopTV-Player` logs the chosen
video/audio decoder, the formats, and every playback error and reconnect. URLs in logs have their
credentials redacted.

## Architecture

```
core/      text normalization (Turkish-aware), URL utilities, natural sort, search patterns
domain/    pure logic: stable keys, resume/continue-watching, reconnect policy, season grouping
data/      m3u parser · classifier · XMLTV parser/matcher · Room · DataStore + Keystore · OkHttp ·
           TMDB · repositories · WorkManager workers
player/    Media3 factory, decoder selector, error classification
ui/        Compose for TV screens + ViewModels (Hilt, StateFlow, Paging 3)
ui/phone/  Material 3 phone screens on the same ViewModels; shared dialogs and menus adapt to the device
```

See `PLAN.md` for design decisions.

## Known limitations

- **Single playlist.** Multiple playlists or profiles are not supported.
- **Codecs depend on the TV.** There is no FFmpeg extension. Audio or video codecs the TV cannot decode, and
  cannot pass through, won't play; for audio that often means DTS or TrueHD on TVs without passthrough. Video
  plays silently if only the audio codec is unsupported.
- **No picture-in-picture**, no recording, no catch-up/timeshift.
- **Episode descriptions** are not fetched from TMDB; only show-level and movie metadata are.
- **Sidecar subtitles** only come from HLS/DASH renditions or a `subtitles="…"` playlist attribute. M3U has no
  standard way to declare them.
- **Heuristics can misclassify** unusual playlists. Use Settings › Categories to correct a category.
- **Guide source order is fixed** (header → Xtream → manual). If the playlist's own guide works, a manual URL
  is used only as a fallback.
- **The release build is signed with the debug key** for easy sideloading. Configure a real signing config
  before distributing it.
- PopTV was developed without access to the target TV. Focus behaviour, decoder fallback on specific streams,
  and performance with very large playlists need real-device testing.
