# Unit Converter

A small, offline Android app that converts length, weight, volume, temperature,
area and speed between common units — six categories, six units each, with a
Kelvin-based temperature engine so Celsius, Fahrenheit, Kelvin, Rankine, Réaumur
and Delisle all convert exactly, including Delisle's inverted absolute-zero limit.

Built with Jetpack Compose, a dark "neon" visual theme, and Google Mobile Ads /
User Messaging Platform for consent-gated banner and interstitial ads.

**Package:** `com.aivigil.unitconverter` — built for the RMAAC / Aivigil intern program.

## Where everything is

| System | Where | Who has access |
|---|---|---|
| Repository | `https://github.com/RmaacIntern/unit-converter` (private, inside RmaacIntern org) [certain] | RmaacIntern org members. Add via GitHub → org settings → People |
| Personal fork (deprecated) | `github.com/Shazil-Qureshi/UnitConverter` [certain] — used for initial pushes; org repo is now canonical | Shazil Qureshi only. Do NOT push here going forward |
| Play listing | Not yet created — Gate 4 blocker | Tech Lead will hold the Play Console account |
| Firebase project | `unitconverter-app-ebc23` (Firebase console → `firebase.google.com`) [certain] | Tech Lead + Shazil Qureshi (Editor role) |
| AdMob app | Not yet created — Gate 6 blocker. Test unit IDs (`ca-app-pub-3940256099942544/...`) used in debug and as release fallback | Tech Lead will own once created |
| Keystore | Not yet generated — Gate 3 blocker. **Password must not be stored in the repo.** Vault path TBD | Tech Lead + Shazil Qureshi |
| Privacy policy | Currently a `.invalid` placeholder in `strings.xml` — Gate 6 blocker | Tech Lead |
| `google-services.json` | Local `app/` folder only, git-ignored [certain]; download fresh from Firebase console | Anyone with Firebase project Editor role |

## Build

Copy-pasteable. Requires JDK 17+ and Android Studio Ladybug (2024.2.1) or newer.

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew installDebug          # requires a running emulator or connected device
```

Without `google-services.json` the build succeeds and the app runs — Firebase
features silently no-op. Add `google-services.json` to `app/` to enable Analytics,
Crashlytics and Remote Config. [certain]

## The three files that matter most

| File | What it does |
|---|---|
| `app/src/main/java/com/aivigil/unitconverter/domain/ConversionEngine.kt` | The whole conversion domain in one pure-Kotlin file: 6 categories × 6 units, temperature via explicit to/from-Kelvin function pairs, absolute-zero validation in Kelvin so inverted Delisle is handled, strict input parsing, locale-aware formatting. If a conversion is wrong, this is where the bug is. |
| `app/src/main/java/com/aivigil/unitconverter/ads/AdsController.kt` | Every ad decision: UMP consent, TCF Purpose-1 check for EEA declines, MobileAds init, interstitial preload + daily cap, banner adaptive sizing, Firebase Remote Config wiring (`banner_show` / `interstitial_show`). If an ad shows when it shouldn't (or doesn't when it should), this is where to look. |
| `app/src/main/java/com/aivigil/unitconverter/ui/main/MainScreen.kt` | The UI layer: Splash / Home grid / Convert pane, the state reducer's Compose renderer, back-press handling, and the wiring from user actions to `ConversionViewModel` events and from those events out to `AdsController`. If the wrong thing happens on tap, this is where. |

## Gotchas

Things that cost hours. Read this before you spend hours on the same things.

1. **Push to `RmaacIntern/unit-converter`, not any personal fork.** An earlier README
   referenced `github.com/rmaacaiintern-shazil/unit-converter` which does not exist;
   the canonical remote is the org URL above. [certain, verified 2026-09-24 after the
   first push failed]
2. **Firebase parameter names have no leading/trailing whitespace.** `interstitial_s how`
   with a stray space is a different key from `interstitial_show` and the app silently
   ignores it. The Firebase console wraps long parameter names visually, so a space
   near the end looks like a line break. [certain, cost ~15 minutes on 2026-09-25]
3. **Debug builds fetch Remote Config on every launch** (`setMinimumFetchIntervalInSeconds(0)`).
   Release builds default to 12 hours. If you flip a value in the console and don't
   see it in a release build immediately, that's why. [certain]
4. **`.kotlin/` (Kotlin daemon cache/logs) must be in `.gitignore`.** It is now.
   Don't commit it — it's per-machine transient data. [certain]
5. **The app does NOT use Play In-App Review API.** The exit dialog's star row opens
   the Play Store listing directly. Google's policy forbids gating the review flow
   behind a call-to-action button (which an exit dialog is), so the "5-star rating"
   is not a review submission — it's a "please leave one on the store" entry point.
   All star values go to the same URL; there is no gating. [certain]
6. **`canRequestAds()` returns true even after an EEA user declines** because Google
   may serve limited ads. We also read `IABTCF_PurposeConsents` bit 1 (device storage)
   to enforce the brief's "declined = zero ads" rule. [certain]
7. **AGP 9.2 / compileSdk 37 is documented in the 2026-09-22 session log as if it
   shipped. It did not.** The project is on AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35.
   The struck-through corrections in the session log are visible on purpose per house
   style. [certain, verified against `gradle/libs.versions.toml`]

## Firebase Remote Config

Two Boolean parameters in project `unitconverter-app-ebc23`:

| Key | Default | What it does |
|---|---|---|
| `banner_show` | `true` | Toggle bottom banner slot at runtime. When `false`, `BannerAdSlot` renders nothing (not even an empty strip) and no requests fire. [certain] |
| `interstitial_show` | `true` | Toggle every interstitial trigger: splash "Tap to enter", unit change, back-press, convert completion. [certain] |

To disable ads for a running install: flip the value in the Firebase console → Publish
changes. Debug builds pick it up on next app launch; release builds pick it up on the
next fetch window (default 12 h). [certain]

## Features

- Length, Weight, Volume, Temperature, Area, Speed — 6 units each, 36 total [certain]
- Temperature via explicit to/from-Kelvin function pairs, not linear multipliers [certain]
- Decimal-places setting (0–6) and default-category setting, both persisted in DataStore [certain]
- Four explicit UI states (Loading / Empty / Content / Invalid) driven by a pure reducer [certain]
- Rotation- and process-death-safe input via `SavedStateHandle` [certain]
- UMP consent gates every ad request; a decline leaves the app fully usable with zero ads [certain]
- Bottom banner ad; interstitials on splash-enter, unit change, back-press, and convert (all gated by `interstitial_show`) [certain]
- Firebase Remote Config + Analytics wired to project `unitconverter-app-ebc23` [certain]
- Exit-confirmation dialog with a Play-Store rating entry (opens the store listing, not the In-App Review API) [certain]

See [`SPEC.md`](SPEC.md) for the requirements this was built against,
[`DESIGN.md`](DESIGN.md) for the screen-by-screen UI spec, and
[`ARCHITECTURE.md`](ARCHITECTURE.md) for how it fits together.

## Tech stack

| | | Confidence |
|---|---|---|
| UI | Jetpack Compose, Material 3 components with a custom dark palette | [certain] |
| Language | Kotlin 2.0.21 | [certain — `gradle/libs.versions.toml`] |
| Build | AGP 8.7.3, Gradle 9.6.0 | [certain — `gradle/libs.versions.toml`] |
| SDK | `minSdk` 24 · `compileSdk` 35 · `targetSdk` 35 | [certain — `app/build.gradle.kts`] |
| Persistence | Jetpack DataStore (Preferences) | [certain] |
| Ads | Google Mobile Ads SDK 23.x + User Messaging Platform 3.x | [certain] |
| Analytics | Firebase Analytics, Crashlytics, Remote Config (optional — see Where everything is) | [certain] |
| Tests | JUnit4, `ConversionEngineTest` (only test file so far) | [certain] |

## Project structure

```
app/src/main/java/com/aivigil/unitconverter/
├── MainActivity.kt          entry point: consent → theme → Main/Settings
├── domain/                  ConversionEngine.kt — categories, units, parsing, formatting
├── data/                    SettingsRepository, AdPrefsStore (DataStore)
├── ui/main/                 ConversionViewModel (reducer), MainScreen (Splash/Home/Convert),
│                            ExitDialog (Play Store rating entry)
├── ui/settings/             SettingsScreen
├── ui/theme/                Theme.kt, NeonThemePalette.kt, Color.kt, Type.kt
├── ads/                     AdsController, BannerAdSlot, AdConfig
└── analytics/               Telemetry (Firebase Analytics wrapper)
```

## Known issues

Full detail in [`ARCHITECTURE.md` § Known issues](ARCHITECTURE.md#known-issues). Summary:

- Most user-facing strings hardcoded in `MainScreen.kt` instead of `strings.xml` — blocks localization [certain]
- Two colour-palette files (`Color.kt` and `NeonThemePalette.kt`) with similar-but-not-identical values [certain]
- Two `AdConfig` types (`ads/AdConfig.kt` unused; the live one is internal to `AdsController.kt`) [certain]
- No physical-device testing yet — Gate 10 blocker [certain]

## Publishing to GitHub

The repo is already published. To pull:

```bash
git clone https://github.com/RmaacIntern/unit-converter.git
cd unit-converter
```

To push new changes (from a working copy that already has the remote configured):

```bash
git add .
git commit -m "describe what changed"
git push
```

Before your first commit, double-check `.gitignore` excludes `.gradle/`, `build/`,
`local.properties`, `google-services.json`, signing files, `.idea/`, and `.kotlin/`
(see Gotchas 4 above). [certain]

## Next gates

- Gate 3: generate a release keystore (kept outside the repo)
- Gate 4: create a Play Console draft listing
- Gate 6: link real Firebase project + real AdMob IDs (Firebase project itself is done — AdMob linking pending)
- Gate 10: QA on two real phones — blocker for QA-REPORT.md
- Gate 11: COMPLIANCE-WORKSHEET.md — blocked on Gate 10 output
- Gate 15: assemble dossier, finalize handover note
