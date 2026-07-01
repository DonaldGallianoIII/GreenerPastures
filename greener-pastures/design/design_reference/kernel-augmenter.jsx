import React, { useState } from "react";
import {
  X, ChevronRight, ChevronLeft, Plus, Check, Cpu,
  Sparkles, Timer, Dna, Lock, GitBranch, Globe, TrendingUp, Shapes, Flame, Scale,
} from "lucide-react";

/* ⌬ KERNEL AUGMENTER — kernel + (augment from list, costs 1 GPU) -> augmented kernel */

const K0 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADi0lEQVR42uWXXUgcVxTHf5P9cqc+hE0kIlafBCOBmA8SIfuihFAohCYvzZMPMSRCwBhJu0lMiEIrEVu1PpS+1JI0fSiFJtCSNsFoYj4wQkxtwEgtQoyKqyIiu7Ofk+nDOrOOMzvr7gp56IFh7rlz7r3nnP/533sH/u8iWHxTRKcLgJjNBoBDli0ni2JHsCn6TodAbCWYci17qslEp4u7z0b46/kTrS8gSeSLYtJ7W1Q3ZubNotYuKi7S2hfONxGPBsnIgejqp4Ak0Tdw1/Bdll2W2YgPD/LxR0fTQmBPZ9DSfGU1WsXcwCFATAGHwLH2En6/5k8EsBzh0+Mn0jqwZSOFcvybEhRZ4FhnKQDVbSKKLCSecEInpmgLey8lp6086E3tfDoHhh7eSeuYLhNZyJZcKHTbN4WQt6rEjFEGJCn3GgD49dyUTu/3hRI8DSd1wWY+9snA/dwzUNPutkx/Tbv7/UCgOtLvC5lCAOCtPpI7BHoozCNVZGN/VApvTg2sTfXAtdCGINoUCOKKaIw0bGSAWpTri3TTilB3euVZ0zLTrGzIATWifl+I6lZ3YvdbfasU7feFtOysz4JZfaStAXX7bPnyCwDyRZGK8rLE6fYHurcqU2/9hGPJmOZnJ7PPgMvt0ek9bdepr63bMFTzs5N4tns4fGhflixwmvs2PDLKgb27Uw7Lc7yjrfmyplf9ciM3GvZ29xB3OTXD3u4eeoHvbn5vsC35cAf++QVdX9/TF7kVYdzlxB6JWtpMvfVTX1tHfW0dbrcrI1alzUBnZzvTM3562q4DcLKxgdI11y0zOdnYoLUPH9pHq8V9wNKBqBRmesav61MX988vEIm8M4xZXg5lBEFKguZ7ipWmc2fxbE+yYZtnq8HOzIm5hTkADuzdTfe3Hdz/bZB4NChkVQNLi0uGvld/j9H7w88AfN3VQf3pUwSCKwSCK8wtzFFYUEhhQSFj4xMcqfkEJ/HMIYiElnROeL0HGRufAKCru4PHD/5k6OVr/v1nnK+6ujTbn279qJvnzOkz2n9FRhA43KJy6eLVxJm+ZvF0EpAkSouLuDf4kP27KgH47MJ5YiEpOwicYh7DI6Oarm7HZvrs9CwrS8t83uxj/65KqvbspGrPzuzOAkUWdAWoyvpMqHpFeRkV5WX09T3i9q2byZv1y9fZscDu/EBRDyVFFizv9qY/K2vuDkBKFrx3+Q9aPk839V7tJQAAAABJRU5ErkJggg==", K1 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADdElEQVR42uWXX0hUWRzHPzdn7ox3fIhZIxGrJ8EkyCxU0JeWiCCIpZd68iGjpKAt6c9uFv2BpLBVMZBeMrY/D9FDQRAVRmXtUsLatgsm2xJUKo7GIDL3OnPvTLeHce7M9d6513GEHvrBhfv7nd855/v7d87vwPdOgsOYLok+ALSCAgC8iYTjYioehALdLPQKaNNy1r082RaTRB/3/xzk71cvDFlEUSiSpDT6AtU0Z/TDZ+O/tKzU+D98qIW4KpMTAHV2KKIo9D25bxlPJHyO3ogP9LN1yzbXEHjcFE63npi1VrdX8Aqg6eAVUDtkAseDSQOmYuzYvtMVwJL5JIrWI6MnBNRLSnLxSwp6Qkh+0SSPphsby21hY25VbUN28G4AXj696wrM5IkF0JJ8SkhsCSD4U2isVkYUJf8cAPDuC9jyejTNZ3PziyePFicH5vKZMq1H/jYhSG3u3RewDQFAw8bN+YcgWyhMx2bC6gFViS6uB7QeGcGfTD6nkCxaCOK6ZLU0aq0AJ88sShI6JeTcspyPXs4AUhZmlp/aIaNHzWN61D5f7PLD9Tr2Fkr6qbPnDL5IkqisKHcE+vFTiKiWtmli7D2i5Od06wniqizk5AFfYdDEd7edp7mxad6hmhh7T7A4yKb69QssQ9Ee28DgG2qq12ad5vd+oa31uMHX3f49v3Ogt6ubuE80FHu7uukFLl+7YtFduWI5oYlJk6zvj7/yS8K4T8QTU11j39zYRHNjE4WFvpyqytUDHR0XGBkN0d12HoBdBw+wKqPdsqNdBw8Y/5vq13PGoR9wBKAqUUZGQyZZavPQxCSx2BfLnKmpmZxCkLUMi4JlesvP+wkWp6vhh+BSi54diPHJcQBqqtfS1dPOo3v9uZdhisKfwxbZv/8M0Xv1FgC/dbbTvGc3EXmaiDzN+OQ4JctKKFlWwtDwOzb/+BMi8dxDEJsJm0A0NNQyNPwOgM6udp4/fsDL12/5/79hLnZ2Gro3b1w3rbN3z17jXZHzSfjrLyeTd3rG5m4UURRWlZXysP8pG9ZUAXDk8CG0GWVhIRAlPwODbwx+7nGcyY+NjDEdnuJo6zE2rKmibt1q6tatdrwLPE4NRmYCpmiuJ1J8ZUU5lRXl9PU9486Na+nO+vXbhVWBRwzoqQeJnhAce3vbx0pG7wBkrYJvTl8B5/tVfAIYycYAAAAASUVORK5CYII=", K2 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADZUlEQVR42uVXT0gUURj/je7MrOOAsSouYnkSVAT/ooJeCpEgiOhSJw8aKgSmYn81siAxLFc8RJeMzA7RoSCQEkMzCxPSLFDJECoVV01UZkd3dsfpsO7szs7sjLtaHfpO7/ve9977fX/fe8D/ToTOnMRQNADAFRkJACBFUXczASYQkZJSSBJwbTiCnmUKthlD0eh9P4ZPH4ZlGcfzYBnGhz5SUKyZ/74ijxOTEuVxQ1093IIDIQEQdqY4nkf/QK9qXhRpXW+4R4dw7OhxwxCYjBSaG5t2rJW0FUgCcEkASUBY5xCdEOsxYM2JUydPGwKI2E2iuJwcJJGAwHvcKPAOSCLhka1zHrlLkg922H/Ja7MKioODNwIwMvjcEJjCE2FQxF5KiIphQcWwO2jUVnI8v/ccAACSZjV5act47fBA3/7kQCAfKPtrIfAmoj8YkmY1QwAAxYdL9w8ASbOqcMhtUyQ0wG7trwdcTg6EGb7E0wmJKkThAHBLjNqqdd/GhFk7Sfc9CUMpyz+ShF4LA8vPvwxJmlXxevlheB2TUYx07cZNmWcZBumpKbpAf/y0Y8vls2lpYRYUY0ZzYxPcgoMIyQN0lEXBd7a0orqsYteuXVqYhSXOgpKi3DA7IaWNbXRsAvk5mUGXmclttDRekfnCpw/31oq7OjrhpilZsaujE10A7nXfV+keOpgA+9KyQtb/7uPektBNUzA5BcPYV5dVoLqsAlFRdEhVYOiB9vZbmJu3o7OlFQBQXluDZL/nlhaV19bI45KiXFzXeQ+Y9Pv+Fubm7QqZ93D70jKczm3VmrW1zZBCELQMWUuSVH/uLCxxvmqItRxQ6WmBWFxeBADk52Si424b+l4MhV6GXlpdWVXJvnyeRNeDJwCAO7Y2VFeeAefYAOfYwOLyIqzxVljjrZicnkHpkROg4A49BM7NVQWI4uICTE7PAABsHW14+/olRsan8O3rNG7bbLLu455Hin2qKqvkf0XInfDypaueO93vcCPieB7JSYl4NTSIvIwsAMD5hjq4NvnwQkAxZoyOTch8YDv25xfmFrCxuoYLjReRl5GFwuw0FGan6d4FQUMgiYQiAb0U6Akvn56agvTUFPT3v8Gznm7fy3p8KrwqMFHRkvdDIomE7tte87MScFsGq4J/Tr8BJP1RIK8sGR4AAAAASUVORK5CYII=", K3 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADiklEQVR42uWXXWgUVxTHf2N2Z3Zng8pWMYS0PgViEIwfaMC8tIgIgpS+tE95MEUDglXxOxY/wGBJa0IeSl8a8aMPxQcLBWklRZuqaMBYW4hBRVCTkN3IEpedmf0abx92d3Ynszub3QR96IFl7zlz7r3/e/7n3A/4v4vk8k2osgJAqqYGAK9pug6WxINUI+xGr0QqqpWcy1NqMFVWuH53hL/v37ZsMV2nVlXz6GuStj4TL15b7fqGeqt9cP8B0kmNigAks59ius7gzeuO76apuEYjPTzE9m07ylLgKedwqutEdrWiuINXgpQAr4TxRmPximBmATMJPv/si7IAFs0lUeIJDWFKGLoOgKHrCFNCmBl+U4aeAQFEQxH0cMTq27KprTT4cgDu3frFPcyFvHqlqqpg0XxKKC0yv0ypCEfUYtmIzRuATwkU1QvtRonJbt+8sTA5UDhRPKFZthwI/5LAu6PAIxUBl6XApwTytABtH29dOAA+JeCgA8AjBxCm5PBN6vGFjUA8oSH5wLs44LAX0uSokGoBpIXqsBlv8gNLvuJJuuBJOFepJhErKsPcv8hSK+J2n0LdI+fBzM6POR3HXr8qTp45a+m1qkpzU6Mr0JevQsRT+TWFJ58jqz5OdZ0gndSkiiKg+IM2vb/7HJ3tHXMObXjyOcFlQbZsXl/laSgXxzY88oiN69aUpsv7lu6u45beevXi/I7jgb5+0opsOQ709TMA/HDpR4fvRx+uIBSettkG7zyYXxKmFRlPIlmW+872DjrbO/D7lcp21XIO589/w/hEiP7ucwDs3LeXlQXXrWKyc99eq71l83pOu9wHXAEk9TjjEyGbLTd5KDxNIvHW0WdmxqiIgpJlWBtsEAe+2kNwWb4aPggudfgVAzE1PQXAxnVr6Pu+hxu/DlVehjmJvI44bP/+M8rAhZ8B+K63h85dXxLTosS0KFPTU9Qtr6NueR2jY0/Z+smnyKQrpyBhRGwg2to2MTr2FIDevh7++uM37j18zLMnY3zb22v5/nTlsm2c3bt2W++KinfCY0e/zpzpBZOXk5ius7Khnt+HbrFhdQsAhw7uJ2Xo1VEgqz6GRx5Z+uztuFCfHJ8kGpnhcNcRNqxuoXXtKlrXrnI9C0pSIEzJloA5mR2JnN7c1EhzUyODg39y7cql/M364ePqqsAjB0TuQSJMyfVuX/SxMuu0LFUF713+A3I8Ww9s4f+qAAAAAElFTkSuQmCC", K4 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADdUlEQVR42uVXXUgUURT+xt2d3Z1kyU3JxPJJMBH8RQX3pRAJgohe6skHDRUCU7FfjSxIDEvFh+glI7OH6KEgkBJDMwsT0ixQyRAqNXdXVMbd2Z+ZcXpYd3bHmd1x16UeOjAw59xz7z33fN+5P8D/LkSYNoEi9QAAVqMBAOh4PuxgXmhBaASpUUeApZ0h59KGGowi9ej/MIHPH0dFm4NhEE9Rgeg1XkmfxR8r4n9Kaor431jfAM7rREQBeLeaHAyDwaF+WTvP68NmgxsfwfFjJ1Qh0Ko5tDQ1b61WUHbQEQAr+FK9RoM6kORbwLoHp0+dUQ0gbidE4Vw0BJ4A69jwccKxAYEnfJ/bp4MVxImZ33axb06RJXTwahkYG36hGpgkE1FI3G5KSJdggi7B5FNY+SodDLN7DgCA1mhS1AV3kK5R7js6NBAbDmzXg22ci/57EPiJGDy51mhShAAALEfKYheA1miSwSFum7w8A17GHdsMcC4ahAEB4oWAJGYQcAIlX6k7UAGEQZmkMSdhOEJuL8ud+EUcgH+FweXHrtEQ3NI2wa1cukr8UD2OdUZKuH7zlqjHUxQyM9LDBvrzlxVuNrAm29I8SMqAlqZmcF4nEVEG9EazRO9ubUNNeeWOobItzcOcaEZpSX6UOyGpHNv4xBQK87JDdjPoNtHadFXUi5892t1W3NPVDU5Pio49Xd3oAXC/94HM99DB/bDa7BLb4PtPuyMhpyeh9XhVsa8pr0RNeSWMRn1EVaWagY6O21hYtKK7tQ0AUFFXi7Sg65aSVNTViv+lJfm4Ee19wMu4sbBoldj8k1ttdng8m7I+6+uuiCAIWYbx5lSh4fw5mBMD1bDPvFfmpxTEsn0ZAFCYl42ue+0YeDkSeRn6ZXVlVWb7+mUaPQ+fAgDudrajpuosHE4aDieNZfsykpOSkZyUjOnZOZQdPQkSXOQQeFyrkiAsliJMz84BADq72vHuzSuMTc7g+7dZ3OnsFH2f9D2WjFNdVS2+KyLeCa9cvuY704MmVxMHwyAtNQWvR4ZRkJUDALjQWA/WxUQHAUkZMD4xJerbt+NgfWlhCfTqOi42XUJBVg6Kcw+jOPdw2LMgJAQCT0gI6JftmfDrmRnpyMxIx+DgWzzv6w3crCdnoqsCLblH8D9IBJ4Ie7dXfKwE3R0AhKyCfy5/AJpzWFIkBb8/AAAAAElFTkSuQmCC";
const GPU = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADGklEQVR42u2Wy08TURTGf8yr1uAzBlExUlOxQEUjIkIaQRMWbHXj34FrFy5MjHFhokZduNLEFU00Jhhc+AhxwUrL07ZQikiQKAgSR8o8XLSddpwpDDHqQs7ufjP3nO9895x7D2zYhm3YP7ay4oXsk01dFjCWlhHKfet2Jq4YAOiysPqPuoChqo74AOada5dMwe83/5S9TyfMG7fvmYLfbwJIxdEFvx8ASVIYSU1auLo4R39swFq3RdocSamLczS1tDrwwaFRRDFjw443HkOSFDKodgKSpACgafYN/bEB6msOs6ui0im7mKE21GCtaw5UAzA7N8/X7/OE60OuJKykixf5wJKkEO2OEu2OWt9KBT8SPmHDxlJJABQle7xNza0crQ2i64orAalUnXR2tK9ZdPfvPkTTMgT277KwyfQM8alxKrbvBGBhZhpdFmg7207fq17vBHqevwTg3PlzlMvuHXH95lUQIfXhc04Sg8GRrALhhhoCeyusf798mlm/Av6tO1EX51Ztu8N1h9BUk/jUOIOxeKH4YnHCDTWgfwaxtIoOAgvCJocCpds5uz2envi1nCyCp09HAHjR98bCF78sea+B1RQoRDJAdxLQ8WUDi4YrQUqiOQXyKqxmw6Mp0AXqQgGO1gYtPFwfAkG3bj5EA11XHN3wW10gSQqalqEuFMgSkYVs4FzGdaFAgWhizJsCcibjWYG372LW5ZU/86rKPZxpiVjqDI+mGE6M2QrU0xF0drR7UuHtuxjx5EdbUQKcibTaOsJbFxii4x7o7GinsfkUvT3PAAhWBzlYdNZ5Er09z+jqusjU9CxT07N5TRgcGXINrJk/XAjkikbL6Fb2qYk0kKZq324AkhNJV4fB6iBPoo8d+PhIkh8r32zYaCK1RhEKeuFlK9FixWqV8lHoUq0wI+R8GepymSuBFUXJOs85MJaWbU+1oaooW7YBENix2TW24pMd2EBisszzVSwpIpqmY6iqtSk3wQDw4PIF82nPa5ojJz1NSrce9XsbqYRyn3n1+hVTKPeZf2smFErNbP/K8tmbbNj/Yj8B9eSMj1qtba8AAAAASUVORK5CYII=";

const C = {
  scrim: "rgba(3,6,9,0.72)", bg: "#070c11", panel: "#0c141b", panel2: "#0f1a22",
  inset: "#070e13", line: "#1b2a36", lineHi: "#2c4150", green: "#43d869",
  greenDim: "#1d6b38", amber: "#f5b234", text: "#cfe3da", muted: "#688089", slot: "#060b0f",
};

const KERNELS = [
  { name: "Kernel",         tone: "#9aa6ad", sprite: K0 },
  { name: "Copper Kernel",  tone: "#e08a5b", sprite: K1 },
  { name: "Iron Kernel",    tone: "#cdd2d6", sprite: K2 },
  { name: "Golden Kernel",  tone: "#f2c84b", sprite: K3 },
  { name: "Diamond Kernel", tone: "#5be0e0", sprite: K4 },
];
const capacityFor = (i) => i + 1; // base 1 … diamond 5

const AUGMENTS = [
  { id: "shiny",     name: "Shiny Catalyst", icon: Sparkles,  slots: 2, desc: "Raises the shiny chance of eggs produced." },
  { id: "tempo",     name: "Egg Tempo",      icon: Timer,     slots: 1, desc: "Eggs are produced faster." },
  { id: "iv_forge",  name: "IV Forge",       icon: Dna,       slots: 2, desc: "Better IVs are passed to offspring." },
  { id: "nature",    name: "Nature Lock",    icon: Lock,      slots: 1, desc: "Offspring inherit a fixed nature." },
  { id: "ability",   name: "Ability Splice", icon: GitBranch, slots: 1, desc: "Passes the parent's hidden ability." },
  { id: "masuda",    name: "Masuda Lens",    icon: Globe,     slots: 2, desc: "Foreign-parent shiny bonus." },
  { id: "ev_primer", name: "EV Primer",      icon: TrendingUp,slots: 1, desc: "Seeds offspring with EVs." },
  { id: "form",      name: "Form Inherit",   icon: Shapes,    slots: 1, desc: "Passes the parent's regional form." },
  { id: "warmer",    name: "Hatch Warmer",   icon: Flame,     slots: 1, desc: "Eggs hatch in fewer cycles." },
  { id: "gender",    name: "Gender Bias",    icon: Scale,     slots: 1, desc: "Bias the offspring's gender." },
];
const AMAP = Object.fromEntries(AUGMENTS.map((a) => [a.id, a]));
const GPU_PER_AUGMENT = 1;

export default function KernelAugmenter() {
  const [tierIdx, setTierIdx] = useState(2);
  const [applied, setApplied] = useState(["tempo"]);
  const [selected, setSelected] = useState("shiny");
  const [gpus, setGpus] = useState(3);

  const kernel = KERNELS[tierIdx];
  const cap = capacityFor(tierIdx);
  const used = applied.reduce((s, id) => s + AMAP[id].slots, 0);
  const sel = selected ? AMAP[selected] : null;

  const already = sel && applied.includes(selected);
  const fits = sel && used + sel.slots <= cap;
  const hasGpu = gpus >= GPU_PER_AUGMENT;
  const canApply = sel && !already && fits && hasGpu;

  const cycleTier = (d) => setTierIdx((t) => (t + d + KERNELS.length) % KERNELS.length);
  const apply = () => { if (canApply) { setApplied((a) => [...a, selected]); setGpus((g) => g - GPU_PER_AUGMENT); } };
  const removeApplied = (id) => setApplied((a) => a.filter((x) => x !== id));

  const Header = ({ children, sub }) => (
    <div className="px-3 py-2 flex items-baseline justify-between" style={{ borderBottom: `1px solid ${C.line}` }}>
      <span className="font-mono text-xs font-bold tracking-[0.2em]" style={{ color: C.green }}>{children}</span>
      {sub && <span className="font-mono" style={{ fontSize: 10, color: C.muted }}>{sub}</span>}
    </div>
  );
  const Op = ({ icon: Ico }) => (
    <div className="flex items-center justify-center" style={{ width: 24 }}><Ico size={18} style={{ color: C.lineHi }} /></div>
  );
  const CapPips = ({ used, cap }) => (
    <div className="flex items-center gap-1">
      {Array.from({ length: cap }).map((_, i) => (
        <span key={i} style={{ width: 9, height: 9, borderRadius: 2,
          background: i < used ? C.green : "transparent",
          border: `1px solid ${i < used ? C.green : C.line}`,
          boxShadow: i < used ? `0 0 6px ${C.green}88` : "none" }} />
      ))}
    </div>
  );

  const applyLabel = already ? "ALREADY APPLIED" : !fits ? "NOT ENOUGH SLOTS" : !hasGpu ? "NEED A GPU" : null;

  return (
    <div style={{ background: C.scrim, minHeight: 600 }} className="w-full flex items-center justify-center p-4">
      <div style={{
        width: "min(1080px, 95vw)", aspectRatio: "16 / 10",
        background: `linear-gradient(180deg, ${C.panel} 0%, ${C.bg} 100%)`,
        border: `1px solid ${C.lineHi}`, borderRadius: 8,
        boxShadow: `0 0 0 1px #000, 0 18px 60px rgba(0,0,0,0.6), inset 0 0 80px rgba(0,0,0,0.5)`,
        position: "relative", overflow: "hidden",
        fontFamily: "ui-monospace, monospace", color: C.text, display: "flex", flexDirection: "column",
      }}>
        <div style={{ position: "absolute", inset: 0, pointerEvents: "none", opacity: 0.05,
          backgroundImage: `radial-gradient(${C.green} 1px, transparent 1px)`, backgroundSize: "22px 22px" }} />

        <div className="flex items-center justify-between px-4 py-2.5" style={{ borderBottom: `1px solid ${C.lineHi}`, background: C.panel2 }}>
          <div className="flex items-center gap-2">
            <span style={{ color: C.green, fontSize: 18 }}>⌬</span>
            <span className="font-mono text-sm font-bold tracking-[0.25em]" style={{ color: C.text }}>KERNEL AUGMENTER</span>
            <span className="font-mono text-xs ml-1" style={{ color: C.muted }}>· pasture</span>
          </div>
          <button title="Close" style={{ color: C.muted, border: `1px solid ${C.line}`, borderRadius: 4 }}
            className="w-6 h-6 flex items-center justify-center hover:opacity-70"><X size={14} /></button>
        </div>

        <div className="flex flex-1 min-h-0 p-3">
          {/* ===== LEFT — KERNEL ===== */}
          <div style={{ width: "27%", background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 6 }} className="flex flex-col min-h-0">
            <Header sub="input">KERNEL</Header>
            <div className="flex-1 flex flex-col items-center justify-center p-4 gap-3">
              <div style={{ width: 102, height: 102, background: C.slot, border: `2px solid ${kernel.tone}`, borderRadius: 6,
                boxShadow: `inset 0 0 12px #000, 0 0 14px ${kernel.tone}44`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <img src={kernel.sprite} alt={kernel.name} draggable={false} style={{ width: 78, height: 78, imageRendering: "pixelated" }} />
              </div>
              <div className="flex items-center gap-2">
                <button onClick={() => cycleTier(-1)} style={{ border: `1px solid ${C.line}`, color: C.text, background: C.inset }} className="w-6 h-6 rounded flex items-center justify-center"><ChevronLeft size={13} /></button>
                <span className="font-mono text-xs font-bold" style={{ color: kernel.tone, minWidth: 110, textAlign: "center" }}>{kernel.name}</span>
                <button onClick={() => cycleTier(1)} style={{ border: `1px solid ${C.line}`, color: C.text, background: C.inset }} className="w-6 h-6 rounded flex items-center justify-center"><ChevronRight size={13} /></button>
              </div>
              <div className="w-full flex flex-col gap-2 font-mono text-xs mt-1">
                <div className="flex items-center justify-between">
                  <span style={{ color: C.muted }}>Slots used</span>
                  <span style={{ color: used >= cap ? C.amber : C.green }}>{used} / {cap}</span>
                </div>
                <div className="flex justify-end"><CapPips used={used} cap={cap} /></div>
              </div>
            </div>
          </div>

          <Op icon={Plus} />

          {/* ===== MIDDLE — AUGMENT (list + GPU recipe) ===== */}
          <div style={{ width: "41%", background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 6 }} className="flex flex-col min-h-0">
            <Header sub="select enchant">AUGMENT</Header>
            <div className="flex-1 overflow-y-auto min-h-0">
              {AUGMENTS.map((a) => {
                const Ico = a.icon; const isSel = selected === a.id; const on = applied.includes(a.id);
                return (
                  <button key={a.id} onClick={() => setSelected(a.id)}
                    style={{ background: isSel ? C.inset : "transparent", borderLeft: `2px solid ${isSel ? C.green : "transparent"}`,
                      borderBottom: `1px solid ${C.line}`, opacity: on ? 0.5 : 1 }}
                    className="w-full flex items-center gap-2.5 px-3 py-1.5 text-left">
                    <Ico size={15} style={{ color: isSel ? C.green : C.muted, flexShrink: 0 }} />
                    <span className="font-mono text-xs flex-1 truncate" style={{ color: C.text }}>{a.name}</span>
                    {on && <Check size={13} style={{ color: C.green }} />}
                    <span className="font-mono" style={{ fontSize: 10, color: C.muted }}>{a.slots} slot{a.slots > 1 ? "s" : ""}</span>
                  </button>
                );
              })}
            </div>
            {sel && (
              <div style={{ borderTop: `1px solid ${C.lineHi}`, background: C.panel }} className="p-3">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="font-mono text-xs font-bold" style={{ color: C.text }}>{sel.name}</span>
                  <span className="font-mono" style={{ fontSize: 10, color: C.muted }}>{sel.slots} slot{sel.slots > 1 ? "s" : ""}</span>
                </div>
                <div className="font-mono text-xs mb-2.5" style={{ color: C.muted }}>"{sel.desc}"</div>
                {/* recipe: the GPU is the required ingredient */}
                <div className="flex items-center justify-between mb-2.5 px-2 py-1.5 rounded" style={{ background: C.inset, border: `1px solid ${C.line}` }}>
                  <span className="font-mono" style={{ fontSize: 10, color: C.muted }}>requires</span>
                  <div className="flex items-center gap-1.5">
                    <img src={GPU} alt="gpu" style={{ width: 20, height: 20, imageRendering: "pixelated" }} />
                    <span className="font-mono text-xs" style={{ color: C.text }}>GPU ×{GPU_PER_AUGMENT}</span>
                    <span className="font-mono ml-1" style={{ fontSize: 10, color: hasGpu ? C.green : C.amber }}>(have {gpus})</span>
                    <button onClick={() => setGpus((g) => g + 1)} title="restock (mock)"
                      style={{ border: `1px solid ${C.line}`, color: C.muted, borderRadius: 3 }} className="w-4 h-4 flex items-center justify-center ml-1 text-xs">+</button>
                  </div>
                </div>
                <button onClick={apply} disabled={!canApply}
                  style={{ background: canApply ? C.greenDim : C.inset, border: `1px solid ${canApply ? C.green : C.line}`,
                    color: canApply ? "#eafff0" : C.muted, cursor: canApply ? "pointer" : "not-allowed" }}
                  className="w-full py-1.5 rounded font-mono text-xs font-bold tracking-wider flex items-center justify-center gap-1">
                  {applyLabel || <>APPLY <ChevronRight size={13} /></>}
                </button>
              </div>
            )}
          </div>

          <Op icon={ChevronRight} />

          {/* ===== RIGHT — AUGMENTED ===== */}
          <div style={{ width: "32%", background: C.panel2, border: `1px solid ${C.line}`, borderRadius: 6 }} className="flex flex-col min-h-0">
            <Header sub="output">AUGMENTED</Header>
            <div className="flex flex-col items-center pt-3 pb-2" style={{ borderBottom: `1px solid ${C.line}` }}>
              <div style={{ width: 72, height: 72, background: C.slot, borderRadius: 6,
                border: `2px solid ${applied.length ? C.green : C.line}`,
                boxShadow: applied.length ? `0 0 16px ${C.green}66, inset 0 0 10px ${kernel.tone}22` : "inset 0 0 8px #000",
                display: "flex", alignItems: "center", justifyContent: "center", position: "relative" }}>
                <img src={kernel.sprite} alt="augmented kernel" draggable={false} style={{ width: 54, height: 54, imageRendering: "pixelated" }} />
                {applied.length > 0 && <span style={{ position: "absolute", bottom: 2, right: 4, color: C.green }} className="font-mono text-xs font-bold">×{applied.length}</span>}
              </div>
              <span className="font-mono mt-2" style={{ fontSize: 10, color: applied.length ? C.green : C.muted }}>
                {applied.length ? `augmented ×${applied.length}` : "no augments"}
              </span>
            </div>
            <div className="flex-1 overflow-y-auto min-h-0">
              {applied.length === 0 && <div className="p-4 font-mono text-xs" style={{ color: C.muted }}>Pick an augment, feed it a GPU, hit Apply.</div>}
              {applied.map((id) => {
                const a = AMAP[id]; const Ico = a.icon;
                return (
                  <div key={id} style={{ borderBottom: `1px solid ${C.line}` }} className="flex items-center gap-2 px-3 py-1.5">
                    <Ico size={14} style={{ color: C.green, flexShrink: 0 }} />
                    <span className="font-mono text-xs flex-1 truncate" style={{ color: C.text }}>{a.name}</span>
                    <span className="font-mono" style={{ fontSize: 10, color: C.muted }}>{a.slots}</span>
                    <button onClick={() => removeApplied(id)} title="Remove" style={{ color: C.muted }} className="hover:text-red-400"><X size={13} /></button>
                  </div>
                );
              })}
            </div>
            <div style={{ borderTop: `1px solid ${C.lineHi}`, background: C.panel }} className="px-3 py-2 flex items-center justify-between">
              <span className="font-mono text-xs" style={{ color: C.muted }}>Capacity</span>
              <span className="font-mono text-xs font-bold" style={{ color: used >= cap ? C.amber : C.green }}>{used} / {cap} slots</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
