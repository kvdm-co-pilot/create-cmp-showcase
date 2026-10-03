// WHERE THIS LANE'S BYTES CAME FROM — ADR-0008's provenance, as a file.
//
// THE DECISION THIS IMPLEMENTS. ADR-0008 (accepted 2026-09-08) settled that the
// harness is ALWAYS vendored: pinning is how the bytes arrive, never how they
// are trusted, so there is one kind of receipt. What resolution adds is
// PROVENANCE — `source ∈ {local, registry, git}` — "recorded because the
// resolver knows it, and never counted as a check by anything."
//
// WHAT IT BUYS, precisely, and it is one thing. A checker with a network needs
// to know WHICH artifact to fetch before comparing it to `lock.files`, and
// whether fetching one is meaningful at all. `name` + `version` + `source` are
// those fetch coordinates. Without them a reader cannot tell a receipt naming a
// version that was never published from one naming a version they can pull —
// and the published core HAS sat versions behind this tree, so both exist.
//
// IT IS NOT A CHECK, AND MUST NEVER READ AS ONE. It is exactly as forgeable as
// the `version` beside it. Nothing gates on it, no verdict consults it, and a
// notary that compares the lock against a registry reports that as ITS finding,
// dated and named — never as a property of the receipt (ADR-0008; NORTH-STAR
// §8.2's "notarisation is not examination").
//
// WHY IT IS A FILE IN THE LOCKED REGION rather than a field in the lock. Two
// reasons, and the second is the load-bearing one:
//
//   1. The lock cannot hash itself, so a `source` written there sits outside
//      every digest — the one place an editor's change leaves no trace.
//   2. Inside the region it is inside `lock.files` AND inside `inputs.hash`,
//      so editing it FAILs harnessIntegrity by name. Provenance is offered as
//      no kind of check; that is no reason to make it silently editable.
//
// It is machine-owned, NOT adopter-owned: `harness relock` re-baselines the
// files an adopter authors, and provenance is not one of them. An adopter who
// hand-edits this file gets a refusal naming it, which is the correct outcome —
// the alternative is a lane that will re-lock a forged origin without comment.
//
// ABSENT MEANS UNRECORDED, never `local`. Every receipt minted before this
// existed has no provenance, and inventing one for them would be the precise
// falsehood the field was added to prevent.
import fs from "node:fs";
import path from "node:path";

/**
 * The package whose bytes a lane is made of. Named here because four writers
 * record it — `prooflane init`, `prooflane upgrade`, the stamp and
 * `upgrade --harness` — and a provenance record naming the wrong package is
 * exactly the falsehood this file exists to prevent.
 */
export const HARNESS_PKG_NAME = "prooflane-harness";

/** Project-relative path of the provenance record. */
export const SOURCE_PATH = "qa/harness-source.json";

export const SOURCE_SCHEMA = "prooflane-harness-source/1";

/**
 * How the bytes arrived. The enum is ADR-0008's and gains no fourth value here:
 * an unknown origin is an ABSENT record, not a new word.
 *
 *   registry  a package manager placed them (resolved from node_modules)
 *   local     copied from a path on this machine — a checkout, a workspace
 *   git       cloned from a repository
 */
export const SOURCE_KINDS = Object.freeze(["local", "registry", "git"]);

/**
 * Read the record, or null when there is none or it is unusable.
 *
 * Unparseable reads as ABSENT rather than throwing: a malformed provenance file
 * must not stop a lane from running, because provenance is not a check. The
 * integrity gate is what notices the file changed; this reader's job is only to
 * answer "what does it say", and "nothing usable" is a valid answer.
 *
 * @param {string} root project root
 * @returns {{name: string, version: string, source: string|null}|null}
 */
export function readHarnessSource(root) {
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(path.join(root, ...SOURCE_PATH.split("/")), "utf8"));
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== "object") return null;
  const name = typeof parsed.name === "string" && parsed.name ? parsed.name : null;
  const version = typeof parsed.version === "string" && parsed.version ? parsed.version : null;
  if (!name || !version) return null;
  const source = SOURCE_KINDS.includes(parsed.source) ? parsed.source : null;
  return { name, version, source };
}

/**
 * Write the record. Deterministic by construction — no timestamp, no host, no
 * user. A field that changes on every run would make the region's digest move
 * for no reason and turn `harnessIntegrity` into noise; and the question this
 * answers ("which artifact, from where") has no time in it.
 *
 * @param {string} root project root
 * @param {{name: string, version: string, source?: string|null}} rec
 * @returns {{name: string, version: string, source: string|null}}
 */
export function writeHarnessSource(root, { name, version, source = null }) {
  if (typeof name !== "string" || !name) throw new Error("writeHarnessSource: a package name is required");
  if (typeof version !== "string" || !version) throw new Error("writeHarnessSource: a version is required");
  const kind = SOURCE_KINDS.includes(source) ? source : null;
  const body = { schema: SOURCE_SCHEMA, name, version, source: kind };
  const abs = path.join(root, ...SOURCE_PATH.split("/"));
  fs.mkdirSync(path.dirname(abs), { recursive: true });
  fs.writeFileSync(abs, `${JSON.stringify(body, null, 2)}\n`);
  return { name, version, source: kind };
}

/**
 * What a package manager's own record says about where a dependency came from,
 * for the one project that has the answer: the adopter's lockfile.
 *
 * DERIVED, NEVER ASSUMED. The tempting shortcut is "it is in node_modules, so
 * it came from the registry" — and that is false for the common local-tarball
 * install, which is exactly how this repo's own Stage 1 gate installs the
 * harness. A wrong provenance is worse than none: it tells a checker to fetch
 * an artifact that was never published and to conclude something from failing.
 * So the answer comes from `resolved` in the lockfile, and anything it does not
 * cover reads as null — unrecorded.
 *
 * @param {string} root project root
 * @param {string} pkgName
 * @returns {string|null} one of SOURCE_KINDS, or null when unknowable
 */
export function resolvedSourceKind(root, pkgName) {
  let lock;
  try {
    lock = JSON.parse(fs.readFileSync(path.join(root, "package-lock.json"), "utf8"));
  } catch {
    return null;
  }
  const entry = lock?.packages?.[`node_modules/${pkgName}`] ?? lock?.dependencies?.[pkgName];
  const resolved = typeof entry?.resolved === "string" ? entry.resolved : null;
  if (!resolved) return null;
  if (/^https?:\/\//.test(resolved)) return "registry";
  if (/^file:/.test(resolved)) return "local";
  if (/^git(\+|:)/.test(resolved)) return "git";
  return null;
}
