package forge.screens.home;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import forge.Singletons;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.Colors;
import forge.toolbox.FSkin.SkinnedPanel;

/**
 * Command Center — the redesigned Forge home landing.
 *
 * A single custom-painted (Java2D) screen that reproduces the Forge.dc.html
 * redesign: a grouped gold sidebar, a header, and a dashboard (hero banner,
 * game-mode cards, recent decks, release-notes teaser). All navigation hands
 * off to Forge's existing screens, so no functionality is lost.
 */
@SuppressWarnings("serial")
public class CommandCenter extends SkinnedPanel {
    // ----- design tokens (theme-driven where possible) -----
    private static Color c(final Colors k) { return FSkin.getColor(k).getColor(); }
    private static Color bg()     { return c(Colors.CLR_THEME); }
    private static Color bgDark() { return c(Colors.CLR_OVERLAY); }
    private static Color panel()  { return c(Colors.CLR_THEME2); }
    private static Color hover()  { return c(Colors.CLR_HOVER); }
    private static Color line()   { return c(Colors.CLR_BORDERS); }
    private static Color text()   { return c(Colors.CLR_TEXT); }
    private static Color mute()   { return c(Colors.CLR_INACTIVE); }
    private static Color gold()   { return c(Colors.CLR_ACTIVE); }
    private static final Color GOLD_B = new Color(0xF2D78F);
    private static final Color DIM    = new Color(0xA99F88);
    // MTG color accents (design tokens)
    private static final Color W = new Color(0xEFE4C2), U = new Color(0x5A97DD),
            B = new Color(0x9A78C4), R = new Color(0xDB5C48), G = new Color(0x5FB06F);

    private static final int SIDEBAR_W = 238;
    private static final int HEADER_H = 64;

    private Font serif(final int size, final boolean bold) {
        return new Font(Font.SERIF, bold ? Font.BOLD : Font.PLAIN, size);
    }
    private Font sans(final int size, final int style) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static final class Hotspot {
        final Rectangle r; final Runnable onClick;
        Hotspot(final Rectangle r, final Runnable onClick) { this.r = r; this.onClick = onClick; }
    }

    private final List<Hotspot> hotspots = new ArrayList<>();
    private int hoverIndex = -1;

    public CommandCenter() {
        setOpaque(true);
        final MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(final MouseEvent e) {
                int idx = -1;
                for (int i = 0; i < hotspots.size(); i++) {
                    if (hotspots.get(i).r.contains(e.getPoint())) { idx = i; break; }
                }
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    setCursor(Cursor.getPredefinedCursor(idx >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }
            @Override public void mouseExited(final MouseEvent e) {
                if (hoverIndex != -1) { hoverIndex = -1; setCursor(Cursor.getDefaultCursor()); repaint(); }
            }
            @Override public void mousePressed(final MouseEvent e) {
                for (final Hotspot h : hotspots) {
                    if (h.r.contains(e.getPoint())) { if (h.onClick != null) h.onClick.run(); return; }
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ----- navigation -----
    private void openSubmenu(final EDocID id) {
        VHomeUI.SINGLETON_INSTANCE.showMenus();
        CHomeUI.SINGLETON_INSTANCE.itemClick(id);
    }
    private void openScreen(final FScreen s) {
        Singletons.getControl().setCurrentScreen(s);
    }

    // ----- painting -----
    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        hotspots.clear();
        final Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        final int w = getWidth(), h = getHeight();

        // app background
        g2.setColor(bg());
        g2.fillRect(0, 0, w, h);

        paintSidebar(g2, h);
        paintHeader(g2, w);
        paintDashboard(g2, w, h);

        g2.dispose();
    }

    private void diamond(final Graphics2D g2, final int cx, final int cy, final int r, final Color col) {
        final Path2D p = new Path2D.Float();
        p.moveTo(cx, cy - r); p.lineTo(cx + r, cy); p.lineTo(cx, cy + r); p.lineTo(cx - r, cy); p.closePath();
        g2.setColor(col); g2.fill(p);
    }

    private void paintSidebar(final Graphics2D g2, final int h) {
        g2.setPaint(new GradientPaint(0, 0, bgDark(), 0, h, bg()));
        g2.fillRect(0, 0, SIDEBAR_W, h);
        g2.setColor(line());
        g2.fillRect(SIDEBAR_W - 1, 0, 1, h);

        // logo
        diamond(g2, 38, 42, 15, GOLD_B);
        g2.setFont(serif(20, true)); g2.setColor(GOLD_B);
        g2.drawString("FORGE", 64, 40);
        g2.setFont(sans(9, Font.PLAIN)); g2.setColor(mute());
        g2.drawString("R U L E S   E N G I N E", 64, 54);

        int y = 92;
        y = navItem(g2, "Home", y, true, () -> VHomeUI.SINGLETON_INSTANCE.showDashboard());
        y = navItem(g2, "Deck Editor", y, false, () -> openScreen(FScreen.DECK_EDITOR_CONSTRUCTED));

        y = group(g2, "Sanctioned", y);
        y = navItem(g2, "Constructed", y, false, () -> openSubmenu(EDocID.HOME_CONSTRUCTED));
        y = navItem(g2, "Booster Draft", y, false, () -> openSubmenu(EDocID.HOME_DRAFT));
        y = navItem(g2, "Sealed Deck", y, false, () -> openSubmenu(EDocID.HOME_SEALED));

        y = group(g2, "Quest Mode", y);
        y = navItem(g2, "New Quest", y, false, () -> openSubmenu(EDocID.HOME_QUESTSTART));
        y = navItem(g2, "Duels", y, false, () -> openSubmenu(EDocID.HOME_QUESTDUELS));
        y = navItem(g2, "Challenges", y, false, () -> openSubmenu(EDocID.HOME_QUESTCHALLENGES));
        y = navItem(g2, "Quest Decks", y, false, () -> openSubmenu(EDocID.HOME_QUESTDECKS));

        y = group(g2, "More Modes", y);
        y = navItem(g2, "Gauntlet", y, false, () -> openSubmenu(EDocID.HOME_GAUNTLETQUICK));
        y = navItem(g2, "Puzzle", y, false, () -> openSubmenu(EDocID.HOME_PUZZLE_SOLVE));
        y = navItem(g2, "Online Multiplayer", y, false, () -> openSubmenu(EDocID.HOME_NETWORK));

        y = group(g2, "Settings", y);
        y = navItem(g2, "Preferences", y, false, () -> openSubmenu(EDocID.HOME_PREFERENCES));
        y = navItem(g2, "Achievements", y, false, () -> openSubmenu(EDocID.HOME_ACHIEVEMENTS));
        y = navItem(g2, "Avatars", y, false, () -> openSubmenu(EDocID.HOME_AVATARS));
        y = navItem(g2, "Content Downloaders", y, false, () -> openSubmenu(EDocID.HOME_UTILITIES));
        y = navItem(g2, "Release Notes", y, false, () -> openSubmenu(EDocID.HOME_RELEASE_NOTES));
    }

    private int group(final Graphics2D g2, final String title, final int y) {
        g2.setFont(sans(9, Font.PLAIN));
        g2.setColor(mute());
        g2.drawString(spaced(title.toUpperCase()), 24, y + 18);
        return y + 26;
    }

    private String spaced(final String s) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { b.append(s.charAt(i)); if (i < s.length() - 1) b.append(' '); }
        return b.toString();
    }

    private int navItem(final Graphics2D g2, final String label, final int y, final boolean active, final Runnable action) {
        final int x = 12, wItem = SIDEBAR_W - 24, hItem = 34;
        final Rectangle rect = new Rectangle(x, y, wItem, hItem);
        final boolean hovered = isHovered(rect);
        if (active || hovered) {
            g2.setColor(active ? hover() : panel());
            g2.fill(new RoundRectangle2D.Float(x, y, wItem, hItem, 9, 9));
        }
        if (active) {
            g2.setColor(GOLD_B);
            g2.fill(new RoundRectangle2D.Float(x, y + 8, 3, 18, 3, 3));
        }
        diamond(g2, x + 18, y + hItem / 2, 3, active ? GOLD_B : mute());
        g2.setFont(sans(13, active ? Font.BOLD : Font.PLAIN));
        g2.setColor(active ? GOLD_B : (hovered ? text() : DIM));
        g2.drawString(label, x + 30, y + hItem / 2 + 5);
        hotspots.add(new Hotspot(rect, action));
        return y + hItem + 2;
    }

    private boolean isHovered(final Rectangle rect) {
        return hoverIndex >= 0 && hoverIndex < hotspots.size() && hotspots.get(hoverIndex).r.equals(rect);
    }

    private void paintHeader(final Graphics2D g2, final int w) {
        g2.setPaint(new GradientPaint(0, 0, bgDark(), 0, HEADER_H, bg()));
        g2.fillRect(SIDEBAR_W, 0, w - SIDEBAR_W, HEADER_H);
        g2.setColor(line());
        g2.fillRect(SIDEBAR_W, HEADER_H - 1, w - SIDEBAR_W, 1);
        g2.setFont(serif(16, true));
        g2.setColor(text());
        g2.drawString("COMMAND CENTER", SIDEBAR_W + 28, 40);

        // search box
        final int sw = 220, sx = w - sw - 130, sy = 14, sh = 36;
        g2.setColor(panel());
        g2.fill(new RoundRectangle2D.Float(sx, sy, sw, sh, 10, 10));
        g2.setColor(line());
        g2.draw(new RoundRectangle2D.Float(sx, sy, sw, sh, 10, 10));
        g2.setColor(mute());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(sx + 13, sy + 12, 11, 11);
        g2.setFont(sans(12, Font.PLAIN));
        g2.drawString("Search cards, decks, sets…", sx + 34, sy + 23);

        // theme toggle (dark/light)
        final int tx = w - 96, ty = 14, box = 29;
        g2.setColor(panel());
        g2.fill(new RoundRectangle2D.Float(tx, ty, box * 2 + 12, 36, 10, 10));
        final boolean dark = !isLightTheme();
        drawToggleGlyph(g2, tx + 6, ty + 4, box, "☾", dark);   // moon
        drawToggleGlyph(g2, tx + 6 + box, ty + 4, box, "☀", !dark); // sun
        final Rectangle tRect = new Rectangle(tx, ty, box * 2 + 12, 36);
        hotspots.add(new Hotspot(tRect, this::toggleTheme));
    }

    private void drawToggleGlyph(final Graphics2D g2, final int x, final int y, final int box, final String glyph, final boolean on) {
        if (on) {
            g2.setColor(gold());
            g2.fill(new RoundRectangle2D.Float(x, y, box, box, 8, 8));
            g2.setColor(new Color(0x1C1608));
        } else {
            g2.setColor(DIM);
        }
        g2.setFont(sans(14, Font.PLAIN));
        g2.drawString(glyph, x + box / 2 - 7, y + box / 2 + 6);
    }

    private boolean isLightTheme() {
        // luminance test on the theme background
        final Color t = bg();
        final double lum = 0.299 * t.getRed() + 0.587 * t.getGreen() + 0.114 * t.getBlue();
        return lum > 128;
    }
    private void toggleTheme() {
        final String next = isLightTheme() ? "Umbral" : "Radiant";
        FSkin.changeSkin(next);
        SwingUtilitiesSafeRepaint();
    }
    private void SwingUtilitiesSafeRepaint() {
        repaint();
        if (getParent() != null) { getParent().revalidate(); getParent().repaint(); }
    }

    // ----- dashboard content -----
    private void paintDashboard(final Graphics2D g2, final int w, final int h) {
        final int left = SIDEBAR_W + 32;
        final int top = HEADER_H + 24;
        final int contentW = Math.min(w - left - 32, 1400);

        // hero banner
        final int heroH = 176;
        g2.setPaint(new GradientPaint(left, top, panel(), left + contentW, top, bgDark()));
        g2.fill(new RoundRectangle2D.Float(left, top, contentW, heroH, 20, 20));
        g2.setColor(line());
        g2.draw(new RoundRectangle2D.Float(left, top, contentW, heroH, 20, 20));

        g2.setFont(sans(10, Font.PLAIN)); g2.setColor(GOLD_B);
        g2.drawString(spaced("◆ QUEST · CONTINUE CAMPAIGN"), left + 34, top + 40);
        g2.setFont(serif(34, true)); g2.setColor(text());
        g2.drawString("The Shadowmoor Gambit", left + 32, top + 82);
        g2.setFont(sans(13, Font.PLAIN)); g2.setColor(DIM);
        g2.drawString("Resume your quest — battle tuned AI, earn credits, and grow your collection.", left + 34, top + 110);

        // hero buttons
        pillButton(g2, left + 34, top + 128, 168, 42, "▸ Resume Quest", true, () -> openSubmenu(EDocID.HOME_QUESTSTART));
        pillButton(g2, left + 214, top + 128, 120, 42, "Spell Shop", false, () -> openSubmenu(EDocID.HOME_QUESTSTART));

        // Game Modes header
        int y = top + heroH + 30;
        g2.setFont(serif(16, true)); g2.setColor(text());
        g2.drawString(spaced("GAME MODES"), left, y);
        y += 16;

        // mode cards grid 4 cols
        final String[][] modes = {
            {"SANCTIONED", "Constructed", "Bring a 60-card deck vs AI."},
            {"LIMITED", "Booster Draft", "Open packs, pick, and build."},
            {"LIMITED", "Sealed Deck", "Six packs, one pool."},
            {"CAMPAIGN", "Quest", "Earn credits, grow a collection."},
            {"SERIES", "Gauntlet", "Run a ladder of opponents."},
            {"SOLVE", "Puzzle", "Win from a fixed board state."},
            {"ONLINE", "Multiplayer", "Play against people over the net."},
            {"UTILITY", "Deck Editor", "Build and tune your decks."},
        };
        final Color[] accents = {U, G, R, G, R, U, B, gold()};
        final Runnable[] actions = {
            () -> openSubmenu(EDocID.HOME_CONSTRUCTED), () -> openSubmenu(EDocID.HOME_DRAFT),
            () -> openSubmenu(EDocID.HOME_SEALED), () -> openSubmenu(EDocID.HOME_QUESTSTART),
            () -> openSubmenu(EDocID.HOME_GAUNTLETQUICK), () -> openSubmenu(EDocID.HOME_PUZZLE_SOLVE),
            () -> openSubmenu(EDocID.HOME_NETWORK), () -> openScreen(FScreen.DECK_EDITOR_CONSTRUCTED),
        };
        final int cols = 4, gap = 15;
        final int cardW = (contentW - gap * (cols - 1)) / cols;
        final int cardH = 118;
        for (int i = 0; i < modes.length; i++) {
            final int cx = left + (i % cols) * (cardW + gap);
            final int cy = y + (i / cols) * (cardH + gap);
            modeCard(g2, cx, cy, cardW, cardH, modes[i][0], modes[i][1], modes[i][2], accents[i], actions[i]);
        }
    }

    private void pillButton(final Graphics2D g2, final int x, final int y, final int w, final int h,
            final String label, final boolean primary, final Runnable action) {
        final Rectangle rect = new Rectangle(x, y, w, h);
        final boolean hovered = isHovered(rect);
        if (primary) {
            g2.setPaint(new GradientPaint(x, y, GOLD_B, x, y + h, gold()));
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            g2.setColor(new Color(0x1C1608));
        } else {
            g2.setColor(hovered ? hover() : panel());
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            g2.setColor(line());
            g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            g2.setColor(text());
        }
        g2.setFont(sans(14, Font.BOLD));
        final int tw = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, x + (w - tw) / 2, y + h / 2 + 5);
        hotspots.add(new Hotspot(rect, action));
    }

    private void modeCard(final Graphics2D g2, final int x, final int y, final int w, final int h,
            final String tag, final String name, final String desc, final Color accent, final Runnable action) {
        final Rectangle rect = new Rectangle(x, y, w, h);
        final boolean hovered = isHovered(rect);
        g2.setPaint(new GradientPaint(x, y, panel(), x, y + h, bgDark()));
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 15, 15));
        // accent glow top-right
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
        g2.fillRect(x, y, w, h);
        g2.setPaint(new GradientPaint(x, y, panel(), x, y + h, bgDark()));
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 15, 15));
        g2.setColor(hovered ? gold() : line());
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 15, 15));

        g2.setFont(sans(9, Font.BOLD)); g2.setColor(accent);
        g2.drawString(spaced(tag), x + 15, y + 28);
        g2.setFont(serif(17, true)); g2.setColor(text());
        g2.drawString(name, x + 14, y + 58);
        g2.setFont(sans(11, Font.PLAIN)); g2.setColor(DIM);
        g2.drawString(desc, x + 15, y + 82);
        hotspots.add(new Hotspot(rect, action));
    }
}
