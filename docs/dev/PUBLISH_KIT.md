# 🚀 PUBLISH KIT - Greener Pastures 1.0.0-beta.1 (first release, 2026-07-07)

Everything below in order. Nothing goes public until step 5, and you eyeball it all first.
All files referenced live in `Desktop/GreenerPasturesScreenshots/`.

---

## 0 · Account (5 min, once ever)
1. Go to **modrinth.com** → Sign up → **"Sign in with GitHub"** (use DonaldGallianoIII - ties your
   dev identity together and skips a password).
2. Top-right avatar → Settings → confirm the display name reads **DonaldGalliano**.
3. (Optional but smart) Settings → enable 2FA.

## 1 · Create the project (starts as a PRIVATE DRAFT)
Avatar → **"Create a project"**:
- **Name**: `Greener Pastures`
- **URL/slug**: `greener-pastures`
- **Summary**: `Your Cobblemon breeding operation, run like a lab - eggs as data, a real in-game console, and a six-cabinet Game Corner.`
- **Visibility**: leave as it defaults (draft - only you can see it)
Click create. You land on the draft project page.

## 2 · Fill the draft (this is the "approve the listing" part)
Work through the left-hand settings tabs:

**Description tab** → paste ALL of `MODRINTH_BODY.md`. Hit Preview and READ IT - this is the
page as players see it.

**General/Settings**:
- Environment: **Client: required · Server: required**
- Categories: `game-mechanics`, `management`, `economy` (up to 3 primary; add `minigame`
  as secondary if it offers more)
- License: **MIT**

**Links**:
- Source: `https://github.com/DonaldGallianoIII/GreenerPastures`
- Issues: `https://github.com/DonaldGallianoIII/GreenerPastures/issues`
- Donation: platform **Ko-fi** → `https://ko-fi.com/donaldgallianoiii`

**Icon**: upload `gp_icon_512.png`.

## 3 · Upload the version
Versions tab → **Create version**:
- File: `greenerpastures-1.0.0-beta.1.jar`
- Version number: `1.0.0-beta.1` · Channel: **Beta**
- Loaders: **Fabric** · Game versions: **1.21.1**
- **Dependencies** (search each by name, set the type):
  - Cobblemon → **Required**
  - Fabric API → **Required**
  - Cobbreeding → **Optional**
  - MCEF → **Optional** (the mod runs without it; the console shows an install prompt)
- Changelog: paste the `1.0.0-beta.1` section from `CHANGELOG.md`.

## 4 · Gallery
Gallery tab → upload the 11 shots in the order in `GALLERY_CAPTIONS.md` (titles + captions from
the same file). Mark **VisusalScriptingWooloo.png as Featured**.

## 5 · Final eyeball, then submit
Read the whole draft page top to bottom one last time. When you're happy:
**Submit for review**. A Modrinth moderator reviews it (typically a few hours to ~2 days for a
first project; they may message you in the project's moderation thread - answer there).
When it flips to **Approved**, your page is live at `modrinth.com/mod/greener-pastures`.

**Wait for Approved before posting the Discord thread** - the link 404s until then.

---

## 6 · The Cobblemon Discord thread (post AFTER approval)
Their showcase channel is a forum - "new post" = title + body. Attach 3-4 screenshots directly
(Discord shows them big): VisusalScriptingWooloo, GameCornerShop, BioBankFroakie, Rits.

**Thread title:**
```
Greener Pastures - A Data Science Mod [Fabric 1.21.1] - eggs as data, node-graph breeding, and a 6-cabinet Game Corner
```

**Thread body (paste as-is):**
```
Hey everyone! First mod release ever, so be gentle (but honest) with me.

Greener Pastures turns your Cobblemon breeding operation into a little data science lab.
One item - the Notebook - is a real in-game console (an actual web app rendered inside
Minecraft). Link your pastures to it and every egg becomes data: filtered by IVs, nature
and shininess through a visual node graph you wire yourself, banked losslessly in the
BioBank, or rendered into Data - the currency that powers rented buffs, breeding augments,
and more. No chests, no hoppers, no lag-farm entity soup.

Some highlights:
- Multi-pair parallel breeding on named lines, with Nature Lock, EV spreads, IV floors,
  hidden-ability splice and egg moves as installable Kernel augments
- 17 hidden rituals - assemble the right Pokemon in one pasture and something happens.
  Every recipe is secret, teased only by a riddle
- A six-cabinet Game Corner (full Voltorb Flip!) with its own closed Coin economy - the
  arcade only pays in prizes, never in power
- 500+ fan-made PMD Sprite Collab portraits, every one verified non-rip, 45 artists
  credited in the mod
- Everything is server-authoritative and unit-tested (369 tests) - the hidden tiles,
  decks and odds never leave the server

Needs: Cobblemon 1.7.3, Fabric API. Strongly recommended: Cobbreeding (the breeding half)
and MCEF (renders the console - without it you get a polite install prompt).

Modrinth: <MODRINTH LINK HERE>
Source (MIT): https://github.com/DonaldGallianoIII/GreenerPastures

It's a beta - I'd genuinely love bug reports and balance feedback. Everything I fix goes
out fast. Enjoy the pastures. 🌱
```

(Reply to your own thread later with the QUICK CLAW / slots screenshots as a "more looks" comment -
forum threads with author activity rank better in Discord's sort.)

---

## After it's live
- Pin the Modrinth link in the thread once posted.
- CurseForge + Cobbleverse ports: later, once beta feedback settles (same kit works).
- Watch the GitHub Issues tab - it's public now.
