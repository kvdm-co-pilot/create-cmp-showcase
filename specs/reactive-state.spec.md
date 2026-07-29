# Spec: reactive-state (observed reads, derived time)

> Cross-feature contract for how screen state stays TRUE: reads are observed streams, the
> logical day is a derivation that moves with the clock, and writes never re-read. Born from
> two on-device bugs of one class — derived state computed once and held (the dashboard still
> speaking for yesterday after a night open; a tray add invisible on Today after back) — this
> spec makes "held" the defect. The mechanism is `core/time/TimeSignal` + Room's invalidation
> streams; the decisions are the brief, `docs/features/reactive-state.md`. Every clause id is
> cited by the durable test(s) that verify it (`// SPEC: RS-NN`).

- **RS-01** — Given a screen state built from an observed read, When any row in the tables
  behind it changes — written by this screen, another screen, or the tray — Then the state
  re-emits the re-derived answer to the already-listening collector without any reload call;
  and a transient read failure heals the same way: the source's next emission replaces the
  error on its own (TODAY-05), with no retry control anywhere in the flow.
- **RS-02** — Given state derived from the current instant (the logical day in view, focus,
  late, missed), When the day boundary (`dayStartHour`, MEAL-01) passes, Then the logical day
  re-derives and the state follows — the day in view is never held from screen-open, and a
  tick that does not cross the boundary re-derives the SAME day rather than a new one
  (`days()` is de-duplicated on the derived date, not the instant).
- **RS-03** — Given the process returns to the foreground (or the platform announces a
  date/timezone change), When `TimeSignal.wake()` fires, Then "now" re-emits immediately and
  every time-derived state re-derives from it — coroutine timers are parked under Doze while
  the device sleeps, which is why the minute ticker ALONE is not the contract and a wake
  after real time passed must be.
- **RS-04** — Given a WRITE fails, Then the failure surfaces on its own channel
  (`writeFailed`), never into the read state: the rendered day stays standing, the read
  stream never carries the write's error, and clearing the flag is explicit
  (`clearWriteError`) — an error folded into an observed read state would either destroy the
  rendered answer or be silently swallowed by the next emission.
