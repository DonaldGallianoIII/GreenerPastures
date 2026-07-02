/* ============================================================
   GREENER PASTURES — Notebook console (real, data-wired)
   Built on Deuce's mockup aesthetic (mockups/GreenerPasturesNotebook.jsx),
   wired to the live data contract (NOTEBOOK_DATA_CONTRACT.md) via the bridge SDK.
   Viewport-sized window; every tab reads its channel; buttons send actions.
   ============================================================ */
import { useState, useEffect, useRef } from 'react'
import { useChannel, send, isMock } from './bridge.js'

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
]
const STAT_NAMES = ['HP', 'At', 'Df', 'SA', 'SD', 'Sp']
const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s)
const fmt = (n) => (n == null ? '…' : n.toLocaleString('en-US'))
const compact = (n) => (n < 1000 ? `${n}` : n < 1e6 ? `${(n / 1e3).toFixed(1)}k` : `${(n / 1e6).toFixed(1)}M`)
const shortId = (id) => (id || '').split(':').pop().replace(/_/g, ' ')
const fmtTime = (s) => (s < 60 ? `${s}s` : s < 3600 ? `${(s / 60) | 0}m` : s < 86400 ? `${(s / 3600) | 0}h` : `${(s / 86400) | 0}d`)

export default function App() {
  const [tab, setTab] = useState('biobank')
  const active = TABS.find((t) => t.id === tab)
  const pcfg = useChannel('pastureConfig')      // set when a pasture is right-clicked with the Notebook
  const focused = pcfg?.present
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
            <button key={t.id} className={`gp-tab${tab === t.id ? ' on' : ''}`} onClick={() => setTab(t.id)}>{t.label}</button>
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
        </div>
        </>)}
        <StatusBar />
      </div>
      </div>
    </div>
  )
}
// InventoryWindow removed — the real MC inventory (with icons) is now drawn natively over the browser
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
// (persists across tabs — always mounted outside the tab body) and minimizable to a single line. Drag applies a
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
        {!min && <span className="dim" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 8 }}>⇧-click → storage</span>}
        <span onMouseDown={(e) => e.stopPropagation()} onClick={() => setMin((m) => !m)} title={min ? 'expand' : 'minimize'}
          style={{ cursor: 'pointer', marginLeft: 8, color: 'var(--muted)', fontFamily: "'JetBrains Mono',monospace", fontSize: 12, lineHeight: 1 }}>{min ? '▢' : '—'}</span>
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
    <div className={`islot2 has${hot ? ' hot' : ''}${isGpu ? ' gpu' : ''}`} title={`${shortId(s.id)} ×${s.count} · ⇧-click → storage`}
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
  if (!d) return <div className="pane" />   // channel not received yet → blank, not a flash of the empty state
  const entries = d?.entries || []
  const groups = {}
  entries.forEach((e, i) => (groups[e.species] ||= []).push({ e, i }))
  const species = Object.keys(groups)
  if (!species.length) return <Empty title="BioBank empty" msg="link a pasture — its eggs are pulled in here automatically while it's loaded" />
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
        <button className="btn" style={{ padding: '2px 7px', fontSize: 10 }} title="withdraw → a real egg in your inventory (to hatch)" onClick={() => send('biobank', 'WITHDRAW', { index: idx })}>↧ pull</button>
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

// ── Harvester / Storage ──────────────────────────────────────────────────────
function Harvester() {
  const d = useChannel('storage')
  const items = d?.items || {}
  const list = Object.entries(items).sort((a, b) => b[1] - a[1])
  const total = list.reduce((a, [, n]) => a + n, 0)
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="h">Storage</span>
        <span className="muted" style={{ fontSize: 11 }}>{list.length} types · {compact(total)} items</span>
        <span style={{ flex: 1 }} />
        <span className="dim" style={{ fontSize: 11 }}>L: one · ⇧-click: stack · R: all → inventory</span>
      </div>
      {!list.length ? <div style={{ color: 'var(--muted)', fontSize: 12 }}>empty — harvested loot from your linked pastures collects here</div> : (
        <div className="grid">
          {list.map(([id, n]) => (
            <div key={id} className="cell" title={`${id} · L: one · ⇧: stack · R: all`}
              onClick={(ev) => send('storage', shiftHeld(ev) ? 'PULL_STACK' : 'PULL_ONE', { item: id })}
              onContextMenu={(ev) => { ev.preventDefault(); send('storage', 'PULL_ID', { item: id }) }}>
              <span className="ct">{compact(n)}</span>
              <span className="nm">{shortId(id)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Pastures ─────────────────────────────────────────────────────────────────
function Pastures() {
  const d = useChannel('pastures')
  const [sel, setSel] = useState(0)
  const list = d?.pastures || []
  if (!list.length) return <Empty title="No pastures tracked" msg="open a pasture in-world (right-click with the wand) to monitor it here" />
  const p = list[Math.min(sel, list.length - 1)]
  return (
    <div className="pane split">
      <div className="plist inset">
        <div className="h" style={{ marginBottom: 8 }}>Pastures · {list.length}</div>
        {list.map((x, i) => (
          <div key={i} className={`pitem${i === sel ? ' on' : ''}`} onClick={() => setSel(i)}>
            <span style={{ fontWeight: 500 }}>{x.name}</span>
            <span style={{ flex: 1 }} />
            <span className="amb mono" style={{ fontSize: 10 }}>{x.eggCount} eggs</span>
          </div>
        ))}
      </div>
      <div className="pdetail inset">
        <div className="grn" style={{ fontWeight: 600, fontSize: 15 }}>{p.name}</div>
        <div className="muted mono" style={{ fontSize: 11, margin: '4px 0 2px' }}>{p.tier} · {p.eggCount} eggs queued · {p.pairs.length} pairs</div>
        <div className="dim" style={{ fontSize: 11, marginBottom: 10 }}>read-only — modify at the pasture in-world</div>
        {p.pairs.length ? p.pairs.map((line, i) => <div key={i} className="pair" style={{ color: pairColor(line) }}>{line}</div>)
          : <div className="dim" style={{ fontSize: 11 }}>no pairs arranged</div>}
      </div>
    </div>
  )
}
const pairColor = (l) => (l.endsWith('Breeding') ? 'var(--cyan)' : l.endsWith('Ready') ? 'var(--green)' : l.endsWith('Incomplete') ? 'var(--dim)' : 'var(--text)')

// ── Compiler (Daemon buff loadout) ───────────────────────────────────────────
function Compiler() {
  const d = useChannel('compiler')
  const status = useChannel('status')
  if (!d) return <Empty title="…" msg="loading the Compiler channel" />
  if (!d.hasDaemon) return <Empty title="No Daemon in your inventory" msg="hold a Daemon (anywhere in your pack) to compile its buffs" />
  const installed = d.installed || {}
  const gpu = status?.gpu ?? 0
  const runtime = d.drainPerSec > 0 ? Math.floor((status?.data ?? 0) / d.drainPerSec) : null
  return (
    <div className="trip">
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
          return (
            <div key={b.id} className={`brow${tier > 0 ? ' on' : ''}`}>
              <span style={{ color: tier > 0 ? 'var(--text)' : 'var(--muted)', flex: 1 }}>{b.label}</span>
              <span className="dim mono" style={{ fontSize: 9 }}>{b.category}</span>
              <span className="mono" style={{ fontSize: 10, width: 30, textAlign: 'right', color: gpu >= b.gpuCost ? 'var(--cyan)' : 'var(--red)' }} title="GPU to add one tier">◈{b.gpuCost}</span>
              <button className="step" disabled={tier <= 0} onClick={() => send('compiler', 'SET_BUFF', { buff: b.id, tier: tier - 1 })}>−</button>
              <span className="mono" style={{ fontSize: 11, color: tier > 0 ? 'var(--green)' : 'var(--muted)', width: 36, textAlign: 'center' }}>L{tier}/{b.cap}</span>
              <button className="step" disabled={tier >= b.cap || gpu < b.gpuCost} title={gpu < b.gpuCost ? 'not enough GPU' : ''} onClick={() => send('compiler', 'SET_BUFF', { buff: b.id, tier: tier + 1 })}>+</button>
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
  )
}

// ── Augmenter (Kernel augments) ──────────────────────────────────────────────
function Augmenter() {
  const d = useChannel('augmenter')
  const status = useChannel('status')
  const gpu = status?.gpu ?? 0
  if (!d) return <Empty title="…" msg="loading the Augmenter channel" />
  if (!d.hasKernel) return <Empty title="No Kernel in your inventory" msg="hold a Kernel (a Pasture Upgrade) to augment it" />
  return (
    <div className="trip">
      <div className="tcol inset" style={{ width: 150 }}>
        <span className="h">Kernel</span>
        <span className="grn" style={{ fontWeight: 600 }}>{d.tier}</span>
        <span className="muted">slots {d.slotsUsed}/{d.slotCap}</span>
        <div className="row" style={{ gap: 4 }}>
          {Array.from({ length: d.slotCap }).map((_, i) => <span key={i} className={`pip${i < d.slotsUsed ? ' on' : ''}`} />)}
        </div>
        <span className="cyn" style={{ marginTop: 6 }}>◈ {gpu} GPU</span>
      </div>
      <div className="tcol inset" style={{ flex: 1 }}>
        <span className="h">Augments · ◈ = GPU cost</span>
        {(d.catalog || []).map((a) => {
          const canApply = d.slotsUsed + a.slotCost <= d.slotCap && gpu >= a.gpuCost
          return (
            <div key={a.type} className={`brow${a.applied ? ' on' : ''}`}>
              <span style={{ color: a.applied ? 'var(--text)' : 'var(--muted)', flex: 1 }}>{a.label}</span>
              <span className="dim mono" style={{ fontSize: 10 }}>{a.slotCost} slot{a.slotCost !== 1 ? 's' : ''}</span>
              <span className="mono" style={{ fontSize: 10, width: 30, textAlign: 'right', color: gpu >= a.gpuCost ? 'var(--cyan)' : 'var(--red)' }} title="GPU cost">◈{a.gpuCost}</span>
              {a.applied
                ? <button className="btn warn" title="frees the slot — GPU already spent, not refunded" onClick={() => send('augmenter', 'REMOVE_AUGMENT', { type: a.type })}>REMOVE</button>
                : <button className="btn go" disabled={!canApply} title={gpu < a.gpuCost ? 'not enough GPU' : (d.slotsUsed + a.slotCost > d.slotCap ? 'no free slots' : '')} onClick={() => send('augmenter', 'APPLY_AUGMENT', { type: a.type })}>APPLY</button>}
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── Dashboard (live analytics — real breeding data over the `dashboard` channel) ──
function Dashboard() {
  const d = useChannel('dashboard')
  const laid = d?.laid || 0, shiny = d?.shiny || 0, kept = d?.kept || 0, voided = d?.voided || 0, dataEarned = d?.dataEarned || 0
  const spark = d?.spark && d.spark.length ? d.spark : [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
  const shinyRate = laid ? (shiny / laid * 100) : 0
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="h">Dashboard</span>
        <span className="dim" style={{ fontSize: 11 }}>live · this session</span>
      </div>
      <Goals />
      <div className="statrow">
        <Stat label="eggs" value={laid} />
        <Stat label="shiny" value={shiny} sub={`${shinyRate.toFixed(2)}%`} color="var(--pair)" />
        <Stat label="kept" value={kept} color="var(--green)" />
        <Stat label="voided" value={voided} color="var(--red)" />
        <Stat label="data earned" value={dataEarned} color="var(--cyan)" />
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
// The breeding-goal hunt panel — set a target (species/shiny/IVs/count), watch live progress.
function Goals() {
  const g = useChannel('goals')
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
      {!present && !open && <div className="dim" style={{ fontSize: 11, marginTop: 6 }}>no active hunt — set a target and watch progress as your pastures lay.</div>}
      {open && (
        <div className="goal-form">
          <label>species<input className="gp-input" value={form.species} placeholder="any" onFocus={focusOn} onBlur={focusOff} onChange={(e) => setForm({ ...form, species: e.target.value })} /></label>
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
  const c = node.config || {}, t = node.type
  return (
    <div className="dcfg" onMouseDown={(e) => e.stopPropagation()} onWheel={(e) => e.stopPropagation()}>
      <div className="dcfg-h">{(NODE_META[t] || {}).title?.(node) || 'filter'}<span className="dcfg-x" onClick={onClose}>✕</span></div>
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

function DaemonGraph({ cfg }) {
  const roster = cfg.roster || []
  const maxPairs = cfg.maxPairs || 0
  const [doc, setDoc] = useState(() => parseDoc(cfg.graph))
  const [view, setView] = useState({ x: 0, y: 0, zoom: 1 })
  const [wiring, setWiring] = useState(null)
  const [dragPos, setDragPos] = useState(null)
  const [sel, setSel] = useState(null)
  const drag = useRef(null)
  const boxRef = useRef(null)
  const touched = useRef(false)
  const lastPos = useRef(cfg.pos)

  // Adopt the server graph on pasture-switch, or while still untouched (shell→real-graph arrival), but never
  // overwrite the player's own local edits (the editor is the authority once touched).
  useEffect(() => {
    if (cfg.pos !== lastPos.current) { lastPos.current = cfg.pos; touched.current = false; setSel(null); setView({ x: 0, y: 0, zoom: 1 }); setDoc(parseDoc(cfg.graph)) }
    else if (!touched.current) setDoc(parseDoc(cfg.graph))
  }, [cfg.pos, cfg.graph])

  const threads = doc.threads || []
  const active = threads.find((t) => t.id === doc.active) || threads[0] || null
  const nodes = active ? (active.nodes || []) : []
  const edges = active ? (active.edges || []) : []
  const speciesOf = (id) => { const m = roster.find((r) => r.id === id); return m ? m.species : String(id).slice(0, 6) }
  const monThread = (id) => threads.find((t) => (t.nodes || []).some((n) => n.type === 'MON' && n.monId === id))
  const renderNodes = nodes.map((n) => n.type === 'MON' ? { ...n, species: speciesOf(n.monId) } : n)
  const byId = (id) => renderNodes.find((n) => n.id === id)
  const liveNode = (n) => (n && dragPos && dragPos.id === n.id) ? { ...n, x: dragPos.x, y: dragPos.y } : n

  const scale = () => parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--gp-scale')) || 1
  const toGraph = (cx, cy) => { const r = boxRef.current.getBoundingClientRect(), s = scale(); return { x: ((cx - r.left) / s - view.x) / view.zoom, y: ((cy - r.top) / s - view.y) / view.zoom } }

  // persistence + pairing derivation (each line's ≤2 parents → a bucket, so the breeder pairs them)
  const persist = (nd) => { touched.current = true; setDoc(nd); send('pasture', 'GRAPH', { pos: cfg.pos, json: JSON.stringify(nd) }) }
  const pushPairings = (nd) => { const p = {}; nd.threads.forEach((t, k) => { const mons = (t.nodes || []).filter((n) => n.type === 'MON').map((n) => n.monId); if (mons.length >= 2 && k + 1 <= maxPairs) { p[mons[0]] = k + 1; p[mons[1]] = k + 1 } }); send('pasture', 'PAIRINGS', { pos: cfg.pos, pairings: p }) }
  const commit = (nd, repair) => { persist(nd); if (repair) pushPairings(nd) }
  const updateActive = (mut, repair = false) => { if (!active) return; const nt = mut({ ...active, nodes: [...nodes], edges: [...edges] }); commit({ ...doc, threads: threads.map((t) => t.id === active.id ? nt : t) }, repair) }

  // thread tabs (breeding lines)
  const capLines = Math.max(1, maxPairs)
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
      if (t.id === active.id) return { ...t, nodes: [...(t.nodes || []), { id: mid, type: 'MON', monId, x: 26, y: 20 + cnt * 76 }] }
      if ((t.nodes || []).some((n) => n.id === mid)) return { ...t, nodes: t.nodes.filter((n) => n.id !== mid), edges: (t.edges || []).filter((e) => e.from !== mid && e.to !== mid) }
      return t
    }) }, true)
  }

  // node + edge mutations on the active line
  const addNode = (type) => { const r = boxRef.current.getBoundingClientRect(); const c = toGraph(r.left + r.width / 2, r.top + r.height / 2); const id = 'n' + Date.now().toString(36) + Math.floor(Math.random() * 1000); updateActive((t) => { t.nodes = [...t.nodes, { id, type, x: Math.round(c.x - NODE_W / 2), y: Math.round(c.y - NODE_H / 2), config: defaultConfig(type) }]; return t }); setSel(id) }
  const delNode = (id) => { const node = nodes.find((n) => n.id === id); updateActive((t) => { t.nodes = t.nodes.filter((n) => n.id !== id); t.edges = t.edges.filter((e) => e.from !== id && e.to !== id); return t }, !!(node && node.type === 'MON')); if (sel === id) setSel(null) }
  const setConfig = (id, key, val) => updateActive((t) => { t.nodes = t.nodes.map((n) => n.id === id ? { ...n, config: { ...(n.config || {}), [key]: val } } : n); return t })
  const setNodePos = (n, x, y) => updateActive((t) => { t.nodes = t.nodes.map((z) => z.id === n.id ? { ...z, x, y } : z); return t })
  const addEdge = (from, fromPort, to, toPort) => { if (from === to) return; updateActive((t) => { t.edges = [...t.edges.filter((e) => !(e.to === to && e.toPort === toPort)), { from, fromPort, to, toPort }]; return t }) }
  const delEdge = (e) => updateActive((t) => { t.edges = t.edges.filter((x) => !(x.from === e.from && x.fromPort === e.fromPort && x.to === e.to && x.toPort === e.toPort)); return t })
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
        <div className="muted" style={{ fontSize: 12, padding: '18px 4px' }}>No breeding lines yet — <b>+ line</b> to start one, add two parents, then wire their egg pipeline.{maxPairs ? '' : ' (Slot a Kernel first.)'}</div>
      ) : (<>
        <div className="thread-roster" onMouseDown={(e) => e.stopPropagation()}>
          <span className="dim" style={{ fontSize: 10 }}>parents</span>
          {roster.length === 0 ? <span className="dim" style={{ fontSize: 10 }}>— tether mons in-world</span> : roster.map((m) => { const home = monThread(m.id); const here = home && home.id === active.id; return (
            <button key={m.id} className={`monchip${here ? ' here' : home ? ' busy' : ''}`} title={home ? (here ? 'in this line — click to remove' : 'in ' + home.name) : 'add to this line'} onClick={() => toggleMon(m.id)}>{cap(m.species)}{home && !here ? ` · ${home.name}` : ''}</button>
          )})}
        </div>
        <div className="daemon-palette" onMouseDown={(e) => e.stopPropagation()}>
          {PALETTE.filter((p) => p.type !== 'SOURCE').map((p) => <button key={p.type} className="dchip" style={{ borderColor: p.hue, color: p.hue }} onClick={() => addNode(p.type)}>{p.label}</button>)}
          <span className="dim mono" style={{ marginLeft: 'auto', fontSize: 10 }}>{Math.round(view.zoom * 100)}%</span>
        </div>
        <div className="daemon-canvas" ref={boxRef} onMouseDown={onCanvasDown} onWheel={onWheel}>
          <div className="daemon-view" style={{ transform: `translate(${view.x}px,${view.y}px) scale(${view.zoom})`, transformOrigin: '0 0' }}>
            <svg className="daemon-wires">
              {edges.map((e, i) => { const fn = byId(e.from), tn = byId(e.to); if (!fn || !tn) return null; const fp = portByK(fn.type, e.fromPort), tp = portByK(tn.type, e.toPort); if (!fp || !tp) return null; const a = portXY(liveNode(fn), fp), b = portXY(liveNode(tn), tp); return <path key={'e' + i} d={wirePath(a.x, a.y, b.x, b.y)} stroke={fp.kind === 'void' ? 'var(--red)' : 'var(--cyan)'} className="dwire" onClick={(ev) => { ev.stopPropagation(); delEdge(e) }} /> })}
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
          <div className="daemon-hud">{active.name} · {nodes.filter((n) => n.type === 'MON').length}/2 parents · scroll=zoom · drag=pan · drag a port to wire</div>
        </div>
      </>)}
    </div>
  )
}

// The editable pasture config — shown when you right-click a pasture with the Notebook (replaces the owo screen).
// Wired to the server over the `pasture` bridge channel: NAME · PAIRINGS · CLAIM (link) · KERNEL · CLOSE (← back).
// The player-facing egg-ingest feed — the void-log trust feature (kept K · voided V by which filter · recent).
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
          ? <span className="dim" style={{ fontSize: 10 }}>no eggs yet — link a Kernel'd pasture and wire a pipeline</span>
          : entries.map((e, i) => (
            <div key={i} className="egglog-row">
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
  if (cfg.loading) return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="grn" style={{ cursor: 'pointer', fontWeight: 600 }} onClick={() => send('pasture', 'CLOSE', {})}>← console</span>
        <span style={{ flex: 1 }} />
        <span className="dim mono" style={{ fontSize: 11 }}>pasture config</span>
      </div>
      <div className="muted" style={{ fontSize: 12, padding: '28px 4px', textAlign: 'center' }}>loading pasture…</div>
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
      <div className="row inset" style={{ padding: 8, marginBottom: 10, borderRadius: 8 }}>
        <span className="dim" style={{ fontSize: 10, letterSpacing: 1 }}>KERNEL</span>
        <span style={{ flex: 1 }} />
        {hasKernel
          ? <span className="cyn mono" style={{ fontSize: 12 }}>{cap(cfg.tier.toLowerCase())} · {maxPairs} pairs</span>
          : <span className="amb" style={{ fontSize: 11 }}>none — slot a Kernel for multi-pair breeding</span>}
        <button className="btn" style={{ marginLeft: 8 }} onClick={() => send('pasture', 'KERNEL', { pos: cfg.pos })}>
          {hasKernel ? 'remove' : 'slot from inventory'}</button>
      </div>
      <div className="dim" style={{ fontSize: 11, marginBottom: 6 }}>
        Daemon · one tab per breeding line — add two parents, then wire their eggs through filters to BioBank / Data{maxPairs ? '' : ' · slot a Kernel to start'}
      </div>
      <DaemonGraph cfg={cfg} />
      <EggLogStrip />
    </div>
  )
}
const pairHue = (b) => `hsl(${(b * 67) % 360} 70% 62%)`
function Empty({ title, msg }) { return <div className="empty"><div><b>{title}</b><span className="muted">{msg}</span></div></div> }
