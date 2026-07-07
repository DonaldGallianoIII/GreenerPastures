/* ============================================================
   GREENER PASTURES - Notebook console (real, data-wired)
   Built on Deuce's mockup aesthetic (mockups/GreenerPasturesNotebook.jsx),
   wired to the live data contract (NOTEBOOK_DATA_CONTRACT.md) via the bridge SDK.
   Viewport-sized window; every tab reads its channel; buttons send actions.
   ============================================================ */
import { useState, useEffect, useMemo, useRef, Fragment } from 'react'
import { useChannel, send, isMock } from './bridge.js'
import { CARD_POOL } from './carddeck.js'
import { HAPPY, SAD, VOLTORB_ANGRY, HAPPY_KEYS, SAD_KEYS } from './pmdsprites.js'
import { SCORBUNNY_SHEET } from './treelinesprites.js'

// MCEF forwards key events but NOT mouse-click modifiers, so ev.shiftKey is always false in-game. Track Shift via
// key events (which DO come through) and OR it into click handlers, so ⇧-click works both in-game and in a browser.
let SHIFT_HELD = false
if (typeof window !== 'undefined') {
  window.addEventListener('keydown', (e) => { if (e.key === 'Shift' || e.shiftKey) SHIFT_HELD = true })
  window.addEventListener('keyup', (e) => { if (e.key === 'Shift' || !e.shiftKey) SHIFT_HELD = false })
  window.addEventListener('blur', () => { SHIFT_HELD = false })
}
const shiftHeld = (ev) => (ev && ev.shiftKey) || SHIFT_HELD

const CSS = `
@import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap');
.gp-backdrop{
  --bg:#0c0f14; --bg2:#0a0d12; --panel:#161c25; --panel-hi:#1d2530; --inset:#0e131a; --slot:#080b10;
  --line:#2a3543; --line2:#212a36; --text:#e7eef6; --muted:#8593a4; --dim:#566273;
  --green:#4fd6a0; --amber:#ffb454; --cyan:#5cc8ff; --pink:#f2a7c4; --red:#ff6b6b; --pair:#d56bff;
  position:fixed; inset:0; display:grid; place-items:center; overflow:hidden;
  background:radial-gradient(circle at 28% 8%,#11161f,#06080c 72%);
  font-family:'Space Grotesk',system-ui,sans-serif; -webkit-font-smoothing:antialiased; color:var(--text); user-select:none;
}
.gp-backdrop *,.gp-backdrop *::before,.gp-backdrop *::after{ box-sizing:border-box; }
.gp-backdrop ::-webkit-scrollbar{ width:8px; height:8px; }
.gp-backdrop ::-webkit-scrollbar-track{ background:transparent; }
.gp-backdrop ::-webkit-scrollbar-thumb{ background:linear-gradient(180deg,#6cd2ff,#2f8fd0); border-radius:20px;
  box-shadow:0 0 6px rgba(92,200,255,.55),0 0 0 1px rgba(0,0,0,.35); }
.gp-backdrop ::-webkit-scrollbar-thumb:hover{ background:linear-gradient(180deg,#8fddff,#43a6e6); }
.gp-backdrop ::-webkit-scrollbar-corner{ background:transparent; }
.mono{ font-family:'JetBrains Mono',monospace; }
.gp-input{ flex:1; background:var(--inset); border:1px solid var(--line); border-radius:6px; color:var(--text);
  padding:6px 9px; font-family:'Space Grotesk',sans-serif; font-size:12px; outline:none; }
.gp-input:focus{ border-color:#2e5a47; }
.gp-input::placeholder{ color:var(--dim); }
.daemon-canvas{ position:relative; width:100%; height:340px; background:var(--inset); border:1px solid var(--line2); border-radius:8px; overflow:hidden; cursor:grab; }
.daemon-canvas:active{ cursor:grabbing; }
.daemon-view{ position:absolute; top:0; left:0; }
.daemon-wires{ position:absolute; top:0; left:0; width:5000px; height:5000px; overflow:visible; pointer-events:none; }
.daemon-hud{ position:absolute; bottom:6px; left:8px; font-size:9px; color:var(--dim); font-family:'JetBrains Mono',monospace; pointer-events:none; }
.daemon-wrap{ display:flex; flex-direction:column; gap:6px; }
.daemon-palette{ display:flex; gap:5px; flex-wrap:wrap; align-items:center; }
.dchip{ background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:3px 9px; font-size:11px; font-weight:600; cursor:pointer; }
.dchip:hover{ filter:brightness(1.25); }
.gnode{ position:absolute; background:var(--panel); border:1.5px solid var(--line); border-radius:8px; cursor:grab; box-shadow:0 4px 12px -4px rgba(0,0,0,.6); display:flex; }
.gnode.sel{ box-shadow:0 0 0 2px var(--cyan), 0 5px 14px -4px rgba(0,0,0,.7); }
.gnode-bar{ width:5px; border-radius:6px 0 0 6px; flex:none; }
.gnode-body{ flex:1; padding:4px 9px; min-width:0; display:flex; flex-direction:column; justify-content:center; gap:1px; }
.gnode-title{ font-size:12px; font-weight:600; color:var(--text); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.gnode-sub{ font-size:9px; color:var(--dim); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.gnode-x{ position:absolute; top:-7px; right:-7px; width:15px; height:15px; border-radius:50%; background:var(--panel); border:1px solid var(--line); color:var(--muted); font-size:8px; display:none; align-items:center; justify-content:center; cursor:pointer; z-index:4; }
.gnode:hover .gnode-x{ display:flex; }
.gnode-x:hover{ color:var(--red); border-color:var(--red); }
.gport{ position:absolute; width:12px; height:12px; border:2px solid var(--bg); cursor:crosshair; z-index:3; box-shadow:0 0 4px rgba(0,0,0,.5); }
.gport:hover{ filter:brightness(1.5); }
.dcfg{ position:absolute; right:8px; top:8px; width:190px; max-height:calc(100% - 16px); overflow:auto; background:rgba(18,22,28,.98); border:1px solid var(--line2); border-radius:8px; padding:9px; z-index:6; box-shadow:0 10px 28px -10px #000; }
.dcfg-h{ font-size:11px; font-weight:700; margin-bottom:7px; color:var(--cyan); display:flex; align-items:center; }
.dcfg-x{ margin-left:auto; cursor:pointer; color:var(--muted); }
.dcfg-x:hover{ color:var(--red); }
.dcfg-grid{ display:grid; grid-template-columns:1fr 1fr; gap:5px; }
.dcfg-stat{ display:flex; align-items:center; justify-content:space-between; font-size:10px; gap:4px; color:var(--muted); }
.dcfg-stat input{ width:46px; background:var(--inset); border:1px solid var(--line); border-radius:4px; color:var(--text); font-size:10px; padding:2px 4px; }
.dcfg-row{ display:flex; gap:5px; flex-wrap:wrap; }
.dcfg-natures{ display:flex; gap:4px; flex-wrap:wrap; }
.dchip2{ background:var(--inset); border:1px solid var(--line); border-radius:5px; padding:2px 7px; font-size:9px; cursor:pointer; color:var(--muted); }
.dchip2.on{ background:var(--cyan); color:#04222b; border-color:var(--cyan); font-weight:700; }
.egglog{ margin-top:8px; border:1px solid var(--line); border-radius:8px; background:var(--inset); padding:7px 9px; }
.egglog-h{ display:flex; align-items:center; gap:6px; font-size:11px; font-weight:600; margin-bottom:5px; }
.egglog-feed{ max-height:92px; overflow:auto; display:flex; flex-direction:column; gap:2px; }
.egglog-row{ font-size:10px; display:flex; gap:6px; align-items:center; }
.thread-tabs{ display:flex; gap:3px; align-items:center; flex-wrap:wrap; }
.ttab{ display:flex; align-items:center; gap:5px; background:var(--panel); border:1px solid var(--line); border-radius:7px; padding:3px 9px; font-size:11px; cursor:pointer; color:var(--muted); }
.ttab.on{ background:var(--inset); border-color:var(--cyan); color:var(--text); }
.tab-name{ background:transparent; border:none; color:var(--text); font-size:11px; font-weight:600; width:92px; outline:none; padding:0; }
.tab-x{ color:var(--dim); font-size:8px; }
.tab-x:hover{ color:var(--red); }
.tab-add{ background:transparent; border:1px dashed var(--line); border-radius:7px; color:var(--muted); font-size:11px; padding:3px 9px; cursor:pointer; }
.tab-add:disabled{ opacity:.4; cursor:default; }
.thread-roster{ display:flex; gap:4px; align-items:center; flex-wrap:wrap; }
.monchip{ background:var(--panel); border:1px solid var(--line); border-radius:5px; padding:2px 8px; font-size:10px; cursor:pointer; color:var(--muted); }
.monchip.here{ background:#173341; border-color:var(--cyan); color:var(--cyan); font-weight:600; }
.monchip.busy{ opacity:.5; }
.gnode.mon{ background:#1a2230; }
.pairlink{ stroke:#c9a227; stroke-width:2; stroke-dasharray:3 4; opacity:.55; pointer-events:none; }
.shinybadge{ display:flex; gap:10px; align-items:center; font-size:10px; }
.sb-on{ color:var(--pair); font-weight:700; }
.sb-off{ color:var(--dim); }
.mon-insp .iv-grid{ display:grid; grid-template-columns:repeat(3,1fr); gap:4px; margin-bottom:7px; }
.iv-cell{ background:var(--bg); border:1px solid var(--line); border-radius:5px; padding:3px 2px; display:flex; flex-direction:column; align-items:center; gap:1px; }
.iv-k{ font-size:8px; color:var(--muted); text-transform:uppercase; }
.iv-v{ font-size:13px; font-weight:700; font-family:'JetBrains Mono',monospace; line-height:1; }
.insp-row{ display:flex; justify-content:space-between; font-size:11px; padding:2px 0; color:var(--text); }
.dcfg-drag{ cursor:move; user-select:none; }
.cell-full{ opacity:.42; cursor:not-allowed; }
.tab-badge{ display:inline-block; margin-left:5px; background:var(--cyan); color:#04222b; border-radius:8px; padding:0 5px; font-size:9px; font-weight:800; line-height:14px; vertical-align:1px; }
.hstrip{ display:flex; flex-wrap:wrap; gap:5px; margin-bottom:8px; }
.tl-cab{ display:flex; flex-direction:column; align-items:flex-start; gap:4px; width:240px; padding:16px 14px;
  border:1px solid var(--line2); border-radius:10px; background:var(--inset); color:var(--text);
  cursor:pointer; transition:all .15s; text-align:left; }
.tl-cab:hover{ border-color:var(--grn); box-shadow:0 0 14px rgba(70,200,120,.18); transform:translateY(-2px); }
.tl-cab-name{ font-size:16px; font-weight:700; letter-spacing:2px; color:var(--grn); }
.tl-cab-sub{ font-size:10px; color:var(--dim); }
.gc-shop{ width:min(560px,96%); display:flex; flex-direction:column; gap:8px; }
.gc-shop-head{ display:flex; align-items:baseline; gap:10px; }
.gc-offers{ display:grid; grid-template-columns:repeat(3,1fr); gap:8px; }
.gc-offer{ display:flex; flex-direction:column; gap:3px; padding:9px 10px; border:1px solid var(--line2);
  border-radius:8px; background:var(--inset); }
.gc-offer-name{ font-size:11px; color:var(--text); }
.gc-offer-art{ height:26px; display:flex; align-items:center; }
.gc-offer-art img{ width:24px; height:24px; image-rendering:pixelated; }
.gc-offer-noart{ color:var(--dim); font-size:16px; }
.td-table{ position:relative; width:648px; max-width:100%; }
.td-board{ position:relative; width:100%; height:416px; border:1px solid var(--line2); border-radius:10px;
  background:linear-gradient(180deg,#0a1218 0%,#0c151d 100%); overflow:hidden; }
.td-deck{ position:absolute; top:6px; right:12px; width:40px; height:46px; z-index:30; }
.td-deck-card{ position:absolute; inset:0; border:1px solid var(--cyan); border-radius:5px; background:var(--inset);
  display:flex; align-items:center; justify-content:center; color:var(--cyan); font-size:13px;
  box-shadow:0 2px 8px rgba(0,0,0,.5); }
.td-deck-count{ position:absolute; bottom:-16px; width:100%; text-align:center; font-size:9px; color:var(--dim); }
.td-card{ position:absolute; width:110px; height:80px; border:1px solid var(--line2); border-radius:8px;
  background:var(--inset); display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px;
  padding:4px; cursor:default; transition:transform .85s cubic-bezier(.45,1.25,.4,1) var(--dly,0s), opacity .7s ease var(--dly,0s); }
.td-card img{ width:44px; height:44px; image-rendering:pixelated; }
.td-card-name{ font-size:9px; color:var(--muted); letter-spacing:.5px; }
.td-in-deck{ transform:translate(var(--dx),var(--dy)) rotate(6deg) scale(.16); opacity:0; }
.td-return .td-card{ transition:transform 1.25s ease-in var(--rdly,0s), opacity 1s ease .25s; }
.td-pickable{ cursor:pointer; }
.td-pickable:hover{ border-color:var(--cyan); }
.td-sel{ border-color:var(--cyan); box-shadow:0 0 0 2px var(--cyan), 0 0 12px rgba(92,200,255,.35); }
.td-back-face{ display:flex; align-items:center; justify-content:center; width:100%; height:100%; color:var(--cyan);
  font-size:22px; opacity:.75; }
.td-hit{ border-color:var(--grn); box-shadow:0 0 0 2px var(--grn), 0 0 14px rgba(70,200,120,.4); }
.td-bust{ border-color:#ff6b81; box-shadow:0 0 0 2px #ff6b81, 0 0 14px rgba(255,107,129,.4); }
.td-chips{ display:flex; gap:6px; align-items:center; }
.td-chip{ padding:5px 11px; border:1px solid var(--line2); border-radius:14px; background:var(--inset);
  color:var(--muted); font-size:11px; font-weight:700; cursor:pointer; }
.td-chip.on{ border-color:var(--cyan); color:var(--cyan); box-shadow:0 0 8px rgba(92,200,255,.25); }
.td-chip:disabled{ opacity:.35; cursor:default; }
.td-ladder{ display:flex; gap:8px; font-size:10px; color:var(--dim); align-items:center; }
.td-rung{ padding:2px 8px; border:1px solid var(--line2); border-radius:5px; }
.td-rung.on{ border-color:var(--cyan); color:var(--cyan); font-weight:700; }
.td-rung.done{ border-color:var(--grn); color:var(--grn); }
.sl-machine{ display:flex; flex-direction:column; align-items:center; gap:12px; padding:18px 22px;
  border:1px solid var(--line2); border-radius:12px; background:linear-gradient(180deg,#10161f 0%,#0c1118 100%); }
.sl-reels{ display:flex; gap:10px; }
.sl-reel{ width:92px; height:92px; border:1px solid var(--line2); border-radius:9px; background:var(--slot);
  display:flex; align-items:center; justify-content:center; overflow:hidden; position:relative; }
.sl-reel img{ width:64px; height:64px; image-rendering:pixelated; }
.sl-reel-spin img{ filter:blur(2px) brightness(1.1); }
.sl-reel-hit{ border-color:var(--grn); box-shadow:0 0 12px rgba(70,200,120,.4); }
.sl-reel-jack{ border-color:var(--amber); box-shadow:0 0 16px rgba(255,180,84,.55); animation:tlBlink .5s steps(2) 6; }
.sl-pay{ display:grid; grid-template-columns:auto auto; gap:3px 14px; font-size:10px; color:var(--muted);
  border:1px solid var(--line2); border-radius:8px; padding:8px 12px; background:var(--inset); }
.sl-pay-row{ display:flex; align-items:center; gap:4px; }
.sl-pay img{ width:16px; height:16px; image-rendering:pixelated; }
.sl-pay-x{ color:var(--cyan); font-weight:700; margin-left:auto; }
.gc-offer-row{ display:flex; align-items:center; justify-content:space-between; margin-top:2px; }
.gc-price{ font-size:11px; font-weight:700; color:var(--amber); }
.tl-console{ width:min(860px,97%); margin:0 auto; padding:12px; border:1px solid var(--line2); border-radius:10px;
  background:var(--inset); display:flex; flex-direction:column; gap:8px; color:var(--text); }
.tl-head{ display:flex; align-items:flex-end; justify-content:space-between; gap:10px;
  border-bottom:1px solid var(--line2); padding-bottom:7px; }
.tl-title{ display:flex; flex-direction:column; line-height:1; }
.tl-title-main{ font-size:17px; font-weight:700; letter-spacing:3px; color:var(--grn); }
.tl-title-sub{ font-size:9px; color:var(--dim); letter-spacing:1px; margin-top:3px; }
.tl-meters{ display:flex; gap:16px; align-items:flex-end; }
.tl-meter{ display:flex; flex-direction:column; align-items:flex-end; line-height:1; }
.tl-meter-label{ font-size:8px; color:var(--dim); margin-bottom:3px; letter-spacing:1px; }
.tl-meter-val{ font-size:16px; font-weight:700; color:var(--amber); font-variant-numeric:tabular-nums; }
.tl-pips{ display:flex; gap:3px; }
.tl-pip{ width:9px; height:14px; background:var(--bg); border:1px solid var(--line2); border-radius:2px; }
.tl-pip-on{ background:var(--cyan); box-shadow:0 0 5px rgba(92,200,255,.5); }
.tl-screen{ width:100%; aspect-ratio:16/8; overflow:hidden; border:1px solid var(--line2); border-radius:7px;
  background:var(--bg); position:relative; }
.tl-strip{ display:flex; width:200%; height:100%; transform:translateX(0); transition:transform 1.25s cubic-bezier(0.55,0.05,0.25,1); }
.tl-strip-panned{ transform:translateX(-50%); }
.tl-map{ width:50%; height:100%; position:relative; overflow:hidden; }
.tl-meadow{ background:linear-gradient(180deg, #0d1512 0%, #101b14 100%); }
.tl-grass{ position:absolute; inset:0; }
.tl-blade{ position:absolute; color:var(--dim); font-size:13px; opacity:.55; line-height:1; }
.tl-edge-fade{ position:absolute; top:0; right:0; bottom:0; width:12%;
  background:linear-gradient(90deg, rgba(70,200,120,0) 0%, rgba(70,200,120,.07) 100%); border-right:2px dashed var(--line2); }
.tl-critter-pos{ position:absolute; width:30px; height:38px; transform:translate(-50%,-50%);
  transition:left 1.35s cubic-bezier(0.5,0,0.9,0.6), top 1.35s ease-in; }
.tl-idle{ animation:tlIdle 2.6s ease-in-out infinite; }
@keyframes tlIdle{ 0%,100%{ margin-top:0; } 50%{ margin-top:-2px; } }
.tl-flee{ left:112% !important; top:var(--exitY) !important; }
.tl-bun{ display:block; width:100%; aspect-ratio:24/40; background-repeat:no-repeat; background-size:400% 800%;
  background-position:0% calc(var(--row,0) * 14.2857%); image-rendering:pixelated;
  filter:drop-shadow(0 0 4px rgba(120,255,170,.5)); }
.tl-bun-walk{ animation:tlWalk var(--spd,0.6s) steps(4) infinite; }
@keyframes tlWalk{ from{ background-position-x:0%; } to{ background-position-x:133.3333%; } }
.tl-bun-dim{ filter:saturate(.15) brightness(.7); }
.tl-forest{ background:linear-gradient(180deg, #0a120e 0%, #0e1712 100%); }
.tl-tree{ position:absolute; width:calc(46px * var(--sc)); height:calc(60px * var(--sc));
  transform:translate(-50%,-78%); background:transparent; border:none; padding:0; cursor:pointer; }
.tl-tree:disabled{ cursor:default; }
.tl-tree-inner{ display:block; width:100%; height:100%; transition:filter .15s; }
.tl-tree:not(:disabled):hover .tl-tree-inner{ filter:drop-shadow(0 0 6px rgba(92,200,255,.55)) brightness(1.3); }
.tl-tree:not(:disabled):active .tl-tree-inner{ animation:tlRustle .35s linear; }
.tl-searched .tl-tree-inner{ opacity:.3; }
@keyframes tlRustle{ 0%,100%{ transform:rotate(0);} 25%{ transform:rotate(-4deg);} 50%{ transform:rotate(3.5deg);} 75%{ transform:rotate(-2.5deg);} }
.tl-puff{ position:absolute; top:12%; left:50%; transform:translateX(-50%); color:var(--dim); font-size:13px;
  animation:tlFade .9s ease-out forwards; }
@keyframes tlFade{ from{ opacity:1; } to{ opacity:.35; } }
.tl-popout{ position:absolute; top:-16%; left:50%; width:56%; height:52%; transform:translateX(-50%);
  animation:tlPop .35s cubic-bezier(0.3,1.6,0.5,1); }
@keyframes tlPop{ from{ transform:translateX(-50%) translateY(60%) scale(.4); opacity:0; }
  to{ transform:translateX(-50%) translateY(0) scale(1); opacity:1; } }
.tl-arrow{ position:absolute; top:-55%; left:50%; transform:translateX(-50%); font-size:19px; font-weight:700;
  color:var(--cyan); text-shadow:0 0 7px rgba(92,200,255,.85); animation:tlArrowPulse .9s ease-in-out infinite; }
@keyframes tlArrowPulse{ 0%,100%{ opacity:1; margin-top:0; } 50%{ opacity:.55; margin-top:-3px; } }
.tl-found{ filter:drop-shadow(0 0 9px rgba(140,255,190,.9)); }
.tl-taunt{ animation:tlTaunt 1.1s ease-in forwards; }
@keyframes tlTaunt{ 0%{ transform:translateX(-50%) translateY(60%) scale(.4); opacity:0; }
  30%{ transform:translateX(-50%) translateY(0) scale(1); opacity:1; }
  60%{ transform:translateX(-50%) translateY(-9px) scale(1); }
  100%{ transform:translateX(300%) translateY(-13px) scale(.8); opacity:0; } }
.tl-log{ min-height:24px; padding:5px 9px; border:1px solid var(--line2); border-radius:6px; background:var(--bg);
  font-size:11px; display:flex; align-items:center; gap:5px; color:var(--muted); }
.tl-log-prompt{ color:var(--dim); }
.tl-log-win{ color:var(--grn); }
.tl-log-lose{ color:#ff6b81; border-color:#5a2432; }
.tl-controls{ display:flex; align-items:center; gap:8px; }
.tl-watch{ font-size:12px; font-weight:700; color:var(--grn); letter-spacing:1px; animation:tlBlink .9s steps(2) infinite; }
.tl-watch-dim{ color:var(--dim); animation:none; font-weight:400; font-size:11px; }
@keyframes tlBlink{ 50%{ opacity:.35; } }
.vf-wrap{ display:flex; flex-direction:column; align-items:center; gap:10px; padding-top:6px; }
.vf-board{ display:grid; grid-template-columns:repeat(6, 56px); grid-template-rows:repeat(6, 56px); gap:6px; }
.vf-tile{ position:relative; width:56px; height:56px; border-radius:8px; border:1px solid var(--line2);
  background:linear-gradient(145deg,#1c2a22,#12201a); display:flex; align-items:center; justify-content:center;
  cursor:pointer; user-select:none; transition: transform .12s ease, background .12s ease; }
.vf-tile:hover{ transform:scale(1.06); border-color:var(--grn); }
.vf-tile.flip{ cursor:default; background:var(--inset); transform:none; }
.vf-tile.flip:hover{ transform:none; border-color:var(--line2); }
.vf-tile.volt{ background:linear-gradient(145deg,#3a1620,#26101a); border-color:#6e2635; }
.vf-tile.reveal{ opacity:.42; }
.vf-tile img{ width:44px; height:44px; image-rendering:pixelated; }
.vf-badge{ position:absolute; right:2px; bottom:2px; background:#0e1512e0; border:1px solid var(--amber);
  color:var(--amber); font-size:11px; font-weight:700; border-radius:5px; padding:0 3px; line-height:1.35; }
.vf-chip{ width:56px; height:56px; border-radius:8px; background:var(--inset); border:1px dashed var(--line2);
  display:flex; flex-direction:column; align-items:center; justify-content:center; font-size:11px; line-height:1.25; cursor:default; }
.vf-chip b{ color:var(--text); font-size:13px; }
.vf-chip span{ font-weight:600; display:flex; align-items:center; gap:2px; }
.vf-chip span img{ width:12px; height:12px; image-rendering:pixelated; }
.hflag{ display:flex; align-items:center; gap:5px; background:rgba(122,92,30,.16); border:1px solid #5a4a2e; color:var(--amber); border-radius:6px; padding:3px 8px; font-size:10px; }
.pbadge{ background:rgba(122,92,30,.3); border:1px solid #5a4a2e; color:var(--amber); border-radius:8px; padding:0 5px; font-size:9px; font-weight:800; line-height:14px; }
.kload{ display:flex; gap:5px; flex-wrap:wrap; align-items:center; }
.kchip{ background:var(--inset); border:1px solid var(--line); border-radius:5px; padding:2px 7px; font-size:9px; color:var(--cyan); font-family:'JetBrains Mono',monospace; }
.nat-grid{ display:grid; grid-template-columns:repeat(3,1fr); gap:4px; }
.nat-grid .dchip2{ display:flex; flex-direction:column; align-items:center; gap:1px; padding:4px 2px; }
.nat-fx{ font-size:8px; opacity:.75; }
.ball-grid{ display:grid; grid-template-columns:repeat(2,1fr); gap:4px; max-height:230px; overflow:auto; }
.ev-row{ display:flex; align-items:center; gap:6px; font-size:10px; color:var(--muted); margin-bottom:5px; }
.ev-row input[type=range]{ flex:1; accent-color:var(--cyan); }
.ev-row input[type=number]{ width:44px; background:var(--inset); border:1px solid var(--line); border-radius:4px; color:var(--text); font-size:10px; padding:2px 4px; }
.evbar{ height:6px; border-radius:3px; background:var(--inset); overflow:hidden; margin:6px 0; }
.evbar>div{ height:100%; background:var(--cyan); transition:width .15s; }
.augval{ font-family:'JetBrains Mono',monospace; font-size:10px; color:var(--cyan); margin-right:6px; }
.note{ display:flex; align-items:center; gap:8px; background:var(--inset); border:1px solid var(--line); border-radius:8px; padding:7px 10px; }
.note-ic{ font-size:14px; flex:none; }
.note-tx{ font-size:12px; color:var(--text); flex:1; }
.note-x{ color:var(--dim); cursor:pointer; font-size:10px; flex:none; padding:2px 4px; }
.note-x:hover{ color:var(--red); }
.sb-bad{ color:var(--red); font-weight:600; }
.loadsplash{ display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; padding:52px 0; color:var(--muted); font-size:13px; }
.spinner{ width:30px; height:30px; border:3px solid var(--line); border-top-color:var(--cyan); border-radius:50%; animation:gpspin .8s linear infinite; }
@keyframes gpspin{ to{ transform:rotate(360deg); } }
.statrow{ display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap; }
.stat{ flex:1; min-width:78px; background:var(--inset); border:1px solid var(--line); border-radius:8px; padding:8px 10px; }
.stat-v{ font-size:20px; font-weight:700; font-family:'JetBrains Mono',monospace; line-height:1; }
.stat-l{ font-size:9px; color:var(--muted); text-transform:uppercase; letter-spacing:.5px; margin-top:4px; }
.tbar-row{ display:flex; align-items:center; gap:6px; font-size:10px; margin-bottom:3px; }
.tbar-k{ width:54px; color:var(--muted); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.tbar-track{ flex:1; height:9px; background:var(--bg); border-radius:5px; overflow:hidden; }
.tbar-fill{ height:100%; background:var(--cyan); border-radius:5px; }
.tbar-v{ width:28px; text-align:right; color:var(--text); font-family:'JetBrains Mono',monospace; }
.goalbox{ background:var(--inset); border:1px solid var(--line2); border-radius:8px; padding:9px 11px; margin-bottom:10px; }
.goal-bar{ height:8px; background:var(--bg); border-radius:5px; overflow:hidden; }
.goal-fill{ height:100%; background:linear-gradient(90deg,var(--amber),var(--green)); border-radius:5px; transition:width .3s; }
.goal-form{ display:grid; grid-template-columns:1fr 1fr; gap:7px; margin-top:8px; }
.goal-form label{ display:flex; flex-direction:column; gap:2px; font-size:9px; color:var(--muted); text-transform:uppercase; letter-spacing:.4px; }
.goal-form input, .goal-form select{ background:var(--bg); border:1px solid var(--line); border-radius:5px; color:var(--text); font-size:12px; padding:3px 6px; }
.goal-form button{ grid-column:1 / -1; }
.dwire{ stroke-width:2.5; fill:none; pointer-events:stroke; cursor:pointer; }
.dwire:hover{ stroke:var(--red) !important; }
.dwire.live{ stroke:var(--cyan); stroke-dasharray:5 4; }
.dnode{ position:absolute; width:92px; height:34px; background:var(--panel); border:1px solid var(--line); border-radius:7px;
  display:flex; align-items:center; padding:0 8px; cursor:grab; box-shadow:0 4px 12px -4px rgba(0,0,0,.6); }
.dnode-name{ font-size:12px; font-weight:500; flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.dport{ width:13px; height:13px; border-radius:50%; border:2px solid var(--bg); cursor:crosshair; flex:none; box-shadow:0 0 5px rgba(0,0,0,.5); }

.gp-stage{ position:relative; width:1180px; height:724px;
  transform:scale(var(--gp-scale,1)); transform-origin:center center; }
.gp-win{ width:100%; height:100%; background:var(--bg); border:1px solid var(--line);
  border-radius:14px; overflow:hidden; display:flex; flex-direction:column;
  box-shadow:0 44px 130px -30px rgba(0,0,0,.85),0 0 0 1px rgba(255,255,255,.02) inset; }

.gp-title{ height:42px; flex:none; display:flex; align-items:center; gap:9px; padding:0 14px;
  background:linear-gradient(180deg,#12171f,#0d1219); border-bottom:1px solid var(--line); }
.dot{ width:11px; height:11px; border-radius:50%; }
.gp-fname{ font-family:'JetBrains Mono',monospace; font-size:12px; font-weight:600; margin-left:4px; }
.gp-sub{ font-size:11px; color:var(--dim); }
.gp-conn{ margin-left:auto; display:flex; align-items:center; gap:7px; font-family:'JetBrains Mono',monospace;
  font-size:11px; color:var(--muted); }
.gp-cdot{ width:8px; height:8px; border-radius:50%; box-shadow:0 0 9px currentColor; animation:pulse 2s ease-in-out infinite; }
@keyframes pulse{ 50%{ opacity:.4; } }
.gp-x{ width:16px; height:16px; display:grid; place-items:center; color:var(--dim); cursor:pointer; border-radius:5px; }
.gp-x:hover{ background:#2a1620; color:var(--red); }

.gp-tabs{ height:36px; flex:none; display:flex; gap:3px; padding:5px 10px 0; background:var(--bg2); border-bottom:1px solid var(--line); }
.gp-tab{ display:flex; align-items:center; padding:0 14px; font-size:12px; font-weight:500; color:var(--muted);
  cursor:pointer; border-radius:8px 8px 0 0; border:1px solid transparent; border-bottom:none; background:transparent; }
.gp-tab:hover{ color:var(--text); }
.gp-tab.on{ color:var(--text); background:var(--bg); border-color:var(--line); }

.gp-cmd{ height:28px; flex:none; display:flex; align-items:center; gap:8px; padding:0 14px; background:var(--inset);
  border-bottom:1px solid var(--line2); font-family:'JetBrains Mono',monospace; font-size:11px; color:var(--muted); }
.gp-caret{ width:6px; height:13px; background:var(--green); animation:blink 1s steps(1) infinite; }
@keyframes blink{ 50%{ opacity:0; } }

.gp-body{ flex:1; position:relative; overflow:hidden; background:var(--bg); }
.pane{ position:absolute; inset:0; overflow:auto; padding:14px; }
.pane.split{ display:flex; gap:12px; padding:12px; }

.gp-foot{ height:30px; flex:none; display:flex; align-items:center; gap:14px; padding:0 14px;
  background:linear-gradient(180deg,#0d1219,#0a0e13); border-top:1px solid var(--line);
  font-family:'JetBrains Mono',monospace; font-size:11px; }
.gp-foot .sp{ flex:1; }

.h{ font-family:'JetBrains Mono',monospace; font-size:10px; letter-spacing:1px; text-transform:uppercase; color:var(--dim); }
.muted{ color:var(--muted); } .dim{ color:var(--dim); } .grn{ color:var(--green); } .amb{ color:var(--amber); } .cyn{ color:var(--cyan); }
.row{ display:flex; align-items:center; gap:8px; }
.inset{ background:var(--inset); border:1px solid var(--line); border-radius:10px; }
.empty{ position:absolute; inset:0; display:grid; place-items:center; text-align:center; color:var(--muted); padding:24px; }
.empty b{ display:block; color:var(--green); margin-bottom:6px; font-weight:600; }
.btn{ font-family:'JetBrains Mono',monospace; font-size:11px; color:var(--text); background:var(--panel-hi);
  border:1px solid var(--line); border-radius:7px; padding:4px 10px; cursor:pointer; }
.btn:hover:not(:disabled){ border-color:#3a4960; } .btn:disabled{ opacity:.35; cursor:not-allowed; }
.btn.go{ color:var(--green); border-color:#2e5a47; } .btn.warn{ color:var(--amber); border-color:#5a4a2e; }
.chip{ font-family:'JetBrains Mono',monospace; font-size:10px; padding:1px 6px; border-radius:5px;
  background:var(--slot); border:1px solid var(--line); color:var(--muted); }

.acc{ display:flex; flex-direction:column; gap:5px; }
.sphead{ display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:9px; cursor:pointer;
  background:var(--slot); border:1px solid var(--line); }
.sphead.on{ background:var(--panel-hi); }
.spname{ font-weight:600; }
.card{ margin:4px 0 8px 14px; padding:8px 10px; border-left:2px solid var(--line); }
.stat{ display:flex; gap:6px; flex-wrap:wrap; margin-top:4px; align-items:center; }

.grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(64px,1fr)); gap:7px; }
.cell{ aspect-ratio:1; border-radius:9px; background:var(--slot); border:1px solid var(--line); cursor:pointer;
  display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; padding:4px; text-align:center; }
.cell:hover{ border-color:#3a4960; }
.cell .nm{ font-size:9px; color:var(--muted); line-height:1.1; word-break:break-word; }
.cell .ct{ font-family:'JetBrains Mono',monospace; font-size:12px; color:var(--text); }

.plist{ width:200px; flex:none; overflow:auto; padding:8px; }
.pitem{ display:flex; align-items:center; gap:6px; padding:7px 9px; border-radius:8px; cursor:pointer; margin-bottom:4px;
  background:var(--slot); border:1px solid var(--line); }
.pitem.on{ background:var(--panel-hi); border-color:#3a4960; }
.pdetail{ flex:1; overflow:auto; padding:12px; }
.pair{ padding:6px 9px; border-radius:7px; background:var(--slot); border:1px solid var(--line); margin-bottom:5px;
  font-family:'JetBrains Mono',monospace; font-size:11px; }

.trip{ display:flex; gap:12px; height:100%; padding:12px; }
.tcol{ display:flex; flex-direction:column; gap:8px; padding:12px; overflow:auto; }
.brow{ display:flex; align-items:center; gap:8px; padding:5px 8px; border-radius:8px; background:var(--slot); border:1px solid var(--line); }
.brow.on{ background:var(--panel-hi); }
.step{ width:20px; height:20px; border-radius:6px; border:1px solid var(--line); background:var(--panel-hi); color:var(--text);
  cursor:pointer; font-family:'JetBrains Mono',monospace; font-size:12px; }
.step:disabled{ opacity:.3; cursor:not-allowed; }
.pip{ width:9px; height:9px; border-radius:2px; background:var(--dim); } .pip.on{ background:var(--green); }
.power{ padding:8px; border-radius:9px; font-weight:600; font-size:13px; cursor:pointer; border:1px solid; width:100%; }
.power.on{ color:var(--muted); background:#201217; border-color:#5a2e3a; }
.power.off{ color:var(--green); background:#0e2417; border-color:#2e5a47; }

.dgrid{ display:grid; grid-template-columns:repeat(2,1fr); gap:12px; }
.dcard{ background:linear-gradient(180deg,#11161e,#0e131a); border:1px solid var(--line); border-radius:12px; padding:10px; }

.gp-invwin{ position:absolute; bottom:14px; right:14px; z-index:60; width:270px;
  background:linear-gradient(180deg,#12171f,#0d1219); border:1px solid var(--line); border-radius:10px;
  box-shadow:0 24px 70px -24px rgba(0,0,0,.85); padding:8px; }
.gp-invwin .hd{ display:flex; align-items:center; gap:6px; margin-bottom:7px; }
.gp-invwin .hd .t{ font-family:'JetBrains Mono',monospace; font-size:9px; letter-spacing:1px; text-transform:uppercase; color:var(--muted); }
.invgrid{ display:grid; grid-template-columns:repeat(9,1fr); gap:3px; }
.invgrid.hot{ margin-top:6px; padding-top:6px; border-top:1px solid var(--line2); }
.islot2{ aspect-ratio:1; border-radius:5px; background:var(--slot); border:1px solid var(--line2); position:relative;
  display:flex; align-items:center; justify-content:center; overflow:hidden; }
.islot2.hot{ background:#0d1420; }
.islot2.has{ border-color:var(--line); background:var(--panel); cursor:pointer; }
.islot2.gpu{ border-color:#2e5a47; }
.islot2 .g{ font-family:'JetBrains Mono',monospace; font-size:8px; color:var(--muted); line-height:1; text-align:center; }
.islot2 .c{ position:absolute; bottom:0; right:2px; font-family:'JetBrains Mono',monospace; font-size:8px; color:var(--text); }
`

const TABS = [
  { id: 'biobank',   label: 'BioBank',   path: 'gp://biobank' },
  { id: 'harvester', label: 'Harvester', path: 'gp://harvester' },
  { id: 'pastures',  label: 'Pastures',  path: 'gp://pastures' },
  { id: 'compiler',  label: 'Compiler',  path: 'gp://daemon/compiler' },
  { id: 'augmenter', label: 'Augmenter', path: 'gp://kernel/augmenter' },
  { id: 'dashboard', label: 'Dashboard', path: 'gp://dashboard' },
  { id: 'inbox',     label: 'Inbox',     path: 'gp://inbox' },
  { id: 'rituals',   label: 'Rituals',   path: 'gp://rituals' },
  { id: 'specimens', label: 'Specimens', path: 'gp://specimens' },
  { id: 'gamecorner', label: 'Game Corner', path: 'gp://gamecorner' },
  { id: 'guide',     label: 'Guide',     path: 'gp://guide' },
]
const STAT_NAMES = ['HP', 'At', 'Df', 'SA', 'SD', 'Sp']
const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s)
const fmt = (n) => (n == null ? '…' : n.toLocaleString('en-US'))
const compact = (n) => (n < 1000 ? `${n}` : n < 1e6 ? `${(n / 1e3).toFixed(1)}k` : `${(n / 1e6).toFixed(1)}M`)
const shortId = (id) => (id || '').split(':').pop().replace(/_/g, ' ')
const fmtTime = (s) => (s < 60 ? `${s}s` : s < 3600 ? `${(s / 60) | 0}m` : s < 86400 ? `${(s / 3600) | 0}h` : `${(s / 86400) | 0}d`)

export default function App() {
  const [tab, setTab] = useState(() => {
    try {   // a brand-new player's FIRST-ever open lands on the Guide (review C1: the old landing was an
            // empty BioBank full of undefined jargon); every later open goes straight to work.
      if (!localStorage.getItem('gp_seen_guide')) { localStorage.setItem('gp_seen_guide', '1'); return 'guide' }
    } catch (e) { /* storage unavailable - fall through */ }
    return 'biobank'
  })
  const active = TABS.find((t) => t.id === tab)
  const pcfg = useChannel('pastureConfig')      // set when a pasture is right-clicked with the Notebook
  const focused = pcfg?.present
  const notifs = useChannel('notifications')
  const noteCount = notifs?.notes?.length || 0
  const nav = useChannel('nav')          // one-shot tab requests (the Field Guide item opens gp://guide)
  useEffect(() => { if (nav?.tab && TABS.some((t) => t.id === nav.tab)) setTab(nav.tab) }, [nav?.n])
  // Viewport scaling: uniformly zoom the fixed-design stage to fill the MC window (viewport-ui-principle),
  // so the console holds the same proportion at any window size instead of being a fixed-px panel in black.
  useEffect(() => {
    const DESIGN_W = 1180, DESIGN_H = 724, MARGIN = 0.97
    const fit = () => {
      const s = Math.min(window.innerWidth / DESIGN_W, window.innerHeight / DESIGN_H) * MARGIN
      document.documentElement.style.setProperty('--gp-scale', s)
    }
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  return (
    <div className="gp-backdrop">
      <style>{CSS}</style>
      <div className="gp-stage">
      <div className="gp-win">
        <div className="gp-title">
          <span className="dot" style={{ background: '#ff5f57' }} />
          <span className="dot" style={{ background: '#febc2e' }} />
          <span className="dot" style={{ background: '#28c840' }} />
          <span className="gp-fname">pasture.ipynb</span>
          <span className="gp-sub">· greener-pastures :: notebook</span>
          <Conn />
          <span className="gp-x" title="close" onClick={() => send('console', 'CLOSE_CONSOLE', {})}>✕</span>
        </div>
        {focused ? (
          <div className="gp-body"><PastureConfig cfg={pcfg} /></div>
        ) : (<>
        <div className="gp-tabs">
          {TABS.map((t) => (
            <button key={t.id} className={`gp-tab${tab === t.id ? ' on' : ''}`} onClick={() => setTab(t.id)}>
              {t.label}{t.id === 'inbox' && noteCount > 0 && <span className="tab-badge">{noteCount}</span>}
            </button>
          ))}
        </div>
        <div className="gp-cmd"><span>{active.path}</span><span className="gp-caret" /></div>
        <div className="gp-body">
          {tab === 'biobank' && <BioBank />}
          {tab === 'harvester' && <Harvester />}
          {tab === 'pastures' && <Pastures />}
          {tab === 'compiler' && <Compiler />}
          {tab === 'augmenter' && <Augmenter />}
          {tab === 'dashboard' && <Dashboard />}
          {tab === 'inbox' && <InboxTab />}
          {tab === 'rituals' && <RitualsTab />}
          {tab === 'specimens' && <SpecimensTab />}
          {tab === 'gamecorner' && <GameCorner />}
          {tab === 'guide' && <GuideTab />}
        </div>
        </>)}
        <StatusBar />
      </div>
      </div>
    </div>
  )
}
// InventoryWindow removed - the real MC inventory (with icons) is now drawn natively over the browser
// (NotebookBrowserScreen), since the browser can't render Minecraft item textures.

function Conn() {
  useChannel('status') // subscribe so the indicator re-renders when data (mock or live) lands
  const mock = isMock()
  return (
    <span className="gp-conn">
      <span className="gp-cdot" style={{ color: mock ? 'var(--amber)' : 'var(--green)' }} />
      {mock ? 'mock data' : 'kernel · connected'}
    </span>
  )
}

function StatusBar() {
  const s = useChannel('status')
  return (
    <div className="gp-foot">
      <span className="amb">Data {s ? fmt(s.data) : '…'}</span>
      <span className="cyn">GPU {s ? s.gpu : '…'}</span>
      <span className="muted">⌬ Kernel</span>
      <span className="sp" />
      <span style={{ color: s?.daemonOn ? 'var(--green)' : 'var(--dim)' }}>● Daemon {s ? (s.daemonOn ? 'ON' : 'OFF') : '…'}</span>
    </div>
  )
}

// Floating inventory window: mirrors the player's REAL inventory (`inventory` channel). Draggable by its header
// (persists across tabs - always mounted outside the tab body) and minimizable to a single line. Drag applies a
// GPU transform straight to the node (no per-move React re-render → smooth in MCEF), committed to state on release;
// the delta is divided by --gp-scale to track the cursor under the viewport zoom.
function InventoryWindow() {
  const inv = useChannel('inventory')
  const slots = inv?.slots || Array(36).fill(null)
  const [pos, setPos] = useState({ x: 892, y: 512 })   // stage coords; default ≈ bottom-right of the 1180×724 stage
  const [min, setMin] = useState(false)
  const winRef = useRef(null)
  const drag = useRef(null)
  const onDown = (e) => {
    const scale = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--gp-scale')) || 1
    drag.current = { sx: e.clientX, sy: e.clientY, bx: pos.x, by: pos.y, scale, nx: pos.x, ny: pos.y }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    e.preventDefault()
  }
  const onMove = (e) => {
    const d = drag.current; if (!d) return
    const maxY = min ? 724 - 46 : 724 - 190   // keep at least the header on-screen (min = just the bar)
    d.nx = Math.max(4, Math.min(906, d.bx + (e.clientX - d.sx) / d.scale))
    d.ny = Math.max(4, Math.min(maxY, d.by + (e.clientY - d.sy) / d.scale))
    // transform reflects the CLAMPED position (not the raw cursor) so there's no snap on release
    if (winRef.current) winRef.current.style.transform = `translate(${d.nx - d.bx}px,${d.ny - d.by}px)`
  }
  const onUp = () => {
    const d = drag.current; drag.current = null
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    if (winRef.current) winRef.current.style.transform = ''
    if (d) setPos({ x: d.nx, y: d.ny })
  }
  return (
    <div className="gp-invwin" ref={winRef} style={{ left: pos.x, top: pos.y, right: 'auto', bottom: 'auto' }}>
      <div className="hd" onMouseDown={onDown} style={{ cursor: 'grab', marginBottom: min ? 0 : undefined }}>
        <span className="dot" style={{ background: 'var(--green)', width: 7, height: 7 }} />
        <span className="t">inventory</span>
        <span style={{ flex: 1 }} />
        {!min && <span className="dim" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 8 }}>browser preview - manage items in-game</span>}
        <span onMouseDown={(e) => e.stopPropagation()} onClick={() => setMin((m) => !m)} title={min ? 'expand' : 'minimize'}
          style={{ cursor: 'pointer', marginLeft: 8, color: 'var(--muted)', fontFamily: "'JetBrains Mono',monospace", fontSize: 12, lineHeight: 1 }}>{min ? '▢' : '-'}</span>
      </div>
      {!min && <div className="invgrid">{slots.slice(9, 36).map((s, i) => <Slot key={i} s={s} idx={i + 9} />)}</div>}
      {!min && <div className="invgrid hot">{slots.slice(0, 9).map((s, i) => <Slot key={i} s={s} idx={i} hot />)}</div>}
    </div>
  )
}
function Slot({ s, idx, hot }) {
  if (!s) return <div className={`islot2${hot ? ' hot' : ''}`} />
  const isGpu = s.id.endsWith(':gpu')
  const label = isGpu ? '◈' : shortId(s.id).replace(/\s/g, '').slice(0, 3)
  return (
    <div className={`islot2 has${hot ? ' hot' : ''}${isGpu ? ' gpu' : ''}`} title={`${shortId(s.id)} ×${s.count}`}
      onClick={(ev) => { if (shiftHeld(ev)) send('storage', 'DEPOSIT', { slot: idx }) }}>
      <span className="g" style={isGpu ? { color: 'var(--cyan)' } : null}>{label}</span>
      <span className="c">{s.count}</span>
    </div>
  )
}

// ── BioBank ──────────────────────────────────────────────────────────────────
function BioBank() {
  const d = useChannel('biobank')
  const [open, setOpen] = useState(null)
  const [sortKey, setSortKey] = useState('iv')
  const entries = d?.entries
  // Memoized: grouping thousands of eggs re-ran on EVERY render (sort clicks, accordion toggles) - R3 #9.
  const { groups, species } = useMemo(() => {
    const groups = {}
    ;(entries || []).forEach((e, i) => (groups[e.species] ||= []).push({ e, i }))
    return { groups, species: Object.keys(groups) }
  }, [entries])
  if (!d) return <div className="pane" />   // channel not received yet → blank, not a flash of the empty state
  if (!species.length) return <Empty title="BioBank empty" msg="link a pasture - its eggs are pulled in here automatically while it's loaded" />
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 8 }}>
        <span className="h">BioBank · {d.total} eggs · {species.length} species</span>
        <span style={{ flex: 1 }} />
        <span className="dim" style={{ fontSize: 10 }}>sort ↓</span>
        <SortBtn k="iv" cur={sortKey} set={setSortKey}>ΣIV</SortBtn>
        {STAT_NAMES.map((n, i) => <SortBtn key={i} k={String(i)} cur={sortKey} set={setSortKey}>{n}</SortBtn>)}
        <SortBtn k="shiny" cur={sortKey} set={setSortKey}>★</SortBtn>
      </div>
      <div className="acc">
        {species.map((sp) => {
          const on = sp === open
          return (
            <div key={sp}>
              <div className={`sphead${on ? ' on' : ''}`} onClick={() => setOpen(on ? null : sp)}>
                <span className="grn">{on ? '▾' : '▸'}</span>
                <span className="spname">{cap(sp)}</span>
                <span style={{ flex: 1 }} />
                <span className="chip">×{groups[sp].length}</span>
              </div>
              {on && sortEggs(groups[sp], sortKey).map(({ e, i }) => <EggCard key={i} e={e} idx={i} />)}
            </div>
          )
        })}
      </div>
    </div>
  )
}
function EggCard({ e, idx }) {
  const ivT = e.ivs.reduce((a, b) => a + b, 0)
  const hasEv = e.evs.some((v) => v > 0)
  return (
    <div className="card inset">
      <div className="stat">
        {e.shiny && <span className="amb">★</span>}
        {e.gender && <span style={{ color: e.gender === 'female' ? 'var(--pink)' : e.gender === 'male' ? 'var(--cyan)' : 'var(--muted)' }}>{e.gender === 'female' ? '♀' : e.gender === 'male' ? '♂' : '⚲'}</span>}
        {e.nature && <span className="mono" style={{ fontSize: 11 }}>{cap(e.nature)}</span>}
        {e.ability && <span className="cyn mono" style={{ fontSize: 11 }}>{cap(e.ability)}</span>}
        <span style={{ flex: 1 }} />
        <span className="mono" style={{ fontSize: 11, color: ivT >= 160 ? 'var(--green)' : 'var(--muted)' }}>Σ{ivT}/186</span>
        <button className="btn" style={{ padding: '2px 7px', fontSize: 10 }} title="pull → a real egg in your inventory (to hatch)" onClick={() => send('biobank', 'WITHDRAW', { index: idx })}>↧ pull</button>
      </div>
      <StatRow tag="IV" vals={e.ivs} perfect={31} color="var(--green)" />
      {hasEv && <StatRow tag="EV" vals={e.evs} perfect={252} color="var(--amber)" />}
    </div>
  )
}
function StatRow({ tag, vals, perfect, color }) {
  return (
    <div className="stat">
      <span className="dim mono" style={{ fontSize: 10, width: 16 }}>{tag}</span>
      {vals.map((v, i) => (
        <span key={i} className="mono" style={{ fontSize: 10, color: v >= perfect ? color : v === 0 ? 'var(--dim)' : 'var(--text)' }}>{STAT_NAMES[i]} {v}</span>
      ))}
    </div>
  )
}

// ── Inbox - dismissible notifications (catch-up pings etc.; nothing stacks in chat) ──
function InboxTab() {
  const d = useChannel('notifications')
  const notes = d?.notes || []
  const ago = (t) => {
    const s = Math.max(0, (Date.now() - t) / 1000)
    return s < 60 ? `${Math.floor(s)}s ago` : s < 3600 ? `${Math.floor(s / 60)}m ago` : `${Math.floor(s / 3600)}h ago`
  }
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="h">Inbox</span>
        <span className="muted" style={{ fontSize: 11 }}>{notes.length} notification{notes.length === 1 ? '' : 's'}</span>
        <span style={{ flex: 1 }} />
        {notes.length > 0 && <button className="btn" style={{ fontSize: 10, padding: '2px 8px' }} onClick={() => send('storage', 'DISMISS_NOTE', { id: 'all' })}>clear all</button>}
      </div>
      {!notes.length ? <div className="muted" style={{ fontSize: 12 }}>all caught up - away-progress and pasture events land here.</div> : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {notes.map((n) => (
            <div key={n.id} className="note">
              <span className="note-ic">{n.icon}</span>
              <span className="note-tx">{n.text}</span>
              <span className="dim mono" style={{ fontSize: 9, flex: 'none' }}>{ago(n.t)}</span>
              <span className="note-x" title="dismiss" onClick={() => send('storage', 'DISMISS_NOTE', { id: String(n.id) })}>✕</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Harvester / Storage ──────────────────────────────────────────────────────
function Harvester() {
  const d = useChannel('storage')
  const inv = useChannel('inventory')
  const items = d?.items
  const { list, total } = useMemo(() => {
    const list = Object.entries(items || {}).sort((a, b) => b[1] - a[1])
    return { list, total: list.reduce((a, [, n]) => a + n, 0) }
  }, [items])
  // Room check (client-side hint; the server re-verifies + refuses): an empty slot, or a same-item partial stack.
  const slots = inv?.slots || []
  const canTake = (id) => slots.some((s) => !s || (s.id === id && s.count < 64))
  const invFull = slots.length > 0 && !slots.some((s) => !s)
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="h">Storage</span>
        <span className="muted" style={{ fontSize: 11 }}>{list.length} types · {compact(total)} items</span>
        <span style={{ flex: 1 }} />
        {invFull && <span style={{ color: 'var(--red)', fontSize: 11, marginRight: 8 }}>⚠ inventory full</span>}
        <span className="dim" style={{ fontSize: 11 }}>L: one · ⇧-click: stack · R: all → inventory</span>
      </div>
      {!list.length ? <div style={{ color: 'var(--muted)', fontSize: 12 }}>empty - harvested loot from your linked pastures collects here</div> : (
        <div className="grid">
          {list.map(([id, n]) => { const ok = canTake(id); return (
            <div key={id} className={`cell${ok ? '' : ' cell-full'}`} title={ok ? `${id} · L: one · ⇧: stack · R: all` : `${id} · inventory full - make room to pull`}
              onClick={(ev) => { if (ok) send('storage', shiftHeld(ev) ? 'PULL_STACK' : 'PULL_ONE', { item: id }) }}
              onContextMenu={(ev) => { ev.preventDefault(); if (ok) send('storage', 'PULL_ID', { item: id }) }}>
              <span className="ct">{compact(n)}</span>
              <span className="nm">{shortId(id)}</span>
            </div>
          )})}
        </div>
      )}
    </div>
  )
}

// ── Pastures ─────────────────────────────────────────────────────────────────
const FLAG_TEXT = {
  unlinked: '🔗 not linked - drops & eggs are not collected',
  no_kernel: '🧬 no Kernel - breeding & harvest offline',
  no_parents: '👥 fewer than 2 parents',
  tray_full: '🥚 egg tray full - breeding paused',
}
const flagLine = (id) => id.startsWith('bank_full:') ? `🏦 BioBank full for ${id.slice(10)}` : (FLAG_TEXT[id] || `⚠ ${id}`)

function Pastures() {
  const d = useChannel('pastures')
  const [sel, setSel] = useState(0)
  const list = d?.pastures || []
  const health = d?.health || {}
  const flagsOf = (x) => { const csv = health[`${x.dim}|${x.pos}`]; return csv ? csv.split(',') : [] }
  if (!list.length) return <Empty title="No pastures tracked" msg="right-click a pasture in-world with your Notebook to monitor it here" />
  const p = list[Math.min(sel, list.length - 1)]
  const pFlags = flagsOf(p)
  return (
    <div className="pane split">
      <div className="plist inset">
        <div className="h" style={{ marginBottom: 8 }}>Pastures · {list.length}</div>
        {list.map((x, i) => {
          const n = flagsOf(x).length
          return (
            <div key={i} className={`pitem${i === sel ? ' on' : ''}`} onClick={() => setSel(i)}>
              <span style={{ fontWeight: 500 }}>{x.name}</span>
              {n > 0 && <span className="pbadge" title={flagsOf(x).map(flagLine).join('\n')}>⚠{n}</span>}
              <span style={{ flex: 1 }} />
              <span className="amb mono" style={{ fontSize: 10 }}>{x.eggCount} eggs</span>
            </div>
          )
        })}
      </div>
      <div className="pdetail inset">
        <div className="grn" style={{ fontWeight: 600, fontSize: 15 }}>{p.name}</div>
        <div className="muted mono" style={{ fontSize: 11, margin: '4px 0 2px' }}>{p.tier} · {p.eggCount} eggs queued · {p.pairs.length} pairs</div>
        <div className="dim" style={{ fontSize: 11, marginBottom: 10 }}>read-only - modify at the pasture in-world</div>
        {pFlags.length > 0 && (
          <div className="hstrip">
            {pFlags.map((id) => <span key={id} className="hflag">{flagLine(id)}</span>)}
          </div>
        )}
        {p.pairs.length ? p.pairs.map((line, i) => <div key={i} className="pair" style={{ color: pairColor(line) }}>{line}</div>)
          : <div className="dim" style={{ fontSize: 11 }}>no pairs arranged</div>}
      </div>
    </div>
  )
}
const pairColor = (l) => (l.endsWith('Breeding') ? 'var(--cyan)' : l.endsWith('Ready') ? 'var(--green)' : l.endsWith('Incomplete') ? 'var(--dim)' : 'var(--text)')

// ── Compiler (Daemon buff loadout) ───────────────────────────────────────────
// Target selector (backlog #5): with 2+ Kernels/Daemons in the pack the tab operated on first-found.
// Cards list every candidate by inventory slot; click = server-side target (re-validated on every use).
function TargetCards({ items, kind, action }) {
  if (!items || items.length < 2) return null
  return (
    <div className="row" style={{ gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
      <span className="dim" style={{ fontSize: 10 }}>{kind} target:</span>
      {items.map((k) => (
        <button key={k.slot} className={`btn${k.target ? ' go' : ''}`} style={{ fontSize: 10 }}
          title={`inventory slot ${k.slot === 1000 ? 'offhand' : k.slot}`}
          onClick={() => send('augmenter', action, { slot: k.slot })}>
          {k.slot === 1000 ? '⬅' : `#${k.slot}`} {k.name ? k.name : k.tier ? cap(k.tier) : (k.on ? '● on' : '○ off')}
          {' · '}{k.augs != null ? `${k.augs} aug${k.augs === 1 ? '' : 's'}` : `${k.buffs} buff${k.buffs === 1 ? '' : 's'}`}
          {k.corrupted ? ' ⛧' : ''}
        </button>
      ))}
    </div>
  )
}

function Compiler() {
  const d = useChannel('compiler')
  const status = useChannel('status')
  const meta = useChannel('augmenter')?.meta   // the augmeta blob (rides the augmenter channel) - carries the target-candidate lists
  if (!d) return <Empty title="…" msg="loading the Compiler channel" />
  if (!d.hasDaemon) return <Empty title="No Daemon in your inventory" msg="hold a Daemon (anywhere in your pack) to compile its buffs" />
  const installed = d.installed || {}
  const gpu = status?.gpu ?? 0
  const runtime = d.drainPerSec > 0 ? Math.floor((status?.data ?? 0) / d.drainPerSec) : null
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <TargetCards items={meta?.daemons} kind="Daemon" action="SET_DAEMON_TARGET" />
    <div className="trip" style={{ flex: 1, minHeight: 0, height: 'auto' }}>
      <div className="tcol inset" style={{ width: 130 }}>
        <span className="h">Daemon</span>
        <span style={{ color: d.daemonOn ? 'var(--green)' : 'var(--muted)' }}>{d.daemonOn ? '● running' : '○ idle'}</span>
        <span className="amb">Data {status ? fmt(status.data) : '…'}</span>
        <span className="cyn">◈ {gpu} GPU</span>
        <span className="muted">{Object.keys(installed).length} buffs on</span>
      </div>
      <div className="tcol inset" style={{ flex: 1 }}>
        <span className="h">Effect · −/+ tier · ◈ = GPU per tier</span>
        {(d.catalog || []).map((b) => {
          const tier = installed[b.id] || 0
          const gcost = b.gpuCost ?? 0
          return (
            <div key={b.id} className={`brow${tier > 0 ? ' on' : ''}`}>
              <span style={{ color: tier > 0 ? 'var(--text)' : 'var(--muted)', flex: 1 }}>{b.label}</span>
              <span className="dim mono" style={{ fontSize: 9 }}>{b.category}</span>
              <span className="mono" style={{ fontSize: 10, width: 30, textAlign: 'right', color: gpu >= gcost ? 'var(--cyan)' : 'var(--red)' }} title="GPU to add one tier">◈{gcost}</span>
              <button className="step" disabled={tier <= 0} onClick={() => send('compiler', 'SET_BUFF', { buff: b.id, tier: tier - 1 })}>−</button>
              <span className="mono" style={{ fontSize: 11, color: tier > 0 ? 'var(--green)' : 'var(--muted)', width: 36, textAlign: 'center' }}>L{tier}/{b.cap}</span>
              <button className="step" disabled={tier >= b.cap || gpu < gcost} title={gpu < gcost ? 'not enough GPU' : ''} onClick={() => send('compiler', 'SET_BUFF', { buff: b.id, tier: tier + 1 })}>+</button>
              <span className="amb mono" style={{ fontSize: 10, width: 44, textAlign: 'right' }}>{(tier * b.costPerTier).toFixed(2)}/s</span>
            </div>
          )
        })}
      </div>
      <div className="tcol inset" style={{ width: 150 }}>
        <span className="h">Loadout</span>
        <span className="amb mono">drain {d.drainPerSec.toFixed(2)}/s</span>
        <span className="muted mono" style={{ fontSize: 11 }}>runtime {runtime == null ? '∞' : fmtTime(runtime)}</span>
        <span style={{ flex: 1 }} />
        <button className={`power ${d.daemonOn ? 'on' : 'off'}`} onClick={() => send('compiler', 'TOGGLE_DAEMON', {})}>
          {d.daemonOn ? 'Power OFF' : 'Power ON'}
        </button>
      </div>
    </div>
    </div>
  )
}

// ── Augmenter (Kernel augments · #34 EV allocator · #35 nature/ball pickers) ─
// Standard nature table (universal Pokémon canon, stable) - display hints only; the LIST comes from the server.
const NATURE_FX = {
  hardy: '-', docile: '-', serious: '-', bashful: '-', quirky: '-',
  lonely: '+Atk −Def', brave: '+Atk −Spe', adamant: '+Atk −SpA', naughty: '+Atk −SpD',
  bold: '+Def −Atk', relaxed: '+Def −Spe', impish: '+Def −SpA', lax: '+Def −SpD',
  timid: '+Spe −Atk', hasty: '+Spe −Def', jolly: '+Spe −SpA', naive: '+Spe −SpD',
  modest: '+SpA −Atk', mild: '+SpA −Def', quiet: '+SpA −Spe', rash: '+SpA −SpD',
  calm: '+SpD −Atk', gentle: '+SpD −Def', sassy: '+SpD −Spe', careful: '+SpD −SpA',
}
const PARAM_TYPES = ['EV', 'NATURE', 'BALL']   // these open a picker instead of applying bare

function Augmenter() {
  const d = useChannel('augmenter')
  const status = useChannel('status')
  const gpu = status?.gpu ?? 0
  const inv = useChannel('inventory')          // hooks stay above the early returns - hook order must never change between renders
  const [picker, setPicker] = useState(null)   // 'NATURE' | 'BALL' | 'EV' | null
  if (!d) return <Empty title="…" msg="loading the Augmenter channel" />
  if (!d.hasKernel) return <Empty title="No Kernel in your inventory" msg="hold a Kernel (a Pasture Upgrade) to augment it" />
  const meta = d.meta || {}
  const values = meta.values || {}
  const corrupted = !!meta.corrupted
  const orbCount = (inv?.slots || []).reduce((a, s) => a + (s?.id === 'greenerpastures:data_disk_rocket' ? s.count : 0), 0)
  const valueChip = (t) => {
    if (t === 'NATURE') return values.NATURE ? cap(values.NATURE.label) : null
    if (t === 'BALL') return values.BALL ? cap(shortId(values.BALL.label)) : null
    if (t === 'EV') return values.EV?.spread ? values.EV.spread.join('/') : null
    return null
  }
  return (
    <div style={{ position: 'relative', height: '100%', display: 'flex', flexDirection: 'column' }}>
    <TargetCards items={meta.kernels} kind="Kernel" action="SET_KERNEL_TARGET" />
    <div className="trip" style={{ flex: 1, minHeight: 0, height: 'auto' }}>
      <div className="tcol inset" style={{ width: 150 }}>
        <span className="h">Kernel</span>
        <span className="grn" style={{ fontWeight: 600 }}>{d.tier}{corrupted && <span style={{ color: '#a06bd4' }}> ⛧</span>}</span>
        {corrupted && <span style={{ color: '#a06bd4', fontSize: 10 }}>corrupted - beyond modification{meta.corruptPairs ? ` · +${meta.corruptPairs} pair` : ''}</span>}
        <span className="muted">slots {d.slotsUsed}/{d.slotCap}</span>
        <div className="row" style={{ gap: 4 }}>
          {Array.from({ length: d.slotCap }).map((_, i) => <span key={i} className={`pip${i < d.slotsUsed ? ' on' : ''}`} />)}
        </div>
        <span className="cyn" style={{ marginTop: 6 }}>◈ {gpu} GPU</span>
        {orbCount > 0 && !corrupted && (
          <button className="btn" style={{ marginTop: 8, color: '#a06bd4', borderColor: '#5a3c74' }}
            title={`consume 1 Illicit Data Disk (you have ${orbCount}) - bless, twist, or brick this Kernel. PERMANENT.`}
            onClick={() => send('augmenter', 'CORRUPT_KERNEL', {})}>⛧ CORRUPT</button>
        )}
      </div>
      <div className="tcol inset" style={{ flex: 1 }}>
        <span className="h">Augments · ◈ = GPU cost{corrupted ? ' · ⛧ locked' : ''}</span>
        {(d.catalog || []).map((a) => {
          const gcost = a.gpuCost ?? 0   // GPU economy deferred server-side - treat "not sent" as free
          const lvl = a.appliedLevel ?? (a.applied ? 1 : 0)   // 6-field payload (appliedLevel); tolerate old shape
          const param = PARAM_TYPES.includes(a.type)
          const chip = param ? valueChip(a.type) : null
          const curSlots = lvl >= 2 ? 3 : lvl === 1 ? 1 : 0
          const slotDelta = (a.slotCost ?? 1) - curSlots           // slotCost = TOTAL slots of the next action
          const canDo = d.slotsUsed + slotDelta <= d.slotCap && gpu >= gcost
          const canUpgrade = lvl === 1 && (a.maxLevel ?? 1) > 1 && !param
          return (
            <div key={a.type} className={`brow${lvl > 0 ? ' on' : ''}`}>
              <span style={{ color: lvl > 0 ? 'var(--text)' : 'var(--muted)', flex: 1 }}>
                {a.label}{lvl === 2 && <span className="cyn mono" style={{ fontSize: 10 }} title="level II - 1.5× effect · 3 slots"> II</span>}{lvl >= 3 && <span className="mono" style={{ fontSize: 10, color: '#c77dff' }} title="TIER III - corruption-only"> ⛧III</span>}
              </span>
              {chip && <span className="augval" title="current setting">{chip}</span>}
              <span className="dim mono" style={{ fontSize: 10 }} title={lvl === 1 && canUpgrade ? 'level II occupies 3 slots total' : ''}>{a.slotCost} slot{a.slotCost !== 1 ? 's' : ''}</span>
              {gcost > 0 && <span className="mono" style={{ fontSize: 10, width: 30, textAlign: 'right', color: gpu >= gcost ? 'var(--cyan)' : 'var(--red)' }} title="GPU cost">◈{gcost}</span>}
              {lvl > 0 && param && !corrupted && <button className="btn" onClick={() => setPicker(a.type)}>EDIT</button>}
              {canUpgrade && !corrupted &&
                <button className="btn go" disabled={!canDo} title={gpu < gcost ? 'not enough GPU' : d.slotsUsed + slotDelta > d.slotCap ? `level II needs 3 slots total (${slotDelta} more free)` : '1.5× effect · 3 slots total'}
                  onClick={() => send('augmenter', 'APPLY_AUGMENT', { type: a.type })}>UPGRADE</button>}
              {lvl > 0
                ? <button className="btn warn" disabled={corrupted} title={corrupted ? '⛧ corrupted - beyond modification' : 'frees the slots'} onClick={() => send('augmenter', 'REMOVE_AUGMENT', { type: a.type })}>REMOVE</button>
                : <button className="btn go" disabled={!canDo || corrupted} title={corrupted ? '⛧ corrupted - beyond modification' : gpu < gcost ? 'not enough GPU' : (d.slotsUsed + slotDelta > d.slotCap ? 'no free slots' : '')}
                    onClick={() => param ? setPicker(a.type) : send('augmenter', 'APPLY_AUGMENT', { type: a.type })}>{param ? 'PICK…' : 'APPLY'}</button>}
            </div>
          )
        })}
      </div>
      {picker === 'NATURE' && <NaturePicker natures={meta.natures || []} current={values.NATURE?.value || 0} onClose={() => setPicker(null)} />}
      {picker === 'BALL' && <BallPicker balls={meta.balls || []} current={values.BALL?.value || 0} onClose={() => setPicker(null)} />}
      {picker === 'EV' && <EvAllocator initial={values.EV?.spread} onClose={() => setPicker(null)} />}
    </div>
    </div>
  )
}

// #35 - Nature Lock picker: the full server catalog (25), each with its stat-effect hint. Click = install.
function NaturePicker({ natures, current, onClose }) {
  const d = usePanelDrag()
  return (
    <div className="dcfg" ref={d.ref} style={{ ...d.style, width: 300 }} onMouseDown={(e) => e.stopPropagation()} onWheel={(e) => e.stopPropagation()}>
      <div className="dcfg-h dcfg-drag" onMouseDown={d.start}>🧬 Nature Lock - every egg hatches this nature<span className="dcfg-x" onClick={onClose} onMouseDown={(e) => e.stopPropagation()}>✕</span></div>
      <div className="nat-grid">
        {natures.map((n, i) => (
          <button key={n} className={`dchip2${current === i + 1 ? ' on' : ''}`}
            onClick={() => { send('augmenter', 'APPLY_AUGMENT', { type: `NATURE:${i + 1}` }); onClose() }}>
            <span>{cap(n)}</span><span className="nat-fx">{NATURE_FX[n.toLowerCase()] || ''}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

// #35 - Ball Lock picker: every breedable ball from the server catalog. Click = install.
function BallPicker({ balls, current, onClose }) {
  const d = usePanelDrag()
  return (
    <div className="dcfg" ref={d.ref} style={{ ...d.style, width: 280 }} onMouseDown={(e) => e.stopPropagation()} onWheel={(e) => e.stopPropagation()}>
      <div className="dcfg-h dcfg-drag" onMouseDown={d.start}>◉ Ball Lock - every egg hatches in this ball<span className="dcfg-x" onClick={onClose} onMouseDown={(e) => e.stopPropagation()}>✕</span></div>
      <div className="ball-grid">
        {balls.map((b, i) => (
          <button key={b} className={`dchip2${current === i + 1 ? ' on' : ''}`}
            onClick={() => { send('augmenter', 'APPLY_AUGMENT', { type: `BALL:${i + 1}` }); onClose() }}>
            {cap(shortId(b))}
          </button>
        ))}
      </div>
    </div>
  )
}

// #34 - EV Primer allocator: a targeted 6-stat spread (each ≤252, total ≤510), applied to every bred egg.
function EvAllocator({ initial, onClose }) {
  const d = usePanelDrag()
  const [ev, setEv] = useState(() => (initial && initial.length === 6 ? [...initial] : [0, 0, 0, 0, 0, 0]))
  const total = ev.reduce((a, b) => a + b, 0)
  const setStat = (i, raw) => {
    const v = Math.max(0, Math.min(252, raw | 0))
    const others = total - ev[i]
    const next = [...ev]
    next[i] = Math.min(v, 510 - others)   // clamp into the remaining budget - the bar can never overflow
    setEv(next)
  }
  return (
    <div className="dcfg" ref={d.ref} style={{ ...d.style, width: 250 }} onMouseDown={(e) => e.stopPropagation()} onWheel={(e) => e.stopPropagation()}>
      <div className="dcfg-h dcfg-drag" onMouseDown={d.start}>EV Primer - spread on every egg<span className="dcfg-x" onClick={onClose} onMouseDown={(e) => e.stopPropagation()}>✕</span></div>
      {STATS.map((s, i) => (
        <div key={s} className="ev-row">
          <span style={{ width: 26 }}>{s}</span>
          <input type="range" min={0} max={252} step={4} value={ev[i]} onChange={(e) => setStat(i, +e.target.value)} />
          <input type="number" min={0} max={252} value={ev[i]}
            onFocus={() => send('console', 'INPUT_FOCUS', { v: true })} onBlur={() => send('console', 'INPUT_FOCUS', { v: false })}
            onChange={(e) => setStat(i, parseInt(e.target.value) || 0)} />
        </div>
      ))}
      <div className="evbar"><div style={{ width: `${(total / 510) * 100}%` }} /></div>
      <div className="row" style={{ fontSize: 10 }}>
        <span className={total >= 510 ? 'amb' : 'dim'}>{total}/510 EVs</span>
        <span style={{ flex: 1 }} />
        <button className="btn" onClick={() => setEv([0, 0, 0, 0, 0, 0])}>clear</button>
        <button className="btn go" disabled={total === 0}
          onClick={() => { send('augmenter', 'APPLY_AUGMENT', { type: `EV:${ev.join(',')}` }); onClose() }}>APPLY</button>
      </div>
    </div>
  )
}

// ── Disks (§5c): write your Data balance onto blank media - the Notebook is the drive ──
const DENOMS = [   // the rocket disk is NOT here - it's the corruption orb (Black Market ritual), not currency
  ['data_disk_byte', 'byte', 8], ['data_disk_kilobyte', 'kB', 1024], ['data_disk_megabyte', 'MB', 16384],
  ['data_disk_gigabyte', 'GB', 262144], ['data_disk_terabyte', 'TB', 4194304],
]
function DisksCard() {
  const status = useChannel('status')
  const inv = useChannel('inventory')
  const data = status?.data ?? 0
  const blanks = (inv?.slots || []).reduce((a, s) => a + (s?.id === 'greenerpastures:data_disk_blank' ? s.count : 0), 0)
  return (
    <div className="inset" style={{ padding: 10, borderRadius: 8, marginTop: 10 }}>
      <div className="row" style={{ marginBottom: 6 }}>
        <span className="h">Data disks</span>
        <span style={{ flex: 1 }} />
        <span className="dim mono" style={{ fontSize: 10 }}>{blanks} blank{blanks === 1 ? '' : 's'} · right-click a written disk to read it back</span>
      </div>
      <div className="row" style={{ gap: 5, flexWrap: 'wrap' }}>
        {DENOMS.map(([id, label, v]) => {
          const ok = blanks > 0 && data >= v
          return (
            <button key={id} className="btn" disabled={!ok}
              title={blanks === 0 ? 'craft a blank disk first' : data < v ? `needs ${v.toLocaleString()} Data` : `write ${v.toLocaleString()} Data onto a blank`}
              onClick={() => send('storage', 'WRITE_DISK', { denom: `greenerpastures:${id}` })}>
              💾 {label} · {compact(v)}
            </button>
          )
        })}
      </div>
    </div>
  )
}

// ── Dashboard (live analytics - real breeding data over the `dashboard` channel) ──
// The 1M-lifetime capstone: progress odometer + SUMMON when a MissingNo. is owed.
function MissingnoCard() {
  const d = useChannel('dashboard')
  const life = d?.lifetimeEarned ?? 0
  const claimable = d?.mnClaimable ?? 0
  if (life <= 0 && claimable <= 0) return null   // zalgo noise means nothing to a day-one player (review U8)
  const prog = Math.max(0, Math.min(1, d?.mnProgress ?? 0))
  return (
    <div className="inset" style={{ padding: 10, borderRadius: 8, marginBottom: 10 }}>
      <div className="row">
        <span style={{ fontWeight: 700, fontSize: 12, color: '#c77dff', fontFamily: 'monospace' }}>▓▒░ M̸i̷s̶s̴i̵n̷g̸N̵o̶. ░▒▓</span>
        <span style={{ flex: 1 }} />
        <span className="dim mono" style={{ fontSize: 10 }}>{fmt(life)} lifetime rendered</span>
      </div>
      <div className="row" style={{ gap: 8, marginTop: 6, alignItems: 'center' }}>
        <div style={{ flex: 1, height: 6, background: '#1a1330', borderRadius: 3, overflow: 'hidden' }}>
          <div style={{ width: `${(prog * 100).toFixed(1)}%`, height: '100%', background: 'linear-gradient(90deg,#7b2cbf,#c77dff)' }} />
        </div>
        <span className="dim mono" style={{ fontSize: 10 }}>{(prog * 100).toFixed(1)}% to next</span>
        <button className="btn go" disabled={claimable < 1}
          title={claimable < 1 ? 'one summon per 1,000,000 lifetime Data rendered' : `${claimable} owed - a glitch that rotates its very being · cannot battle`}
          onClick={() => send('dashboard', 'SUMMON_MISSINGNO', {})}>
          SUMMON{claimable > 0 ? ` (${claimable})` : ''}
        </button>
      </div>
      <div className="dim" style={{ fontSize: 10, marginTop: 4, fontStyle: 'italic' }}>
        one per million rendered · rotates its form every few seconds · refuses all battles - a trophy, not a weapon
      </div>
    </div>
  )
}

function Dashboard() {
  const d = useChannel('dashboard')
  const laid = d?.laid || 0, shiny = d?.shiny || 0, kept = d?.kept || 0, voided = d?.voided || 0, dataEarned = d?.dataEarned || 0
  const spark = d?.spark && d.spark.length ? d.spark : [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
  const shinyRate = laid ? (shiny / laid * 100) : 0
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="h">Dashboard</span>
        <span className="dim" style={{ fontSize: 11 }}>live · stats reset each session</span>
      </div>
      <Goals />
      <DisksCard />
      <MissingnoCard />
      <div className="statrow">
        <Stat label="eggs" value={laid} />
        <Stat label="shiny" value={shiny} sub={`${shinyRate.toFixed(2)}%`} color="var(--pair)" />
        <Stat label="kept" value={kept} color="var(--green)" />
        <Stat label="voided" value={voided} color="var(--red)" />
        <Stat label="data earned (session)" value={dataEarned} color="var(--cyan)" />
      </div>
      <div className="dgrid">
        <div className="dcard"><LinePlot title="eggs/min" sub="last 12 min" color="var(--amber)" data={spark} /></div>
        <div className="dcard"><Donut accepted={kept} voided={voided} /></div>
        <div className="dcard"><TierBars byTier={d?.byTier || {}} /></div>
      </div>
    </div>
  )
}
function Stat({ label, value, sub, color }) {
  return (
    <div className="stat">
      <div className="stat-v" style={{ color: color || 'var(--text)' }}>{typeof value === 'number' ? value.toLocaleString() : value}</div>
      <div className="stat-l">{label}{sub ? <span className="dim"> · {sub}</span> : null}</div>
    </div>
  )
}
function TierBars({ byTier }) {
  const entries = Object.entries(byTier || {})
  const max = Math.max(1, ...entries.map(([, v]) => v))
  return (
    <div style={{ padding: '4px 2px' }}>
      <div className="mono" style={{ fontSize: 10, color: 'var(--text)', fontWeight: 600, marginBottom: 6 }}>eggs by kernel</div>
      {entries.length === 0 ? <div className="dim" style={{ fontSize: 10 }}>no eggs yet</div> : entries.map(([k, v]) => (
        <div key={k} className="tbar-row">
          <span className="tbar-k">{cap(String(k).toLowerCase())}</span>
          <div className="tbar-track"><div className="tbar-fill" style={{ width: `${v / max * 100}%` }} /></div>
          <span className="tbar-v">{v}</span>
        </div>
      ))}
    </div>
  )
}
// The breeding-goal hunt panel - set a target (species/shiny/IVs/count), watch live progress.
function Goals() {
  const g = useChannel('goals')
  const bank = useChannel('biobank')
  const speciesList = useMemo(() => [...new Set((bank?.entries || []).map((e) => e.species).filter(Boolean))].sort(), [bank?.entries])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ species: '', shiny: 1, minPerfect: 0, minIvTotal: 0, count: 1 })
  const present = g?.present
  const focusOn = () => send('console', 'INPUT_FOCUS', { v: true })
  const focusOff = () => send('console', 'INPUT_FOCUS', { v: false })
  return (
    <div className="goalbox">
      <div className="row" style={{ alignItems: 'center' }}>
        <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--amber)' }}>🎯 breeding goal</span>
        <span style={{ flex: 1 }} />
        {present && <button className="btn" style={{ fontSize: 10, padding: '2px 7px' }} onClick={() => send('goals', 'CLEAR', {})}>clear</button>}
        <button className="btn" style={{ fontSize: 10, padding: '2px 7px', marginLeft: 4 }} onClick={() => setOpen((o) => !o)}>{open ? 'close' : present ? 'edit' : 'set goal'}</button>
      </div>
      {present && !open && (
        <div style={{ marginTop: 8 }}>
          <div style={{ fontSize: 12, color: 'var(--text)', marginBottom: 5 }}>{g.describe}{g.reached ? ' ✓ reached' : ''}</div>
          <div className="goal-bar"><div className="goal-fill" style={{ width: `${Math.min(100, g.count ? g.matched / g.count * 100 : 0)}%` }} /></div>
          <div className="dim" style={{ fontSize: 10, marginTop: 4 }}>{g.matched}/{g.count} matched · {g.checked} eggs checked · best IV total {g.bestIvTotal}</div>
        </div>
      )}
      {!present && !open && <div className="dim" style={{ fontSize: 11, marginTop: 6 }}>no active hunt - set a target and watch progress as your pastures lay.</div>}
      {open && (
        <div className="goal-form">
          <label>species<input className="gp-input" list="gp-species" value={form.species} placeholder="any species" onFocus={focusOn} onBlur={focusOff} onChange={(e) => setForm({ ...form, species: e.target.value })} />
            <datalist id="gp-species">{speciesList.map((s) => <option key={s} value={cap(s)} />)}</datalist></label>
          <label>shiny<select value={form.shiny} onChange={(e) => setForm({ ...form, shiny: +e.target.value })}><option value={1}>must be shiny</option><option value={0}>non-shiny</option><option value={-1}>either</option></select></label>
          <label>min perfect IVs<input type="number" min={0} max={6} value={form.minPerfect} onFocus={focusOn} onBlur={focusOff} onChange={(e) => setForm({ ...form, minPerfect: Math.max(0, Math.min(6, +e.target.value || 0)) })} /></label>
          <label>min IV total<input type="number" min={0} max={186} value={form.minIvTotal} onFocus={focusOn} onBlur={focusOff} onChange={(e) => setForm({ ...form, minIvTotal: Math.max(0, Math.min(186, +e.target.value || 0)) })} /></label>
          <label>count<input type="number" min={1} value={form.count} onFocus={focusOn} onBlur={focusOff} onChange={(e) => setForm({ ...form, count: Math.max(1, +e.target.value || 1) })} /></label>
          <button className="btn" onClick={() => { send('goals', 'SET', form); setOpen(false) }}>{present ? 'update hunt' : 'start hunt'}</button>
        </div>
      )}
    </div>
  )
}
function LinePlot({ title, sub, data, color }) {
  const W = 320, H = 150, pad = { l: 30, r: 12, t: 28, b: 18 }
  const max = Math.max(...data), min = Math.min(...data, 0)
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b
  const X = (i) => pad.l + (i / (data.length - 1)) * iw
  const Y = (v) => pad.t + ih - ((v - min) / (max - min || 1)) * ih
  const pts = data.map((v, i) => `${X(i).toFixed(1)},${Y(v).toFixed(1)}`).join(' ')
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%' }}>
      <text x={pad.l} y={13} style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 10, fill: 'var(--text)', fontWeight: 600 }}>{title}</text>
      <text x={pad.l} y={23} style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 8, fill: 'var(--dim)' }}>{sub}</text>
      <line x1={pad.l} x2={W - pad.r} y1={pad.t + ih} y2={pad.t + ih} stroke="var(--line)" />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" />
      <circle cx={X(data.length - 1)} cy={Y(data[data.length - 1])} r="3" fill={color} />
    </svg>
  )
}
function Donut({ accepted, voided }) {
  const total = accepted + voided || 1, r = 42, cx = 70, cy = 78, C = 2 * Math.PI * r
  const frac = accepted / total
  return (
    <svg viewBox="0 0 320 150" style={{ width: '100%' }}>
      <text x="14" y="13" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 10, fill: 'var(--text)', fontWeight: 600 }}>accepted vs voided</text>
      <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--red)" strokeWidth="15" />
      <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--green)" strokeWidth="15" strokeDasharray={`${(C * frac).toFixed(1)} ${C.toFixed(1)}`} strokeLinecap="round" transform={`rotate(-90 ${cx} ${cy})`} />
      <text x={cx} y={cy + 5} textAnchor="middle" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 18, fontWeight: 600, fill: 'var(--green)' }}>{Math.round(frac * 100)}%</text>
      <g transform="translate(150,58)" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 11 }}>
        <rect width="10" height="10" rx="2" fill="var(--green)" /><text x="16" y="9" fill="var(--text)">accepted · {accepted}</text>
        <rect y="20" width="10" height="10" rx="2" fill="var(--red)" /><text x="16" y="29" fill="var(--text)">voided · {voided}</text>
      </g>
    </svg>
  )
}
function Histogram({ bins }) {
  const W = 320, H = 150, pad = { l: 14, r: 12, t: 28, b: 16 }
  const max = Math.max(...bins), iw = W - pad.l - pad.r, ih = H - pad.t - pad.b, bw = iw / bins.length
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%' }}>
      <text x={pad.l} y={13} style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 10, fill: 'var(--text)', fontWeight: 600 }}>iv_total · distribution</text>
      <line x1={pad.l} x2={W - pad.r} y1={pad.t + ih} y2={pad.t + ih} stroke="var(--line)" />
      {bins.map((b, i) => { const hh = (b / max) * ih; return <rect key={i} x={pad.l + i * bw + 2} y={pad.t + ih - hh} width={bw - 4} height={hh} rx="2" fill="var(--cyan)" opacity={0.5 + 0.5 * (b / max)} /> })}
    </svg>
  )
}

function SortBtn({ k, cur, set, children }) {
  const on = k === cur
  return <button className="btn" style={{ padding: '2px 6px', fontSize: 10, color: on ? 'var(--green)' : 'var(--muted)', borderColor: on ? '#2e5a47' : 'var(--line)' }} onClick={() => set(k)}>{children}</button>
}
const eggIvTotal = (e) => e.ivs.reduce((a, b) => a + b, 0)
function sortEggs(list, key) {
  const arr = [...list]
  if (key === 'shiny') arr.sort((a, b) => (b.e.shiny - a.e.shiny) || (eggIvTotal(b.e) - eggIvTotal(a.e)))
  else if (key === 'iv') arr.sort((a, b) => eggIvTotal(b.e) - eggIvTotal(a.e))
  else { const s = +key; arr.sort((a, b) => ((b.e.ivs[s] ?? 0) - (a.e.ivs[s] ?? 0)) || (eggIvTotal(b.e) - eggIvTotal(a.e))) }
  return arr
}
// The Daemon node graph (visual scripting, Arrangement v2). Each tethered mon is a draggable node; drag a mon's
// port onto another mon to WIRE them = a breeding pair. Wires are derived from PastureData.pairings (two mons in
// one bucket = a pair) and mutate it via the PAIRINGS action. Positions are client-side (auto-laid-out) for now.
const NODE_W = 112, NODE_H = 46
const STATS = ['HP', 'Atk', 'Def', 'SpA', 'SpD', 'Spe']
const NATURES = ['Adamant', 'Jolly', 'Modest', 'Timid', 'Bold', 'Calm', 'Impish', 'Careful', 'Brave', 'Relaxed', 'Naughty', 'Lonely', 'Serious', 'Hardy']
const PALETTE = [
  { type: 'FILTER_IV', label: '+ IV', hue: '#4ea1ff' },
  { type: 'FILTER_EV', label: '+ EV', hue: '#7c5cff' },
  { type: 'FILTER_NATURE', label: '+ Nature', hue: '#ff9f43' },
  { type: 'FILTER_SHINY', label: '+ Shiny', hue: '#ffd93b' },
  { type: 'SINK_BIOBANK', label: '+ → BioBank', hue: '#34d399' },
  { type: 'SINK_DATA', label: '+ → Data', hue: '#f87171' },
  { type: 'SOURCE', label: '+ Source', hue: '#9aa4b2' },
]
const NODE_META = {
  MON: { hue: '#8b93a1', title: (n) => cap(n.species) },
  FILTER_IV: { hue: '#4ea1ff', title: () => 'IV filter' },
  FILTER_EV: { hue: '#7c5cff', title: () => 'EV filter' },
  FILTER_NATURE: { hue: '#ff9f43', title: () => 'Nature filter' },
  FILTER_SHINY: { hue: '#ffd93b', title: () => 'Shiny filter' },
  SINK_BIOBANK: { hue: '#34d399', title: () => '→ BioBank' },
  SINK_DATA: { hue: '#f87171', title: () => '→ Data' },
  SOURCE: { hue: '#9aa4b2', title: () => 'Source' },
}
const isFilter = (t) => typeof t === 'string' && t.startsWith('FILTER_')
const parseDoc = (s) => {
  try {
    const o = JSON.parse(s || '{}')
    if (Array.isArray(o.threads)) return { threads: o.threads, active: o.active || (o.threads[0] && o.threads[0].id) || null }
    if (Array.isArray(o.nodes)) return { threads: [{ id: 'th1', name: 'Line 1', nodes: o.nodes, edges: Array.isArray(o.edges) ? o.edges : [] }], active: 'th1' }   // migrate old single canvas
  } catch { /* fall through to empty */ }
  return { threads: [], active: null }
}
const defaultConfig = (t) => t === 'FILTER_IV' || t === 'FILTER_EV' ? { HP: 0, Atk: 0, Def: 0, SpA: 0, SpD: 0, Spe: 0 } : t === 'FILTER_SHINY' ? { gate: 'only' } : t === 'FILTER_NATURE' ? { list: [] } : {}
const portsFor = (t) => t === 'MON' ? [{ k: 'eggs', side: 'r', ry: 0.5, kind: 'flow', dir: 'out' }]
  : isFilter(t) ? [{ k: 'in', side: 'l', ry: 0.5, kind: 'flow', dir: 'in' }, { k: 'pass', side: 'r', ry: 0.32, kind: 'flow', dir: 'out' }, { k: 'void', side: 'r', ry: 0.72, kind: 'void', dir: 'out' }]
  : t === 'SOURCE' ? [{ k: 'out', side: 'r', ry: 0.5, kind: 'flow', dir: 'out' }]
  : [{ k: 'in', side: 'l', ry: 0.5, kind: 'flow', dir: 'in' }]
const portByK = (t, k) => portsFor(t).find((p) => p.k === k)
const portXY = (n, p) => ({ x: n.x + (p.side === 'l' ? 0 : NODE_W), y: n.y + p.ry * NODE_H })
const wirePath = (x1, y1, x2, y2) => { const dx = Math.max(28, Math.abs(x2 - x1) * 0.5); return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}` }
const nodeSub = (n) => {
  if (n.type === 'MON') return 'parent'
  if (n.type === 'FILTER_IV' || n.type === 'FILTER_EV') { const c = n.config || {}; const on = STATS.filter((s) => (c[s] || 0) > 0); return on.length ? on.map((s) => `${s}≥${c[s]}`).join(' ') : (n.type === 'FILTER_IV' ? 'any IV' : 'any EV') }
  if (n.type === 'FILTER_NATURE') { const l = (n.config && n.config.list) || []; return l.length ? l.length + ' natures' : 'any nature' }
  if (n.type === 'FILTER_SHINY') { const g = (n.config && n.config.gate) || 'only'; return g === 'no' ? 'non-shiny' : g === 'any' ? 'any' : 'shiny only' }
  if (n.type === 'SOURCE') return 'bred eggs'
  if (n.type === 'SINK_BIOBANK') return 'keep'
  if (n.type === 'SINK_DATA') return 'render'
  return ''
}

// The per-filter config popover (IV/EV mins · shiny gate · nature multi-select). Writes into the node's config.
function ConfigPanel({ node, onSet, onClose }) {
  const d = usePanelDrag()
  const c = node.config || {}, t = node.type
  return (
    <div className="dcfg" ref={d.ref} style={d.style} onMouseDown={(e) => e.stopPropagation()} onWheel={(e) => e.stopPropagation()}>
      <div className="dcfg-h dcfg-drag" onMouseDown={d.start}>{(NODE_META[t] || {}).title?.(node) || 'filter'}<span className="dcfg-x" onClick={onClose} onMouseDown={(e) => e.stopPropagation()}>✕</span></div>
      {(t === 'FILTER_IV' || t === 'FILTER_EV') && (
        <div className="dcfg-grid">
          {STATS.map((s) => (
            <label key={s} className="dcfg-stat"><span>{s}</span>
              <input type="number" min={0} max={t === 'FILTER_IV' ? 31 : 252} value={c[s] || 0}
                onFocus={() => send('console', 'INPUT_FOCUS', { v: true })} onBlur={() => send('console', 'INPUT_FOCUS', { v: false })}
                onChange={(e) => onSet(s, Math.max(0, Math.min(t === 'FILTER_IV' ? 31 : 252, parseInt(e.target.value) || 0)))} />
            </label>
          ))}
          <div className="dim" style={{ gridColumn: '1 / -1', fontSize: 9 }}>pass if every stat ≥ its min</div>
        </div>
      )}
      {t === 'FILTER_SHINY' && (
        <div className="dcfg-row">{[['only', 'shiny only'], ['no', 'non-shiny'], ['any', 'any']].map(([v, l]) => (
          <button key={v} className={`dchip2${(c.gate || 'only') === v ? ' on' : ''}`} onClick={() => onSet('gate', v)}>{l}</button>))}
        </div>
      )}
      {t === 'FILTER_NATURE' && (
        <div className="dcfg-natures">{NATURES.map((nat) => { const on = (c.list || []).includes(nat); return (
          <button key={nat} className={`dchip2${on ? ' on' : ''}`} onClick={() => { const set = new Set(c.list || []); on ? set.delete(nat) : set.add(nat); onSet('list', [...set]) }}>{nat}</button>) })}
        </div>
      )}
    </div>
  )
}

// Gender helpers (Cobblemon gives MALE / FEMALE / GENDERLESS). ^-anchored so "FEMALE" isn't caught by /male/.
const gsym = (g) => /^female/i.test(String(g)) ? '♀' : /^male/i.test(String(g)) ? '♂' : '⚲'
const gcol = (g) => /^female/i.test(String(g)) ? '#ff8fb0' : /^male/i.test(String(g)) ? '#7db6ff' : 'var(--muted)'

// Draggable pop-up - the header is the handle; offsetLeft/Top base + delta/scale keeps it under the cursor.
function usePanelDrag() {
  const ref = useRef(null)
  const [pos, setPos] = useState(null)
  const start = (e) => {
    e.stopPropagation(); e.preventDefault()
    const el = ref.current; if (!el) return
    const s = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--gp-scale')) || 1
    const bx = el.offsetLeft, by = el.offsetTop, sx = e.clientX, sy = e.clientY
    const move = (ev) => setPos({ x: bx + (ev.clientX - sx) / s, y: by + (ev.clientY - sy) / s })
    const up = () => { window.removeEventListener('mousemove', move); window.removeEventListener('mouseup', up) }
    window.addEventListener('mousemove', move); window.addEventListener('mouseup', up)
  }
  return { ref, start, style: pos ? { left: pos.x, top: pos.y, right: 'auto', bottom: 'auto' } : undefined }
}

// Parent inspector - click a mon node OR right-click a parent chip to see IVs / nature / gender / shiny / OT.
function MonInspector({ species, stats, onClose }) {
  const d = usePanelDrag()
  const ivs = stats.ivs || [0, 0, 0, 0, 0, 0]
  const total = ivs.reduce((a, b) => a + (b || 0), 0)
  const g = String(stats.gender || '')
  const sym = gsym(g)
  const ot = stats.ot || ''
  return (
    <div className="dcfg mon-insp" ref={d.ref} style={d.style} onMouseDown={(e) => e.stopPropagation()} onWheel={(e) => e.stopPropagation()}>
      <div className="dcfg-h dcfg-drag" onMouseDown={d.start}>{cap(species)} <span style={{ color: gcol(g) }}>{sym}</span>{stats.shiny ? ' ✨' : ''}<span className="dcfg-x" onClick={onClose} onMouseDown={(e) => e.stopPropagation()}>✕</span></div>
      <div className="iv-grid">
        {STATS.map((k, i) => <div key={k} className="iv-cell"><span className="iv-k">{k}</span><span className="iv-v" style={{ color: ivs[i] >= 31 ? 'var(--green)' : !ivs[i] ? 'var(--dim)' : 'var(--text)' }}>{ivs[i] || 0}</span></div>)}
      </div>
      <div className="insp-row"><span className="dim">IV total</span><span className="mono">{total}/186</span></div>
      <div className="insp-row"><span className="dim">nature</span><span>{stats.nature ? cap(stats.nature) : '-'}</span></div>
      <div className="insp-row"><span className="dim">OT</span><span className="mono" title={ot}>{ot ? (ot.length > 14 ? ot.slice(0, 14) + '…' : ot) : '-'}</span></div>
    </div>
  )
}

function DaemonGraph({ cfg }) {
  const roster = cfg.roster || []
  const maxPairs = cfg.maxPairs || 0
  const [doc, setDoc] = useState(() => parseDoc(cfg.graph))
  const [view, setView] = useState({ x: 0, y: 0, zoom: 1 })
  const [wiring, setWiring] = useState(null)
  const [dragPos, setDragPos] = useState(null)
  const [sel, setSel] = useState(null)
  const [inspectMon, setInspectMon] = useState(null)
  const drag = useRef(null)
  const boxRef = useRef(null)
  const touched = useRef(false)
  const lastPos = useRef(cfg.pos)
  const dash = useChannel('dashboard')   // for the server's shiny-method multipliers (Masuda/Crystal indicator)
  const viewRef = useRef(view); viewRef.current = view

  // Adopt the server graph on pasture-switch, or while still untouched (shell→real-graph arrival), but never
  // overwrite the player's own local edits (the editor is the authority once touched).
  useEffect(() => {
    if (cfg.pos !== lastPos.current) { lastPos.current = cfg.pos; touched.current = false; setSel(null); setView({ x: 0, y: 0, zoom: 1 }); setDoc(parseDoc(cfg.graph)) }
    else if (!touched.current) setDoc(parseDoc(cfg.graph))
  }, [cfg.pos, cfg.graph])

  // Native non-passive wheel → zoom WITHOUT the console pane scrolling (React's onWheel is passive, can't preventDefault).
  useEffect(() => {
    const el = boxRef.current; if (!el) return
    const onWheelNative = (e) => {
      e.preventDefault(); e.stopPropagation()
      const v = viewRef.current
      const nz = Math.max(0.35, Math.min(2.5, v.zoom * (e.deltaY < 0 ? 1.12 : 1 / 1.12)))
      const r = el.getBoundingClientRect(), s = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--gp-scale')) || 1
      const sx = (e.clientX - r.left) / s, sy = (e.clientY - r.top) / s
      setView({ x: sx - ((sx - v.x) / v.zoom) * nz, y: sy - ((sy - v.y) / v.zoom) * nz, zoom: nz })
    }
    el.addEventListener('wheel', onWheelNative, { passive: false })
    return () => el.removeEventListener('wheel', onWheelNative)
  }, [doc.active])   // doc is declared at the top; `active` (derived below) would be a TDZ ReferenceError → blank app

  const threads = doc.threads || []
  const active = threads.find((t) => t.id === doc.active) || threads[0] || null
  const nodes = active ? (active.nodes || []) : []
  const edges = active ? (active.edges || []) : []
  const speciesOf = (id) => { const m = roster.find((r) => r.id === id); return m ? m.species : String(id).slice(0, 6) }
  const monThread = (id) => threads.find((t) => (t.nodes || []).some((n) => n.type === 'MON' && n.monId === id))
  const renderNodes = nodes.map((n) => n.type === 'MON' ? { ...n, species: speciesOf(n.monId) } : n)
  const byId = (id) => renderNodes.find((n) => n.id === id)
  const liveNode = (n) => (n && dragPos && dragPos.id === n.id) ? { ...n, x: dragPos.x, y: dragPos.y } : n
  const monStats = (id) => { const m = roster.find((r) => r.id === id); return (m && m.stats) || {} }
  const monNodesActive = nodes.filter((n) => n.type === 'MON')
  const methods = (dash && dash.shinyMethods) || {}
  let masuda = false, crystal = false
  if (monNodesActive.length === 2) {
    const a = monStats(monNodesActive[0].monId), b = monStats(monNodesActive[1].monId)
    masuda = methods.masuda > 1 && !!a.ot && !!b.ot && a.ot !== b.ot
    crystal = methods.crystal > 1 && (!!a.shiny || !!b.shiny)
  }
  const pairValid = (() => {   // a pair needs ♂+♀, or exactly one Ditto (Ditto can't breed Ditto)
    if (monNodesActive.length !== 2) return true
    const a = monStats(monNodesActive[0].monId), b = monStats(monNodesActive[1].monId)
    const dA = speciesOf(monNodesActive[0].monId).toLowerCase() === 'ditto', dB = speciesOf(monNodesActive[1].monId).toLowerCase() === 'ditto'
    const gA = String(a.gender || '').toLowerCase(), gB = String(b.gender || '').toLowerCase()
    const mf = (gA === 'male' && gB === 'female') || (gA === 'female' && gB === 'male')
    return (dA !== dB) || mf
  })()

  const scale = () => parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--gp-scale')) || 1
  const toGraph = (cx, cy) => { const r = boxRef.current.getBoundingClientRect(), s = scale(); return { x: ((cx - r.left) / s - view.x) / view.zoom, y: ((cy - r.top) / s - view.y) / view.zoom } }

  // persistence + pairing derivation (each line's ≤2 parents → a bucket, so the breeder pairs them)
  const persist = (nd) => { touched.current = true; setDoc(nd); send('pasture', 'GRAPH', { pos: cfg.pos, json: JSON.stringify(nd) }) }
  // Derive pairings from thread ORDER (bucket = k+1, clamped to the server's 1..8) - NOT gated on the live Kernel's
  // maxPairs, so removing/swapping the Kernel never wipes the graph's pairs. The breeder clamps buckets > maxPairs.
  const pushPairings = (nd) => { const p = {}; nd.threads.forEach((t, k) => { const mons = (t.nodes || []).filter((n) => n.type === 'MON').map((n) => n.monId); if (mons.length >= 2 && k < 8) { p[mons[0]] = k + 1; p[mons[1]] = k + 1 } }); send('pasture', 'PAIRINGS', { pos: cfg.pos, pairings: p }) }
  const commit = (nd, repair) => { persist(nd); if (repair) pushPairings(nd) }
  const updateActive = (mut, repair = false) => { if (!active) return; const nt = mut({ ...active, nodes: [...nodes], edges: [...edges] }); commit({ ...doc, threads: threads.map((t) => t.id === active.id ? nt : t) }, repair) }

  // thread tabs (breeding lines)
  const capLines = maxPairs   // no Kernel (maxPairs 0) → can't add new lines; existing lines still render + persist
  const addThread = () => { if (threads.length >= capLines) return; const id = 'th' + Date.now().toString(36) + Math.floor(Math.random() * 1000); setSel(null); setView({ x: 0, y: 0, zoom: 1 }); commit({ threads: [...threads, { id, name: 'Line ' + (threads.length + 1), nodes: [], edges: [] }], active: id }, false) }
  const renameThread = (id, name) => commit({ ...doc, threads: threads.map((t) => t.id === id ? { ...t, name } : t) }, false)
  const delThread = (id) => { const nt = threads.filter((t) => t.id !== id); setSel(null); commit({ threads: nt, active: nt[0] ? nt[0].id : null }, true) }
  const switchThread = (id) => { setSel(null); setView({ x: 0, y: 0, zoom: 1 }); persist({ ...doc, active: id }) }

  // assign parents to the active line (a mon breeds in one line; max 2 parents per line)
  const toggleMon = (monId) => {
    if (!active) return
    const mid = 'mon:' + monId
    if (nodes.some((n) => n.id === mid)) { commit({ ...doc, threads: threads.map((t) => t.id === active.id ? { ...t, nodes: t.nodes.filter((n) => n.id !== mid), edges: (t.edges || []).filter((e) => e.from !== mid && e.to !== mid) } : t) }, true); return }
    const cnt = nodes.filter((n) => n.type === 'MON').length
    if (cnt >= 2) return
    commit({ ...doc, threads: threads.map((t) => {
      if (t.id === active.id) {
        // a pair shares its egg stream - auto-wire the new parent to whatever the existing parent already feeds
        let targets = [...new Map((t.edges || []).filter((e) => { const fn = (t.nodes || []).find((n) => n.id === e.from); return fn && fn.type === 'MON' && e.fromPort === 'eggs' }).map((e) => [e.to + '/' + e.toPort, { to: e.to, toPort: e.toPort }])).values()]
        let extraNodes = []
        if (targets.length === 0) {
          // review M3: an unwired line silently stalled eggs in the tray. First parent auto-creates a
          // BioBank sink and wires to it - the safe default (keep everything); reroute freely after.
          let sink = (t.nodes || []).find((n) => n.type === 'SINK_BIOBANK')
          if (!sink) { sink = { id: 'sink:auto', type: 'SINK_BIOBANK', x: 320, y: 60 }; extraNodes.push(sink) }
          targets = [{ to: sink.id, toPort: 'in' }]
        }
        return { ...t, nodes: [...(t.nodes || []), ...extraNodes, { id: mid, type: 'MON', monId, x: 26, y: 20 + cnt * 76 }], edges: [...(t.edges || []), ...targets.map((s) => ({ from: mid, fromPort: 'eggs', to: s.to, toPort: s.toPort }))] }
      }
      if ((t.nodes || []).some((n) => n.id === mid)) return { ...t, nodes: t.nodes.filter((n) => n.id !== mid), edges: (t.edges || []).filter((e) => e.from !== mid && e.to !== mid) }
      return t
    }) }, true)
  }

  // node + edge mutations on the active line
  const addNode = (type) => {
    const r = boxRef.current.getBoundingClientRect()
    const c = toGraph(r.left + r.width / 2, r.top + r.height / 2)
    let x = Math.round(c.x - NODE_W / 2), y = Math.round(c.y - NODE_H / 2)
    // cascade to the first free spot - stacking new nodes dead-center made each add look like it REPLACED the last
    for (let guard = 0; guard < 60 && nodes.some((n) => Math.abs(n.x - x) < NODE_W * 0.6 && Math.abs(n.y - y) < NODE_H * 0.9); guard++) { x += 34; y += 40 }
    const id = 'n' + Date.now().toString(36) + Math.floor(Math.random() * 1000)
    updateActive((t) => { t.nodes = [...t.nodes, { id, type, x, y, config: defaultConfig(type) }]; return t })
    setSel(id)
  }
  const delNode = (id) => { const node = nodes.find((n) => n.id === id); updateActive((t) => { t.nodes = t.nodes.filter((n) => n.id !== id); t.edges = t.edges.filter((e) => e.from !== id && e.to !== id); return t }, !!(node && node.type === 'MON')); if (sel === id) setSel(null) }
  const setConfig = (id, key, val) => updateActive((t) => { t.nodes = t.nodes.map((n) => n.id === id ? { ...n, config: { ...(n.config || {}), [key]: val } } : n); return t })
  const setNodePos = (n, x, y) => updateActive((t) => { t.nodes = t.nodes.map((z) => z.id === n.id ? { ...z, x, y } : z); return t })
  const addEdge = (from, fromPort, to, toPort) => {
    if (from === to) return
    updateActive((t) => {
      const fromNode = t.nodes.find((n) => n.id === from)
      const monIds = new Set(t.nodes.filter((n) => n.type === 'MON').map((n) => n.id))
      if (fromNode && fromNode.type === 'MON' && fromPort === 'eggs') {
        // a pair shares ONE egg stream → wire BOTH parents to this target, and move the whole pair off any old one
        const kept = t.edges.filter((e) => !(monIds.has(e.from) && e.fromPort === 'eggs') && !(e.to === to && e.toPort === toPort))
        const pair = [...monIds].map((id) => ({ from: id, fromPort: 'eggs', to, toPort }))
        t.edges = [...kept, ...pair]
      } else {
        t.edges = [...t.edges.filter((e) => !(e.to === to && e.toPort === toPort)), { from, fromPort, to, toPort }]
      }
      return t
    })
  }
  const delEdge = (e) => updateActive((t) => {
    const fromNode = t.nodes.find((n) => n.id === e.from)
    if (fromNode && fromNode.type === 'MON') {   // deleting a pair wire removes BOTH parents' eggs edges to that target
      const monIds = new Set(t.nodes.filter((n) => n.type === 'MON').map((n) => n.id))
      t.edges = t.edges.filter((x) => !(monIds.has(x.from) && x.fromPort === 'eggs' && x.to === e.to && x.toPort === e.toPort))
    } else {
      t.edges = t.edges.filter((x) => !(x.from === e.from && x.fromPort === e.fromPort && x.to === e.to && x.toPort === e.toPort))
    }
    return t
  })
  const tryConnect = (aN, aP, bN, bP) => { if (aN.id === bN.id) return; let out, inp; if (aP.dir === 'out' && bP.dir === 'in') { out = [aN, aP]; inp = [bN, bP] } else if (bP.dir === 'out' && aP.dir === 'in') { out = [bN, bP]; inp = [aN, aP] } else return; addEdge(out[0].id, out[1].k, inp[0].id, inp[1].k) }
  const portHit = (cx, cy) => { const gpt = toGraph(cx, cy); for (const raw of renderNodes) { const n = liveNode(raw); for (const port of portsFor(n.type)) { const pp = portXY(n, port); if (Math.hypot(pp.x - gpt.x, pp.y - gpt.y) <= 13) return { node: raw, port } } } return null }

  const bind = () => { window.addEventListener('mousemove', onMove); window.addEventListener('mouseup', onUp) }
  const onCanvasDown = (e) => { setSel(null); drag.current = { type: 'pan', sx: e.clientX, sy: e.clientY, bx: view.x, by: view.y, s: scale() }; bind() }
  const onNodeDown = (e, n) => { e.stopPropagation(); setSel(n.id); const gpt = toGraph(e.clientX, e.clientY); drag.current = { type: 'node', node: n, gx: gpt.x - n.x, gy: gpt.y - n.y, cx: n.x, cy: n.y }; setDragPos({ id: n.id, x: n.x, y: n.y }); bind() }
  const onPortDown = (e, n, port) => { e.stopPropagation(); drag.current = { type: 'wire', node: n, port }; const pp = portXY(n, port); setWiring({ node: n, port, x: pp.x, y: pp.y }); bind() }
  const onMove = (e) => {
    const d = drag.current; if (!d) return
    if (d.type === 'pan') setView((v) => ({ ...v, x: d.bx + (e.clientX - d.sx) / d.s, y: d.by + (e.clientY - d.sy) / d.s }))
    else if (d.type === 'node') { const gpt = toGraph(e.clientX, e.clientY); d.moved = true; d.cx = Math.round(gpt.x - d.gx); d.cy = Math.round(gpt.y - d.gy); setDragPos({ id: d.node.id, x: d.cx, y: d.cy }) }
    else if (d.type === 'wire') { const gpt = toGraph(e.clientX, e.clientY); setWiring({ node: d.node, port: d.port, x: gpt.x, y: gpt.y }) }
  }
  const onUp = (e) => {
    const d = drag.current; drag.current = null
    window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp)
    if (!d) return
    if (d.type === 'node') { if (d.moved) setNodePos(d.node, d.cx, d.cy); setDragPos(null) }
    else if (d.type === 'wire') { const hit = portHit(e.clientX, e.clientY); if (hit) tryConnect(d.node, d.port, hit.node, hit.port); setWiring(null) }
  }
  const onWheel = (e) => {
    e.preventDefault()
    const nz = Math.max(0.35, Math.min(2.5, view.zoom * (e.deltaY < 0 ? 1.12 : 1 / 1.12)))
    const r = boxRef.current.getBoundingClientRect(), s = scale()
    const sx = (e.clientX - r.left) / s, sy = (e.clientY - r.top) / s
    setView({ x: sx - ((sx - view.x) / view.zoom) * nz, y: sy - ((sy - view.y) / view.zoom) * nz, zoom: nz })
  }

  return (
    <div className="daemon-wrap">
      <div className="thread-tabs" onMouseDown={(e) => e.stopPropagation()}>
        {threads.map((t) => { const on = active && t.id === active.id; return (
          <div key={t.id} className={`ttab${on ? ' on' : ''}`} onClick={() => on ? null : switchThread(t.id)}>
            {on
              ? <input className="tab-name" value={t.name} maxLength={24} onFocus={() => send('console', 'INPUT_FOCUS', { v: true })} onBlur={() => send('console', 'INPUT_FOCUS', { v: false })} onChange={(e) => renameThread(t.id, e.target.value)} />
              : <span>{t.name || 'line'}</span>}
            <span className="tab-x" title="delete line" onClick={(e) => { e.stopPropagation(); delThread(t.id) }}>✕</span>
          </div>
        )})}
        <button className="tab-add" disabled={threads.length >= capLines} title={threads.length >= capLines ? `Kernel allows ${maxPairs} lines` : 'new breeding line'} onClick={addThread}>+ line</button>
      </div>
      {!active ? (
        <div className="muted" style={{ fontSize: 12, padding: '18px 4px' }}>No breeding lines yet - <b>+ line</b> to start one, add two parents, then wire their egg pipeline.{maxPairs ? '' : ' (Slot a Kernel first.)'}</div>
      ) : (<>
        <div className="thread-roster" onMouseDown={(e) => e.stopPropagation()}>
          <span className="dim" style={{ fontSize: 10 }}>parents</span>
          {roster.length === 0 ? <span className="dim" style={{ fontSize: 10 }}>- tether mons in-world</span> : roster.map((m) => { const home = monThread(m.id); const here = home && home.id === active.id; return (
            <button key={m.id} className={`monchip${here ? ' here' : home ? ' busy' : ''}`} title={(home ? (here ? 'in this line - click to remove' : 'in ' + home.name) : 'click to add') + ' · right-click to inspect'} onClick={() => toggleMon(m.id)} onContextMenu={(e) => { e.preventDefault(); setInspectMon(m.id) }}>{cap(m.species)} <span style={{ color: gcol(m.stats?.gender) }}>{gsym(m.stats?.gender)}</span>{home && !here ? ` · ${home.name}` : ''}</button>
          )})}
        </div>
        <div className="daemon-palette" onMouseDown={(e) => e.stopPropagation()}>
          {PALETTE.filter((p) => p.type !== 'SOURCE').map((p) => <button key={p.type} className="dchip" style={{ borderColor: p.hue, color: p.hue }} onClick={() => addNode(p.type)}>{p.label}</button>)}
          <span className="dim mono" style={{ marginLeft: 'auto', fontSize: 10 }}>{Math.round(view.zoom * 100)}%</span>
        </div>
        {monNodesActive.length === 2 && (
          <div className="shinybadge" onMouseDown={(e) => e.stopPropagation()}>
            {!pairValid ? <span className="sb-bad" title="A pair needs one male + one female, or exactly one Ditto (Ditto can't breed Ditto).">⚠ this pair can't breed - need ♂ + ♀, or exactly one Ditto</span> : <>
              <span className="dim">shiny breeding:</span>
              <span className={masuda ? 'sb-on' : 'sb-off'} title={methods.masuda > 1 ? 'parents have different original trainers → boosted shiny odds' : 'Masuda not enabled on this server'}>✨ Masuda {masuda ? '✓' : '-'}</span>
              <span className={crystal ? 'sb-on' : 'sb-off'} title={methods.crystal > 1 ? `shiny parent → ×${methods.crystal} shiny odds (GP guarantees ×2 even if the server ships Crystal off; higher server values win)` : 'add a shiny parent to boost odds'}>💎 Crystal {crystal ? '✓' : '-'}</span>
            </>}
          </div>
        )}
        <div className="daemon-canvas" ref={boxRef} onMouseDown={onCanvasDown}>
          <div className="daemon-view" style={{ transform: `translate(${view.x}px,${view.y}px) scale(${view.zoom})`, transformOrigin: '0 0' }}>
            <svg className="daemon-wires">
              {(() => { const ms = renderNodes.filter((n) => n.type === 'MON'); if (ms.length !== 2) return null; const a = liveNode(ms[0]), b = liveNode(ms[1]); return <line x1={a.x + NODE_W / 2} y1={a.y + NODE_H / 2} x2={b.x + NODE_W / 2} y2={b.y + NODE_H / 2} className="pairlink" /> })()}
              {edges.map((e, i) => { const fn = byId(e.from), tn = byId(e.to); if (!fn || !tn) return null; const fp = portByK(fn.type, e.fromPort), tp = portByK(tn.type, e.toPort); if (!fp || !tp) return null; const a = portXY(liveNode(fn), fp), b = portXY(liveNode(tn), tp); return <path key={`${e.from}:${e.fromPort}>${e.to}:${e.toPort}`} d={wirePath(a.x, a.y, b.x, b.y)} stroke={fp.kind === 'void' ? 'var(--red)' : 'var(--cyan)'} className="dwire" onClick={(ev) => { ev.stopPropagation(); delEdge(e) }} /> })}
              {wiring && (() => { const pp = portXY(liveNode(wiring.node), wiring.port); return <path d={wirePath(pp.x, pp.y, wiring.x, wiring.y)} className="dwire live" /> })()}
            </svg>
            {renderNodes.map((raw) => { const n = liveNode(raw); const meta = NODE_META[n.type] || NODE_META.SOURCE; const accent = meta.hue; return (
              <div key={n.id} className={`gnode${sel === n.id ? ' sel' : ''}${n.type === 'MON' ? ' mon' : ''}`} style={{ left: n.x, top: n.y, width: NODE_W, height: NODE_H, borderColor: accent }} onMouseDown={(e) => onNodeDown(e, n)}>
                <span className="gnode-bar" style={{ background: accent }} />
                <div className="gnode-body"><span className="gnode-title">{meta.title(n)}</span><span className="gnode-sub">{nodeSub(n)}</span></div>
                {n.type !== 'SOURCE' && <span className="gnode-x" title={n.type === 'MON' ? 'remove parent' : 'delete node'} onMouseDown={(e) => { e.stopPropagation(); delNode(n.id) }}>✕</span>}
                {portsFor(n.type).map((port) => { const pp = portXY(n, port); return <span key={port.k} className="gport" title={port.k} onMouseDown={(e) => onPortDown(e, n, port)}
                  style={{ left: pp.x - n.x - 6, top: pp.y - n.y - 6, background: port.kind === 'void' ? 'var(--red)' : (port.dir === 'out' ? 'var(--cyan)' : '#2b6cb0'), borderRadius: '50%' }} /> })}
              </div>
            )})}
          </div>
          {sel && byId(sel) && isFilter(byId(sel).type) && <ConfigPanel node={byId(sel)} onSet={(k, v) => setConfig(sel, k, v)} onClose={() => setSel(null)} />}
          {(() => { const iid = inspectMon || (sel && byId(sel) && byId(sel).type === 'MON' ? byId(sel).monId : null); return iid ? <MonInspector species={speciesOf(iid)} stats={monStats(iid)} onClose={() => { setInspectMon(null); setSel(null) }} /> : null })()}
          <div className="daemon-hud">{active.name} · {nodes.filter((n) => n.type === 'MON').length}/2 parents · scroll=zoom · drag=pan · drag a port to wire</div>
        </div>
      </>)}
    </div>
  )
}

// The editable pasture config - shown when you right-click a pasture with the Notebook (replaces the owo screen).
// Wired to the server over the `pasture` bridge channel: NAME · PAIRINGS · CLAIM (link) · KERNEL · CLOSE (← back).
// The player-facing egg-ingest feed - the void-log trust feature (kept K · voided V by which filter · recent).
function EggLogStrip() {
  const d = useChannel('eggLog')
  const entries = (d && d.entries) || []
  return (
    <div className="egglog">
      <div className="egglog-h">
        <span style={{ color: 'var(--green)' }}>✓ {d ? d.kept : 0} kept</span>
        <span className="dim">·</span>
        <span style={{ color: 'var(--red)' }}>✕ {d ? d.voided : 0} voided</span>
        <span style={{ flex: 1 }} />
        <span className="dim" style={{ fontSize: 9 }}>recent activity</span>
      </div>
      <div className="egglog-feed">
        {entries.length === 0
          ? <span className="dim" style={{ fontSize: 10 }}>no eggs yet - link a Kernel'd pasture and wire a pipeline</span>
          : entries.map((e, i) => (
            <div key={`${entries.length - i}:${e.species}:${e.voided ? 1 : 0}`} className="egglog-row">
              <span style={{ color: e.voided ? 'var(--red)' : 'var(--green)' }}>{e.voided ? '✕' : '✓'}</span>
              <span style={{ color: 'var(--text)', fontWeight: 500 }}>{cap(e.species)}</span>
              <span className="dim">{e.voided ? (e.filter ? `voided · ${e.filter}` : 'voided') : 'kept'}</span>
            </div>
          ))}
      </div>
    </div>
  )
}

function PastureConfig({ cfg }) {
  const [name, setName] = useState(cfg.name || '')
  useEffect(() => { setName(cfg.name || '') }, [cfg.pos])   // reset the field when switching pastures
  useEffect(() => { if (cfg.present && !cfg.loading) send('console', 'PASTURE_READY', {}) }, [cfg.loading, cfg.pos])   // tell Java to lift the native loading overlay
  if (cfg.loading) return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="grn" style={{ cursor: 'pointer', fontWeight: 600 }} onClick={() => send('pasture', 'CLOSE', {})}>← console</span>
        <span style={{ flex: 1 }} />
        <span className="dim mono" style={{ fontSize: 11 }}>pasture config</span>
      </div>
      <div className="loadsplash"><div className="spinner" /><span>loading pasture…</span></div>
    </div>
  )
  const hasKernel = !!cfg.tier
  const maxPairs = cfg.maxPairs || 0
  const roster = cfg.roster || []
  const saveName = () => { if (name !== cfg.name) send('pasture', 'NAME', { pos: cfg.pos, name }) }
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="grn" style={{ cursor: 'pointer', fontWeight: 600 }} onClick={() => send('pasture', 'CLOSE', {})}>← console</span>
        <span style={{ flex: 1 }} />
        <span className="dim mono" style={{ fontSize: 11 }}>pasture config</span>
      </div>
      <div className="row" style={{ marginBottom: 8, gap: 8 }}>
        <input className="gp-input" value={name} maxLength={64} placeholder="Name this pasture"
          onFocus={() => send('console', 'INPUT_FOCUS', { v: true })}
          onChange={(e) => setName(e.target.value)}
          onBlur={() => { send('console', 'INPUT_FOCUS', { v: false }); saveName() }}
          onKeyDown={(e) => { if (e.key === 'Enter') { saveName(); e.target.blur() } }} />
        <button className="btn" style={{ color: cfg.linked ? 'var(--green)' : 'var(--muted)', borderColor: cfg.linked ? '#2e5a47' : 'var(--line)' }}
          onClick={() => send('pasture', 'CLAIM', { pos: cfg.pos })}>{cfg.linked ? '🔗 linked' : 'link'}</button>
      </div>
      {(cfg.health || []).length > 0 && (
        <div className="hstrip">
          {cfg.health.map((f) => <span key={f.id} className="hflag" title={f.id}><span>{f.icon}</span>{f.text}</span>)}
        </div>
      )}
      <div className="row inset" style={{ padding: 8, marginBottom: 10, borderRadius: 8 }}>
        <span className="dim" style={{ fontSize: 10, letterSpacing: 1 }}>KERNEL</span>
        <span style={{ flex: 1 }} />
        {hasKernel
          ? <span className="cyn mono" style={{ fontSize: 12 }}>{cfg.kernel?.name ? `“${cfg.kernel.name}” · ` : ''}{cap(cfg.tier.toLowerCase())} · {maxPairs} pairs</span>
          : <span className="amb" style={{ fontSize: 11 }}>none - slot a Kernel for multi-pair breeding</span>}
        <button className="btn" style={{ marginLeft: 8 }} onClick={() => send('pasture', 'KERNEL', { pos: cfg.pos })}>
          {hasKernel ? 'remove' : 'slot from inventory'}</button>
      </div>
      {hasKernel && cfg.kernel && Object.keys(cfg.kernel).length > 0 && (
        <div className="kload" style={{ marginBottom: 10 }}>
          <span className="dim" style={{ fontSize: 9, letterSpacing: 1 }}>LOADOUT</span>
          {cfg.kernel.nature && <span className="kchip">🧬 {cap(cfg.kernel.nature)}</span>}
          {cfg.kernel.ball && <span className="kchip">◉ {cap(shortId(cfg.kernel.ball))}</span>}
          {cfg.kernel.ev && <span className="kchip">EV {cfg.kernel.ev}</span>}
          {cfg.kernel.ha && <span className="kchip">✦ hidden ability</span>}
          {cfg.kernel.moves && <span className="kchip">📖 egg moves</span>}
          {(cfg.kernel.chips || []).map((c) => <span className="kchip" key={c}>{c}</span>)}
        </div>
      )}
      <div className="dim" style={{ fontSize: 11, marginBottom: 6 }}>
        Daemon · one tab per breeding line - add two parents, then wire their eggs through filters to BioBank / Data{maxPairs ? '' : ' · slot a Kernel to start'}
      </div>
      <DaemonGraph cfg={cfg} />
      <EggLogStrip />
    </div>
  )
}
const pairHue = (b) => `hsl(${(b * 67) % 360} 70% 62%)`
// ── Specimens (mon compression v1): party mon → lossless data on a Specimen Disk ──
function SpecimensTab() {
  const d = useChannel('specimens')
  const party = d?.party || []
  const blanks = d?.blanks ?? 0
  const busy = !!d?.busy
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="row" style={{ marginBottom: 8 }}>
        <span className="h">Specimens</span>
        <span style={{ flex: 1 }} />
        <span className="dim mono" style={{ fontSize: 10 }}>💾 {blanks} blank disk{blanks === 1 ? '' : 's'}</span>
      </div>
      <div className="dim" style={{ fontSize: 11, marginBottom: 10, lineHeight: 1.5 }}>
        Archive a party Pokémon onto a <b>Specimen Disk</b> - lossless, IVs and all. Right-click the written
        disk to release it (party first, PC overflow); the media survives as a blank. Your last party member
        can't be archived{busy ? ' · 🔒 party busy (battle)' : ''}.
      </div>
      {party.length === 0 ? (
        <div className="dim" style={{ fontSize: 11 }}>party is empty (or still syncing…)</div>
      ) : party.map((m) => (
        <div key={m.slot} className="brow">
          <span className="mono dim" style={{ fontSize: 10, width: 20 }}>#{m.slot + 1}</span>
          <span style={{ flex: 1, color: m.shiny ? 'var(--amber)' : 'var(--text)' }}>
            {m.shiny ? '✨ ' : ''}{cap(m.species)} <span className="dim mono" style={{ fontSize: 10 }}>Lv.{m.level} · {m.gender}</span>
          </span>
          <button className="btn go" disabled={busy || blanks < 1 || party.length <= 1}
            title={busy ? 'party is busy' : blanks < 1 ? 'craft a blank Specimen Disk (blank data disk + amethyst + echo shard)' : party.length <= 1 ? 'your last party member stays with you' : 'archive to disk'}
            onClick={() => send('specimens', 'COMPRESS_MON', { slot: m.slot })}>ARCHIVE</button>
        </div>
      ))}
    </div>
  )
}

// ── Game Corner: Voltorb Flip with PMD Collab portraits (fan-made only; CREDITS-PMD.md).
// Server-authoritative - the client never receives a face-down tile's value. ──
const VOLT_COLORS = ['#9aa4ab', '#5ab0ff', '#6fd66f', '#ffce4f', '#ff8c42', '#ff5c5c']
const voltColor = (n) => VOLT_COLORS[Math.max(0, Math.min(5, n ?? 0))]
const happySpecies = (i, level) => HAPPY_KEYS[(i * 13 + (level || 1) * 7) % HAPPY_KEYS.length]
const sadSpecies = (i, level) => SAD_KEYS[(i * 11 + (level || 1) * 5) % SAD_KEYS.length]
const SadIcon = () => <img src={VOLTORB_ANGRY} alt="voltorbs" title="hidden Voltorbs in this line" />

function VoltorbCabinet({ onBack }) {
  const d = useChannel('arcade')
  if (!d) return <Empty title="…" msg="loading the Game Corner channel" />
  const tiles = d.tiles || []
  const rows = d.rows || []
  const cols = d.cols || []
  const cells = []
  if (d.playing || d.over) {
    for (let r = 0; r < 5; r++) {
      for (let c = 0; c < 5; c++) {
        const i = r * 5 + c
        const v = tiles[i]
        const revealed = v != null && v >= 0
        const sp = v === 0 ? sadSpecies(i, d.level) : happySpecies(i, d.level)
        cells.push(
          <div key={i}
            className={`vf-tile${revealed ? ' flip' : ''}${revealed && v === 0 ? ' volt' : ''}${d.over && revealed && !(d.flipped || [])[i] ? ' reveal' : ''}`}
            title={revealed ? (v === 0 ? `${cap(sp)} is devastated.` : `${cap(sp)}! ×${v}`) : d.playing ? 'flip' : ''}
            onClick={() => { if (!revealed && d.playing) send('arcade', 'ARCADE_FLIP', { tile: i }) }}>
            {revealed && <img src={v === 0 ? SAD[sp] : HAPPY[sp]} alt={v === 0 ? 'sad' : 'happy'} />}
            {revealed && v >= 2 && <span className="vf-badge">×{v}</span>}
          </div>)
      }
      const rc = rows[r] || {}
      cells.push(<div key={`r${r}`} className="vf-chip"><b>{rc.sum ?? '?'}</b><span style={{ color: voltColor(rc.volts) }}><SadIcon />{rc.volts ?? '?'}</span></div>)
    }
    for (let c = 0; c < 5; c++) {
      const cc = cols[c] || {}
      cells.push(<div key={`c${c}`} className="vf-chip"><b>{cc.sum ?? '?'}</b><span style={{ color: voltColor(cc.volts) }}><SadIcon />{cc.volts ?? '?'}</span></div>)
    }
    cells.push(<div key="corner" />)
  }
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="vf-wrap">
        <div className="row" style={{ gap: 14, alignItems: 'baseline' }}>
          <button className="btn" onClick={onBack} title="back to the Game Corner lobby">‹</button>
          <span className="h">DAEMON FLIP · Lv.{d.level}</span>
          <span className="grn" style={{ fontWeight: 600 }}>pot {fmt(d.coins)}</span>
          {d.dailyLeft >= 0 && <span className="dim" style={{ fontSize: 11 }} title="the house's daily ledger">house pays {fmt(d.dailyLeft)} more today</span>}
        </div>
        {(d.playing || d.over) && <div className="vf-board">{cells}</div>}
        {d.over && <span className={d.cleared ? 'grn' : ''} style={{ fontSize: 12, color: d.cleared ? undefined : '#ff6b81' }}>
          {d.cleared ? `CLEAR! Everyone's happy - ${fmt(d.coins)} paid, the machine levels up.` : 'Oh no. The pot is gone - the machine takes pity and eases up a level.'}</span>}
        <div className="row" style={{ gap: 8 }}>
          <button className="btn go" onClick={() => send('arcade', 'ARCADE_NEW', {})}>{d.playing ? 'FORFEIT · NEW BOARD' : 'NEW BOARD'}</button>
          {d.playing && d.coins > 0 && <button className="btn" onClick={() => send('arcade', 'ARCADE_CASHOUT', {})}>CASH OUT {fmt(d.coins)}</button>}
        </div>
        {!d.playing && !d.over && <span className="dim" style={{ fontSize: 12, maxWidth: 440, textAlign: 'center' }}>
          Each line's chip shows its value sum and how many hidden Voltorbs it holds (silver = a clean line).
          Flip happy starters (×2/×3) to multiply the pot; cash out any time; find every ×2 and ×3 to clear.
          Flip a sad one and the pot is gone. Winnings are Game Corner Coins - spend them at the lobby shop.
        </span>}
        <span className="dim" style={{ fontSize: 9 }}>portraits · PMD Sprite Collab (fan-made, credited - see the mod's CREDITS)</span>
      </div>
    </div>
  )
}

// ── Game Corner lobby: three cabinets (DAEMON FLIP · TREELINE · TOP DECK) ──
function GameCorner() {
  const [cabinet, setCabinet] = useState(null)
  const d = useChannel('arcade')
  const icons = useChannel('icons')
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t) }, [])
  if (cabinet === 'vf') return <VoltorbCabinet onBack={() => setCabinet(null)} />
  if (cabinet === 'tl') return <TreelineCabinet onBack={() => setCabinet(null)} />
  if (cabinet === 'td') return <TopDeckCabinet onBack={() => setCabinet(null)} />
  if (cabinet === 'sl') return <SlotsCabinet onBack={() => setCabinet(null)} />
  const coins = d?.gcoins ?? 0
  const shop = d?.shop
  const left = shop ? Math.max(0, Math.floor((shop.endsAt - now) / 1000)) : 0
  const mm = String(Math.floor(left / 60)).padStart(2, '0')
  const ss = String(left % 60).padStart(2, '0')
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="vf-wrap">
        <div className="row" style={{ gap: 14, alignItems: 'baseline' }}>
          <span className="h">🎰 Game Corner</span>
          <span className="amb" style={{ fontWeight: 700 }}>🪙 {fmt(coins)} Coins</span>
        </div>
        <div className="row" style={{ gap: 12, flexWrap: 'wrap', justifyContent: 'center' }}>
          <button className="tl-cab" onClick={() => setCabinet('vf')}>
            <span className="tl-cab-name">DAEMON FLIP</span>
            <span className="tl-cab-sub">cabinet 01 · deduction · flip the happy ones</span>
          </button>
          <button className="tl-cab" onClick={() => setCabinet('tl')}>
            <span className="tl-cab-name">TREELINE</span>
            <span className="tl-cab-sub">cabinet 02 · scorbunny recovery · decoys snitch</span>
          </button>
          <button className="tl-cab" onClick={() => setCabinet('td')}>
            <span className="tl-cab-name">TOP DECK</span>
            <span className="tl-cab-sub">cabinet 03 · card sharping · let it ride</span>
          </button>
          <button className="tl-cab" onClick={() => setCabinet('sl')}>
            <span className="tl-cab-name">SLOTS</span>
            <span className="tl-cab-sub">cabinet 04 · the classic · three voltorb pays 100x</span>
          </button>
        </div>
        {shop && (
          <div className="gc-shop">
            <div className="gc-shop-head">
              <span className="h">Prize Counter</span>
              <span className="dim" style={{ fontSize: 10 }}>your rotation · refreshes on every buy · new stock in <span className="mono amb">{mm}:{ss}</span></span>
            </div>
            <div className="gc-offers">
              {(shop.offers || []).map((o, i) => (
                <div key={`${o.name}${i}`} className="gc-offer">
                  <span className="gc-offer-art">{icons?.[o.id]
                    ? <img src={icons[o.id]} alt="" draggable={false} />
                    : <span className="gc-offer-noart">◇</span>}</span>
                  <span className="gc-offer-name">{o.name}</span>
                  <div className="gc-offer-row">
                    <span className="gc-price">🪙 {fmt(o.price)}</span>
                    <button className="btn go" disabled={coins < o.price}
                      title={coins < o.price ? 'not enough Coins - the machines await' : 'redeem'}
                      onClick={() => send('arcade', 'SHOP_BUY', { slot: i, item: o.name })}>REDEEM</button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
        <span className="dim" style={{ fontSize: 9 }}>Coins never convert to Data - the arcade only pays in prizes · art: PMD Sprite Collab (fan-made, credited)</span>
      </div>
    </div>
  )
}

// ── TREELINE (Deuce's artifact, server-authoritative port). Scorbunny sheet: 4 frames × 8 dirs. ──
const TL_DIR = { S: 0, SE: 1, E: 2, NE: 3, N: 4, NW: 5, W: 6, SW: 7 }
function Bunny({ dir = 'S', walking = false, dim = false, speed = 0.6 }) {
  return (
    <span className={`tl-bun${walking ? ' tl-bun-walk' : ''}${dim ? ' tl-bun-dim' : ''}`}
      style={{ '--row': TL_DIR[dir], '--spd': `${speed}s`, backgroundImage: `url(${SCORBUNNY_SHEET})` }} />
  )
}
function TlTree() {
  return (
    <svg viewBox="0 0 60 80" style={{ width: '100%', height: '100%' }}>
      <rect x="26" y="58" width="8" height="16" fill="#8a6432" />
      <path d="M30 4 L52 34 L42 34 L56 56 L4 56 L18 34 L8 34 Z" fill="none"
        stroke="#5cc8ff" strokeWidth="2.2" strokeLinejoin="round" opacity="0.85" />
      <path d="M30 12 L44 32 L38 32 L48 50 L12 50 L22 32 L16 32 Z" fill="rgba(92,200,255,0.10)" />
    </svg>
  )
}

function TreelineCabinet({ onBack }) {
  const d = useChannel('treeline')
  const [phase, setPhase] = useState('meadow')   // meadow | scatter | pan | hunt | won | lost
  const [log, setLog] = useState('critters grazing · press RUN THEM OFF when ready')
  const [hintTree, setHintTree] = useState(null)
  const [cache, setCache] = useState(0)
  const seenRef = useRef(new Set())
  const timers = useRef([])
  useEffect(() => () => timers.current.forEach(clearTimeout), [])
  useEffect(() => {   // no live round on the server (fresh session / relog) → deal one
    if (d && !d.active) { send('treeline', 'TREELINE_NEW', {}); seenRef.current = new Set(); setPhase('meadow') }
  }, [d && d.active])
  useEffect(() => {   // server truth drives the log + end states
    if (!d || !d.active) return
    for (const t of d.trees || []) {
      if (t.searched && !seenRef.current.has(t.id)) {
        seenRef.current.add(t.id)
        if (t.reveal === 'decoy') { setHintTree(t.id); setLog(`just a critter · it saw the bright one head ${t.arrow} of here`) }
        else if (!t.reveal) setLog('nothing but leaves')
      }
    }
    if (d.over && (phase === 'hunt')) {
      if (d.won) { setCache((c) => c + d.payout); setLog(`SCORBUNNY RECOVERED · ${fmt(d.payout)} Coins banked`); setPhase('won') }
      else { setLog('out of sweeps · she bolts deeper into the woods · 0 Coins'); setPhase('lost') }
    }
  }, [d, phase])
  if (!d) return <Empty title="…" msg="loading the TREELINE channel" />
  const later = (fn, ms) => timers.current.push(setTimeout(fn, ms))
  const startScatter = () => {
    if (phase !== 'meadow' || !d.active) return
    setPhase('scatter'); setLog('SPOOKED · watch where they enter the treeline -')
    later(() => setPhase('pan'), 1500)
    later(() => { setPhase('hunt'); setLog(`they're in there somewhere · ${d.budget ?? 10} sweeps authorized`) }, 2800)
  }
  const newRound = () => {
    timers.current.forEach(clearTimeout)
    seenRef.current = new Set(); setHintTree(null)
    send('treeline', 'TREELINE_NEW', {})
    setPhase('meadow'); setLog('fresh meadow · press RUN THEM OFF when ready')
  }
  const panned = phase === 'pan' || phase === 'hunt' || phase === 'won' || phase === 'lost'
  const scattering = phase === 'scatter' || panned
  const budget = d.budget ?? 10
  const clicks = d.clicksLeft ?? budget
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="tl-console">
        <header className="tl-head">
          <div className="row" style={{ gap: 8 }}>
            <button className="btn" onClick={onBack} title="back to the Game Corner lobby">‹</button>
            <div className="tl-title">
              <span className="tl-title-main">TREELINE</span>
              <span className="tl-title-sub">game corner · cabinet 02 · scorbunny recovery</span>
            </div>
          </div>
          <div className="tl-meters">
            <div className="tl-meter">
              <span className="tl-meter-label">SWEEPS</span>
              <span className="tl-pips">{Array.from({ length: budget }).map((_, i) =>
                <i key={i} className={`tl-pip${i < clicks ? ' tl-pip-on' : ''}`} />)}</span>
            </div>
            <div className="tl-meter">
              <span className="tl-meter-label">SESSION HAUL</span>
              <span className="tl-meter-val">{fmt(cache)}</span>
            </div>
          </div>
        </header>
        <div className="tl-screen">
          <div className={`tl-strip${panned ? ' tl-strip-panned' : ''}`}>
            <div className="tl-map tl-meadow">
              <div className="tl-grass">{Array.from({ length: 14 }).map((_, i) =>
                <span key={i} className="tl-blade" style={{ left: `${6 + i * 6.8}%`, bottom: `${4 + ((i * 37) % 10)}%` }}>ᨏ</span>)}</div>
              {(d.critters || []).map((c, i) => (
                <div key={i} className={`tl-critter-pos ${scattering ? 'tl-flee' : 'tl-idle'}`}
                  style={{ left: `${c.startX}%`, top: `${c.startY}%`, '--exitY': `${c.exitY}%`, transitionDelay: scattering ? `${i * 140}ms` : '0ms' }}>
                  <Bunny dir={scattering ? 'E' : 'S'} walking={phase === 'meadow' || phase === 'scatter'}
                    dim={!c.isTarget} speed={phase === 'scatter' ? 0.3 : 1.1} />
                </div>
              ))}
              <div className="tl-edge-fade" />
            </div>
            <div className="tl-map tl-forest">
              {(d.trees || []).map((t) => {
                const revealDecoy = t.reveal === 'decoy'
                const revealTarget = t.reveal === 'target'
                const missedTarget = phase === 'lost' && d.targetTreeId === t.id && !t.searched
                return (
                  <button key={t.id} className={`tl-tree${t.searched ? ' tl-searched' : ''}`}
                    style={{ left: `${t.x}%`, top: `${t.y}%`, '--sc': t.scale, zIndex: Math.round(t.y) }}
                    onClick={() => { if (phase === 'hunt' && !t.searched && !d.over) send('treeline', 'TREELINE_SEARCH', { tree: t.id }) }}
                    disabled={phase !== 'hunt' || t.searched}>
                    <span className="tl-tree-inner"><TlTree /></span>
                    {t.searched && !revealDecoy && !revealTarget && <span className="tl-puff">…</span>}
                    {revealDecoy && (
                      <span className="tl-popout">
                        <Bunny dim walking speed={0.9} />
                        {hintTree === t.id && <span className="tl-arrow">{t.arrow}</span>}
                      </span>
                    )}
                    {(revealTarget || missedTarget) && (
                      <span className={`tl-popout ${missedTarget ? 'tl-taunt' : 'tl-found'}`}>
                        <Bunny dir={missedTarget ? 'E' : 'S'} walking speed={missedTarget ? 0.28 : 0.55} />
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          </div>
        </div>
        <div className={`tl-log${phase === 'won' ? ' tl-log-win' : ''}${phase === 'lost' ? ' tl-log-lose' : ''}`}>
          <span className="tl-log-prompt">&gt;</span> {log}
        </div>
        <footer className="tl-controls">
          {phase === 'meadow' && <button className="btn go" onClick={startScatter}>RUN THEM OFF ▸</button>}
          {(phase === 'won' || phase === 'lost') && (
            <button className="btn go" onClick={newRound}>
              {phase === 'won' ? `NEW ROUND · +${fmt(d.payout)} 🪙` : 'NEW ROUND'}</button>
          )}
          {(phase === 'scatter' || phase === 'pan') && <span className="tl-watch">▶ WATCH THE TREELINE</span>}
          {phase === 'hunt' && <span className="tl-watch tl-watch-dim">sweep the trees · decoys snitch · payout falls with every sweep</span>}
        </footer>
      </div>
    </div>
  )
}

// ── TOP DECK (cabinet 03): 20 cards fan from the deck, slide back, pick 5 of 20 for the secret
// draw. Ladder 2x/6x/20x, cash out between rungs. Server owns the draw; this is all theater. ──
const TD_COLS = 5
const TD_CELL_W = 122
const TD_CELL_H = 88
function tdCellPos(i) {   // a 56px top strip keeps the house deck clear of row one
  return { x: 8 + (i % TD_COLS) * TD_CELL_W, y: 56 + Math.floor(i / TD_COLS) * TD_CELL_H }
}
function cap1(n) { return n ? n.charAt(0).toUpperCase() + n.slice(1) : '?' }

function TopDeckCabinet({ onBack }) {
  const d = useChannel('topdeck')
  const a = useChannel('arcade')
  const coins = a?.gcoins ?? 0
  const [phase, setPhase] = useState('table')   // table | fan | return | pick | ride | reveal
  const [wager, setWager] = useState(50)
  const [picks, setPicks] = useState([])
  const [dealt, setDealt] = useState(false)     // false = cards sit in the deck (pre/post animation)
  const [msg, setMsg] = useState('set a wager · the house deals 20 cards')
  const stageRef = useRef(1)
  const timers = useRef([])
  const later = (fn, ms) => timers.current.push(setTimeout(fn, ms))
  useEffect(() => () => timers.current.forEach(clearTimeout), [])

  useEffect(() => {   // server pushes drive the big transitions; the animation phases are local
    if (!d) return
    if (d.active && (phase === 'table' || phase === 'reveal')) {
      stageRef.current = d.stage
      setPicks([]); setDealt(false); setPhase('fan')
      setMsg('the table is dealt · study the 20 · PLAY shuffles them back in')
      later(() => setDealt(true), 60)
    } else if (d.active && d.stage > stageRef.current) {
      stageRef.current = d.stage
      setPicks([]); setPhase('ride')
      setMsg(`rung ${d.stage - 1} cleared · ${d.ladder?.[d.stage - 2]}x is banked if you walk`)
    } else if (d.over && (phase === 'pick' || phase === 'ride')) {
      setPhase('reveal')
      const name = cap1(d.cards?.[d.reveal])
      setMsg(d.won ? `it was ${name} · the house pays ${fmt(d.payout)} Coins`
                   : `it was ${name} · the house keeps your ${fmt(d.wager)}`)
    }
  }, [d])   // eslint-disable-line react-hooks/exhaustive-deps

  const deal = () => send('topdeck', 'TOPDECK_NEW', { wager })
  const play = () => {
    setPhase('return'); setDealt(false)
    setMsg('back into the deck they go...')
    later(() => { setPhase('pick'); setDealt(true); setMsg('pick 5 · the drawn card is on this table') }, 1500)
  }
  const togglePick = (i) => {
    if (phase !== 'pick') return
    setPicks((p) => p.includes(i) ? p.filter((x) => x !== i) : (p.length < 5 ? [...p, i] : p))
  }
  const confirm = () => { if (picks.length === 5) send('topdeck', 'TOPDECK_GUESS', { picks: picks.join(',') }) }
  const ride = () => { setPhase('pick'); setMsg('fresh draw · pick 5 again') }
  const cashout = () => send('topdeck', 'TOPDECK_CASHOUT', {})

  const cards = d?.cards || []
  const faceUp = phase === 'fan' || phase === 'return'
  const stage = d?.stage ?? 1
  const ladder = d?.ladder || [2, 6, 20]
  const deckX = 648 - 52, deckY = 6
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="tl-console">
        <header className="tl-head">
          <div className="row" style={{ gap: 8 }}>
            <button className="btn" onClick={onBack} title="back to the Game Corner lobby">‹</button>
            <div className="tl-title">
              <span className="tl-title-main">TOP DECK</span>
              <span className="tl-title-sub">game corner · cabinet 03 · find the drawn card</span>
            </div>
          </div>
          <div className="tl-meters">
            <div className="tl-meter"><span className="tl-meter-label">LADDER</span>
              <span className="td-ladder">{ladder.map((m, i) => (
                <span key={m} className={`td-rung${d?.active && stage === i + 1 ? ' on' : ''}${d?.active && stage > i + 1 ? ' done' : ''}`}>{m}x</span>
              ))}</span></div>
            <div className="tl-meter"><span className="tl-meter-label">PURSE</span>
              <span className="tl-meter-val">🪙 {fmt(coins)}</span></div>
          </div>
        </header>
        <div className="td-table">
          <div className={`td-board${phase === 'return' ? ' td-return' : ''}`}>
            <div className="td-deck">
              <span className="td-deck-card" style={{ transform: 'translate(4px,3px) rotate(3deg)' }}>◆</span>
              <span className="td-deck-card" style={{ transform: 'translate(2px,1px) rotate(-2deg)' }}>◆</span>
              <span className="td-deck-card">◆</span>
              <span className="td-deck-count">house deck</span>
            </div>
            {cards.map((name, i) => {
              const { x, y } = tdCellPos(i)
              const inDeck = !dealt
              const picked = picks.includes(i)
              const isReveal = phase === 'reveal' && d?.reveal === i
              const showFace = faceUp || isReveal
              return (
                <button key={`${name}${i}`}
                  className={`td-card${inDeck ? ' td-in-deck' : ''}${phase === 'pick' ? ' td-pickable' : ''}${picked ? ' td-sel' : ''}${isReveal ? (d.won ? ' td-hit' : ' td-bust') : ''}`}
                  style={{ left: x, top: y, '--dx': `${deckX - x}px`, '--dy': `${deckY - y}px`,
                           '--dly': `${i * 45}ms`, '--rdly': '0ms' }}
                  onClick={() => togglePick(i)} disabled={phase !== 'pick'}>
                  {showFace ? (<>
                    <img src={CARD_POOL[name]} alt="" draggable={false} />
                    <span className="td-card-name">{cap1(name)}</span>
                  </>) : <span className="td-back-face">◆</span>}
                </button>
              )
            })}
            {!d?.active && phase === 'table' && (
              <div className="empty" style={{ position: 'absolute', inset: 0 }}>
                <div><b>The dealer waits</b><span className="muted">wager Coins · find the drawn card · ride the ladder</span></div>
              </div>
            )}
          </div>
        </div>
        <div className={`tl-log${phase === 'reveal' && d?.won ? ' tl-log-win' : ''}${phase === 'reveal' && d && !d.won ? ' tl-log-lose' : ''}`}>
          <span className="tl-log-prompt">&gt;</span> {msg}
        </div>
        <footer className="tl-controls">
          {(phase === 'table' || phase === 'reveal') && (<>
            <span className="td-chips">{[10, 25, 50, 100, 200].map((v) => (
              <button key={v} className={`td-chip${wager === v ? ' on' : ''}`} disabled={coins < v}
                onClick={() => setWager(v)}>{v}</button>
            ))}</span>
            <button className="btn go" disabled={coins < wager} onClick={deal}>
              {phase === 'reveal' ? 'NEW HAND' : 'DEAL'} · 🪙 {wager}</button>
          </>)}
          {phase === 'fan' && <button className="btn go" onClick={play}>PLAY ▸ shuffle them in</button>}
          {phase === 'return' && <span className="tl-watch">▶ WATCH THE DECK</span>}
          {phase === 'pick' && (<>
            <span className="tl-watch tl-watch-dim">{picks.length}/5 picked</span>
            <button className="btn go" disabled={picks.length !== 5} onClick={confirm}>CALL IT</button>
          </>)}
          {phase === 'ride' && (<>
            <button className="btn" onClick={cashout}>CASH OUT · {ladder[stage - 2]}x = 🪙 {fmt((d?.wager ?? 0) * (ladder[stage - 2] ?? 2))}</button>
            <button className="btn go" onClick={ride}>LET IT RIDE ▸ {ladder[stage - 1]}x</button>
          </>)}
        </footer>
        <span className="dim" style={{ fontSize: 9 }}>portraits · PMD Sprite Collab (fan-made, credited - see the mod's CREDITS)</span>
      </div>
    </div>
  )
}

// ── SLOTS (cabinet 04): the classic. Server rolls; the reels here are pure theater that lands
// on the server result left-to-right. Voltorb (angry) is the jackpot face, obviously. ──
function slSymbolArt(name) { return name === 'voltorb' ? VOLTORB_ANGRY : CARD_POOL[name] }

function SlotsCabinet({ onBack }) {
  const d = useChannel('slots')
  const a = useChannel('arcade')
  const coins = a?.gcoins ?? 0
  const [bet, setBet] = useState(10)
  const [spinning, setSpinning] = useState([false, false, false])
  const [shown, setShown] = useState([1, 2, 3])       // face indices currently displayed
  const [msg, setMsg] = useState('pick a bet · pull the lever')
  const [flash, setFlash] = useState('')              // '' | 'hit' | 'jack'
  const seqRef = useRef(0)
  const timers = useRef([])
  const cycleRef = useRef(null)
  const later = (fn, ms) => timers.current.push(setTimeout(fn, ms))
  useEffect(() => () => { timers.current.forEach(clearTimeout); clearInterval(cycleRef.current) }, [])

  const symbols = d?.symbols || []
  const anySpin = spinning.some(Boolean)

  useEffect(() => {   // a fresh seq = a fresh server result → stop the reels on it, left to right
    if (!d || !d.seq || d.seq === seqRef.current || !d.reels) return
    seqRef.current = d.seq
    setFlash('')
    const stops = [700, 1300, 1900]
    d.reels.forEach((face, i) => {
      later(() => {
        setSpinning((sp) => sp.map((v, j) => (j === i ? false : v)))
        setShown((sh) => sh.map((v, j) => (j === i ? face : v)))
        if (i === 2) {
          clearInterval(cycleRef.current)
          const paid = d.paid ?? 0
          const jack = d.reels.every((f) => f === 0)
          setFlash(paid > 0 ? (jack ? 'jack' : 'hit') : '')
          setMsg(paid > 0
            ? (jack ? `THREE VOLTORB · the machine screams · +${fmt(paid)} Coins` : `winner · +${fmt(paid)} Coins`)
            : 'the house nods · again?')
        }
      }, stops[i])
    })
  }, [d])   // eslint-disable-line react-hooks/exhaustive-deps

  const pull = () => {
    if (anySpin || coins < bet) return
    setSpinning([true, true, true])
    setFlash('')
    setMsg('the reels hum...')
    clearInterval(cycleRef.current)
    cycleRef.current = setInterval(() => {   // visual churn while we wait on the server
      setShown((sh) => sh.map((v, i) => (spinningRefSafe()[i] ? (v + 1 + i) % Math.max(symbols.length, 1) : v)))
    }, 70)
    send('slots', 'SLOTS_SPIN', { bet })
    later(() => {   // server refused (broke/guard)? unfreeze rather than spin forever
      if (seqRef.current === (d?.seq ?? 0)) { setSpinning([false, false, false]); clearInterval(cycleRef.current); setMsg('the machine coughs · no pull') }
    }, 4000)
  }
  // interval closures need the live spinning state
  const spinningLive = useRef(spinning); spinningLive.current = spinning
  function spinningRefSafe() { return spinningLive.current }

  const payRows = [
    { faces: ['voltorb', 'voltorb', 'voltorb'], x: d?.paytable?.[0] ?? 100 },
    { faces: ['lechonk', 'lechonk', 'lechonk'], x: d?.paytable?.[1] ?? 15, note: 'any triple' },
    { faces: ['voltorb', 'voltorb'], x: d?.paytable?.[2] ?? 5 },
    { faces: ['snom', 'snom'], x: d?.paytable?.[3] ?? 1, note: 'any pair' },
  ]
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="tl-console">
        <header className="tl-head">
          <div className="row" style={{ gap: 8 }}>
            <button className="btn" onClick={onBack} title="back to the Game Corner lobby">‹</button>
            <div className="tl-title">
              <span className="tl-title-main">SLOTS</span>
              <span className="tl-title-sub">game corner · cabinet 04 · the classic</span>
            </div>
          </div>
          <div className="tl-meters">
            <div className="tl-meter"><span className="tl-meter-label">PURSE</span>
              <span className="tl-meter-val">🪙 {fmt(coins)}</span></div>
          </div>
        </header>
        <div className="row" style={{ gap: 18, alignItems: 'flex-start', justifyContent: 'center' }}>
          <div className="sl-machine">
            <div className="sl-reels">
              {[0, 1, 2].map((i) => (
                <div key={i} className={`sl-reel${spinning[i] ? ' sl-reel-spin' : ''}${!anySpin && flash === 'hit' ? ' sl-reel-hit' : ''}${!anySpin && flash === 'jack' ? ' sl-reel-jack' : ''}`}>
                  {symbols.length > 0 && <img src={slSymbolArt(symbols[shown[i] % symbols.length])} alt="" draggable={false} />}
                </div>
              ))}
            </div>
            <div className="td-chips">{[5, 10, 25, 50, 100].map((v) => (
              <button key={v} className={`td-chip${bet === v ? ' on' : ''}`} disabled={coins < v || anySpin}
                onClick={() => setBet(v)}>{v}</button>
            ))}</div>
            <button className="btn go" style={{ minWidth: 160 }} disabled={anySpin || coins < bet} onClick={pull}>
              {anySpin ? '···' : `PULL · 🪙 ${bet}`}</button>
          </div>
          <div className="sl-pay">
            {payRows.map((r, i) => (<Fragment key={i}>
              <span className="sl-pay-row">{r.faces.map((f, j) => <img key={j} src={slSymbolArt(f)} alt="" />)}
                {r.note && <span className="dim" style={{ fontSize: 9 }}>{r.note}</span>}</span>
              <span className="sl-pay-x">{r.x}x</span>
            </Fragment>))}
          </div>
        </div>
        <div className={`tl-log${flash ? ' tl-log-win' : ''}`}>
          <span className="tl-log-prompt">&gt;</span> {msg}
        </div>
        <span className="dim" style={{ fontSize: 9 }}>portraits · PMD Sprite Collab (fan-made, credited - see the mod's CREDITS)</span>
      </div>
    </div>
  )
}

// ── Rituals (v2): HIDDEN recipes - compositions are secret until you first assemble one ──
function RitualsTab() {
  const d = useChannel('rituals')
  const inv = useChannel('inventory')
  const learned = d?.learned || []
  const hidden = d?.hidden ?? 0
  const loot = d?.loot || {}
  const lootList = Object.entries(loot).sort((a, b) => b[1] - a[1])
  const slots = inv?.slots || []
  const canTake = (id) => slots.some((s) => !s || (s.id === id && s.count < 64))
  const prettySpecies = (sp) => {                     // "meowth:alolan" → "Alolan Meowth"
    const [base, form] = String(sp).split(':')
    return form ? `${cap(form)} ${cap(base)}` : cap(base)
  }
  const recipeChips = (r) => {
    const chips = []
    Object.entries(r.species || {}).forEach(([sp, n]) => chips.push(`${n}× ${prettySpecies(sp)}`))
    ;(r.groups || []).forEach((g) => chips.push(`${g.min}× ${(g.anyOf || []).map(prettySpecies).join(' / ')}`))
    Object.entries(r.types || {}).forEach(([t, n]) => chips.push(`${n}× ${cap(t)}-type`))
    if (r.minDistinct > 0) chips.push(`${r.minDistinct}+ distinct types`)
    if (r.span > 1) chips.push(`🏟 across ${r.span} pastures`)
    ;(r.signature || []).forEach((sp) => chips.push(`⭑ ${prettySpecies(sp)}`))
    return chips
  }
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="row" style={{ marginBottom: 8 }}>
        <span className="h">Rituals</span>
        <span style={{ flex: 1 }} />
        {hidden > 0 && <span className="dim mono" style={{ fontSize: 10 }}>🔒 {hidden} hidden ritual{hidden === 1 ? '' : 's'} undiscovered</span>}
      </div>
      {learned.length === 0 ? (
        <div className="inset" style={{ padding: 14, borderRadius: 8, marginBottom: 10 }}>
          <div className="amb" style={{ fontWeight: 700, fontSize: 12, marginBottom: 4 }}>᛭ Nothing recorded yet</div>
          <div className="dim" style={{ fontSize: 11, lineHeight: 1.5 }}>
            Certain gatherings of Pokémon resonate. Assemble the right ones - the right species, the right numbers -
            inside a single linked pasture, and its ritual will reveal itself here… along with what it yields.
          </div>
        </div>
      ) : learned.map((r) => { const poolCounts = (r.poolItems || []).map((it) => [it, loot[it] || 0]).filter(([, c]) => c > 0); const n = r.pool ? poolCounts.reduce((a, [, c]) => a + c, 0) : (loot[r.output] || 0); const pullItem = r.pool ? (poolCounts[0] ? poolCounts[0][0] : r.output) : r.output; const ok = canTake(pullItem); return (
        <div key={r.id} className="inset" style={{ padding: 10, borderRadius: 8, marginBottom: 8, display: 'flex', gap: 10 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="row">
              <span className="amb" style={{ fontWeight: 700, fontSize: 12 }}>🗡 {r.name}</span>
              <span style={{ flex: 1 }} />
              <span className="dim mono" style={{ fontSize: 10 }} title="lifetime gacha pulls - climbing means the ritual is live">⟳ {r.pulls || 0} pulls</span>
              <span className="dim mono" style={{ fontSize: 10 }}>{r.hits} hit{r.hits === 1 ? '' : 's'} lifetime</span>
            </div>
            <div className="row" style={{ gap: 4, flexWrap: 'wrap', margin: '6px 0' }}>
              {recipeChips(r).map((c) => <span key={c} className="kchip">{c}</span>)}
              <span className="dim" style={{ fontSize: 10 }}>- in {r.span > 1 ? `${r.span} pastures combined` : 'one pasture'} →</span>
              <span className="kchip" style={{ color: 'var(--amber)' }}>{r.pool ? '🎲 a random music disc' : `${r.qty}× ${cap(shortId(r.output))}`}</span>
            </div>
            {r.pool && poolCounts.length > 0 && (
              <div className="row" style={{ gap: 4, flexWrap: 'wrap', marginTop: 2 }}>
                {poolCounts.map(([it, c]) => (
                  <span key={it} className="kchip" style={{ cursor: 'pointer' }}
                    title={`${it} · L: one · ⇧: stack · R: all`}
                    onClick={(ev) => { if (canTake(it)) send('storage', 'RITUAL_PULL', { item: it, mode: shiftHeld(ev) ? 1 : 0 }) }}
                    onContextMenu={(ev) => { ev.preventDefault(); if (canTake(it)) send('storage', 'RITUAL_PULL', { item: it, mode: 2 }) }}>
                    🎵 {cap(shortId(it))} ×{c}</span>
                ))}
              </div>
            )}
          </div>
          <div className={`cell${n > 0 ? (ok ? '' : ' cell-full') : ''}`} style={{ width: 74, alignSelf: 'stretch', opacity: n > 0 ? 1 : 0.35 }}
            title={n > 0 ? (ok ? `${pullItem} · L: one · ⇧: stack · R: all` : `${pullItem} · inventory full`) : 'no spoils banked yet'}
            onClick={(ev) => { if (n > 0 && ok) send('storage', 'RITUAL_PULL', { item: pullItem, mode: shiftHeld(ev) ? 1 : 0 }) }}
            onContextMenu={(ev) => { ev.preventDefault(); if (n > 0 && ok) send('storage', 'RITUAL_PULL', { item: pullItem, mode: 2 }) }}>
            <span className="ct">{compact(n)}</span>
            <span className="nm">{r.pool ? '🎲 spoils' : shortId(r.output)}</span>
          </div>
        </div>
      )})}
      {(d?.locked || []).map((l) => (
        <div key={l.name} className="inset" style={{ padding: 10, borderRadius: 8, marginBottom: 8, opacity: 0.75 }}>
          <div className="row">
            <span style={{ fontWeight: 700, fontSize: 12, color: 'var(--muted)' }}>🔒 {l.name}</span>
          </div>
          <div className="dim" style={{ fontSize: 11, fontStyle: 'italic', marginTop: 4 }}>“{l.hint}”</div>
        </div>
      ))}
      {(() => { const covered = new Set(learned.flatMap((r) => [r.output, ...(r.poolItems || [])])); const orphans = lootList.filter(([id]) => !covered.has(id)); return orphans.length === 0 ? null : (<>
      <div className="row" style={{ margin: '10px 0 6px' }}>
        <span className="h">Unclaimed spoils</span>
        <span className="dim" style={{ fontSize: 10, marginLeft: 8 }}>from sources you haven't discovered yet · L: one · ⇧: stack · R: all</span>
      </div>
        <div className="grid">
          {orphans.map(([id, n]) => { const ok = canTake(id); return (
            <div key={id} className={`cell${ok ? '' : ' cell-full'}`} title={ok ? `${id} · L: one · ⇧: stack · R: all` : `${id} · inventory full`}
              onClick={(ev) => { if (ok) send('storage', 'RITUAL_PULL', { item: id, mode: shiftHeld(ev) ? 1 : 0 }) }}
              onContextMenu={(ev) => { ev.preventDefault(); if (ok) send('storage', 'RITUAL_PULL', { item: id, mode: 2 }) }}>
              <span className="ct">{compact(n)}</span>
              <span className="nm">{shortId(id)}</span>
            </div>
          )})}
        </div>
      </>) })()}
    </div>
  )
}

const GUIDE = [
  ['🌱 The Loop', `Tether parents in a pasture → slot a KERNEL → it breeds your configured pairs on a real clock →
every egg flows into your NOTEBOOK as data. Keepers land in the BioBank; the rest render into Data. Data feeds
your Daemon's buffs and your Soul Tethers, which make the next generation faster and shinier. That's the loop -
everything else in this mod is a lever on it.`],
  ['📓 Getting started', `1 · Craft a Notebook and right-click a pasture with it, then press LINK - an unlinked
pasture collects nothing (watch the amber warnings).  2 · Slot a Kernel (Copper → Greener: more pairs, more
augment slots).  3 · Open Threads and put two parents on a line (♂+♀, or a Ditto) - lines are the ONLY thing that breeds; an
unwired pasture stays quiet.  4 · Wire the line through
filters into the BioBank or Data.  5 · Walk away - drops and eggs keep accruing while the chunk is loaded, and
catch up the moment you return (12h cap, online time only).`],
  ['🧬 Kernels & the Augmenter', `Hold a Kernel near the Augmenter tab. Installs cost GPU (quality 2 ◈ ·
throughput 1 ◈) plus a slot; picking a different nature/ball/EV spread later is FREE - the augment is yours.
Nature Lock and Ball Lock force every egg; the EV Primer applies a full 510-budget spread; IV Floor guarantees
perfect stats; Ability Splice forces the hidden ability. Soul Tethers amplify installed augments - for rent,
paid in Data.`],
  ['👾 The Daemon & Data', `Eggs your graph declines don't vanish - they RENDER into Data, credited to you.
The Daemon spends it: compile buffs onto it (2 ◈ per tier) and switch it on - it drains Data per second while
granting its loadout. Starved Daemon = buffs sleep, base augments keep working. Nothing is ever destroyed
silently: shiny or unreadable eggs are ALWAYS kept, and the void log shows every render.`],
  ['🏦 BioBank & the Graph', `The BioBank holds 256 eggs per species as data - sort by IVs, shininess, stats.
The node graph (per breeding line) decides each egg's fate: IV/EV/nature/shiny filters → keep or render.
Withdrawing checks your inventory first; the bank never deletes.`],
  ['🍰 Snack Science', `ULTRA COMPRESSED SNACK: craft several poke snacks together - up to 9 effects, 6 copies
each. SNACK REPEL: craft a can (6 glass + 2 iron ingots + nether wart), charge it with 1-6 berries of the type
you DON'T want, then bake the charged can into an Ultra snack - that type's snack spawns divide by the charge.
The snack's lore always tells the true spawn speed.`],
  ['📦 Key recipes', `NOTEBOOK: 5 copper + book + 2 redstone + amethyst (one is gifted on first join; extras share
the SAME storage and Data - it's an account, not a container).  COPPER KERNEL: 4 copper blocks corners + 4 quartz
blocks + blank Data Disk center; wrap a kernel in iron/gold/diamond blocks to upgrade it.  BLANK DATA DISK:
2 iron + quartz block + redstone + paper.  GPU: 4 quartz blocks + 2 redstone blocks + 2 iron blocks + kilobyte
disk.  DAEMON: echo shard + 2 amethyst + GPU + megabyte disk.  SOUL TETHER: 3 amethyst + echo shard + string.
SPECIMEN DISK: blank disk + amethyst shard + echo shard. Echo + amethyst drop from Ghost/Dark and
Fairy/Psychic/Rock pastures.`],
  ['⛏ Harvest & Rituals', `Linked pastures trickle each mon's own Cobblemon drop table on a one-minute clock -
no combat needed. Type-drops and gacha rituals (composition-gated, with pity) make a Cobblemon-only world fully
farmable. Rates are baked into the mod ON PURPOSE: no server config can zero your drops and sell them back.`],
  ['🔬 The fine print', `All analytics are local - your data never leaves your machine. The mod profiles itself:
/gp perf prints live ms timings, /gp perf flame renders a flame graph.`],
]

// When Deuce sets up a donation page (Ko-fi etc.), paste the URL here and the About card grows a support line.
const DONATE_URL = ''

function GuideTab() {
  const about = useChannel('about')
  return (
    <div className="pane" style={{ overflow: 'auto' }}>
      <div className="h" style={{ marginBottom: 4 }}>Field Guide</div>
      <div className="dim" style={{ fontSize: 11, marginBottom: 10 }}>the Notebook's core loops, in one page - this tab is always here when you need it</div>
      {GUIDE.map(([title, body]) => (
        <div key={title} className="inset" style={{ padding: 10, borderRadius: 8, marginBottom: 8 }}>
          <div className="grn" style={{ fontWeight: 700, fontSize: 12, marginBottom: 4 }}>{title}</div>
          <div style={{ fontSize: 11, lineHeight: 1.55, color: 'var(--text)', whiteSpace: 'pre-line' }}>{body}</div>
        </div>
      ))}
      <AboutCard about={about} />
    </div>
  )
}

function AboutCard({ about }) {
  const artists = about?.pmdArtists
  return (
    <div className="inset" style={{ padding: 10, borderRadius: 8, marginBottom: 8, borderLeft: '3px solid var(--grn)' }}>
      <div className="grn" style={{ fontWeight: 700, fontSize: 12, marginBottom: 4 }}>📖 About</div>
      <div style={{ fontSize: 11, lineHeight: 1.6, color: 'var(--text)' }}>
        <b>Greener Pastures - A Data Science Mod</b> · v{about?.version || '?'}
        <br />Made by <b>{about?.author || 'DonaldGalliano'}</b>, bred on a live Cobblemon server. {about?.license || 'MIT'} licensed:
        free forever, no paid tiers, and drop rates are baked in so no one can sell them back to you.
        <br />Built on Cobblemon · breeding by Cobbreeding · UI rendered with MCEF.
      </div>
      {artists ? (
        <div className="dim" style={{ fontSize: 10, marginTop: 6, lineHeight: 1.55 }}>
          Game Corner art: fan-made portraits and sprites from the PMD Sprite Collab (sprites.pmdcollab.org),
          used with credit and verified rip-free. Thank you {artists}.
        </div>
      ) : null}
      {DONATE_URL ? (
        <div className="dim" style={{ fontSize: 10, marginTop: 4 }}>
          Enjoying the mod? You can support development at {DONATE_URL}
        </div>
      ) : null}
    </div>
  )
}

function Empty({ title, msg }) { return <div className="empty"><div><b>{title}</b><span className="muted">{msg}</span></div></div> }
