---
title: "Unit Converter — 2026-09-25 session log: Firebase Remote Config wired"
app: com.aivigil.unitconverter
date: 2026-09-25
tip: d2934e7a52d6ae8129d15d3763414da18b21340a (work from this day shipped in the 09-28 commit — no separate SHA exists as version control was not yet initialized)
status: "google-services.json added locally; Remote Config keys created in Firebase console; ads gate on RC flag from launch #2 onwards."
type: session log
---

# Where this stopped
Firebase Remote Config working end-to-end on debug builds: setting `interstitial_show = false`
in the Firebase console (`unitconverter-app-ebc23`) and force-closing the app disables every
interstitial trigger; setting it back to `true` re-enables them. Banner responds the same way
to `banner_show`. [certain, verified on emulator]

# What I did
- Placed `google-services.json` in `app/` (local only, not committed). [certain]
- Created two Boolean Remote Config parameters in the Firebase console: `banner_show`
  (default `true`), `interstitial_show` (default `true`). [certain]
- Verified `AdsController.fetchRemoteConfig()` reads both correctly and that
  `AdsConfig.from(remoteConfig)` maps them to `bannerEnabled` / `interstitialEnabled`. [certain]
- On debug builds, `setMinimumFetchIntervalInSeconds(0)` means the app fetches on every
  launch, so toggling a value in the console + force-close-reopen picks it up immediately.
  [certain]

# What I got wrong
1. **Created `interstitial_s how` instead of `interstitial_show` in the console the first
   time** — a trailing space in the parameter name. The app returned false silently (no such
   key → default from code → default is true, but the RC value didn't override it because
   the key names didn't match). Took 15 minutes to spot because the Firebase console wraps
   long names and the space looked like a line break. [certain]
2. **Assumed release builds would fetch as fast as debug.** Release has the default 12-hour
   throttle interval; a console change won't reach an installed release app until the next
   fetch window unless the user reopens after that gap. Documented this in the README. [certain]
3. **Called it "connected to Firebase" in the README before confirming Analytics events
   actually flowed.** Analytics dashboard confirmed `first_open` and `app_open` events
   arrived within ~1 minute, but `ad_impression` won't fire until AdMob is linked to
   Firebase (Gate 6). The README claim was technically true but oversold what "Firebase
   integration" delivers today. Left the statement in with a clarifying note. [certain]

# Blockers
| Blocker | Owner | Raised | Due |
|---------|-------|--------|-----|
| AdMob → Firebase link not established, so `ad_impression` events never fire | Tech Lead | 2026-09-25 | Gate 6 |
| Still no physical-device testing | Shazil Qureshi | 2026-09-24 | Before Gate 10 sign-off |
| RMAAC logo, privacy URL, real AdMob IDs — all still Tech Lead owned | Tech Lead | 2026-09-21 / 22 | Various |

# Next, in order
1. Add exit-confirmation dialog with Play Store rating entry (requested by tech lead). [pending]
2. Add interstitial ads on unit change and on splash "Tap to enter". [pending]
3. Add back-press interstitial. [pending]
