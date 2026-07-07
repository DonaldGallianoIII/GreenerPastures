# 🔭 Observability Standard — log everything, readable in real time

_Greener Pastures · **LOCKED 2026-06-28 (Deuce)**. Design philosophy: the systems we build are complex and **data-hungry by nature**, so **every feature ships with a debug log we can both read live while the game runs.** Observability is a build requirement, not an afterthought. `glow OBSERVABILITY.md`._

## The rule
Every feature we build emits structured debug events through **one shared logging seam** (`GpLog`). If a feature makes a decision, branches, mutates state, hits an edge, or errors — **it logs it.** "We'll add logging if we need it" is banned; we always need it.

## Why
- The breeding / queue / economy systems have many moving parts and timing (off-thread breeding, away-from-chunk catch-up, IV/shiny filters, proc rolls, fuel drain). Bugs here are **emergent and hard to reproduce** without a trace.
- Deuce plays on **Windows MC**; Claude works on **Linux**. A shared, live log is the only way we debug **together in real time** — no screenshot round-trips.

## Format — line-delimited JSON (human-tailable + machine-parseable)
One event per line:
```
{"t":"2026-06-28T14:03:22.114","lvl":"DEBUG","tag":"breeder","ev":"enqueue","pos":"12,64,-30","queueSize":3,"shiny":false}
```
- `t` timestamp · `lvl` level (TRACE/DEBUG/INFO/WARN/ERROR) · `tag` subsystem · `ev` event name · then arbitrary payload fields.
- JSONL ⇒ `tail -f` reads by eye **and** we can `jq` / parse it for analysis later. Same shape as the existing analytics `events.jsonl` — different audience (see below).

## The seam: `GpLog` (every feature calls this — no ad-hoc file writes)
- `GpLog.d(tag, ev, k1, v1, k2, v2, …)` plus `.i / .w / .e` for levels. One class owns: file handle, format, **off-thread write queue**, prompt flush, rotation, level filter.
- **Off the game thread, flushed promptly** — a background writer drains a queue and flushes each line (or on a ≤1s timer) so a live tail is current. **Never blocks or stutters the game thread.**
- **Never crashes gameplay** — all logging wrapped; a logging failure is swallowed (matches the "a bonus roll can never break egg-gen" discipline).
- **Levels + tags** so we can filter/grep; a config knob sets verbosity (default DEBUG in dev, INFO in release).

## Files & location
- **Per-session file**, timestamped, **plus a stable `latest.log`** (the current session) for easy `tail -F`. Rotate / cap so it never grows unbounded.
- **WSL reality:** MC is a *Windows* process, so a bare `/home/...` path lands on `C:\home\...`, **not** Linux. Two real options:
  - **(A — DEFAULT) Windows-side under the instance, exposed to Linux via symlink.** Mod writes to `…/Greener Pastures Test/gp-logs/`; we `ln -s` it to `~/gp-logs`. Native fast writes (no MC stutter), Claude reads via `/mnt/c` on demand, Deuce tails `~/gp-logs/latest.log`. Real-time tail on `/mnt/c` may need polling (`tail -F`, ~1 s) since inotify is unreliable on drvfs — close enough to live.
  - **(B — fallback) Literal ext4 via UNC** (`\\wsl.localhost\<distro>\home\…`): true Linux fs, instant inotify tail — but cross-boundary 9p writes are slower / fragile and need the distro name. Use only if A proves annoying.
- **Decision: A**, unless Deuce says otherwise.

## Applies to
- **All new features, from the egg-queue forward** — they log through `GpLog` from line one.
- **Existing features** get their key decision points routed through `GpLog` as we touch them (breeder, native-suppression, augments/proc, Daemon, collector). No big-bang refactor required.
- The product **analytics** (`events.jsonl`) stays as-is — it's the **Dashboard data layer** (gameplay events). `GpLog` is the **developer trace** (what the code is doing). Both can live in `~/gp-logs/`; keep the audiences separate.

## First build step (when greenlit)
Land `GpLog` as the foundation, **then** build the egg-queue on top of it — so the most data-heavy subsystem is fully traced from day one.
