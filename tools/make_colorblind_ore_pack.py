#!/usr/bin/env python3
"""Build a client-side colorblind ore resource pack.

Pulls the vanilla ore textures straight from the 1.21.1 client jar and recolors
ONLY the mineral speckles (the pixels that differ from the stone/deepslate
background), leaving the surrounding rock byte-for-byte vanilla. Brightness of
each speckle is preserved so the ore keeps its 3-D look — we only swap the hue.

Re-tune by editing TARGET_HUE / SAT below (hue is 0..1; blue~0.56, orange~0.07,
magenta~0.85, yellow~0.15, cyan~0.50). Then re-run.
"""
import zipfile, colorsys, os, random
from PIL import Image

JAR = "/mnt/c/Users/deuce/AppData/Roaming/.minecraft/versions/1.21.1/1.21.1.jar"
OUT = "/home/donaldgalliano/pokemon-prediction/colorblind-ore-pack"
BLOCK = "assets/minecraft/textures/block"
PACK_FORMAT = 34  # 1.21 / 1.21.1

TOL = 10            # how different from stone a pixel must be to count as "ore"

SEED = 1337         # fixed so the speckle pattern is stable across re-runs

# (ore texture, background texture, target hue 0..1, saturation, brightness pop,
#  var, ice, label)
# sat = how pure/deep the color is; pop = extra brightness added to each speckle.
# Saturated blue reads as DIM to the eye even at high value (blue carries little
# perceived luminance), so iron is desaturated toward a bright sky-blue AND popped
# hard so it actually glows in dark caves like copper's orange does. Same hue (0.56)
# -> still clearly "blue", just lighter. Copper keeps its deep saturated orange.
#
# var = per-pixel jitter strength (0 = flat color, 1 = full mottled hue/sat/value
#       variation so the ore reads as crystalline rather than a solid blob).
# ice = fraction of speckles turned into bright pale "ice-blue" sparkles.
# emit = emissive glow strength 0..1. Writes an `_e` overlay so the specks render
#        bright in the dark (Continuity mod, OptiFine emissive format); the glow color
#        is the speck color scaled by `emit`, so lower = softer glow. The stone
#        background stays normally lit. 1.0 = full-bright (iron), 0 = no glow (copper).
JOBS = [
    ("iron_ore",            "stone",     0.56, 0.55, 0.22, 1.0, 0.22, 1.0,  "iron  -> bright blue (glow)"),
    ("deepslate_iron_ore",  "deepslate", 0.56, 0.55, 0.22, 1.0, 0.22, 1.0,  "iron  -> bright blue (deepslate, glow)"),
    ("coal_ore",            "stone",     0.58, 0.08, 0.32, 0.5, 0.0,  0.1, "coal  -> faint silver glow"),
    ("deepslate_coal_ore",  "deepslate", 0.58, 0.08, 0.32, 0.5, 0.0,  0.1, "coal  -> faint silver glow (deepslate)"),
    ("copper_ore",          "stone",     0.07, 0.92, 0.05, 0.0, 0.0,  0.0,  "copper-> orange"),
    ("deepslate_copper_ore","deepslate", 0.07, 0.92, 0.05, 0.0, 0.0,  0.0,  "copper-> orange(deepslate)"),
]


def clamp(x):
    return max(0.0, min(1.0, x))


def speckle_color(rng, hue, sat, val, var, ice):
    """Return RGB for one recolored speckle, with optional jitter + ice sparkles."""
    if ice and rng.random() < ice:
        # bright pale ice-blue fleck: nudged toward cyan, lightened, brightened
        hh = hue - rng.uniform(0.0, 0.05)
        ss = sat * rng.uniform(0.15, 0.40)
        vv = clamp(val + rng.uniform(0.05, 0.15))
    elif var:
        hh = hue + rng.uniform(-0.03, 0.03) * var
        ss = clamp(sat + rng.uniform(-0.20, 0.12) * var)
        vv = clamp(val + rng.uniform(-0.14, 0.10) * var)
    else:
        hh, ss, vv = hue, sat, val
    return colorsys.hsv_to_rgb(hh % 1.0, ss, vv)

os.makedirs(os.path.join(OUT, BLOCK), exist_ok=True)

def load(zf, name):
    with zf.open(f"{BLOCK}/{name}.png") as f:
        return Image.open(f).convert("RGBA")

previews = []
with zipfile.ZipFile(JAR) as zf:
    for ore_name, base_name, target_h, sat, pop, var, ice, emit, label in JOBS:
        ore = load(zf, ore_name)
        w, h = ore.size
        base = load(zf, base_name).crop((0, 0, w, h))
        out = ore.copy()
        emissive = Image.new("RGBA", (w, h), (0, 0, 0, 0))           # transparent overlay
        op, bp, ou, em = ore.load(), base.load(), out.load(), emissive.load()
        rng = random.Random(SEED)                                    # deterministic per job
        changed = 0
        for y in range(h):
            for x in range(w):
                r, g, b, a = op[x, y]
                br, bg, bb, _ = bp[x, y]
                if a == 0:
                    continue
                if abs(r - br) + abs(g - bg) + abs(b - bb) > TOL:   # a speckle pixel
                    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                    v = min(1.0, v * 1.05 + pop)                     # per-ore brightness pop
                    nr, ng, nb = speckle_color(rng, target_h, sat, v, var, ice)
                    rgb = (int(nr * 255), int(ng * 255), int(nb * 255))
                    ou[x, y] = (rgb[0], rgb[1], rgb[2], a)
                    # glow color = speck color dimmed by emit strength (softer glow)
                    em[x, y] = (int(rgb[0] * emit), int(rgb[1] * emit), int(rgb[2] * emit), 255)
                    changed += 1
        out.save(os.path.join(OUT, BLOCK, f"{ore_name}.png"))
        if emit > 0:
            emissive.save(os.path.join(OUT, BLOCK, f"{ore_name}_e.png"))
        print(f"  {label:38s} {changed:3d} px{f'  (+glow x{emit})' if emit > 0 else ''}")
        previews.append((ore, out))

# pack.mcmeta
with open(os.path.join(OUT, "pack.mcmeta"), "w") as f:
    f.write('{\n  "pack": {\n    "pack_format": %d,\n'
            '    "description": "Colorblind Ores \\u2014 iron=blue, copper=orange"\n  }\n}\n'
            % PACK_FORMAT)

# emissive config (OptiFine format, read by the Continuity mod): any texture whose
# name ends in `_e` is an emissive overlay rendered full-bright. -> iron specks glow.
optifine_dir = os.path.join(OUT, "assets", "minecraft", "optifine")
os.makedirs(optifine_dir, exist_ok=True)
with open(os.path.join(optifine_dir, "emissive.properties"), "w") as f:
    f.write("suffix.emissive=_e\n")

# side-by-side preview (Original | Colorblind), 4x scale
S, PAD = 64, 8
cols, rows = 2, len(previews)
pv = Image.new("RGBA", (cols * S + 3 * PAD, rows * S + (rows + 1) * PAD), (32, 34, 40, 255))
for i, (orig, new) in enumerate(previews):
    y = PAD + i * (S + PAD)
    pv.paste(orig.resize((S, S), Image.NEAREST), (PAD, y))
    pv.paste(new.resize((S, S), Image.NEAREST), (2 * PAD + S, y))
pv.save(os.path.join(OUT, "preview.png"))
print("pack ->", OUT)
print("preview -> preview.png")
