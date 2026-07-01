/* ============================================================
   GREENER PASTURES — Notebook  (React mockup → port to Fabric)
   Source: Claude chat, delivered to Deuce 2026-06-25.
   THIS IS THE CANONICAL GUI PORT REFERENCE for Task #14 slice B / #16 / #17 / #6.
   See VISUAL_SCRIPTING_UI_IDEA.md → "Notebook mockup (2026-06-25)" for the port map.

   Lexicon (LOCKED 2026-06-25):
     Notebook  · the tool, opens this GUI (one tabbed window: Daemon · Dashboard · Compiler)
     Kernel    · slotted item, holds augments AS DATA
     Compiler  · block; apply augments to the Kernel (write→Compile→run). pip-install metaphor.
     Daemon    · the visual node graph (the running breeding program)
       └ Thread · a locked, named breeding-pair pipeline (daemons spawn threads;
                  the pasture runs many concurrently). Augment nodes invoke a
                  chosen SUBSET of a Kernel augment's props — import vs call.
     Dashboard · Plots + Console (the logs / trust feature)
     🌲 Random Cut Forest · anomaly-flags your shinies (easter egg)

   Hand-rolled SVG so geometry ports 1:1 into a Fabric Screen.
   Colorblind-safe: port type = SHAPE + LABEL, color is decorative.
   ============================================================ */

import { useState, useRef, useEffect, useCallback } from "react";

const CSS = `
@import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap');

.gp-backdrop{
  --bg:#0c0f14; --bg2:#0a0d12; --grid:#171c24; --grid2:#10141b;
  --panel:#161c25; --panel-hi:#1d2530; --line:#2a3543; --line2:#212a36;
  --text:#e7eef6; --muted:#8593a4; --dim:#566273;
  --pair:#d56bff; --flow:#ffb454; --pass:#4fd6a0; --void:#ff6b6b; --data:#5cc8ff;
  position:fixed; inset:0; display:grid; place-items:center; padding:2vh;
  background:radial-gradient(circle at 28% 8%,#11161f,#06080c 72%);
  font-family:'Space Grotesk',system-ui,sans-serif; -webkit-font-smoothing:antialiased;
}
.gp-backdrop *, .gp-backdrop *::before, .gp-backdrop *::after{ box-sizing:border-box; }

.gp-win{ width:min(97vw,1180px); height:min(94vh,724px);
  background:var(--bg); border:1px solid var(--line); border-radius:14px; overflow:hidden;
  display:flex; flex-direction:column; color:var(--text);
  box-shadow:0 44px 130px -30px rgba(0,0,0,.85),0 0 0 1px rgba(255,255,255,.02) inset; }

.gp-title{ height:42px; flex:none; display:flex; align-items:center; gap:10px; padding:0 14px;
  background:linear-gradient(180deg,#12171f,#0d1219); border-bottom:1px solid var(--line); }
.gp-ico{ width:18px; height:18px; flex:none; }
.gp-fname{ font-family:'JetBrains Mono',monospace; font-size:12px; font-weight:600; }
.gp-sub{ font-size:11px; color:var(--dim); }
.gp-kernel{ margin-left:auto; display:flex; align-items:center; gap:7px;
  font-family:'JetBrains Mono',monospace; font-size:11px; color:var(--muted); }
.gp-kdot{ width:8px; height:8px; border-radius:50%; background:var(--pass); box-shadow:0 0 9px var(--pass);
  animation:gp-pulse 2s ease-in-out infinite; }
@keyframes gp-pulse{ 50%{ opacity:.4; } }

.gp-tabs{ height:38px; flex:none; display:flex; gap:3px; padding:6px 10px 0;
  background:var(--bg2); border-bottom:1px solid var(--line); }
.gp-tab{ display:flex; align-items:center; gap:8px; padding:0 15px; font-size:12px; font-weight:500;
  color:var(--muted); cursor:pointer; border-radius:8px 8px 0 0; border:1px solid transparent;
  border-bottom:none; background:transparent; }
.gp-tab:hover{ color:var(--text); }
.gp-tab.on{ color:var(--text); background:var(--bg); border-color:var(--line); box-shadow:0 1px 0 var(--bg); }
.gp-tab .tg{ width:13px; height:13px; }
.gp-body{ flex:1; position:relative; overflow:hidden; }

/* ====================== DAEMON ====================== */
.pg-root{ position:absolute; inset:0; overflow:hidden; background:var(--bg); user-select:none; }
.pg-canvas{ position:absolute; inset:0; cursor:grab; }
.pg-canvas.panning{ cursor:grabbing; }
.pg-canvas.locked::after{ content:""; position:absolute; inset:0; pointer-events:none;
  box-shadow:inset 0 0 0 2px rgba(79,214,160,.25); }
.pg-canvas::before{ content:""; position:absolute; inset:-2000px; pointer-events:none;
  background-image:
    linear-gradient(var(--grid) 1px,transparent 1px),linear-gradient(90deg,var(--grid) 1px,transparent 1px),
    linear-gradient(var(--grid2) 1px,transparent 1px),linear-gradient(90deg,var(--grid2) 1px,transparent 1px);
  background-size:160px 160px,160px 160px,32px 32px,32px 32px;
  background-position:var(--gx) var(--gy); opacity:.9; }
.pg-wires{ position:absolute; inset:0; pointer-events:none; overflow:visible; }
.pg-layer{ position:absolute; inset:0; pointer-events:none; }

.pg-node{ position:absolute; pointer-events:auto; border-radius:12px;
  background:linear-gradient(180deg,var(--panel-hi),var(--panel)); border:1px solid var(--line);
  box-shadow:0 12px 28px -12px rgba(0,0,0,.7),0 0 0 1px rgba(255,255,255,.02) inset;
  transition:box-shadow .15s,border-color .15s; }
.pg-node:hover{ border-color:#3a4960; }
.pg-head{ display:flex; align-items:center; gap:8px; height:40px; padding:0 10px; cursor:grab;
  border-bottom:1px solid var(--line); border-radius:12px 12px 0 0; }
.pg-accent{ width:4px; height:18px; border-radius:2px; flex:none; }
.pg-title{ font-weight:600; font-size:13px; flex:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.pg-kind{ font-family:'JetBrains Mono',monospace; font-size:9px; color:var(--dim);
  text-transform:uppercase; letter-spacing:1px; }
.pg-x{ width:18px; height:18px; border-radius:5px; flex:none; cursor:pointer; display:grid;
  place-items:center; color:var(--dim); font-size:13px; }
.pg-x:hover{ background:#2a1620; color:var(--void); }
.pg-body{ padding:10px 12px; font-size:12px; color:var(--muted); }
.pg-sprite{ width:26px; height:26px; border-radius:7px; flex:none; display:grid; place-items:center;
  font-weight:700; font-size:13px; color:#0c0f14; box-shadow:0 0 0 1px rgba(255,255,255,.15) inset; }
.pg-stat{ display:flex; gap:5px; flex-wrap:wrap; margin-top:2px; }
.pg-iv{ font-family:'JetBrains Mono',monospace; font-size:10px; padding:1px 5px; border-radius:4px;
  background:#0e131a; border:1px solid var(--line); color:var(--muted); }
.pg-iv.perfect{ color:var(--pass); border-color:#234a3a; }

.pg-port{ position:absolute; width:16px; height:16px; pointer-events:auto; display:grid;
  place-items:center; cursor:crosshair; z-index:2; }
.pg-port .glyph{ width:11px; height:11px; transition:transform .12s,filter .12s; }
.pg-port:hover .glyph{ transform:scale(1.35); filter:drop-shadow(0 0 6px currentColor); }
.pg-plabel{ position:absolute; font-family:'JetBrains Mono',monospace; font-size:9px; color:var(--muted);
  white-space:nowrap; pointer-events:none; top:50%; transform:translateY(-50%); }
.pg-plabel.l{ right:20px; } .pg-plabel.r{ left:20px; }
.pg-plabel.b{ top:18px; left:50%; transform:translateX(-50%); }

.pg-fhead{ font-family:'JetBrains Mono',monospace; font-size:9px; letter-spacing:1px;
  text-transform:uppercase; color:var(--dim); margin:0 0 6px; }
.pg-frow{ display:grid; grid-template-columns:34px 1fr auto 1fr; align-items:center; gap:6px; margin-bottom:4px; }
.pg-frow .lab{ font-family:'JetBrains Mono',monospace; font-size:10px; color:var(--muted); }
.pg-frow .dash{ color:var(--dim); font-size:10px; }
.pg-mini{ width:100%; min-width:0; background:#0e131a; border:1px solid var(--line); border-radius:5px;
  color:var(--text); padding:2px 4px; font-family:'JetBrains Mono',monospace; font-size:10px; text-align:center; }
.pg-mini:focus{ outline:none; border-color:var(--flow); }
.pg-mini.locked{ color:var(--pass); border-color:#234a3a; }
.pg-div{ height:1px; background:var(--line); margin:8px 0; }
.pg-fline{ display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.pg-fline .k{ font-family:'JetBrains Mono',monospace; font-size:10px; color:var(--muted); width:46px; }
.pg-sel{ flex:1; background:#0e131a; border:1px solid var(--line); border-radius:6px; color:var(--text);
  padding:3px 6px; font-family:inherit; font-size:11px; cursor:pointer; }
.pg-seg{ display:flex; border:1px solid var(--line); border-radius:6px; overflow:hidden; }
.pg-seg button{ font-family:'JetBrains Mono',monospace; font-size:10px; color:var(--muted); background:transparent;
  border:none; padding:3px 9px; cursor:pointer; }
.pg-seg button.on{ background:var(--panel-hi); color:var(--text); }
.pg-seg button.on.yes{ color:var(--pass); } .pg-seg button.on.no{ color:var(--void); }
.pg-augprops{ margin-top:8px; display:flex; flex-direction:column; gap:5px; }
.pg-chk{ display:flex; align-items:center; gap:7px; font-family:'JetBrains Mono',monospace; font-size:10px;
  color:var(--muted); cursor:pointer; }
.pg-chk input{ accent-color:var(--data); width:13px; height:13px; }
.pg-count{ font-family:'JetBrains Mono',monospace; font-size:26px; font-weight:500; line-height:1; margin-top:2px; }
.pg-node.spark{ animation:spark .5s ease-out; }
@keyframes spark{ 0%{ box-shadow:0 0 0 0 rgba(79,214,160,.5);} 100%{ box-shadow:0 0 0 18px rgba(79,214,160,0);} }
@keyframes pgflow{ to{ stroke-dashoffset:-12; } }

.pg-top{ position:absolute; top:12px; left:12px; right:12px; z-index:20; display:flex; flex-direction:column;
  gap:8px; pointer-events:none; }
.pg-top > *{ pointer-events:auto; }
.pg-threadstrip{ display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
.pg-thtab{ display:flex; align-items:center; gap:7px; padding:6px 10px; border-radius:9px; font-size:12px;
  font-family:'JetBrains Mono',monospace; color:var(--muted); background:rgba(13,18,25,.85); backdrop-filter:blur(6px);
  border:1px solid var(--line); cursor:pointer; }
.pg-thtab:hover{ color:var(--text); border-color:#3a4960; }
.pg-thtab.on{ color:var(--text); background:var(--panel-hi); border-color:#3a4960; }
.pg-thtab.lk{ border-color:#2e5a47; }
.pg-thtab .lkico{ font-size:10px; color:var(--pass); }
.pg-thtab .txx{ opacity:.45; padding-left:2px; }
.pg-thtab .txx:hover{ opacity:1; color:var(--void); }
.pg-thadd{ width:30px; height:30px; border-radius:9px; border:1px dashed var(--line); background:rgba(13,18,25,.85);
  color:var(--muted); cursor:pointer; font-size:16px; line-height:1; }
.pg-thadd:hover{ color:var(--text); border-color:#3a4960; }
.pg-lockbtn{ font-family:inherit; font-size:12px; font-weight:600; padding:7px 14px; border-radius:9px; cursor:pointer; }
.pg-lockbtn.lock{ color:var(--flow); background:linear-gradient(180deg,#241d12,#1a150c); border:1px solid #5a4a2e; }
.pg-lockbtn.unlock{ color:var(--pass); background:linear-gradient(180deg,#13241d,#0e1a15); border:1px solid #2e5a47; }

.pg-bar{ display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.pg-status{ display:flex; align-items:center; gap:7px; font-family:'JetBrains Mono',monospace; font-size:11px;
  color:var(--muted); margin-right:4px; }
.pg-btn{ font-family:inherit; font-size:12px; font-weight:500; color:var(--text);
  background:linear-gradient(180deg,#1d2530,#161c25); border:1px solid var(--line); padding:7px 12px;
  border-radius:9px; cursor:pointer; transition:.12s; }
.pg-btn:hover:not(:disabled){ border-color:#3a4960; transform:translateY(-1px); }
.pg-btn:disabled{ opacity:.35; cursor:not-allowed; }
.pg-btn.go{ border-color:#2e5a47; color:var(--pass); }
.pg-spacer{ flex:1; }
.pg-tally{ font-family:'JetBrains Mono',monospace; font-size:12px; color:var(--muted);
  background:#0e131a; border:1px solid var(--line); padding:7px 12px; border-radius:9px; }
.pg-tally b{ color:var(--pass); }
.pg-legend{ position:absolute; bottom:12px; left:12px; z-index:20; display:flex; gap:14px;
  font-family:'JetBrains Mono',monospace; font-size:10px; color:var(--muted); background:rgba(12,15,20,.7);
  backdrop-filter:blur(6px); border:1px solid var(--line); border-radius:9px; padding:8px 12px; }
.pg-legend span{ display:flex; align-items:center; gap:5px; }
.pg-hint{ position:absolute; bottom:12px; right:12px; z-index:20; font-size:11px; color:var(--dim); }
.pg-del{ pointer-events:auto; cursor:pointer; }

/* ====================== DASHBOARD ====================== */
.db-root{ position:absolute; inset:0; display:flex; flex-direction:column; background:var(--bg); }
.db-subtabs{ height:36px; flex:none; display:flex; gap:4px; align-items:center; padding:0 14px;
  border-bottom:1px solid var(--line2); }
.db-subtab{ font-family:'JetBrains Mono',monospace; font-size:11px; color:var(--muted); cursor:pointer;
  padding:5px 12px; border-radius:7px; background:transparent; border:1px solid transparent; }
.db-subtab.on{ color:var(--text); background:var(--panel); border-color:var(--line); }
.db-content{ flex:1; overflow:auto; padding:16px; }
.db-grid{ display:grid; grid-template-columns:repeat(2,1fr); gap:14px; }
.db-card{ background:linear-gradient(180deg,#11161e,#0e131a); border:1px solid var(--line); border-radius:12px;
  padding:12px 12px 6px; }
.db-plot{ width:100%; height:auto; display:block; }
.db-axis{ stroke:var(--line); stroke-width:1; } .db-grid-l{ stroke:var(--line2); stroke-width:1; }
.db-tick{ font-family:'JetBrains Mono',monospace; font-size:8px; fill:var(--dim); }
.db-ptitle{ font-family:'JetBrains Mono',monospace; font-size:10px; fill:var(--text); font-weight:600; }
.db-psub{ font-family:'JetBrains Mono',monospace; font-size:8px; fill:var(--dim); }
.db-term{ position:absolute; inset:0; top:36px; background:#080b0f; padding:14px 16px; overflow:auto;
  font-family:'JetBrains Mono',monospace; font-size:12px; line-height:1.65; }
.db-l{ white-space:pre-wrap; word-break:break-word; }
.db-ts{ color:var(--dim); } .db-out{ color:var(--muted); } .db-ok{ color:var(--pass); }
.db-err{ color:var(--void); } .db-anom{ color:#ffe08a; background:rgba(255,180,84,.08);
  border-left:2px solid var(--flow); padding-left:8px; margin-left:-10px; display:block; }
.db-prompt{ color:var(--data); }
.db-caret{ display:inline-block; width:7px; height:14px; background:var(--pass);
  vertical-align:-2px; animation:gp-blink 1s steps(1) infinite; }
@keyframes gp-blink{ 50%{ opacity:0; } }

/* ====================== COMPILER ====================== */
.cp-root{ position:absolute; inset:0; display:grid; grid-template-columns:230px 1fr; background:var(--bg); }
.cp-lib{ border-right:1px solid var(--line2); padding:14px; overflow:auto; }
.cp-libhead{ font-family:'JetBrains Mono',monospace; font-size:9px; letter-spacing:1px; text-transform:uppercase;
  color:var(--dim); margin-bottom:10px; }
.cp-aug{ display:flex; gap:10px; align-items:flex-start; padding:9px 10px; border:1px solid var(--line);
  border-radius:10px; background:var(--panel); cursor:pointer; margin-bottom:8px; transition:.12s; }
.cp-aug:hover{ border-color:#3a4960; transform:translateY(-1px); }
.cp-aug.sel{ border-color:var(--flow); box-shadow:0 0 0 1px var(--flow) inset; }
.cp-aug .ico{ width:26px; height:26px; border-radius:7px; flex:none; display:grid; place-items:center; font-size:14px;
  background:#0e131a; border:1px solid var(--line); }
.cp-aug .nm{ font-size:12px; font-weight:600; color:var(--text); }
.cp-aug .pkg{ font-family:'JetBrains Mono',monospace; font-size:9px; color:var(--data); margin-top:1px; }
.cp-aug .ef{ font-size:10px; color:var(--muted); margin-top:3px; }
.cp-aug .exp{ font-family:'JetBrains Mono',monospace; font-size:8px; color:var(--flow); }
.cp-bench{ display:flex; flex-direction:column; padding:18px; overflow:auto; }
.cp-row{ display:flex; align-items:center; justify-content:center; gap:20px; flex-wrap:wrap; margin:6px 0 16px; }
.cp-slot{ width:170px; min-height:150px; border-radius:14px; border:1px dashed var(--line);
  background:linear-gradient(180deg,#11161e,#0d1219); padding:12px; display:flex; flex-direction:column; gap:8px; }
.cp-slot.kernel{ border-style:solid; border-color:var(--line); }
.cp-slot.out{ border-color:#2e5a47; }
.cp-slabel{ font-family:'JetBrains Mono',monospace; font-size:9px; letter-spacing:1px; text-transform:uppercase; color:var(--dim); }
.cp-kico{ width:40px; height:40px; border-radius:9px; display:grid; place-items:center; font-size:20px;
  background:#0e131a; border:1px solid var(--line); }
.cp-knm{ font-size:13px; font-weight:600; }
.cp-tier{ font-family:'JetBrains Mono',monospace; font-size:9px; color:var(--flow); }
.cp-chips{ display:flex; flex-direction:column; gap:4px; margin-top:2px; }
.cp-chip{ font-family:'JetBrains Mono',monospace; font-size:9px; color:var(--pass); background:#0e1a15;
  border:1px solid #234a3a; border-radius:5px; padding:2px 6px; }
.cp-empty{ color:var(--dim); font-size:11px; font-family:'JetBrains Mono',monospace; margin:auto; text-align:center; }
.cp-op{ font-size:22px; color:var(--dim); }
.cp-compile{ align-self:center; font-family:'Space Grotesk',sans-serif; font-size:14px; font-weight:600;
  color:#06281c; background:linear-gradient(180deg,#5fe0aa,#3fbf8c); border:none; padding:11px 26px;
  border-radius:11px; cursor:pointer; box-shadow:0 8px 20px -8px rgba(79,214,160,.6); transition:.12s; }
.cp-compile:hover{ transform:translateY(-1px); }
.cp-compile:disabled{ opacity:.4; cursor:not-allowed; transform:none; box-shadow:none; }
.cp-prog{ width:100%; max-width:520px; align-self:center; margin-top:14px; }
.cp-bar{ height:6px; border-radius:3px; background:#0e131a; overflow:hidden; border:1px solid var(--line); }
.cp-bar > i{ display:block; height:100%; background:linear-gradient(90deg,var(--data),var(--pass)); transition:width .1s; }
.cp-clog{ margin-top:12px; background:#080b0f; border:1px solid var(--line); border-radius:10px; padding:10px 12px;
  font-family:'JetBrains Mono',monospace; font-size:11px; line-height:1.6; min-height:74px; max-height:150px; overflow:auto; }
.cp-clog .ok{ color:var(--pass); } .cp-clog .dim{ color:var(--dim); } .cp-clog .dat{ color:var(--data); }
`;

// ---------------- shared constants ----------------
const COMPAT = { pair: ["pair"], flow: ["flow"], pass: ["keep"], void: ["discard"] };
const ACCENT = { pair: "var(--pair)", flow: "var(--flow)", pass: "var(--pass)", void: "var(--void)", keep: "var(--pass)", discard: "var(--void)" };
const STATS = ["HP", "Atk", "Def", "SpA", "SpD", "Spe"];
const NATURES = ["Any", "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed",
  "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet",
  "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky"];
const SPRITE = { Ditto: "#c98fd6", Charmander: "#ff8a5c", Gible: "#4fb0d6", default: "#7a8aa0" };
const AUGMENTS = [
  { id: "shiny", name: "Shiny Boost ×2", pkg: "shiny-boost==2.0", glyph: "✨", effect: "Doubles shiny proc on the egg stream.", props: ["2× shiny rate", "sparkle in log"] },
  { id: "ivfloor", name: "IV Floor +5", pkg: "iv-floor==1.3", glyph: "📈", effect: "Raises minimum rolled IVs by 5.", props: ["+5 IV floor", "physical stats only"] },
  { id: "nature", name: "Nature Lock", pkg: "nature-lock==0.9", glyph: "🔒", effect: "Forces a chosen nature on every egg.", props: ["lock nature", "reroll on miss"] },
  { id: "speed", name: "Speed Tier ×3", pkg: "speed-tier==3.0", glyph: "⚡", effect: "Triples egg production rate.", props: ["3× egg rate", "skip cooldown"] },
  { id: "rcf", name: "Random Cut Forest", pkg: "rcf-anomaly==0.1", glyph: "🌲", effect: "Flags statistically rare eggs as anomalies.", exp: "experimental", props: ["flag anomalies", "auto-tag shinies"] },
];
const filterDefaults = () => ({ ivs: { HP: [0, 31], Atk: [0, 31], Def: [0, 31], SpA: [0, 31], SpD: [0, 31], Spe: [0, 31] }, nature: "Any", shiny: "any" });
let UID = 100; const nid = () => `n${++UID}`;
let TID = 1; const tid = () => `t${++TID}`;

// ============================================================
//  APP SHELL
// ============================================================
export default function GreenerPasturesNotebook() {
  const [tab, setTab] = useState("daemon");
  const TABS = [
    { id: "daemon", label: "Daemon", glyph: <NodeGlyph /> },
    { id: "dashboard", label: "Dashboard", glyph: <ChartGlyph /> },
    { id: "compiler", label: "Compiler", glyph: <GearGlyph /> },
  ];
  return (
    <div className="gp-backdrop">
      <style>{CSS}</style>
      <div className="gp-win">
        <div className="gp-title">
          <BookGlyph />
          <span className="gp-fname">pasture.ipynb</span>
          <span className="gp-sub">· Greener Pastures</span>
          <span className="gp-kernel"><span className="gp-kdot" /> Kernel&nbsp;·&nbsp;running</span>
        </div>
        <div className="gp-tabs">
          {TABS.map(t => (
            <button key={t.id} className={`gp-tab${tab === t.id ? " on" : ""}`} onClick={() => setTab(t.id)}>
              <span className="tg">{t.glyph}</span>{t.label}
            </button>
          ))}
        </div>
        <div className="gp-body">
          {tab === "daemon" && <DaemonView />}
          {tab === "dashboard" && <Dashboard />}
          {tab === "compiler" && <Compiler />}
        </div>
      </div>
    </div>
  );
}

// ============================================================
//  DAEMON  — multi-thread node graph
// ============================================================
function Glyph({ kind }) {
  const c = ACCENT[kind];
  if (kind === "pair") return <svg className="glyph" viewBox="0 0 12 12"><path d="M6 0 12 6 6 12 0 6Z" fill={c} stroke="#0c0f14" strokeWidth="1" /></svg>;
  if (kind === "flow" || kind === "pass" || kind === "keep") return <svg className="glyph" viewBox="0 0 12 12"><path d="M1 1 11 6 1 11Z" fill={c} stroke="#0c0f14" strokeWidth="1" /></svg>;
  return <svg className="glyph" viewBox="0 0 12 12"><path d="M2 2 10 10 M10 2 2 10" stroke={c} strokeWidth="2.4" strokeLinecap="round" /></svg>;
}
function portsFor(node) {
  const W = node.w;
  switch (node.type) {
    case "unit": return [
      { id: "pin", dx: 0, dy: 64, side: "left", kind: "pair", label: "pair", out: false },
      { id: "pout", dx: W, dy: 64, side: "right", kind: "pair", label: "pair", out: true },
      { id: "eggs", dx: W / 2, dy: node.h, side: "bottom", kind: "flow", label: "eggs", out: true },
    ];
    case "augment": return [
      { id: "in", dx: 0, dy: 70, side: "left", kind: "flow", label: "eggs", out: false },
      { id: "out", dx: W, dy: 70, side: "right", kind: "flow", label: "eggs", out: true },
    ];
    case "filter": return [
      { id: "in", dx: 0, dy: 60, side: "left", kind: "flow", label: "eggs", out: false },
      { id: "pass", dx: W, dy: 56, side: "right", kind: "pass", label: "pass", out: true },
      { id: "void", dx: W, dy: node.h - 46, side: "right", kind: "void", label: "void", out: true },
    ];
    case "collection": return [{ id: "keep", dx: 0, dy: 56, side: "left", kind: "keep", label: "keep", out: false }];
    case "void": return [{ id: "discard", dx: 0, dy: 50, side: "left", kind: "discard", label: "void", out: false }];
    default: return [];
  }
}
const getPort = (n, id) => portsFor(n).find(p => p.id === id);
const portPos = (n, p) => ({ x: n.x + p.dx, y: n.y + p.dy });
const FACE = { left: [-1, 0], right: [1, 0], bottom: [0, 1], top: [0, -1] };
function wirePath(a, fa, b, fb) {
  const d = Math.hypot(b.x - a.x, b.y - a.y);
  const k = Math.max(40, Math.min(160, d * 0.45));
  const c1 = { x: a.x + FACE[fa][0] * k, y: a.y + FACE[fa][1] * k };
  const c2 = { x: b.x + FACE[fb][0] * k, y: b.y + FACE[fb][1] * k };
  return `M${a.x} ${a.y} C${c1.x} ${c1.y} ${c2.x} ${c2.y} ${b.x} ${b.y}`;
}

function DaemonView() {
  const [threads, setThreads] = useState(() => [{ id: "t1", name: "pair-1", locked: false, nodes: seed(), edges: [] }]);
  const [activeId, setActiveId] = useState("t1");
  const [pan, setPan] = useState({ x: 70, y: 64 });
  const [temp, setTemp] = useState(null);
  const [hoverEdge, setHoverEdge] = useState(null);
  const [spark, setSpark] = useState({});

  const active = threads.find(t => t.id === activeId) || threads[0];
  const nodes = active ? active.nodes : [];
  const edges = active ? active.edges : [];
  const locked = !!(active && active.locked);

  const rootRef = useRef(null);
  const panRef = useRef(pan);
  const drag = useRef(null); const conn = useRef(null); const panning = useRef(null);
  useEffect(() => { panRef.current = pan; }, [pan]);

  const setNodes = (updater) => setThreads(ts => ts.map(t => t.id === activeId ? { ...t, nodes: typeof updater === "function" ? updater(t.nodes) : updater } : t));
  const setEdges = (updater) => setThreads(ts => ts.map(t => t.id === activeId ? { ...t, edges: typeof updater === "function" ? updater(t.edges) : updater } : t));

  const toWorld = useCallback((e) => {
    const r = rootRef.current.getBoundingClientRect();
    return { x: e.clientX - r.left - panRef.current.x, y: e.clientY - r.top - panRef.current.y };
  }, []);
  const onMove = useCallback((e) => {
    if (drag.current) { const w = toWorld(e); setNodes(ns => ns.map(n => n.id === drag.current.id ? { ...n, x: w.x - drag.current.ox, y: w.y - drag.current.oy } : n)); }
    else if (conn.current) { setTemp(toWorld(e)); }
    else if (panning.current) { const p = panning.current; setPan({ x: p.ox + (e.clientX - p.sx), y: p.oy + (e.clientY - p.sy) }); }
  }, [toWorld, activeId]);
  const onUp = useCallback(() => { drag.current = null; panning.current = null; if (conn.current) { conn.current = null; setTemp(null); } rootRef.current?.classList.remove("panning"); }, []);
  const bgDown = (e) => { if (e.target !== e.currentTarget) return; panning.current = { sx: e.clientX, sy: e.clientY, ox: pan.x, oy: pan.y }; rootRef.current.classList.add("panning"); };
  const headDown = (e, node) => { if (locked) return; e.stopPropagation(); const w = toWorld(e); drag.current = { id: node.id, ox: w.x - node.x, oy: w.y - node.y }; };
  const portDown = (e, node, port) => { if (locked) return; e.stopPropagation(); conn.current = { node, port }; setTemp(portPos(node, port)); };
  const portUp = (e, node, port) => { e.stopPropagation(); if (!conn.current) return; const from = conn.current; conn.current = null; setTemp(null); tryConnect(from.node, from.port, node, port); };

  function tryConnect(an, ap, bn, bp) {
    if (locked || an.id === bn.id) return;
    let out, inp;
    if (ap.out && !bp.out) { out = { n: an, p: ap }; inp = { n: bn, p: bp }; }
    else if (!ap.out && bp.out) { out = { n: bn, p: bp }; inp = { n: an, p: ap }; }
    else return;
    if (!(COMPAT[out.p.kind] || []).includes(inp.p.kind)) return;
    const id = `${out.n.id}.${out.p.id}->${inp.n.id}.${inp.p.id}`;
    setEdges(es => es.some(x => x.id === id) ? es : [...es, { id, fromNode: out.n.id, fromPort: out.p.id, toNode: inp.n.id, toPort: inp.p.id, kind: out.p.kind }]);
  }
  const removeNode = (id) => { if (locked) return; setNodes(ns => ns.filter(n => n.id !== id)); setEdges(es => es.filter(e => e.fromNode !== id && e.toNode !== id)); };
  const removeEdge = (id) => { if (locked) return; setEdges(es => es.filter(e => e.id !== id)); };
  const addNode = (type) => {
    if (locked) return;
    const base = { id: nid(), x: 360 - pan.x, y: 150 - pan.y + Math.random() * 50 };
    if (type === "augment") setNodes(n => [...n, { ...base, type, w: 210, h: 158, augKey: null, use: [] }]);
    if (type === "filter") setNodes(n => [...n, { ...base, type, w: 236, h: 300, ...filterDefaults() }]);
    if (type === "collection") setNodes(n => [...n, { ...base, type, w: 180, h: 96, count: 0 }]);
    if (type === "void") setNodes(n => [...n, { ...base, type, w: 150, h: 84, count: 0 }]);
  };
  const scanPasture = () => {
    if (locked) return;
    const mons = ["Ditto", "Charmander", "Gible"];
    setNodes(ns => {
      const have = new Set(ns.filter(n => n.type === "unit").map(n => n.label));
      const add = mons.filter(m => !have.has(m)).map((m, i) => ({ id: nid(), type: "unit", label: m, w: 190, h: 116, x: 50 + i * 26, y: 110 + i * 168, ivs: randIVs() }));
      return [...ns, ...add];
    });
  };
  const runBatch = () => {
    setNodes(ns => ns.map(n => n.type === "collection" ? { ...n, count: n.count + 3 + (Math.random() * 6 | 0) } : n.type === "void" ? { ...n, count: n.count + 8 + (Math.random() * 12 | 0) } : n));
    const s = {}; nodes.filter(n => n.type === "collection").forEach(n => s[n.id] = true); setSpark(s); setTimeout(() => setSpark({}), 500);
  };
  const reset = () => { if (locked) return; setNodes([]); setEdges([]); };

  // thread ops
  const addThread = () => { const id = tid(); setThreads(ts => [...ts, { id, name: `pair-${ts.length + 1}`, locked: false, nodes: [], edges: [] }]); setActiveId(id); };
  const lockToggle = () => setThreads(ts => ts.map(t => {
    if (t.id !== activeId) return t;
    if (!t.locked) { const nm = (typeof window !== "undefined" && window.prompt("Name this thread", t.name)) || t.name; return { ...t, locked: true, name: nm }; }
    return { ...t, locked: false };
  }));
  const renameThread = (id) => { const t = threads.find(x => x.id === id); const nm = typeof window !== "undefined" && window.prompt("Rename thread", t.name); if (nm) setThreads(ts => ts.map(x => x.id === id ? { ...x, name: nm } : x)); };
  const delThread = (id) => setThreads(ts => { if (ts.length <= 1) return ts; const left = ts.filter(t => t.id !== id); if (id === activeId) setActiveId(left[0].id); return left; });

  const accepted = nodes.filter(n => n.type === "collection").reduce((a, n) => a + (n.count || 0), 0);

  return (
    <div className="pg-root" ref={rootRef}>
      <div className="pg-top">
        <div className="pg-threadstrip">
          {threads.map(t => (
            <div key={t.id} className={`pg-thtab${t.id === activeId ? " on" : ""}${t.locked ? " lk" : ""}`}
              onClick={() => setActiveId(t.id)} onDoubleClick={() => renameThread(t.id)} title="double-click to rename">
              {t.locked && <span className="lkico">🔒</span>}
              {t.name}
              <span className="txx" onClick={(e) => { e.stopPropagation(); delThread(t.id); }}>×</span>
            </div>
          ))}
          <button className="pg-thadd" onClick={addThread} title="new thread">+</button>
          <span className="pg-spacer" />
          <button className={`pg-lockbtn ${locked ? "unlock" : "lock"}`} onClick={lockToggle}>
            {locked ? "🔓 Unlock thread" : "🔒 Lock thread"}
          </button>
          <span className="pg-tally">Accepted&nbsp;<b>{accepted}</b></span>
        </div>
        <div className="pg-bar">
          <span className="pg-status"><span className="gp-kdot" /> daemon · {threads.length} thread{threads.length !== 1 ? "s" : ""}</span>
          <button className="pg-btn" onClick={scanPasture} disabled={locked}>Scan pasture</button>
          <button className="pg-btn" onClick={() => addNode("augment")} disabled={locked}>+ Augment</button>
          <button className="pg-btn" onClick={() => addNode("filter")} disabled={locked}>+ IV filter</button>
          <button className="pg-btn" onClick={() => addNode("collection")} disabled={locked}>+ Collection</button>
          <button className="pg-btn" onClick={() => addNode("void")} disabled={locked}>+ Void bin</button>
          <button className="pg-btn go" onClick={runBatch}>▸ Run batch</button>
          <button className="pg-btn" onClick={reset} disabled={locked}>Reset</button>
        </div>
      </div>

      <div className={`pg-canvas${locked ? " locked" : ""}`} style={{ "--gx": `${pan.x}px`, "--gy": `${pan.y}px` }}
        onMouseDown={bgDown} onMouseMove={onMove} onMouseUp={onUp} onMouseLeave={onUp}>
        <svg className="pg-wires">
          <g transform={`translate(${pan.x},${pan.y})`}>
            {edges.map(e => {
              const fn = nodes.find(n => n.id === e.fromNode), tn = nodes.find(n => n.id === e.toNode);
              if (!fn || !tn) return null;
              const fp = getPort(fn, e.fromPort), tp = getPort(tn, e.toPort);
              if (!fp || !tp) return null;
              const a = portPos(fn, fp), b = portPos(tn, tp);
              const d = wirePath(a, fp.side, b, tp.side), col = ACCENT[e.kind];
              const animated = e.kind === "flow" || e.kind === "pass";
              const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
              return (
                <g key={e.id} onMouseEnter={() => setHoverEdge(e.id)} onMouseLeave={() => setHoverEdge(null)}>
                  <path d={d} fill="none" stroke={col} strokeWidth="9" strokeOpacity="0.12" />
                  <path d={d} fill="none" stroke={col} strokeWidth="2.2" strokeLinecap="round" style={animated ? { strokeDasharray: "5 7", animation: "pgflow 0.9s linear infinite" } : null} />
                  <path d={d} fill="none" stroke="transparent" strokeWidth="16" style={{ pointerEvents: "stroke" }} />
                  {hoverEdge === e.id && !locked && (
                    <g className="pg-del" onClick={() => removeEdge(e.id)}>
                      <circle cx={mid.x} cy={mid.y} r="9" fill="#1d2530" stroke={col} strokeWidth="1.5" />
                      <path d={`M${mid.x - 3} ${mid.y - 3} L${mid.x + 3} ${mid.y + 3} M${mid.x + 3} ${mid.y - 3} L${mid.x - 3} ${mid.y + 3}`} stroke={col} strokeWidth="1.8" strokeLinecap="round" />
                    </g>
                  )}
                </g>
              );
            })}
            {temp && conn.current && (() => {
              const a = portPos(conn.current.node, conn.current.port), col = ACCENT[conn.current.port.kind];
              const d = wirePath(a, conn.current.port.side, temp, conn.current.port.out ? "left" : "right");
              return <path d={d} fill="none" stroke={col} strokeWidth="2.2" strokeDasharray="4 5" strokeLinecap="round" />;
            })()}
          </g>
        </svg>

        <div className="pg-layer" style={{ transform: `translate(${pan.x}px,${pan.y}px)` }}>
          {nodes.map(node => (
            <Node key={node.id} node={node} locked={locked} onHead={headDown} onPortDown={portDown} onPortUp={portUp}
              onClose={removeNode} sparking={!!spark[node.id]}
              onEdit={(patch) => setNodes(ns => ns.map(n => n.id === node.id ? { ...n, ...patch } : n))} />
          ))}
        </div>
      </div>

      <div className="pg-legend">
        <span><svg width="11" height="11" viewBox="0 0 12 12"><path d="M6 0 12 6 6 12 0 6Z" fill="var(--pair)" /></svg> pair</span>
        <span><svg width="11" height="11" viewBox="0 0 12 12"><path d="M1 1 11 6 1 11Z" fill="var(--flow)" /></svg> eggs</span>
        <span><svg width="11" height="11" viewBox="0 0 12 12"><path d="M1 1 11 6 1 11Z" fill="var(--pass)" /></svg> pass</span>
        <span><svg width="11" height="11" viewBox="0 0 12 12"><path d="M2 2 10 10 M10 2 2 10" stroke="var(--void)" strokeWidth="2.4" /></svg> void</span>
      </div>
      <div className="pg-hint">{locked ? "🔒 thread locked · unlock to edit" : "drag head · drag port to wire · drag canvas to pan"}</div>
    </div>
  );
}

function Node({ node, locked, onHead, onPortDown, onPortUp, onClose, onEdit, sparking }) {
  const ports = portsFor(node);
  const aug = node.type === "augment" ? AUGMENTS.find(a => a.id === node.augKey) : null;
  const accent = node.type === "unit" ? "var(--pair)" : node.type === "augment" ? "var(--data)"
    : node.type === "filter" ? "var(--flow)" : node.type === "collection" ? "var(--pass)" : "var(--void)";
  const kindLabel = node.type === "unit" ? "unit" : node.type === "augment" ? "modifier"
    : node.type === "filter" ? "iv gate" : node.type === "collection" ? "collection" : "void bin";
  const title = node.type === "unit" ? node.label : node.type === "augment" ? (aug ? aug.name : "Augment")
    : node.type === "filter" ? "IV filter" : node.type === "collection" ? "Collection" : "Void bin";
  return (
    <div className={`pg-node${sparking ? " spark" : ""}`} style={{ left: node.x, top: node.y, width: node.w, opacity: locked ? 0.92 : 1 }}>
      <div className="pg-head" onMouseDown={(e) => onHead(e, node)} style={{ cursor: locked ? "default" : "grab" }}>
        <span className="pg-accent" style={{ background: accent }} />
        {node.type === "unit" ? <span className="pg-sprite" style={{ background: SPRITE[node.label] || SPRITE.default }}>{node.label[0]}</span> : null}
        {node.type === "augment" ? <span className="pg-sprite" style={{ background: "#0e131a", color: "var(--data)", fontSize: 14 }}>{aug ? aug.glyph : "λ"}</span> : null}
        <span className="pg-title">{title}</span>
        <span className="pg-kind">{kindLabel}</span>
        {!locked && <span className="pg-x" onMouseDown={(e) => e.stopPropagation()} onClick={() => onClose(node.id)}>×</span>}
      </div>
      <div className="pg-body" style={{ height: node.h - 40 }}>
        {node.type === "unit" && <div className="pg-stat">{STATS.map((s, i) => <span key={s} className={`pg-iv${node.ivs[i] === 31 ? " perfect" : ""}`}>{s} {node.ivs[i]}</span>)}</div>}
        {node.type === "augment" && <AugmentBody node={node} onEdit={onEdit} />}
        {node.type === "filter" && <FilterBody node={node} onEdit={onEdit} />}
        {(node.type === "collection" || node.type === "void") && (
          <>
            <div style={{ fontSize: 10, letterSpacing: 1, textTransform: "uppercase", color: "var(--dim)" }}>{node.type === "collection" ? "kept" : "voided"}</div>
            <div className="pg-count" style={{ color: node.type === "collection" ? "var(--pass)" : "var(--void)" }}>{node.count}</div>
          </>
        )}
      </div>
      {ports.map(p => {
        const lblCls = p.side === "left" ? "l" : p.side === "bottom" ? "b" : "r";
        return (
          <div key={p.id} className="pg-port" style={{ left: p.dx - 8, top: p.dy - 8, color: ACCENT[p.kind] }}
            onMouseDown={(e) => onPortDown(e, node, p)} onMouseUp={(e) => onPortUp(e, node, p)}>
            <Glyph kind={p.kind} /><span className={`pg-plabel ${lblCls}`}>{p.label}</span>
          </div>
        );
      })}
    </div>
  );
}

function AugmentBody({ node, onEdit }) {
  const stop = (e) => e.stopPropagation();
  const aug = AUGMENTS.find(a => a.id === node.augKey);
  const pick = (id) => { const a = AUGMENTS.find(x => x.id === id); onEdit({ augKey: id || null, use: a ? a.props.map(() => true) : [] }); };
  const toggle = (i) => { const use = (node.use || []).slice(); use[i] = !use[i]; onEdit({ use }); };
  return (
    <>
      <select className="pg-sel" style={{ width: "100%" }} value={node.augKey || ""} onMouseDown={stop} onChange={(e) => pick(e.target.value)}>
        <option value="">— pick augment —</option>
        {AUGMENTS.map(a => <option key={a.id} value={a.id}>{a.glyph} {a.name}</option>)}
      </select>
      {aug ? (
        <div className="pg-augprops">
          {aug.props.map((p, i) => (
            <label key={i} className="pg-chk" onMouseDown={stop}>
              <input type="checkbox" checked={!!(node.use && node.use[i])} onChange={() => toggle(i)} /> {p}
            </label>
          ))}
        </div>
      ) : <div style={{ fontSize: 10, color: "var(--dim)", marginTop: 8 }}>invokes a Kernel augment · pick which props apply to this thread</div>}
    </>
  );
}

function FilterBody({ node, onEdit }) {
  const stop = (e) => e.stopPropagation();
  const setIV = (s, idx, raw) => {
    const v = Math.max(0, Math.min(31, raw === "" ? 0 : +raw));
    const cur = node.ivs[s]; const next = idx === 0 ? [v, cur[1]] : [cur[0], v];
    onEdit({ ivs: { ...node.ivs, [s]: next } });
  };
  return (
    <>
      <div className="pg-fhead">IV ranges · min – max</div>
      {STATS.map(s => {
        const [lo, hi] = node.ivs[s];
        return (
          <div className="pg-frow" key={s}>
            <span className="lab">{s}</span>
            <input className={`pg-mini${lo === 31 ? " locked" : ""}`} type="number" min="0" max="31" value={lo} onMouseDown={stop} onChange={e => setIV(s, 0, e.target.value)} />
            <span className="dash">–</span>
            <input className={`pg-mini${hi === 31 ? " locked" : ""}`} type="number" min="0" max="31" value={hi} onMouseDown={stop} onChange={e => setIV(s, 1, e.target.value)} />
          </div>
        );
      })}
      <div className="pg-div" />
      <div className="pg-fline">
        <span className="k">nature</span>
        <select className="pg-sel" value={node.nature} onMouseDown={stop} onChange={e => onEdit({ nature: e.target.value })}>
          {NATURES.map(n => <option key={n} value={n}>{n}</option>)}
        </select>
      </div>
      <div className="pg-fline">
        <span className="k">shiny</span>
        <div className="pg-seg" onMouseDown={stop}>
          <button className={node.shiny === "any" ? "on" : ""} onClick={() => onEdit({ shiny: "any" })}>Any</button>
          <button className={node.shiny === "only" ? "on yes" : ""} onClick={() => onEdit({ shiny: "only" })}>Yes</button>
          <button className={node.shiny === "exclude" ? "on no" : ""} onClick={() => onEdit({ shiny: "exclude" })}>No</button>
        </div>
      </div>
    </>
  );
}

// ============================================================
//  DASHBOARD
// ============================================================
function Dashboard() {
  const [tab, setTab] = useState("plots");
  return (
    <div className="db-root">
      <div className="db-subtabs">
        <button className={`db-subtab${tab === "plots" ? " on" : ""}`} onClick={() => setTab("plots")}>Plots</button>
        <button className={`db-subtab${tab === "console" ? " on" : ""}`} onClick={() => setTab("console")}>Console</button>
      </div>
      {tab === "plots" ? <Plots /> : <ConsoleFeed />}
    </div>
  );
}
function Plots() {
  return (
    <div className="db-content">
      <div className="db-grid">
        <div className="db-card"><LinePlot title="eggs/hr" sub="daemon.throughput" color="var(--flow)" data={[8, 14, 11, 19, 22, 18, 26, 24, 31, 28, 34, 39]} /></div>
        <div className="db-card"><LinePlot title="shiny_rate" sub="rolling · per 1k" color="var(--pair)" data={[0.4, 0.6, 0.5, 0.9, 1.1, 1.0, 1.6, 2.1, 1.9, 2.6, 3.0, 3.4]} /></div>
        <div className="db-card"><Donut accepted={341} voided={1289} /></div>
        <div className="db-card"><Histogram title="iv_total · distribution" bins={[2, 5, 9, 14, 20, 27, 31, 24, 16, 9, 4, 2]} /></div>
      </div>
    </div>
  );
}
function LinePlot({ title, sub, data, color }) {
  const W = 320, H = 168, pad = { l: 30, r: 12, t: 30, b: 22 };
  const max = Math.max(...data), min = Math.min(...data, 0);
  const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const X = i => pad.l + (i / (data.length - 1)) * iw;
  const Y = v => pad.t + ih - ((v - min) / (max - min || 1)) * ih;
  const pts = data.map((v, i) => `${X(i).toFixed(1)},${Y(v).toFixed(1)}`).join(" ");
  const area = `M${X(0)},${pad.t + ih} L` + data.map((v, i) => `${X(i).toFixed(1)},${Y(v).toFixed(1)}`).join(" L") + ` L${X(data.length - 1)},${pad.t + ih} Z`;
  const grids = [0, 0.25, 0.5, 0.75, 1].map(t => pad.t + ih - t * ih);
  const last = data.length - 1;
  return (
    <svg className="db-plot" viewBox={`0 0 ${W} ${H}`}>
      <text x={pad.l} y={14} className="db-ptitle">{title}</text>
      <text x={pad.l} y={24} className="db-psub">{sub}</text>
      {grids.map((gy, i) => <line key={i} x1={pad.l} x2={W - pad.r} y1={gy} y2={gy} className="db-grid-l" />)}
      <line x1={pad.l} x2={pad.l} y1={pad.t} y2={pad.t + ih} className="db-axis" />
      <line x1={pad.l} x2={W - pad.r} y1={pad.t + ih} y2={pad.t + ih} className="db-axis" />
      <path d={area} fill={color} opacity="0.12" />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" />
      <circle cx={X(last)} cy={Y(data[last])} r="3.2" fill={color} />
      <text x={pad.l - 4} y={pad.t + 4} textAnchor="end" className="db-tick">{max}</text>
      <text x={pad.l - 4} y={pad.t + ih} textAnchor="end" className="db-tick">{min}</text>
    </svg>
  );
}
function Donut({ accepted, voided }) {
  const total = accepted + voided || 1, r = 46, cx = 90, cy = 84, C = 2 * Math.PI * r;
  const accFrac = accepted / total, pct = Math.round(accFrac * 100);
  return (
    <svg className="db-plot" viewBox="0 0 320 168">
      <text x="14" y="14" className="db-ptitle">accepted vs voided</text>
      <text x="14" y="24" className="db-psub">batch.outcome</text>
      <g transform="translate(0,8)">
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--void)" strokeWidth="16" />
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="var(--pass)" strokeWidth="16"
          strokeDasharray={`${(C * accFrac).toFixed(1)} ${C.toFixed(1)}`} strokeLinecap="round" transform={`rotate(-90 ${cx} ${cy})`} />
        <text x={cx} y={cy - 2} textAnchor="middle" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 20, fontWeight: 600, fill: "var(--pass)" }}>{pct}%</text>
        <text x={cx} y={cy + 14} textAnchor="middle" className="db-psub">kept</text>
      </g>
      <g transform="translate(176,60)" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 11 }}>
        <rect x="0" y="0" width="10" height="10" rx="2" fill="var(--pass)" /><text x="16" y="9" fill="var(--text)">accepted · {accepted}</text>
        <rect x="0" y="22" width="10" height="10" rx="2" fill="var(--void)" /><text x="16" y="31" fill="var(--text)">voided · {voided}</text>
      </g>
    </svg>
  );
}
function Histogram({ title, bins }) {
  const W = 320, H = 168, pad = { l: 14, r: 12, t: 30, b: 18 };
  const max = Math.max(...bins), iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
  const bw = iw / bins.length;
  return (
    <svg className="db-plot" viewBox={`0 0 ${W} ${H}`}>
      <text x={pad.l} y={14} className="db-ptitle">{title}</text>
      <text x={pad.l} y={24} className="db-psub">np.histogram(iv_total)</text>
      <line x1={pad.l} x2={W - pad.r} y1={pad.t + ih} y2={pad.t + ih} className="db-axis" />
      {bins.map((b, i) => {
        const h = (b / max) * ih, x = pad.l + i * bw + 2;
        return <rect key={i} x={x} y={pad.t + ih - h} width={bw - 4} height={h} rx="2" fill="var(--data)" opacity={0.55 + 0.45 * (b / max)} />;
      })}
    </svg>
  );
}
function ConsoleFeed() {
  const [lines, setLines] = useState(() => SEED_LOG.slice());
  const ref = useRef(null);
  useEffect(() => { const id = setInterval(() => setLines(l => [...l.slice(-70), genLine()]), 1500); return () => clearInterval(id); }, []);
  useEffect(() => { if (ref.current) ref.current.scrollTop = ref.current.scrollHeight; }, [lines]);
  return (
    <div className="db-term" ref={ref}>
      {lines.map((ln, i) => (
        <div key={i} className={ln.anom ? "db-anom db-l" : "db-l"}>
          <span className="db-ts">[{ln.t}] </span><span className={ln.cls}>{ln.text}</span>
        </div>
      ))}
      <div className="db-l"><span className="db-prompt">{">>> "}</span>daemon.tail()<span className="db-caret" /></div>
    </div>
  );
}

// ============================================================
//  COMPILER
// ============================================================
function Compiler() {
  const [kernel, setKernel] = useState({ tier: "Tier II", augments: ["shiny-boost==2.0"] });
  const [sel, setSel] = useState(null);
  const [prog, setProg] = useState(0);
  const [busy, setBusy] = useState(false);
  const [log, setLog] = useState([{ cls: "dim", text: "$ kernel ready · slot an augment to compile" }]);
  const selAug = AUGMENTS.find(a => a.id === sel);
  const installed = selAug && kernel.augments.includes(selAug.pkg);

  const compile = () => {
    if (!selAug || busy || installed) return;
    setBusy(true); setProg(0);
    setLog([{ cls: "dat", text: `$ compile ${selAug.pkg} -> kernel` }, { cls: "dim", text: `Collecting ${selAug.pkg}` }]);
    const steps = [
      { at: 30, cls: "dim", text: `Building wheel for ${selAug.id} ...` },
      { at: 65, cls: "dim", text: `Installing collected packages: ${selAug.id}` },
      { at: 100, cls: "ok", text: `Successfully compiled ${selAug.pkg} onto ${kernel.tier} Kernel` },
    ];
    let p = 0;
    const id = setInterval(() => {
      p += 6; setProg(Math.min(100, p));
      const hit = steps.find(s => s.at <= p && !s._done);
      if (hit) { hit._done = true; setLog(l => [...l, { cls: hit.cls, text: hit.text }]); }
      if (p >= 100) {
        clearInterval(id);
        setKernel(k => ({ ...k, augments: [...k.augments, selAug.pkg] }));
        if (selAug.id === "rcf") setLog(l => [...l, { cls: "ok", text: "🌲 anomaly detector armed — your shinies will be flagged" }]);
        setTimeout(() => { setBusy(false); setProg(0); }, 500);
      }
    }, 70);
  };

  return (
    <div className="cp-root">
      <div className="cp-lib">
        <div className="cp-libhead">augment library · import</div>
        {AUGMENTS.map(a => (
          <div key={a.id} className={`cp-aug${sel === a.id ? " sel" : ""}`} onClick={() => setSel(a.id)}>
            <span className="ico">{a.glyph}</span>
            <div>
              <div className="nm">{a.name} {a.exp && <span className="exp">· {a.exp}</span>}</div>
              <div className="pkg">{a.pkg}</div>
              <div className="ef">{a.effect}</div>
            </div>
          </div>
        ))}
      </div>
      <div className="cp-bench">
        <div className="cp-row">
          <div className="cp-slot kernel">
            <span className="cp-slabel">Kernel</span>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span className="cp-kico">🧠</span>
              <div><div className="cp-knm">Pasture Kernel</div><div className="cp-tier">{kernel.tier}</div></div>
            </div>
            <div className="cp-chips">
              {kernel.augments.length ? kernel.augments.map(p => <span key={p} className="cp-chip">{p}</span>) : <span className="cp-empty">no augments</span>}
            </div>
          </div>
          <span className="cp-op">+</span>
          <div className="cp-slot">
            <span className="cp-slabel">Augment</span>
            {selAug ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <span className="cp-kico">{selAug.glyph}</span>
                <div className="cp-knm" style={{ fontSize: 12 }}>{selAug.name}</div>
                <div style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 9, color: "var(--data)" }}>{selAug.pkg}</div>
                {installed && <div style={{ fontSize: 10, color: "var(--pass)" }}>already on kernel</div>}
              </div>
            ) : <span className="cp-empty">pick from library →</span>}
          </div>
          <span className="cp-op">→</span>
          <div className="cp-slot out">
            <span className="cp-slabel">Augmented Kernel</span>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span className="cp-kico">🧠</span>
              <div><div className="cp-knm">Pasture Kernel</div><div className="cp-tier">{kernel.augments.length} augment{kernel.augments.length !== 1 ? "s" : ""}</div></div>
            </div>
          </div>
        </div>
        <button className="cp-compile" onClick={compile} disabled={!selAug || busy || installed}>
          {busy ? "Compiling…" : installed ? "Installed" : "▸ Compile"}
        </button>
        <div className="cp-prog">
          <div className="cp-bar"><i style={{ width: `${prog}%` }} /></div>
          <div className="cp-clog">{log.map((l, i) => <div key={i} className={l.cls}>{l.text}</div>)}</div>
        </div>
      </div>
    </div>
  );
}

// ============================================================
//  glyphs / seeds / helpers
// ============================================================
function BookGlyph() { return <svg className="gp-ico" viewBox="0 0 24 24" fill="none"><path d="M4 5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-2V5Z" stroke="var(--flow)" strokeWidth="1.6" /><path d="M9 3v18M4 12h5" stroke="var(--flow)" strokeWidth="1.6" /></svg>; }
function NodeGlyph() { return <svg viewBox="0 0 24 24" fill="none" width="13" height="13"><rect x="3" y="9" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.6" /><rect x="15" y="3" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.6" /><rect x="15" y="15" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.6" /><path d="M9 12h3M15 6h-2a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h2" stroke="currentColor" strokeWidth="1.6" /></svg>; }
function ChartGlyph() { return <svg viewBox="0 0 24 24" fill="none" width="13" height="13"><path d="M4 20V4M4 20h16" stroke="currentColor" strokeWidth="1.6" /><rect x="7" y="12" width="3" height="5" fill="currentColor" /><rect x="12" y="8" width="3" height="9" fill="currentColor" /><rect x="17" y="14" width="3" height="3" fill="currentColor" /></svg>; }
function GearGlyph() { return <svg viewBox="0 0 24 24" fill="none" width="13" height="13"><circle cx="12" cy="12" r="3.4" stroke="currentColor" strokeWidth="1.6" /><path d="M12 3v3M12 18v3M21 12h-3M6 12H3M18 6l-2 2M8 16l-2 2M18 18l-2-2M8 8 6 6" stroke="currentColor" strokeWidth="1.6" /></svg>; }

function randIVs() { return STATS.map(() => Math.random() < 0.4 ? 31 : 20 + (Math.random() * 11 | 0)); }
function seed() {
  return [
    { id: "n1", type: "unit", label: "Ditto", w: 190, h: 116, x: 40, y: 120, ivs: [31, 31, 25, 31, 28, 31] },
    { id: "n2", type: "unit", label: "Charmander", w: 190, h: 116, x: 40, y: 320, ivs: [31, 24, 31, 31, 22, 30] },
    { id: "n3", type: "filter", label: "IV filter", w: 236, h: 300, x: 340, y: 140, ...filterDefaults() },
    { id: "n4", type: "collection", label: "Collection", w: 180, h: 96, x: 620, y: 150, count: 0 },
    { id: "n5", type: "void", label: "Void bin", w: 150, h: 84, x: 620, y: 320, count: 0 },
  ];
}
const SEED_LOG = [
  { t: "12:04:01", cls: "db-out", text: "egg produced — Charmander | Naughty | 31/24/31/31/22/30" },
  { t: "12:04:01", cls: "db-err", text: "FILTER: SpD 22 outside [0,20] → voided" },
  { t: "12:04:02", cls: "db-out", text: "egg produced — Charmander | Adamant | 31/31/30/22/31/31" },
  { t: "12:04:02", cls: "db-ok", text: "✓ accepted — Charmander | Adamant | 5×31 → Collection" },
  { t: "12:04:03", cls: "db-anom", anom: true, text: "🌲 RandomCutForest: anomaly — ✨shiny✨ 31/31/31/31/31/31 (score 0.99)" },
];
function genLine() {
  const now = new Date();
  const t = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
  const mons = ["Charmander", "Ditto", "Gible", "Larvitar"];
  const m = mons[Math.random() * mons.length | 0];
  const nat = NATURES[1 + (Math.random() * (NATURES.length - 1) | 0)];
  const ivs = STATS.map(() => Math.random() < 0.45 ? 31 : 18 + (Math.random() * 13 | 0));
  const perfect = ivs.filter(v => v === 31).length;
  const r = Math.random();
  if (r < 0.06) return { t, cls: "db-anom", anom: true, text: `🌲 RandomCutForest: anomaly — ✨shiny✨ ${ivs.join("/")} (score 0.9${(Math.random() * 9 | 0)})` };
  if (r < 0.55) return { t, cls: "db-err", text: `FILTER: ${perfect}×31 below gate → voided` };
  if (r < 0.78) return { t, cls: "db-ok", text: `✓ accepted — ${m} | ${nat} | ${perfect}×31 → Collection` };
  return { t, cls: "db-out", text: `egg produced — ${m} | ${nat} | ${ivs.join("/")}` };
}
