#!/usr/bin/env python3
"""Inspect a Litematica .litematic schematic — pure stdlib (gzip + NBT).

Usage:
  python3 litematica_inspect.py <file.litematic>            # metadata + block tallies
  python3 litematica_inspect.py <file.litematic> --coords <minecraft:id>[,<id>...]
        # also print the (x,y,z) of every block matching those ids (region-local coords)

Litematica stores a gzipped NBT tree: Metadata + Regions{<name>{Size, Position,
BlockStatePalette, BlockStates(packed long array)}}. Block index order is
y-major then z then x: idx = y*(w*l) + z*w + x. The long array is *continuously*
bit-packed (entries can straddle longs), bits = max(2, ceil(log2(palette))).
"""
import sys, gzip, struct
from collections import Counter


class NBTReader:
    def __init__(self, data): self.d, self.i = data, 0
    def u1(self): v = self.d[self.i]; self.i += 1; return v
    def i1(self): v = struct.unpack_from('>b', self.d, self.i)[0]; self.i += 1; return v
    def i2(self): v = struct.unpack_from('>h', self.d, self.i)[0]; self.i += 2; return v
    def u2(self): v = struct.unpack_from('>H', self.d, self.i)[0]; self.i += 2; return v
    def i4(self): v = struct.unpack_from('>i', self.d, self.i)[0]; self.i += 4; return v
    def i8(self): v = struct.unpack_from('>q', self.d, self.i)[0]; self.i += 8; return v
    def f4(self): v = struct.unpack_from('>f', self.d, self.i)[0]; self.i += 4; return v
    def f8(self): v = struct.unpack_from('>d', self.d, self.i)[0]; self.i += 8; return v
    def string(self):
        n = self.u2(); s = self.d[self.i:self.i + n]; self.i += n
        return s.decode('utf-8', 'replace')
    def payload(self, tag):
        if tag == 1: return self.i1()
        if tag == 2: return self.i2()
        if tag == 3: return self.i4()
        if tag == 4: return self.i8()
        if tag == 5: return self.f4()
        if tag == 6: return self.f8()
        if tag == 7:  # byte array
            n = self.i4(); b = self.d[self.i:self.i + n]; self.i += n; return list(b)
        if tag == 8: return self.string()
        if tag == 9:  # list
            t = self.u1(); n = self.i4(); return [self.payload(t) for _ in range(n)]
        if tag == 10:  # compound
            out = {}
            while True:
                t = self.u1()
                if t == 0: return out
                name = self.string(); out[name] = self.payload(t)
        if tag == 11:  # int array
            n = self.i4(); a = struct.unpack_from('>%di' % n, self.d, self.i); self.i += 4 * n; return list(a)
        if tag == 12:  # long array
            n = self.i4(); a = struct.unpack_from('>%dq' % n, self.d, self.i); self.i += 8 * n; return list(a)
        raise ValueError('unknown NBT tag %d at %d' % (tag, self.i))


def load(path):
    raw = open(path, 'rb').read()
    while raw[:2] == b'\x1f\x8b':   # .litematic is gzip(NBT); .litematic.gz is gzipped again
        raw = gzip.decompress(raw)
    r = NBTReader(raw)
    assert r.u1() == 10, 'root tag is not a compound'
    r.string()
    return r.payload(10)


def bits_for(n): return max(2, (n - 1).bit_length())


def bitget(longs, bits, index):
    mask = (1 << bits) - 1
    sb = index * bits
    sl, el, off = sb >> 6, (sb + bits - 1) >> 6, sb & 63
    us = longs[sl] & 0xFFFFFFFFFFFFFFFF
    if sl == el:
        return (us >> off) & mask
    ue = longs[el] & 0xFFFFFFFFFFFFFFFF
    return ((us >> off) | (ue << (64 - off))) & mask


def main():
    path = sys.argv[1]
    want = set()
    if len(sys.argv) > 3 and sys.argv[2] == '--coords':
        want = set(sys.argv[3].split(','))

    nbt = load(path)
    meta = nbt.get('Metadata', {})
    es = meta.get('EnclosingSize', {})
    print('File          :', path)
    print('Name / Author :', meta.get('Name'), '/', meta.get('Author'))
    print('EnclosingSize :', es.get('x'), es.get('y'), es.get('z'))
    print('TotalBlocks   :', meta.get('TotalBlocks'), ' RegionCount:', meta.get('RegionCount'))

    global_nonair = 0
    for rname, reg in nbt.get('Regions', {}).items():
        sz, pos = reg['Size'], reg['Position']
        w, h, l = abs(sz['x']), abs(sz['y']), abs(sz['z'])
        pal = reg['BlockStatePalette']
        names = [p['Name'] for p in pal]
        longs = reg['BlockStates']
        bits = bits_for(len(pal))
        counts = Counter()
        hits = []
        for y in range(h):
            for z in range(l):
                for x in range(w):
                    idx = y * (w * l) + z * w + x
                    nm = names[bitget(longs, bits, idx)]
                    counts[nm] += 1
                    if nm in want:
                        hits.append((x, y, z, nm))
        nonair = sum(c for nm, c in counts.items() if nm != 'minecraft:air')
        global_nonair += nonair
        print('\nRegion %r  size=%dx%dx%d  pos=(%d,%d,%d)  palette=%d  bits=%d  non-air=%d'
              % (rname, w, h, l, pos['x'], pos['y'], pos['z'], len(pal), bits, nonair))
        for nm, c in counts.most_common():
            if nm != 'minecraft:air':
                print('   %6d  %s' % (c, nm))
        if want:
            print('  --- coords (region-local x,y,z) for %s ---' % ','.join(sorted(want)))
            for (x, y, z, nm) in hits:
                print('   (%d, %d, %d)  %s' % (x, y, z, nm))

    tb = meta.get('TotalBlocks')
    ok = 'MATCH ✓' if tb == global_nonair else 'MISMATCH ✗'
    print('\nDecode check: non-air %d vs Metadata.TotalBlocks %s  -> %s' % (global_nonair, tb, ok))


if __name__ == '__main__':
    main()
