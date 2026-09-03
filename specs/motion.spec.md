# Spec: motion (the design system's motion layer)

> Cross-feature contract, the `usability-pass` precedent: these clauses span the theme, the
> component registry, the shell, the nav host, and every screen that moves. Born from
> [`docs/features/motion.md`](../docs/features/motion.md): the app had exactly one animation
> (the loading shimmer) and no motion tokens; this contract adds the layer and the discipline
> that keeps it honest. Every clause id is cited by the durable test(s) that verify it
> (`// SPEC: MOTION-NN`). The screen-level choreography (which card rises, which number
> counts) is design, judged on rendered output and the dev-client; what is promised here is
> the machinery every screen's motion is built from and the guarantees it must never break.

**Scope of this contract.** The motion tokens and scheme, the literal gate, the six
primitives, the shell and nav transitions, the four amended components, and the ignition —
the app's first frame (its own surface, `presentation/motion/IntroScreen.kt`, signed as
`feature-design:motion` on rendered output). The IA doors and
the shared title the same brief decided live in the specs of the surfaces they change
(CAT-04, SUPP-14, PLAN-19, FOODS-09). The shared-element transition's *visual* quality is
judged on the dev-client; only its wiring is promised here.

## The tokens and the scheme (brief D1, D2)

- **MOTION-01** — Given any file under `commonMain` outside `presentation/theme` and
  `presentation/components`, Then it constructs no animation spec literal — no `tween(`,
  `spring(`, `keyframes(`, `snap(`, `repeatable(` or `infiniteRepeatable(` — and no raw
  `animate*AsState(` call with an inline spec: motion values come from `FuelledMotion` through
  the `MotionScheme` helpers, the same way design colours come from the token catalog
  (ARCH-05's discipline, applied to time).
- **MOTION-02** — Given a composition, Then `LocalMotion` resolves to exactly one of `Full`,
  `Reduced`, `Instant`: `FuelledTheme` provides `Reduced` when the platform reports reduced
  motion and `Full` otherwise; with no `FuelledTheme` above (every Compose UI test, every
  golden-tree test) it is `Instant`, and the preview harness passes `Instant` explicitly — no
  theme, no motion. The scheme is never read from an in-app setting.
- **MOTION-03** — Given a duration or spring token resolved through the scheme, Then under
  `Instant` every spec is a snap; under `Reduced` a timed spec fades no longer than `Quick`
  with linear easing and every spring snaps; under `Full` the token's own duration, easing,
  damping and stiffness are used unchanged.

## The shell and the graph (brief D5, D6)

- **MOTION-04** — Given the nav graph, Then `AppNavHost` declares its push/pop transitions
  ONCE on the host (`enterTransition`, `exitTransition`, `popEnterTransition`,
  `popExitTransition`), every one built from `FuelledMotion` through the scheme; no
  `composable(...)` registration overrides them. A push slides in `ScreenSlide` from the
  trailing edge with a fade; the outgoing screen fades leading by `ScreenLead`; a pop mirrors
  both; under `Reduced` the slide distances are zero.
- **MOTION-05** — Given the bottom bar renders, Then the selected item reports `selected`
  to assistive tech and only it does, the indicator pill is DRAWN behind the row (never a
  semantics node, so `app_bottom_nav`'s children stay exactly the `nav_<slug>` items), and
  selecting another item moves the pill to it on `Settle` while the shell fades the tab
  content through (`Quick` out, `Standard` in from `TabScale`).

## The primitives (brief D4)

- **MOTION-06** — Given an arrival stagger, Then item `n` is delayed `n × StaggerStep`
  capped at `StaggerCap` (the seventh item and beyond share the sixth's delay), the delay is
  zero under `Reduced` and `Instant`, and the rise never replays on recomposition — an
  arrival happens once per entry into the composition.
- **MOTION-07** — Given an `AnimatedNumber`, Then the text it renders is the rounded live
  value in tabular numerals, which under `Instant` is the target value on the first frame;
  a change of value counts to the new value on `Weighty`.
- **MOTION-08** — Given a `ProgressRing` or `StatBar`, Then it reports
  `progressBarRangeInfo` with the TARGET fraction (clamped 0..1) regardless of where the
  sweep or fill currently is, so assistive tech and tests read where it is going, never a
  frame of the way there; under `Instant` the drawn value is the target.
- **MOTION-09** — Given a `TickButton`, Then in BOTH checked states it exposes the same
  selectable shape `AppIconButton` exposes — one `Button` node of at least 48 dp carrying one
  `Image` child with the required description — and the burst is drawn, never composed, so
  ticking changes no tag, role, text or description.
- **MOTION-10** — Given the protein goal is reached, Then the bloom fires once per logical
  day: the trigger is the date the goal was reached on, a later change of the same day's
  value does not re-fire it, and a new logical day fires it again; a day that starts at or
  above the goal blooms once on arrival. Under `Reduced`/`Instant` the haptic still fires and
  nothing is drawn.
- **MOTION-11** — Given a `TickButton` turns checked, Then a `ToggleOn` haptic is performed
  exactly once for that change, none on un-ticking and none on first composition already
  checked; given the goal bloom fires, a `Confirm` haptic is performed (OD1).
- **MOTION-12** — Given a `ContentStateContainer` changes arm, Then the new arm is rendered
  immediately in the semantics tree (its `<screenTag>_loading` / `_error` / `_empty` /
  content nodes) — the fade is applied to the container's own node, so under `Instant` no
  tag, role, text or description differs from a container with no motion at all.

## What motion does NOT promise — the node-count limit

Written down because the first draft of this contract got it wrong, and the correction is
the kind a future contributor would otherwise re-introduce.

- **MOTION-14** — Given any motion primitive that applies a `graphicsLayer` or a `semantics`
  modifier (`enterRise`, `pressable`, the state container's fade, the ring's and bar's
  `progressBarRangeInfo`), Then it MAY materialise as a structural wrapper node in the tree
  serialiser, and the golden trees hold those wrappers. What every clause above promises is
  the **content** — the tags, roles, text and descriptions a test, a golden and a Maestro
  flow select on — never the node COUNT. A change that alters content is a real regression;
  a change that only nests it deeper is declared golden drift, regenerated explicitly.
  Corollary for reviewers: read a golden diff by its content lines, not its shape.

## The ignition (brief D18)

- **MOTION-13** [tier: device] — Given the app comes to the foreground, Then the ignition (`intro_screen`)
  plays: on a cold process start, and on a return to the foreground after the app has been
  away for at least `IntroReplayAfter`. Coming straight back — glancing at a notification,
  answering a message — does NOT replay it, and neither does a configuration change. The
  away interval is measured with the INJECTED clock (ARCH-13) across the lifecycle's
  stop/start, never from composition state: the root does not recompose on a warm resume,
  which is precisely why a composition-held flag left the ignition unreachable for every
  already-onboarded user (observed on-device, 2026-09-02).
- **MOTION-15** — Given the ignition runs, Then it is built from the app's own parts — the
  spark, the day ring sweeping to full (the registry's `ProgressRing`), the mark revealed
  from the spark, the name's letters rising in the arrival stagger — it completes in under
  2 s on `Full`, a tap anywhere ends it early ("Skip intro"), and `onDone` fires exactly once
  either way. Under `Reduced` the assembled mark is HELD for `IntroHold` and then dismissed —
  reduced motion removes movement, never the moment; under `Instant` it is over on the first
  frame. While the start gate is still unresolved after the ignition ends it holds its end
  state — it is the loading screen, in place of the blank frame. Its ring and Today's hero
  ring share one element key (`hero-ring`) across the gate's transition, so the ring hands
  off into the dashboard.
