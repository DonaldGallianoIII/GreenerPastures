/*
 * Mock server emulation — applies actions to local state in MOCK MODE (no game) so the console is fully
 * interactive: clicking a stepper / APPLY / REMOVE mutates state and the UI re-renders IMMEDIATELY (no window
 * close — that "only updates when closed" was the old owo rebuild bug, which does not exist in React).
 *
 * Mirrors what the real Java bridge does server-side. GPU cost is consumed on apply/upgrade and refunded on
 * remove/downgrade, so item flow is visible in the inventory bar (prototype behavior; final economy TBD).
 */
const clone = (x) => (x == null ? x : JSON.parse(JSON.stringify(x)))
const GPU = 'greenerpastures:gpu'

const gpuOf = (inv) => inv.items.find((i) => i.id === GPU)?.count ?? 0
function addItem(inv, id, n) {
  const it = inv.items.find((x) => x.id === id)
  if (it) it.count += n
  else inv.items.push({ id, count: n })
  inv.items = inv.items.filter((x) => x.count > 0)
}
function takeGpu(inv, n) {
  const it = inv.items.find((x) => x.id === GPU)
  if (!it || it.count < n) return false
  it.count -= n
  inv.items = inv.items.filter((x) => x.count > 0)
  return true
}

export function applyMock(state, channel, action, payload) {
  const out = {}
  const inv = clone(state.inventory) || { items: [] }
  const syncGpu = () => { out.inventory = inv; out.status = { ...state.status, gpu: gpuOf(inv) } }

  if (channel === 'compiler') {
    const c = clone(state.compiler)
    if (!c) return {}
    if (action === 'TOGGLE_DAEMON') {
      c.daemonOn = !c.daemonOn
      out.compiler = c
      out.status = { ...state.status, daemonOn: c.daemonOn }
    } else if (action === 'SET_BUFF') {
      const b = c.catalog.find((x) => x.id === payload.buff)
      if (!b) return {}
      const cur = c.installed[payload.buff] || 0
      const next = Math.max(0, Math.min(b.cap, payload.tier | 0))
      if (next === cur) return {}
      const perStep = b.gpuCost || 0
      if (next > cur) { if (!takeGpu(inv, perStep * (next - cur))) return {} }   // upgrade: consume GPU
      else { addItem(inv, GPU, perStep * (cur - next)) }                          // downgrade: refund GPU
      c.installed = { ...c.installed }
      if (next === 0) delete c.installed[payload.buff]
      else c.installed[payload.buff] = next
      c.drainPerSec = c.catalog.reduce((a, x) => a + (c.installed[x.id] || 0) * x.costPerTier, 0)
      out.compiler = c
      syncGpu()
    }
  } else if (channel === 'augmenter') {
    const a = clone(state.augmenter)
    if (!a) return {}
    const aug = a.catalog.find((x) => x.type === payload.type)
    if (!aug) return {}
    if (action === 'APPLY_AUGMENT') {
      if (aug.applied || a.slotsUsed + aug.slotCost > a.slotCap) return {}
      if (!takeGpu(inv, aug.gpuCost || 0)) return {}                              // consume GPU
      aug.applied = true
      a.slotsUsed += aug.slotCost
      out.augmenter = a
      syncGpu()
    } else if (action === 'REMOVE_AUGMENT') {
      if (!aug.applied) return {}
      aug.applied = false
      a.slotsUsed -= aug.slotCost
      addItem(inv, GPU, aug.gpuCost || 0)                                        // refund GPU → visible in inv bar
      out.augmenter = a
      syncGpu()
    }
  } else if (channel === 'storage') {
    const s = clone(state.storage)
    if (!s) return {}
    const id = payload.item
    const have = s.items[id] || 0
    if (have <= 0) return {}
    const n = action === 'PULL_ID' ? have : Math.min(64, have)
    s.items = { ...s.items }
    s.items[id] = have - n
    if (s.items[id] <= 0) delete s.items[id]
    addItem(inv, id, n)                                                          // loot → inventory
    out.storage = s
    out.inventory = inv
  }
  return out
}
