# Architecture — Unit Converter

## Shape
Single Activity (`MainActivity`), Jetpack Compose, two destinations: **Main** and
**Settings**, switched by a saveable enum in `MainActivity` — no navigation library,
no route per category. Main itself has three panes — `Splash`, `Home`, `Convert` —
switched by a second, local saveable enum inside `MainScreen`. The six categories are
a 2-column grid on Home, not a tab row; picking one opens Convert for that category.
One ViewModel per destination (`ConversionViewModel`, `SettingsViewModel`), not per pane.

The domain layer (`ConversionEngine.kt`) is pure Kotlin — no Android imports — so every
rule is unit-tested on the JVM. Settings and ad-frequency data live in DataStore. The
in-progress form lives in `SavedStateHandle`. Ads are gated: UMP consent → MobileAds
init → ad requests; Remote Config can tune ads only after `fetchAndActivate()` completes.

## Why a shared Convert pane instead of a screen per category
- **Same interaction, different unit list.** All six categories use the same controls
  (value, from-unit, to-unit, swap, convert); six screens would be six copies of the
  same UI and rotation handling for nothing.
- **The typed value survives a category switch.** It's the same `ConvertPane`
  composable and the same `FormState` regardless of which category is selected.
- **Scope lock.** SPEC.md rules out extra screens. Home's grid keeps the back stack
  trivial: `Splash ⇄ Home ⇄ Convert`, plus `Main ⇄ Settings`.
- **One place for ad rules.** The banner and interstitial trigger attach to the
  Convert pane, so the caps and first-session guard are enforced once.
- **One state machine, one test file.** `ConversionEngineTest` covers every category
  and every temperature unit through the same reducer/engine.

## Layers
```
UI            MainScreen / SettingsScreen (Compose, stateless)
              ConversionViewModel / SettingsViewModel
                  │  pure reducer: (FormState, UserSettings) → MainUiState
                  ▼
Domain        ConversionEngine.kt — Category, MeasureUnit, Evaluation, Formula,
              NumberSymbols, NumberFormatter, ConversionEngine   ← pure Kotlin,
                                                                     one file
Data          SettingsRepository (DataStore "settings")
              AdPrefsStore       (DataStore "ad_prefs")
Ads           AdsController — UMP + Google Mobile Ads + Remote Config
              AdConfig (test IDs / RC key constants) + an internal AdsConfig
              (the live config type — see Known issues)
              BannerAdSlot
Analytics     Telemetry — Firebase Analytics (no-op without google-services.json)
```

### Packages
| Package | Contents |
|---|---|
| `domain` | `ConversionEngine.kt` — everything: units, categories, evaluation, formulas, formatting |
| `data` | `SettingsRepository`, `AdPrefsStore` |
| `ui.main` | `MainScreen.kt` (also defines `MainActions`), `ConversionViewModel` (UI state + reducer) |
| `ui.settings` | `SettingsScreen`, `SettingsViewModel` |
| `ui.theme` | `Theme.kt` (`darkColorScheme`), `Color.kt`, `NeonThemePalette.kt`, `Type.kt` — see Known issues for why there are two palette files |
| `ads` | `AdsController`, `BannerAdSlot`, `AdConfig` |
| `analytics` | `Telemetry` |

Unlike an earlier revision of this project, the domain layer is a single
`ConversionEngine.kt` file rather than split across `Category.kt` /
`ConversionEngine.kt` / `NumberFormatter.kt` / `NumberSymbols.kt`. Functionally
equivalent; just fewer files.

## Decisions

| # | Decision | Why | What we gave up | Confidence |
|---|---|---|---|---|
| 1 | Jetpack Compose, not Views | Two screens total, no legacy interop needed. State-driven UI matches the pure-reducer approach the engine already uses | Team's Views familiarity; harder to lift-and-shift into any older codebase that expects XML layouts | [certain] |
| 2 | Single Activity, no navigation library | Two destinations only (Main / Settings). A saveable enum in `MainActivity` costs nothing; adding Navigation-Compose or the fragment nav graph would be more code and more configuration than saved | Deep links, per-screen back-stack tooling, and any Android-Studio navigation-graph visual editor | [certain] |
| 3 | Categories as a Home grid, not tabs | Grid is 2 taps deep (Home → Convert) but each row shows the unit list preview so the user picks with more information. Tabs are 1 tap but demand horizontal scrolling on a phone in portrait | One tap of latency to reach a category; back-stack has one more level than a pure tab layout would | [likely] — tested visually, no A/B data |
| 4 | Pure-Kotlin domain layer | `ConversionEngine.kt` has zero Android imports, so every rule (36 units, 6 temperature scales, absolute-zero validation) is JVM-testable with no Robolectric or emulator | The convenience of Android `Context` inside the engine; anything Android-shaped has to be marshalled at the ViewModel boundary | [certain] |
| 5 | Temperature via explicit to/from-Kelvin function pairs, not multipliers | Delisle is inverted (higher °De = colder). A `toBase` multiplier can't express that. Kelvin as the pivot also makes the absolute-zero check the same code path for every scale | Extra lambda per scale; can't reuse the linear-conversion helper linear categories use | [certain] |
| 6 | DataStore, not SharedPreferences | Async by design, survives process death without a synchronous main-thread read at startup, matches the Flow-based reducer above it | Extra dependency; can't do a synchronous "read one key at launch" the way SharedPreferences allows | [certain] |
| 7 | `SavedStateHandle` for in-progress form | Rotation AND process-death survival for typed input, without persisting half-typed numbers to DataStore forever | Slightly more ViewModel boilerplate than just `remember { mutableStateOf() }` | [certain] |
| 8 | `minSdk` 24 | ~97% device coverage per Google's public dashboard; unblocks Java 8+ APIs, java.time, adaptive icons | Android 5–6 users (~3% globally, ~1% in target markets) | [certain] |
| 9 | UMP + custom TCF Purpose-1 check, not just `canRequestAds()` | `canRequestAds()` returns true even when an EEA user declines because Google may serve limited ads. Brief requires zero ads on decline, so we also read the TCF string | Extra branch; a future IAB TCF v3 change would need re-verification | [likely] |
| 10 | Kept `com.google.android.gms:play-services-ads` (legacy GMA SDK) | Legacy SDK is still in maintenance mode; migrating to the Next-Gen SDK is a separate scope call for the tech lead | Some newer bidding features; `getCurrentOrientationAnchoredAdaptiveBannerAdSize` is deprecated (we use it anyway for now) | [likely] — SDK migration deferred to a later gate |
| 11 | Firebase Remote Config key names `banner_show` / `interstitial_show` | Matches the actual Firebase console (`unitconverter-app-ebc23`) as configured; short names read cleanly in the console UI | An earlier draft used `banner_enabled` / `interstitial_enabled`, so any external documentation referring to those old names needs updating | [certain] |

## Main-screen state machine
The UI state is a pure function of two inputs:

- `FormState`: category id, from/to unit ids, raw input text, and `converted` (has
  Convert been pressed since the last edit). Saved to `SavedStateHandle`, so it
  survives rotation **and** process death.
- `UserSettings`: decimal places and default category, from DataStore. `null` until
  the first read completes.

| From | Event | To |
|---|---|---|
| Loading | DataStore emits settings | Empty(EnterValue), on the saved default category |
| Empty / Content / Invalid | Input edited to blank, `-`, `.` or `-.` | Empty(EnterValue) |
| any Ready | Input can't be converted | Invalid(reason) |
| Empty / Content / Invalid | Input edited to a valid value | Empty(TapConvert), `converted = false` |
| Empty(TapConvert) | Convert or IME Done | Content, `converted = true`, emits `ConversionCompleted` |
| Content | From/to unit changed or swapped | Content (live recompute, no new event, no ad) |
| any Ready | Category selected from Home | Empty, units reset to that category's defaults; typed value kept |

The first user action pins the resolved category and units into `FormState`. After
that, changing the default category in Settings never swaps the category out from
under a user mid-task.

`MainEvent.ConversionCompleted` is a one-shot `Channel` collected while the screen is
STARTED. `MainActivity` forwards it to `AdsController.onConversionCompleted()`, so the
UI layer never touches ad code.

## Conversion engine
- **Linear categories** (length, weight, volume, area, speed) convert through a base
  unit using `MeasureUnit.factorToBase` (e.g. 1 mi = 1609.344 m, 1 lb = 0.45359237 kg,
  1 US gal = 3.785411784 L). Speed includes Mach (340.29 m/s) as its sixth unit.
- **Temperature** carries its own `toKelvin`/`fromKelvin` lambdas and formula strings
  per unit — there is no multiplier anywhere. `ConversionEngine.convert()` checks
  `from.toKelvin != null && to.fromKelvin != null` to route through Kelvin.
- **Validation happens in Kelvin**, with a `-1e-9` tolerance for float rounding.
  Delisle is inverted (higher °De is colder); its limit (559.725 °De) is hardcoded as
  a constant in `absZeroLimit()` alongside the other five scales' limits, rather than
  derived at runtime from `fromKelvin(0.0)` — the values agree, but if a formula ever
  changes, this constant won't update itself.
- **Parsing** accepts either `.` or `,` as a decimal point depending on locale, via a
  regex (`STRICT_REGEX`) applied after normalization. `MAX_INPUT_LENGTH` is 30.
- **Results are always finite.** Overflow is reported as `OutOfRange`, never shown as ∞.
- **Temperature noise is removed.** Results within `1e-9` of zero snap to `0.0`.

## Data

| What | Stored where | Survives process death? | Survives uninstall? | Confidence |
|---|---|---|---|---|
| Decimal places (0–6) | DataStore `settings` file (Preferences) | Yes | No — DataStore lives in the app's private data dir, wiped on uninstall | [certain] |
| Default category | DataStore `settings` | Yes | No | [certain] |
| Interstitial cap counters (day + count) | DataStore `ad_prefs` | Yes | No | [certain] |
| First-session flag | DataStore `ad_prefs` | Yes | No | [certain] |
| In-progress form (category id, from/to unit id, typed value, converted flag) | `SavedStateHandle` (Bundle-backed) | Yes | No — SavedState is per-process, not persisted to disk | [certain] |
| UMP consent record | Managed by UMP SDK (private SharedPreferences) | Yes | No | [certain] |
| Firebase installation ID (used by Remote Config, Analytics) | Managed by Firebase SDK | Yes | No — new UUID on reinstall | [likely] |
| AdMob ad-serving state (frequency capping etc.) | Managed by Google Mobile Ads SDK | Yes | No | [likely] |

Nothing this app writes survives an uninstall. There is no cloud sync, no account,
no external backup. That is deliberate — SPEC.md rules out accounts and network state.

## Persistence — details on the DataStore stores
| Store | Keys | Why here |
|---|---|---|
| DataStore `settings` | `decimal_places` (0–6, default 4), `default_category` | User preferences; survive process death; failed writes are swallowed so the UI snaps back instead of crashing |
| DataStore `ad_prefs` | `session_count`, `interstitial_day`, `interstitial_count_today` | Frequency rules that must not depend on Remote Config; day is computed from `System.currentTimeMillis()` + timezone offset, deliberately avoiding `java.time` so `minSdk 24` needs no desugaring |
| `SavedStateHandle` | `form.*` | In-progress input: rotation + process death |

Both DataStore repositories fail closed on `IOException`: `AdPrefsStore` treats an
unreadable file as "first session" / "capped today" (no interstitial), and
`SettingsRepository` falls back to `emptyPreferences()` (defaults) rather than
crashing the settings flow.

## Ads flow
```
onCreate ─▶ AdsController.gatherConsent(activity)         (once per process, guarded by an AtomicBoolean)
             ├─ requestConsentInfoUpdate ─▶ loadAndShowConsentFormIfRequired ─▶ onConsentResolved()
             └─ consent already permits ads? ─▶ startMobileAds() immediately, in parallel

onConsentResolved():
   canRequestAds()  AND  NOT (GDPR applies AND Purpose-1 declined)
     ├─ yes ─▶ startMobileAds(): MobileAds.initialize (IO dispatcher) ─▶ availability = Ready
     │          ─▶ preloadInterstitialIfEligible() if not first session and under the daily cap
     └─ no  ─▶ availability = Off: banner slot hidden, zero ad requests, app fully usable
```
- **TCF check.** `TcfConsent.userDeclinedDeviceStorage()` first checks
  `IABTCF_gdprApplies` — if GDPR doesn't apply to this user at all, it returns `false`
  (not declined) without even looking at Purpose 1. Only when GDPR does apply does it
  check `IABTCF_PurposeConsents`'s first character. This is a closer reading of the
  TCF spec than "always check Purpose 1."
- **Privacy choices.** When UMP reports `PrivacyOptionsRequirementStatus.REQUIRED`,
  Settings shows a *Privacy choices* button that reopens the form; consent is
  re-evaluated (`onConsentResolved()`) when it closes.
- **Banner.** `BannerAdSlot` uses
  `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)`,
  floored at 50 dp, padded for navigation bars and the display cutout.
- **Interstitial.** `AdsController` is a process-wide singleton so the preloaded ad
  survives Activity recreation. `firstSession` is computed once (a `Deferred<Boolean>`
  created in `init`) and reused for the process lifetime. Both
  `onAdDismissedFullScreenContent` and `onAdFailedToShowFullScreenContent` clear the
  "showing" flag and call `preloadInterstitialIfEligible()` again.
- **Fail closed.** If DataStore can't be read, `AdPrefsStore` reports "first session"
  and "at the cap," so no interstitial shows.

## Remote Config
`AdsController.fetchRemoteConfig()` runs once from `init`, but only if
`Telemetry.isFirebaseReady()` — i.e. only if `google-services.json` was present at
build time. It sets `AdsConfig.DEFAULTS` as the Remote Config defaults, then
`fetchAndActivate()`s; the completion listener sets the live `config` StateFlow either
way; a failed fetch just logs and keeps the last-activated (or default) values.

| Key | Default | Notes |
|---|---|---|
| `banner_show` | `true` | Kills the banner slot at runtime — layout hides, no ad requests |
| `interstitial_show` | `true` | Kills every interstitial trigger (splash, unit-change, back-press, convert) |
| `interstitial_daily_cap` | `4` | Clamped to 0–4 in `AdsConfig.from()`: remote can lower the cap, never raise it |
| `log_paid_ad_impressions` | `true` | No kill switch is wired up yet — see Known issues |

## Telemetry
`OnPaidEventListener`/`setOnPaidEventListener` is set on both the `AdView` (in
`AdsController.createBannerAdView`) and each `InterstitialAd`. `Telemetry.logAdImpression`
logs `FirebaseAnalytics.Event.AD_IMPRESSION` with platform, format, unit ID, ad source
name (from `responseInfo.loadedAdapterResponseInfo`), value (micros → units), currency,
and precision type — but only if `Telemetry.isFirebaseReady()`, i.e. it's a no-op on a
fresh clone with no `google-services.json`.

## SDK levels and toolchain
| Setting | Value |
|---|---|
| `minSdk` | 24 |
| `compileSdk` | 35 |
| `targetSdk` | 35 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| Gradle wrapper | 9.6.0 |
| Compose BOM | 2024.10.01 |
| Firebase BOM | 33.5.1 |
| play-services-ads | 23.5.0 |
| user-messaging-platform | 3.1.0 |

An earlier revision of this document described a planned AGP 9.2 / compileSdk 37
upgrade with `kotlinOptions {}` removed. That upgrade was never applied — the table
above is what `build.gradle.kts` and `gradle/libs.versions.toml` actually pin, and the
`org.jetbrains.kotlin.android` plugin plus `kotlinOptions { jvmTarget = "17" }` are
both still present in `app/build.gradle.kts`. Don't raise compileSdk/targetSdk or drop
the Kotlin plugin without also bumping AGP and re-verifying `kotlinOptions {}` still
exists at that AGP version.

- **Conditional Firebase plugins.** `app/build.gradle.kts` only applies
  `com.google.gms.google-services` and `com.google.firebase.crashlytics` when
  `google-services.json` exists, so a fresh clone builds and runs without it.
- **`gradle/libs.versions.toml` declares `androidx-navigation-compose` and
  `navigationCompose` that nothing in `app/build.gradle.kts` actually uses** — dead
  catalog entries, safe to remove.

## Third-party dependencies

| Library | Version | Why | What breaks without it | Confidence |
|---|---|---|---|---|
| `androidx.compose.*` (via BOM 2024.10.01) | BOM 2024.10.01 | Entire UI layer | UI won't build; there is no XML fallback | [certain] |
| `androidx.compose.material3` | via BOM | M3 components (Card, Slider, Scaffold, TopAppBar, DropdownMenu, RadioButton) | UI unstyled; would need to hand-roll every control | [certain] |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.x | ViewModels in composables (`viewModel(...)`) | Would need to pass ViewModels through parameters everywhere | [certain] |
| `androidx.datastore:datastore-preferences` | 1.1.x | Async settings + ad-prefs storage | Would fall back to synchronous `SharedPreferences` on the main thread | [certain] |
| `com.google.android.gms:play-services-ads` | 23.x | Banner + interstitial ads | No monetization | [certain] |
| `com.google.android.ump:user-messaging-platform` | 3.x | GDPR consent form | Cannot legally serve personalized ads in EEA; brief requires consent-gated flow | [certain] |
| `com.google.firebase:firebase-config-ktx` | via Firebase BOM | Remote-controlled `banner_show` / `interstitial_show` kill switches | Ad flags freeze at build-time defaults; cannot disable ads remotely after release | [certain] |
| `com.google.firebase:firebase-analytics-ktx` | via Firebase BOM | `ad_impression` event logging (paid-event listener) | No revenue reporting through Firebase | [certain] |
| `com.google.firebase:firebase-crashlytics-ktx` | via Firebase BOM | Crash reporting on production installs | No production crash visibility | [likely] — plugin only applied when `google-services.json` present |
| `junit:junit` | 4.13.x | `ConversionEngineTest` (unit-tested engine, 36 units + temperature scales) | Tests won't compile | [certain] |

## Threading

| Work | Runs on | Why | Confidence |
|---|---|---|---|
| Compose UI, all `@Composable` code | Main thread (immediate dispatcher) | Compose contract; touching UI state off-main throws | [certain] |
| `MobileAds.initialize()` | IO dispatcher (`Dispatchers.IO`) | First-time init opens files, reads config; blocking the main thread here shows an ANR on cold start on some devices | [certain] |
| DataStore reads/writes (`SettingsRepository`, `AdPrefsStore`) | DataStore's own dispatcher (`Dispatchers.IO` internally) | DataStore forbids main-thread access by design | [certain] |
| UMP consent gathering, `requestConsentInfoUpdate` | Main thread (SDK handles its own threading) | UMP SDK contract | [certain] |
| Firebase Remote Config `fetchAndActivate()` | Firebase-managed background thread | Firebase SDK contract; the completion listener returns to main | [certain] |
| Ad-impression paid-event listener | Called by GMA SDK on the main thread | Just forwards to `Telemetry.logAdImpression`, which is a no-op if Firebase isn't wired | [certain] |
| Conversion engine (`ConversionEngine.evaluate`, `.formula`) | Called from the ViewModel's `combine { ... }` on the ViewModel scope's default dispatcher | Pure Kotlin, blocking, no I/O — safe on any thread, but not on the main thread for a tight loop | [certain] |
| One-shot events (`ConversionCompleted`, `UnitChanged`) | `Channel` collected on main via `repeatOnLifecycle` | Ensures the interstitial trigger runs while the Activity is at least STARTED | [certain] |

## Manifest permissions
`AndroidManifest.xml` declares `INTERNET` **and** `ACCESS_NETWORK_STATE`. SPEC.md's
"out of scope permanently" list says INTERNET only; `ACCESS_NETWORK_STATE` is a common
addition the Google Mobile Ads SDK itself requests/merges in, so this is expected in
practice, but the discrepancy against the written brief is real and worth a one-line
sign-off from whoever owns the brief.

## Known issues
Things a future contributor (or reviewer) will trip over:

1. **Two colour palettes.** `Color.kt` (`NeonBackground`, `NeonPrimary`,
   `NeonSecondary`, …) is what `Theme.kt` actually wires into
   `MaterialTheme.colorScheme`. `NeonThemePalette.kt` (`BgDarkVoid`, `NeonPurple`,
   `ElectricCyan`, …) defines *similar but numerically different* colours for the same
   roles (e.g. `NeonPrimary` is `0xFF9D00FF`, `NeonPurple` is `0xFFA855F7`) and is what
   `MainScreen.kt` and `BannerAdSlot.kt` actually import and use directly. In effect,
   `MaterialTheme.colorScheme` is barely used by the visible UI. Pick one palette and
   delete the other before this drifts further.
2. **Two ad-config types.** `ads/AdConfig.kt` is a small object with test IDs and
   Remote Config key names. `AdsController.kt` separately defines its own internal
   `AdsConfig` data class with the same purpose (defaults, `from()`, key constants)
   that's what's actually live. `AdConfig`'s constants aren't referenced by
   `AdsController`. Consolidate into one.
3. **Most user-facing strings are hardcoded, not in `strings.xml`.** `strings.xml`
   defines `error_not_a_number`, `error_negative`, `result_spoken`,
   `unit_picker_description`, and about 30 others that are never referenced from
   Kotlin — `MainScreen.kt`'s `invalidHelperText()` and its result/hint text are
   plain string literals instead. This blocks localization and means the "every
   message comes from strings.xml" goal an earlier revision of this doc claimed is
   not actually true.
4. **Two RMAAC logo drawables.** `ic_rmaac_logo.xml` (used by `SettingsScreen.kt`)
   and `rmaac_logo.xml` (unused) both exist. Delete the unused one once confirmed.
5. **`.kotlin/` is not gitignored.** The repo as zipped includes
   `.kotlin/errors/errors-*.log` (a transient Kotlin-daemon-connection failure, not a
   source error) — this is a local build cache/log directory and should never be
   committed. Add `.kotlin/` to `.gitignore` before pushing.
6. **`log_paid_ad_impressions` has no reader.** It's fetched into `AdsConfig` and
   `Telemetry.logAdImpression` is always called from the paid-event listeners
   regardless of that flag's value — the kill switch described in "Telemetry" above
   isn't actually checked anywhere. Confirm intent: if double counting after linking
   AdMob to Firebase (Gate 6) is a real risk, `logPaidImpression` needs to check
   `config.value.logPaidImpressions` before calling `Telemetry`.

## What I would change with more time

1. **Consolidate the two colour palettes** (`Color.kt` vs `NeonThemePalette.kt`). Pick
   one, delete the other, and point every import at it. The visible UI barely uses
   `MaterialTheme.colorScheme` because most colours are pulled directly from
   `NeonThemePalette`. This is a purely mechanical rename that would take an hour but
   nobody else can safely do it while ambiguity remains. *[certain] worthwhile*
2. **Consolidate the two `AdConfig` types.** `ads/AdConfig.kt` is unused; the live
   config is an internal `AdsConfig` inside `AdsController.kt`. Merge into a single
   top-level file. *[certain] worthwhile*
3. **Move hardcoded strings out of `MainScreen.kt` into the existing (but unused)
   `strings.xml` entries.** Blocks localization today. *[certain] worthwhile*
4. **Add `ConversionStateTest` and `AdPrefsStoreTest`** — the reducer and the daily
   cap store are both testable pure logic; only `ConversionEngineTest` exists so far.
   *[certain] worthwhile*
5. **Write a proper `QA-REPORT.md` on two real devices.** No device testing has been
   performed yet; the app has only ever been run on emulators and the current author's
   own phone. Gate 10 blocker. *[certain] worthwhile*
6. **Investigate Google Mobile Ads Next-Gen SDK migration.** The legacy SDK is in
   maintenance mode. Not urgent — no user-visible impact — but a ticking bell. *[likely]
   worth doing eventually*
7. **Route Firebase Remote Config keys through a single `RemoteConfigKeys` object**
   instead of string literals inside `AdsController`. Would prevent the kind of
   `banner_show` vs `banner_enabled` drift the last docs pass had to fix. *[likely]
   worthwhile*

## Package name
`com.aivigil.unitconverter`. Permanent: never change it after the first Play upload.

## Tests
| File | Covers |
|---|---|
| `ConversionEngineTest` | Unit catalogue, legal factors, temperature pairs, absolute zero on every scale incl. inverted Delisle, parsing, overflow, formatting |

Run: `./gradlew :app:testDebugUnitTest`

An earlier revision of this document also listed `ConversionStateTest` and
`AdPrefsStoreTest` as existing test files. Only `ConversionEngineTest` is present in
this project — the other two describe tests worth writing, not tests that exist yet.
