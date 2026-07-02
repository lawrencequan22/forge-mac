# Forge Grounding Sweep — replace placeholder design content with real Forge functionality

The embedded design currently renders **placeholder flavor** (fake decks, fake quest, fake
cards). This sweep maps **every design element** to its **real Forge source of truth** and assigns
an action:

- **INJECT** — feed real data into the design via the JS↔Java bridge (Java reads Forge, pushes to
  the WebView).
- **HANDOFF** — the design element is a launcher; clicking it opens Forge's real native screen
  (which already works and is complex to reimplement).
- **REMOVE** — not real in the desktop app; drop it so the UI doesn't imply features that don't
  exist.
- **KEEP** — already correct/real.

Guiding principle (also the most update-proof as a mod): **the design is the SHELL** — home,
browsing, game setup, settings — rendered with **real data**; **deep functional flows (real deck
editing, actual gameplay, advanced lobby) HAND OFF to native Forge**, which changes most each
release and shouldn't be reimplemented.

All Java sources verified against the codebase.

---

## Bridge data model (what Java must expose to the WebView)

Read (INJECT): `decks(category)`, `lastUsedDeck(category)`, `quest()`, `achievements(gameType)`,
`pref(key)` / `setPref(key,val)`, `avatars()/sleeves()`, `releaseNotes()`, `cardCatalog(filter)`.
Act (HANDOFF/APPLY): `startGame(config)`, `openDeckEditor()`, `openSubmenu(EDocID)`,
`selectAvatar(idx)`, `runDownloader(id)`.

Core entry point for all of it: `forge.model.FModel` (`getDecks()`, `getQuest()`,
`getAchievements()`, `getPreferences()`, `getMagicDb()`, `getAllCards()`).

---

## 1. Home / Command Center

| Design element (now) | Real Forge source | Action |
|---|---|---|
| Hero "The Shadowmoor Gambit" / "Resume Quest" | `FModel.getQuest().getQuest()` — if non-null: `getName()`, `getAssets().getCredits()/getLife()`; if null → "Start Quest" | INJECT (show real quest or a Start-Quest CTA) |
| "Spell Shop" button | `QuestController.getBazaar()` (only if quest exists) | INJECT/HANDOFF (else hide) |
| Recent Decks (Azorius Control, Gruul Aggro, Prossh) | `DeckPreferences.getCurrentDeck()` + `FModel.getDecks().getConstructed()` (name, `getMain().countAll()`, color identity via `PaperCard.getRules().getColorIdentity()`) | INJECT (real deck names/counts/colors) |
| Release Notes teaser (fake Bloomburrow blurbs) | `ForgeConstants.CHANGES_FILE_NO_RELEASE` / `CHANGES_FILE` via `CSubmenuReleaseNotes` | INJECT (first N real changelog lines) |
| Game-mode cards | real desktop modes | Constructed/Draft/Sealed/Quest/Gauntlet/Puzzle = KEEP → HANDOFF; **Commander** = it's a variant, route to Constructed+Commander; **Planar Conquest** = REMOVE (mobile-only, not desktop) |

## 2. Play / Lobby

| Design element (now) | Real Forge source | Action |
|---|---|---|
| Format tabs: Constructed / Commander / Draft / Sealed | Constructed/Draft/Sealed are real submenus; **Commander is a variant** (`GameType.Commander`), not a format | KEEP tabs but map Commander→variant; Draft/Sealed → HANDOFF to their real pick/pool flows |
| Player seat: name, Human/AI | `GameLobby.getSlot(i)` (`setName`, type LOCAL/AI); default names real | KEEP (wire to slot) |
| Per-seat deck picker (Azorius Control + fake pips) | `FModel.getDecks().getConstructed()` (+ Random/Generated/precon); assign via `LobbySlot.setDeck(Deck)` | INJECT real deck list; wire selection |
| Variants chips (Commander, Planechase, Archenemy, Vanguard, Momir Basic, Tiny Leaders) | real `GameType` variants | KEEP; optionally add MoJhoSto/Oathbreaker/Brawl/ArchenemyRumble for completeness |
| Starting Life stepper (free 20±) | Forge derives life from variant (20, 40 Commander…); not a free lobby control | REMOVE the free stepper (show derived life read-only) or keep only where Forge allows |
| Games Bo1/Bo3/Bo5 | `FPref.UI_MATCHES_PER_GAME` (1/3/5) | KEEP (wire) |
| Avatar/sleeve/team/AI-profile (not in design) | `LobbySlot.setAvatarIndex/setSleeveIndex/team`, `FPref.UI_ENABLE_AI_PICKER` | ADD (progressive disclosure) or HANDOFF to native for advanced |
| Variant decks (scheme/planar/vanguard — not in design) | native lobby selectors | HANDOFF (native) when those variants are on |
| **START GAME** | `GameLobby.startGame().run()` after slots set | WIRE → launches the **native match** (real gameplay) |

**Recommended split:** the design lobby handles the common case (pick real decks, Human/AI, games)
and calls `bridge.startGame(config)`; when advanced variants/teams/sleeves are needed, HANDOFF to
the native lobby. Confirm below.

## 3. Quest

| Design element (now) | Real Forge source | Action |
|---|---|---|
| Header stats (58 wins / 19 losses / 4,820 credits / 20 life) | `QuestData.getAchievements().getTotalWins()/getTotalLosses()`, `getAssets().getCredits()/getLife()` | INJECT (or "no quest → Start Quest") |
| Rank/class ("Rank 12 · Spellslinger") | no such system | REMOVE |
| Duels (Goblin Warren, …) | `QuestController.getDuelManager()` events (`getTitle()`, `getEventReward()`) | INJECT (real duels) |
| Challenges (Trapped in the Tower, …) | `QuestController.getChallenges()` | INJECT (real) |
| Shops (Spell Shop, Bazaar) | `QuestController.getBazaar()` | KEEP → HANDOFF to native shop |

Quest actions (start duel, buy) are complex → HANDOFF to native quest screens; the design shows
real state + launches.

## 4. Deck Editor  (heaviest screen)

| Design element (now) | Real Forge source | Action |
|---|---|---|
| Editor tabs (Constructed/Commander/Limited) | `FScreen.DECK_EDITOR_*` | — |
| Card grid (Supreme Verdict, Teferi… 12 static cards) | `FModel.getAllCards()` / `getMagicDb()` (30k+ real cards; `PaperCard` + `CardRules`) | too large to reimplement faithfully |
| Filters (color/type/CMC) | real `ItemManager` filters | native has full set (rarity/set/format/text/PT) |
| Deck list + curve (static) | real editable `Deck` + `VStatistics` | native is fully functional |
| Save / Import / Export | native `VCurrentDeck` toolbar | — |

**Recommendation: HANDOFF.** The design's "Deck Editor" opens Forge's **native deck editor**
(already fully functional, and Umbral-themed). Optionally, the design can show a real **deck
browser** (list decks from `FModel.getDecks()`) that opens the native editor on a chosen deck.
Reimplementing a real editor in HTML (catalog, filters, drag, save) is a large project with no
functional gain. Confirm below.

## 5. Settings

| Design element (now) | Real Forge source | Action |
|---|---|---|
| Theme toggle (Umbral/Radiant) | `FPref.UI_SKIN` (+ `FSkin.changeSkin`) | KEEP (already real) |
| "Full-art card frames" | `FPref.UI_CARD_ART_FORMAT` ("Full"/…) | INJECT/WIRE |
| "Auto-yield priority" | real yield prefs (`YIELD_*`) | WIRE (map to real) |
| "Stack animations" | `FPref.UI_ANIMATED_CARD_TAPUNTAP` | WIRE |
| "Rules tooltips" | closest: `FPref.UI_HIDE_REMINDER_TEXT` (inverse) | WIRE or REMOVE (no exact match) |
| Card Animation **Speed** slider | on/off only in Forge; no speed | REMOVE (or make it the on/off toggle) |
| Achievements (First Blood…) | `FModel.getAchievements(GameType)` (`getTitle/getDescription/isEarned`) | INJECT (real achievements) |
| Avatars grid (fake gradients) | `FSkin.getAvatars()`; select → `FPref.UI_AVATARS` | INJECT + WIRE |
| Content Downloaders | `CSubmenuDownloaders` (LQ/HQ pics, set pics, quest, achievements, prices, skins) | INJECT list → HANDOFF to `GuiDownloader` |
| Release Notes (fake versions) | `CHANGES_FILE_NO_RELEASE` / `CHANGES_FILE` | INJECT (real changelog) |
| (missing) ~100 other prefs | `ForgePreferences` | Surface a native-settings HANDOFF for the long tail |

## 6. Battlefield

The design's board (Sarkhan, fake cards, 7 phases) is a **mockup**. Real play is the **native
match engine** (13 phases, all zones, dock, stack, targeting, combat). **REMOVE** the design
battlefield from the shell; **START GAME launches the real native match.** Never reimplement
gameplay in HTML.

---

## Net plan

1. Build a small **`ForgeBridge`** (Java) exposed to the WebView (`window.forge`) with the read
   and act methods above.
2. Change the design's data source: replace the hardcoded arrays in the component
   (`recentDecks`, `deckOptions`, `questStats`, `achievements`, `downloads`, `releaseNotes`, card
   data) with values the bridge injects at load (`window.forge.getX()`), so the UI shows **your
   real collection/quest/prefs**.
3. Wire the design's actions to the bridge: mode cards / START GAME / Deck Editor / Downloaders /
   Preferences → real Forge (launch native match, open native editor, run downloader, set prefs).
4. **REMOVE** what isn't real (Planar Conquest, rank/class, life stepper, anim-speed slider,
   design battlefield).
5. Keep gameplay + deep editing **native** (handoff).

## Status — DONE (verified running)

- **Home**: real quest hero (`getQuest`), real Recent Decks (`getRecentDecks`) / Release Notes
  (`getReleaseNotes`) with honest empty states (no fabricated data in-app). ✅
- **Deck Editor**: opens the real native editor (94k cards). ✅
- **Constructed lobby**: real decks in the picker (`getDecks`), and **START GAME launches a real
  native match** (`launchConstructed` → `hostMatch().startMatch`). ✅
- **Handoffs to native** (real screens): Preferences/Settings, Quest (New Quest/Duels/Challenges/
  Quest Draft/Quest Decks), Booster Draft, Sealed, Gauntlet, Puzzle, Online, Achievements,
  Avatars, Content Downloaders, Release Notes, Resume Quest/Spell Shop. ✅
- **Removed**: Planar Conquest (not desktop). ✅
- Round-trip back to the design Home via the anvil logo. ✅

### Known gaps / next enhancements
- **Lobby variants** (Commander, Planechase, Archenemy, Vanguard, Momir, Tiny Leaders): the chips
  display but are **not yet applied** to the launched match (common case = plain Constructed
  only). Next: either apply variants in `launchConstructed`, or hand off to the native lobby when
  a variant is selected.
- The design's own Quest/Settings/Battlefield screens still contain placeholder markup, but are
  **unreachable in-app** (those nav items hand off to native). Optional future work: bring them
  into the design with injected real data instead of handing off.
- Starting-Life stepper in the design lobby is not applied (Forge derives life); currently cosmetic.

## Decisions to confirm
- **Deck Editor:** HANDOFF to native (recommended) vs. rebuild a real HTML editor (large)?
- **Lobby:** design handles common case + native handoff for advanced (recommended) vs. full
  native handoff for all game setup?
- **Quest:** show real state + HANDOFF actions (recommended) vs. deeper HTML quest?
