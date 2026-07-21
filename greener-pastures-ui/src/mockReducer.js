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
const MAXSTACK = 64

// Inventory is 36 slots: [0..8] hotbar, [9..35] main. GPU is CONSUMED on activate and never refunded.
const gpuTotal = (inv) => inv.slots.reduce((a, s) => a + (s && s.id === GPU ? s.count : 0), 0)
function takeGpu(inv, n) {
  if (gpuTotal(inv) < n) return false
  let need = n
  for (const s of inv.slots) { if (need <= 0) break; if (s && s.id === GPU) { const t = Math.min(s.count, need); s.count -= t; need -= t } }
  for (let i = 0; i < inv.slots.length; i++) if (inv.slots[i] && inv.slots[i].count <= 0) inv.slots[i] = null
  return true
}
function addItem(inv, id, n) {
  let left = n
  for (const s of inv.slots) { if (left <= 0) break; if (s && s.id === id && s.count < MAXSTACK) { const add = Math.min(MAXSTACK - s.count, left); s.count += add; left -= add } }
  const order = [...Array(27).keys()].map((i) => i + 9).concat([...Array(9).keys()])   // fill main (9-35) then hotbar (0-8)
  for (const i of order) { if (left <= 0) break; if (!inv.slots[i]) { const add = Math.min(MAXSTACK, left); inv.slots[i] = { id, count: add }; left -= add } }
}

export function applyMock(state, channel, action, payload) {
  const out = {}
  const inv = clone(state.inventory) || { slots: Array(36).fill(null) }
  const syncGpu = () => { out.inventory = inv; out.status = { ...state.status, gpu: gpuTotal(inv) } }

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
      if (next > cur && !takeGpu(inv, perStep * (next - cur))) return {}   // upgrade CONSUMES GPU; downgrade refunds nothing
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
      out.augmenter = a   // GPU was consumed on apply — removing frees the slot but does NOT refund GPU
    }
  } else if (channel === 'storage') {
    const s = clone(state.storage)
    if (!s) return {}
    if (action === 'DEPOSIT') {                                                  // shift-click inventory slot → storage
      const slot = inv.slots[payload.slot]
      if (!slot) return {}
      s.items = { ...s.items }
      s.items[slot.id] = (s.items[slot.id] || 0) + slot.count                    // merges with the same type if present
      inv.slots[payload.slot] = null
      out.storage = s
      out.inventory = inv
    } else {                                                                     // PULL_ONE (1) / PULL_STACK (64) / PULL_ID (all) → inventory
      const id = payload.item
      const have = s.items[id] || 0
      if (have <= 0) return {}
      const n = action === 'PULL_ID' ? have : action === 'PULL_STACK' ? Math.min(64, have) : 1
      s.items = { ...s.items }
      s.items[id] = have - n
      if (s.items[id] <= 0) delete s.items[id]
      addItem(inv, id, n)                                                        // merges into matching inv stacks first
      out.storage = s
      out.inventory = inv
    }
  } else if (channel === 'loom') {
    if (action === 'INSCRIBE_TETHER') {                                          // write [fn · tier] onto the selected tether
      const l = clone(state.loom)
      const t = (l?.tethers || []).find((x) => x.slot === payload.slot)
      if (!t) return {}
      const wipe = !payload.tier || payload.fn === 'wipe'
      if (t.count > 1) {                                                         // split one off the stack, like the server
        t.count -= 1
        l.tethers.push({ slot: 35, count: 1, fn: wipe ? '' : payload.fn, tier: wipe ? 0 : payload.tier })
      } else {
        t.fn = wipe ? '' : payload.fn
        t.tier = wipe ? 0 : payload.tier
      }
      out.loom = l
    } else if (action === 'RENAME_TETHER') {
      const l = clone(state.loom)
      const t = (l?.tethers || []).find((x) => x.slot === payload.slot)
      if (!t) return {}
      if (payload.name) t.name = payload.name; else delete t.name
      out.loom = l
    }
  } else if (channel === 'biobank') {
    if (action === 'WITHDRAW') {                                                 // pull an egg out → materializes in inventory (to hatch)
      const b = clone(state.biobank)
      if (!b || payload.index < 0 || payload.index >= b.entries.length) return {}
      b.entries.splice(payload.index, 1)
      b.total = Math.max(0, b.total - 1)
      addItem(inv, 'cobbreeding:pokemon_egg', 1)
      out.biobank = b
      out.inventory = inv
    } else if (action === 'COMPRESS' || action === 'COMPRESS_SERVER') {          // press/donate 100 worst non-shiny eggs
      const b = clone(state.biobank)
      const sp = payload.species
      const norm = (s) => (s || '').toLowerCase().replace(/[^a-z0-9]/g, '')
      const eligible = b.entries.map((e, i) => ({ e, i })).filter(({ e }) => e.species === sp && !e.shiny)
      if (eligible.length < 100) return {}
      eligible.sort((a, z) => a.e.ivs.reduce((x, y) => x + y, 0) - z.e.ivs.reduce((x, y) => x + y, 0))
      const eaten = new Set(eligible.slice(0, 100).map(({ i }) => i))
      b.entries = b.entries.filter((_, i) => !eaten.has(i))
      b.total = Math.max(0, b.total - 100)
      const field = action === 'COMPRESS' ? 'compression' : 'serverCompression'  // server pool: 1000 = +1% for everyone
      b[field] = { ...(b[field] || {}) }
      b[field][norm(sp)] = (b[field][norm(sp)] || 0) + 100
      out.biobank = b
    }
  }
  return out
}
