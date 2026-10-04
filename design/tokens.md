# Design tokens — Anti-Theft Alarm

No external design source exists for this project. These tokens were proposed during intake
and approved ("go with your defaults") on 2026-10-04. Theme code in
`app/src/main/java/com/anti/theft/core/theme/` is generated from this file; change the
snapshot here first, then the theme.

Direction: dark-first "security console". Deep navy canvas with a soft radial glow, a large
animated shield as the status hero, glass-like feature cards, and a full-screen red alarm
overlay. A matching light theme follows the system setting.

## Colour

Tokens are named `Color` + the first four hex digits of the colour.

| Token | Hex | Role |
|---|---|---|
| `Color0A0F` | #0A0F1F | Dark background · text on light |
| `Color131A` | #131A2E | Dark surface (cards) |
| `Color1C24` | #1C2440 | Dark raised surface · switch track |
| `Color2A33` | #2A3354 | Dark outline / divider |
| `Color4F7C` | #4F7CFF | Primary accent · gradient start |
| `Color22D3` | #22D3EE | Secondary accent · gradient end |
| `Color34D3` | #34D399 | Protected / success |
| `ColorFBBF` | #FBBF24 | Arming · warnings |
| `ColorFF4D` | #FF4D5E | Alarm · error |
| `ColorB312` | #B3123A | Deep alarm red (overlay gradient) |
| `ColorF5F7` | #F5F7FF | Primary text on dark |
| `Color9AA4` | #9AA4C7 | Secondary text on dark |
| `ColorF0F3` | #F0F3FA | Light background |
| `ColorFFFF` | #FFFFFF | Light surface · text on accent |
| `Color5B65` | #5B6585 | Secondary text on light |
| `ColorDFE5` | #DFE5F2 | Light outline / divider |

Alpha variants use `.copy(alpha = …)` on the base token.

## Typography

Family: **Manrope** (SIL OFL 1.1, `res/font/manrope_*.ttf`). Static instances (400, 500,
600, 700, 800) were generated from the Google Fonts variable font so weights render
correctly down to API 24.

| Style | Size / line height | Weight | Letter spacing |
|---|---|---|---|
| displayLarge | 32 / 40 | ExtraBold (800) | -0.5 |
| headlineSmall | 24 / 32 | Bold (700) | -0.25 |
| titleLarge | 22 / 28 | Bold (700) | 0 |
| titleMedium | 17 / 24 | SemiBold (600) | 0 |
| bodyLarge | 16 / 24 | Regular (400) | 0 |
| bodyMedium | 15 / 22 | Regular (400) | 0 |
| bodySmall | 13 / 18 | Medium (500) | 0 |
| labelLarge | 14 / 20 | SemiBold (600) | 0.1 |
| labelMedium | 12 / 16 | SemiBold (600) | 0.4 |
| labelSmall | 12 / 16 | Medium (500) | 0.2 |

## Spacing

4 · 8 · 12 · 16 · 20 · 24 · 32 · 48 dp.

## Shape

| Use | Radius |
|---|---|
| Chips, small controls | 12 dp |
| Cards | 20 dp |
| Sheets, alarm panel | 28 dp |
| Buttons | Fully rounded (pill) |

## Layout

- Compact (< 600 dp wide): single scrolling column, 16–20 dp side gutters.
- Medium / expanded (≥ 600 dp): two panes — status hero left, feature cards right — with
  content width capped at 1040 dp and centred.
- No orientation lock.

## Iconography

Material Symbols Rounded (Apache 2.0), converted to vector drawables in `res/drawable/ic_*.xml`.
App launcher icon: custom shield on the primary→secondary gradient.

## Sound

`res/raw/alarm_siren.wav`: synthesised in-house (no third-party licence), 1.6 s seamless
loop, 22.05 kHz mono.
