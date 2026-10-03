// profile.mjs — what this project's profile declares, and what each declaration
// is supposed to mean.
//
//   node qa/profile.mjs                       every field, with what this project declares
//   node qa/profile.mjs explain ladder.l2Execution
//
// WHY IT EXISTS. `kubectl explain`'s trick: serve the help from the SAME bytes
// the validator reads, so the two cannot drift. qa/lib/profile-contract.mjs is
// that object — meaning, question, options, default, refusal — and this is the
// surface an author reads at the moment they are declaring, rather than a
// document they were supposed to have read at session start.
//
// It also makes a refusal true. qa/lib/evidence-ladder.mjs tells an author whose
// profile still uses the pre-rename spellings to run `node qa/profile.mjs
// explain ladder.l2Execution`. That command did not exist when the refusal was
// written — a review found it — and a refusal naming a command nobody can run
// is the same defect as an escape hatch whose condition can never fire.
//
// NO WORKED EXAMPLE IS COMPILED IN. What it prints beside each field is THIS
// project's own declaration, read from the profile the manifest names. An
// example in the core would be a copy of some profile's declaration, and a copy
// drifts from the thing it illustrates.
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/profile.mjs in the create-cmp
// repo. Vendored byte-identical into qa/ — edit the package source, then run
// `node scripts/sync-harness.mjs` in the create-cmp repo.

import path from "node:path";
import { fileURLToPath } from "node:url";

import { evidenceLadderFor } from "./lib/evidence-ladder.mjs";
import { resolveHarnessManifest } from "./lib/harness-manifest.mjs";
import { CONTRACT, CONTRACT_PATHS, contractAt, explain } from "./lib/profile-contract.mjs";
import { loadProfileSync } from "./lib/profile-loader.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/**
 * What this project declares for one contract path, or `undefined` when it
 * declares nothing — which is different from declaring an empty list, and the
 * difference is printed.
 *
 * Every failure is silent-by-design HERE and loud elsewhere: a tree with no
 * manifest, an unloadable profile or a refused ladder still gets the contract's
 * words, because an author reading `explain` to find out what to declare is
 * precisely the author whose profile does not load yet. The lane refuses them
 * elsewhere, at length.
 *
 * @param {string} root
 * @returns {(path: string) => unknown}
 */
export function declaredReader(root) {
  let ladder = null;
  try {
    const manifest = resolveHarnessManifest(root);
    if (manifest.ok) {
      const loaded = loadProfileSync(root, manifest.manifest.profile);
      if (loaded.ok) {
        const resolved = evidenceLadderFor(loaded.profile);
        if (resolved.ok) ladder = resolved.ladder;
      }
    }
  } catch {
    ladder = null;
  }
  return (contractPath) => {
    const [decl, field] = String(contractPath).split(".");
    if (decl !== "ladder" || !ladder) return undefined;
    return field ? ladder[field] : ladder;
  };
}

/** One field's block, for the listing. */
function summarise(contractPath, declared) {
  const entry = contractAt(contractPath);
  const shown = declared === undefined ? "(not declared)" : JSON.stringify(declared);
  return `  ${contractPath.padEnd(24)} ${shown}${entry.required ? "   [required]" : ""}`;
}

/**
 * @param {string[]} argv
 * @param {string} root
 * @returns {{out: string, code: number}}
 */
export function run(argv, root = ROOT) {
  const declaredFor = declaredReader(root);
  const [verb, target] = argv;

  if (verb === "explain") {
    if (!target) {
      return { code: 2, out: `explain needs a field: one of\n${CONTRACT_PATHS.map((p) => `  ${p}`).join("\n")}` };
    }
    const text = explain(target, { declared: declaredFor(target) });
    if (!text) {
      return {
        code: 2,
        out: `nothing in the contract is called "${target}". The fields are:\n${CONTRACT_PATHS.map((p) => `  ${p}`).join("\n")}`,
      };
    }
    return { code: 0, out: text };
  }

  if (verb && verb !== "list") {
    return { code: 2, out: `unknown command "${verb}" — try \`node qa/profile.mjs\` or \`node qa/profile.mjs explain <field>\`` };
  }

  const lines = [];
  for (const [decl, spec] of Object.entries(CONTRACT)) {
    lines.push(`${decl} — ${spec.meaning}`, "");
    for (const field of Object.keys(spec.fields ?? {})) lines.push(summarise(`${decl}.${field}`, declaredFor(`${decl}.${field}`)));
    lines.push("");
  }
  lines.push("`node qa/profile.mjs explain <field>` says what one of them means, and what it is asked as.");
  return { code: 0, out: lines.join("\n") };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { out, code } = run(process.argv.slice(2));
  (code === 0 ? console.log : console.error)(out);
  process.exit(code);
}
