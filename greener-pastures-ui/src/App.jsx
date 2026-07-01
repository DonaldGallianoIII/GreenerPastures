/* ============================================================
   GREENER PASTURES — Notebook console (real, data-wired)
   Built on Deuce's mockup aesthetic (mockups/GreenerPasturesNotebook.jsx),
   wired to the live data contract (NOTEBOOK_DATA_CONTRACT.md) via the bridge SDK.
   Viewport-sized window; every tab reads its channel; buttons send actions.
   ============================================================ */
import { useState } from 'react'
import { useChannel, send, isMock } from './bridge.js'

const CSS = `
@import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap');
.gp-backdrop{
  --bg:#0c0f14; --bg2:#0a0d12; --panel:#161c25; --panel-hi:#1d2530; --inset:#0e131a; --slot:#080b10;
  --line:#2a3543; --line2:#212a36; --text:#e7eef6; --muted:#8593a4; --dim:#566273;
  --green:#4fd6a0; --amber:#ffb454; --cyan:#5cc8ff; --pink:#f2a7c4; --red:#ff6b6b; --pair:#d56bff;
  position:fixed; inset:0; display:grid; place-items:center; padding:2vh;
  background:radial-gradient(circle at 28% 8%,#11161f,#06080c 72%);
  font-family:'Space Grotesk',system-ui,sans-serif; -webkit-font-smoothing:antialiased; color:var(--text); user-select:none;
}
.gp-backdrop *,.gp-backdrop *::before,.gp-backdrop *::after{ box-sizing:border-box; }
.mono{ font-family:'JetBrains Mono',monospace; }

.gp-win{ width:min(97vw,1180px); height:min(94vh,724px); background:var(--bg); border:1px solid var(--line);
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

.gp-invwin{ position:fixed; bottom:14px; right:14px; z-index:60; width:270px;
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
  return (
    <div className="gp-backdrop">
      <style>{CSS}</style>
      <div className="gp-win">
        <div className="gp-title">
          <span className="dot" style={{ background: '#ff5f57' }} />
          <span className="dot" style={{ background: '#febc2e' }} />
          <span className="dot" style={{ background: '#28c840' }} />
          <span className="gp-fname">pasture.ipynb</span>
          <span className="gp-sub">· greener-pastures :: notebook</span>
          <Conn />
          <span className="gp-x">✕</span>
        </div>
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
        <StatusBar />
      </div>
      <InventoryWindow />
    </div>
  )
}

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

// A tiny floating window (bottom-right) mocking the player's MC inventory + hotbar, so slot/item flow is
// visible: GPU is consumed on apply, storage grabs land in a slot. (Real bridge: an `inventory` channel of slots.)
function InventoryWindow() {
  const inv = useChannel('inventory')
  const slots = inv?.slots || Array(36).fill(null)
  return (
    <div className="gp-invwin">
      <div className="hd">
        <span className="dot" style={{ background: 'var(--green)', width: 7, height: 7 }} />
        <span className="t">inventory</span>
        <span style={{ flex: 1 }} />
        <span className="dim" style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 8 }}>⇧-click → storage</span>
      </div>
      <div className="invgrid">{slots.slice(9, 36).map((s, i) => <Slot key={i} s={s} idx={i + 9} />)}</div>
      <div className="invgrid hot">{slots.slice(0, 9).map((s, i) => <Slot key={i} s={s} idx={i} hot />)}</div>
    </div>
  )
}
function Slot({ s, idx, hot }) {
  if (!s) return <div className={`islot2${hot ? ' hot' : ''}`} />
  const isGpu = s.id.endsWith(':gpu')
  const label = isGpu ? '◈' : shortId(s.id).replace(/\s/g, '').slice(0, 3)
  return (
    <div className={`islot2 has${hot ? ' hot' : ''}${isGpu ? ' gpu' : ''}`} title={`${shortId(s.id)} ×${s.count} · ⇧-click → storage`}
      onClick={(ev) => { if (ev.shiftKey) send('storage', 'DEPOSIT', { slot: idx }) }}>
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
        <span className="dim" style={{ fontSize: 11 }}>L: one stack · R / ⇧-click: all → inventory</span>
      </div>
      {!list.length ? <div style={{ color: 'var(--muted)', fontSize: 12 }}>empty — harvested loot from your linked pastures collects here</div> : (
        <div className="grid">
          {list.map(([id, n]) => (
            <div key={id} className="cell" title={`${id} · L: one · R / ⇧-click: all`}
              onClick={(ev) => send('storage', ev.shiftKey ? 'PULL_ID' : 'PULL_ONE', { item: id })}
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

// ── Dashboard (sample charts — live analytics channel is next) ────────────────
function Dashboard() {
  return (
    <div className="pane">
      <div className="row" style={{ marginBottom: 10 }}>
        <span className="h">Dashboard</span>
        <span className="dim" style={{ fontSize: 11 }}>sample plots — the live analytics feed lands with the bridge</span>
      </div>
      <div className="dgrid">
        <div className="dcard"><LinePlot title="eggs/hr" sub="daemon.throughput" color="var(--amber)" data={[8, 14, 11, 19, 22, 18, 26, 24, 31, 28, 34, 39]} /></div>
        <div className="dcard"><LinePlot title="shiny_rate" sub="rolling · per 1k" color="var(--pair)" data={[0.4, 0.6, 0.5, 0.9, 1.1, 1, 1.6, 2.1, 1.9, 2.6, 3, 3.4]} /></div>
        <div className="dcard"><Donut accepted={341} voided={1289} /></div>
        <div className="dcard"><Histogram bins={[2, 5, 9, 14, 20, 27, 31, 24, 16, 9, 4, 2]} /></div>
      </div>
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
function Empty({ title, msg }) { return <div className="empty"><div><b>{title}</b><span className="muted">{msg}</span></div></div> }
