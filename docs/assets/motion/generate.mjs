#!/usr/bin/env node
// Generates the animated SVGs in this folder FROM THE APP'S OWN MOTION TOKENS.
//
// Why a generator and not hand-drawn art: a README that illustrates a design system is a
// claim about the code, and a hand-drawn claim rots the first time someone retunes a spring.
// This reads `presentation/theme/Tokens.kt` and `Theme.kt`, integrates the same damped
// harmonic oscillator Compose integrates (mass 1, the token's own damping ratio and
// stiffness), and emits CSS `linear()` easings sampled from that solution. The curves in the
// README are therefore the curves in the app, not an artist's impression of them.
//
// Re-run after any motion-token change:   node docs/assets/motion/generate.mjs
// It is documentation tooling, not lane code — nothing gates on it, and it writes only into
// this folder.

import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = join(HERE, '..', '..', '..')
const THEME_DIR = join(ROOT, 'composeApp/src/commonMain/kotlin/com/kvdm/fuelled/presentation/theme')

// ── Reading the tokens ────────────────────────────────────────────────────────────────────

const tokensSrc = readFileSync(join(THEME_DIR, 'Tokens.kt'), 'utf8')
const themeSrc = readFileSync(join(THEME_DIR, 'Theme.kt'), 'utf8')

/** Every `const val NAME = 123` in Tokens.kt, by name. */
const ints = Object.fromEntries(
  [...tokensSrc.matchAll(/const val (\w+)\s*=\s*(\d+)\b(?!f)/g)].map(([, k, v]) => [k, Number(v)]),
)
/** Every `const val NAME = 0.97f`. */
const floats = Object.fromEntries(
  [...tokensSrc.matchAll(/const val (\w+)\s*=\s*([\d.]+)f/g)].map(([, k, v]) => [k, Number(v)]),
)
/** Every `val Name: Easing = CubicBezierEasing(a, b, c, d)`. */
const easings = Object.fromEntries(
  [...tokensSrc.matchAll(/val (\w+):\s*Easing\s*=\s*CubicBezierEasing\(([^)]+)\)/g)].map(([, k, args]) => [
    k,
    args.split(',').map((n) => Number(n.trim().replace(/f$/, ''))),
  ]),
)
/** Every `val Name = SpringSpec(dampingRatio = z, stiffness = k)`. */
const springs = Object.fromEntries(
  [...tokensSrc.matchAll(/val (\w+)\s*=\s*SpringSpec\(dampingRatio = ([\d.]+)f, stiffness = ([\d.]+)f\)/g)].map(
    ([, k, z, s]) => [k, { dampingRatio: Number(z), stiffness: Number(s) }],
  ),
)
/** Every `val Name = 16.dp`, with or without the `: Dp` annotation. */
const dps = Object.fromEntries(
  [...tokensSrc.matchAll(/val (\w+)(?::\s*Dp)?\s*=\s*([\d.]+)\.dp/g)].map(([, k, v]) => [k, Number(v)]),
)
/** Every `val Name = Color(0xFFRRGGBB)` in Theme.kt. */
const C = Object.fromEntries(
  [...themeSrc.matchAll(/val (\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)/g)].map(([, k, hex]) => [
    k,
    '#' + hex.slice(2).toUpperCase(),
  ]),
)

/** Refuses to hand a missing token to a drawing routine — a NaN coordinate fails silently. */
function need(bag, key, what) {
  const v = bag[key]
  if (v === undefined || Number.isNaN(v)) throw new Error(`token ${what}.${key} not found in Tokens.kt`)
  return v
}

const D = ints // Duration.* all live in the flat const namespace
const STAGGER = need(ints, 'StaggerStepMs', 'FuelledMotion')
const CAP = need(ints, 'StaggerCap', 'FuelledMotion')
const RISE = need(dps, 'EnterRise', 'FuelledMotion')
const NAV_H = need(dps, 'BottomNavHeight', 'FuelledTokens')

/** `IntroHold` is a product rule in IntroScreen.kt, not a token — quoted, never assumed. */
const introSrc = readFileSync(
  join(ROOT, 'composeApp/src/commonMain/kotlin/com/kvdm/fuelled/presentation/motion/IntroScreen.kt'),
  'utf8',
)
const introHoldMs = introSrc.match(/val IntroHold: Duration = (\d+)\.milliseconds/)
const IntroHoldNote = introHoldMs ? `${introHoldMs[1]} ms` : 'briefly'

// ── The spring solver: the same ODE Compose integrates ────────────────────────────────────

/**
 * Unit step response of a damped harmonic oscillator, mass 1.
 *   critically damped (z == 1): 1 - (1 + w t) e^(-w t)
 *   under-damped     (z <  1): 1 - e^(-z w t)[cos(wd t) + (z w / wd) sin(wd t)]
 *   over-damped      (z >  1): sum of two decaying exponentials
 */
function springValue({ dampingRatio: z, stiffness: k }, t) {
  const w = Math.sqrt(k) // mass = 1
  if (Math.abs(z - 1) < 1e-9) return 1 - (1 + w * t) * Math.exp(-w * t)
  if (z < 1) {
    const wd = w * Math.sqrt(1 - z * z)
    return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t))
  }
  const r = w * Math.sqrt(z * z - 1)
  const a = -z * w + r
  const b = -z * w - r
  return 1 - (b * Math.exp(a * t) - a * Math.exp(b * t)) / (b - a)
}

/**
 * Milliseconds until the response stays inside `eps` of its target for good.
 *
 * The default is Compose's own `Spring.DefaultDisplacementThreshold` (0.01), NOT a stricter
 * mathematical ideal — an animation is over when Compose stops running it, and quoting a
 * settle time the runtime never waits for would be a prettier number about a different app.
 */
function settleMs(spring, eps = 0.01) {
  let last = 0
  for (let ms = 1; ms <= 6000; ms += 1) {
    if (Math.abs(springValue(spring, ms / 1000) - 1) >= eps) last = ms
  }
  return last + 8
}

/** Peak overshoot as a percentage (0 for anything critically or over-damped). */
function overshootPct(spring) {
  let peak = 0
  const dur = settleMs(spring)
  for (let ms = 0; ms <= dur; ms += 1) peak = Math.max(peak, springValue(spring, ms / 1000))
  return Math.max(0, (peak - 1) * 100)
}

/**
 * A CSS `linear()` easing sampled from that solution — so the browser replays the integral
 * rather than a designer's guess at it. Values outside 0..1 are legal and carry the overshoot.
 */
function springEasing(spring, samples = 36) {
  const dur = settleMs(spring)
  const pts = []
  for (let i = 0; i <= samples; i += 1) {
    const p = i / samples
    const v = springValue(spring, (p * dur) / 1000)
    pts.push(i === 0 ? '0' : i === samples ? '1' : `${round(v)} ${round(p * 100, 2)}%`)
  }
  return { css: `linear(${pts.join(', ')})`, durationMs: dur }
}

/**
 * `var(--sp-lively)` etc. The sampled table is ~1 kB; inlining it at every use point put five
 * copies in nav.svg alone, so each document declares the three springs once, in svg().
 */
const springVar = (name) => `var(--sp-${name.toLowerCase()})`

const round = (n, dp = 4) => Number(n.toFixed(dp)).toString()
const bez = (name) => `cubic-bezier(${easings[name].map((n) => round(n, 3)).join(', ')})`

/** Sample a cubic-bezier easing y at x, by Newton-solving for the parameter. */
function bezierY([x1, y1, x2, y2], x) {
  const cx = (t) => 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
  const cy = (t) => 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
  let lo = 0
  let hi = 1
  for (let i = 0; i < 40; i += 1) {
    const mid = (lo + hi) / 2
    if (cx(mid) < x) lo = mid
    else hi = mid
  }
  return cy((lo + hi) / 2)
}

// ── Shared SVG furniture ──────────────────────────────────────────────────────────────────

const SANS = 'ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif'
const MONO = 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'

/** Percentage of a loop, as a keyframe selector. */
const at = (ms, loop) => `${round((ms / loop) * 100, 4)}%`

/**
 * Keyframes for a property whose FIRST FRAME must already read.
 *
 * An animated SVG is shown as a still more often than it looks: a throttled background tab,
 * a renderer that does not run CSS in an <img>, a social-card preview. All of them show
 * frame 0. Written the obvious way — start empty, animate to full — the hero's frame 0 is a
 * black rectangle and the stagger card's is an empty panel, which reads as broken rather
 * than as not-yet-started.
 *
 * So every track holds the FINISHED state at 0%, rewinds inside a window where the element
 * is hidden or off-canvas, replays, and closes the loop back on the finished state.
 */
function replay(prop, { loop, final, start, rewindAt, resetAt, from, to, ease, outEase }) {
  const lines = [`    0%,${at(rewindAt, loop)}{${prop}:${final}${outEase ? `;animation-timing-function:${outEase}` : ''}}`]
  if (resetAt < from) lines.push(`    ${at(resetAt, loop)}{${prop}:${start}}`)
  lines.push(`    ${at(from, loop)}{${prop}:${start}${ease ? `;animation-timing-function:${ease}` : ''}}`)
  lines.push(`    ${at(to, loop)},100%{${prop}:${final}}`)
  return lines.join('\n')
}

/**
 * Wraps a document. `base` styles describe the FINISHED state, so that when
 * `prefers-reduced-motion` switches every animation off the still is the end of the story
 * rather than the start of it — the same promise MOTION-02 makes inside the app.
 */
function svg({ w, h, title, desc, css, body }) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-labelledby="t d">
<title id="t">${title}</title><desc id="d">${desc}</desc>
<style>
  svg{${Object.entries(springs).map(([n, sp]) => `--sp-${n.toLowerCase()}:${springEasing(sp).css}`).join(';')}}
  .sans{font-family:${SANS}}
  .mono{font-family:${MONO}}
  text{fill:${C.OnSurface};dominant-baseline:middle}
  .dim{fill:${C.OnSurfaceVariant}}
  .lime{fill:${C.Primary}}
  .eyebrow{font-size:10px;font-weight:700;letter-spacing:1.4px;fill:${C.OnSurfaceVariant}}
${css}
  @media (prefers-reduced-motion: reduce){
    *{animation:none !important}
  }
</style>
<rect width="${w}" height="${h}" rx="18" fill="${C.Background}"/>
${body}
</svg>
`
}

const out = (name, content) => {
  writeFileSync(join(HERE, name), content)
  console.log(`  ${name.padEnd(16)} ${String(content.length).padStart(6)} bytes`)
}

// ══ 1. ignition.svg — the app's first frame, beside the spec it is playing ═════════════════
//
// The left half is the ignition itself, rebuilt from the same geometry `IntroScreen.kt`
// draws (the ring's radius and stroke, the bolt's unit-box path, the seven letters and their
// stagger). The right half is the choreography's own score, lighting a beat at a time in
// sync — so the picture and the spec cannot disagree.

function ignition() {
  const W = 920
  const H = 340
  const lively = springEasing(springs.Lively)
  const weighty = springEasing(springs.Weighty)

  // The choreography, in milliseconds, read straight off IntroScreen.kt's LaunchedEffect.
  const tSpark = 0
  const tRing = lively.durationMs // spark.animateTo suspends until it settles
  const tReveal = tRing + D.Expressive // then delay(Expressive)
  const tLetters = tReveal + D.Emphasized
  const letterWindow = D.Standard + STAGGER * CAP
  const tDone = tLetters + letterWindow + D.Quick

  // The loop OPENS on the assembled mark and holds it, then blanks, rewinds unseen, and
  // plays the ignition again — see replay(). A still of this file is therefore the finished
  // logo rather than an unlit stage, and the loop closes assembled-to-assembled.
  const HOLD = 1300 // assembled, at the top of the loop
  const DARK = 1560 // stage fully faded out
  const RESET = 1600 // inner elements snap back, unseen
  const T0 = 1700 // the spark ignites
  const LOOP = T0 + tDone + 900

  // Ring geometry, in the same proportions as the 196 dp ring with its 16 dp stroke.
  const cx = 196
  const cy = 152
  const stroke = 16
  const r = 76
  const CIRC = 2 * Math.PI * r

  // The bolt, from BrandMark's unit-box points, scaled to a 72 dp badge in the ring's centre.
  const MARK = 72
  const mx = cx - MARK / 2
  const my = cy - MARK / 2
  const BOLT = [
    [0.58, 0.06], [0.24, 0.55], [0.47, 0.55], [0.4, 0.94], [0.8, 0.42], [0.55, 0.42],
  ]
  const boltPath =
    BOLT.map(([x, y], i) => `${i ? 'L' : 'M'}${round(mx + x * MARK, 2)} ${round(my + y * MARK, 2)}`).join(' ') + ' Z'

  // "Fuelled", one <text> per letter so each can carry its own stagger delay.
  const NAME = 'Fuelled'
  const FS = 42
  const ADV = { F: 0.58, u: 0.62, e: 0.57, l: 0.28, d: 0.62 }
  const widths = [...NAME].map((ch) => ADV[ch] * FS)
  const track = -0.5
  const total = widths.reduce((a, b) => a + b, 0) + track * (NAME.length - 1)
  let pen = cx - total / 2
  const letters = [...NAME].map((ch, i) => {
    const x = pen + widths[i] / 2
    pen += widths[i] + track
    return { ch, x, i }
  })

  const beats = [
    ['spark', `Springs.Lively · ${lively.durationMs} ms`, tSpark],
    ['ring sweep', `Springs.Weighty · ${weighty.durationMs} ms`, tRing],
    ['mark reveal', `Emphasized ${D.Emphasized} ms · Enter`, tReveal],
    ['wordmark', `Standard ${D.Standard} ms · +${STAGGER} ms/letter`, tLetters],
  ]
  const RX = 492
  const RW = 396
  const rowY = (i) => 136 + i * 42

  // `prop` names the first declaration; a value may carry further declarations after a ';'
  // (the letters animate opacity AND transform as one track).
  const R = (prop, o) => replay(prop, { loop: LOOP, rewindAt: DARK, resetAt: RESET, ...o })
  const sweepFrom = T0 + tRing
  const sweepTo = sweepFrom + weighty.durationMs

  const css = `
  .stage{animation:stage ${LOOP}ms infinite}
  @keyframes stage{
    0%,${at(HOLD, LOOP)}{opacity:1;animation-timing-function:${bez('Exit')}}
    ${at(DARK, LOOP)},${at(T0 - 10, LOOP)}{opacity:0}
    ${at(T0, LOOP)},100%{opacity:1}
  }

  .arc{animation:arc ${LOOP}ms infinite}
  @keyframes arc{
${R('stroke-dashoffset', { final: '0', start: round(CIRC, 1), from: sweepFrom, to: sweepTo, ease: springVar('Weighty') })}
  }
  .head-fade{animation:headFade ${LOOP}ms infinite}
  @keyframes headFade{
    0%,${at(sweepFrom, LOOP)}{opacity:0}
    ${at(sweepFrom + 40, LOOP)},${at(sweepTo, LOOP)}{opacity:1}
    ${at(T0 + tReveal, LOOP)},100%{opacity:0}
  }
  .head-spin{animation:headSpin ${LOOP}ms infinite;transform-origin:${cx}px ${cy}px}
  @keyframes headSpin{
${R('transform', { final: 'rotate(270deg)', start: 'rotate(-90deg)', from: sweepFrom, to: sweepTo, ease: springVar('Weighty') })}
  }

  .spark{transform:scale(0);animation:spark ${LOOP}ms infinite;transform-origin:${cx}px ${cy}px}
  @keyframes spark{
    0%,${at(T0, LOOP)}{transform:scale(0);animation-timing-function:${springVar('Lively')}}
    ${at(sweepFrom, LOOP)},${at(T0 + tReveal, LOOP)}{transform:scale(1);animation-timing-function:${bez('Enter')}}
    ${at(T0 + tReveal + D.Emphasized, LOOP)},100%{transform:scale(0)}
  }

  .reveal{animation:reveal ${LOOP}ms infinite;transform-origin:${cx}px ${cy}px}
  @keyframes reveal{
${R('transform', { final: 'scale(1)', start: 'scale(0)', from: T0 + tReveal, to: T0 + tReveal + D.Emphasized, ease: bez('Enter') })}
  }
  .mark{animation:mark ${LOOP}ms infinite;transform-origin:${cx}px ${cy}px}
  @keyframes mark{
${R('transform', { final: 'scale(1)', start: 'scale(.8)', from: T0 + tReveal, to: T0 + tReveal + lively.durationMs, ease: springVar('Lively') })}
  }

${letters
  .map(({ i }) => {
    const from = T0 + tLetters + STAGGER * Math.min(i, CAP)
    return `  .l${i}{animation:l${i} ${LOOP}ms infinite}
  @keyframes l${i}{
${R('opacity', { final: '1;transform:translateY(0)', start: `0;transform:translateY(${RISE}px)`, from, to: from + D.Standard, ease: bez('Enter') })}
  }`
  })
  .join('\n')}

${beats
  .map(
    ([, , t], i) => `  .b${i}{animation:b${i} ${LOOP}ms infinite}
  @keyframes b${i}{
${R('opacity', { final: '1', start: '.3', from: T0 + t, to: T0 + t + D.Quick })}
  }
  .d${i}{animation:d${i} ${LOOP}ms infinite}
  @keyframes d${i}{
${R('fill', { final: C.Primary, start: C.Outline, from: T0 + t, to: T0 + t + D.Quick })}
  }`,
  )
  .join('\n')}

  .play{transform:translateX(${RW}px);animation:play ${LOOP}ms linear infinite}
  @keyframes play{
${R('transform', { final: `translateX(${RW}px)`, start: 'translateX(0)', from: T0, to: T0 + tDone })}
  }
`

  const body = `<defs>
  <radialGradient id="amb"><stop offset="0" stop-color="${C.Primary}" stop-opacity=".07"/><stop offset="1" stop-color="${C.Primary}" stop-opacity="0"/></radialGradient>
  <radialGradient id="hd"><stop offset="0" stop-color="${C.Primary}" stop-opacity=".45"/><stop offset="1" stop-color="${C.Primary}" stop-opacity="0"/></radialGradient>
  <clipPath id="mask"><circle class="reveal" cx="${cx}" cy="${cy}" r="${round(MARK * 0.75, 1)}"/></clipPath>
</defs>
<g class="stage">
  <ellipse cx="${cx}" cy="${cy}" rx="250" ry="215" fill="url(#amb)"/>
  <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${C.OutlineVariant}" stroke-width="${stroke}"/>
  <circle class="arc" cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${C.Primary}" stroke-width="${stroke}"
          stroke-linecap="round" stroke-dasharray="${round(CIRC, 1)}" stroke-dashoffset="0"
          transform="rotate(-90 ${cx} ${cy})"/>
  <g class="head-fade" opacity="0"><g class="head-spin" transform="rotate(270 ${cx} ${cy})">
    <circle cx="${cx + r}" cy="${cy}" r="${stroke}" fill="url(#hd)"/>
  </g></g>
  <circle class="spark" cx="${cx}" cy="${cy}" r="7" fill="${C.Primary}"/>
  <g class="mark" clip-path="url(#mask)">
    <rect x="${mx}" y="${my}" width="${MARK}" height="${MARK}" rx="${round(MARK * 0.28, 1)}" fill="${C.Primary}"/>
    <path d="${boltPath}" fill="${C.OnPrimary}"/>
  </g>
  <g class="sans" font-size="${FS}" font-weight="700" text-anchor="middle">
${letters.map(({ ch, x, i }) => `    <text class="l${i}" x="${round(x, 1)}" y="288">${ch}</text>`).join('\n')}
  </g>

  <line x1="452" y1="46" x2="452" y2="296" stroke="${C.Divider}"/>
  <text class="sans eyebrow" x="${RX}" y="62">IGNITION — THE APP'S FIRST FRAME</text>
  <text class="sans dim" x="${RX}" y="90" font-size="12">MOTION-13 · an instrument powering up, from parts the app owns</text>
${beats
  .map(
    ([name, spec], i) => `  <g class="b${i}">
    <circle class="d${i}" cx="${RX + 5}" cy="${rowY(i)}" r="4.5" fill="${C.Primary}"/>
    <text class="sans" x="${RX + 22}" y="${rowY(i)}" font-size="15" font-weight="600">${name}</text>
    <text class="mono dim" x="${RX + RW}" y="${rowY(i)}" font-size="11.5" text-anchor="end">${spec}</text>
  </g>`,
  )
  .join('\n')}
  <rect x="${RX}" y="304" width="${RW}" height="3" rx="1.5" fill="${C.OutlineVariant}"/>
${beats.map(([, , t]) => `  <rect x="${round(RX + (t / tDone) * RW, 1)}" y="301" width="1.5" height="9" fill="${C.Outline}"/>`).join('\n')}
  <g class="play"><rect x="${RX - 1}" y="300" width="2.5" height="11" rx="1.25" fill="${C.Primary}"/></g>
  <text class="mono dim" x="${RX}" y="326" font-size="10.5">0 ms</text>
  <text class="mono dim" x="${RX + RW}" y="326" font-size="10.5" text-anchor="end">${tDone} ms · then it hands the ring to Today</text>
</g>`

  return svg({
    w: W,
    h: H,
    title: 'The Fuelled ignition',
    desc: `A lime spark ignites, sweeps the day ring, reveals the brand mark and raises the seven letters of "Fuelled" ${STAGGER} ms apart — beside the four beats of the choreography and their timings.`,
    css,
    body,
  })
}

// ══ 2. springs.svg — the three springs, integrated ═════════════════════════════════════════
//
// Each panel plots the actual step response and moves a puck along it, so "Weighty" and
// "Lively" stop being adjectives: you can see that one is critically damped and the other
// overshoots by a measured percentage, and read the settle time each produces.

function springsCard() {
  const W = 920
  const H = 300
  const LOOP = 2600
  const names = ['Settle', 'Weighty', 'Lively']
  const blurb = {
    Settle: 'Layout, the tab indicator, press feedback.',
    Weighty: 'The ring, the bars, the animated numbers.',
    Lively: 'The tick pop, tag pop-in, the goal hit.',
  }
  const PW = 280
  const PX = (i) => 20 + i * 300
  const PLOT = { x: 18, y: 116, w: 244, h: 78 }
  const HEAD = 0.18 // headroom above the target line, so overshoot has somewhere to go

  // Opens with the puck and the plot head at rest at the end of the curve; they fade out,
  // rewind unseen, and run again.
  const HOLD = 1200
  const DARK = 1350
  const RESET = 1400
  const T0 = 1500
  const R = (prop, o) => replay(prop, { loop: LOOP, rewindAt: DARK, resetAt: RESET, ...o })
  const css = names
    .map((n, i) => {
      const e = springEasing(springs[n])
      const seg = { from: T0, to: T0 + e.durationMs }
      const topY = round(-PLOT.h * (1 - HEAD), 2)
      return `  .px${i}{animation:px${i} ${LOOP}ms linear infinite}
  @keyframes px${i}{
${R('transform', { final: `translateX(${PLOT.w}px)`, start: 'translateX(0)', ...seg })}
  }
  .py${i}{animation:py${i} ${LOOP}ms infinite}
  @keyframes py${i}{
${R('transform', { final: `translateY(${topY}px)`, start: 'translateY(0)', ...seg, ease: springVar(n) })}
  }
  .puck${i}{animation:puck${i} ${LOOP}ms infinite}
  @keyframes puck${i}{
${R('transform', { final: 'translateX(212px)', start: 'translateX(0)', ...seg, ease: springVar(n) })}
  }
  .fade${i}{animation:fade${i} ${LOOP}ms infinite}
  @keyframes fade${i}{
    0%,${at(HOLD, LOOP)}{opacity:1;animation-timing-function:${bez('Exit')}}
    ${at(DARK, LOOP)},${at(T0 - 40, LOOP)}{opacity:0}
    ${at(T0, LOOP)},100%{opacity:1}
  }`
    })
    .join('\n')

  const body = names
    .map((n, i) => {
      const s = springs[n]
      const e = springEasing(s)
      const over = overshootPct(s)
      const px = PX(i)
      const bx = px + PLOT.x
      const by = PLOT.y
      const targetY = by + PLOT.h * HEAD
      // The response, plotted over its own settle time.
      const pts = []
      for (let k = 0; k <= 120; k += 1) {
        const p = k / 120
        const v = springValue(s, (p * e.durationMs) / 1000)
        pts.push(`${round(bx + p * PLOT.w, 2)} ${round(by + PLOT.h - v * PLOT.h * (1 - HEAD), 2)}`)
      }
      return `<g>
  <rect x="${px}" y="48" width="${PW}" height="232" rx="14" fill="${C.Surface}"/>
  <text class="sans" x="${px + 18}" y="76" font-size="16" font-weight="600">Springs.${n}</text>
  <text class="mono lime" x="${px + 18}" y="97" font-size="11">ζ ${s.dampingRatio} · k ${s.stiffness} · settles in ${e.durationMs} ms</text>
  <line x1="${bx}" y1="${round(targetY, 2)}" x2="${bx + PLOT.w}" y2="${round(targetY, 2)}" stroke="${C.Outline}" stroke-dasharray="3 3"/>
  <line x1="${bx}" y1="${by + PLOT.h}" x2="${bx + PLOT.w}" y2="${by + PLOT.h}" stroke="${C.OutlineVariant}"/>
  <polyline points="${pts.join(' ')}" fill="none" stroke="${C.Primary}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity=".55"/>
  <g class="fade${i}"><g class="px${i}" style="transform:translateX(${PLOT.w}px)"><g class="py${i}" style="transform:translateY(${round(-PLOT.h * (1 - HEAD), 2)}px)">
    <circle cx="${bx}" cy="${by + PLOT.h}" r="4.5" fill="${C.Primary}"/>
  </g></g></g>
  <rect x="${bx}" y="212" width="${PLOT.w}" height="4" rx="2" fill="${C.OutlineVariant}"/>
  <g class="fade${i}"><g class="puck${i}" style="transform:translateX(212px)">
    <rect x="${bx}" y="206" width="32" height="16" rx="8" fill="${C.Primary}"/>
  </g></g>
  <text class="sans dim" x="${px + 18}" y="248" font-size="12">${blurb[n]}</text>
  <text class="mono dim" x="${px + 18}" y="266" font-size="10.5">${s.dampingRatio === 1 ? 'critically damped' : `under-damped · +${round(over, 1)}% overshoot`}</text>
</g>`
    })
    .join('\n')

  return svg({
    w: W,
    h: H,
    title: "Fuelled's three springs",
    desc: names
      .map((n) => `${n}: damping ratio ${springs[n].dampingRatio}, stiffness ${springs[n].stiffness}, settling in ${settleMs(springs[n])} ms`)
      .join('. '),
    css: `  .eyebrow{font-size:10px}\n${css}`,
    body: `<text class="sans eyebrow" x="20" y="28">THE SPRINGS — INTEGRATED FROM THE TOKENS, NOT DRAWN BY HAND</text>\n${body}`,
  })
}

// ══ 3. easings.svg — the four curves, and what each one feels like ════════════════════════

function easingsCard() {
  const W = 920
  const H = 262
  const LOOP = 2200
  const SHOWN = 900 // slowed from the app's 120–400 ms so the curve is legible; labelled as such
  const list = [
    ['Standard', easings.Standard, 'colour and state swaps'],
    ['Enter', easings.Enter, 'anything arriving'],
    ['Exit', easings.Exit, 'anything leaving'],
    ['Linear', null, 'the wordmark timeline'],
  ]
  const PW = 208
  const PX = (i) => 20 + i * 224
  const PLOT = { x: 16, y: 116, w: 176, h: 72 }

  const HOLD = 700
  const DARK = 850
  const RESET = 900
  const T0 = 1000
  const R = (prop, o) => replay(prop, { loop: LOOP, rewindAt: DARK, resetAt: RESET, ...o })
  const css = list
    .map(([name, e], i) => {
      const tf = e ? `cubic-bezier(${e.map((n) => round(n, 3)).join(', ')})` : 'linear'
      const seg = { from: T0, to: T0 + SHOWN }
      return `  .ex${i}{animation:ex${i} ${LOOP}ms linear infinite}
  @keyframes ex${i}{
${R('transform', { final: `translateX(${PLOT.w}px)`, start: 'translateX(0)', ...seg })}
  }
  .ey${i}{animation:ey${i} ${LOOP}ms infinite}
  @keyframes ey${i}{
${R('transform', { final: `translateY(-${PLOT.h}px)`, start: 'translateY(0)', ...seg, ease: tf })}
  }
  .ep${i}{animation:ep${i} ${LOOP}ms infinite}
  @keyframes ep${i}{
${R('transform', { final: 'translateX(148px)', start: 'translateX(0)', ...seg, ease: tf })}
  }
  .ef${i}{animation:ef${i} ${LOOP}ms infinite}
  @keyframes ef${i}{
    0%,${at(HOLD, LOOP)}{opacity:1;animation-timing-function:${bez('Exit')}}
    ${at(DARK, LOOP)},${at(T0 - 40, LOOP)}{opacity:0}
    ${at(T0, LOOP)},100%{opacity:1}
  }`
    })
    .join('\n')

  const body = list
    .map(([name, e, use], i) => {
      const px = PX(i)
      const bx = px + PLOT.x
      const by = PLOT.y
      const pts = []
      for (let k = 0; k <= 60; k += 1) {
        const p = k / 60
        const v = e ? bezierY(e, p) : p
        pts.push(`${round(bx + p * PLOT.w, 2)} ${round(by + PLOT.h - v * PLOT.h, 2)}`)
      }
      return `<g>
  <rect x="${px}" y="48" width="${PW}" height="196" rx="14" fill="${C.Surface}"/>
  <text class="sans" x="${px + 16}" y="76" font-size="15" font-weight="600">Easings.${name}</text>
  <text class="mono lime" x="${px + 16}" y="96" font-size="10.5">${e ? `cubic-bezier(${e.map((n) => round(n, 2)).join(', ')})` : 'linear'}</text>
  <rect x="${bx}" y="${by}" width="${PLOT.w}" height="${PLOT.h}" fill="none" stroke="${C.OutlineVariant}"/>
  <polyline points="${pts.join(' ')}" fill="none" stroke="${C.Primary}" stroke-width="2" stroke-linecap="round" opacity=".55"/>
  <g class="ef${i}"><g class="ex${i}" style="transform:translateX(${PLOT.w}px)"><g class="ey${i}" style="transform:translateY(-${PLOT.h}px)">
    <circle cx="${bx}" cy="${by + PLOT.h}" r="4" fill="${C.Primary}"/>
  </g></g></g>
  <rect x="${bx}" y="206" width="${PLOT.w}" height="4" rx="2" fill="${C.OutlineVariant}"/>
  <g class="ef${i}"><g class="ep${i}" style="transform:translateX(148px)">
    <rect x="${bx}" y="200" width="28" height="16" rx="8" fill="${C.Primary}"/>
  </g></g>
  <text class="sans dim" x="${px + 16}" y="232" font-size="11.5">${use}</text>
</g>`
    })
    .join('\n')

  return svg({
    w: W,
    h: H,
    title: "Fuelled's four easing curves",
    desc: 'Standard, Enter, Exit and Linear, each plotted and replayed on a puck.',
    css,
    body: `<text class="sans eyebrow" x="20" y="26">THE CURVES — SHOWN AT ${SHOWN} MS SO THEY READ; THE APP RUNS THEM AT ${D.Quick}–${D.Emphasized} MS</text>\n${body}`,
  })
}

// ══ 4. stagger.svg — the arrival, and the cap that keeps a long list honest ════════════════

function staggerCard() {
  const W = 920
  const H = 336
  const LOOP = 2600
  const ROWS = 8
  const rowY = (i) => 78 + i * 30
  const items = ['Oats, 80 g', 'Greek yoghurt, 170 g', 'Banana', 'Chicken breast, 200 g', 'Rice, 250 g', 'Olive oil, 15 g', 'Whey, 30 g', 'Almonds, 25 g']

  // Opens on the arrived list, holds it, clears it on the Exit curve, then deals it again.
  const HOLD = 1200
  const CLEAR = 1400
  const T0 = 1450
  const css = `${Array.from({ length: ROWS }, (_, i) => {
    const from = T0 + STAGGER * Math.min(i, CAP)
    return `  .r${i}{animation:r${i} ${LOOP}ms infinite}
  @keyframes r${i}{
${replay('opacity', {
      loop: LOOP,
      final: '1;transform:translateY(0)',
      start: `0;transform:translateY(${RISE}px)`,
      rewindAt: HOLD,
      resetAt: CLEAR,
      from,
      to: from + D.Standard,
      ease: bez('Enter'),
      outEase: bez('Exit'),
    })}
  }`
  }).join('\n')}
`

  const rows = Array.from({ length: ROWS }, (_, i) => {
    const delay = STAGGER * Math.min(i, CAP)
    const capped = i > CAP
    return `  <g class="r${i}">
    <rect x="20" y="${rowY(i)}" width="536" height="26" rx="9" fill="${C.Surface}"/>
    <circle cx="38" cy="${rowY(i) + 13}" r="3.5" fill="${C.Primary}"/>
    <text class="sans" x="54" y="${rowY(i) + 13}" font-size="12.5">${items[i]}</text>
    <text class="mono ${capped ? 'lime' : 'dim'}" x="542" y="${rowY(i) + 13}" font-size="11" text-anchor="end">+${delay} ms${capped ? ' · capped' : ''}</text>
  </g>`
  }).join('\n')

  const RX = 604
  const lines = [
    'A list is dealt, not painted. Item i fades in',
    `and rises ${RISE} dp over ${D.Standard} ms on the Enter curve,`,
    `delayed i × ${STAGGER} ms.`,
    '',
    `Past item ${CAP} the delay stops growing. Without`,
    'that cap a 40-row day would spend 1.6 s',
    'arriving, and the stagger would read as lag.',
    '',
    'It fires once per entry into the composition',
    '— never on recomposition, never on scroll.',
  ]

  return svg({
    w: W,
    h: H,
    title: 'The arrival stagger',
    desc: `Eight list rows arriving ${STAGGER} ms apart, rising ${RISE} dp over ${D.Standard} ms, with the per-item delay capped at item ${CAP}.`,
    css,
    body: `<text class="sans eyebrow" x="20" y="30">THE ARRIVAL — MOTION-06</text>
<text class="sans dim" x="20" y="52" font-size="12">Modifier.enterRise(index) · every list in the app uses this one primitive</text>
${rows}
<line x1="580" y1="30" x2="580" y2="306" stroke="${C.Divider}"/>
<text class="sans eyebrow" x="${RX}" y="30">WHY THE CAP</text>
${lines.map((t, i) => (t ? `<text class="sans dim" x="${RX}" y="${58 + i * 17}" font-size="12">${t}</text>` : '')).filter(Boolean).join('\n')}
<text class="mono lime" x="${RX}" y="252" font-size="11">StaggerStepMs = ${STAGGER}</text>
<text class="mono lime" x="${RX}" y="270" font-size="11">StaggerCap    = ${CAP}</text>
<text class="mono lime" x="${RX}" y="288" font-size="11">EnterRise     = ${RISE}.dp</text>`,
  })
}

// ══ 5. nav.svg — the sliding indicator M3 does not ship ═══════════════════════════════════

function navCard() {
  const W = 920
  const H = 210
  const tabs = ['Today', 'Week', 'Meals', 'Training', 'Profile']
  const DWELL = 900
  const settle = springEasing(springs.Settle)
  // One extra dwell on tab 0 at the end, so the loop closes mid-dwell instead of snapping.
  const LOOP = DWELL * (tabs.length + 1)
  const BAR = { x: 20, y: 62, w: 880, h: NAV_H }
  const TW = BAR.w / tabs.length
  const IND = { w: 64, h: 32, top: 12 }

  const icons = [
    `<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M3 9.5h18"/><rect x="7" y="12.5" width="6.5" height="5" rx="1.2" fill="currentColor" stroke="none"/>`,
    `<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M3 9.5h18"/><circle cx="8" cy="13.5" r="1.3" fill="currentColor" stroke="none"/><circle cx="12" cy="13.5" r="1.3" fill="currentColor" stroke="none"/><circle cx="16" cy="13.5" r="1.3" fill="currentColor" stroke="none"/><circle cx="8" cy="17.6" r="1.3" fill="currentColor" stroke="none"/><circle cx="12" cy="17.6" r="1.3" fill="currentColor" stroke="none"/>`,
    `<path d="M6.6 3v5.4a2.2 2.2 0 0 0 4.4 0V3"/><path d="M8.8 10.6V21"/><path d="M17.4 3c1.9 2.4 1.9 6.4 0 8.3V21"/>`,
    `<path d="M4 9.5v5M7 7v10M17 7v10M20 9.5v5M7 12h10"/>`,
    `<circle cx="12" cy="8" r="3.6"/><path d="M5 20.2c0-3.9 3.1-6.3 7-6.3s7 2.4 7 6.3"/>`,
  ]

  // The indicator's stops, in loop order: 0, 1, 2, 3, 4, then home to 0 for the closing dwell.
  const stops = [...tabs.map((_, i) => i), 0]

  // Every track is built as an explicit (time, value) list and only then turned into
  // keyframes. The first cut wrote the move at the START of each dwell rather than the end —
  // the pill ran a tab ahead of the lit label for the whole loop — and left tab 0 with no
  // keyframe between its two lit windows, so its colour drifted across four seconds instead
  // of holding. Both are invisible in a still and obvious in a seek.
  const track = (frames, prop, tf) => {
    const seen = new Set()
    return frames
      .filter(([ms]) => !seen.has(round(ms)) && seen.add(round(ms)))
      .map(([ms, v, ease]) => `    ${at(ms, LOOP)}{${prop}:${v}${ease ? `;animation-timing-function:${tf}` : ''}}`)
      .join('\n')
  }

  const pillPts = [[0, `translateX(0px)`]]
  for (let k = 0; k + 1 < stops.length; k += 1) {
    const leave = (k + 1) * DWELL
    pillPts.push([leave, `translateX(${round(stops[k] * TW, 1)}px)`, true])
    pillPts.push([leave + settle.durationMs, `translateX(${round(stops[k + 1] * TW, 1)}px)`])
  }
  pillPts.push([LOOP, 'translateX(0px)'])
  const pillFrames = track(pillPts, 'transform', springVar('Settle'))

  const tabFrames = tabs
    .map((_, i) => {
      // Tab 0 is lit twice: the opening dwell and the closing one that carries the loop seam.
      const lit = i === 0 ? [[0, DWELL], [DWELL * tabs.length, LOOP]] : [[i * DWELL, (i + 1) * DWELL]]
      const ON = C.Primary
      const OFF = C.OnSurfaceVariant
      const pts = [[0, lit[0][0] === 0 ? ON : OFF]]
      lit.forEach(([a, b]) => {
        if (a > 0) {
          pts.push([a, OFF])
          pts.push([a + D.Quick, ON])
        }
        if (b < LOOP) {
          pts.push([b, ON])
          pts.push([b + D.Quick, OFF])
        } else {
          pts.push([LOOP, ON])
        }
      })
      if (pts[pts.length - 1][0] < LOOP) pts.push([LOOP, OFF])
      pts.sort((x, y) => x[0] - y[0])
      return `  .t${i}{animation:t${i} ${LOOP}ms infinite}\n  @keyframes t${i}{\n${track(pts, 'color')}\n  }`
    })
    .join('\n')

  const css = `  .cc{fill:currentColor}
  .ic{fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
  .pill{animation:pill ${LOOP}ms infinite}
  @keyframes pill{
${pillFrames}
  }
${tabFrames}`

  const body = `<text class="sans eyebrow" x="20" y="32">THE BOTTOM BAR — MOTION-05</text>
<text class="sans dim" x="20" y="52" font-size="12">Material 3's NavigationBar, ${BAR.h} dp, ripple and Tab role intact — with one indicator that SLIDES instead of five that cross-fade</text>
<rect x="${BAR.x}" y="${BAR.y}" width="${BAR.w}" height="${BAR.h}" rx="18" fill="${C.Surface}"/>
<g class="pill"><rect x="${round(BAR.x + TW / 2 - IND.w / 2, 1)}" y="${BAR.y + IND.top}" width="${IND.w}" height="${IND.h}" rx="${IND.h / 2}" fill="${C.NavIndicator}"/></g>
${tabs
  .map((label, i) => {
    const cxTab = BAR.x + TW * i + TW / 2
    return `<g class="t${i}" style="color:${i === 0 ? C.Primary : C.OnSurfaceVariant}">
  <g class="ic" transform="translate(${round(cxTab - 12, 1)} ${BAR.y + IND.top + 4})">${icons[i]}</g>
  <text class="sans cc" x="${round(cxTab, 1)}" y="${BAR.y + 60}" font-size="12" font-weight="500" text-anchor="middle">${label}</text>
</g>`
  })
  .join('\n')}
<text class="mono dim" x="20" y="${BAR.y + BAR.h + 26}" font-size="11">pill: Springs.Settle (ζ ${springs.Settle.dampingRatio} · k ${springs.Settle.stiffness} · ${settle.durationMs} ms) · icon and label: ${D.Quick} ms colour swap · the pill is DRAWN behind the bar, so it adds no node</text>`

  return svg({
    w: W,
    h: H,
    title: 'The bottom bar indicator',
    desc: `A lime pill sliding between the five tabs — ${tabs.join(', ')} — on the Settle spring, while the selected label and icon swap colour over ${D.Quick} ms.`,
    css,
    body,
  })
}

// ══ 6. durations.svg — the whole duration scale on one time axis ══════════════════════════

function durationsCard() {
  const W = 920
  const H = 316
  // Opens on the filled bars; they retract together, then refill at their true speeds.
  const HOLD = 1300
  const CLEAR = 1450
  const T0 = 1550
  const LOOP = 3200
  const rows = [
    ['Quick', D.Quick, 'press feedback, colour and state swaps, outgoing fades'],
    ['Standard', D.Standard, 'fades, rises, cross-fades, expand and collapse'],
    ['Emphasized', D.Emphasized, 'screen pushes and pops, tab fade-through'],
    ['Expressive', D.Expressive, "the ring's sweep, the bars' first fill, count-ups"],
    ['Celebration', D.Celebration, 'the goal bloom — once per logical day, nothing else'],
    ['ShimmerSweep', D.ShimmerSweep, 'the loading shimmer — the one loop in the app'],
  ]
  const MAXMS = Math.max(...rows.map((r) => r[1]))
  const X0 = 250
  const SCALE = 600 / MAXMS
  const rowY = (i) => 84 + i * 34

  const R = (prop, o) => replay(prop, { loop: LOOP, rewindAt: HOLD, resetAt: CLEAR, outEase: bez('Exit'), ...o })
  const css = `${rows
    .map(([, ms], i) => `  .bar${i}{animation:bar${i} ${LOOP}ms infinite;transform-origin:${X0}px ${rowY(i)}px}
  @keyframes bar${i}{
${R('transform', { final: 'scaleX(1)', start: 'scaleX(0)', from: T0, to: T0 + ms, ease: 'linear' })}
  }`)
    .join('\n')}
  .head{transform:translateX(600px);animation:head ${LOOP}ms infinite}
  @keyframes head{
${R('transform', { final: 'translateX(600px)', start: 'translateX(0)', from: T0, to: T0 + MAXMS, ease: 'linear' })}
  }`

  const ticks = [0, 300, 600, 900, 1200]
  const body = `<text class="sans eyebrow" x="20" y="30">THE DURATION SCALE — ONE TIME AXIS, EVERY BAR AT ITS TRUE SPEED</text>
<text class="sans dim" x="20" y="52" font-size="12">Six durations, six jobs. Nothing in the app animates for a length that is not one of these.</text>
${rows
  .map(([name, ms, use], i) => `<g>
  <text class="sans" x="20" y="${rowY(i) - 6}" font-size="13" font-weight="600">${name}</text>
  <text class="sans dim" x="20" y="${rowY(i) + 9}" font-size="10.5">${use}</text>
  <rect x="${X0}" y="${rowY(i) - 5}" width="${round(ms * SCALE, 1)}" height="10" rx="5" fill="${C.OutlineVariant}"/>
  <rect class="bar${i}" x="${X0}" y="${rowY(i) - 5}" width="${round(ms * SCALE, 1)}" height="10" rx="5" fill="${C.Primary}"/>
  <text class="mono dim" x="900" y="${rowY(i)}" font-size="11" text-anchor="end">${ms} ms</text>
</g>`)
  .join('\n')}
<line x1="${X0}" y1="288" x2="${X0 + 600}" y2="288" stroke="${C.OutlineVariant}"/>
${ticks
  .map((t) => `<line x1="${round(X0 + t * SCALE, 1)}" y1="284" x2="${round(X0 + t * SCALE, 1)}" y2="292" stroke="${C.Outline}"/>
<text class="mono dim" x="${round(X0 + t * SCALE, 1)}" y="304" font-size="10" text-anchor="middle">${t}</text>`)
  .join('\n')}
<g class="head"><rect x="${X0 - 1}" y="72" width="2" height="216" fill="${C.Primary}" opacity=".45"/></g>`

  return svg({
    w: W,
    h: H,
    title: 'The duration scale',
    desc: rows.map(([n, ms]) => `${n} ${ms} ms`).join(', ') + ' — drawn on a shared time axis and filled at their real speeds.',
    css,
    body,
  })
}

// ── Write ────────────────────────────────────────────────────────────────────────────────

console.log('motion tokens read from Tokens.kt / Theme.kt:')
console.log(
  `  durations ${Object.entries({ Quick: D.Quick, Standard: D.Standard, Emphasized: D.Emphasized, Expressive: D.Expressive, Celebration: D.Celebration })
    .map(([k, v]) => `${k}=${v}`)
    .join(' ')}`,
)
for (const [n, s] of Object.entries(springs)) {
  console.log(`  spring ${n.padEnd(8)} ζ=${s.dampingRatio} k=${s.stiffness} -> settles ${settleMs(s)} ms, overshoot ${round(overshootPct(s), 1)}%`)
}
// ══ 7. lab.html — the same six, on one page, with the numbers beside them ═════════════════
//
// The README can only show pictures. This is the page to open when you want the pictures AND
// the catalogue they came from. Every SVG is inlined, so the file works anywhere it lands.

function lab(svgs) {
  const rows = Object.entries({
    Quick: [D.Quick, 'press feedback, colour and state swaps, outgoing fades'],
    Standard: [D.Standard, 'fades, rises, cross-fades, expand and collapse'],
    Emphasized: [D.Emphasized, 'screen pushes and pops, tab fade-through'],
    Expressive: [D.Expressive, "the ring's sweep, the bars' first fill, count-ups"],
    Celebration: [D.Celebration, 'the goal bloom — once per logical day'],
    ShimmerSweep: [D.ShimmerSweep, 'the loading shimmer — the one loop in the app'],
  })
  const sections = [
    ['ignition', 'The ignition', 'The app\u2019s first frame. A spark, the day ring, the mark, then seven letters 40 ms apart — and the ring hands off to Today\u2019s hero ring as a shared element.'],
    ['durations', 'The duration scale', 'Six durations, six jobs, drawn on one time axis and filled at their real speeds. Nothing in the app animates for a length that is not one of these.'],
    ['easings', 'The curves', 'Three shaped curves and a linear one. Arriving uses Enter, leaving uses Exit; Standard carries state changes that neither arrive nor leave.'],
    ['springs', 'The springs', 'Not adjectives. Each panel plots the actual step response of a mass-1 oscillator at the token\u2019s damping ratio and stiffness, and moves a puck along it at the speed the app would.'],
    ['stagger', 'The arrival', 'One primitive behind every list in the app, with the cap that keeps a long day from crawling.'],
    ['nav', 'The bottom bar', 'Material 3\u2019s NavigationBar, kept whole — ripple, Tab role, 80 dp, equal-weight cells — with one indicator that slides instead of five that cross-fade.'],
  ]
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Fuelled Motion Layer</title>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=DM+Sans:wght@400;500;700&display=swap">
<style>
  :root{color-scheme:dark;--sans:"DM Sans",${SANS};--mono:"DM Mono",${MONO}}
  body{margin:0;background:${C.Background};color:${C.OnSurface};
       font-family:var(--sans);line-height:1.55;-webkit-font-smoothing:antialiased}
  svg .sans{font-family:var(--sans)}
  svg .mono{font-family:var(--mono)}
  .wrap{max-width:960px;margin:0 auto;padding:56px 20px 96px}
  h1{font-size:clamp(32px,6vw,52px);line-height:1.05;letter-spacing:-.03em;margin:0 0 16px}
  h2{font-size:22px;letter-spacing:-.02em;margin:64px 0 6px}
  p{margin:0 0 20px;color:${C.OnSurfaceVariant};max-width:68ch}
  .lede{font-size:17px;color:${C.OnSurfaceVariant};max-width:68ch;margin-bottom:8px}
  .eyebrow{font-size:11px;font-weight:700;letter-spacing:1.6px;text-transform:uppercase;color:${C.Primary};margin:0 0 12px}
  svg{display:block;width:100%;height:auto;margin:0 0 8px}
  table{border-collapse:collapse;width:100%;margin:8px 0 28px;font-size:14px;font-variant-numeric:tabular-nums}
  th,td{text-align:left;padding:9px 12px;border-bottom:1px solid ${C.Divider};vertical-align:top}
  th{font-size:11px;letter-spacing:1.2px;text-transform:uppercase;color:${C.OnSurfaceVariant};font-weight:700}
  h1,h2{text-wrap:balance}
  td.n{font-family:var(--mono);color:${C.Primary};white-space:nowrap}
  td.d{color:${C.OnSurfaceVariant}}
  .note{border-left:2px solid ${C.Primary};padding:2px 0 2px 16px;margin:28px 0;color:${C.OnSurfaceVariant};max-width:68ch}
  code{font-family:var(--mono);font-size:.92em;color:${C.OnSurface}}
  footer{margin-top:80px;padding-top:24px;border-top:1px solid ${C.Divider};color:${C.OnSurfaceVariant};font-size:13px}
  footer code{font-family:var(--mono)}
  @media (prefers-reduced-motion: reduce){*{animation:none !important}}
</style></head><body><div class="wrap">
<p class="eyebrow">Fuelled · the design system, moving</p>
<h1>Motion is a token, not a flourish.</h1>
<p class="lede">Six durations, four curves, three springs and one arrival stagger — declared in <code>Tokens.kt</code>, read through a scheme that honours reduced motion by construction, and enforced by a conformance gate the same way a hardcoded colour is.</p>
<div class="note">Every animation on this page was <strong>generated from those tokens</strong>. The springs are integrated from the same damped harmonic oscillator the Compose runtime integrates — same damping ratio, same stiffness, mass 1, and Compose&rsquo;s own 0.01 visibility threshold for when an animation is over — then emitted as sampled CSS <code>linear()</code> easings. These are not a drawing of the app&rsquo;s motion. They are its motion, replayed in a browser.</div>
${sections.map(([k, title, blurb]) => `<h2>${title}</h2>\n<p>${blurb}</p>\n${svgs[k].replace(/^<\?xml[^>]*>\n?/, '')}`).join('\n')}
<h2>The catalogue</h2>
<table><thead><tr><th>Duration</th><th>ms</th><th>What it carries</th></tr></thead><tbody>
${rows.map(([n, [ms, use]]) => `<tr><td>${n}</td><td class="n">${ms}</td><td class="d">${use}</td></tr>`).join('\n')}
</tbody></table>
<table><thead><tr><th>Spring</th><th>ζ · k</th><th>Settles</th><th>Overshoot</th></tr></thead><tbody>
${Object.entries(springs)
  .map(([n, sp]) => `<tr><td>${n}</td><td class="n">${sp.dampingRatio} · ${sp.stiffness}</td><td class="n">${settleMs(sp)} ms</td><td class="n">${overshootPct(sp) > 0.05 ? `+${round(overshootPct(sp), 1)}%` : '—'}</td></tr>`)
  .join('\n')}
</tbody></table>
<table><thead><tr><th>Curve</th><th>Control points</th><th>Used for</th></tr></thead><tbody>
${Object.entries(easings)
  .map(([n, e]) => `<tr><td>${n}</td><td class="n">${e.map((v) => round(v, 2)).join(', ')}</td><td class="d">${{ Standard: 'state changes that neither arrive nor leave', Enter: 'anything arriving', Exit: 'anything leaving', Linear: 'timelines the app drives itself' }[n] || ''}</td></tr>`)
  .join('\n')}
</tbody></table>
<h2>Reduced motion removes movement, not the moment</h2>
<p>Three schemes, one composition local. <strong>Full</strong> is everything on this page. <strong>Reduced</strong> keeps every state change and every beat, and takes away the travel — the ignition becomes a quick fade of the assembled mark, held ${IntroHoldNote} so the moment still lands. <strong>Instant</strong> is what tests and previews run under: every animation is over on frame 0, so a golden tree is deterministic.</p>
<p>This page honours it too. With <code>prefers-reduced-motion: reduce</code>, every figure above renders as its finished frame rather than its first one.</p>
<footer>Generated by <code>docs/assets/motion/generate.mjs</code> from <code>presentation/theme/Tokens.kt</code> and <code>Theme.kt</code>. Regenerate after any motion-token change.</footer>
</div></body></html>
`
}

console.log('writing:')
out('ignition.svg', ignition())
out('springs.svg', springsCard())
out('easings.svg', easingsCard())
out('stagger.svg', staggerCard())
out('nav.svg', navCard())
out('durations.svg', durationsCard())

// The lab page inlines the six it just wrote, so it stays a single portable file.
const inlined = Object.fromEntries(
  ['ignition', 'durations', 'easings', 'springs', 'stagger', 'nav'].map((k) => [
    k,
    readFileSync(join(HERE, `${k}.svg`), 'utf8'),
  ]),
)
writeFileSync(join(HERE, '..', '..', 'motion-lab.html'), lab(inlined))
console.log('  ../../motion-lab.html')
