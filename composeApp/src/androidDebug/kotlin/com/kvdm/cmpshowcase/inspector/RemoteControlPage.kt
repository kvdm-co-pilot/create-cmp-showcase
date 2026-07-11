package com.kvdm.cmpshowcase.inspector

/**
 * The `/inspect/remote` page: a self-contained, same-origin (zero CORS) remote-control view
 * of the running app — the "Running Devices window" for a browser. The HUMAN watches the live
 * screenshot (re-fetched ~every 700ms with a cache-buster) and clicks it; the click is scaled
 * from displayed-image space to device px and delivered via `POST /inspect/tap`, and the next
 * poll shows the result. A light `/inspect/tree` poll (~every 3s) feeds the header stats.
 *
 * No external resources, dark-friendly, phone-aspect. Pixels here flow to the HUMAN's browser
 * only — the agent keeps asserting on the tree. Debug builds only, loopback only, like every
 * other route on this server.
 */
object RemoteControlPage {

    fun html(appId: String): String = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$appId — live device view</title>
<style>
  body { margin: 0; background: #101216; color: #e8eaed; display: flex; flex-direction: column;
         min-height: 100vh; font: 13px/1.4 -apple-system, BlinkMacSystemFont, sans-serif; }
  header { display: flex; justify-content: space-between; align-items: baseline; gap: 12px;
           padding: 10px 16px; background: #1a1d23; position: sticky; top: 0;
           border-bottom: 1px solid #2a2e36; }
  header .app { font-weight: 600; }
  #stats { color: #7ee2a8; font-variant-numeric: tabular-nums; }
  main { flex: 1; display: flex; justify-content: center; align-items: flex-start; padding: 14px; }
  #screen { max-height: calc(100vh - 92px); max-width: 94vw; border: 1px solid #2a2e36;
            border-radius: 14px; background: #000; cursor: crosshair; }
  #tapdot { position: fixed; width: 16px; height: 16px; border-radius: 50%; pointer-events: none;
            background: rgba(126, 226, 168, .9); transform: translate(-50%, -50%); opacity: 0;
            transition: opacity .4s; }
  footer { padding: 6px 16px 10px; color: #7a8090; font-size: 11px; text-align: center; }
</style>
</head>
<body>
<header>
  <span class="app">$appId · live device view</span>
  <span id="stats">connecting…</span>
</header>
<main><img id="screen" alt="live device screen (click to tap)"></main>
<div id="tapdot"></div>
<footer>click the screen to tap the real device · create-cmp debug inspector · loopback only, debug builds only</footer>
<script>
"use strict";
var img = document.getElementById("screen");
var stats = document.getElementById("stats");
var dot = document.getElementById("tapdot");
var busy = false;

// Live screenshot: re-fetch ~every 700ms (cache-busted); swap only once loaded (no flicker).
function poll() {
  if (busy) return;
  busy = true;
  var next = new Image();
  next.onload = function () { img.src = next.src; busy = false; };
  next.onerror = function () { busy = false; };
  next.src = "/inspect/screenshot?t=" + Date.now();
}
setInterval(poll, 700);
poll();

// Click → scale displayed coords to device px (natural vs displayed size) → POST /inspect/tap.
img.addEventListener("click", function (e) {
  if (!img.naturalWidth) return;
  var r = img.getBoundingClientRect();
  var x = Math.round((e.clientX - r.left) * (img.naturalWidth / r.width));
  var y = Math.round((e.clientY - r.top) * (img.naturalHeight / r.height));
  dot.style.left = e.clientX + "px";
  dot.style.top = e.clientY + "px";
  dot.style.opacity = "1";
  setTimeout(function () { dot.style.opacity = "0"; }, 350);
  fetch("/inspect/tap", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ x: x, y: y })
  }).catch(function () {});
});

// Header stats: a light tree poll — node + tagged counts, ~every 3s.
function countNodes(node) {
  var acc = { nodes: 1, tags: node.testTag ? 1 : 0 };
  (node.children || []).forEach(function (c) {
    var m = countNodes(c);
    acc.nodes += m.nodes;
    acc.tags += m.tags;
  });
  return acc;
}
function pollTree() {
  fetch("/inspect/tree").then(function (res) { return res.json(); }).then(function (tree) {
    if (!tree.root) { stats.textContent = "tree: not ready"; return; }
    var n = countNodes(tree.root);
    stats.textContent = n.nodes + " nodes · " + n.tags + " tagged";
  }).catch(function () { stats.textContent = "tree: unreachable"; });
}
setInterval(pollTree, 3000);
pollTree();
</script>
</body>
</html>
"""
}
