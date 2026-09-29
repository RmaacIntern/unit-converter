---
title: "Unit Converter — 2026-09-24 session log: first build, first commit, GitHub push"
app: com.aivigil.unitconverter
date: 2026-09-24
tip: fba069ab4496eab7d9c1df113184ef6a21367697
status: "App builds and runs on emulator. First push to github.com/RmaacIntern/unit-converter succeeded."
type: session log
---

# Where this stopped
`assembleDebug` passes. App launches on Pixel 6 emulator (API 34); splash → home →
convert flow works; conversion returns correct results for the spot-checks I tried
(1 km → 0.6213711922 mi, 100 °C → 373.15 K, 559.725 °De → 0.0 K + shows a
"colder than absolute zero, max is …" error, matching the code path). [certain]
First commit pushed. Repo is public inside the RmaacIntern org. [certain]

# What I did
- Created `.gitignore` (including `.kotlin/`), `git init`, first commit
  (`fba069ab4496eab7d9c1df113184ef6a21367697`). [certain]
- Pushed to `github.com/RmaacIntern/unit-converter` via HTTPS + PAT after
  `github.com/Shazil-Qureshi/UnitConverter` push failed with "repository not
  found" — the personal-account push URL in the README was wrong. [certain]
- Ran the app on a Pixel 6 emulator (API 34); confirmed splash + home grid + convert
  pane. [certain]
- Ran `./gradlew :app:testDebugUnitTest` — `ConversionEngineTest` passes. [certain]

# What I got wrong
1. **Followed my own README's stale git-remote URL** (`rmaacaiintern-shazil`) instead
   of checking with the tech lead first. Cost 20 minutes on failed pushes before I
   asked. Now the README documents the real org URL. [certain]
2. **Committed `.kotlin/` on the first attempt** because `.gitignore` was drafted
   after `git add .`. Amended the commit before pushing but the near-miss is why
   `.kotlin/` is now the first line under `# Gradle` in `.gitignore`. [certain]
3. **Did not run on a real device.** Only tested on the emulator. Gate 10 needs two
   real devices on different Android versions. Neither has happened. [certain]

# Blockers
| Blocker | Owner | Raised | Due |
|---------|-------|--------|-----|
| No physical-device testing done — Gate 10 blocker | Shazil Qureshi | 2026-09-24 | Before Gate 10 sign-off |
| `google-services.json` still not present — Firebase features silently no-op | Tech Lead | 2026-09-21 | Gate 6 |
| Official RMAAC logo asset | Tech Lead | 2026-09-21 | Before Play listing |
| Privacy policy URL still `.invalid` | Tech Lead | 2026-09-22 | Gate 6 |
| Real AdMob IDs (test IDs used in release fallback) | Tech Lead | 2026-09-22 | Gate 6 |

# Next, in order
1. Add `google-services.json` to `app/` (do NOT commit — already gitignored) and confirm
   Firebase Analytics starts up in logcat. [pending]
2. Create the Remote Config keys `banner_show` / `interstitial_show` (both Boolean,
   default `true`) in the Firebase console. [pending]
3. Test the interstitial daily-cap edge cases from 09-22 next-steps 5 and 6. [pending]
