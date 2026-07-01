package forge.toolbox;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.Icon;

import forge.toolbox.FSkin.Colors;

/**
 * Flat "Arena-lite" icons for check boxes and radio buttons, replacing the
 * Metal look-and-feel's blue defaults so toggles match the gold theme.
 */
public final class ModernToggleIcons {
    private ModernToggleIcons() { }

    private static final int SIZE = 16;

    private static Color fill(final AbstractButton b) {
        return FSkin.getColor(Colors.CLR_THEME2).color;
    }
    private static Color border(final AbstractButton b) {
        if (!b.isEnabled()) { return FSkin.getColor(Colors.CLR_INACTIVE).color; }
        if (b.isSelected() || b.getModel().isRollover()) { return FSkin.getColor(Colors.CLR_ACTIVE).color; }
        return FSkin.getColor(Colors.CLR_BORDERS).color;
    }

    public static final class Check implements Icon {
        @Override public int getIconWidth() { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }
        @Override public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            final AbstractButton b = (AbstractButton) c;
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final int s = SIZE - 3;
            final int oy = y + 1;
            g2.setColor(b.isSelected() && b.isEnabled() ? FSkin.getColor(Colors.CLR_ACTIVE).color : fill(b));
            g2.fillRoundRect(x, oy, s, s, 5, 5);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(border(b));
            g2.drawRoundRect(x, oy, s, s, 5, 5);
            if (b.isSelected()) {
                g2.setColor(FSkin.getColor(Colors.CLR_OVERLAY).color);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 3, oy + 6, x + 6, oy + 9);
                g2.drawLine(x + 6, oy + 9, x + 10, oy + 3);
            }
            g2.dispose();
        }
    }

    public static final class Radio implements Icon {
        @Override public int getIconWidth() { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }
        @Override public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            final AbstractButton b = (AbstractButton) c;
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final int s = SIZE - 3;
            final int oy = y + 1;
            g2.setColor(fill(b));
            g2.fillOval(x, oy, s, s);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(border(b));
            g2.drawOval(x, oy, s, s);
            if (b.isSelected()) {
                g2.setColor(b.isEnabled() ? FSkin.getColor(Colors.CLR_ACTIVE).color : FSkin.getColor(Colors.CLR_INACTIVE).color);
                g2.fillOval(x + 4, oy + 4, s - 8, s - 8);
            }
            g2.dispose();
        }
    }
}
