---
title: "Unit Converter — 2026-09-23 session log: docs reconciled against source"
app: com.aivigil.unitconverter
date: 2026-09-23
tip: no-commit (repo not yet initialized — first commit is 09-24)
status: "Every doc read against real source; three planned-but-never-shipped claims struck through. Still no build."
type: session log
---

# Where this stopped
Documentation-only day. Read every source file top to bottom, checked every doc claim against
what the code actually does, and corrected the drift. [certain]

# What I did
- Re-read `AdsController.kt`, `ConversionEngine.kt`, `MainScreen.kt`, `MainActivity.kt`,
  `build.gradle.kts`, `gradle/libs.versions.toml` against SPEC / DESIGN / ARCHITECTURE. [certain]
- Corrected the AGP 9.2 / compileSdk 37 claim in ARCHITECTURE — the upgrade was written up
  as done but never actually applied. Toolchain is AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35.
  [certain, verified against pinned versions]
- Corrected the "banner API migration done" claim — the code still calls the deprecated
  `getCurrentOrientationAnchoredAdaptiveBannerAdSize`. [certain]
- Wrote up all the drift under ARCHITECTURE.md § Known issues with source pointers. [certain]

# What I got wrong
1. **Trusted my own earlier docs instead of the code.** Two entire claims in the previous day's
   log ("upgrade done", "banner API migrated") were aspirational. Neither was true. Should
   have grepped the source before writing anything the first time. [certain]
2. **Marked five files "complete" in the covering note without checking what "complete" meant
   under the standard.** The standard's DESIGN checklist for ARCHITECTURE says explicitly it needs
   a Decisions table with "What we gave up", a Data table with "Survives uninstall?", a
   Dependencies table with "What breaks without it", and a Threading section. None of these
   existed in my version. Called it complete because the file had headings. It didn't. [certain]
3. **Left the Firebase key names inconsistent between docs.** README already said
   `banner_show` / `interstitial_show`; ARCHITECTURE still said `banner_enabled` /
   `interstitial_enabled`. Same file, different names, one pass apart. [certain]

# Blockers
| Blocker | Owner | Raised | Due |
|---------|-------|--------|-----|
| No repo yet — nowhere to push the docs to for review | Shazil Qureshi | 2026-09-23 | Fix 09-24 |
| RMAAC logo still placeholder | Tech Lead | 2026-09-21 | Before Play listing |
| Privacy policy URL still `.invalid` | Tech Lead | 2026-09-22 | Gate 6 |

# Next, in order
1. `git init`, `.gitignore` (must include `.kotlin/`), first commit, push to
   `github.com/RmaacIntern/unit-converter`.
2. Run `./gradlew assembleDebug` — never actually done before.
3. Add the missing ARCHITECTURE sections (Decisions with "What we gave up", Data with
   "Survives uninstall?", Dependencies with "What breaks without it", Threading, "What
   I would change with more time").
