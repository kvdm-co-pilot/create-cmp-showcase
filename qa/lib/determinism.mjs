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
  // ANCHORED. Unanchored, `name="…"` matches inside `classname="…"`, so for any
  // writer that emits classname FIRST — pytest, jest-junit, gotestsum all do —
  // `attr(attrs, "name")` returned the CLASSNAME. Every test in a class then
  // collapsed onto one `classname.classname` key, last-write-wins, and the
  // determinism probe reported a genuine timezone flip as deterministic. Gradle
  // emits name first and worked by luck, which is why this survived.
  const m = attrs.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`));
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
/**
 * The report formats the core can read. Each is its OWN parser, dispatched by
 * the profile's declaration — never sniffed from the file, because a wrong
 * guess here produces `{}`, and an empty leg compared against an empty leg
 * yields no differences: the probe would pass having read nothing. That exact
 * failure is why the declaration exists.
 *
 * Every parser here obeys one rule, and it is the rule the whole probe rests
 * on: READ ONLY VERDICT-BEARING CONTENT. No durations, no timestamps, no run
 * ids. Two runs that differ solely in how long they took must produce byte-
 * identical outcome maps, or the probe reports its own noise as a defect.
 */
export const REPORT_FORMATS = Object.freeze(["junit-xml", "tap", "ctrf"]);

/**
 * Why a profile's `reports` declaration cannot be read — or null when it can.
 * PATTERN: JUnit XML as the lingua franca (pytest --junitxml, go-junit-report,
 * cargo2junit, jest-junit, swift test --xunit-output); the profile DECLARES it.
 * WHY IT WORKS: the parser stops assuming what a runner emitted. HOW IT FAILS:
 * a dialect parses to {} and looks like "no tests". WHAT WE DO: undeclared or
 * unsupported is a refusal by name here, and the probe treats an empty leg as a
 * refusal, never a pass.
 * @param {{format?: string}|null|undefined} reports
 * @returns {string|null}
 */
export function reportFormatProblem(reports) {
  if (!reports || typeof reports !== "object") return "the profile declares no `reports` — declare { format: \"junit-xml\" } (the format this stack's test runner emits) so the probe parses what was declared rather than assuming it";
  if (!REPORT_FORMATS.includes(reports.format)) return `reports.format ${JSON.stringify(reports.format)} is not one the core can read (${REPORT_FORMATS.join(", ")}) — a parser for it is its own change`;
  return null;
}

export function parseJUnitOutcomes(dir, { format } = {}) {
  const problem = reportFormatProblem({ format });
  if (problem) throw new Error(problem);
  const outcomes = {};
  if (!fs.existsSync(dir)) return outcomes;
  for (const entry of fs.readdirSync(dir)) {
    // ANY .xml, not just `TEST-*.xml`. That prefix is the Ant/Gradle/Surefire
    // filename convention; pytest writes `junit.xml`, jest-junit `junit.xml`,
    // gotestsum `junit.xml`, cargo2junit `results.xml`, `dotnet test`
    // `TestResults.xml`. Every one of them parsed to {} — and an empty leg
    // compared against an empty leg yields no differences, so the probe passed
    // having read nothing. The `<testcase` match below is the real filter: a
    // file with no test cases contributes nothing either way.
    if (!entry.endsWith(".xml")) continue;
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
 * Which lane step owns a test class — so the probe's failure message names the
 * step a reader would re-run, not just a class name.
 *
 * THIS IS THE PROFILE'S KNOWLEDGE and it moved there (profiles/cmp/steps-cmp.mjs).
 * The core used to answer it with four names — `goldenTrees`, `conformance`,
 * `a11y`, `unitTests` — three of which exist only in the cmp pack, matched
 * against Kotlin class-name conventions. `compareOutcomes` called it
 * unconditionally, so ANY profile reusing the core's determinism comparison got
 * another stack's step names stamped onto its own diffs. It survived the lint
 * only because this module was not in the lint's list; it is now.
 *
 * A caller that supplies no attribution gets `null` and the diff carries no
 * step — an honest absence, not a borrowed name.
 * @param {string} classname fully-qualified test class
 * @param {((classname: string) => string|null)} [attribute] the profile's mapping
 * @returns {string|null}
 */
export function laneStepForTestClass(classname, attribute) {
  return typeof attribute === "function" ? (attribute(classname) ?? null) : null;
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
export function compareOutcomes(a, b, labelA, labelB, attribute) {
  const diffs = [];
  const ids = [...new Set([...Object.keys(a), ...Object.keys(b)])].sort();
  for (const id of ids) {
    const step = laneStepForTestClass(classnameOf(id), attribute);
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

// ── TAP and CTRF ────────────────────────────────────────────────────────────
// The second and third formats the core can read. Both were named as "next"
// beside REPORT_FORMATS for months; a profile declaring either got a refusal
// telling it a parser was its own change. This is that change.
//
// The rule both obey is the one the whole probe rests on: READ ONLY
// VERDICT-BEARING CONTENT. TAP carries timings in YAML diagnostics and in
// `# time=…` comments; CTRF carries `duration`, `start` and `stop` on every
// test and a `summary` block full of them. None of it is read. A parser that
// let a millisecond through would make every second run "nondeterministic" and
// the probe would be reporting its own noise.

/**
 * Parse a TAP stream into per-test outcomes.
 *
 * IDENTITY. TAP has no classname — a test is its description, and the number is
 * positional, so it is NOT part of the key: a suite whose tests reorder between
 * two runs would otherwise read as every test having changed. Duplicate
 * descriptions are disambiguated by occurrence, which is the honest answer when
 * a runner emits two tests with one name.
 *
 * DIRECTIVES decide status before the ok/not-ok does: `# SKIP` is a skip
 * whichever way the line reads, and a `# TODO` failure is an expected one, so
 * it is a skip rather than a fail — that is TAP's own semantics, and reading it
 * as a failure would make a passing suite look broken.
 *
 * @param {string} text one TAP stream
 * @returns {Record<string, {status: "pass"|"fail"|"error"|"skip", messages: string[]}>}
 */
export function parseTapStream(text) {
  const outcomes = {};
  const seen = new Map();
  const lines = String(text ?? "").split("\n");
  // NESTING IS INDENTATION, and identity is the PATH. Found by running Node's
  // own `--test-reporter=tap` at it: a child keyed by its bare name collides
  // with a same-named child under another parent, and the collision is then
  // resolved positionally — which reintroduces exactly the ordering
  // sensitivity the bare test number was excluded to avoid. `parent > child`
  // is stable however the parents run, and it mirrors what JUnit keys as
  // `classname.name` and CTRF as `suite.name`, so all three formats identify
  // the same test the same way.
  //
  // THE PARENT IS NOT KNOWN FROM ITS RESULT LINE. TAP emits a parent's `ok`
  // AFTER its children — it cannot report a verdict it has not finished
  // computing — so a stack built from result lines makes the previous SIBLING
  // the parent, which is what the first attempt did (children came out under
  // "todo one"). The line that does precede the block is `# Subtest: <name>`,
  // at the parent's own indentation, and both major TAP producers emit it.
  // A producer that emits none has a flat stream, where there is nothing to
  // nest and bare names are already the whole path.
  const stack = [];
  let pending = null;

  const commit = () => {
    if (!pending) return;
    const n = (seen.get(pending.name) ?? 0) + 1;
    seen.set(pending.name, n);
    outcomes[n === 1 ? pending.name : `${pending.name} #${n}`] = { status: pending.status, messages: pending.messages };
    pending = null;
  };

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const sub = line.match(/^(\s*)#\s*Subtest:\s*(.+?)\s*$/);
    if (sub) {
      commit();
      const depth = sub[1].length;
      while (stack.length && stack[stack.length - 1].depth >= depth) stack.pop();
      stack.push({ depth, name: sub[2] });
      continue;
    }
    const m = line.match(/^(\s*)(not ok|ok)\b[ \t]*(\d+)?[ \t]*-?[ \t]*(.*)$/);
    if (!m) {
      // A YAML diagnostic block belongs to the test above it.
      //
      // WHICH KEYS ARE READ decides whether a whole defect class is visible.
      // The first version read `message` and `severity` — the keys the TAP spec's
      // own examples show — and Node writes the failure text under `error:` as a
      // block scalar instead. Every failure therefore parsed with NO messages,
      // and `compareOutcomes`'s "failed under both, with different output" could
      // never fire for the most widely available TAP producer there is. That is a
      // silent wrong verdict, and no amount of reading the spec would have found
      // it.
      //
      // `duration_ms` and `location` stay excluded: one is time, the other is an
      // absolute path. Neither is verdict-bearing, and both would make two
      // identical runs look different.
      if (pending && /^\s*---\s*$/.test(line)) {
        for (i += 1; i < lines.length && !/^\s*\.\.\.\s*$/.test(lines[i]); i += 1) {
          const kv = lines[i].match(/^(\s*)(message|severity|error|code|name|failureType)\s*:\s*(.*)$/);
          if (!kv) continue;
          const [, indent, key, rawValue] = kv;
          const value = rawValue.trim();
          if (/^[|>]-?\+?$/.test(value)) {
            // A block scalar: its body is the more-indented lines beneath it.
            const body = [];
            for (let j = i + 1; j < lines.length; j += 1) {
              if (/^\s*\.\.\.\s*$/.test(lines[j])) break;
              const deeper = lines[j].match(/^(\s*)(.*)$/);
              if (lines[j].trim() && deeper[1].length <= indent.length) break;
              body.push(deeper[2]);
              i = j;
            }
            const firstLine = body.find((b) => b.trim());
            if (firstLine) pending.messages.push(`${key}: ${firstLine.trim()}`);
            continue;
          }
          if (value) pending.messages.push(key === "message" ? value.replace(/^["']|["']$/g, "") : `${key}: ${value.replace(/^["']|["']$/g, "")}`);
        }
      }
      continue;
    }
    commit();
    const depth = m[1].length;
    // A result line CLOSES its own frame: everything at or deeper than it is
    // finished, and the frame it names is itself (pushed by its `# Subtest:`).
    while (stack.length && stack[stack.length - 1].depth >= depth) stack.pop();
    const rest = m[4] ?? "";
    // The directive is separated from the description by ` # `. Splitting on it
    // also strips a runner's `# time=…` trailer, which must never reach a key.
    const hash = rest.indexOf("#");
    const description = (hash === -1 ? rest : rest.slice(0, hash)).trim();
    const directive = hash === -1 ? "" : rest.slice(hash + 1).trim();
    let status = m[2] === "ok" ? "pass" : "fail";
    if (/^skip\b/i.test(directive)) status = "skip";
    else if (/^todo\b/i.test(directive)) status = "skip";
    const own = description || `test ${m[3] ?? Object.keys(outcomes).length + 1}`;
    const name = [...stack.map((f) => f.name), own].join(" > ");
    pending = { name, status, messages: [] };
  }
  commit();
  return outcomes;
}

/**
 * Parse a CTRF report (JSON) into per-test outcomes.
 *
 * CTRF names four statuses this maps onto the probe's four: `passed`,
 * `failed`, `skipped`, `pending` (a skip — it did not run), and `other`, which
 * is the honest match for `error`: the runner could not say. A status the
 * schema does not define is `error` too, because "I do not know what this
 * means" is not "it passed" — the same stance `ERROR` takes everywhere else in
 * this harness.
 *
 * IDENTITY is `suite.name` when a suite is given, mirroring JUnit's
 * `classname.name`, so the two formats key the same test the same way.
 *
 * @param {string} json one CTRF document
 * @returns {Record<string, {status: "pass"|"fail"|"error"|"skip", messages: string[]}>}
 */
export function parseCtrfReport(json) {
  const outcomes = {};
  let doc;
  try {
    doc = JSON.parse(json);
  } catch {
    // Unparseable is EMPTY, not a throw: the caller's own rule is that an empty
    // leg is a refusal, never a pass, so this cannot hide a defect — and one
    // corrupt file must not stop the other leg from being read.
    return outcomes;
  }
  const tests = Array.isArray(doc?.results?.tests) ? doc.results.tests : Array.isArray(doc?.tests) ? doc.tests : [];
  const STATUS = { passed: "pass", failed: "fail", skipped: "skip", pending: "skip", other: "error" };
  const seen = new Map();
  for (const t of tests) {
    if (!t || typeof t.name !== "string" || !t.name) continue;
    const base = typeof t.suite === "string" && t.suite ? `${t.suite}.${t.name}` : t.name;
    const n = (seen.get(base) ?? 0) + 1;
    seen.set(base, n);
    const messages = [];
    if (typeof t.message === "string" && t.message) messages.push(t.message);
    if (!messages.length && typeof t.trace === "string" && t.trace) messages.push(t.trace.split("\n")[0]);
    outcomes[n === 1 ? base : `${base} #${n}`] = { status: STATUS[t.status] ?? "error", messages };
  }
  return outcomes;
}

/**
 * The one entry point a step should call: parse a results directory according
 * to the format the PROFILE declared.
 *
 * Dispatching here rather than at each call site is what keeps the refusal in
 * one place. A step that picked its own parser would be free to disagree with
 * `reportFormatProblem` about what is supported, and the disagreement would
 * surface as an empty outcome map — the failure mode this whole file is
 * arranged to prevent.
 *
 * @param {string} dir a test-results directory
 * @param {{format?: string}} declared the profile's `reports`
 * @returns {Record<string, {status: string, messages: string[]}>}
 */
export function parseReportOutcomes(dir, { format } = {}) {
  const problem = reportFormatProblem({ format });
  if (problem) throw new Error(problem);
  if (format === "junit-xml") return parseJUnitOutcomes(dir, { format });

  const outcomes = {};
  if (!fs.existsSync(dir)) return outcomes;
  const wanted = format === "tap" ? /\.(tap|txt)$/i : /\.json$/i;
  for (const entry of fs.readdirSync(dir).sort()) {
    if (!wanted.test(entry)) continue;
    const body = fs.readFileSync(path.join(dir, entry), "utf8");
    Object.assign(outcomes, format === "tap" ? parseTapStream(body) : parseCtrfReport(body));
  }
  return outcomes;
}
