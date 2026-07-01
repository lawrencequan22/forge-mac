# Forge Desktop Redesign — Fidelity Sweep & Implementation Plan

Design source: `docs/redesign/Forge.dc.html` (imported from Claude Design project
`f41d6429-1937-4842-a57a-038061032678`, file `Forge.dc.html`).

Target app: **`Forge.app` = `forge-gui-desktop`** (classic Java Swing client). Adventure
(`forge-gui-mobile-dev`) is explicitly out of scope. Verified against the live desktop source
on 2026-06-30.

The design is a static **web/CSS mockup** built in the Claude Design framework (not Java). It
is a look-and-feel + information-architecture target, not runnable code. This document is the
"comprehensive sweep": what in the mockup is faithful to real Forge, what it **invented**, and
what real Forge functionality it **dropped** — followed by an implementation plan.

---

## 1. What the design gets RIGHT (faithful to real Forge)

| Area | Design | Real Forge | Verdict |
|---|---|---|---|
| Sanctioned group | Constructed, Booster Draft, Sealed Deck | Same three (Winston disabled upstream) | ✅ accurate |
| Play lobby seats | add/remove up to 8, Human/AI per seat, per-seat deck picker | `VLobby.MAX_PLAYERS = 8`, `PlayerPanel` LOCAL/AI/OPEN, per-player `FDeckChooser` | ✅ core-accurate |
| Games in match | Bo1 / Bo3 / Bo5 | `UI_MATCHES_PER_GAME` = 1/3/5, default 3 | ✅ accurate |
| Variants shown | Commander, Planechase, Archenemy, Vanguard, Momir Basic, Tiny Leaders | all real `GameType` variants | ✅ (subset, none invented) |
| Quest shops | Spell Shop, Bazaar | Quest has Spell Shop + Bazaar | ✅ accurate |
| Quest sub-items | Duels, Challenges, Quest Draft, Quest Decks | real quest submenus | ✅ accurate |
| Deck editor filters | colors (WUBRG), types, mana value | `CardColorFilter`, `CardTypeFilter`, `CardCMCFilter` | ✅ (subset) |
| Deck editor stats | mana curve, avg CMC | `VStatistics` curve + average | ✅ (subset) |
| Deck ops | Save, Import, Export | Save/Save As/Load/Import/Print/Copy | ✅ (subset) |
| Settings group | Preferences, Achievements, Avatars, Content Downloaders, Release Notes | exact same 5 items | ✅ accurate |
| Battlefield basics | phase strip, life, mana, hand, lands, creatures | all real concepts | ✅ (heavily simplified) |

The per-seat lobby and the Settings group are the two most faithful parts.

---

## 2. INVENTED functionality (in the mockup, NOT in real desktop Forge)

These would have to be **built from scratch**, or are mislabeled. Flagged because the task was
to ensure nothing was "made up."

1. **Light / "Radiant" parchment theme.** Forge themes via the **FSkin** system (`res/skins/`,
   ships `default` + `modern`, both dark). There is **no light theme and no dark/light toggle**.
   The `Radiant` light palette is entirely net-new — it would mean authoring a new skin.
2. **Card Animation Speed slider.** Real Forge has only an on/off toggle
   (`UI_ANIMATED_CARD_TAPUNTAP`, "Enable Card Animations"). No speed slider exists.
3. **"Rules tooltips" toggle.** No matching preference. Closest is *Hide Reminder Text*
   (inverse, and it targets card text, not hover tooltips).
4. **Planar Conquest in the menu.** Planar Conquest is a **mobile/libGDX-only** mode. It does
   **not** exist in the desktop home menu. Listing it under "More Modes" invents a desktop screen.
5. **Global header search** ("Search cards, decks, sets…"). Desktop Forge has no global search;
   search lives inside the deck editor only.
6. **Home "Command Center" dashboard** (hero banner + mode cards + Recent Decks + Release Notes
   preview). Desktop Forge's home is a **menu launcher over a background image** — there is no
   dashboard, and no "recent decks" widget (only `DeckPreferences` last-used tracking exists).
7. **"Puzzle" nav item routes to the Quest screen.** Broken mapping — real Puzzle mode has its
   own Solve + Create screens.
8. **Free "Starting Life" stepper in the lobby.** The sanctioned lobby does not expose a
   free-form starting-life control; life is variant-derived (20, or 40 for Commander, etc.).
9. **Rank/class flavor** ("Rank 12 · Spellslinger", achievement names) — cosmetic placeholder
   data, not a real progression system.

---

## 3. DROPPED functionality (real Forge features MISSING from the mockup)

The redesign must not silently delete these. Grouped by screen.

### Home menu / modes
- **Gauntlets group entirely** — real: Quick Gauntlet, Build A Gauntlet, Load Gauntlet, Gauntlet
  Contests, Quick EDH Gauntlet, Build An EDH Gauntlet (design collapses to one "Gauntlet" that
  routes to a generic lobby).
- **Online Multiplayer detail** — real: Lobby + Draft/Sealed Decks, with networking (host/join,
  server port, UPnP, AFK timeout, deck conformance). Design shows one entry → plain lobby.
- **Quest: Load Quest, Tournaments, Quest Preferences** — absent from design nav.
- **Puzzle: Create** (design has neither Solve nor Create screen).
- **Token Viewer.**

### Play lobby
- Per-seat **avatar + sleeve** selection, **team** assignment, **AI profile** picker, **ready**
  state, dev-mode; **variant-specific deck sections** (scheme deck for Archenemy, planar deck for
  Planechase, vanguard avatar). Design has none of these.
- Draft/Sealed are shown as "format tabs" in the lobby, but real Draft = interactive **pick-from-
  packs** flow (`CEditorDraftingProcess`) and Sealed = **open-6-packs + pool build**. The mockup
  models neither.

### Deck editor
- Missing filters: **rarity, set/edition, format legality, full-text search, power/toughness,
  advanced search**. (Rarity appears only as a sort tab.)
- Missing editor variants: **Oathbreaker, Tiny Leaders, Brawl, Draft, Sealed, Quest, Planechase,
  Archenemy** editors (design offers only Constructed/Commander/Limited toggle).
- Missing stats: color distribution, type distribution, colored-mana-symbol counts.
- Missing ops: **Save As, New, Load, Print to HTML, Copy to clipboard**, favorites, card table
  columns (design is grid-only).

### Battlefield (largest gap)
- **No graveyard, exile, library, stack, or command zone** are drawn — only lands + creatures +
  hand. A real match needs all zones.
- **No dock/toolbar**: Concede, End Turn, Alpha Strike, Targeting Arcs, Auto-Pass, Auto-Yields,
  Yield Settings, View Deck List, Offer Draw, Macro record/play.
- **No stack display, targeting arrows, or combat-damage assignment UI.**
- Phase strip shows **7** phases; real engine has **13** (combat split into begin / declare
  attackers / declare blockers / first strike / damage / end combat, plus cleanup).
- Player details omit poison / energy / experience / ticket / rad counters, full mana pool, and
  zone counts.

### Preferences
- Design shows ~4 toggles + a slider. Real Forge has **~100 preferences** across Gameplay,
  Graphics (~40), Sound/Music (volumes + sets), Random Deck Generation, Deck Editor, Advanced/Dev,
  Server/Network, plus Troubleshooting actions (reset, clear cache, open dirs, token previewer).
  These must remain reachable.
- **Avatars screen** omits **sleeves** (real Forge has both).

---

## 4. Net menu-tree reconciliation (proposed)

Keep the mockup's cleaner grouping, but restore dropped items so nothing is lost:

```
(top)      Home*            Deck Editor
Sanctioned Constructed      Booster Draft      Sealed Deck
Quest      Start Quest      Load Quest†        Duels
           Challenges       Quest Draft        Quest Decks
           Tournaments†     Quest Preferences†
Gauntlets† Quick            Build              Load
           Contests         Quick EDH          Build EDH
Online†    Lobby            Draft/Sealed Decks
Settings   Preferences      Achievements       Avatars (+ Sleeves†)
           Content Downloaders   Release Notes
```
`*` = new dashboard concept (net-new, acceptable).  `†` = present in real Forge, missing from
mockup — restore. Planar Conquest is **omitted** (desktop has no such screen).

---

## 5. Implementation approaches (decision pending)

### Option A — Reskin + restructure the Swing UI  ✅ recommended
Restyle the existing Swing views via the **FSkin** system to the Umbral (dark-gold) palette,
author a new **Radiant** light skin, and reshape the highest-visibility layouts (home launcher,
lobby, deck editor chrome) toward the mockup. All game logic, zones, dock, and preferences stay
intact.
- **Pros:** preserves 100% of functionality; ships as a normal `Forge.app`; incremental and
  low-risk; changes stay close to upstream (easy `update-from-upstream.sh` merges).
- **Cons:** Swing cannot pixel-match CSS (rounded gradients, blur, web fonts) exactly — it will be
  a faithful *interpretation*, not a screenshot-perfect clone.

### Option B — Embed the HTML UI (JCEF/webview) + bridge to the engine
Render the actual HTML and bridge every control to Forge's game/lobby APIs.
- **Pros:** pixel-perfect look.
- **Cons:** massive, high-risk rewrite of the entire UI layer; re-wires zones, stack, targeting,
  combat, preferences, deck editor; adds a heavyweight native dependency to the Mac bundle;
  fights upstream on every update. Not advisable for a functionality-preserving redesign.

**Recommendation: Option A.** It satisfies "keep all important Forge functionality" while
delivering the mockup's aesthetic and IA.

---

## 6. Phased plan (Option A)

**Phase 0 — Foundations**
- Author an **Umbral** FSkin (palette from the mockup: `--bg #0d0c0a`, `--gold #d9b45f`, etc.)
  and a **Radiant** light FSkin. Wire a dark/light switch into the Theme menu / Preferences
  (maps the mockup's toggle onto skin selection).
- Add Cinzel/Sora-equivalent bundled fonts (or nearest licensed substitutes) to FSkin.

**Phase 1 — Home / Command Center**
- Replace the plain launcher with a dashboard panel: hero (Continue Quest), game-mode cards,
  Recent Decks (from `DeckPreferences`), Release Notes preview — while keeping the full menu tree
  from §4 (restore Gauntlets/Online/Load Quest/etc.).

**Phase 2 — Play lobby restyle**
- Reskin `VLobby`/`PlayerPanel` to the mockup's seat cards; keep avatar/sleeve/team/AI-profile/
  variant-deck controls (progressive disclosure so the clean look survives).

**Phase 3 — Deck editor restyle**
- Apply the palette to `VDeckEditorUI`; adopt the mockup's 3-pane layout and curve widget while
  keeping all filters, columns, stats, and deck ops.

**Phase 4 — Match/battlefield restyle**
- Reskin `VMatchUI` (life orbs, phase strip, hand fan, card frames) **without** removing zones,
  dock, stack, targeting, or combat UI. This is the biggest and riskiest phase.

**Phase 5 — Settings**
- Restyle `VSubmenuPreferences` into the mockup's card sections; surface the 4 showcased toggles
  prominently but keep every existing preference reachable (e.g. an "Advanced" expander).

**Cross-cutting:** verify `./scripts/build-mac-app.sh` still produces a working `Forge.app` after
each phase; keep patches upstream-friendly.

---

## 6b. Implementation progress

Decisions taken: **Option A (reskin/restructure Swing)**, vertical-slice-first, **restore all
dropped menu items** (they were already registered in `VHomeUI`; only the web mockup dropped them).

- **Phase 0 — Skins: DONE & verified.** Added `res/skins/umbral/` (dark, mockup palette) and
  `res/skins/radiant/` (light) as `theme.txt` overrides on the fork's existing FSkin mechanism;
  default `UI_SKIN` → `Umbral`. Both appear in Layout → Theme and switch live. (Built on prior
  WIP: `theme.txt` support + flattened `FButton`/`FCheckBox`/`FRadioButton`/`ModernToggleIcons`.)
- **Phase 1 — Home / Command Center: DONE & verified.** New custom-painted
  `screens/home/CommandCenter.java` (grouped gold sidebar, header with search + dark/light toggle,
  hero banner, 8 game-mode cards). Wired into `VHomeUI` via `showDashboard()`/`showMenus()`; the
  anvil logo returns to the dashboard; cards/nav hand off to the real screens. Hero copy is the
  mockup's placeholder (not yet wired to real quest state).
- **Phase 2 — Play/Lobby: IN PROGRESS.** Decision: restyle toward mockup, keep Forge's deck
  chooser (do NOT rework deck selection). Done: `PlayerPanel` seat cards (rounded, panel2 fill,
  gold border on the human seat); mockup padding on `constructedFrame`. Variants already render as
  flat chips and Start is gold via the toolbox WIP.
- **Phases 3–5 — Deck Editor / Battlefield / Settings: TODO.**

## 7. Open decisions for the user
1. **Approach A vs B** (recommend A).
2. **Fidelity bar** for Swing: "faithful interpretation" (recommended) vs. "as pixel-close as
   Swing allows, even at perf/complexity cost".
3. **Scope/sequencing:** do all 5 phases, or start with Phase 0–1 (skin + home) as a vertical
   slice to validate the direction before the heavier editor/match phases?
4. **Restored menu items:** confirm we re-add Gauntlets / Online / Load Quest / Tournaments /
   Puzzle-Create (per §4) rather than shipping the mockup's reduced menu.
