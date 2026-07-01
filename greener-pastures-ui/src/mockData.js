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
      { id: 'fortune', label: 'Fortune', category: 'ENCHANT', cap: 3, costPerTier: 0.75 },
      { id: 'looting', label: 'Looting', category: 'ENCHANT', cap: 3, costPerTier: 0.75 },
      { id: 'haste', label: 'Haste', category: 'EFFECT', cap: 3, costPerTier: 0.5 },
      { id: 'saturation', label: 'Saturation', category: 'EFFECT', cap: 3, costPerTier: 0.5 },
      { id: 'magnet', label: 'Item Magnet', category: 'HOOK', cap: 3, costPerTier: 0.4 },
      { id: 'auto_smelt', label: 'Auto-Smelt', category: 'HOOK', cap: 3, costPerTier: 0.4 },
      { id: 'vein_mine', label: 'Vein-Miner', category: 'HOOK', cap: 3, costPerTier: 0.6 },
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
      { type: 'SHINY', label: 'Shiny Odds', slotCost: 1, applied: true },
      { type: 'SPEED', label: 'Breed Speed', slotCost: 1, applied: true },
      { type: 'IV_FLOOR', label: 'IV Floor', slotCost: 1, applied: false },
      { type: 'EV', label: 'EV Spread', slotCost: 1, applied: false },
      { type: 'ENRICHMENT', label: 'Enrichment', slotCost: 1, applied: false },
    ],
  },

  biobank: {
    total: 3,
    entries: [
      { species: 'eevee', shiny: true, ivs: [31, 31, 31, 31, 31, 31], evs: [0, 0, 0, 0, 0, 0], nature: 'timid', gender: 'female', ability: 'adaptability' },
      { species: 'eevee', shiny: false, ivs: [31, 31, 31, 0, 31, 31], evs: [252, 0, 0, 0, 4, 252], nature: 'jolly', gender: 'male', ability: 'run_away' },
      { species: 'ditto', shiny: false, ivs: [31, 0, 31, 31, 0, 31], evs: [0, 0, 0, 0, 0, 0], nature: '', gender: '', ability: '' },
    ],
  },
}
