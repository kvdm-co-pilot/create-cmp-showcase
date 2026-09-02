# Fuelled design system

The doc of record for the design language. The **governed truth** is the code it describes —
`presentation/theme/Theme.kt` + `Tokens.kt` (the `design-system` artifact) and
`presentation/components/*.kt` (the `components` artifact) — and the studio console's
Design-language page renders the live catalogue from the tree. This document says *why* each
value is what it is and *how* to use it, which the code cannot. When the two disagree, the
code is right and this file has a bug.

Brand feel, from [`specs/intent.md`](../specs/intent.md): **strong · precise · energizing** —
"closer to a performance instrument than a clinical health chart, without tipping into loud
or gimmicky". Every section below is that sentence, applied.

The motion layer (§7) was decided in [`docs/features/motion.md`](./features/motion.md) and
is promised in `specs/motion.spec.md` (MOTION-01..12); like everything else here it is
signed and gated.

---

## 1. Colour

Dark-first, single-theme. One accent, a graphite depth ladder, four semantic hues, three
macro hues. Screens read colour from `FuelledColors` or `MaterialTheme.colorScheme`;
`Color(0x…)` literals outside `presentation/theme` fail ARCH-05.

### The accent

| Token | Hex | Role |
|---|---|---|
| `Primary` / `Accent` | `#B8FF3C` | electric lime — progress, primary CTA, selection, **and the protein macro** |
| `OnPrimary` / `OnAccent` | `#0C1500` | text on lime |
| `Secondary` | `#2A332E` | muted graphite for secondary surfaces and unselected chips |
| `OnSecondary` | `#E7EEE9` | text on secondary |

**Rule — the accent is scarce.** It marks the thing that matters on the screen: the ring's
progress, the one primary button, the selected tab, the protein figure. If two things on a
screen are lime, one of them is wrong. Protein sharing the accent is deliberate: it is the
star macro, and the intent brief asks for it "front and centre".

### The graphite ladder

| Token | Hex | Where |
|---|---|---|
| `Background` | `#0A0C0B` | the page |
| `Surface` | `#121614` | cards, the bottom bar, list rows |
| `SurfaceVariant` | `#1B211E` | chips, skeleton bars, the tray's checked row |
| `SurfaceBright` | `#232A26` | the hero card, the highest resting surface |
| `OnSurface` | `#F3F6F4` | primary text |
| `OnSurfaceVariant` | `#A7B2AC` | secondary text, captions, unselected icons |
| `Outline` | `#333B37` | input borders |
| `OutlineVariant` / `Divider` | `#232A26` | hairlines, the ring's track |

Material's tonal overlay is switched off (`surfaceTint = Transparent`) and the M3 container
levels are mapped onto the ladder explicitly, so dialogs, menus and sheets stack on *these*
greys rather than on a computed tint. Depth is the ladder, never a shadow: `ElevationCard`
is 2 dp of tonal elevation and `ElevationModal` 8 dp.

### Semantic and macro hues

| Token | Hex | Meaning |
|---|---|---|
| `Success` | `#58E08A` | done, veg count, a kept session |
| `Warning` | `#FFC24B` | late, missed-but-recoverable; **also `Fat`** |
| `Error` | `#FF6B6B` | failures only — never decoration |
| `Info` | `#6BB4FF` | water, supplements; **also `Carbs`** |
| `Protein` | `#B8FF3C` | = `Primary` |

Semantic colour is separate from the accent and does not count against the scarcity rule.
Macro hues are used **only** in data visualisation (bars, tags, segments), never on chrome.

---

## 2. Typography

One family — **DM Sans**, weights 400 / 500 / 600 / 700, bundled — on a twelve-rung ramp
(`FuelledTypeRamp`, readable without a composition so the console publishes the same
numbers the app renders).

| Rung | Weight | Size / line | Tracking | Use |
|---|---|---|---|---|
| `displayLarge` | 700 | 56 / 58 | −1.5 | reserved |
| `displayMedium` | 700 | 44 / 46 | −1.0 | the protein figure |
| `displaySmall` | 700 | 32 / 36 | −0.5 | "kcal left" in the ring |
| `headlineLarge` | 700 | 28 / 32 | −0.5 | screen titles (`AppHeader`) |
| `headlineMedium` | 600 | 22 / 28 | −0.3 | section heads |
| `titleLarge` | 600 | 18 / 24 | — | card titles, stat values, the wordmark |
| `titleMedium` | 600 | 16 / 22 | — | list-row titles, slot labels |
| `bodyLarge` | 400 | 16 / 24 | — | settings rows, prose |
| `bodyMedium` | 400 | 14 / 20 | — | subtitles, captions with sentences |
| `labelLarge` | 600 | 14 / 18 | — | bar labels |
| `labelMedium` | 500 | 12 / 16 | +0.5 | values, times, units |
| `labelSmall` | 600 | 11 / 14 | +0.8 | ALL-CAPS eyebrows ("UP NEXT", "PROTEIN"), tab labels |

**Rules.** Hero numerals are heavy and optically tightened — the negative tracking on the
display rungs is what makes a data screen read as a product, not a form. Eyebrows are
`labelSmall`, uppercase, tracked. Every figure that can change wears **tabular numerals**
(motion D10) so a counting number never wobbles. No size off the ramp: the bottom bar's raw
`10.sp` is the one exception in the app and motion D5 retires it.

---

## 3. Space, shape, layout

| Token | Value | Use |
|---|---|---|
| `PaddingPage` | 20 dp | screen gutter (`ScreenColumn`) — motion OD4: 20 dp everywhere |
| `PaddingCard` | 16 dp | inside a card |
| `GapCard` | 12 dp | between cards in a list |
| `BottomNavHeight` | 72 dp | the bar, above the system inset |
| `RadiusCard` | 16 dp | list rows, tiles |
| `RadiusInput` | 14 dp | text fields |
| `RadiusModal` | 24 dp | dialogs, the hero card |
| `RadiusPill` | 999 dp | chips, tags, the tab indicator |

Screens are a single column: `ScreenColumn(screenTag)` as the root (it pads `PaddingPage`,
tags the root, and self-reports its tokens to the inspector), `AppHeader` first on pushed
screens, then content in `GapCard` rhythm. Tabs inherit insets from `BaseScreen` via the
shell and never re-wrap it (SHELL-05). The touch floor is **48 dp** on every control
(COMP-03, WCAG 2.2 SC 2.5.8); `AppButtonDefaults.MinTouchTarget` and `AppIconButton` carry
it so screens do not have to.

---

## 4. Iconography and brand

Material Symbols, filled, 24 dp, tinted `OnSurfaceVariant` at rest and `Primary` when
selected; always a `contentDescription` or an explicit `null` for the decorative case.
The brand is the **bolt in a lime badge** (`FuelledMark`, `FuelledWordmark` in
`presentation/brand/`), drawn from a point path, not an asset — which is what lets motion D7
animate it as a stroke.

---

## 5. Components — the registry

Nineteen composables in `presentation/components/`, one story each in
`inspector/ComponentStories.kt` (the lane fails a component without a story). Screens
compose these; hand-rolling a header, a loading state or a list row is a review finding.

| Group | Component | Job |
|---|---|---|
| Shell | `BaseScreen` | edge-to-edge scaffold owning the system insets |
| | `ScreenColumn` | the tagged, token-padded page root |
| | `AppHeader` | title, optional back, trailing actions |
| | `AppBottomBar` (+ `NavItem`) | the five tabs, `nav_<slug>` tags, 48 dp targets |
| Actions | `AppPrimaryButton` | the one filled CTA on a screen |
| | `AppTextButton` | low-emphasis actions, header actions |
| | `AppIconButton` | 48 dp icon control, description required |
| State | `ContentStateContainer` | Loading / Error / Empty / Content, tags derived from `screenTag` |
| | `EmptyState`, `ErrorState` | the two non-content arms |
| | `ListItemSkeleton`, `Modifier.shimmer()` | the loading arm, same geometry as the row it replaces |
| Data | `ProgressRing` | the circular primitive with a centre slot |
| | `StatBar` | the horizontal bar, optional caption row |
| | `StatTile` | big value over caption |
| | `Tag` | inline coloured label + muted value ("P 38g", "NEXT up now") |
| Lists | `ListItemCard` | title / subtitle / leading / trailing on a card surface |
| Stub | `PlaceholderScreen` | a generated tab that has no feature yet |

**Inclusion rubric.** A composable joins the registry when a second screen needs the same
shape — not the same *look*, the same shape. A segmented macro bar is not a `StatBar` with
options; it is its own thing (do-not-force-reuse). The registry is law once approved: a new
or changed common component invalidates `components` until a human re-approves on rendered
output.

Motion D4 adds six: `Modifier.pressable()`, `Modifier.enterRise(index)`, `AnimatedNumber`,
`TickBurst`, `GoalBloom`, and the cross-fading arms of `ContentStateContainer`.

---

## 6. States and copy

- **Loading** is a skeleton shaped like the content it precedes, never a spinner in a list;
  the spinner exists for short, single-value waits.
- **Empty** says what would fill it and offers the verb ("Add food"), in the container's own
  body — the empty body *is* the control.
- **Error** names what went wrong in the user's words and offers retry; a raw
  `Throwable.message` never reaches the screen.
- **Affordance is a promise.** A row that looks tappable is tappable. Nothing renders as a
  control while wired to nothing (usability-pass's rule; motion D14 applies it).
- Eyebrows are uppercase and terse ("UP NEXT"); verdicts are human ("goal hit", "nice work",
  "That's the day — six for six").

---

## 7. Motion (`docs/features/motion.md` · `specs/motion.spec.md`)

Motion is how the data explains itself. Six principles: weighted not floaty · data moves,
chrome doesn't · the accent is light and light is rare · continuity over cuts · nothing loops,
nothing nags · reduced motion is a scheme, not a switch.

### Tokens — `FuelledMotion` (in `Tokens.kt`)

| Duration | ms | Easing | Curve | Spring | ζ / k |
|---|---|---|---|---|---|
| `Instant` | 0 | `Standard` | 0.2, 0, 0, 1 | `Settle` | 1.0 / 1500 |
| `Quick` | 120 | `Enter` | 0.05, 0.7, 0.1, 1 | `Weighty` | 1.0 / 200 |
| `Standard` | 240 | `Exit` | 0.3, 0, 0.8, 0.15 | `Lively` | 0.6 / 400 |
| `Emphasized` | 400 | | | | |
| `Expressive` | 700 | | | | |
| `Celebration` | 1100 | | | | |

Stagger `40 ms` per item, capped at 6 · `EnterRise` 16 dp · `ScreenSlide` 24 dp ·
`PressScale` 0.97 · `TabScale` 0.96 · `TickPop` 1.25 · `ShimmerSweep` 1200 ms.

### Scheme

`LocalMotion` is `Full`, `Reduced` (cross-fades only, from the platform's reduce-motion
setting) or `Instant` (end state on frame 0 — what the preview harness and every UI test
run under). Every animation reads its spec through the scheme; a spec literal outside
`theme`/`components` fails MOTION-01.

### Where motion lives

| Moment | Motion |
|---|---|
| Screen push / pop | slide `ScreenSlide` + fade, `Emphasized`/`Enter`; pop mirrored |
| Tab switch | fade-through; indicator pill slides on `Settle`; icon pops on `Lively` |
| Arrival | cards rise `EnterRise` in a 40 ms stagger, once per entry |
| Ring, bars, numbers | sweep / fill / count on `Weighty`; the ring's head glows |
| Tick | `TickPop` on `Lively` + one expanding ring in the tick's colour |
| State change | Loading ↔ Content cross-fade on `Standard`; expand/collapse on `Standard` |
| Goal reached | one lime bloom on `Celebration`, once per logical day (OD3) |
| Loading | the shimmer — the only loop in the app |

---

## 8. Accessibility

48 dp targets everywhere (gated). Text contrast: `OnSurface` on `Background` is 18.0:1,
`OnSurfaceVariant` on `Surface` 8.3:1, `OnPrimary` on `Primary` 15.5:1 — all AAA;
`OnSurfaceVariant` on `SurfaceBright` is 6.7:1 (AA). Every control has a name; icons that
only decorate say so. Reduced motion is honoured from the OS setting (motion D2); nothing
flashes faster than 3 Hz; no animation is required to understand a state — every animated
value is also written as text.

---

## 9. Changing the system

The design system is a governed artifact. To change a token or component: reopen with a
reason (`node qa/approve.mjs --reopen design-system --reason "…"` or the console), change
it, render it on real screens in the preview, and re-approve on the rendered output. The
preview harness publishes the live catalogue; the inspector's drift check compares rendered
`designToken` self-reports against it — so a component that does not self-report is a
component the check cannot protect (motion D12 fixes the three screen roots that do not).
