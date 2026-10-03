// THE CONSOLE'S COMPOSE-SPECIFIC COPY, OWNED BY THE PROFILE THAT KNOWS IT.
//
// PATTERN: contribution points (VS Code `contributes.viewsWelcome`, Backstage
// plugins, Grafana panels): the shell renders neutral section types; the
// provider supplies rows AND the words around them. WHY IT WORKS: the shell
// stops naming Tokens.kt or @Composable, so a Python profile's console does not
// tell its user to edit a Kotlin file. HOW IT FAILS: a provider forgets a key
// and the shell shows a neutral placeholder where the adopter expected help —
// or the shell grows a new string nobody routes through `copy`. WHAT WE DO:
// every key has a neutral default in the shell, the widened agnostic lint now
// scans console/ for language-shaped strings, and this file is the one place
// a Compose word may live on the console path.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/profiles/cmp/console-copy.mjs.
export const copy = Object.freeze({
  usesIn: "commonMain",
  tokensEditHint: "Edit <code>Tokens.kt</code>, let the preview re-render, then stash the result with the",
  componentsEmpty: "no @Composable components found in presentation/components/*.kt",
  versionSetFile: "gradle/libs.versions.toml",
  versionSetUnreadable: "gradle/libs.versions.toml not readable",
  kspPairLabel: "KSP is <code>&lt;kotlin&gt;-&lt;ksp&gt;</code>",
  kspPrefixWarning: "is not prefixed by Kotlin",
  previewRegistryFile: "inspector/PreviewRegistry.kt",
  noRenderableApp: "no Compose app",
  kspName: "KSP",
  kspCarriesLabel: "carries Kotlin",
  kspCarriesNote: " — Room's KMP native compilation breaks on this.",
  depGraphGatesNote: "The Kotlin conformance gates (and the receipt they write, below) are authoritative.",
  // Which of THIS pack's steps governs which console section. Lived in
  // console/console-tabs.mjs as a literal until 2026-09-10, where it named
  // `e2eSmoke` and `tokenDrift` to every adopter — the shell linking steps a
  // backend never runs. The section ids are the shell's; which step earns them
  // is ours, and only we can say.
  stepGoverns: Object.freeze({
    specCoverage: { section: "specs", label: "Specs" },
    conformance: { section: "architecture", label: "Architecture" },
    archDoc: { section: "architecture", label: "Architecture" },
    componentStories: { section: "components", label: "Components" },
    goldenTrees: { section: "screens", label: "Screens" },
    a11y: { section: "screens", label: "Screens" },
    e2eSmoke: { section: "screens", label: "Screens" },
    tokenDrift: { section: "design-system", label: "Design language" },
    approvals: { section: "approvals", label: "Approvals" },
  }),
  walkthroughPrereq: "With the debug app live (adb forward tcp:9500)",
  liveStartHint:
    "Start the whole chain from here — boot a headless AVD if no device is attached, install the debug build, launch it, forward the inspector port, and wait for health:",
});
