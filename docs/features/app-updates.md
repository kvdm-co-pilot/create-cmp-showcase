# Feature brief: app-updates — the build tells you when it is stale

**Spec:** [`specs/app-updates.spec.md`](../../specs/app-updates.spec.md) — UPD-01..UPD-09
(to be written from this brief, once signed).

## The ask

> "automatic updates in app from github"

Fuelled is sideloaded. Every new build today means noticing a release happened, finding the
APK, and installing it by hand — which means the phone in your pocket is running whatever you
last remembered to install. The app knows its own `versionCode`; GitHub knows the latest. The
gap between those two numbers is the whole feature.

## The three constraints that shape it

Raised before any decision, because two of them can invalidate the feature outright.

**Play distribution is off the table for this app.** Google Play's Device and Network Abuse
policy bars an app from downloading executable code or updating itself outside Play's own
mechanism. *Karel's call, asked and answered: sideload / internal only.* That makes an in-app
installer legitimate here — and makes this feature and a future Play listing mutually
exclusive. If Fuelled ever goes to Play, this surface must be gated off in that build, not
softened.

**iOS cannot do this at all.** App Store apps cannot install code, and there is no
sideload-equivalent to fall back to. *Karel's call: hide the surface entirely on iOS.* So the
seam is `expect`/`actual` with a no-op iOS implementation — not a disabled button, not an
explanatory screen. Nothing.

**Signing is load-bearing.** Android refuses an update whose signature differs from the
installed app (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` — hit on the emulator during the e2e work,
2026-08-24). `CLAUDE.md` records that the lane's release APK is **unsigned**; the keystore is
Karel's to create and keep out of the repo. Until one exists and every release is signed with
it, this feature has nothing it can successfully install. That is a release-process
prerequisite, not something code can solve.

## Decisions

**D1 — Notify, download, hand off. Never silent.** The app fetches the APK and fires the
system package-installer intent; Android's own dialog is the confirmation. Silent install
needs device-owner or privileged permissions an ordinary sideloaded app cannot hold, so it is
not a smaller version of this feature — it is a different deployment model.

**D2 — The source of truth is the GitHub Releases API**, `/repos/{owner}/{repo}/releases/latest`,
read with the Ktor client already in the catalog (3.1.0, with content-negotiation and
kotlinx-json). Unauthenticated: public repos need no token, and shipping one in an APK would
publish it. Unauthenticated calls are rate-limited per IP (60/hour), which D8's cadence keeps
us far below.

**D3 — Compare `versionCode`, never `versionName`.** Semver strings sort wrong under string
comparison — `"0.10.0" < "0.9.0"` — and a release-name typo would otherwise decide whether the
app updates. The integer is monotonic and is what Android itself compares. The release's tag
carries the human-readable version for display only.

**D4 — One `expect fun` seam, Android-only actual.** Mirrors `core/connectivity/NetworkMonitor`:
common code owns the check-and-compare, the platform owns install capability. iOS's actual
reports "updates unsupported" and the presentation layer renders nothing at all (the
constraint above, made structural rather than remembered).

**D5 — The APK is verified before the installer sees it.** The release asset's size and digest
are checked against what GitHub reported; a mismatch is a failure, not a prompt. Signature
verification is the OS's job and it is not optional there — Android will reject a
differently-signed APK regardless of what we do — but handing the installer a truncated
download is a failure mode we own.

**D6 — Its own screen, not a card bolted into Settings.** `presentation/app-updates/` with an
`UpdateScreen`, entered from Settings. Two reasons, one product and one governance:

- The surface has real content — current version, available version, release notes, download
  progress, failure states. That is a screen's worth of state, and Settings is already long.
- `feature-design:<name>` resolves to `presentation/<name>/*Screen.kt`. A feature declaring
  `screens: true` whose UI lives only inside another feature's screen can never resolve its
  design gate, so `--accept` is refused forever (learned the hard way this session: naming a
  brief `navigation` bound its design gate to `presentation/navigation/Screen.kt`, a route
  registry with no rendered output).

**D7 — Errors are typed and the surface degrades to silence.** No network, rate-limited, no
release published, malformed asset — all fold into the sealed UI state as an ordinary error
arm. An update checker that interrupts a meal log to complain about GitHub is worse than one
that says nothing.

**D8 — Check on a cadence, not on every launch.** At most once per logical day, and only when
the surface is opened or the app comes to the foreground — never a background job, never a
scheduled alarm. This app already spends its alarm budget on meals, supplements and training
(NOTIF-*, SUPP-12, WORK-06); a version check is not worth a wake-up.

**D9 — The check is observable state, not a held answer.** Like everything else keyed on the
logical day, it re-derives rather than caching a verdict at launch (`TimeSignal`'s discipline
— the overnight bug that produced it is recorded in `core/time/TimeSignal.kt`).

## Blast radius

Declared, so the console shows "as planned" rather than unexplained drift.

| Artifact | What happens |
|---|---|
| `specs/app-updates.spec.md` | NEW — UPD-01..09 |
| `feature-design:app-updates` | NEW — `presentation/app-updates/UpdateScreen.kt`, signed on rendered output |
| `feature-spec:settings` | Settings gains the entry point → reopen + amend |
| `feature-design:settings` | The settings screen grows a row → re-approve on rendered output |
| `AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES`, plus a `FileProvider` to hand the APK URI to the installer |
| Golden trees | NEW `app-updates`; `settings` drifts by one row |
| `PreviewRegistry.kt` | The screen registers, with forced-state variants (up-to-date / update-available / downloading / failed) |
| `qa/e2e/` | A per-feature flow — though note the lane runs only `smoke.yaml` (harness limitation, recorded 2026-08-24) |

**Not touched:** `architecture` (no new layer, no ARCH clause moves), `components` (the screen
composes the existing registry vocabulary), `design-system`.

## Edge cases

Audited before the signature, not after.

**E1 — The installed build is NEWER than the latest release.** A local debug build carries a
`versionCode` no release has. It reports up-to-date, never "downgrade available" — offering to
replace a newer build with an older one is a data-loss suggestion, and Android would refuse
the install anyway.

**E2 — A release exists with no APK asset.** Common: a tag pushed for a source-only release.
Treated as "no update available", not as an error — nothing is wrong, there is simply nothing
to install.

**E3 — Rate-limited (HTTP 403 with the rate-limit header).** Indistinguishable from failure to
a naive client. Surfaced as its own quiet state and the cadence backs off; retrying harder is
exactly what earned the limit.

**E4 — "Install unknown apps" not granted.** The install intent silently does nothing on some
OEM builds. The permission state is checked BEFORE the download starts — spending a user's
data to fetch an APK we then cannot hand over is the rude version of this feature.

**E5 — The download dies mid-flight.** Partial file, no retry loop. D5's digest check fails
closed, the partial is discarded, and the surface offers the action again.

**E6 — The user installs, and the app is killed.** The installer replaces the running process.
Nothing to preserve: the check is derived state (D9), and there is no in-flight write to lose.

**E7 — Signature mismatch.** Android refuses with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. We
cannot detect this before handing off, so the failure surfaces from the OS. The copy names the
real cause — a differently-signed build — rather than "install failed", because the fix is in
the release process, not on the phone.

**E8 — Airplane mode / no connectivity.** `NetworkMonitor` already exists and already reports
this; the surface uses it rather than discovering the outage through a timeout.

`unrouted` is TEMPORARY and belongs to the Design stage only: the screen is drafted and
rendered before it is wired, so the reachability gate would otherwise read it as a screen
nobody can navigate to. Build removes the flag in the same change that registers the
destination and adds the Settings entry point.

```json cmp:feature
{ "touches": ["feature-spec:settings", "feature-design:settings"], "screens": true, "unrouted": true }
```

## Open decisions

**OD1 — Which repository publishes Fuelled's releases?** This app's git remote is
`kvdm-co-pilot/create-cmp-showcase`, which is the *showcase for the harness*, not obviously
where a Fuelled APK would be released. The owner/repo pair is a compile-time constant and the
feature is inert if it points at the wrong one. **Needs Karel.**

**OD2 — The cadence, concretely.** D8 says "at most once per logical day, on open or
foreground". The alternative is manual-only — a "Check for updates" button that does nothing
until pressed, which is quieter still and removes any question of unrequested network use.
*Recommendation: once per logical day, since the whole point is not having to remember.*

**OD3 — Does the release-notes body render as markdown?** GitHub returns the body as markdown
text. Rendering it needs a markdown composable this app does not have (a new common component
→ `components` re-approval); showing it as plain text is honest but ugly for anything with
lists. *Recommendation: plain text for the first slice, note it as a known limitation.*

## The walk, once this is signed

1. `node qa/approve.mjs feature-brief:app-updates` — this document
2. **Design** — draft `UpdateScreen` on STUB data, register its four states in
   `PreviewRegistry`, render, and STOP. `feature-design:app-updates` is signed on the rendered
   screens, never on this description.
3. **Contract** — `specs/app-updates.spec.md` (UPD-01..09) + the `feature-spec:settings`
   amendment for the entry point.
4. **Build** — the slice, plus the citing tests.
5. **Prove** — one full `node qa/verify.mjs`. Note the device tier matters unusually much
   here: the install hand-off is a platform fact no desktop tier can see, so this feature's
   behavior test belongs in `androidInstrumentedTest`.
6. **Sign-off** — `node qa/approve.mjs --accept app-updates`.
