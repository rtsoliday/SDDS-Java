/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.*;

/** The flat widgets of the editor's interface. */
final class Flat {
    private Flat() {
    }

    /** A button with no outline that shows a rounded background when hovered, pressed or checked. */
    static class ToolButton extends JToggleButton {
        boolean checkable;
        int radius = 12;
        Color checkedBg, checkedFg, hoverBg;

        ToolButton(String text, Icon icon, String tip) {
            super(text, icon);
            setToolTipText(tip);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(Theme.t.text);
            setBackground(Theme.t.chrome);
            setMargin(new Insets(0, 0, 0, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            setIconTextGap(4);
            setRolloverEnabled(true);
            checkedBg = Theme.t.accentSoft;
            checkedFg = Theme.t.accentText;
            hoverBg = Theme.t.hover;
        }

        /** A plain click must not leave an ordinary button checked. */
        protected void fireActionPerformed(ActionEvent event) {
            if (!checkable)
                setSelected(false);
            super.fireActionPerformed(event);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            ButtonModel m = getModel();
            Color fill = null;
            if (isEnabled()) {
                if (m.isPressed() && m.isArmed())
                    fill = Theme.t.border;
                else if (isSelected() && checkable)
                    fill = checkedBg;
                else if (m.isRollover())
                    fill = hoverBg;
            }
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            }
            g2.dispose();
            Color normal = getForeground();
            if (isSelected() && checkable && checkedFg != null)
                setForegroundQuietly(checkedFg);
            super.paintComponent(g);
            setForegroundQuietly(normal);
        }

        private boolean painting;

        private void setForegroundQuietly(Color color) {
            painting = true;
            super.setForeground(color);
            painting = false;
        }

        public void repaint() {
            if (!painting)
                super.repaint();
        }

        public void repaint(long time, int x, int y, int width, int height) {
            if (!painting)
                super.repaint(time, x, y, width, height);
        }
    }

    /** A small action at the right of a panel header. */
    static ToolButton panelAction(String text, Icons icon, String tip) {
        ToolButton button = new ToolButton(text, icon == null ? null : icon.icon(14, Theme.t.muted), tip);
        button.setForeground(Theme.t.muted);
        button.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        button.radius = 10;
        return button;
    }

    /** A thin line and padding around a field, with a highlight while it has focus. */
    static class FieldBorder extends AbstractBorder {
        final int radius;
        final int left;

        FieldBorder(int radius, int left) {
            this.radius = radius;
            this.left = left;
        }

        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            /* An opaque field fills its square corners, so cover them with what is behind it. */
            if (c.isOpaque() && c.getParent() != null) {
                java.awt.geom.Area outside = new java.awt.geom.Area(new Rectangle(x, y, width, height));
                outside.subtract(new java.awt.geom.Area(new RoundRectangle2D.Float(x, y, width, height, radius, radius)));
                g2.setColor(c.getParent().getBackground());
                g2.fill(outside);
            }
            Color color = c.isFocusOwner() ? Theme.t.accent : Theme.t.border;
            Object invalid = c instanceof JComponent ? ((JComponent) c).getClientProperty("invalid") : null;
            if (Boolean.TRUE.equals(invalid))
                color = Theme.t.warn;
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, radius, radius));
            g2.dispose();
        }

        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(3, left, 3, c instanceof SearchField ? 22 : 6);
            return insets;
        }
    }

    /**
     * Make a field in the main window keep its keys from the window's menu shortcuts: Ctrl+Z
     * undoes typing rather than a change to the document, and Ctrl+Shift+O is the window's
     * Outline shortcut rather than flipping the text direction.
     *
     * @return the field's history, so that it can be cleared
     */
    static javax.swing.undo.UndoManager keepKeys(final javax.swing.text.JTextComponent text) {
        final javax.swing.undo.UndoManager history = new javax.swing.undo.UndoManager();
        text.getDocument().addUndoableEditListener(history);
        InputMap keys = text.getInputMap();
        keys.put(KeyStroke.getKeyStroke("ctrl shift O"), "none");
        keys.put(KeyStroke.getKeyStroke("ctrl Z"), "undoTyping");
        keys.put(KeyStroke.getKeyStroke("ctrl shift Z"), "redoTyping");
        text.getActionMap().put("undoTyping", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                if (history.canUndo())
                    history.undo();
            }
        });
        text.getActionMap().put("redoTyping", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                if (history.canRedo())
                    history.redo();
            }
        });
        return history;
    }

    /** Give a text field the editor's look. */
    static <T extends JTextField> T field(T text) {
        text.setBorder(new FieldBorder(10, 6));
        text.setBackground(Theme.t.surface);
        text.setForeground(Theme.t.text);
        text.setCaretColor(Theme.t.text);
        text.setSelectionColor(Theme.t.accentSoft);
        text.setSelectedTextColor(Theme.t.text);
        repaintOnFocus(text);
        return text;
    }

    private static void repaintOnFocus(final JComponent component) {
        component.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                component.repaint();
            }

            public void focusLost(FocusEvent e) {
                component.repaint();
            }
        });
    }

    /** A rounded field with a magnifying glass and grey text while it is empty. */
    static class SearchField extends JTextField {
        String placeholder;
        Color fill;

        SearchField(String placeholder, Color fill) {
            super();
            this.placeholder = placeholder;
            this.fill = fill;
            setBorder(new FieldBorder(12, 28));
            setForeground(Theme.t.text);
            setCaretColor(Theme.t.text);
            setSelectionColor(Theme.t.accentSoft);
            setSelectedTextColor(Theme.t.text);
            setOpaque(false);
            repaintOnFocus(this);
            /* The cross at the right clears the field. */
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (!getText().isEmpty() && e.getX() >= getWidth() - 22)
                        setText("");
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(!getText().isEmpty() && e.getX() >= getWidth() - 22
                            ? Cursor.DEFAULT_CURSOR
                            : Cursor.TEXT_CURSOR));
                }
            });
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            Icons.SEARCH.icon(14, Theme.t.muted).paintIcon(this, g2, 9, (getHeight() - 14) / 2);
            if (!getText().isEmpty())
                Icons.CLOSE.icon(12, Theme.t.muted).paintIcon(this, g2, getWidth() - 18, (getHeight() - 12) / 2);
            if (getText().isEmpty() && placeholder != null) {
                g2.setColor(Theme.t.muted);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, 28, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A panel with a rounded fill and outline. */
    static class Card extends JPanel {
        int radius;
        Color fill, line;

        Card(LayoutManager layout, Color fill, Color line, int radius) {
            super(layout);
            this.fill = fill;
            this.line = line;
            this.radius = radius;
            setOpaque(false);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            if (line != null) {
                g2.setColor(line);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, radius, radius));
            }
            g2.dispose();
        }
    }

    /** A pill showing a count. */
    static class Chip extends JLabel {
        Chip() {
            setFont(Theme.small(Theme.uiFont()));
            setForeground(Theme.t.muted);
            setHorizontalAlignment(CENTER);
            setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(Theme.t.header);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thin scroll bars with a rounded thumb and no arrows. */
    static class ScrollBarUI extends BasicScrollBarUI {
        protected void configureScrollBarColors() {
            thumbColor = Theme.t.border;
            trackColor = Theme.t.surface;
        }

        private JButton nothing() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        protected JButton createDecreaseButton(int orientation) {
            return nothing();
        }

        protected JButton createIncreaseButton(int orientation) {
            return nothing();
        }

        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(Theme.t.surface);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty())
                return;
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(isDragging ? Theme.t.muted : isThumbRollover() ? Theme.t.muted.brighter() : Theme.t.border);
            int inset = 3;
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL)
                g2.fill(new RoundRectangle2D.Float(r.x + inset, r.y + 1, r.width - 2 * inset, r.height - 2, 6, 6));
            else
                g2.fill(new RoundRectangle2D.Float(r.x + 1, r.y + inset, r.width - 2, r.height - 2 * inset, 6, 6));
            g2.dispose();
        }
    }

    /** Give a scroll pane thin bars and no border. */
    static <T extends JScrollPane> T scrollPane(T pane) {
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setViewportBorder(null);
        pane.getVerticalScrollBar().setUI(new ScrollBarUI());
        pane.getHorizontalScrollBar().setUI(new ScrollBarUI());
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(12, 12));
        pane.getHorizontalScrollBar().setPreferredSize(new Dimension(12, 12));
        pane.getVerticalScrollBar().setUnitIncrement(24);
        pane.getHorizontalScrollBar().setUnitIncrement(24);
        pane.setOpaque(false);
        pane.getViewport().setBackground(Theme.t.surface);
        return pane;
    }

    /** A combo box with no raised arrow. */
    static class ComboUI extends BasicComboBoxUI {
        /* Text can paint a little wider than it measures, which would elide the value. */
        public Dimension getMinimumSize(JComponent c) {
            Dimension size = super.getMinimumSize(c);
            size.width += 8;
            return size;
        }

        protected JButton createArrowButton() {
            JButton button = new JButton(Icons.CHEVRON_DOWN.icon(12, Theme.t.muted));
            button.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
            button.setContentAreaFilled(false);
            button.setFocusPainted(false);
            button.setOpaque(false);
            return button;
        }

        protected javax.swing.plaf.basic.ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox);
            popup.setBorder(BorderFactory.createLineBorder(Theme.t.border));
            popup.getAccessibleContext().setAccessibleParent(comboBox);
            return popup;
        }

        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            /* The owner of the combo box paints any background. */
        }

        protected ListCellRenderer<Object> createRenderer() {
            return new BasicComboBoxRenderer.UIResource() {
                public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected,
                        boolean focus) {
                    Component c = super.getListCellRendererComponent(list, value, index, selected, false);
                    setOpaque(index >= 0);
                    setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                    setBackground(selected && index >= 0 ? Theme.t.accentSoft : Theme.t.surface);
                    setForeground(comboBox != null && !comboBox.isEnabled() ? Theme.t.muted : Theme.t.text);
                    return c;
                }
            };
        }
    }

    /** Give a combo box the editor's look. */
    static <T> JComboBox<T> combo(JComboBox<T> box, boolean outlined) {
        box.setUI(new ComboUI());
        box.setOpaque(outlined);
        box.setBackground(Theme.t.surface);
        box.setForeground(Theme.t.text);
        box.setFocusable(true);
        if (outlined) {
            box.setBorder(new FieldBorder(10, 2));
            repaintOnFocus(box);
        } else {
            box.setBorder(BorderFactory.createEmptyBorder());
        }
        return box;
    }

    /** A plain dialog button. */
    static JButton button(String text) {
        JButton button = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.smooth(g2);
                ButtonModel m = getModel();
                boolean primary = Boolean.TRUE.equals(getClientProperty("primary")) && isEnabled();
                Color fill = primary ? Theme.t.accent : m.isArmed() ? Theme.t.border : m.isRollover() ? Theme.t.hover : Theme.t.surface;
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                if (!primary) {
                    g2.setColor(Theme.t.border);
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 10, 10));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setForeground(Theme.t.text);
        return button;
    }

    /** A dialog's main button, in the accent color. */
    static JButton primaryButton(String text) {
        JButton button = button(text);
        button.putClientProperty("primary", Boolean.TRUE);
        button.setForeground(Theme.t.onAccent);
        return button;
    }

    /** Set a label to be muted. */
    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.t.muted);
        return label;
    }

    /** A one-pixel rule. */
    static JComponent rule(boolean vertical) {
        JPanel line = new JPanel();
        line.setBackground(Theme.t.border);
        Dimension size = vertical ? new Dimension(1, 1) : new Dimension(1, 1);
        line.setPreferredSize(size);
        line.setMinimumSize(size);
        line.setMaximumSize(vertical ? new Dimension(1, Integer.MAX_VALUE) : new Dimension(Integer.MAX_VALUE, 1));
        return line;
    }

    /** A column of padding, for simple layouts. */
    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** Let Escape close a dialog and Enter activate its main button. */
    static void dialogKeys(final JDialog dialog, JButton main) {
        if (main != null)
            dialog.getRootPane().setDefaultButton(main);
        dialog.getRootPane().registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispatchEvent(new java.awt.event.WindowEvent(dialog, java.awt.event.WindowEvent.WINDOW_CLOSING));
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
