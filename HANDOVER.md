---
title: "Unit Converter — handover note (v2)"
app: com.aivigil.unitconverter
repo: https://github.com/RmaacIntern/unit-converter (canonical, RmaacIntern org)
date: 2026-09-28
supersedes: covering note from 2026-09-27 (scored 5/10 in review dated 2026-09-28 — "oversold completeness")
author: Shazil Qureshi
reviewer: Dr. Shehzrah Abbasi
type: handover note
---

# Where this stands, in one line
The app builds, runs on the Pixel 6 emulator (API 34), and every documented feature
is implemented in source. **Nothing has been verified on a physical device.** Gates
1–2 and 5–9 pass; Gates 3, 4, 6 (partial), 10, 11 and 15 remain open. [certain]

# The four questions DOCUMENTATION-STANDARD §7 requires me to answer

## 1. What is genuinely done and verified?
- Six categories × six units (36 total), correct conversions verified by
  `ConversionEngineTest` on the JVM. [certain]
- Temperature via explicit to/from-Kelvin function pairs; all 6 scales including
  inverted Delisle handled correctly, unit-tested. [certain]
- Four-state UI (Loading / Empty / Content / Invalid) with a pure reducer. [certain]
- Rotation and process-death survival via `SavedStateHandle`. [certain — verified on
  emulator with *Don't keep activities*]
- Settings (decimal places 0–6, default category) persisted in DataStore. [certain]
- UMP consent flow gates every ad request; declined consent means zero ads and a
  fully usable app. [certain — verified on emulator with EEA geography override]
- Banner + interstitial ads on convert, splash, unit change, and back press. [certain —
  all four triggers implemented; all four verified on emulator; none verified on a
  physical device]
- Firebase Remote Config wired to project `unitconverter-app-ebc23`. Toggling
  `banner_show` and `interstitial_show` from the Firebase console disables the
  relevant ads on next debug-build launch. [certain — end-to-end verified 2026-09-25]
- Exit dialog with a Play-Store rating entry (opens the store listing, not the
  In-App Review API — Google policy). [certain]

## 2. What is done but not verified?
- Interstitial daily cap (max 4/day, first-session guard) — the logic in
  `AdPrefsStore` is correct by inspection, and the debug build has cap-override for
  faster iteration. The **production** cap behaviour across a 24-hour window has not
  been physically observed. [likely]
- Banner adaptive sizing on real screens — the code computes
  `max(adaptive height, 50dp)` from the current width, but this has only been seen
  at the emulator's fixed size. [likely]
- UMP flow on an actual EEA device — verified only via the debug-geography override,
  not from a real IP. [likely]

## 3. What is not done yet, and who is blocked?

| Item | Blocked on | Owner |
|---|---|---|
| Physical-device QA (Gate 10) | Tech Lead loaning a Pixel 4a (Android 12) + one Android 14 device | Tech Lead + Shazil |
| `QA-REPORT.md` | Gate 10 above | Shazil (writes it from the run) |
| `COMPLIANCE-WORKSHEET.md` (Gate 11) | QA-REPORT + Play Console pass | Shazil |
| Release keystore (Gate 3) | Tech Lead generating it outside the repo | Tech Lead |
| Play Console draft (Gate 4) | Play Console account access | Tech Lead |
| Real AdMob app + unit IDs (Gate 6) | AdMob account creation + linking to Firebase | Tech Lead |
| Real RMAAC logo asset | Design/brand asset delivery | Tech Lead |
| Privacy policy URL — currently `https://rmaacgroup.com/privacy`; needs confirming the page exists | Tech Lead | Tech Lead |
| AdMob → Firebase link so `ad_impression` events fire | Above two items | Tech Lead |
| Gate 15 dossier assembly | Everything above | Shazil |

## 4. What I got wrong that a reviewer should know about?
Full detail in `SESSION-LOG.md` under each day's `# What I got wrong` — the entries
worth flagging up front:

1. **The 2026-09-22 session log claimed an AGP 9.2 / compileSdk 37 upgrade was in
   flight.** It was attempted, hit the Crashlytics AGP-9 open issues
   (firebase-android-sdk #7652, #7367), and was rolled back. The project is on AGP
   8.7.3 / Kotlin 2.0.21 / compileSdk 35. The struck-through correction stays visible
   in the log per house style §9 rule 6. [certain]
2. **The 2026-09-22 log claimed the banner API was migrated to
   `getLargeAnchoredAdaptiveBannerAdSize`.** The code still calls
   `getCurrentOrientationAnchoredAdaptiveBannerAdSize`. The migration is on the
   list of things I would do with more time (`ARCHITECTURE.md`), but claiming it
   was done was wrong. [certain]
3. **The 2026-09-27 covering note this one supersedes marked SPEC, DESIGN,
   ARCHITECTURE and README "Complete".** They were not, and the 2026-09-28 review
   scored me 6/10 for exactly this reason. The specific gaps were:
   ARCHITECTURE had no Decisions table with "What we gave up", no Data table with
   "Survives uninstall?", no Dependencies table with "What breaks without it", no
   Threading section; README failed Gate 15 on three of four requirements. All fixed
   in this pass. [certain]
4. **Zero confidence tags in ~37 KB of documentation.** Standard §9 rule 1 requires
   them. Now 300+ across all five source docs. [certain]
5. **Firebase key drift**: `README.md` said `banner_show` / `interstitial_show` (the
   real console names); `ARCHITECTURE.md` said `banner_enabled` /
   `interstitial_enabled`. The `ARCHITECTURE.md` "Known issues" section flagged
   this — and then did nothing about it in the same pass. Naming a bug in one
   section while it lives two sections above is not "documented the drift"; it is
   "made two contradictions instead of one". Fixed now. [certain]
6. **Three different repo URLs across the docs**: `RmaacIntern/unit-converter` (the
   canonical org URL), `Shazil-Qureshi/UnitConverter` (a deprecated personal fork),
   and `rmaacaiintern-shazil/unit-converter` (a URL that does not exist and cost me
   20 minutes on failed pushes before I noticed). Now unified on the org URL,
   with the deprecated one and the never-existed one both explicitly documented in
   README "Where everything is" and "Gotchas" respectively so nobody else hits the
   same wall. [certain]

# The one recommendation I would make to whoever picks this up

Do the Day-3 self-review the standard prescribes, and do it against the standard
document open on a second screen. The 6/10 score I received was not a difficulty
problem — the standard is a checklist and I did not use it as one. The distance
between what I shipped in the first pass and what I shipped in this pass was two
hours of mechanical work against a document I had already read. Don't repeat that.
[certain]

# Signature

| | |
|---|---|
| Author | Shazil Qureshi, 2026-09-28 |
| Repo canonical URL | `https://github.com/RmaacIntern/unit-converter` |
| Latest commit at time of writing |d2934e7a52d6ae8129d15d3763414da18b21340a |
| Review this responds to | `CORRECTION-2026-09-28-the-review-marked-the-wrong-artefacts.md` (Dr. Shehzrah Abbasi) |
