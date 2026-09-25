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

## Persistence
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
| `banner_enabled` | `true` | |
| `interstitial_enabled` | `true` | |
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
