/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.toolbox;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;

import forge.gui.UiCommand;
import forge.gui.framework.ILocalRepaint;
import forge.gui.interfaces.IButton;
import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FSkin.Colors;
import forge.toolbox.FSkin.SkinImage;
import forge.toolbox.FSkin.SkinnedButton;

/**
 * The core JButton used throughout the Forge project. Follows skin font and
 * theme button styling.
 *
 */
@SuppressWarnings("serial")
public class FButton extends SkinnedButton implements ILocalRepaint, IButton {

    /** The img r. */
    private SkinImage imgL;
    private SkinImage imgM;
    private SkinImage imgR;
    private int w, h = 0;
    private boolean allImagesPresent = false;
    private boolean toggle = false;
    private boolean primary = false;
    private boolean hovered = false;
    private final AlphaComposite disabledComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f);
    private KeyAdapter klEnter;

    /**
     * Instantiates a new FButton.
     */
    public FButton() {
        this("");
    }

    public FButton(final String label) {
        super(label);
        this.setOpaque(false);
        this.setForeground(FSkin.getColor(FSkin.Colors.CLR_TEXT));
        this.setBackground(Color.red);
        this.setFocusPainted(false);
        this.setBorder(BorderFactory.createEmptyBorder());
        this.setContentAreaFilled(false);
        this.setMargin(new Insets(0, 25, 0, 25));
        this.setFont(FSkin.getBoldFont(14));
        this.imgL = FSkin.getIcon(FSkinProp.IMG_BTN_UP_LEFT);
        this.imgM = FSkin.getIcon(FSkinProp.IMG_BTN_UP_CENTER);
        this.imgR = FSkin.getIcon(FSkinProp.IMG_BTN_UP_RIGHT);

        if ((this.imgL != null) && (this.imgM != null) && (this.imgR != null)) {
            this.allImagesPresent = true;
        }

        klEnter = new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                if (e.getKeyCode() == 10) {
                    doClick();
                }
            }
        };

        // Mouse events
        this.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(final MouseEvent evt) {
                hovered = true;
                if (isToggled() || !isEnabled()) { return; }
                resetImg();
                repaintSelf();
            }

            @Override
            public void mouseExited(final MouseEvent evt) {
                hovered = false;
                if (isToggled() || !isEnabled()) { return; }
                resetImg();
                repaintSelf();
            }

            @Override
            public void mousePressed(final MouseEvent evt) {
                if (isToggled() || !isEnabled()) { return; }
                imgL = FSkin.getIcon(FSkinProp.IMG_BTN_DOWN_LEFT);
                imgM = FSkin.getIcon(FSkinProp.IMG_BTN_DOWN_CENTER);
                imgR = FSkin.getIcon(FSkinProp.IMG_BTN_DOWN_RIGHT);
                repaintSelf();
            }

            @Override
            public void mouseReleased(final MouseEvent evt) {
                if (isToggled() || !isEnabled()) { return; }
                resetImg();
                repaintSelf();
            }
        });

        // Focus events
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(final FocusEvent e) {
                if (isToggled()) { return; }
                resetImg();
                addKeyListener(klEnter);
                repaintSelf();
            }

            @Override
            public void focusLost(final FocusEvent e) {
                if (isToggled()) { return; }
                resetImg();
                removeKeyListener(klEnter);
                repaintSelf();
            }
        });
    }

    private void resetImg() {
        if (hovered) {
            imgL = FSkin.getIcon(FSkinProp.IMG_BTN_OVER_LEFT);
            imgM = FSkin.getIcon(FSkinProp.IMG_BTN_OVER_CENTER);
            imgR = FSkin.getIcon(FSkinProp.IMG_BTN_OVER_RIGHT);
        }
        else if (isFocusOwner()) {
            imgL = FSkin.getIcon(FSkinProp.IMG_BTN_FOCUS_LEFT);
            imgM = FSkin.getIcon(FSkinProp.IMG_BTN_FOCUS_CENTER);
            imgR = FSkin.getIcon(FSkinProp.IMG_BTN_FOCUS_RIGHT);
        } else {
            imgL = FSkin.getIcon(FSkinProp.IMG_BTN_UP_LEFT);
            imgM = FSkin.getIcon(FSkinProp.IMG_BTN_UP_CENTER);
            imgR = FSkin.getIcon(FSkinProp.IMG_BTN_UP_RIGHT);
        }
    }

    @Override
    public void setEnabled(final boolean b0) {
        if (!b0) {
            imgL = FSkin.getIcon(FSkinProp.IMG_BTN_DISABLED_LEFT);
            imgM = FSkin.getIcon(FSkinProp.IMG_BTN_DISABLED_CENTER);
            imgR = FSkin.getIcon(FSkinProp.IMG_BTN_DISABLED_RIGHT);
        }
        else {
            resetImg();
        }

        super.setEnabled(b0);
        repaintSelf();
    }

    /**
     * Button toggle state, for a "permanently pressed" functionality, e.g. as a tab.
     *
     * @return boolean
     */
    public boolean isToggled() {
        return toggle;
    }

    /** Render this button as a filled gold primary action (e.g. Start / Play). */
    public void setPrimary(final boolean b0) {
        this.primary = b0;
        repaintSelf();
    }

    /** @param b0 &emsp; boolean. */
    public void setToggled(final boolean b0) {
        if (b0) {
            imgL = FSkin.getIcon(FSkinProp.IMG_BTN_TOGGLE_LEFT);
            imgM = FSkin.getIcon(FSkinProp.IMG_BTN_TOGGLE_CENTER);
            imgR = FSkin.getIcon(FSkinProp.IMG_BTN_TOGGLE_RIGHT);
        }
        else if (isEnabled()) {
            resetImg();
        }
        else {
            imgL = FSkin.getIcon(FSkinProp.IMG_BTN_DISABLED_LEFT);
            imgM = FSkin.getIcon(FSkinProp.IMG_BTN_DISABLED_CENTER);
            imgR = FSkin.getIcon(FSkinProp.IMG_BTN_DISABLED_RIGHT);
        }
        this.toggle = b0;
        repaintSelf();
    }

    public int getAutoSizeWidth() {
        int width = 0;
        if (this.getText() != null && !this.getText().isEmpty()) {
            final FontMetrics metrics = this.getFontMetrics(this.getFont());
            width = metrics.stringWidth(this.getText());
        }
        return width;
    }

    /** Prevent button from repainting the whole screen. */
    @Override
    public void repaintSelf() {
        final Dimension d = getSize();
        repaint(0, 0, d.width, d.height);
    }

    @Override
    protected void paintComponent(final Graphics g) {
        w = getWidth();
        h = getHeight();

        // Flat "Arena-lite" vector painting (replaces the old 3-slice sprite button).
        // Default is a quiet secondary button (dark fill + muted-gold hairline); the gold
        // accent is reserved for hover/focus and the toggled "selected" state so the app
        // isn't flooded with gold.
        final Color theme2   = FSkin.getColor(Colors.CLR_THEME2).color;
        final Color themeDk  = FSkin.getColor(Colors.CLR_THEME).color;
        final Color hoverClr = FSkin.getColor(Colors.CLR_HOVER).color;
        final Color borders  = FSkin.getColor(Colors.CLR_BORDERS).color;
        final Color gold     = FSkin.getColor(Colors.CLR_ACTIVE).color;
        final Color textClr  = FSkin.getColor(Colors.CLR_TEXT).color;
        final Color inactive = FSkin.getColor(Colors.CLR_INACTIVE).color;

        Color fill, border, foreground;
        Color goldWash = null;
        if (!isEnabled()) {
            fill = theme2; border = inactive; foreground = inactive;
        } else if (primary) {
            if (getModel().isPressed()) { fill = FSkin.getColor(Colors.CLR_ACTIVE).stepColor(-30).color; }
            else if (hovered) { fill = FSkin.getColor(Colors.CLR_ACTIVE).stepColor(30).color; }
            else { fill = gold; }
            border = FSkin.getColor(Colors.CLR_ACTIVE).stepColor(-45).color;
            foreground = FSkin.getColor(Colors.CLR_OVERLAY).color;
        } else if (isToggled()) {
            fill = theme2; border = gold; foreground = gold;
            goldWash = new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 46);
        } else if (getModel().isPressed()) {
            fill = themeDk; border = gold; foreground = textClr;
        } else if (hovered) {
            fill = hoverClr; border = gold; foreground = textClr;
        } else if (isFocusOwner()) {
            fill = theme2; border = gold; foreground = textClr;
        } else {
            fill = theme2; border = borders; foreground = textClr;
        }

        final Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        final int arc = Math.min(h, 18);
        g2d.setColor(fill);
        g2d.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        if (goldWash != null) {
            g2d.setColor(goldWash);
            g2d.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        }
        g2d.setStroke(new BasicStroke(1f));
        g2d.setColor(border);
        g2d.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2d.dispose();

        setForeground(foreground);
        super.paintComponent(g);
    }

    @Override
    public void setCommand(final UiCommand command) {
        addActionListener(e -> command.run());
    }

    @Override
    public void setImage(final FSkinProp color) {
        setForeground(FSkin.getColor(Colors.fromSkinProp(color)));
    }

    @Override
    public void setTextColor(final int r, final int g, final int b) {
        setForeground(new Color(r, g, b));
    }
}
