# MEOW design system

One visual language for every screen. Code: `core/designsystem/theme` (`Color.kt`,
`MeowTokens.kt`, `Type.kt`, `Theme.kt`) and `core/mascot`.

## Colour

Brand values (`MeowPalette`): lavender `#A78BFA`, light lavender `#C4B5FD`, deep violet `#37305F`,
night `#131217`, charcoal `#1F1E24`, cream `#F5F1EA`, soft white `#F4F0F8`. The logo purple
`#8F6EFC` (`BrandPurple`) is kept for brand moments only: icon, splash, welcome.

| Role | Light | Dark |
|---|---|---|
| Background | cream `#F5F1EA` | night `#131217` |
| Card (`cardColor`) | warm off-white `#FFFDF9` + hairline border | charcoal `#1F1E24` + hairline border |
| Raised: dialogs, bubbles, menus (`raisedColor`) | `#FFFDF9` | `#28272E` |
| Inset: fields, tiles inside cards (`insetColor`) | `#F0EBE3` | `#323139` |
| Primary (actions, selection) | violet `#6B46E8` | lavender `#A78BFA` |
| Primary container | `#E7DFFF` | deep violet `#37305F` |
| Text | ink `#1B1A1F` | soft white `#F4F0F8` (never pure white) |
| Secondary text | `#524D58` | `#C9C3D2` |
| Outline (borders of controls) | `#8A8277` | `#7A7587` |
| Divider / card edge | `#DDD5C9` | `#3C3A44` |

Contrast (checked by `ThemeContrastTest` on background, card and raised/inset surfaces): text
≥ 7:1, secondary text and primary ≥ 4.5:1, control borders ≥ 3:1, in both themes.

## Spacing, shape, size (`MeowTokens`)

- Spacing: 4, 8, 12, 16, 24.
- Radius: controls 12, cards 16, sheets 24.
- Touch targets at least 48dp; tool tiles 44dp with a 22dp icon.

## Typography

System font (`Type.kt`). Section headings: `titleSmall`. Body: `bodyMedium`. Hints: `bodySmall`
in secondary text. Timer: `displaySmall`, semibold, tabular figures.

## Interaction states

- **Selected tool**: a filled tile in the primary colour with the icon in its contrasting colour.
  The shape appears, not only the colour, so it reads without colour vision. Changes cross-fade
  in 120 ms.
- **Selected colour or width**: a 3dp primary ring with a gap. Width choices show thickness in
  the text colour, so a black pen still shows on a dark panel.
- **Disabled**: Material's 38%.
- **Pressed**: ripple or, on hold-to-repeat buttons, a soft primary wash.
- **Motion**: short (120–150 ms) fades and expands for tool settings and the cat's bubble; the
  drawer slides; nothing loops in the study panel.

## Components

- **Study panel**: cards in order of use: Araçlar (tools, then "Renk" and "Kalınlık" for the
  selected tool), Çalışma süresi (cat beside the time, progress, main action full width,
  duration), Notlar, Görünüm (page, zoom, rotation), Belge.
- **Speech bubble**: lavender container in light, raised surface in dark; above the cat in the
  panel so it never squeezes the timer.
- **Sticky notes**: selected note gets a 2dp primary border and a stronger shadow; handles are
  20dp dots with a white edge, centred on the corners, in 40dp touch areas.

## Mascot (`core/mascot`)

Drawn in code from the logo (`res/drawable-nodpi/meow_peek.png`): wide round head; broad ears
with rounded tips and warm taupe insides; a curl of fur on top; very large white eyes with big
pupils that look up at rest; small rounded mauve nose; two soft whiskers a side. In the dark
theme a quiet outline separates the black cat from the background (drawn under the fill, so
there are no seams).

| State | Mood | Look |
|---|---|---|
| Awake | `Idle` | looking up, as in the logo |
| Thinking | `Curious` | glancing up and aside, three thought dots |
| Happy | `Happy`, `Celebrating` | smiling eyes, blush (sparkles when celebrating) |
| Sleeping | `Sleeping` | closed eyes, Zz |
| Waking | `Waking` | half-open eyes, a small yawn |
| Others | `Sleepy`, `Surprised`, `Grumpy`, `Encouraging` | kept for existing screens |

Ambient loops (blink, tail, breathing) only run where enabled and when the system allows
motion; the study panel never runs them.
