# Feature brief: reactive-state — observed reads, derived time

## What and why

Two bugs came off the device on 2026-07-28, and they were the same bug. The app left open
overnight still showed yesterday's date — ring, focused container, LATE tag all frozen with
it. And adding a food in the meal tray then pressing back showed a Today screen that did not
know the meal existed. In both cases derived state had been **computed once and held**: the
screen read the clock (or the database) at composition, kept the answer, and nothing ever
told it the answer had moved.

This slice makes "held" the defect, structurally. Reads become **observed streams**: Room's
invalidation tracker re-emits every affected query on every write, and a new
`core/time/TimeSignal` does the same for the clock — a boundary-aligned minute ticker plus a
`wake()` poked on foreground. ViewModels fold those streams with `stateIn` and delete their
`load()` functions entirely; writes stop re-reading because the write's own emission carries
the change back. The contract is `specs/reactive-state.spec.md` (RS-01..RS-04); the
amended surface is TODAY-05 (retry is gone — recovery is the stream's next emission).

## Decisions

1. **Room `Flow` + `stateIn(WhileSubscribed(5000))`, not reload-on-resume.** The DAOs gain
   `*Stream()` queries beside the one-shots; repositories expose `observeTodaySummary` /
   `observePlanDay` / `observeStack`; ViewModels build state with `flatMapLatest` over the
   current-day stream and `stateIn(viewModelScope, WhileSubscribed(5_000), Loading)` — alive
   across rotation and a quick tab switch, fully stopped (ticker included) when the screen is
   gone. Rejected:
   - *Manual per-screen refresh (`load()` in `onResume`/nav callbacks)* — this IS the bug
     class: every screen must remember every path back to itself (back-press, tab switch,
     process restore), and the tray→Today case was precisely a path nobody remembered.
   - *`savedStateHandle` result flags* — couples screens pairwise ("tray tells Today"), and
     says nothing about writers that aren't a navigation result (the plan screen's tick
     landing on Today, the day boundary, a future sync).
   - *A global event bus* — an untyped second channel that still relies on every writer
     remembering to publish; Room's invalidation tracker already IS that bus, typed, at the
     only layer that actually knows a write happened.
2. **A boundary-aligned minute ticker plus `wake()`, not 1-second polling.** `TimeSignal.ticks`
   sleeps to the next wall-clock minute boundary (so "late since 09:30" appears at 09:30, not
   up to 59 s later), emits immediately on collection, and does not run at all without a
   subscriber. `wake()` re-emits "now" from a `LifecycleResumeEffect` in AppNavHost — because
   Doze parks coroutine timers, a ticker alone provably cannot cover the overnight case; the
   foreground poke is the recovery path (RS-03). Rejected: *1 s polling* — ~86,000 wakeups a
   day to notice a boundary that passes once, still parked under Doze, and every derived
   surface recomputes at 1 Hz for nothing.
3. **One shared `TimeSignal` single in DI.** `single<TimeSignal> { RealTimeSignal() }` feeds
   TodayRepositoryImpl, MealPlanRepositoryImpl, and GetPlanDayUseCase. Rejected: *a factory
   per consumer* — each instance would own a private wake channel, so the lifecycle's
   `wake()` would reach only the instance the nav host happens to hold and every other
   consumer would sleep through the foreground; the whole point of `wake()` is that it
   reaches ALL time-derived state at once.
4. **Write failures live beside the read state, never inside it** (`writeFailed` +
   `clearWriteError`, RS-04). Folding a write error into an observed read state does one of
   two bad things: it throws away a rendered day because one tick failed to persist, or the
   stream's next emission silently swallows the error a moment later. A separate transient
   channel does neither. The same reasoning removed `onRetry` from Today: a transient READ
   failure heals on the source's next emission (RS-01/TODAY-05) — a retry button re-running
   a load that no longer exists would be theatre.
5. **Writes keep one-shot clocks.** AddLogEntriesUseCase and MealTrayViewModel still take
   `Clock` (`FixedClock` in tests): a write's target day is decided at the moment of the tap
   and must not drift with a stream while the tray is open. Observed time is for what a
   screen SHOWS; the instant of an action is a fact about the action.

## Blast radius and contracts

- `feature-spec:reactive-state` — NEW (RS-01..RS-04): re-emission on write, day-boundary
  re-derivation, wake, write-failure isolation.
- `feature-spec:today` — reopened; TODAY-05 amended: mapped error copy without a retry
  control, recovery automatic on the source's next emission. All other clauses stand.
- Data: TodayDao/MealPlanDao/SupplementDao gain `*Stream()` queries; repositories gain
  `observe*` streams mapped through `Flow.asAppResult()` (CancellationException always
  rethrown); one-shot reads remain for writes' derivations.
- Presentation: Today/MealPlan/Supplements ViewModels are `stateIn`-built, `load()` deleted;
  `writeFailed`/`clearWriteError` added; TodayRoute/SupplementsScreen error arms render
  without a reload handler.
- DI: one `TimeSignal` single (must STAY a single — decision 3); AppNavHost wires
  `LifecycleResumeEffect { wake() }`.
- Test infra: `FakeTimeSignal` (drivable), `TestScope.keepCollecting` (WhileSubscribed needs
  a collector), revision-backed fakes that bump AFTER mutation (Room invalidates on commit)
  and suspend across the write like the real DAO does.
- Golden trees: unchanged content states; the error arm of Today loses its retry node.

## Open decisions

None — implemented at Karel's instruction 2026-07-28.

```json cmp:feature
{ "touches": ["feature-spec:today"], "screens": false }
```
