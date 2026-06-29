#!/usr/bin/env python3
"""
Greener Pastures — RITUAL gacha simulator (companion to sim_drops.py).

Faithfully replays the in-game ritual model so you can tune odds/pity WITHOUT grinding:
  ACCRUAL (per Harvester tick = 1 IRL min): per mon, a pull-proc = (BASE_PROC + droprate)/rarityFactor
          (3x rarer than a staple drop by default); each proc banks (1 + yield) pulls.
          → so Drop Rate speeds the gacha, Drop Yield fattens each proc, a fuller pasture pulls faster.
  GACHA  (per pull): base% with HARD pity (guaranteed by N) + optional SOFT pity ramp — the per-item tier.
Mirrors HarvesterBlockEntity.customDrops + ritual/Gacha + RitualConfig.defaults().

Examples:
  python3 sim_rituals.py --types "fire:6,dark:5,ghost:5"                 # Nether Forge, full pasture
  python3 sim_rituals.py --types "fairy:8,ghost:8" --species sableye     # Last Stand (totem)
  python3 sim_rituals.py --types "fire:6,dark:5,ghost:5" --droprate 1.0 --yield 1   # with drop augments
  python3 sim_rituals.py --types "a:3,b:3,c:3,d:3,e:4"                   # Forbidden Orchard (5 distinct)
  python3 sim_rituals.py --config "/path/to/config/greenerpastures/rituals.json" --types "fire:6,dark:5,ghost:5"
"""
import argparse, json, os, random

BASE_PROC = 0.03          # matches HarvesterBlockEntity.BASE_PROC (3%/mon/min)
DEFAULT_RARITY = 3.0      # matches RitualConfig default rarityFactor

# Mirror of RitualConfig.defaults() — keep in sync (or pass --config to read the game-written rituals.json).
DEFAULT_RITUALS = [
    dict(id="nether_forge",      name="Nether Forge",      type_min={"fire":1,"dark":1,"ghost":1}, distinct=0, sig=[],
         item="minecraft:netherite_scrap",     qty=1, base=5.0, hard=30, soft=0),
    dict(id="forbidden_orchard", name="Forbidden Orchard", type_min={},                           distinct=5, sig=[],
         item="minecraft:enchanted_golden_apple", qty=1, base=3.0, hard=40, soft=25),
    dict(id="last_stand",        name="Last Stand",        type_min={"fairy":1,"ghost":1},        distinct=0, sig=["sableye"],
         item="minecraft:totem_of_undying",    qty=1, base=4.0, hard=35, soft=0),
    dict(id="endless_sky",       name="Endless Sky",       type_min={"flying":1,"dragon":1,"ghost":1}, distinct=0, sig=[],
         item="minecraft:elytra",              qty=1, base=2.0, hard=50, soft=30),
    dict(id="tide_caller",       name="Tide Caller",       type_min={"water":1,"ice":1},          distinct=0, sig=["kyogre","suicune","lugia"],
         item="minecraft:trident",             qty=1, base=5.0, hard=30, soft=0),
    dict(id="soul_convergence",  name="Soul Convergence",  type_min={"ghost":3,"dark":1},         distinct=0, sig=["darkrai","giratina"],
         item="minecraft:nether_star",         qty=1, base=1.5, hard=60, soft=40),
    dict(id="dragons_hoard",     name="Dragon's Hoard",    type_min={"dragon":2,"flying":1},      distinct=0, sig=["rayquaza","dialga","palkia"],
         item="minecraft:dragon_egg",          qty=1, base=1.0, hard=80, soft=50),
]


def effective_chance(base, hard, soft, pull_index):
    """Mirror of Gacha.effectiveChance."""
    if hard > 0 and pull_index >= hard:
        return 100.0
    if soft > 0 and hard > soft and pull_index >= soft:
        frac = (pull_index - soft) / (hard - soft)
        return base + (100.0 - base) * frac
    return base


def satisfied(r, type_counts, species):
    """(active?, reason-if-not) — mirror of Requirement.satisfiedBy."""
    distinct = sum(1 for c in type_counts.values() if c > 0)
    if distinct < r["distinct"]:
        return False, f"needs {r['distinct']} distinct types (have {distinct})"
    for t, need in r["type_min"].items():
        if type_counts.get(t, 0) < need:
            return False, f"needs {t}≥{need} (have {type_counts.get(t,0)})"
    if r["sig"] and not any(s in species for s in r["sig"]):
        return False, "needs a signature mon: " + "/".join(r["sig"])
    return True, ""


def simulate(r, mons, ritual_proc, pulls_per_proc, minutes, trials, rng):
    """Replay accrual + auto-pull gacha; return (avg pulls, avg hits) per full run."""
    total_pulls = total_hits = 0
    for _ in range(trials):
        pity = 0
        for _m in range(minutes):
            procs = rng.binomialvariate(mons, ritual_proc) if mons > 0 else 0
            pulls = procs * pulls_per_proc
            total_pulls += pulls
            for _p in range(pulls):
                idx = pity + 1
                if rng.random() * 100.0 < effective_chance(r["base"], r["hard"], r["soft"], idx):
                    total_hits += 1
                    pity = 0
                else:
                    pity = idx
    return total_pulls / trials, total_hits / trials


def load_config(path):
    """Parse a game-written rituals.json into (ritual list, rarityFactor)."""
    cfg = json.load(open(path))
    out = []
    for r in cfg.get("rituals", {}).get("rituals", []):
        if not r.get("enabled", True):
            continue
        req = r.get("requirement", {})
        out.append(dict(id=r["id"], name=r.get("name", r["id"]),
                        type_min={k.lower(): v for k, v in (req.get("typeMinCounts") or {}).items()},
                        distinct=req.get("minDistinctTypes", 0),
                        sig=[s.lower() for s in (req.get("signatureSpeciesAnyOf") or [])],
                        item=r["outputItem"], qty=r.get("outputQty", 1),
                        base=r["baseChancePercent"], hard=r["hardPity"], soft=r.get("softPityStart", 0)))
    return out, float(cfg.get("rarityFactor", DEFAULT_RARITY))


def parse_types(s):
    counts = {}
    for tok in (s or "").split(","):
        tok = tok.strip()
        if not tok:
            continue
        if ":" in tok:
            name, n = tok.split(":"); counts[name.strip().lower()] = int(n)
        else:
            counts[tok.lower()] = 1
    return counts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--types", default="", help='pasture types as "fire:6,dark:5,ghost:5" (mons-per-type)')
    ap.add_argument("--species", default="", help="signature species present (comma-sep, e.g. sableye)")
    ap.add_argument("--mons", type=int, default=0, help="distinct mons in the pasture (accrual); default = sum of types, cap 16")
    ap.add_argument("--droprate", type=float, default=0.25, help="drop-rate %% (kernel base 0.25 + augments/tethers)")
    ap.add_argument("--yield", type=int, default=0, dest="yield_", help="drop-yield bonus (adds pulls per proc)")
    ap.add_argument("--rarity", type=float, default=None, help="rarityFactor (default 3 / from --config)")
    ap.add_argument("--hours", type=float, default=8.0)
    ap.add_argument("--trials", type=int, default=400)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--config", default="", help="optional rituals.json to sim instead of the built-in defaults")
    a = ap.parse_args()

    rituals, cfg_rarity = (load_config(a.config) if a.config else (DEFAULT_RITUALS, DEFAULT_RARITY))
    rarity = a.rarity if a.rarity is not None else cfg_rarity
    rarity = max(1e-9, rarity)

    type_counts = parse_types(a.types)
    species = set(s.strip().lower() for s in a.species.split(",") if s.strip())
    mons = a.mons if a.mons > 0 else min(16, sum(type_counts.values())) or 16

    ritual_proc = (BASE_PROC + a.droprate / 100.0) / rarity
    pulls_per_proc = 1 + max(0, a.yield_)
    minutes = int(round(a.hours * 60))
    rng = random.Random(a.seed)

    print("\nGreener Pastures — Ritual gacha simulator")
    print(f"pasture: {mons} mons · types " + (", ".join(f"{k}:{v}" for k, v in type_counts.items()) or "(none)")
          + (" · species " + ",".join(sorted(species)) if species else ""))
    print(f"accrual: ({BASE_PROC*100:.2f}% + {a.droprate:.2f}% droprate)/{rarity:g} = {ritual_proc*100:.3f}%/mon/min "
          f"· {pulls_per_proc} pull(s)/proc · ~{mons*ritual_proc*60:.1f} pulls/h"
          f"{' (×'+str(pulls_per_proc)+' yield)' if pulls_per_proc>1 else ''}")
    print(f"window:  {a.hours}h · {a.trials} trials · seed {a.seed}\n")

    active = [(r, satisfied(r, type_counts, species)) for r in rituals]
    live = [r for r, (ok, _) in active if ok]

    if live:
        print(f"  {'ritual':20} {'item':26} {'odds/pity':>11} {'pulls/h':>8} {'items/h':>8} {'1 per':>9}")
        print("  " + "-" * 88)
        for r in live:
            pulls_h, hits_run = simulate(r, mons, ritual_proc, pulls_per_proc, minutes, a.trials, rng)
            pulls_h /= a.hours
            items_h = hits_run * r["qty"] / a.hours
            per = f"{1/items_h:.1f} h" if items_h > 1e-9 else "  —"
            softnote = f"+s{r['soft']}" if r["soft"] else ""
            print(f"  {r['name']:20} {r['item'].replace('minecraft:',''):26} "
                  f"{str(r['base'])+'%/'+str(r['hard'])+softnote:>11} {pulls_h:>8.1f} {items_h:>8.3f} {per:>9}")
        print("  " + "-" * 88)
    else:
        print("  (no rituals active for this composition)")

    inactive = [(r, why) for r, (ok, why) in active if not ok]
    if inactive:
        print("\n  inactive here:")
        for r, why in inactive:
            print(f"    {r['name']:20} — {why}")
    print()


if __name__ == "__main__":
    main()
