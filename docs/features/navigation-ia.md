# Feature brief: navigation-ia — the bottom bar, re-cut around the five things you do

**Spec:** [`specs/navigation-ia.spec.md`](../../specs/navigation-ia.spec.md) — NAV-01..NAV-07 (to be
written from this brief, once signed). Amends TODAY-12 (withdrawn) and WORK-05/WORK-07.

## The ask

> "Should we move the UI around. I feel like the today and week screens should be moved to the
> bottom bar I'm not sure it's so easy to navigate at the moment. What are industry patterns
> here. Rethink how it is currently displayed maybe a meals tab?? […] we can replace foods with
> a training flow."

## The problem

The bar carries **Today · Foods · Supplements · Profile**. Three of those four are wrong for a
bottom bar, and the diagnosis is the same each time: *the bar is carrying nouns from the data
model instead of the things a person does in a day.*

- **The week is not on it at all.** Planning is the app's most deliberate act — six containers a
  day, seven days — and it lives behind a card near the bottom of Today. It is below the fold:
  the e2e smoke flow has to `swipe UP` **twice** before `today_plan_link` is even tappable
  ([`qa/e2e/smoke.yaml`](../../qa/e2e/smoke.yaml)). A surface you scroll to reach is a surface
  you reach less.
- **Foods holds prime real estate for a lookup table.** You open the catalog when you are adding
  a food — which you arrive at *from* a meal container, already targeted (MEAL-10). It is a
  destination of a flow, not a place you go to start one.
- **Supplements is a whole tab for a list you tick.** Its real entry point is already the Today
  highlight (TODAY-11): the card tells you 2 of 4 are taken, which is the entire question.
- **Training has no home at all.** 0.6.0 added the sixth pillar as three fragments — a card on
  Today (WORK-03), a strip inside Progress (WORK-05), an editor card in Settings (WORK-07).
  You can tick today's session and you can see last week's dots, but there is nowhere that
  answers "what does my training week look like".

The industry pattern for a daily-logging app is stable and worth naming plainly, because it is
what the tabs below are: **dashboard · log/plan · library · discipline · you**. MyFitnessPal,
MacroFactor, Cronometer and Strong all sort their bars that way. The library tab is never in
position two.

## Decisions

**D1 — Five tabs: Today · Week · Meals · Training · You.** In the order of the day, not the
order of the data model. Five is Material 3's ceiling for `NavigationBar` and this app has
exactly five daily surfaces — a sixth would force the overflow pattern and there is nothing to
put in it.

**D2 — Week is promoted from a link to a tab, and TODAY-12 is withdrawn.** The clause exists
*because* the week had no tab: it reads "it offers one control into the full week … planning is
one tap from the dashboard". Once Week sits on the bar, that card is a second route to a place
already one tap away, and the weaker of the two — the bar is always visible, the card is below
the fold. Withdrawn, not amended: there is no surviving claim once the tab exists.

**D3 — Foods becomes Meals.** The tab's job is unchanged (the catalog, custom foods, the meal
builder); the *label* stops naming the row type and starts naming what you came for. "Foods" is
what the table is called; "Meals" is what a person is looking for when they open it.
*Naming carries a real risk — see [OD1](#open-decisions).*

**D4 — Supplements comes off the bar and becomes a pushed destination.** Its Today highlight
(TODAY-11) stays exactly as it is and becomes its primary entry point; Profile keeps a link for
the deliberate visit. Nothing about the supplements *feature* changes — only how you arrive.

**D5 — Training becomes a tab. This reverses D1 of [`feature-brief:workouts`](./workouts.md),
deliberately and with the reason stated.** That brief decided "No new tab, no new screen", and
its reasoning was specific: *"a surface you must navigate to in order to tick one box is a
surface that goes unticked."* That reasoning was right and it still holds — **so the tick is not
moving.** WORK-03's card stays on Today, with its own control, exactly where it is. What the tab
adds is the job that reasoning never covered: *seeing and shaping the week*. That job currently
has no home, and the two fragments it does have (a read-only strip inside Progress, an editor
card inside Settings) are in two different places, neither of which is about training. D1
answered "where does the tick live"; this answers "where does the week live", and they have
different answers.

**D6 — The Training tab is the workouts feature's screen, not a new feature.** It is governed by
the existing `feature-brief:workouts` + `feature-spec:workouts` pair, reopened under this change.
Concretely: `workouts.md` gains `"screens": true`, which creates `feature-design:workouts`
resolving to `presentation/workouts/*Screen.kt` — so the screen lands there, and the existing
`presentation/workout/WorkoutCard.kt` (singular, Today's card) moves with it into the plural
package so the feature is one directory. A second brief called "training" for the feature
already called "workouts" would put one feature under two names in the ledger.

**D7 — Today's content does not otherwise change.** Today was built as the highlights surface
(decision 13 of `feature-brief:meal-plan`) — ring, macros, protein focus, the one focused
container, next water, veg count, the workout card, the supplement bucket. Every one of those is
navigation-independent, so none of them move. The screen loses exactly one card and gains
nothing. **This is deliberate**: an IA change that also redesigns the hero screen cannot be
judged, because a regression in either half looks like the other half's fault.

**D8 — The bottom bar component itself is unchanged.** `AppBottomBar` already takes
`tabs: List<AppTab>` and derives each item's `nav_<slug>` testTag from the label
([`AppBottomBar.kt:98`](../../composeApp/src/commonMain/kotlin/com/kvdm/fuelled/presentation/components/AppBottomBar.kt)).
Five entries instead of four is data, not a component change — which is why the `components`
artifact is not in this brief's `touches`.

**D9 — Progress keeps `week_training` (WORK-05).** Not duplication: Progress is the retrospective
surface — sessions kept of sessions planned, over the same window the verdict covers (HIST-01) —
while the Training tab is the plan and today's state. The same split Today and the plan screen
already live with, and they share composables rather than diverging (TODAY-13's discipline). The
strip stays where the history is.

**D10 — The workout-week editor moves from Settings into the Training tab; Settings'
`settings_week` card becomes a link into it.** Editing the week belongs with the week. Settings
stays the place you *find* it — WORK-07's discipline that every save re-arms is unchanged, it
just fires from a different screen. *Karel's call, and the one most reasonable to overrule: the
smaller change is leaving the editor in Settings and making Training read-and-tick only.*

## Blast radius and contracts

Declared, so the console shows "as planned" rather than unexplained drift.

| Artifact | What happens |
|---|---|
| `feature-spec:today` | TODAY-12 withdrawn. TODAY-11's card keeps its clause; only its destination changes (tab switch → pushed route) |
| `feature-brief:workouts` | Reopened — D1 reversed (D5 above), `"screens": true` declared |
| `feature-spec:workouts` | WORK-07 amended (editor's home), new clause for the tab. WORK-03/WORK-05 unchanged |
| `feature-spec:supplements` | Unchanged as behavior; the screen becomes a NavHost destination, so it must wrap itself in `BaseScreen` — SHELL-05, which tabs are exempt from |
| `specs/navigation-ia.spec.md` | New — NAV-01..NAV-07, the tab set and its claims |
| `qa/e2e/smoke.yaml` | **Three breaks, all mechanical**: `nav_foods` → `nav_meals` (the tag derives from the label); `nav_supplements` no longer exists, so SUPP-03's on-device proof reroutes via Today's highlight; `today_plan_link` no longer exists, so the route into the plan becomes a `nav_week` tap — which deletes the two `swipe UP` steps and their explanation |
| Golden trees | Today (one card fewer), plus a new tree for the Training screen |
| `PreviewRegistry.kt` | The Training screen registers, with a forced-state variant for a rest day |

**`architecture` is NOT reopened.** SHELL-01 and SHELL-02 are written tab-agnostically ("the
first tab's screen", "another tab in the bottom nav") — they stay true word for word with five
tabs. Worth stating because the instinct is that a nav change must touch the shell contract, and
here it genuinely does not.

**One simplification falls out.** `AppShell`'s `selectedIndex`/`onSelectTab` hoisting exists for
exactly one caller — its own doc comment says so: *"Today's supplement highlight opens the
Supplements tab (TODAY-11)"*. With Supplements off the bar that link becomes an ordinary push,
and the hoisting has no remaining caller. Remove it with the change rather than leaving a
parameter pair that nothing passes.

## Pre-existing state this change does not own

Recorded so it is not mistaken for this change's damage, and because this change **cannot reach
`provenDone` until it clears**. A full `node qa/verify.mjs` on the tree as it stands (2026-08-22)
returns **FAIL (desktop-only)** — `build`, `releaseBuild`, `goldenTrees` and `a11y` all PASS;
three steps do not. **All three trace to one commit**, `dd36639 chore(harness): upgrade to
create-cmp 0.13.0 via `upgrade --harness``:

- **`conformance` FAIL — ARCH-13**, added to `specs/app-base.spec.md` *by that commit*: ambient
  time (`Clock.System`, `*.now()`, `TimeZone.currentSystemDefault()`) may only be read inside
  `core/time`. **15 files violate it** across data, domain and presentation — five
  `*RepositoryImpl`, four use cases, five ViewModels, and `SettingsScreen.kt`.
- **`conformance` FAIL — SHELL-05**: every NavHost destination must wrap itself in `BaseScreen`.
  **7 destinations do not** — the meal tray, both meal-plan routes, Progress, the meal builder,
  Settings, and the food editor.
- **`approvals` FAIL** — `architecture` and `components` are both `changed-since-approval`,
  because the same commit edited `specs/app-base.spec.md`, `docs/ARCHITECTURE.md` and three
  files under `presentation/components/`.

**The app did not regress — the bar moved.** ARCH-13 did not exist when this code was written;
the upgrade added the rule and the existing tree fails it. That is a legitimate thing for a
harness upgrade to do, but it is worth noting that `CLAUDE.md` describes `upgrade --harness` as
"safe to do unattended — it touches no app content and no signed artifact", and this run touched
both: two signed artifacts invalidated, and `composeApp/src/**` edited (DI, three components, a
new `BrandMark.kt`, the conformance test itself). **That gap is upstream in create-cmp, not
here**, and it is the more valuable finding of the two.

Three consequences for this brief:

1. **E4's safety net is currently down.** It argues the conformance gate will catch a Supplements
   screen moved to a NavHost destination without `BaseScreen`. True — but SHELL-05 is *already*
   failing for 7 destinations, so a new violation would land in an existing red list rather than
   turning the gate red. Fix SHELL-05 first, or E4's proof is indistinguishable from the noise.
2. **ARCH-13 is a feature-sized refactor**, not a cleanup to fold into an IA change: injecting a
   clock through 15 files across three layers. It needs its own brief.
3. **Order matters.** Doing this IA work first means building on a red lane and being unable to
   tell which failures are new. *Recommendation: clear the conformance debt first, re-approve
   `architecture` and `components`, then start here from green.*

## Not built, on purpose

A "+" / FAB in the centre of the bar. Every logging app has one and it is the obvious fifth
element — but this app's adds are *targeted*: an add control belongs to a container and carries
that container's slot (TODAY-07, MEAL-10, and TODAY-08 was withdrawn precisely because an
untargeted add had to guess its slot from the clock). A centre FAB is an untargeted add by
construction. It would re-introduce the guess the meal-plan brief spent a decision removing.

Also not built: reordering tabs by time of day, badge counts on tabs, and a tablet/landscape
rail. The first two are personalization with no evidence behind them yet; the third is a real
piece of work that starts with `WindowSizeClass` and deserves its own brief.

## Edge cases

Audited before the signature, not after. Each names how it resolves.

**E1 — Which day does the Week tab open on?** The plan screen is a route with a date argument
(`plan/{date}`, PLAN-11); a tab has no argument to carry. The tab opens on the **current logical
day**, re-anchored on every selection — not on the day it last showed. It also re-anchors across
the day boundary (04:00, configurable), so an app left open overnight does not show yesterday
under a "Week" tab the next morning.

**E2 — Re-tapping the already-selected tab.** Today a no-op (`internal = it`). With Week holding
a scroll position and a day, re-tapping **Week** returns to the current logical day — the
standard "tap the active tab to go home" gesture, and the recovery when E1's anchor has been
scrolled away from. The other four tabs stay no-ops; nothing on them is lost by scrolling.

**E3 — Back from Supplements.** It stops being a tab (D4), so arriving from Today's highlight now
pushes a destination and back returns to Today. Previously the highlight *switched tabs*, which
left no back-stack entry and stranded the user on a tab they did not choose — the switch is a
behavioral improvement, but it IS a change: system-back now has somewhere to go where it
previously exited or fell through to Today's tab index.

**E4 — Supplements needs its own insets.** SHELL-05: a tab inherits `BaseScreen` from `AppShell`;
a NavHost destination does not. Moving the screen without wrapping it is a silent inset bug that
only shows on a device with gesture navigation — the exact class the conformance gate catches, so
it will fail loudly rather than ship.

**E5 — A restored tab index means a different tab after upgrade.** `AppShell` keeps selection in
`rememberSaveable` as an **Int**. A user whose saved index is 3 left on Profile and returns to
Training; index 4 did not previously exist. Harmless once, confusing once — and the honest fix is
that an index is not a stable identity. Either reset the saved selection on the version that
lands this, or key it by tab label. *Recommendation: reset — one launch on Today is cheaper than
carrying a migration for a nav index.*

**E6 — Five tabs at large font scale.** Four labels fit comfortably; five at 1.3–2.0x scale is
where truncation starts, and "Supplements" was already the longest label the bar has carried.
This is testable in the instrumented tier (`ConfigControl` sets font scale), so it gets a
behavior test rather than a judgment call — and it is the strongest argument for the shortened
"Train"/"You" labels in OD2.

**E7 — A rest day on the Training tab.** `feature-brief:workouts` D5 decided a rest day renders
*nothing* — but that was about **Today**, where a rest day has nothing to say. On the week view
every day renders, rest included, because the shape of the week is the point and a gap would read
as missing data. WORK-05's four states (done / missed / pending / rest) already carry rest as a
first-class value, so this needs no new vocabulary.

**E8 — The meal tray's return path.** The tray pops back onto the container that opened it
(MEAL-13). Containers now live under a *tab* rather than a pushed plan screen, so the pop lands
on a different back-stack shape. Unchanged as a claim, and the e2e smoke already proves it —
but the flow's route into the plan changes with it (see Blast radius), so the proof must be
re-established rather than assumed.

```json cmp:feature
{ "touches": ["feature-spec:today", "feature-brief:workouts", "feature-spec:workouts"], "screens": false }
```

`"screens": false` is deliberate and not an oversight: this brief ships no screen of its own —
the one new screen it calls for is governed by `feature-design:workouts`, per D6.

## Open decisions — all three closed at implementation

**OD1 — "Week" and "Meals" side by side.** *Closed: Week + Meals, as recommended.* The icons
carry the distinction (calendar vs. cutlery) and the labels name what the user came for. Worth
re-judging on the rendered screens rather than on this page — which is still owed, since the
design gate has not been rendered (below).

**OD2 — Labels are testTags.** *Closed: full words.* `Today · Week · Meals · Training ·
Profile`, giving `nav_today` / `nav_week` / `nav_meals` / `nav_training` / `nav_profile`. The
shortened "Train"/"You" were rejected: both fit at the tested font scales, and `nav_training`
outlives `nav_train` as an automation id. E6 (five labels at 1.3–2.0x font scale) is still owed
a behavior test in the instrumented tier.

**OD3 — Where the workout-week editor lives.** *Closed: it STAYS in Settings — D10 overruled,
taking the smaller change.* The Training tab is read-and-tick; a row that is not the current
logical day opens the Settings editor. Recorded as an amendment to WORK-07 rather than left
implicit, because "the editor is not on the tab that shows the week" is the kind of decision a
future contributor would otherwise read as an oversight and helpfully "fix".

## The walk, once this is signed

1. `node qa/approve.mjs feature-brief:navigation-ia` — this document
2. `node qa/approve.mjs --reopen-feature workouts --reason "IA: training gets a tab (navigation D5), editor moves off Settings (D10)"`
3. Design: draft the Training screen on stub data, register it in `PreviewRegistry`, render,
   **stop** — `feature-design:workouts` is signed on rendered output, never on this description
4. Contract: `specs/navigation-ia.spec.md` (NAV-01..07) + the TODAY-12 withdrawal + the WORK
   amendments; signed before the build
5. Build, then one full `node qa/verify.mjs` at done
