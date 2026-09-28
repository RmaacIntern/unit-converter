# Unit Converter

## What it does
Convert length, weight, volume, temperature, area and speed between common units — fully offline. [certain]

## Who it is for
Anyone who needs quick, accurate unit conversions without network or accounts. [certain]

## Features — status

| # | Feature | Screen | Done when | Status | Confidence |
|---|---------|--------|-----------|--------|------------|
| 1 | Six categories × six units | Home / Convert | All 36 units selectable and convert correctly | ✅ Built | [certain — `ConversionEngine.kt` + `ConversionEngineTest`] |
| 2 | Temperature function pairs | Convert | C↔F↔K↔R↔Ré↔De verified by unit tests | ✅ Built | [certain — `ConversionEngineTest` passes on JVM, 2026-09-24] |
| 3 | Decimal places setting | Settings | 0–6 places applied to result | ✅ Built | [certain — `SettingsRepository`, verified on emulator] |
| 4 | Default category setting | Settings | Survives process death | ✅ Built | [certain — DataStore-backed, verified with *Don't keep activities* on emulator 09-24] |
| 5 | Empty / Content / Invalid states | Convert | No crash on bad input | ✅ Built (plus a fourth, Loading) | [certain — `MainUiState` sealed interface, 4 states] |
| 6 | Rotation preserves state | Convert | Input + selections kept | ✅ Built (`SavedStateHandle`) | [certain — verified on emulator 09-24] |
| 7 | Bottom banner (test) | Convert | Never covers controls | ✅ Built | [likely — verified on emulator only; not yet on a real device, Gate 10 blocker] |
| 8 | Interstitial after conversion | Convert | Never first session, daily cap 4 | ✅ Built | [likely — daily cap logic in `AdPrefsStore`, verified in `debug` build with cap-override; production cap not yet verified] |
| 9 | UMP consent | App start | Before any ad request | ✅ Built | [likely — verified in debug with EEA geography override; not yet tested from an actual EEA device] |
| 10 | RMAAC logo in About | Settings | Visible | ✅ Built (placeholder vector — see below) | [certain — placeholder shipped; real asset a Tech Lead blocker] |
| 11 | Firebase Remote Config `banner_show` / `interstitial_show` | App-wide | Toggling in Firebase console disables the relevant ads on next fetch | ✅ Built | [certain — verified end-to-end on debug 2026-09-25 with project `unitconverter-app-ebc23`] |
| 12 | Interstitial on splash "Tap to enter" | Splash | Shows once per launch, gated by `interstitial_show` | ✅ Built | [likely — verified on emulator; pending Gate 10 on device] |
| 13 | Interstitial on unit change | Convert | Fires on category/from/to/swap; cooldown prevents rapid stacking | ✅ Built | [likely — verified on emulator; cooldown 30 s in release, 0 s in debug] |
| 14 | Interstitial on back-press | Convert / Home / Splash | Fires before back navigation | ✅ Built | [likely — verified on emulator] |
| 15 | Exit-confirmation dialog with Play Store rating entry | Home / Splash | Shown on back from Home; stars open Play Store listing | ✅ Built | [certain — `ExitDialog.kt` + `RatingPrefsStore`; opens Play Store directly, does NOT use In-App Review API (Google policy)] |

## Visual direction
A dark "neon" theme with a fixed custom palette replaces the original
Material-3-default-colours-only constraint. The app does not use dynamic colour and has
no light theme. See [`DESIGN.md`](DESIGN.md) for what's still Material 3 underneath
(components, touch targets) versus what's custom (colour, the on-screen keypad). [certain]

## NOT building this week

| Idea | Why not now | Confidence |
|------|-------------|------------|
| Live currency rates | Stretch goal only; requires network | [certain] |
| Extra screens | Scope lock — Home, Convert and Settings only | [certain] |
| Maps / GPS / sensors | Explicitly out of scope | [certain] |
| Play In-App Review API | Google policy forbids gating the review flow behind a CTA button (which the exit dialog is) — we open the Play Store listing directly instead | [certain] |
| AdMob → Firebase link | Gate 6 blocker; without it `ad_impression` events never fire | [certain] |

## Monetization

| Placement | Format | Trigger | Cap | Confidence |
|-----------|--------|---------|-----|------------|
| Bottom of Convert | Banner | Always (after consent) | — | [certain] |
| After conversion | Interstitial | Successful convert button | 4 / day, never first session | [certain] |
| Splash "Tap to enter" | Interstitial | Once per launch | 30 s cooldown (release); no daily cap | [certain] |
| Unit change (category/from/to/swap) | Interstitial | Any unit switch | 30 s cooldown (release); no daily cap | [certain] |
| Back press | Interstitial | Back from Convert/Home/Splash | 30 s cooldown (release); no daily cap | [certain] |

All interstitial triggers respect the same UMP consent + TCF Purpose 1 gate + the
`interstitial_show` Remote Config flag. [certain]

## Out of scope permanently
- Any permission beyond INTERNET for ads (in practice `ACCESS_NETWORK_STATE` is also
  declared — see `ARCHITECTURE.md` "Manifest permissions") [certain]
- Claiming measurement accuracy beyond pure arithmetic [certain]
- Live data of any kind [certain]

## Known gaps against this spec
- The RMAAC logo (`ic_rmaac_logo.xml`) is still a placeholder vector, not the official
  asset. A second, unused logo file (`rmaac_logo.xml`) also exists in the same
  directory and should be deleted once the real asset lands. [certain]
- The privacy policy URL in `strings.xml` points at `https://rmaacgroup.com/privacy`
  — Tech Lead needs to confirm that page actually exists before shipping; it was a
  `.invalid` placeholder in an earlier revision. [likely — I have not personally
  loaded the URL]
- No Gate-10 device testing yet: every "verified" claim above marked [likely] is
  verified on the Pixel 6 emulator (API 34) but not on physical hardware. [certain]

## Open questions
None from the brief. Two questions for the Tech Lead noted in the blockers table of
the most recent session log:
1. Is `ACCESS_NETWORK_STATE` (auto-merged by the ads SDK) acceptable under the
   "INTERNET only" spirit of the brief, or does it need documenting as an exception? [certain — unresolved]
2. Which of the two RMAAC logo drawables (`ic_rmaac_logo.xml` vs `rmaac_logo.xml`)
   should be kept once the official asset arrives? Recommend keeping `ic_rmaac_logo.xml`
   for naming consistency with `ic_launcher_*`. [likely]
