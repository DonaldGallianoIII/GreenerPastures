# Changelog — Greener Pastures

## [unreleased] — 1.0.0-beta.1 (release-prep)

### Added
- **Field Guide** (#18): in-console handbook (Guide tab) + a Field Guide item every player receives on
  first join; right-click opens the guide directly.
- **GPU economy live (§7.5)**: Augmenter installs cost GPU (quality 2 ◈ / throughput 1 ◈); Daemon buff
  tiers cost 2 ◈ per step. Re-picking a parameterized augment's value stays free; removals never refund.
  Baked constants — no config, by design.
- **Built-in profiler**: `/gp perf` (live ms table), `/gp perf flame` (self-contained flame-graph HTML
  in `gp-logs/`), `/gp perf reset`.
- **Pasture health strip**: not-linked / no-Kernel / needs-parents / tray-full / BioBank-full warnings on
  the pasture view + ⚠ badges on the Pastures tab.
- **Breeding-meta UI**: Nature Lock picker (25 natures + stat hints), Ball Lock picker, EV Primer
  allocator (510 budget), Ability Splice + Egg-Move Tutor rows in the Augmenter.
- **Inbox**: dismissible console notifications for away-progress (catch-up deposits/broods) — no chat spam.
- **Catch-up**: drops and eggs accrue for unloaded chunks and roll exactly on reload (12h cap; online
  away-time only — offline gaps are gated out). Kernel drop rates doubled and statistically verified.
- Dedicated **creative tab** with every mod item.

### Design decisions (locked for release)
- Multi-pair breeding has a hard **2.5-minute floor** per brood regardless of kernel tier × Speed augment ×
  tether stacking (server protection; signed off 2026-07-04). Kernel perk ladder final: pairs 2→8,
  egg speed ×1.1→×1.6, drop +0.5%→+5.0% (Greener jumps drops+pairs, not cadence).

### Changed
- **Idle cost ≈ 0**: the console pipeline (serialize + poll + Chromium pump) fully stands down while the
  Notebook is closed; the server never re-sends unchanged console data (per-player change gates); saves
  re-encode only players/pastures whose data changed.
- Release log level defaults to INFO; the single `-Dgreenerpastures.qa=true` flag restores DEBUG **and**
  registers the QA commands (`/gp breed|harvest|data|daemon|augment`) — public builds don't carry them.
- Harvest sweeps schedule per pasture (catch-up fires within a second of chunk reload, lined up with eggs).

### Fixed
- Pulling items with a full inventory could destroy them (inventory capacity is now counted manually and
  refusals are explicit).
- New singleplayer worlds inherited the previous world's console stats (full session hygiene on both sides).
- Closed singleplayer worlds stayed pinned in memory across world switches (weak world-key cache).
- Client pasture caches could collide across dimensions at identical coordinates (dim-scoped keys + LRU).
- The Augmenter's APPLY button was permanently disabled by a phantom GPU-cost check; both benches now show
  and charge real costs.
