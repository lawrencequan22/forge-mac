package forge.screens.home;

import java.io.File;
import java.util.Map;

import javax.swing.SwingUtilities;

import forge.Singletons;
import forge.card.ColorSet;
import forge.deck.Deck;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;

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

    // ---------------- actions (Swing EDT) ----------------

    /** Open Forge's native deck editor. */
    public void openDeckEditor() {
        SwingUtilities.invokeLater(() -> {
            try { Singletons.getControl().setCurrentScreen(FScreen.DECK_EDITOR_CONSTRUCTED); }
            catch (final Throwable t) { t.printStackTrace(); }
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
