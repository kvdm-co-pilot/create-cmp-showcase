# Spec: app-updates (the build tells you when it is stale)

> Fuelled is sideloaded, so nothing tells it a newer build exists. This feature closes the gap
> between the `versionCode` on the phone and the latest GitHub release: it checks, it says so,
> it downloads, and it hands the APK to Android's own installer. It never installs silently and
> it does not exist on iOS.
> Brief: [`docs/features/app-updates.md`](../docs/features/app-updates.md). Every clause id is
> cited by the durable test(s) that verify it (`// SPEC: UPD-NN`).

## Knowing

- **UPD-01** — Given the update check runs, Then the newest release is read from the GitHub
  Releases API for the repository this app is published from, unauthenticated. No token ships
  in the APK — an APK is a public artifact and a secret inside one is a published secret.

- **UPD-02** — Given a release is found, Then "is it newer" is decided by comparing
  **`versionCode` integers**, never release names or tags. Version STRINGS sort wrong
  (`"0.10.0" < "0.9.0"` under string comparison), so a release-name typo could otherwise
  decide whether the app updates itself. The tag is carried for display only.

  The releases API does not report `versionCode` — it is an Android concept GitHub knows
  nothing about — so it travels in the **asset filename**, `fuelled-<versionCode>.apk`. That
  makes the release process responsible for a convention, which is the honest cost of the
  rule: the alternative is parsing a semver tag into an ordering, which is precisely the
  string comparison this clause exists to forbid. An asset whose name does not carry a
  parseable integer is not an installable asset (UPD-04).

- **UPD-03** — Given the installed build's `versionCode` is greater than or equal to the
  latest release's, Then the surface reports "up to date" and offers no action — including
  when the installed build is NEWER (a local debug build). A downgrade is never offered:
  Android would refuse the install, and replacing a newer build with an older one is a
  data-loss suggestion whatever the OS does.

- **UPD-04** — Given the latest release carries no installable asset, Then the result is "up
  to date", not an error. A source-only tag is a normal thing for a repository to contain;
  nothing is wrong, there is simply nothing to install.

## Failing

- **UPD-05** — Given the check cannot complete — no connectivity, a transport failure, a
  malformed response, or the API's unauthenticated rate limit — Then the failure crosses the
  boundary as a typed `AppResult.Failure` carrying a `DomainError` kind, is folded into the
  screen's sealed state, and is rendered as copy that names what did not happen. It is never
  thrown, never a crash, and never a silent "up to date" — reporting currency the app could
  not verify is the one wrong answer here.

## Installing

- **UPD-06** — Given an update is available and the user asks for it, Then the app downloads
  the asset and hands it to the platform installer, where the user confirms. The app never
  installs silently: that needs privileges an ordinary sideloaded app cannot hold, so it is
  not a smaller version of this behavior but a different deployment model.

- **UPD-07** — Given the download completes, Then its size is checked against what the release
  metadata declared, and a mismatch fails closed — the partial file is discarded and nothing is
  handed to the installer. Signature verification belongs to the OS and is not ours to skip or
  reproduce; handing over a truncated download is the failure we own.

## Platform

- **UPD-08** — Given the app runs on a platform that cannot install applications — iOS, where
  it is impossible rather than merely unavailable — Then the update surface does not exist:
  no entry point, no screen, no check. Not a disabled control and not an explanatory message,
  because there is nothing a user could do about it.

## Surface

- **UPD-09** — Given the update surface renders, Then it always shows the installed version
  (`updates_installed`) alongside exactly one of four states — up to date (`updates_current`),
  available (`updates_available`, with the version, publication date, size and release notes,
  and a control that names the version it will fetch), downloading (`updates_downloading`), or
  failed (`updates_failed`, naming that nothing was installed before offering the retry). A
  download whose total size the server never declared shows no progress bar rather than a bar
  at zero: progress that invents its own number is worse than none.
