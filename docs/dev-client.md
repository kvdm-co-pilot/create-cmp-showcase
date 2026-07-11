# Desktop dev-client — the daily dev loop

Your app ships with a pre-wired **JVM desktop run target**: the shared `commonMain` UI in a live,
clickable window on your workstation, with **Compose Hot Reload** attached. Edit a composable,
save, and watch the window update — no emulator, no reinstall, no restart.

## Run it

```bash
# Hot reload (recommended): auto-reloads on every save
./gradlew :composeApp:hotRunDesktop --auto

# Hot reload, explicit mode: reload only when you run the `reload` task
./gradlew :composeApp:hotRunDesktop

# Plain run (no hot reload — works on any JDK 17+)
./gradlew :composeApp:run
```

The window is phone-sized (411×891 dp) and titled `CMP Showcase dev-client`.

### How reload triggers

- With `--auto` (or `--autoReload`), Gradle's continuous build watches your sources; **saving a
  file** recompiles and hot-swaps the running UI in place.
- Without it, trigger reloads explicitly with the IDE's **Reload UI** button (IntelliJ /
  Android Studio with the Kotlin Multiplatform plugin) or `./gradlew reload`.
- Hot reload runs on the **JetBrains Runtime**. The scaffold wires the
  `foojay-resolver-convention` settings plugin so Gradle auto-downloads a JBR the first time you
  run `hotRunDesktop` if none is installed (one-time download). `:composeApp:run` needs no JBR.

## What's real and what's faked on desktop

The desktop target reuses all of `commonMain` — screens, ViewModels, navigation, theme, DI. The
platform seams get JVM implementations in `composeApp/src/desktopMain`:

| Seam | Desktop implementation |
|---|---|
| `NetworkMonitor` | Always-online stub (`NetworkMonitor.desktop.kt`) |
| Room database | Real Room via `BundledSQLiteDriver`, stored in the OS temp dir (`DatabaseBuilder.desktop.kt`) |
| Firebase | **Not wired at all** — the dev-client never initializes or contacts Firebase |
| `ItemRepository` (example feature) | The same in-memory `ItemRepositoryImpl` every platform binds |

When you add a real remote-backed repository, bind a desktop fake for it in
`desktopMain/.../di/DesktopModule.kt` so the dev-client keeps running without a backend.

## Limits

- Desktop is a **development client**, not a shipping target: platform-specific behavior
  (permissions, push, deep links, insets) still needs the Android/iOS builds.
- Hot reload swaps UI and most code changes in place; structural changes (new `expect`/`actual`,
  build-file edits) need a restart of the run task.
