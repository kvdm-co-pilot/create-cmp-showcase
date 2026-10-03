// lane-markers.mjs — where the lane says "I am running", and where the eyes say
// "I am building". One place, so no reader ever looks in the wrong directory.
//
// THE LANE MARKER IS CORE STATE (Stage 0 PR 6a). It used to live at
// composeApp/build/.cmp-lane-in-progress — inside a Compose app's Gradle
// build directory — and every reader (the Stop hook, the narrator, the
// watcher, the chain view, the console) carried that path as a constant. A
// backend has no composeApp/build; a lane there stamped a marker nobody read.
// The marker is the lane's, not the stack's, so it lives beside the lane's
// other transient state (qa/.agent-hold.json, qa/.plan.json): gitignored,
// excluded from the inputs hash, never a lane OUTPUT for the fast filter,
// outside the lock region (the region is .mjs files and the two declarations).
//
// THE RENDER MARKER IS THE PROVIDER'S. The preview daemon (mobile's eyes)
// stamps <buildDir>/.cmp-render-in-progress around its Gradle builds so the
// lane can defer and self-heal around it. Which directory that is comes from
// the profile's `layout.buildDir`; a profile with none has no render marker,
// and every reader takes `null` as "nothing to wait for".
//
// SINGLE SOURCE OF TRUTH: packages/harness/src/lib/lane-markers.mjs in the
// create-cmp repo. Vendored byte-identical into qa/lib/ — edit the package
// source, then run `node scripts/sync-harness.mjs` in the create-cmp repo.

import fs from "node:fs";
import path from "node:path";

import { resolveHarnessManifest } from "./harness-manifest.mjs";
import { loadProfileSync } from "./profile-loader.mjs";

/** The lane's in-flight marker, project-relative. Stamped by qa/verify.mjs for a run's duration. */
export const LANE_MARKER_REL = "qa/.lane-in-progress";
/**
 * The lane's STEP STREAM, project-relative — one NDJSON line per finished step,
 * appended while the run is still going (`verify --events`,
 * docs/proposals/LIVE-CONSOLE.md Phase B). The console tails it to append a row
 * as each step lands, and reads it on a page load so a run that happened while
 * nobody was watching still renders.
 *
 * IT IS PROGRESS, NOT EVIDENCE, and it lives here rather than under
 * qa/evidence/ for exactly that reason: qa/evidence/ holds the committed
 * receipt-of-record, and a file rewritten on every save in the watch loop does
 * not belong beside it. It is transient lane state like the marker above —
 * gitignored, excluded from the receipt's hashed input surface
 * (qa/lib/inputs-hash.mjs), and a lane OUTPUT for the fast filter
 * (qa/lib/affected-tests.mjs). One spelling of the path, here, because the lane
 * writes it and the console reads it and neither may guess where the other put it.
 */
export const LANE_STEPS_REL = "qa/.lane-steps.ndjson";
/** The eyes' in-flight marker's file name, under the profile's `layout.buildDir`. */
export const RENDER_MARKER_NAME = ".cmp-render-in-progress";

/** Past this, a lane marker is a crashed lane's leftover, not a lane. */
export const LANE_MARKER_STALE_MS = 30 * 60 * 1000;
/** Past this, a render marker is a crashed daemon's leftover, not a build. */
export const RENDER_MARKER_FRESH_MS = 5 * 60 * 1000;

/**
 * @param {string} root project root
 * @returns {string} absolute path of the lane marker
 */
export function laneMarkerPath(root) {
  return path.join(root, ...LANE_MARKER_REL.split("/"));
}

/**
 * @param {string} root project root
 * @returns {string} absolute path of the lane's step stream
 */
export function laneStepsPath(root) {
  return path.join(root, ...LANE_STEPS_REL.split("/"));
}

/**
 * The render marker's absolute path for this project, or null when the
 * profile declares no build directory (no eyes, nothing to wait for). Resolved
 * through the manifest and the profile, synchronously; an unusable manifest or
 * profile also yields null — a coordination courtesy must never refuse a lane.
 * @param {string} root
 * @returns {string|null}
 */
export function renderMarkerPath(root) {
  const manifest = resolveHarnessManifest(root);
  if (!manifest.ok) return null;
  const loaded = loadProfileSync(root, manifest.manifest.profile);
  if (!loaded.ok) return null;
  const buildDir = loaded.profile.layout && typeof loaded.profile.layout.buildDir === "string" ? loaded.profile.layout.buildDir : null;
  if (!buildDir) return null;
  return path.join(root, ...buildDir.split("/"), RENDER_MARKER_NAME);
}

/**
 * A marker's mtime in ms, or null when absent/unreadable.
 * @param {string|null} markerPath
 * @returns {number|null}
 */
export function markerMtimeMs(markerPath) {
  if (!markerPath) return null;
  try {
    return fs.statSync(markerPath).mtimeMs;
  } catch {
    return null;
  }
}
