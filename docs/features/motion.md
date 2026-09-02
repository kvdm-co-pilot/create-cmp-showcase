# Feature brief: motion — the design system's missing layer, and the screens it moves

**Spec:** [`specs/motion.spec.md`](../../specs/motion.spec.md) — MOTION-01..MOTION-NN (to be
written from this brief, once signed). Amends the "not promised here" line of
[`specs/meal.spec.md`](../../specs/meal.spec.md) (M6 lands), CAT-01's surface title, and
PROF/SUPP/WORK clauses where the information-architecture decisions below close *yes*.
**Reference:** [`docs/DESIGN-SYSTEM.md`](../DESIGN-SYSTEM.md) — the full design system this
brief adds the motion layer to, kept current as the doc of record for the design language.

```json cmp:feature
{
  "touches": [
    "design-system",
    "components",
    "exemplar-feature",
    "feature-design:app-updates",
    "feature-design:meal-plan",
    "feature-design:meal",
    "feature-design:settings",
    "feature-design:workouts",
    "feature-spec:catalog",
    "feature-spec:profile",
    "feature-spec:supplements",
    "feature-spec:meal"
  ],
  "screens": true
}
```

## The ask

> "Create the best UI possible for this app — we really want to demonstrate what frontier
> models are capable of, and make this a huge wow factor. Add animations to further enhance
> the wow factor. Give me a full design system; you don't need to change the colours or UI if
> you can't find anything better than what we have, but we can improve on animations. Think
> deep and creatively as a master UI/UX expert. Also look at whether the current screens and
> information grouping are ideal for users, or whether we can improve."

## The problem, honestly

Fuelled's visual language is already good and already governed: a dark graphite ladder, one
electric-lime accent used with discipline, a twelve-rung DM Sans ramp with tight display
tracking, four radius tokens, a nineteen-component registry, and a token self-report the
inspector reads for drift. The palette is not the gap. **The gap is that nothing moves.**

The survey is unambiguous. Across every source set there is exactly one animation —
`Shimmer.kt`'s loading sweep. There are no navigation transitions (the `NavHost` inherits the
library default on all eleven destinations), the bottom bar swaps colour instantly, the
`ContentStateContainer` flips its four arms with a bare `when`, `ProgressRing` and `StatBar`
draw their value with no interpolation, and the app-gate swap from onboarding to the app is a
hard cut. `docs/features/meal.md` §5 said as much in July and deferred it as slice M6 —
"motion tokens + ring animation". This brief is M6, grown into the layer the design system
was always missing, plus the information-architecture pass the second ask adds.

Why motion is the right place to spend the "wow": in a data-forward instrument, motion is
not decoration — it is **how the data explains itself**. A ring that fills to 61% tells you
where you stand; a ring that *sweeps* to 61% in 700 ms tells you that this meal moved you
there. That is legibility, not flourish, and it is what MacroFactor and Zero (the intent's
reference apps) do that a static screen cannot.

## Principles — the physics of Fuelled

The intent brief's three words are **strong · precise · energizing**, and "closer to a
performance instrument than a clinical health chart — without tipping into loud or
gimmicky". Every motion decision below is derived from those words:

1. **Weighted, not floaty.** Springs settle decisively — a chronograph needle, not a bubble.
   Overshoot is reserved for reward moments (a tick, a goal hit); layout never bounces.
2. **Data moves, chrome doesn't.** Numbers count, rings sweep, bars grow. Cards and headers
   only fade and rise a little. If an element carries no data, it earns no choreography.
3. **The accent is light, and light is rare.** Lime already means "progress / CTA /
   selection". In motion it becomes the *glow at the head of the ring* and the *one bloom*
   when the protein goal lands. Nothing else glows.
4. **Continuity over cuts.** Tabs fade through, pushes slide from the direction you are
   going, state changes cross-fade in place. You are never dropped onto a screen.
5. **Nothing loops, nothing nags.** The shimmer is the only repeating animation. No idle
   pulses, no attention-seeking, no parallax on scroll.
6. **Reduced motion is a first-class scheme, not a switch that turns things off.** And the
   preview harness renders every animation at its end state, so golden trees and structural
   diffs never see a frame mid-flight.

## Decisions

### The motion layer

**D1 — Motion tokens join the design system in `Tokens.kt`, as `FuelledMotion`.** Not a new
`Motion.kt`: the governed `design-system` artifact hashes exactly `Theme.kt` + `Tokens.kt`,
and motion values that lived outside those two files would be design values nobody signed.
Supersedes meal.md §5's three provisional names (`MotionFast/Standard/Ring`) with a full
scale, so the same three moments still have a token — and so does everything else:

| Group | Token | Value | For |
|---|---|---|---|
| Duration | `Instant` | 0 ms | the instant scheme; tests |
| | `Quick` | 120 ms | press feedback, colour/state swaps, outgoing fades |
| | `Standard` | 240 ms | fades, rises, cross-fades, expand/collapse |
| | `Emphasized` | 400 ms | screen pushes and pops, tab fade-through |
| | `Expressive` | 700 ms | the ring's sweep, the bars' first fill, count-ups |
| | `Celebration` | 1100 ms | the goal bloom — once per logical day, nothing else |
| Easing | `Standard` | cubic(0.2, 0, 0, 1) | anything that starts and ends on screen |
| | `Enter` | cubic(0.05, 0.7, 0.1, 1) | arriving elements (decelerate) |
| | `Exit` | cubic(0.3, 0, 0.8, 0.15) | leaving elements (accelerate) |
| Spring | `Settle` | damping 1.0 · stiffness 1500 | layout, the tab indicator, press scale |
| | `Weighty` | damping 1.0 · stiffness 200 | the ring, bars, animated numbers |
| | `Lively` | damping 0.6 · stiffness 400 | the tick pop, tag pop-in, "goal hit" |
| Stagger | `StaggerStep` | 40 ms | per-item delay in an entrance |
| | `StaggerCap` | 6 | the seventh item and beyond share the sixth's delay |
| Distance | `EnterRise` | 16 dp | how far an arriving card rises |
| | `ScreenSlide` | 24 dp | how far a pushed screen slides in |
| Scale | `PressScale` | 0.97 | a pressed card / row / nav item |
| | `TabScale` | 0.96 | the incoming tab's fade-through start |
| | `TickPop` | 1.25 | the tick's peak before it settles |

Two easings are Material's emphasized pair on purpose — the platform's own vocabulary for
"arriving" and "leaving" is what users' thumbs already expect; the springs are ours.

**D2 — Motion is a composition-local *scheme*, and every animation reads its spec through
it.** `LocalMotion` carries one of three `MotionScheme`s: **Full**, **Reduced**, **Instant**.
`Reduced` keeps meaning intact with cross-fades only — no rise, no stagger, no pops, no
sweep, the ring and bars jump — and replaces the shimmer with a static skeleton. `Instant`
renders every animation at its end state on frame 0. The preview harness and every Compose
UI test set `Instant`, so a golden tree can never capture a frame mid-flight and a test can
never flake on a clock — deterministic by construction (ARCH-12's spirit applied to time).
`Reduced` is selected from the platform's own setting through an `expect fun
platformReducedMotion(): Boolean` (Android: animator duration scale = 0; iOS:
`UIAccessibilityIsReduceMotionEnabled`; desktop: false), never from an in-app toggle —
one setting, the one the person already made.

**D3 — Motion literals are gated, like colours.** A `tween(`, `spring(`, `keyframes(` or
`animate*AsState(` whose spec is not a `FuelledMotion` reference fails a conformance test
(MOTION-01) in any `commonMain` file outside `presentation/theme` and
`presentation/components`. The gate is a MOTION clause in the feature's own spec, not a new
ARCH clause — so `architecture` is *not* reopened for a rule the design system owns. The one
existing offender (`Shimmer.kt`'s raw 1200 ms) is folded into the token catalogue as
`ShimmerSweep`.

**D4 — Six motion primitives join the component registry, each with a story.** Screens
compose these; they never animate by hand (the same rule as "do not hand-roll a header"):

| Primitive | What it is |
|---|---|
| `Modifier.pressable()` | scale to `PressScale` on press, `Settle` back — for any card, row, nav item |
| `Modifier.enterRise(index)` | fade + rise `EnterRise`, staggered by `index × StaggerStep` (capped), once per screen entry |
| `AnimatedNumber(value)` | a count-up on `Weighty`, tabular figures so the digits never jitter |
| `MotionStateContainer` | `ContentStateContainer` gains a cross-fade between its four arms (`Standard`); not a new component, an amended one |
| `TickBurst` | the checked state of an `AppIconButton` tick: `TickPop` on `Lively` plus one expanding ring in the tick's colour |
| `GoalBloom` | a single lime radial sweep across a surface on `Celebration`, with a haptic `Confirm` |

`ProgressRing` and `StatBar` are amended rather than wrapped: they animate their own value
on `Weighty`, and the ring gains a glow at the arc's head (D9).

**D5 — The registry's tab bar gets an indicator, and the shell gets a transition.**
`AppBottomBar` draws a lime pill (14% alpha, `RadiusPill`) behind the selected icon that
*slides* between items on `Settle` — the instrument's needle. The icon scales up on `Lively`
as it becomes selected. `AppShell` swaps tab content with a Material fade-through: outgoing
fades on `Quick`/`Exit`, incoming fades on `Standard`/`Enter` from `TabScale`. Labels move to
the ramp's `labelSmall` (the bar carries a raw `10.sp` today — the only off-ramp size in the
app).

**D6 — Pushes slide, pops slide back.** `AppNavHost` declares one transition set for the
whole graph: enter = slide in `ScreenSlide` from the trailing edge + fade on
`Emphasized`/`Enter`; exit = fade on `Quick`/`Exit` with an 8 dp lead; pop mirrors both. Set
once on the host, inherited by every destination — the same reasoning that put
`exposeTestTagsForAutomation()` on the host rather than per screen.

**D7 — The app's first frame is choreographed.** The gate swap (intro → onboarding → app)
cross-fades on `Standard` (see D18 for the intro itself). Onboarding itself opens with the brand mark scaling in on `Lively`
and the bolt drawing itself on `Expressive` (a stroke-trim on the existing bolt path — no
new asset), then the three fields rise in a stagger. First impressions are the cheapest wow
in the app, and today the screen simply appears.

**D8 — Per-screen choreography, derived from the principles, not invented per screen.**

| Screen | On arrival | On interaction |
|---|---|---|
| Today | hero card rises; ring sweeps from 0 with its glow head; "kcal left" counts up; the three bars fill in a 40 ms stagger; then the protein card and the rest rise in stagger | tick → `TickBurst`; card border colour cross-fades; DONE tag pops in on `Lively`; water tick the same in Info; entry editor expands on `Standard` with `animateContentSize`; undo bar slides up on `Settle`; protein goal reached → `GoalBloom` on the protein card, once per logical day |
| Week | day-strip selection pill slides on `Settle`; slot cards rise in stagger | day change → content fade-through on `Standard`; everything else as Today's card |
| Meals | rows rise in stagger; search results use `animateItem` for reorder and fade | row press → `pressable`; detail push slides; the macro segment bar grows on `Weighty` |
| Tray | rows rise in stagger | check → row tint cross-fades; stepper value is an `AnimatedNumber`; total bar counts; the Add button's enabled colour cross-fades |
| Training | day cards rise in stagger; the summary counts | today's tick → `TickBurst` |
| Progress | the week's numbers count up; day bars fill one day at a time — the chart draws itself; training dots pop in on `Lively` | — |
| Profile / Settings | cards rise in stagger | dialogs scale from `TabScale` + fade on `Standard` |
| Supplements | summary bar fills; groups rise in stagger | take → `TickBurst` |
| Any data screen | Loading → Content cross-fades: the skeleton already shares the row's geometry, so the swap is a fade with no jump | — |

Every arrival choreography runs **once per entry to the screen**, never on recomposition,
and never on scroll.

**D18 — Ignition: the app's first frame is the instrument powering up (added on Karel's
ask, 2026-09-02, inside this walk).** The app rendered NOTHING while the start gate resolved.
It now plays the ignition: a lime spark, the day ring sweeping to full — the real
`ProgressRing`, glow head and all — the mark revealed from the spark, the name's letters
rising in the arrival stagger, and then the hand-off: the intro's ring is a shared element
with Today's hero ring, so as the app dissolves in, the ring flies into its place on the
dashboard. Under 2 s, tap to skip, once per process (a rotation never replays it), a Quick
fade under Reduced, instant in tests. It doubles as the loading screen — if the gate is still
unresolved when it ends, it holds its end state. Built from parts the app already owns, on
purpose: an intro that introduces a visual language the app then doesn't speak is a trailer
for a different film. This makes the feature's own surface `presentation/motion/IntroScreen.kt`
(`screens: true` → `feature-design:motion`, signed on rendered output).
*Considered and rejected:* a logo animation as a video/Lottie asset (a second source of truth
for the brand mark); a tagline (the intent says "without tipping into loud"); showing the
day's numbers in the intro (there is no data yet — an intro that lies about readiness is
worse than a blank frame).

### Visual refinements — the palette stays, the finish improves

The ask allowed colour changes only for something clearly better. I found nothing better
than the lime-on-graphite ladder; what I found were four finishing details a performance
instrument has and a scaffold does not:

**D9 — The ring gets light.** The arc is a sweep gradient (Primary at 100% → 70%) and its
head carries a soft radial glow (Primary, 35% alpha, radius = stroke). The track stays
`OutlineVariant`. When progress reaches 1.0 the ring does one `Lively` breath. Same token
colours; the difference is that the ring reads as *charged* rather than *filled*.

**D10 — Tabular numerals everywhere a number can change.** `FontFeatureSettings("tnum")` on
the display and label rungs used for figures. DM Sans ships tabular figures; without them an
animated count visibly wobbles as digit widths change. Precision is a brand word.

**D11 — Machined edges and one ambient glow.** The hero card gains a 1 dp top-edge highlight
(onSurface 10% → 0%), and Today alone gains a radial lime glow (4% alpha) behind the hero,
on the token background. Depth without a new colour. Only Today: a glow on every screen is a
wallpaper, on one it is a spotlight.

**D12 — The three hand-rolled screen roots (Today, Profile, Supplements) move onto
`ScreenColumn`.** They pad 20 dp where the token says 16 dp and emit no token self-report, so
the inspector's drift check cannot see them. Supplements also gains the `AppHeader` every
other pushed screen has. *This changes Today's gutter by 4 dp — see OD4.*

### Information architecture — what the second ask found

The five-tab bar from `navigation-ia` is right and stays. Walking every route from every
entry point found six seams, three of them the app's own rules would call defects:

**D13 — The Meals tab says "Foods".** NAV-D3 renamed the tab; the screen's header still
reads "Foods". The two words disagree on every visit. The header becomes "Meals" — a copy
amendment to CAT-01's surface name, nothing else.

**D14 — Profile's two inert rows come off.** "Connected apps" and "Account" render as list
rows with no tap and no chevron. usability-pass's standing rule — *an affordance is a
promise* — says a row that does nothing is a row that should not be there. Removed until
they are real; the tags return with the features.

**D15 — The meal builder gets a door on the Meals tab.** The builder is reachable only from
the plan screen (`plan_build_meal`). It builds *meals*; the tab is called *Meals*; a person
looking for it goes there first and finds a catalog. It becomes a header action on Meals
("Build a meal") alongside "New food"; the plan's door stays.

**D16 — Supplements gains "Edit stack".** The supplements screen shows today's doses and
offers no way to change the stack; editing lives under Settings → Supplement stack, two
screens away from the thing you are looking at. A header action on Supplements opens the
same editor. The editor does not move (it stays a Settings card — the same "smaller change"
reasoning navigation-ia OD3 applied to the workout editor); it gains a second door.

**D17 — Progress gets a door on Week.** The retrospective (the week's verdict, four-week
trend, weight) is reachable only through Profile's stats row. A person planning their week
on the Week tab is one thought away from "how did last week go" and two screens from the
answer. Week's header gains a "Review" action; Profile's link stays. *Considered and
rejected:* a Plan / Review segmented control on the Week tab — it makes one tab two surfaces
and reopens NAV-01's "five daily surfaces" reasoning for a link's worth of benefit.

**Considered and kept as-is:** protein appearing twice on Today (the hero bar and the focus
card) — the intent brief asks for protein "front and centre" and the focus card is that
promise; D8 makes the second appearance the goal-bloom surface, which gives the repetition a
job. The workout editor staying in Settings — closed by navigation-ia OD3, not reopened.
Settings as the home of Units, Reminders and Updates — those *are* settings. The tab label
"Profile" — NAV-01 names it.

## Blast radius and contracts

| Artifact | What happens |
|---|---|
| `design-system` | `FuelledMotion` in `Tokens.kt`; `Theme.kt` provides `LocalMotion` (D1, D2) |
| `components` | six primitives added, `ProgressRing`/`StatBar`/`ContentStateContainer`/`AppBottomBar` amended, each with a story (D4, D5, D9) |
| `exemplar-feature` | `FoodsScreen` adopts `enterRise` and `pressable`; the stamper clones the motion pattern from then on |
| `feature-design:*` (five signed) | every screen re-renders with motion at end state; re-approve on rendered output — "as declared" |
| `feature-spec:meal` | the "motion tokens deliberately not promised here" note is struck; M6 lands under `specs/motion.spec.md` |
| `feature-spec:catalog` | CAT-01's surface named "Meals" (D13), the builder door (D15) |
| `feature-spec:profile` | the two inert rows withdrawn (D14) |
| `feature-spec:supplements` | the "Edit stack" door (D16) |
| `specs/motion.spec.md` | new — MOTION-01..NN: the scheme, the gate, the primitives' behaviour, the once-per-day bloom, reduced motion, instant-in-tests |
| `specs/meal-plan.spec.md` | D17's "Review" door, if OD2 closes yes |
| Golden trees | unchanged where motion only interpolates; Today/Profile/Supplements roots change under D12; Meals/Supplements/Profile chrome changes under D13–D16 |
| `qa/e2e/smoke.yaml` | no tag changes expected; the E2E device tier runs under `Full` motion, so Maestro's waits stay as they are (the longest non-celebration motion is 700 ms) |
| `PreviewRegistry` | `today@goal-hit` variant added (the bloom's end state); component stories for the six primitives |

**`architecture` is NOT reopened** (D3 keeps the gate in the feature spec). **`navigation-ia`
is NOT reopened** (the tab set and the editor location stand).

## How this is judged — the honest limit of stills

The preview loop renders stills, and stills cannot show a spring. Three surfaces carry the
judgment: (1) the design page published with this brief, which plays the whole language in a
browser so the tokens can be felt before code exists; (2) the component stories, which render
each primitive at its end state — geometry and colour are signed there; (3) the desktop
dev-client (`./gradlew :composeApp:hotRunDesktop --auto`), where the human watches the real
screens move before re-approving the five feature designs. The brief is signed on (1); the
build is accepted on (3).

## Open decisions

**All five closed by Karel on 2026-09-02, with the full walk delegated:** OD1 yes (ToggleOn on
ticks, Confirm on the bloom) · OD2 take it (`plan_review`, PLAN-19) · OD3 take it (the
bloom, MOTION-10) · OD4 20 dp everywhere (`PaddingPage` = 20 dp) · OD5 **now**, overruling
the recommendation — the shared title ships in this build (FOODS-09). The original questions
stay below as the record of what was asked.

**OD1 — Haptics on the tick and the bloom.** Compose's `LocalHapticFeedback` is available
in `commonMain` with `Confirm`/`ToggleOn`. Proposed: `ToggleOn` on every tick, `Confirm` on
the goal bloom, nothing else. *Yes / no / ticks only.*

**OD2 — The "Review" door on Week (D17).** It is the one IA change that touches a signed
plan spec. *Take it / leave Progress behind Profile only.*

**OD3 — The goal bloom.** Once per logical day when protein reaches the goal: a lime radial
sweep across the protein card on `Celebration`, "goal hit" springing in. Considered and
rejected: confetti and full-screen takeovers (the intent's "without tipping into loud or
gimmicky"). *Take it / reduce to the ring's breath only / none.*

**OD4 — Today's gutter (D12).** Moving Today onto `ScreenColumn` narrows its gutter from
20 dp to the 16 dp token. Alternative: raise `PaddingPage` to 20 dp app-wide (every screen
gets wider gutters). *16 dp everywhere / 20 dp everywhere.* I recommend 20 dp everywhere —
it is what Today, Profile and Supplements already do, and it is the airier of the two.

**OD5 — Shared-element transition from a food row to its detail** (the row's title and macro
tags travel into the detail header). Compose Multiplatform 1.10 supports
`SharedTransitionLayout` in `commonMain`, still marked experimental. It is the single most
"wow" transition available and the single riskiest line in this brief. *Take it as a
stretch, behind the experimental opt-in / leave for a later slice.* I recommend a later
slice: the rest of this brief lands on stable APIs.

## The walk, once this is signed

Decide (this brief) → Design (component stories at end state, the dev-client for motion) →
Contract (`specs/motion.spec.md`, the four amended clauses) → Build (tokens → scheme →
primitives → shell/nav → screens, in that order, so each step is previewable) → Prove (the
lane) → Sign-off (re-approve `design-system`, `components`, `exemplar-feature`, the five
feature designs, then `--accept motion`).
