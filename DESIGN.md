# Design — Unit Converter

## Screens
1. **Main**, internally three panes switched by a local saveable `Pane` enum
   (`Splash → Home → Convert`) — not a navigation library, not separate destinations:
   - **Splash.** One-time entry screen: an "ENGINE V2.4 READY" badge, the app logo
     (`R.drawable.ic_app_logo`, a real PNG), title, tagline, and a "Tap to enter" pill.
     Shown once per process; back from Home returns here.
   - **Home.** A header ("Units" / "6 Categories" / a "PRECISION" pill) above a
     2-column grid of the six categories, each card showing an icon, name, and a short
     unit-list subtitle. Tapping a card opens Convert for that category.
   - **Convert.** A *From* card (unit picker + value), a swap button, a *To* card that
     doubles as the result, a formula pill (Content state only), a custom on-screen
     numeric keypad, and the bottom banner.
2. **Settings.** Decimal-places slider with a live preview, category-on-launch radio
   list, and About (RMAAC logo, app name, version, privacy policy, *Privacy choices*
   when UMP requires it). Reached from a gear icon on Home or Convert.

No other screens.

## Main layout — Convert pane

```
┌──────────────────────────────────────────┐
│ ← Length                              ⚙  │  NeonTopBar
├──────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐ │
│ │ FROM               [ Kilometre    ▾ ]│ │  SurfaceCard, BorderPurple
│ │                            12.5   km │ │  ExtraBold 34sp
│ └──────────────────────────────────────┘ │
│   (only shown when Invalid) ⚠ helper text│
│                   ( ⇅ )                  │  gradient circular button
│ ┌──────────────────────────────────────┐ │
│ │ TO                     [ Mile     ▾ ]│ │  SurfaceCard, BorderCyan
│ │                        7.7671    mi  │ │  ElectricCyan, ExtraBold 34sp
│ └──────────────────────────────────────┘ │
│      ⇄ K = °C + 273.15 · °F = K×9/5−459.67│  formula pill (Content only)
│ ┌───┬───┬───┬───┐                        │
│ │ 7 │ 8 │ 9 │ ⌫ │                         │  NeonKeypad
│ │ 4 │ 5 │ 6 │ C │                         │
│ │ 1 │ 2 │ 3 │ . │                         │
│ │ ± │ 0 │       │  ± only for Temperature │
│ └───┴───┴───────┘                        │
│ [             CONVERT                  ] │  full-width button
├──────────────────────────────────────────┤
│               banner slot                │  Scaffold bottomBar
└──────────────────────────────────────────┘
```

Unlike a calculator, there are **no decorative operator keys** (`÷ × − +`) — the keypad
is digits, `.`, `±` (Temperature only), `⌫`, `C`, and a separate full-width CONVERT
button. Unit pickers are dropdown menus (`ExposedDropdownMenuBox`), not inline text
fields. The formula pill, when shown, joins *every* line `ConversionEngine.formula()`
returns with " · " — for temperature that's both the to-Kelvin and from-Kelvin steps
together, not just one.

## The four states (DESIGN-STANDARD §1.3)
| # | State | When | What the user sees | Convert |
|---|---|---|---|---|
| 1 | **Loading** | DataStore settings not yet read | `CircularProgressIndicator`, no delay | hidden |
| 2 | **Empty** | Blank input, or a lone `-` / `.` / `-.` while typing | Result card shows "0" | disabled |
|   | **Empty** | Valid value, not converted since the last edit | Result card shows "0" | enabled |
| 3 | **Content** | Convert pressed on a valid value | The TO card's number turns `ElectricCyan`; a formula pill appears below it. Changing units or swapping recomputes in place. | disabled until the value changes |
| 4 | **Invalid** | The value can't be converted | FROM card's value turns error-red; a warning row appears above the swap button with helper text. Result card still shows "0". | disabled |

A lone minus sign is *Empty*, not *Invalid*: nobody should see an error for typing the
first character of `-40`.

### Invalid copy (as actually shown — see "Known issues" below)
| Reason | Helper text shown |
|---|---|
| Not a number | Invalid number format |
| Multiple decimal points | Multiple decimal points detected |
| Negative not allowed | Negative values are not allowed for `<Category>` |
| Below/above absolute zero | Value cannot be below `<limit>` (or "cannot exceed" for Delisle) |
| Overflow / too long | Value is out of valid range |

These strings are written directly in `MainScreen.kt`'s `invalidHelperText()`, **not**
pulled from `strings.xml` — see Known issues.

## Number formatting
- **Results** use exactly the decimal-places setting (0–6), rounding HALF_UP.
- **Scientific notation** is used above `1e15`, or when a non-zero result would
  otherwise round to zero at the chosen precision.
- **The user's own value** is echoed back unrounded (`NumberFormatter.exact`).
- **Separators** are locale-aware (`NumberSymbols.forLocale`). For a period-decimal
  locale (e.g. en), a comma anywhere in the input is rejected outright rather than
  treated as a thousands separator; for a comma-decimal locale, the grouping
  character (`.`) is silently stripped. This asymmetry is intentional-looking but
  undocumented in code — worth a comment if it surprises anyone later.
- **Temperature noise.** `ConversionEngine.convert()` snaps a result to `0.0` when
  `abs(result) < 1e-9`.

## Ad placement
- **Banner.** `BannerAdSlot` renders nothing (`BannerState.Hidden`) rather than an
  empty strip when consent is declined or Remote Config disables it. Otherwise it
  reserves `max(adaptive height, 50dp)` computed from the available width, padded for
  navigation bars and the display cutout, so the ad never sits under system UI.
- **Interstitial.** Preloaded ahead of time; shown only from `onConversionCompleted`,
  only once the Activity is at least RESUMED, never in the first session
  (`AdPrefsStore.isFirstSession()`), and at most `AdsConfig.interstitialDailyCap`
  (≤ 4) per local calendar day. Both dismiss and fail-to-show paths clear the
  "showing" flag and preload the next one.

## Rules followed
- **Material 3 components, custom colour.** `Card`, `ExposedDropdownMenuBox`,
  `Slider`, `RadioButton`, `Scaffold`/`TopAppBar` are M3 components, but colour comes
  from a fixed dark palette rather than Material defaults or dynamic colour — see
  `ARCHITECTURE.md` "Known issues" for the fact that there are actually *two*
  overlapping palette files.
- **Touch targets ≥ 48 dp.** Keypad keys are 52 dp tall, dropdown items and radio
  rows are at least 48 dp, the swap button is 44 dp.
- **Accessibility.** Unit pickers show the unit name and symbol; icon buttons have
  `contentDescription`. There is no explicit live region on the result text — a
  screen reader will announce the recomposed value, but not necessarily in the
  "X is Y" phrasing `R.string.result_spoken` was written for (that string is unused).
- **Logo.** The RMAAC Group logo appears only in Settings → About
  (`R.drawable.ic_rmaac_logo`; still a vector placeholder — replace with the official
  asset when available, and delete the unused `rmaac_logo.xml` alongside it).
