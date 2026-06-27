"""Validate the engine against the numbers we hand-computed in the design chat.

Run: python -m pytest tests/   (or)   python tests/test_validation.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pokesnack import analytics, optimize, combine  # noqa: E402
from pokesnack.pool import load_pool  # noqa: E402


def approx(a, b, rel=0.02):
    return abs(a - b) <= rel * max(abs(a), abs(b), 1e-12)


def test_seasoning_math():
    # Shiny is bonus-additive: 3x Starf -> 1 + 4*3 = 13
    assert approx(combine(["starf", "starf", "starf"]).shiny_mult, 13)
    # Starf + Golden Apple -> 1 + 4 + 1 = 6
    assert approx(combine(["starf", "golden_apple"]).shiny_mult, 6)
    # Type is pure sum: 2x Chilan -> Normal x20
    assert approx(combine(["chilan", "chilan"]).type_mults["Normal"], 20)
    # Bucket tiers add: 2 EGA + 1 GA -> 21
    assert combine(["enchanted_golden_apple", "enchanted_golden_apple",
                    "golden_apple"]).bucket_tier == 21
    print("ok: seasoning combination math")


def test_ditto_share_with_chilan():
    from pokesnack.engine import per_bite_distribution
    pool = load_pool()
    dist, bundle = per_bite_distribution(pool, "mansion", "grounded", ["chilan"])
    # Within uncommon, Ditto share should be ~37.3% after Chilan x10.
    # P(ditto/bite) = P(uncommon, tier0) * share = 0.1028 * 0.3732
    assert approx(dist["ditto"], 0.1028 * 0.3732, rel=0.01)
    print(f"ok: Ditto/bite w/ 1 Chilan = {dist['ditto']:.5f}  (~0.03836)")


def test_shiny_ditto_best_no_ega():
    pool = load_pool()
    out = analytics.outlook(pool, "mansion", "grounded",
                            ["chilan", "starf", "starf"], "ditto")
    # Hand result: ~2,640 snacks per shiny Ditto.
    assert approx(out.snacks_per_shiny, 2640, rel=0.03)
    print(f"ok: 1 Chilan + 2 Starf -> {out.snacks_per_shiny:,.0f} snacks/shiny (~2,640)")


def test_68_snack_run():
    pool = load_pool()
    rb = analytics.run_breakdown(pool, "mansion_duraludon", "grounded",
                                 ["chilan", "golden_apple", "starf"], 68)
    # Hand results: ~34 Dittos seen, ~13 Duraludon seen, ~0.025 shiny Ditto.
    assert approx(rb["species_seen"]["ditto"], 34.3, rel=0.03)
    assert approx(rb["species_seen"]["duraludon"], 13.35, rel=0.03)
    assert approx(rb["shiny_seen"]["ditto"], 0.0251, rel=0.03)
    print(f"ok: 68-snack run -> Ditto seen ~{rb['species_seen']['ditto']:.1f}, "
          f"Duraludon ~{rb['species_seen']['duraludon']:.1f}, "
          f"shiny Ditto ~{rb['shiny_seen']['ditto']:.4f}")


def test_dura_vs_ditto_head_to_head():
    pool = load_pool()
    h = analytics.head_to_head(pool, "mansion_duraludon", "grounded",
                               ["chilan", "golden_apple", "starf"], "duraludon", "ditto")
    # Hand result: ~28% chance the shiny is Duraludon vs Ditto.
    assert approx(h["duraludon"], 0.28, rel=0.05)
    print(f"ok: shiny Dura-vs-Ditto -> Dura {h['duraludon']*100:.1f}% / "
          f"Ditto {h['ditto']*100:.1f}%")


def test_optimizer_picks_chilan_2starf_no_ega():
    pool = load_pool()
    from pokesnack.gamedata import SEASONINGS
    available = [k for k in SEASONINGS if k != "enchanted_golden_apple"]
    best = optimize.best(pool, "mansion", "grounded", "ditto", available=available)
    assert set(best.snack) == {"chilan", "starf"} and best.snack.count("starf") == 2
    print(f"ok: optimizer (no EGA) picks {best.label()} "
          f"-> {best.snacks_per_shiny:,.0f} snacks/shiny")


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("\nAll validation checks passed.")
