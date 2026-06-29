#!/usr/bin/env python3
"""
Greener Pastures — Harvester drop SIMULATOR.

Faithfully replays the in-game model so you can tune rates without waiting in QA:
  LEVER 1 (cadence): each tethered mon, per IRL minute, has (proc + droprate)% to proc a drop EVENT.
  LEVER 2 (the roll): a procced mon runs Cobblemon's getDrops — amount budget + per-entry % + qty range
                      (replicated from the decompiled DropTable.getDrops / ItemDropEntry).

Examples:
  python3 sim_drops.py                                   # 16x Froakie, 3% proc + 0.25% kernel, 1 hour
  python3 sim_drops.py --fill Gible --hours 8
  python3 sim_drops.py --roster Froakie,Greninja,Pikachu,Eevee,Ditto --proc 3 --droprate 0.25
  python3 sim_drops.py --fill Froakie --droprate 1.0     # what a Drop-Rate-augmented kernel feels like
"""
import argparse, json, os, random, collections

REF = os.path.dirname(os.path.abspath(__file__))
DATA = json.load(open(os.path.join(REF, "all-species-drops.json")))
# case-insensitive lookup
BY_LOWER = {k.lower(): k for k in DATA}

def find(name):
    k = BY_LOWER.get(name.strip().lower())
    return (k, DATA[k]) if k else (None, None)

def parse_range(s):
    a, b = str(s).split("-"); return int(a), int(b)

def budget_qty(e):                       # what getQuantity() returns for the amount budget (decompile)
    return int(e.get("quantity", 1))

def stack_qty(e, rng):                   # the actual count dropped (RangesKt.random over the range, else quantity)
    if "quantityRange" in e:
        a, b = parse_range(e["quantityRange"]); return rng.randint(a, b)
    return int(e.get("quantity", 1))

def get_drops(table, rng):
    """Replicates com.cobblemon...DropTable.getDrops(amount, pokemon): amount = quantity budget; walk
       entries in order, select if random*100 < percentage, spend budget by getQuantity(), stop when full."""
    amt = table.get("amount", 1)
    chosen = amt if isinstance(amt, int) else rng.randint(*parse_range(amt))
    possible = [e for e in table.get("entries", []) if "item" in e and budget_qty(e) <= chosen]
    out = []
    count = 0
    while count < chosen and possible:
        picked = next((e for e in possible if rng.random() * 100.0 < float(e.get("percentage", 100.0))), None)
        if picked is None:
            count += 1; continue                      # nothing fired this pass (matches the decompile)
        q = stack_qty(picked, rng)
        if q > 0: out.append((picked["item"], q))
        count += budget_qty(picked)
        possible = [e for e in possible if e is not picked and budget_qty(e) <= chosen - count]
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--roster", help="comma-separated species (the tethered mons)")
    ap.add_argument("--fill", help="fill the pasture with N of ONE species")
    ap.add_argument("--count", type=int, default=16, help="mons in the pasture (with --fill or default); cap 16")
    ap.add_argument("--proc", type=float, default=3.0, help="base proc %% per mon per minute")
    ap.add_argument("--droprate", type=float, default=0.25, help="drop-rate bonus %% (kernel base + augments)")
    ap.add_argument("--hours", type=float, default=1.0)
    ap.add_argument("--trials", type=int, default=2000)
    ap.add_argument("--seed", type=int, default=1)
    a = ap.parse_args()

    if a.roster:
        names = [s for s in a.roster.split(",") if s.strip()]
    else:
        fill = a.fill or "Froakie"
        names = [fill] * min(a.count, 16)
    roster = []
    for n in names:
        key, tbl = find(n)
        if not tbl: print(f"  !! species not found: {n} (skipped)"); continue
        roster.append((key, tbl))
    if not roster: print("no valid species"); return

    minutes = int(round(a.hours * 60))
    eff = (a.proc + a.droprate) / 100.0
    rng = random.Random(a.seed)

    totals = collections.Counter()
    events = 0
    for _ in range(a.trials):
        for _m in range(minutes):
            for _key, tbl in roster:
                if rng.random() < eff:
                    events += 1
                    for item, q in get_drops(tbl, rng):
                        totals[item] += q
    T = a.trials

    print(f"\nGreener Pastures — Harvester drop simulator")
    rost = collections.Counter(k for k, _ in roster)
    print("roster: " + ", ".join(f"{c}× {k}" for k, c in rost.items()))
    print(f"proc {a.proc}% + droprate {a.droprate}% = {a.proc + a.droprate}%/mon/min · "
          f"{a.hours}h ({minutes} min) · {T} trials")
    print(f"\nexpected drop EVENTS: {events / T:.1f} over {a.hours}h  ({events / T / a.hours:.1f}/h)\n")
    print(f"  {'item':40} {'per ' + str(a.hours) + 'h':>10} {'/hour':>8}")
    print("  " + "-" * 60)
    for item, n in totals.most_common():
        per = n / T
        print(f"  {item:40} {per:>10.2f} {per / a.hours:>8.2f}")
    tot = sum(totals.values()) / T
    print("  " + "-" * 60)
    print(f"  {'TOTAL ITEMS':40} {tot:>10.2f} {tot / a.hours:>8.2f}\n")

if __name__ == "__main__":
    main()
