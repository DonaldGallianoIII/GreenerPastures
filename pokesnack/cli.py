"""Command-line interface for the PokeSnack engine."""
from __future__ import annotations

import argparse

from . import analytics, optimize
from .gamedata import SEASONINGS
from .pool import load_pool


def _parse_snack(s):
    return tuple(x.strip() for x in s.split(",") if x.strip()) if s else ()


def _fmt(x, nd=4):
    return f"{x:.{nd}g}"


def cmd_biomes(args, pool):
    for b in pool.biomes():
        print(f"  {b:24s} {pool.display(b)}")
        for ctx in pool.contexts(b):
            buckets = pool.buckets(b, ctx)
            print(f"      [{ctx}] " + ", ".join(
                f"{bk}:{len(roster)}" for bk, roster in buckets.items()))


def cmd_outlook(args, pool):
    out = analytics.outlook(pool, args.biome, args.context, _parse_snack(args.snack),
                            args.target)
    print(f"Target: {args.target}  |  Biome: {pool.display(args.biome)}")
    print(f"Snack:  {' + '.join(_parse_snack(args.snack)) or '(none)'}")
    print(f"  P(target / bite)        = {_fmt(out.p_per_bite)}")
    print(f"  expected target / snack = {_fmt(out.expected_per_snack)}")
    print(f"  P(shiny target / bite)  = {_fmt(out.shiny_p_per_bite)}")
    print(f"  expected shiny / snack  = {_fmt(out.expected_shiny_per_snack)}")
    spc = out.snacks_per_shiny
    print(f"  snacks per shiny        = {spc:,.0f}" if spc != float('inf') else
          "  snacks per shiny        = impossible")


def cmd_run(args, pool):
    rb = analytics.run_breakdown(pool, args.biome, args.context,
                                 _parse_snack(args.snack), args.snacks)
    print(f"Run: {args.snacks} snacks = {rb['bites']} bites  "
          f"(tier {rb['bucket_tier']}, shiny x{_fmt(rb['shiny_mult'])})")
    print("  Bucket rolls:")
    for b, n in rb["bucket_rolls"].items():
        print(f"    {b:12s} ~{n:6.1f}")
    print("  Top species seen:")
    for sp, n in sorted(rb["species_seen"].items(), key=lambda kv: -kv[1])[:8]:
        print(f"    {sp:20s} ~{n:6.2f}   (shiny ~{rb['shiny_seen'][sp]:.4f})")
    print(f"  Expected shinies (any species): {_fmt(rb['expected_total_shinies'])}"
          f"   P(>=1 shiny): {_fmt(rb['p_at_least_one_shiny_any'])}")
    if args.target:
        n = rb["species_seen"].get(args.target, 0.0)
        sh = rb["shiny_seen"].get(args.target, 0.0)
        print(f"  TARGET {args.target}: ~{n:.2f} seen, ~{sh:.4f} shiny")


def cmd_versus(args, pool):
    h = analytics.head_to_head(pool, args.biome, args.context,
                               _parse_snack(args.snack), args.a, args.b)
    print(f"Among shinies that are {args.a} or {args.b}:")
    print(f"  {args.a}: {h[args.a]*100:.1f}%")
    print(f"  {args.b}: {h[args.b]*100:.1f}%")


def cmd_optimize(args, pool):
    available = None
    if args.have:
        available = [x.strip() for x in args.have.split(",") if x.strip()]
    elif args.exclude:
        excl = {x.strip() for x in args.exclude.split(",")}
        available = [k for k in SEASONINGS if k not in excl]
    ranked = optimize.rank(pool, args.biome, args.context, args.target,
                           available=available, shiny=not args.count, top=args.top)
    kind = "raw target/snack" if args.count else "shiny"
    print(f"Best snacks for {args.target} ({kind}) in {pool.display(args.biome)}:")
    for i, c in enumerate(ranked, 1):
        if args.count:
            print(f"  {i:2d}. {c.label():42s} {c.expected_per_snack:.3f}/snack")
        else:
            spc = c.snacks_per_shiny
            spc_s = f"{spc:,.0f}" if spc != float('inf') else "inf"
            print(f"  {i:2d}. {c.label():42s} {spc_s:>9s} snacks/shiny")


def build_parser():
    p = argparse.ArgumentParser(prog="pokesnack", description=__doc__)
    p.add_argument("--data", help="path to spawn_pool.json")
    p.add_argument("--biome", default="mansion_duraludon")
    p.add_argument("--context", default="grounded")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("biomes", help="list loaded biomes/buckets")

    o = sub.add_parser("outlook", help="forecast a target under a snack")
    o.add_argument("target"); o.add_argument("--snack", default="")

    r = sub.add_parser("run", help="expected counts over N snacks")
    r.add_argument("--snack", default=""); r.add_argument("--snacks", type=int, default=10)
    r.add_argument("--target", default=None)

    v = sub.add_parser("versus", help="head-to-head shiny odds of two species")
    v.add_argument("a"); v.add_argument("b"); v.add_argument("--snack", default="")

    z = sub.add_parser("optimize", help="rank snacks for a target")
    z.add_argument("target")
    z.add_argument("--have", help="comma list of seasonings you have (restrict search)")
    z.add_argument("--exclude", help="comma list of seasonings to exclude")
    z.add_argument("--count", action="store_true", help="optimize raw count, not shiny")
    z.add_argument("--top", type=int, default=10)
    return p


_DISPATCH = {
    "biomes": cmd_biomes, "outlook": cmd_outlook, "run": cmd_run,
    "versus": cmd_versus, "optimize": cmd_optimize,
}


def main(argv=None):
    args = build_parser().parse_args(argv)
    pool = load_pool(args.data)
    _DISPATCH[args.cmd](args, pool)


if __name__ == "__main__":
    main()
