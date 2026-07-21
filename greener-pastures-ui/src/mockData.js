/*
 * Contract sample data (NOTEBOOK_DATA_CONTRACT.md) — served by the bridge in MOCK MODE so the UI renders
 * standalone (no Minecraft). Values are true-to-life (pulled from a real world) so layouts look right.
 * When the real Java WS bridge answers, these are replaced per-channel by live `state` frames.
 */
export const MOCK = {
  status: { data: 97036, gpu: 64, daemonOn: false },

  storage: {
    capacity: 2147483647,
    items: {
      'cobblemon:silk_scarf': 12,
      'minecraft:iron_ingot': 340,
      'minecraft:copper_ingot': 288,
      'cobblemon:leftovers': 3,
      'minecraft:redstone': 96,
    },
  },

  compiler: {
    hasDaemon: true,
    daemonOn: true,
    drainPerSec: 3.5,
    installed: { fortune: 3, haste: 2 },
    catalog: [
      { id: 'fortune', label: 'Fortune', category: 'ENCHANT', cap: 3, costPerTier: 0.75, gpuCost: 2 },
      { id: 'looting', label: 'Looting', category: 'ENCHANT', cap: 3, costPerTier: 0.75, gpuCost: 2 },
      { id: 'haste', label: 'Haste', category: 'EFFECT', cap: 3, costPerTier: 0.5, gpuCost: 1 },
      { id: 'saturation', label: 'Saturation', category: 'EFFECT', cap: 3, costPerTier: 0.5, gpuCost: 1 },
      { id: 'magnet', label: 'Item Magnet', category: 'HOOK', cap: 3, costPerTier: 0.4, gpuCost: 1 },
      { id: 'auto_smelt', label: 'Auto-Smelt', category: 'HOOK', cap: 3, costPerTier: 0.4, gpuCost: 1 },
      { id: 'vein_mine', label: 'Vein-Miner', category: 'HOOK', cap: 3, costPerTier: 0.6, gpuCost: 3 },
    ],
  },

  pastures: {
    pastures: [
      {
        name: 'Eevee Shiny Farm', dim: 'minecraft:overworld', pos: 1234567890,
        tier: 'GREENER', eggCount: 4,
        pairs: ['Eevee ♀ × Eevee ♂ · Breeding', 'Ditto × Eevee ♀ · Ready', 'Eevee × — · Incomplete'],
      },
      {
        name: 'Ditto Line', dim: 'minecraft:overworld', pos: 987654321,
        tier: 'COPPER', eggCount: 0,
        pairs: ['Ditto × Ditto · Idle'],
      },
    ],
  },

  augmenter: {
    hasKernel: true, tier: 'GREENER', slotsUsed: 2, slotCap: 4,
    catalog: [
      { type: 'SHINY', label: 'Shiny Odds', slotCost: 1, gpuCost: 3, applied: true },
      { type: 'SPEED', label: 'Breed Speed', slotCost: 1, gpuCost: 2, applied: true },
      { type: 'IV_FLOOR', label: 'IV Floor', slotCost: 1, gpuCost: 2, applied: false },
      { type: 'EV', label: 'EV Spread', slotCost: 1, gpuCost: 2, applied: false },
      { type: 'ENRICHMENT', label: 'Enrichment', slotCost: 1, gpuCost: 2, applied: false },
    ],
  },

  biobank: {
    total: 103,
    entries: [
      { species: 'eevee', shiny: true, ivs: [31, 31, 31, 31, 31, 31], evs: [0, 0, 0, 0, 0, 0], nature: 'timid', gender: 'female', ability: 'adaptability' },
      { species: 'eevee', shiny: false, ivs: [31, 31, 31, 0, 31, 31], evs: [252, 0, 0, 0, 4, 252], nature: 'jolly', gender: 'male', ability: 'run_away' },
      { species: 'ditto', shiny: false, ivs: [31, 0, 31, 31, 0, 31], evs: [0, 0, 0, 0, 0, 0], nature: '', gender: '', ability: '' },
      // 100 pressable klink so the Compression press modal is exercisable in dev
      ...Array.from({ length: 100 }, (_, i) => (
        { species: 'klink', shiny: false, ivs: [i % 32, 10, 10, 10, 10, 10], evs: [0, 0, 0, 0, 0, 0], nature: 'hardy', gender: '', ability: 'plus' }
      )),
    ],
    // Compression ledgers: normalized species → eggs ever pressed (personal: 100 = +5%; server: 1000 = +1%)
    compression: { eevee: 200 },
    serverCompression: { eevee: 2300, klink: 800 },
  },

  // Loom: Soul Tethers in the main inventory + the inscription catalog (costs 100/400/900 by tier²)
  loom: {
    tethers: [
      { slot: 3, count: 4, fn: '', tier: 0 },
      { slot: 11, count: 1, fn: 'shiny', tier: 2 },
    ],
    catalog: ['shiny', 'speed', 'iv_floor', 'ev', 'enrichment', 'drop_rate', 'drop_yield', 'hatch'].map((id) => ({
      id,
      label: { shiny: 'Shiny', speed: 'Speed', iv_floor: 'IV Floor', ev: 'Fine-Tune (EV)', enrichment: 'Enrichment', drop_rate: 'Drop Rate', drop_yield: 'Drop Yield', hatch: 'Hatch Haste' }[id],
      cls: ['speed', 'enrichment', 'drop_rate', 'drop_yield', 'hatch'].includes(id) ? 'throughput' : 'quality',
      tiers: [1, 2, 3].map((t) => ({ tier: t, cost: t * t * 100, ampPct: 10 * t, burn: (['speed', 'enrichment', 'drop_rate', 'drop_yield', 'hatch'].includes(id) ? 3 : 8) * t })),
    })),
    refunds: [0, 50, 200, 450],
  },

  // Inbox: dismissible notes + the global server-press donation feed (24h rolling window, not dismissible)
  notifications: {
    notes: [
      { id: 1, icon: '⛏', text: 'North Farm caught up 42 sweeps while away → +117 items', t: Date.now() - 40 * 60 * 1000 },
    ],
    donations: [
      { id: 3, t: Date.now() - 5 * 60 * 1000, who: 'Deuce222XX', species: 'klink', eggs: 100, mult: 1.02, tierUp: true },
      { id: 2, t: Date.now() - 3 * 60 * 60 * 1000, who: 'Phishing4Feebas', species: 'klink', eggs: 100, mult: 1.01, tierUp: false },
      { id: 1, t: Date.now() - 22 * 60 * 60 * 1000, who: 'Tinderbeef', species: 'eevee', eggs: 100, mult: 1.02, tierUp: false },
    ],
  },

  // The player's inventory as 36 MC slots: [0..8] hotbar, [9..35] main. null = empty. Shown as a little
  // inventory window so slot/item flow is visible (storage grabs land here; GPU is consumed on apply).
  // (Real bridge: an `inventory` channel of slots — contract update to follow.)
  inventory: {
    slots: [
      { id: 'greenerpastures:gpu', count: 64 },
      { id: 'greenerpastures:daemon', count: 1 },
      { id: 'greenerpastures:breeding_upgrade_greener', count: 1 },
      { id: 'greenerpastures:augment_iv_floor', count: 2 },
      { id: 'greenerpastures:augment_ev', count: 1 },
      null, null, null, null,
      null, null, null, null, null, null, null, null, null,
      null, null, null, null, null, null, null, null, null,
      null, null, null, null, null, null, null, null, null,
    ],
  },
  topdeck: {
    active: true, minBet: 10, maxBet: 200, ladder: [2, 6, 20], stage: 1, wager: 50,
    over: false, won: false, payout: 0, flipsLeft: 5, flips: [],
    mercy: { available: false, started: false, used: false, won: false },
    cards: [
      { s: 'emolga', e: 'happy' }, { s: 'snom', e: 'normal' }, { s: 'pawmi', e: 'sad' },
      { s: 'fidough', e: 'joyous' }, { s: 'smoliv', e: 'worried' }, { s: 'applin', e: 'normal' },
      { s: 'yamper', e: 'happy' }, { s: 'morpeko', e: 'angry' }, { s: 'dedenne', e: 'surprised' },
      { s: 'rockruff', e: 'happy' }, { s: 'hatenna', e: 'normal' }, { s: 'sinistea', e: 'sigh' },
      { s: 'wooloo', e: 'happy' }, { s: 'axew', e: 'crying' }, { s: 'goomy', e: 'normal' },
      { s: 'mimikyu', e: 'pain' }, { s: 'zorua', e: 'happy' }, { s: 'milcery', e: 'normal' },
      { s: 'litten', e: 'angry' }, { s: 'popplio', e: 'joyous' }],
  },
  icons: {},
  slots: { symbols: ['voltorb', 'lechonk', 'applin', 'snom', 'yamper', 'dedenne', 'mimikyu', 'morpeko'],
    minBet: 5, maxBet: 100, paytable: [100, 15, 5, 1] },
  vibe: { deckSize: 12, sourTotal: 4, active: false },
  tag: { active: false },
  // arcade mock gains the fixed High Roller shelf via the live channel; nothing extra needed here

  about: {
    version: '0.0.0-mock',
    author: 'DonaldGalliano',
    license: 'MIT',
    pmdArtists: 'Emmuffin, G〜, JFain, NOLASMOR, baronessfaron',
  },
}
