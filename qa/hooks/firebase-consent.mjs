#!/usr/bin/env node
// firebase-consent.mjs — a PreToolUse hook on Bash: a person sees each Firebase cloud mutation
// before it runs.
//
//   registered in .claude/settings.json by `create-cmp add firebase`; the hook payload on stdin
//
// Four Firebase CLI commands change something outside this repository that git cannot undo:
// `projects:create`, `apps:create`, `firestore:databases:create` and `apps:android:sha:create`.
// When a Bash command runs one of them — directly, through `npx firebase …` / `npx firebase-tools …`
// (or `npm exec`, `pnpm dlx`, `bunx`), behind `env`/`sudo`/`timeout`-style wrappers, or inside
// `sh -c "…"` — this hook answers `permissionDecision: "ask"` with a reason that names the exact
// mutation. For every other command it prints nothing.
//
// `ask` NEVER GRANTS. It asks the person at the keyboard; what they answer is the decision. This
// hook never emits `allow`, so it cannot widen what the session may do.
//
// A CONSENT CHECK, NOT A SAFETY GATE. It exits 0 on its own crash (a payload it cannot read, a
// bug) and asks nothing: a Firebase command it failed to read still goes through the session's
// own permission rules. It reads simple, top-level command words the way a shell splits them;
// a command assembled at runtime (`$(…)`, variables, backticks) is not recognised.
//
// It is the program behind the cmp-firebase-connect skill's consent rule; the skill points here
// and does not restate it. Edit it only on purpose — .claude/settings.json asks before an edit.

/** The mutations, by subcommand, and what each one does in words a person can confirm. */
const MUTATIONS = Object.freeze({
  "projects:create": "creates a new Firebase (Google Cloud) project under the signed-in Google account",
  "apps:create": "registers a new app in a Firebase project",
  "firestore:databases:create": "creates a Firestore database in a Firebase project (its location cannot be changed afterwards)",
  "apps:android:sha:create": "adds a SHA certificate fingerprint to a Firebase Android app",
});

/** Wrappers that run the command after them, and which of their single-letter flags take a value. */
const WRAPPERS = Object.freeze({
  "!": "",
  builtin: "",
  caffeinate: "tw",
  command: "",
  env: "uC",
  exec: "a",
  nice: "n",
  noglob: "",
  nohup: "",
  stdbuf: "ioe",
  sudo: "ugprtUC",
  time: "of",
  timeout: "sk",
  xargs: "nILPsEadJRS",
});
const SHELLS = new Set(["sh", "bash", "zsh", "dash"]);
const FIREBASE_PACKAGES = new Set(["firebase-tools", "firebase"]);
const FIREBASE_VALUE_FLAGS = new Set(["-P", "--project", "--account", "--token", "-c", "--config"]);
const LAUNCHER_VALUE_FLAGS = new Set(["-p", "--package", "-c", "--call", "-w", "--workspace", "-C", "--prefix", "--dir"]);

const ASSIGNMENT = /^[A-Za-z_][A-Za-z0-9_]*=/;
const basename = (w) => w.slice(w.lastIndexOf("/") + 1);

/** The simple commands in a command line, each as its words: quotes resolved, split at ; & | ( ) and newlines. */
export function commandsOf(line) {
  const commands = [];
  let words = [];
  let word = null;
  const endWord = () => {
    if (word !== null) words.push(word);
    word = null;
  };
  const endCommand = () => {
    endWord();
    if (words.length) commands.push(words);
    words = [];
  };
  for (let i = 0; i < line.length; i += 1) {
    const c = line[i];
    if (c === "'" || c === '"') {
      const close = line.indexOf(c, i + 1);
      const end = close < 0 ? line.length : close;
      let text = line.slice(i + 1, end);
      if (c === '"') text = text.replace(/\\(["\\$`])/g, "$1");
      word = (word ?? "") + text;
      i = end;
    } else if (c === "\\" && i + 1 < line.length) {
      if (line[i + 1] !== "\n") word = (word ?? "") + line[i + 1];
      i += 1;
    } else if (c === "#" && word === null) {
      while (i + 1 < line.length && line[i + 1] !== "\n") i += 1;
    } else if (";&|()`\n".includes(c)) {
      endCommand();
    } else if (/\s/.test(c)) {
      endWord();
    } else {
      word = (word ?? "") + c;
    }
  }
  endCommand();
  return commands;
}

/** Past `VAR=value` assignments and the wrappers above: the program's basename and its arguments. */
function argvOf(words) {
  let i = 0;
  for (;;) {
    while (i < words.length && ASSIGNMENT.test(words[i])) i += 1;
    if (i >= words.length) return null;
    const name = basename(words[i]);
    if (!Object.hasOwn(WRAPPERS, name)) return { prog: name, args: words.slice(i + 1) };
    i += 1;
    while (i < words.length && /^-./.test(words[i])) {
      const flag = words[i++];
      if (flag === "--") break;
      if (flag.length === 2 && WRAPPERS[name].includes(flag[1])) i += 1;
    }
    if (name === "timeout" && i < words.length && /^\d+(?:\.\d+)?[smhd]?$/.test(words[i])) i += 1;
  }
}

/** A package spec without its version: `firebase-tools@13` → `firebase-tools`, `@scope/x@1` → `@scope/x`. */
const unversioned = (spec) => spec.replace(/(?!^)@[^/]*$/, "");

/** The Firebase subcommand and its arguments this argv runs, or null. `depth` bounds nested launchers. */
function firebaseCall(argv, depth = 0) {
  if (!argv || depth > 4) return [];
  const { prog, args } = argv;
  if (prog === "firebase") {
    let j = 0;
    while (j < args.length && /^-./.test(args[j])) {
      if (args[j] === "--") return j + 1 < args.length ? [{ sub: args[j + 1], rest: args.slice(j + 2) }] : [];
      j += FIREBASE_VALUE_FLAGS.has(args[j]) ? 2 : 1;
    }
    return j < args.length ? [{ sub: args[j], rest: args.slice(j + 1) }] : [];
  }
  if (SHELLS.has(prog)) {
    const k = args.findIndex((a) => /^-[a-z]*c[a-z]*$/.test(a));
    return k >= 0 && k + 1 < args.length ? callsIn(args[k + 1], depth + 1) : [];
  }
  let rest = null;
  if (prog === "npx" || prog === "bunx") rest = args;
  else if (prog === "npm" || prog === "pnpm" || prog === "yarn") {
    let k = 0;
    while (k < args.length && /^-./.test(args[k])) k += LAUNCHER_VALUE_FLAGS.has(args[k]) ? 2 : 1;
    if (["exec", "x", "dlx"].includes(args[k])) rest = args.slice(k + 1);
  }
  if (rest === null) return [];
  let k = 0;
  let pkg = null;
  while (k < rest.length && /^-./.test(rest[k])) {
    const flag = rest[k];
    if (flag === "--") {
      k += 1;
      break;
    }
    if (flag === "-c" || flag === "--call") return callsIn(rest[k + 1] ?? "", depth + 1);
    if (flag === "-p" || flag === "--package") pkg = rest[k + 1] ?? null;
    if (/^--(package|call)=/.test(flag)) {
      if (flag.startsWith("--call=")) return callsIn(flag.slice(7), depth + 1);
      pkg = flag.slice(10);
    }
    k += LAUNCHER_VALUE_FLAGS.has(flag) ? 2 : 1;
  }
  if (k >= rest.length) return [];
  const bin = unversioned(rest[k]);
  // `npx firebase-tools …` runs the firebase binary; with `-p firebase-tools` the binary is named.
  const prog2 = FIREBASE_PACKAGES.has(bin) && pkg === null ? "firebase" : basename(bin);
  return firebaseCall({ prog: prog2, args: rest.slice(k + 1) }, depth + 1);
}

/** Every Firebase call in a command line. */
function callsIn(line, depth = 0) {
  return commandsOf(line).flatMap((words) => firebaseCall(argvOf(words), depth));
}

/** The mutations a Bash command line would run, each named as the command that runs it. */
export function mutationsIn(line) {
  return callsIn(line)
    .filter(({ sub }) => Object.hasOwn(MUTATIONS, sub))
    .map(({ sub, rest }) => {
      const shown = ["firebase", sub, ...rest].join(" ");
      return { sub, command: shown.length > 200 ? `${shown.slice(0, 197)}...` : shown, what: MUTATIONS[sub] };
    });
}

/** The hook's answer to one payload: the JSON to print, or null for silence. */
export function decide(payload) {
  if (payload?.tool_name !== undefined && payload.tool_name !== "Bash") return null;
  const line = payload?.tool_input?.command;
  if (typeof line !== "string") return null;
  const found = mutationsIn(line);
  if (found.length === 0) return null;
  const named = found.map((m) => `\`${m.command}\` ${m.what}`).join("; and ");
  return {
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "ask",
      permissionDecisionReason:
        `Firebase cloud mutation: ${named}. This changes a Firebase project outside this repository, and git cannot ` +
        "undo it — confirm the project, the account and the values before it runs. " +
        "(qa/hooks/firebase-consent.mjs asks; it never allows.)",
    },
  };
}

async function main() {
  let raw = "";
  for await (const chunk of process.stdin) raw += chunk;
  const answer = decide(JSON.parse(raw));
  if (answer) process.stdout.write(`${JSON.stringify(answer)}\n`);
}

main().then(
  () => process.exit(0),
  (e) => {
    process.stderr.write(`firebase-consent: could not read this command (${e?.message ?? e}); not asking.\n`);
    process.exit(0);
  },
);
