// determinism.mjs — the comparison half of the lane's determinism probe.
//
// ARCH-13 statically bans ambient time reads (Clock.System, LocalDate.now,
// TimeZone.currentSystemDefault) in APP code — but a library the app calls
// can still read the wall clock, and a golden test can still depend on the
// machine's timezone through a seam the static net cannot see. This project
// family has already been bitten: a golden tree green at 23:00 and red by
// morning, because a ViewModel was constructed without its injected clock.
//
// The probe (verify.mjs stepDeterminism) runs the JVM test tier TWICE under
// maximally-shifted timezones and fails iff the two runs' OUTCOMES differ.
// This module owns the two judgments that make that comparison honest:
//
//   - WHAT COUNTS AS AN OUTCOME: a test's verdict (pass/fail/error/skip)
//     and its failure output — never its duration. Durations are not parsed
//     at all, so a timing wobble is structurally incapable of tripping the
//     probe (the brief-level rule "duration is not a difference" is enforced
//     by construction, not by filtering).
//
//   - WHAT THE FAILURE MESSAGE MUST SAY: which test, which lane step owns
//     it, and the observable difference between the two runs — never a bare
//     "nondeterministic". A probe whose red is unactionable just teaches
//     people to turn it off.

import fs from "node:fs";
import path from "node:path";

/**
 * The two probe timezones — chosen so the two legs NEVER share a calendar
 * date, at any instant:
 *
 *   Etc/GMT+12 = UTC-12  (POSIX sign convention: Etc/GMT+N means UTC-N)
 *   Etc/GMT-14 = UTC+14  (the highest real-world offset, Line Islands)
 *
 * The offsets are 26 hours apart — more than a full day — so the two legs'
 * local dates differ at EVERY moment of every day, and any date-derived
 * value (a "today" default, a day-boundary bucket, a formatted date in a
 * golden tree) is guaranteed to differ between the legs. A UTC-vs-UTC+14
 * pair would NOT have this property: those legs share a date for ten hours
 * of every day, so the probe's power would depend on what time you ran it —
 * the exact class of flakiness it exists to hunt.
 */
export const DETERMINISM_TIMEZONES = [
  { tz: "Etc/GMT+12", label: "UTC-12" },
  { tz: "Etc/GMT-14", label: "UTC+14" },
];

const XML_ENTITIES = { "&lt;": "<", "&gt;": ">", "&quot;": '"', "&apos;": "'", "&amp;": "&" };

function unescapeXml(s) {
  return s
    .replace(/&#x([0-9a-fA-F]+);/g, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec) => String.fromCodePoint(Number(dec)))
    .replace(/&(lt|gt|quot|apos|amp);/g, (m) => XML_ENTITIES[m]);
}

function attr(attrs, name) {
  const m = attrs.match(new RegExp(`${name}="([^"]*)"`));
  return m ? unescapeXml(m[1]) : null;
}

/**
 * Parse one Gradle JUnit results directory into per-test outcomes.
 * DELIBERATELY parses only verdict-bearing content: testcase identity,
 * status, and failure/error text. `time="…"` attributes are never read, so
 * two runs that differ only in duration produce identical outcome maps.
 *
 * @param {string} dir a test-results directory (TEST-*.xml files, flat)
 * @returns {Record<string, {status: "pass"|"fail"|"error"|"skip", messages: string[]}>}
 *   keyed by `classname.name`; empty object when the directory is absent
 *   (the caller decides what an empty leg means — this parser never guesses)
 */
export function parseJUnitOutcomes(dir) {
  const outcomes = {};
  if (!fs.existsSync(dir)) return outcomes;
  for (const entry of fs.readdirSync(dir)) {
    if (!entry.startsWith("TEST-") || !entry.endsWith(".xml")) continue;
    const xml = fs.readFileSync(path.join(dir, entry), "utf8");
    const caseRe = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
    for (const m of xml.matchAll(caseRe)) {
      const attrs = m[1];
      const body = m[2] ?? "";
      const classname = attr(attrs, "classname") ?? "";
      const name = attr(attrs, "name") ?? "";
      if (!classname && !name) continue;
      let status = "pass";
      const messages = [];
      const childRe = /<(failure|error)\b([^>]*?)(?:\/>|>([\s\S]*?)<\/\1>)/g;
      for (const c of body.matchAll(childRe)) {
        status = c[1] === "error" ? "error" : "fail";
        const message = attr(c[2], "message");
        const text = c[3] ? unescapeXml(c[3]).trim() : "";
        messages.push(message ?? text.split("\n")[0] ?? "");
      }
      if (status === "pass" && /<skipped\b/.test(body)) status = "skip";
      outcomes[`${classname}.${name}`] = { status, messages };
    }
  }
  return outcomes;
}

/**
 * Which lane step owns a test class — so the probe's failure message names
 * the step a reader would re-run, not just a class name. The patterns are
 * the same filters the lane's own gradleTestStep calls use.
 * @param {string} classname fully-qualified test class
 * @returns {"goldenTrees"|"conformance"|"a11y"|"unitTests"}
 */
export function laneStepForTestClass(classname) {
  if (/GoldenTreeTest$/.test(classname)) return "goldenTrees";
  if (/ArchitectureConformanceTest$/.test(classname)) return "conformance";
  if (/A11yConformanceTest$/.test(classname)) return "a11y";
  return "unitTests";
}

function classnameOf(testId) {
  // testId is `classname.name`; the class is everything before the last dot
  // segment that starts the (possibly backticked, space-bearing) test name.
  // Kotlin test names contain dots rarely but spaces often — the classname
  // never contains a space, so split at the first segment containing one,
  // falling back to the last dot.
  const spaceIdx = testId.indexOf(" ");
  const scope = spaceIdx === -1 ? testId : testId.slice(0, spaceIdx);
  const lastDot = scope.lastIndexOf(".");
  return lastDot === -1 ? testId : testId.slice(0, lastDot);
}

/**
 * Compare two legs' outcomes. Returns one entry per observable difference,
 * each carrying everything the failure message must name: the test, the
 * owning lane step, and what differed between the legs.
 *
 * Kinds:
 *   verdict-flip           different status (pass/fail/error/skip)
 *   only-in-one-leg        the test executed in one leg only
 *   failure-text-changed   failed in BOTH legs, but with different output —
 *                          a date-dependent assertion message is still a
 *                          timezone leak even when both legs are red
 *
 * @param {Record<string, {status: string, messages: string[]}>} a leg A outcomes
 * @param {Record<string, {status: string, messages: string[]}>} b leg B outcomes
 * @param {string} labelA human label for leg A (e.g. "TZ=Etc/GMT+12 (UTC-12)")
 * @param {string} labelB human label for leg B
 * @returns {Array<{test: string, step: string, kind: string, detail: string}>}
 */
export function compareOutcomes(a, b, labelA, labelB) {
  const diffs = [];
  const ids = [...new Set([...Object.keys(a), ...Object.keys(b)])].sort();
  for (const id of ids) {
    const step = laneStepForTestClass(classnameOf(id));
    const inA = a[id];
    const inB = b[id];
    if (!inA || !inB) {
      const where = inA ? labelA : labelB;
      const missing = inA ? labelB : labelA;
      diffs.push({ test: id, step, kind: "only-in-one-leg", detail: `executed under ${where} but produced no result under ${missing}` });
      continue;
    }
    if (inA.status !== inB.status) {
      const firstLine = (inA.status === "pass" ? inB : inA).messages[0]?.split("\n")[0] ?? "";
      diffs.push({
        test: id,
        step,
        kind: "verdict-flip",
        detail: `${inA.status.toUpperCase()} under ${labelA}, ${inB.status.toUpperCase()} under ${labelB}${firstLine ? `: ${firstLine}` : ""}`,
      });
      continue;
    }
    if (inA.status !== "pass" && inA.messages.join("\n") !== inB.messages.join("\n")) {
      diffs.push({
        test: id,
        step,
        kind: "failure-text-changed",
        detail: `failed under both, with different output — ${labelA}: "${inA.messages[0]?.split("\n")[0] ?? ""}" vs ${labelB}: "${inB.messages[0]?.split("\n")[0] ?? ""}"`,
      });
    }
  }
  return diffs;
}
