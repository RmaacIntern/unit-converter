# Unit Converter

## What it does
Convert length, weight, volume, temperature, area and speed between common units — fully offline.

## Who it is for
Anyone who needs quick, accurate unit conversions without network or accounts.

## Features — status

| # | Feature | Screen | Done when | Status |
|---|---------|--------|-----------|--------|
| 1 | Six categories × six units | Home / Convert | All 36 units selectable and convert correctly | ✅ Built |
| 2 | Temperature function pairs | Convert | C↔F↔K↔R↔Ré↔De verified by unit tests | ✅ Built (`ConversionEngineTest`) |
| 3 | Decimal places setting | Settings | 0–6 places applied to result | ✅ Built |
| 4 | Default category setting | Settings | Survives process death | ✅ Built |
| 5 | Empty / Content / Invalid states | Convert | No crash on bad input | ✅ Built (plus a fourth, Loading) |
| 6 | Rotation preserves state | Convert | Input + selections kept | ✅ Built (`SavedStateHandle`) |
| 7 | Bottom banner (test) | Convert | Never covers controls | ✅ Built |
| 8 | Interstitial after conversion | Convert | Never first session, daily cap 4 | ✅ Built |
| 9 | UMP consent | App start | Before any ad request | ✅ Built |
| 10 | RMAAC logo in About | Settings | Visible | ✅ Built (placeholder vector — see below) |

## Visual direction
A dark "neon" theme with a fixed custom palette replaces the original
Material-3-default-colours-only constraint. The app does not use dynamic colour and has
no light theme. See [`DESIGN.md`](DESIGN.md) for what's still Material 3 underneath
(components, touch targets) versus what's custom (colour, the on-screen keypad).

## NOT building this week

| Idea | Why not now |
|------|-------------|
| Live currency rates | Stretch goal only; requires network |
| Extra screens | Scope lock — Home, Convert and Settings only |
| Maps / GPS / sensors | Explicitly out of scope |

## Monetization

| Placement | Format | Trigger | Cap |
|-----------|--------|---------|-----|
| Bottom of Convert | Banner | Always (after consent) | — |
| After conversion | Interstitial | Successful convert button | 4 / day, never first session |

## Out of scope permanently
- Any permission beyond INTERNET for ads (in practice `ACCESS_NETWORK_STATE` is also
  declared — see `ARCHITECTURE.md` "Manifest permissions")
- Claiming measurement accuracy beyond pure arithmetic
- Live data of any kind

## Known gaps against this spec
- The RMAAC logo (`ic_rmaac_logo.xml`) is still a placeholder vector, not the official
  asset. A second, unused logo file (`rmaac_logo.xml`) also exists in the same
  directory and should be deleted once the real asset lands.
- The privacy policy URL in `strings.xml` now points at a real-looking domain
  (`https://rmaacgroup.com/privacy`) — confirm that page actually exists before
  shipping; it was a `.invalid` placeholder in an earlier revision of this project.

## Open questions
None — brief is complete.
