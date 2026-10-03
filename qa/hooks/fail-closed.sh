#!/bin/sh
# fail-closed.sh — run a Stop-hook gate so that a gate which cannot run refuses.
#
#   sh qa/hooks/fail-closed.sh <gate.mjs> [args...]      (the hook payload on stdin)
#
# Claude Code blocks on exit 2 only. Any other non-zero exit is a "non-blocking error":
# the session stops anyway. So a gate that crashes, finds no `node` on a GUI-launched
# PATH, or hangs would let "done" through ungated. This launcher runs the gate with the
# payload on its stdin, passes its stdout and its exit 0 or 2 through unchanged, and
# turns every other outcome into exit 2 with a named reason on stderr.
#
# One exception, recorded in the gate's own design: when the payload carries
# "stop_hook_active": true the session is already continuing because of a Stop hook,
# and the gate nags at most once — so a gate that cannot run then exits 0 rather than
# looping the session on a fault it cannot fix.
#
# The deadline (CMP_GATE_DEADLINE_S, default 45 seconds) is this launcher's own and sits
# under Claude Code's registered hook timeout. A hard kill by Claude Code itself — its
# own timeout, or the session ending — still lets the stop through: nothing inside a
# hook can refuse after it has been killed. CI and the pre-push hook are the gates that
# do not depend on this one.

deadline=${CMP_GATE_DEADLINE_S:-45}
active=0
payload=$(cat)
if printf '%s' "$payload" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  active=1
fi

refuse() {
  [ "$active" = 1 ] && exit 0
  printf '%s\n' "gate could not run ($1) — refusing. Fix the gate, or ask the human to end the session." >&2
  exit 2
}

[ "$#" -ge 1 ] || refuse "no gate named"
tmp=$(mktemp -d "${TMPDIR:-/tmp}/cmp-gate.XXXXXX") || refuse "no temporary directory"
trap 'rm -rf "$tmp"' EXIT
printf '%s' "$payload" > "$tmp/in" || refuse "could not stage the payload"

node "$@" < "$tmp/in" > "$tmp/out" &
gate=$!
# The watchdog's sleep is killed with it, and nothing it runs holds the hook's
# stdout — a stray sleep on that pipe would make Claude Code wait out the deadline.
(
  trap 'kill "$s" 2>/dev/null; exit 0' TERM
  sleep "$deadline" &
  s=$!
  wait "$s"
  : > "$tmp/late"
  kill -TERM "$gate" 2>/dev/null
) < /dev/null > /dev/null 2>&1 &
watchdog=$!
wait "$gate"
rc=$?
kill -TERM "$watchdog" 2>/dev/null
wait "$watchdog" 2>/dev/null

cat "$tmp/out"
case "$rc" in
  0 | 2) exit "$rc" ;;
esac
if [ -f "$tmp/late" ]; then
  refuse "did not finish within ${deadline}s"
fi
refuse "rc=$rc"
