---
title: "Unit Converter — 2026-09-22 session log: card UI refactor, 4 states, ads hardening, SDK 37"
app: com.aivigil.unitconverter
date: 2026-09-22
tip: refactor (follows 2026-09-21 scaffold)
status: "Code and docs written; not yet compiled. First Gradle sync on AGP 9.2 / compileSdk 37 is the next step."
type: session log
---

# Where this stopped
The refactor is written: engine, UI, ViewModel, ads, Gradle files, tests and docs. It has **not** been built yet.
The engine's arithmetic, parsing and formatting rules were checked outside Gradle with a JVM port of the same logic, and every expected value in `ConversionEngineTest` matched.
Nothing Android-side (Compose, DataStore, GMA, UMP) has run.

# What I did
- Rebuilt Main as a calculator-style card layout: category tabs, a From card, a swap button, and a To card that doubles as the result. It is still one screen; Settings is the only other destination.
- Made the four UI states explicit (`MainUiState`: Loading, Empty, Content, Invalid), produced by a pure reducer and unit-tested.
- Moved the in-progress form to `SavedStateHandle`, so it survives process death, not just rotation.
- Engine:
  - Checks absolute zero in Kelvin, so inverted Delisle is handled.
  - Parses with a strict regex (no `NaN`, `Infinity`, `1e5`).
  - Reports overflow instead of showing ∞.
  - Formats with locale separators and a scientific fallback.
- Ads:
  - Added a TCF Purpose 1 check, so a declined consent means zero ads.
  - Added a *Privacy choices* entry in Settings.
  - Reserve the banner slot at the adaptive height computed before the request.
  - Handle both interstitial end callbacks.
  - Paid-event → `ad_impression` logging, with a Remote Config kill switch.
  - Remote Config keys are read only in the `fetchAndActivate()` listener.
- Gradle: compileSdk 37 / targetSdk 36 / minSdk 24 on AGP 9.2 with built-in Kotlin; version catalog updated.
- Rewrote ARCHITECTURE.md and DESIGN.md.

# What I got wrong
1. **"Fixed 50 dp" banner height (yesterday's DESIGN.md).** Anchored adaptive banners are 50 dp or taller, depending on the device width, so a hard 50 dp slot would clip the ad. Fix: compute the adaptive `AdSize` from the width first and reserve exactly that height, with 50 dp as the floor.
2. **Only three UI states.** Nothing covered the moment before DataStore answers. The screen therefore had to render *some* category before it knew the saved default: either it flashed Length and then jumped, or it ignored the setting. Added Loading, with a 150 ms delay before the spinner shows.
3. **Assumed the SDK bump was a one-line change.** compileSdk 37 needs AGP 9.1 or later. AGP 9 compiles Kotlin itself, so the `org.jetbrains.kotlin.android` plugin must come out of both build files and `kotlinOptions {}` no longer exists. The Crashlytics Gradle plugin also has open AGP 9 issues (firebase-android-sdk #7652, #7367).
4. **"Below absolute zero" as a per-unit minimum is wrong for Delisle.** On Delisle, higher numbers are colder, so its limit (559.725 °De) is a maximum. The check now converts to Kelvin first, and the error copy says "highest possible value" for that scale. The raw limit also comes out of the arithmetic as `559.7249999999999`, which is why limits are formatted to 10 significant digits.
5. **`canRequestAds()` is not "user consented".** It stays true when an EEA user declines, because Google can still serve limited ads. The brief says declined = zero ads, so the app now also reads the TCF Purpose 1 signal.
6. **`KeyboardType.Decimal` doesn't promise a minus key.** It sets no "signed" flag, so negative temperatures could be impossible to type on some keyboards. Added a ± key, shown on Temperature only.
7. **Manual `ad_impression` logging will double count** once AdMob is linked to Firebase, because AdMob then reports impressions to Analytics itself. Put it behind the `log_paid_ad_impressions` Remote Config flag.
8. **Deprecated banner API.** GMA SDK 25 deprecated `getCurrentOrientationAnchoredAdaptiveBannerAdSize`; the app now uses `getLargeAnchoredAdaptiveBannerAdSize`.

# Blockers
| Blocker | Owner | Raised | Due |
|---------|-------|--------|-----|
| google-services.json not yet created | Intern + Tech Lead | 2026-09-21 | Gate 6 |
| Official RMAAC logo asset (current `rmaac_logo` is a vector approximation) | Tech Lead | 2026-09-21 | Before Play listing |
| Privacy policy URL (`privacy_policy_url` is a `.invalid` placeholder) | Tech Lead | 2026-09-22 | Gate 6 |
| Real AdMob app + unit IDs (release falls back to test IDs with a build warning) | Tech Lead | 2026-09-22 | Gate 6 |
| Decide: keep `AD_ID` / `ACCESS_NETWORK_STATE`, which the ads SDK merges into the manifest, vs "INTERNET only" in the brief | Tech Lead | 2026-09-22 | Gate 6 |

# Next, in order
1. SDK Manager: install `platforms;android-37.0` and `build-tools;37.0.0`. Let the AGP Upgrade Assistant set the Gradle wrapper for AGP 9.2, then sync.
2. `./gradlew :app:testDebugUnitTest`, then `./gradlew assembleDebug`.
3. On an emulator, check each of the four states, rotation mid-input, and process death: *Don't keep activities*, or `adb shell am kill com.aivigil.unitconverter` while the app is backgrounded.
4. On a real phone with the Samsung keyboard, confirm the ± key is needed and works on Temperature.
5. UMP with debug geography = EEA:
   - decline → confirm no banner slot and no ad requests in logcat;
   - accept → banner shows;
   - Settings → Privacy choices → decline → banner disappears.
6. Interstitial:
   - never on the first launch after a fresh install;
   - on the second launch, the 5th conversion of the day shows no ad;
   - dismiss and failed-to-show both return to the result.
7. Gate 6: add google-services.json, create the Remote Config keys, link AdMob, then set `log_paid_ad_impressions = false`.
