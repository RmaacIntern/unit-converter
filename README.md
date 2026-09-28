# Unit Converter

A small, offline Android app that converts length, weight, volume, temperature, area
and speed between common units — six categories, six units each, with a Kelvin-based
temperature engine so Celsius, Fahrenheit, Kelvin, Rankine, Réaumur and Delisle all
convert exactly, including Delisle's inverted absolute-zero limit.

Built with Jetpack Compose, a dark "neon" visual theme, and Google Mobile Ads /
User Messaging Platform for consent-gated banner and interstitial ads.

**Package:** `com.aivigil.unitconverter` — built for the RMAAC / Aivigil intern program.

## Features

- Length, Weight, Volume, Temperature, Area, Speed — 6 units each, 36 total
- Temperature via explicit to/from-Kelvin function pairs (no linear-multiplier shortcuts)
- Decimal-places setting (0–6) and a default-category setting, both persisted in DataStore
- Four explicit UI states (Loading / Empty / Content / Invalid) driven by a pure reducer
- Rotation- and process-death-safe input via `SavedStateHandle`
- UMP consent gates every ad request; a decline leaves the app fully usable with zero ads
- Bottom banner ad and interstitial ads on category/unit clicks
- **Firebase Remote Config & Analytics Integration:** Connected to Firebase (`unitconverter-app-ebc23`) to remotely control ad parameters (`banner_show` and `interstitial_show`) dynamically in real-time.

See [`SPEC.md`](SPEC.md) for the full requirements this was built against,
[`DESIGN.md`](DESIGN.md) for the screen-by-screen UI spec, and
[`ARCHITECTURE.md`](ARCHITECTURE.md) for how it's put together.

> **Note on Firebase Remote Config Testing:**
> This app is connected to Firebase Remote Config for remote ad management (`unitconverter-app-ebc23`).
> - `banner_show` (`Boolean`): Toggles bottom banner ads on/off remotely in real-time.
> - `interstitial_show` (`Boolean`): Toggles full-screen interstitial ads on/off remotely in real-time.
> - In Debug builds, the Remote Config fetch interval is `0` seconds, enabling instant remote parameter updates upon launch.

## Tech stack

| | |
|---|---|
| UI | Jetpack Compose, Material 3 components with a custom dark palette |
| Language | Kotlin 2.0.21 |
| Build | AGP 8.7.3, Gradle 9.6.0 |
| SDK | `minSdk` 24 · `compileSdk` 35 · `targetSdk` 35 |
| Persistence | Jetpack DataStore (Preferences) |
| Ads | Google Mobile Ads SDK + User Messaging Platform (UMP) |
| Analytics | Firebase Analytics, Crashlytics, Remote Config (optional — see below) |
| Tests | JUnit4, `ConversionEngineTest` |

## Quick start

1. Open this folder in **Android Studio** (File → Open) and let Gradle sync.
2. Run on an emulator or a real device (▶). The app builds and runs with **no
   `google-services.json`** — Firebase features silently no-op, and ads use Google's
   published test unit IDs in debug builds.
3. Optional, for real ads/analytics: drop `google-services.json` into `app/`, and
   provide real AdMob IDs via Gradle properties (`ADMOB_APP_ID`, `ADMOB_BANNER_ID`,
   `ADMOB_INTERSTITIAL_ID`) for release builds — see `app/build.gradle.kts`.

## Build & test

```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
```

## Project structure

```
app/src/main/java/com/aivigil/unitconverter/
├── MainActivity.kt          entry point: consent → theme → Main/Settings
├── domain/                  ConversionEngine.kt — categories, units, parsing, formatting
├── data/                    SettingsRepository, AdPrefsStore (DataStore)
├── ui/main/                 ConversionViewModel (reducer), MainScreen (Splash/Home/Convert)
├── ui/settings/             SettingsScreen
├── ui/theme/                Theme.kt, NeonThemePalette.kt, Color.kt, Type.kt
├── ads/                     AdsController, BannerAdSlot, AdConfig
└── analytics/               Telemetry (Firebase Analytics wrapper)
```

## Known issues

A few rough edges worth knowing about before you build on this — see
[`ARCHITECTURE.md` § Known issues](ARCHITECTURE.md#known-issues) for detail:

- Most user-facing strings (errors, hints, results) are hardcoded in `MainScreen.kt`
  rather than pulled from `strings.xml`, even though matching string resources exist
  there unused — this blocks localization.
- Two separate colour palettes exist (`Color.kt` and `NeonThemePalette.kt`) with
  similar but not identical values; only `Color.kt`'s is actually wired into
  `MaterialTheme.colorScheme`.
- Two separate ad-config types exist (`ads/AdConfig.kt` and an internal `AdsConfig`
  inside `AdsController.kt`); only the latter is live.
- `.kotlin/` (local Kotlin daemon cache/logs) is not yet in `.gitignore` — don't commit it.

## Publishing to GitHub

```bash
git init
git add .
git commit -m "Initial commit: Unit Converter (engine, UI, ads, docs)"
git branch -M main
git remote add origin https://github.com/rmaacaiintern-shazil/unit-converter.git
git push -u origin main
```

Before your first commit, double check `.gitignore` excludes `.gradle/`, `build/`,
`local.properties`, `google-services.json`, signing files, `.idea/`, and `.kotlin/`
(see Known issues above) — none of these belong in the repo.

## Next gates

- Gate 3: generate a release keystore (kept outside the repo)
- Gate 4: create a Play Console draft listing
- Gate 6: link real Firebase project + real AdMob IDs
- Gate 10: QA on two real phones
