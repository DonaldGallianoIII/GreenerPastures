import React, { useState, useEffect, useRef } from "react";
import {
  X, Power, Plus, Minus, ChevronRight,
  Gem, Flame, Pickaxe, Sparkles, FlaskConical, Coins, Fish, Anchor,
  Snowflake, Droplets, Footprints, Feather, Zap, Apple, Magnet,
} from "lucide-react";

/* ⌬ DAEMON COMPILER — interactive UI mock (Greener Pastures · A Data Science Mod) */

const HAPPY_SPRITE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAACwUlEQVR42uVXTU8TURQ9d97MwLQZChn5kI+EEF0aI9aAv8DEsjLSBSosmrAzIYaFCSbdsHBBiKkbNWFnXLhwp8ZfgCQi0a0JUcOXklILKSN0ptdFmYERCtN2SmK8STN57cw759177rlT4H8PqvRBIxXlurBw1yuJOToVxpHpZm6f6WPSiUknRkhhhBQ2UlE2UlGuKbiRirKiqx5g5+MQ0sbOl0VCKgd8c+IzAGBwaRjx1SH3t/jqEK5/j7nrckn4CjHZxZIsHTr5UZkInEDnVL+b+nh2hOPZEY5lBlxAZx3LDDA1qSzJkm8ScrlkcoX0fgsJC2wXtwhLRvG7LQusywj1RGAGRcBULdimBVlT8Lb5DQqq8Dz+8uwLKIKQtxlQBbDD2F7MBifCurBAQRWwkD8ADki7tnu1TcuTmcC7AADYlhFfHfJ0gLRro6AK3EzfQWz5mqcsB7uilCaolOIbG1rd0wPA+thCMcV7bXeUNl53vNsHHe0uaiOkI/14AfWJczAffSFfGmhsaEXm3kewxmBbhvHwogvu1NxJ9eDSMAB4wF3tPPsKEwCJCktAZpFw+v6nkiVxyBwFXlW0z/Sxoqus6OqJ5nOcKZFOLCa7KjOmyHRzVSRIJ+6c6ueqxnHbq6sMAGu3P5RFXhvthtFpYGn8PVXVhms3ZknKFCrK4Engvn2A6iXXdHzdXysjqkX4JiA0/3OLTILW1BAsgYNG5Cey4mdwBPj3vggVQWh7Hj3+fo0RsVuCI5DLLrrianly2TMJ/xaecw2FleAIhCM9e4NDcVuz7ekVKIJAwgIJC8bdSzgzdgEA0JHs89WCvl9IVhJzZKSibFnfsHxr9tDGTeO9KADYSM4T6VSWWHxL+9fmD9gP1kueaiM5TwDAW0zbuTzX3AdWEnPUmux1y+LZdIdPz0hOGjY1d8LtXP7f/nf8B6E2NpUV+EtWAAAAAElFTkSuQmCC";
const MAD_SPRITE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAABmElEQVR42uVW2Y6DMAwc50Bqpfz/hyJRLYTMPpRQzmJYaB/WEkKEJJ7YnnGA/25ydCEBTjaSjwDIjk14LmXrnu+qOQRE9jovQoGIpnfcb2QjACCVPByNTec+FCTALgrP5+6JbiyPT9NzGgDjzMzZ9FuCnA8gnx7Dk795jDNqEOYKajE49Vz1zPYRczRehXf3IwbkMf7oM6COQCosJIyL29TtMrU6RpyeArYO0nFXBiDk7l/jNs4oyjfMMGtFx3F1w9s5tVNMSDEBgxSw5GwfE2R/DVhnwBvB1oFVg6blFOSmquUaEcRjKZCHjDaaOh+Gfq1AD0txll0As9OrdV4hz+YNMqnLGmv5722Q/0X2bPQGtxEeYVlzFNKqARTh1XZHo8jR4c6mWavWgTXR+agQXWFqAPambzCZvqcC6Kmo5PdlKfBWRt1vCRBvvAaA2IisDdOizIW3pwB3A3DwPb3amOCtQGyE2IhUso/Mnoup0/KZVcM1bqeuAwogsvM+aM4SlvyPJeUSvi5dKIZX9a25l9lfnX1dCb9uvyHr2Q4ZZhSGAAAAAElFTkSuQmCC";

const C = {
  scrim: "rgba(3,6,9,0.72)",
  bg: "#070c11", panel: "#0c141b", panel2: "#0f1a22", inset: "#070e13",
  line: "#1b2a36", lineHi: "#26footnote", green: "#43d869", greenDim: "#1d6b38",
  amber: "#f5b234", amberDim: "#7a5410", text: "#cfe3da", muted: "#688089", slot: "#060b0f",
};
C.lineHi = "#2c4150";

const BUFFS = [
  { id: "fortune",        name: "Fortune",         icon: Gem,         cost: 1.5,  max: 3, desc: "More drops from mined blocks." },
  { id: "auto_smelt",     name: "Auto-Smelt",      icon: Flame,       cost: 2.0,  max: 3, desc: "Smelts ores the moment you mine them." },
  { id: "vein_miner",     name: "Vein-Miner",      icon: Pickaxe,     cost: 2.5,  max: 3, desc: "Break a whole connected ore vein at once." },
  { id: "xp_boost",       name: "XP Boost",        icon: Sparkles,    cost: 1.0,  max: 3, desc: "Gain extra experience from everything." },
  { id: "potion_dur",     name: "Potion Duration", icon: FlaskConical,cost: 0.5,  max: 3, desc: "Your potion effects last longer." },
  { id: "looting",        name: "Looting",         icon: Coins,       cost: 1.5,  max: 3, desc: "Better and more drops from mobs." },
  { id: "lure",           name: "Lure",            icon: Fish,        cost: 0.4,  max: 3, desc: "Fish bite sooner." },
  { id: "luck_sea",       name: "Luck of the Sea", icon: Anchor,      cost: 0.4,  max: 3, desc: "Better loot while fishing." },
  { id: "frost_walker",   name: "Frost Walker",    icon: Snowflake,   cost: 0.6,  max: 2, desc: "Freeze water beneath your feet." },
  { id: "respiration",    name: "Respiration",     icon: Droplets,    cost: 0.5,  max: 3, desc: "Breathe far longer underwater." },
  { id: "swift_sneak",    name: "Swift Sneak",     icon: Footprints,  cost: 0.8,  max: 3, desc: "Move quickly while sneaking." },
  { id: "feather_fall",   name: "Feather Falling", icon: Feather,     cost: 0.25, max: 3, desc: "Reduce fall damage." },
  { id: "haste",          name: "Haste",           icon: Zap,         cost: 1.2,  max: 3, desc: "Mine and attack faster." },
  { id: "saturation",     name: "Saturation",      icon: Apple,       cost: 0.7,  max: 3, desc: "Hunger drains slower." },
  { id: "magnet",         name: "Magnet",          icon: Magnet,      cost: 1.0,  max: 3, desc: "Pull nearby item drops toward you." },
];
const BMAP = Object.fromEntries(BUFFS.map((b) => [b.id, b]));
const MAX_INSTALL = 32;

const fmtRuntime = (sec) => {
  if (!isFinite(sec)) return "\u221e";
  if (sec >= 3600) return `~${Math.floor(sec / 3600)}h ${Math.floor((sec % 3600) / 60)}m`;
  if (sec >= 60) return `~${Math.floor(sec / 60)}m`;
  return `~${Math.floor(sec)}s`;
};

export default function DaemonCompiler() {
  const [data, setData] = useState(48210);
  const [on, setOn] = useState(false);
  const [installed, setInstalled] = useState([
    { id: "feather_fall", tier: 3 },
    { id: "auto_smelt", tier: 1 },
  ]);
  const [selected, setSelected] = useState("feather_fall");
  const [tier, setTier] = useState(3);

  const drain = installed.reduce((s, i) => s + i.tier * BMAP[i.id].cost, 0);
  const drainRef = useRef(drain);
  drainRef.current = drain;

  useEffect(() => {
    if (!on) return;
    const t = setInterval(() => {
      setData((d) => {
        const nd = d - drainRef.current * 0.1;
        if (nd <= 0) { setOn(false); return 0; }
        return nd;
      });
    }, 100);
    return () => clearInterval(t);
  }, [on]);

  const selBuff = selected ? BMAP[selected] : null;
  const selInstalledTier = installed.find((i) => i.id === selected)?.tier;

  const pick = (id) => {
    setSelected(id);
    const inst = installed.find((i) => i.id === id);
    setTier(inst ? inst.tier : 1);
  };
  const stepTier = (d) => setTier((t) => Math.max(1, Math.min(selBuff.max, t + d)));
  const install = () => {
    if (!selBuff) return;
    setInstalled((ls) => {
      const exists = ls.find((i) => i.id === selected);
      if (exists) return ls.map((i) => (i.id === selected ? { ...i, tier } : i));
      if (ls.length >= MAX_INSTALL) return ls;
      return [...ls, { id: selected, tier }];
    });
  };
  const remove = (id) => setInstalled((ls) => ls.filter((i) => i.id !== id));

  const runtime = drain > 0 ? data / drain : Infinity;

  // ---- atoms ----
  const Header = ({ children }) => (
    <div className="flex items-center gap-2 px-3 py-2" style={{ borderBottom: `1px solid ${C.line}` }}>
      <span className="font-mono text-xs font-bold tracking-[0.2em]" style={{ color: C.green }}>{children}</span>
    </div>
  );
  const Arrow = () => (
    <div className="flex items-center justify-center" style={{ width: 22 }}>
      <ChevronRight size={18} style={{ color: C.lineHi }} />
    </div>
  );

  return (
    <div style={{ background: C.scrim, minHeight: 600 }}
      className="w-full flex items-center justify-center p-4">
      {/* faint dimmed game-world hint behind the scrim */}
      <div style={{
        width: "min(1080px, 95vw)", aspectRatio: "16 / 10",
        background: `linear-gradient(180deg, ${C.panel} 0%, ${C.bg} 100%)`,
        border: `1px solid ${C.lineHi}`, borderRadius: 8,
        boxShadow: `0 0 0 1px #000, 0 18px 60px rgba(0,0,0,0.6), inset 0 0 80px rgba(0,0,0,0.5)`,
        position: "relative", overflow: "hidden",
        fontFamily: "ui-monospace, monospace", color: C.text,
        display: "flex", flexDirection: "column",
      }}>
        {/* circuit etch */}
        <div style={{
          position: "absolute", inset: 0, pointerEvents: "none", opacity: 0.05,
          backgroundImage: `radial-gradient(${C.green} 1px, transparent 1px)`, backgroundSize: "22px 22px",
        }} />

        {/* title bar */}
        <div className="flex items-center justify-between px-4 py-2.5"
          style={{ borderBottom: `1px solid ${C.lineHi}`, background: C.panel2 }}>
          <div className="flex items-center gap-2">
            <span style={{ color: C.green, fontSize: 18 }}>⌬</span>
            <span className="font-mono text-sm font-bold tracking-[0.25em]" style={{ color: C.text }}>DAEMON COMPILER</span>
          </div>
          <button title="Close"
            style={{ color: C.muted, border: `1px solid ${C.line}`, borderRadius: 4 }}
            className="w-6 h-6 flex items-center justify-center hover:opacity-70">
            <X size={14} />
          </button>
        </div>

        {/* three columns */}
        <div className="flex flex-1 min-h-0 p-3 gap-0">
          {/* ===== LEFT — DAEMON (input) ===== */}
          <div style={{ width: "28%", background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 6 }}
            className="flex flex-col min-h-0">
            <Header>DAEMON</Header>
            <div className="flex-1 flex flex-col items-center justify-start p-4 gap-4">
              {/* item slot */}
              <div style={{
                width: 96, height: 96, background: C.slot,
                border: `2px solid ${on ? C.green : C.line}`, borderRadius: 6,
                boxShadow: on ? `0 0 18px ${C.amber}66, inset 0 0 12px ${C.green}33` : "inset 0 0 10px #000",
                display: "flex", alignItems: "center", justifyContent: "center", position: "relative",
                transition: "border-color .2s, box-shadow .2s",
              }}>
                <img src={on ? HAPPY_SPRITE : MAD_SPRITE} alt="daemon" draggable={false}
                  style={{ width: 72, height: 72, imageRendering: "pixelated" }} />
              </div>

              <div className="w-full flex flex-col gap-2 font-mono text-xs mt-1">
                <div className="flex items-center justify-between">
                  <span style={{ color: C.muted }}>Data</span>
                  <span style={{ color: C.amber }} className="font-bold tabular-nums">
                    {Math.floor(data).toLocaleString()}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span style={{ color: C.muted }}>Status</span>
                  <span className="flex items-center gap-1.5" style={{ color: on ? C.green : C.muted }}>
                    <span style={{ width: 8, height: 8, borderRadius: 99,
                      background: on ? C.green : "#2a3a44",
                      boxShadow: on ? `0 0 8px ${C.green}` : "none" }} />
                    {on ? "ON" : "OFF"}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span style={{ color: C.muted }}>Installed</span>
                  <span style={{ color: C.text }}>{installed.length} / {MAX_INSTALL}</span>
                </div>
              </div>
            </div>
            <div className="px-3 pb-3 font-mono" style={{ fontSize: 10, color: C.muted }}>
              the subject everything acts on
            </div>
          </div>

          <Arrow />

          {/* ===== MIDDLE — EFFECT (work area) ===== */}
          <div style={{ width: "40%", background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 6 }}
            className="flex flex-col min-h-0">
            <Header>EFFECT</Header>
            {/* catalog */}
            <div className="flex-1 overflow-y-auto min-h-0">
              {BUFFS.map((b) => {
                const Ico = b.icon;
                const isSel = selected === b.id;
                const inst = installed.find((i) => i.id === b.id);
                return (
                  <button key={b.id} onClick={() => pick(b.id)}
                    style={{
                      background: isSel ? C.inset : "transparent",
                      borderLeft: `2px solid ${isSel ? C.green : "transparent"}`,
                      borderBottom: `1px solid ${C.line}`,
                    }}
                    className="w-full flex items-center gap-2.5 px-3 py-1.5 text-left">
                    <Ico size={15} style={{ color: isSel ? C.green : C.muted, flexShrink: 0 }} />
                    <span className="font-mono text-xs flex-1 truncate"
                      style={{ color: isSel ? C.text : C.text }}>{b.name}</span>
                    {inst && <span className="font-mono" style={{ fontSize: 10, color: C.green }}>L{inst.tier}</span>}
                    <span className="font-mono tabular-nums" style={{ fontSize: 10, color: C.amber }}>{b.cost.toFixed(2)}/s</span>
                  </button>
                );
              })}
            </div>
            {/* selected detail */}
            {selBuff && (
              <div style={{ borderTop: `1px solid ${C.lineHi}`, background: C.panel }} className="p-3">
                <div className="font-mono text-xs mb-2" style={{ color: C.text }}>"{selBuff.desc}"</div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <button onClick={() => stepTier(-1)}
                      style={{ border: `1px solid ${C.line}`, color: C.text, background: C.inset }}
                      className="w-7 h-7 rounded flex items-center justify-center"><Minus size={13} /></button>
                    <div style={{ border: `1px solid ${C.lineHi}`, background: C.slot, minWidth: 44 }}
                      className="h-7 rounded flex items-center justify-center font-mono text-sm font-bold">
                      {tier}<span style={{ color: C.muted, fontSize: 10 }} className="ml-1">/{selBuff.max}</span>
                    </div>
                    <button onClick={() => stepTier(1)}
                      style={{ border: `1px solid ${C.line}`, color: C.text, background: C.inset }}
                      className="w-7 h-7 rounded flex items-center justify-center"><Plus size={13} /></button>
                  </div>
                  <span className="font-mono tabular-nums text-sm" style={{ color: C.amber }}>
                    {(tier * selBuff.cost).toFixed(2)}/s
                  </span>
                </div>
                <button onClick={install}
                  style={{ background: C.greenDim, border: `1px solid ${C.green}`, color: "#eafff0" }}
                  className="w-full mt-3 py-1.5 rounded font-mono text-xs font-bold tracking-wider flex items-center justify-center gap-1">
                  {selInstalledTier ? "UPDATE" : "INSTALL"} <ChevronRight size={13} />
                </button>
              </div>
            )}
          </div>

          <Arrow />

          {/* ===== RIGHT — LOADOUT (outcome) ===== */}
          <div style={{ width: "32%", background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 6 }}
            className="flex flex-col min-h-0">
            <Header>LOADOUT</Header>
            <div className="flex-1 overflow-y-auto min-h-0">
              {installed.length === 0 && (
                <div className="p-4 font-mono text-xs" style={{ color: C.muted }}>
                  Nothing installed. Pick a buff in EFFECT and install it.
                </div>
              )}
              {installed.map((i) => {
                const b = BMAP[i.id]; const Ico = b.icon;
                return (
                  <div key={i.id} style={{ borderBottom: `1px solid ${C.line}` }}
                    className="flex items-center gap-2 px-3 py-1.5">
                    <Ico size={14} style={{ color: C.green, flexShrink: 0 }} />
                    <span className="font-mono text-xs flex-1 truncate" style={{ color: C.text }}>{b.name}</span>
                    <span className="font-mono" style={{ fontSize: 10, color: C.text }}>L{i.tier}</span>
                    <span className="font-mono tabular-nums" style={{ fontSize: 10, color: C.amber }}>
                      {(i.tier * b.cost).toFixed(2)}/s
                    </span>
                    <button onClick={() => remove(i.id)} title="Remove"
                      style={{ color: C.muted }} className="hover:text-red-400"><X size={13} /></button>
                  </div>
                );
              })}
            </div>
            {/* totals + switch */}
            <div style={{ borderTop: `1px solid ${C.lineHi}`, background: C.panel }} className="p-3">
              <div className="flex items-center justify-between font-mono text-sm">
                <span style={{ color: C.muted }} className="text-xs">Total drain</span>
                <span style={{ color: C.amber }} className="font-bold tabular-nums">{drain.toFixed(2)} Data/s</span>
              </div>
              <div className="flex items-center justify-between font-mono mt-1" style={{ fontSize: 10, color: C.muted }}>
                <span>at current Data</span><span>{fmtRuntime(runtime)}</span>
              </div>
              <button onClick={() => { if (!on && data <= 0) return; setOn((v) => !v); }}
                style={{
                  background: on ? C.greenDim : C.inset,
                  border: `1px solid ${on ? C.green : C.line}`,
                  color: on ? "#eafff0" : C.muted,
                }}
                className="w-full mt-3 py-2 rounded font-mono text-sm font-bold tracking-[0.2em] flex items-center justify-center gap-2">
                <Power size={15} /> {on ? "ON" : "OFF"}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
