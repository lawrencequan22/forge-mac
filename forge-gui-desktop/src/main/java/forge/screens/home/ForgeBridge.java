package forge.screens.home;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import forge.Singletons;
import forge.card.ColorSet;
import forge.deck.Deck;
import forge.deck.DeckgenUtil;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;

/**
 * Bridge exposed to the HTML design as {@code window.forge}. Read methods return JSON
 * strings the design parses to render REAL Forge data; action methods drive real Forge
 * (run on the Swing EDT). Everything is guarded so a data hiccup never breaks the UI.
 */
public class ForgeBridge {

    // ---------------- reads (JSON strings) ----------------

    /** Up to 6 of the user's real Constructed decks. */
    public String getRecentDecks() {
        final StringBuilder sb = new StringBuilder("[");
        try {
            int n = 0;
            for (final Deck d : FModel.getDecks().getConstructed()) {
                if (n >= 6) { break; }
                if (n > 0) { sb.append(','); }
                appendDeck(sb, d, "Constructed");
                n++;
            }
        } catch (final Throwable t) { /* ignore, return what we have */ }
        return sb.append(']').toString();
    }

    private void appendDeck(final StringBuilder sb, final Deck d, final String cat) {
        int count = 0;
        boolean w = false, u = false, b = false, r = false, g = false;
        try {
            count = d.getMain().countAll();
            for (final Map.Entry<PaperCard, Integer> e : d.getMain()) {
                final ColorSet cs = e.getKey().getRules().getColorIdentity();
                w |= cs.hasWhite(); u |= cs.hasBlue(); b |= cs.hasBlack(); r |= cs.hasRed(); g |= cs.hasGreen();
            }
        } catch (final Throwable t) { /* ignore */ }
        final StringBuilder colors = new StringBuilder("[");
        final boolean[] flags = { w, u, b, r, g };
        final String[] letters = { "W", "U", "B", "R", "G" };
        boolean first = true;
        for (int i = 0; i < 5; i++) {
            if (flags[i]) { if (!first) { colors.append(','); } colors.append('"').append(letters[i]).append('"'); first = false; }
        }
        colors.append(']');
        sb.append('{')
          .append("\"name\":\"").append(esc(d.getName())).append("\",")
          .append("\"meta\":\"").append(cat).append(" · ").append(count).append(" cards\",")
          .append("\"count\":").append(count).append(',')
          .append("\"colors\":").append(colors)
          .append('}');
    }

    /** First few lines of the real changelog. */
    public String getReleaseNotes() {
        final StringBuilder sb = new StringBuilder("[");
        try {
            File f = new File(ForgeConstants.CHANGES_FILE_NO_RELEASE);
            if (!f.exists()) { f = new File(ForgeConstants.CHANGES_FILE); }
            if (f.exists()) {
                int n = 0;
                for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                    line = line.trim();
                    if (line.isEmpty()) { continue; }
                    if (n >= 3) { break; }
                    if (n > 0) { sb.append(','); }
                    sb.append("{\"tag\":\"Changelog\",\"title\":\"").append(esc(line)).append("\"}");
                    n++;
                }
            }
        } catch (final Throwable t) { /* ignore */ }
        return sb.append(']').toString();
    }

    /** Real quest state, or {"active":false} if none started. */
    public String getQuest() {
        try {
            final forge.gamemodes.quest.QuestController q = FModel.getQuest();
            if (q == null || q.getAchievements() == null || q.getAssets() == null) {
                return "{\"active\":false}";
            }
            return "{\"active\":true,"
                + "\"name\":\"" + esc(q.getName()) + "\","
                + "\"credits\":" + q.getAssets().getCredits() + ","
                + "\"life\":" + q.getAssets().getLife(q.getMode()) + ","
                + "\"wins\":" + q.getAchievements().getWin() + ","
                + "\"losses\":" + q.getAchievements().getLost() + "}";
        } catch (final Throwable t) {
            return "{\"active\":false}";
        }
    }

    /** The user's real Constructed decks, for the lobby deck picker. */
    public String getDecks() {
        final StringBuilder sb = new StringBuilder("[");
        try {
            boolean first = true;
            for (final Deck d : FModel.getDecks().getConstructed()) {
                if (!first) { sb.append(','); }
                appendDeck(sb, d, "Constructed");
                first = false;
            }
        } catch (final Throwable t) { /* ignore */ }
        return sb.append(']').toString();
    }

    // ---------------- match launch (stateful builder, avoids JSON parsing) ----------------

    private final List<String[]> pendingPlayers = new ArrayList<>(); // {name, ai("1"/"0"), deckName}
    private int pendingGames = 3;

    public void newGame() { pendingPlayers.clear(); pendingGames = 3; }
    public void addPlayer(final String name, final boolean ai, final String deckName) {
        pendingPlayers.add(new String[] { name == null ? "Player" : name, ai ? "1" : "0", deckName == null ? "" : deckName });
    }
    public void setGames(final int n) { pendingGames = (n == 1 || n == 3 || n == 5) ? n : 3; }

    /** Launch a real native Constructed match from the collected seats. */
    public void launchConstructed() {
        final List<String[]> cfg = new ArrayList<>(pendingPlayers);
        final int games = pendingGames;
        SwingUtilities.invokeLater(() -> {
            try {
                final List<RegisteredPlayer> players = new ArrayList<>();
                RegisteredPlayer human = null;
                for (final String[] p : cfg) {
                    final boolean ai = "1".equals(p[1]);
                    final Deck deck = resolveDeck(p[2], ai);
                    final RegisteredPlayer rp = new RegisteredPlayer(deck).setPlayer(
                            ai ? GamePlayerUtil.createAiPlayer(p[0]) : GamePlayerUtil.getGuiPlayer());
                    players.add(rp);
                    if (!ai && human == null) { human = rp; }
                }
                if (players.size() < 2) { return; }
                if (human == null) { human = players.get(0); }
                final GameRules rules = new GameRules(GameType.Constructed);
                rules.setGamesPerMatch(games);
                final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();
                hostedMatch.startMatch(rules, null, players, human, GuiBase.getInterface().getNewGuiGame());
            } catch (final Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private Deck resolveDeck(final String name, final boolean ai) {
        try {
            if (name != null && !name.isEmpty()
                    && !name.equalsIgnoreCase("Random") && !name.equalsIgnoreCase("Generated")) {
                final Deck d = FModel.getDecks().getConstructed().get(name);
                if (d != null) { return d; }
            }
        } catch (final Throwable t) { /* fall through to random */ }
        return DeckgenUtil.getRandomColorDeck(ai);
    }

    // ---------------- actions (Swing EDT) ----------------

    /** Open Forge's native constructed deck editor (screen + editor controller). */
    public void openDeckEditor() {
        SwingUtilities.invokeLater(() -> {
            try {
                Singletons.getControl().setCurrentScreen(FScreen.DECK_EDITOR_CONSTRUCTED);
                final forge.screens.deckeditor.CDeckEditorUI ui = forge.screens.deckeditor.CDeckEditorUI.SINGLETON_INSTANCE;
                ui.setEditorController(new forge.screens.deckeditor.controllers.CEditorConstructed(ui.getCDetailPicture()));
            } catch (final Throwable t) {
                t.printStackTrace();
            }
        });
    }

    /** Leave the WebView home and open a native home submenu (EDocID name). */
    public void openSubmenu(final String edocId) {
        SwingUtilities.invokeLater(() -> {
            try {
                VHomeUI.SINGLETON_INSTANCE.showMenus();
                CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.valueOf(edocId));
            } catch (final Throwable t) { t.printStackTrace(); }
        });
    }

    private static String esc(final String s) {
        if (s == null) { return ""; }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
