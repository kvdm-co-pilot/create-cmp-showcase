# Spec: navigation-ia (the bottom bar and what sits on it)

> The app's information architecture: which surfaces earn a bottom-bar tab, which are reached
> by pushing a destination, and what each tab is anchored on. The shell MECHANICS are
> `specs/app-base.spec.md`'s (SHELL-01..05) and are unchanged by this feature — those clauses
> are written tab-agnostically and stay true at any tab count. What is specified here is the
> tab SET and the two surfaces the restructure created or moved.
> Brief: [`docs/features/navigation-ia.md`](../docs/features/navigation-ia.md). Every clause id
> is cited by the durable test(s) that verify it (`// SPEC: NAV-NN`).

## The tab set

- **NAV-01** — Given the app shell renders, Then the bottom bar carries exactly five tabs, in
  this order: **Today**, **Week**, **Meals**, **Training**, **Profile**. Five is Material 3's
  ceiling for a navigation bar and is the number of daily surfaces this app has; the order is
  the order of the day (what is true now → what is planned → what you eat from → the sixth
  pillar → you), not the order of the data model.

- **NAV-02** — Given the Week tab, Then it hosts the structured day (PLAN-11) with **no date
  argument** — a tab carries none — anchored on the current logical day and re-anchored across
  the day boundary (MEAL-01's `dayStartHour`), so an app left open overnight does not show
  yesterday. The dated route `plan/{date}` remains registered for links that DO carry a day
  (HIST-02's day card).

- **NAV-03** — Given the Today screen, Then it carries **no** control into the week: TODAY-12
  is withdrawn. The week is a bottom-bar tab, always visible and one tap away, so a card
  offering a second route to the same destination — below the fold, at that — is removed
  rather than kept alongside.

- **NAV-04** — Given the Meals tab, Then it is the food catalog (FOODS-*, CAT-*) under a label
  naming what the user came for rather than the row type. Behavior is unchanged from the tab
  formerly labelled "Foods"; the label change is an id change too (`nav_meals`), because a nav
  item's testTag derives from its label.

- **NAV-05** — Given the supplement stack, Then it is **not** a bottom-bar tab: it is a pushed
  destination (`supplements`), entered from Today's highlight (TODAY-11) and from Profile.
  System-back therefore returns to the surface that opened it, where the previous tab-switch
  left no back-stack entry at all.

## The Training tab

- **NAV-06** — Given the Training tab, Then it shows the **current logical week** — Monday to
  Sunday, always all seven days, rest days included — each row carrying its plan label, its
  state (WORK-05's four: done / missed / pending / rest) and its reminder time when it has one;
  with a summary of sessions kept against sessions planned, in which rest days count toward
  neither. The window is derived from the same [TimeSignal] every other surface anchors on.
  Only the current logical day's row is tickable (WORK-04) — a week view that could retro-tick
  Tuesday would be inventing a fact nobody observed — and that tick writes through the same
  repository call Today's card uses, never a second write path.

  Distinct from WORK-05 on purpose: Progress looks BACK over a rolling seven days because it is
  the retrospective surface; this tab shows the week you are IN because it is the plan. Two
  questions that happen to span seven days each; one clock, so they cannot disagree at 04:00.

- **NAV-07** — Given a repository failure behind the Training tab, Then the failure crosses the
  boundary as a typed `AppResult.Failure` folded into the shared content-state machine and is
  rendered as the error arm — never thrown, and never an empty week presented as a real one.
