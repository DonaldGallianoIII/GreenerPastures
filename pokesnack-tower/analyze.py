#!/usr/bin/env python3
"""Structural analysis of a PokeSnack design schematic: columns, spawn surface,
apparatus, builder sign-text, and the home-block (-8,-8) offset check.
Reuses the NBT decoder in tools/litematica_inspect.py."""
import sys
sys.path.insert(0, '/home/donaldgalliano/pokemon-prediction/tools')
import litematica_inspect as L
from collections import defaultdict


def analyze(path):
    nbt = L.load(path)
    rname, reg = next(iter(nbt['Regions'].items()))
    sz = reg['Size']; w, h, l = abs(sz['x']), abs(sz['y']), abs(sz['z'])
    names = [p['Name'] for p in reg['BlockStatePalette']]
    longs = reg['BlockStates']; bits = L.bits_for(len(names))

    grid, bytype = {}, defaultdict(list)
    for y in range(h):
        for z in range(l):
            for x in range(w):
                nm = names[L.bitget(longs, bits, y * (w * l) + z * w + x)]
                if nm == 'minecraft:air':
                    continue
                grid[(x, y, z)] = nm
                bytype[nm].append((x, y, z))

    print('== %s ==' % path.split('/')[-1])
    print('size (w,h,l) = %d,%d,%d   non-air = %d' % (w, h, l, len(grid)))

    def columns(nm):
        cols = defaultdict(list)
        for (x, y, z) in bytype.get(nm, []):
            cols[(x, z)].append(y)
        return cols

    for key in ('cobblemon:poke_snack', 'minecraft:ladder'):
        cols = columns(key)
        print('\n%s: %d blocks in %d column(s)' % (key, len(bytype.get(key, [])), len(cols)))
        for (x, z), ys in sorted(cols.items()):
            ys.sort()
            gaps = [b for a, b in zip(ys, ys[1:]) if b - a != 1]
            print('   x=%2d z=%2d  count=%3d  y=%d..%d%s'
                  % (x, z, len(ys), ys[0], ys[-1], '  (contiguous)' if not gaps else '  GAPS!'))

    print('\napparatus / singletons (excluding planks, glass):')
    for nm in sorted(bytype):
        if nm in ('cobblemon:poke_snack', 'minecraft:ladder', 'minecraft:oak_planks', 'minecraft:glass'):
            continue
        ps = bytype[nm]
        print('   %-28s x%-3d %s' % (nm, len(ps), ps if len(ps) <= 10 else ps[:10] + ['...']))

    sc = columns('cobblemon:poke_snack')
    if sc:
        (sx, sz), ys = max(sc.items(), key=lambda kv: len(kv[1]))
        hx, hz = sx - 8, sz - 8
        homecol = sorted((y, grid[(hx, y, hz)]) for y in range(h) if (hx, y, hz) in grid)
        print('\nhome-offset check: main snack column x=%d z=%d -> home col x=%d z=%d' % (sx, sz, hx, hz))
        print('   solid blocks in that home column:', homecol if homecol else '(none in schematic)')
        for d in bytype.get('minecraft:dirt', []):
            print('   dirt @ %s   offset from snack col: dx=%d dy(rel snack span)=%d..%d dz=%d'
                  % (d, d[0] - sx, d[1] - ys[0], d[1] - ys[-1], d[2] - sz))

    tes = reg.get('TileEntities', [])
    if tes:
        print('\ntile entities (%d):' % len(tes))
        for te in tes:
            txt = ''
            for side in ('front_text', 'back_text'):
                blk = te.get(side)
                if isinstance(blk, dict):
                    for m in blk.get('messages', []):
                        if str(m).strip() not in ('', '""', '{"text":""}'):
                            txt += ' [%s]' % m
            print('   %-22s @ (%s,%s,%s)%s' % (te.get('id', '?'), te.get('x'), te.get('y'), te.get('z'), txt))


if __name__ == '__main__':
    analyze(sys.argv[1])
