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
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

/** The panels, headers and splitter around the data tables. */
final class Panels {
    private Panels() {
    }

    /** A card with a title bar, a count chip, actions and a collapsible body. */
    static final class DataPanel extends Flat.Card {
        private static final long serialVersionUID = 1L;
        static final int HEADER_HEIGHT = 38;
        static final int BOTTOM_INSET = 5;
        final Flat.ToolButton toggle;
        final Flat.Chip chip = new Flat.Chip();
        private final JPanel header;
        private final JComponent rule = Flat.rule(false);
        private final JPanel bodyHolder = new JPanel(new BorderLayout());
        private JComponent body;
        private Runnable onToggle;

        DataPanel(String title) {
            super(new BorderLayout(), Theme.t.surface, Theme.t.border, 16);
            header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
            header.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 8));
            Dimension height = new Dimension(10, HEADER_HEIGHT);
            header.setPreferredSize(height);
            header.setMinimumSize(height);
            toggle = new Flat.ToolButton(title, Icons.CHEVRON_DOWN.icon(14, Theme.t.muted), "Show or hide " + title) {
                private static final long serialVersionUID = 1L;

                /* Bold text can paint wider than it measures, which would elide the title. */
                public Dimension getPreferredSize() {
                    Dimension size = super.getPreferredSize();
                    size.width += 6;
                    return size;
                }

                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            toggle.checkable = true;
            toggle.checkedBg = null;
            toggle.checkedFg = null;
            toggle.setSelected(true);
            toggle.setFont(Theme.bold(Theme.uiFont()));
            toggle.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            toggle.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    applyExpanded();
                    if (onToggle != null)
                        onToggle.run();
                }
            });
            header.add(toggle);
            header.add(Box.createHorizontalStrut(6));
            header.add(chip);
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(header, BorderLayout.CENTER);
            top.add(rule, BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);
            bodyHolder.setOpaque(false);
            bodyHolder.setBorder(BorderFactory.createEmptyBorder(0, 1, BOTTOM_INSET, 1));
            add(bodyHolder, BorderLayout.CENTER);
        }

        void setOnToggle(Runnable onToggle) {
            this.onToggle = onToggle;
        }

        void addHeaderStretch() {
            header.add(Box.createHorizontalGlue());
        }

        void addHeaderWidget(JComponent widget) {
            header.add(Box.createHorizontalStrut(6));
            widget.setAlignmentY(CENTER_ALIGNMENT);
            header.add(widget);
        }

        /** Add a widget right after the count chip. */
        void addLeadingWidget(JComponent widget) {
            header.add(Box.createHorizontalStrut(6));
            header.add(widget);
        }

        void setBody(JComponent body) {
            this.body = body;
            bodyHolder.add(body, BorderLayout.CENTER);
        }

        void setCountText(String text) {
            chip.setText(text);
        }

        boolean isExpanded() {
            return toggle.isSelected();
        }

        void setExpanded(boolean expanded) {
            if (toggle.isSelected() != expanded) {
                toggle.setSelected(expanded);
                applyExpanded();
            }
        }

        private void applyExpanded() {
            boolean on = toggle.isSelected();
            bodyHolder.setVisible(on);
            rule.setVisible(on);
            toggle.setIcon((on ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT).icon(14, Theme.t.muted));
            revalidate();
            repaint();
        }

        /** The height of everything but the body while expanded. */
        int chromeHeight() {
            return HEADER_HEIGHT + 1 + BOTTOM_INSET;
        }

        /** The height when collapsed. */
        int collapsedHeight() {
            return HEADER_HEIGHT + 1;
        }
    }

    /** A vertical stack of panels with grips between them, like the Qt editor's splitter. */
    static final class PanelStack extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int HANDLE = 10;
        private final List<DataPanel> panels = new ArrayList<DataPanel>();
        private int[] sizes;
        private int[] stretch;
        private int[] handles = new int[0];
        private int dragHandle = -1;
        private int dragStart;
        private int[] dragSizes;
        private int lastHeight = -1;

        PanelStack() {
            super(null);
            setOpaque(false);
            MouseAdapter grip = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragHandle = handleAt(e.getY());
                    if (dragHandle >= 0) {
                        dragStart = e.getY();
                        dragSizes = sizes.clone();
                    }
                }

                public void mouseDragged(MouseEvent e) {
                    if (dragHandle >= 0)
                        dragTo(e.getY());
                }

                public void mouseReleased(MouseEvent e) {
                    dragHandle = -1;
                }

                public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(
                            handleAt(e.getY()) >= 0 ? Cursor.N_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            };
            addMouseListener(grip);
            addMouseMotionListener(grip);
        }

        void addPanel(DataPanel panel) {
            panels.add(panel);
            add(panel);
            sizes = new int[panels.size()];
            stretch = new int[panels.size()];
            java.util.Arrays.fill(stretch, 1);
        }

        private List<Integer> visibleIndexes() {
            List<Integer> shown = new ArrayList<Integer>();
            for (int i = 0; i < panels.size(); i++)
                if (panels.get(i).isVisible())
                    shown.add(i);
            return shown;
        }

        private int handleAt(int y) {
            for (int i = 0; i < handles.length; i++)
                if (y >= handles[i] && y < handles[i] + HANDLE)
                    return i;
            return -1;
        }

        private int minimum(int i) {
            DataPanel p = panels.get(i);
            return p.isExpanded() ? p.chromeHeight() + 30 : p.collapsedHeight();
        }

        private void dragTo(int y) {
            List<Integer> shown = visibleIndexes();
            int upper = shown.get(dragHandle), lower = shown.get(dragHandle + 1);
            int total = dragSizes[upper] + dragSizes[lower];
            if (!panels.get(upper).isExpanded() || !panels.get(lower).isExpanded()) {
                /* A collapsed neighbor cannot change, so the next expanded panel does. */
                for (int i = dragHandle; i >= 0; i--)
                    if (panels.get(shown.get(i)).isExpanded()) {
                        upper = shown.get(i);
                        break;
                    }
                for (int i = dragHandle + 1; i < shown.size(); i++)
                    if (panels.get(shown.get(i)).isExpanded()) {
                        lower = shown.get(i);
                        break;
                    }
                if (upper == lower || !panels.get(upper).isExpanded() || !panels.get(lower).isExpanded())
                    return;
                total = dragSizes[upper] + dragSizes[lower];
            }
            int height = Math.max(minimum(upper), Math.min(total - minimum(lower), dragSizes[upper] + y - dragStart));
            sizes[upper] = height;
            sizes[lower] = total - height;
            layoutPanels();
            repaint();
        }

        /** Set how a panel shares extra height. */
        void setStretch(int panel, int factor) {
            stretch[panel] = factor;
        }

        int size(int panel) {
            return sizes[panel];
        }

        void setSizes(int... values) {
            System.arraycopy(values, 0, sizes, 0, sizes.length);
            layoutPanels();
            repaint();
        }

        /** The height left for panels once the handles are taken away. */
        int available() {
            return getHeight() - Math.max(0, visibleIndexes().size() - 1) * HANDLE;
        }

        /** Lay out after a panel collapses, expands, shows or hides. */
        void panelsChanged() {
            layoutPanels();
            repaint();
        }

        public void doLayout() {
            layoutPanels();
        }

        private void layoutPanels() {
            List<Integer> shown = visibleIndexes();
            handles = new int[Math.max(0, shown.size() - 1)];
            if (shown.isEmpty())
                return;
            int available = available();
            int used = 0, stretchers = 0, last = -1;
            for (int i : shown) {
                DataPanel p = panels.get(i);
                if (!p.isExpanded()) {
                    sizes[i] = p.collapsedHeight();
                } else {
                    last = i;
                    if (stretch[i] > 0)
                        stretchers++;
                }
                used += sizes[i];
            }
            /* Extra or missing height goes to the panels that stretch, by their current sizes. */
            int delta = available - used;
            if (delta != 0 && last >= 0) {
                int weight = 0;
                for (int i : shown)
                    if (panels.get(i).isExpanded() && (stretchers == 0 || stretch[i] > 0))
                        weight += Math.max(sizes[i], 1);
                int remaining = delta;
                int lastEligible = -1;
                for (int i : shown) {
                    if (panels.get(i).isExpanded() && (stretchers == 0 || stretch[i] > 0)) {
                        int share = (int) ((long) delta * Math.max(sizes[i], 1) / Math.max(weight, 1));
                        sizes[i] += share;
                        remaining -= share;
                        lastEligible = i;
                    }
                }
                if (lastEligible >= 0)
                    sizes[lastEligible] += remaining;
                /* Keep every panel usable, taking any shortfall from the others. */
                int shortfall = 0;
                for (int i : shown) {
                    int low = minimum(i);
                    if (sizes[i] < low) {
                        shortfall += low - sizes[i];
                        sizes[i] = low;
                    }
                }
                for (int i = shown.size() - 1; i >= 0 && shortfall > 0; i--) {
                    int p = shown.get(i);
                    int spare = sizes[p] - minimum(p);
                    int take = Math.min(spare, shortfall);
                    sizes[p] -= take;
                    shortfall -= take;
                }
            }
            int y = 0, width = getWidth();
            for (int k = 0; k < shown.size(); k++) {
                int i = shown.get(k);
                panels.get(i).setBounds(0, y, width, sizes[i]);
                panels.get(i).revalidate();
                y += sizes[i];
                if (k + 1 < shown.size()) {
                    handles[k] = y;
                    y += HANDLE;
                }
            }
            for (int i = 0; i < panels.size(); i++)
                if (!panels.get(i).isVisible())
                    panels.get(i).setBounds(0, 0, 0, 0);
            lastHeight = getHeight();
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(Theme.t.border);
            for (int handle : handles)
                g2.fill(new RoundRectangle2D.Float(getWidth() / 2f - 18, handle + 3.5f, 36, 3, 3, 3));
            g2.dispose();
        }

        int lastHeight() {
            return lastHeight;
        }
    }

    /** One-line header of the parameter grid. */
    static final class PlainHeaderRenderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private String text = "";
        private boolean strong;

        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                int row, int column) {
            text = value == null ? "" : value.toString();
            GridModels.ParameterModel model = (GridModels.ParameterModel) table.getModel();
            strong = column < model.getColumnCount() && model.isGroupEnd(column);
            return this;
        }

        public Dimension getPreferredSize() {
            return new Dimension(40, getFontMetrics(Theme.uiFont()).getHeight() + 6);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.t.header);
            g2.fillRect(0, 0, w, h);
            Theme.smooth(g2);
            g2.setFont(Theme.uiFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.t.text);
            g2.drawString(Theme.elide(text, fm, w - 12), 8, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.setColor(Theme.t.border);
            g2.fillRect(0, h - 1, w, 1);
            g2.setColor(strong ? Theme.t.border : Theme.t.grid);
            g2.fillRect(w - 1, 0, 1, h);
        }
    }

    /** Two-line column header: a bold name over a type badge, shape chip and units. */
    static final class TwoLineHeaderRenderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private static final int GAP = 4;
        private final CellTable table;
        private String title = "";
        private Definition definition;
        private String shape;
        private boolean highlighted;

        TwoLineHeaderRenderer(CellTable table) {
            this.table = table;
        }

        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row,
                int column) {
            title = value == null ? "" : value.toString();
            int index = t.getColumnModel().getColumn(column).getModelIndex();
            java.util.List<Definition> definitions = table.grid.definitions();
            definition = index < definitions.size() ? definitions.get(index) : null;
            highlighted = table.columnHasSelection(column);
            shape = null;
            if (table.grid instanceof GridModels.ArrayModel && definition != null) {
                Document.Page page = table.grid.currentPage();
                if (page != null && index < page.arrays.length)
                    shape = Document.shapeText(page.arrays[index].dims);
            }
            return this;
        }

        private Font titleFont() {
            return Theme.bold(Theme.uiFont());
        }

        private Font detailFont() {
            return Theme.small(Theme.uiFont());
        }

        private String units() {
            return definition == null || definition.units == null ? "" : definition.units;
        }

        private int detailWidth() {
            if (definition == null)
                return 0;
            Font badge = Theme.badgeFont();
            FontMetrics fm = getFontMetrics(badge);
            int width = Theme.badgeSize(badge, definition.typeName(), fm).width;
            if (shape != null && !shape.isEmpty())
                width += GAP + Theme.badgeSize(badge, shape, fm).width;
            if (!units().isEmpty())
                width += GAP + getFontMetrics(detailFont()).stringWidth(units());
            return width;
        }

        private int detailHeight() {
            Font badge = Theme.badgeFont();
            return Math.max(getFontMetrics(detailFont()).getHeight(),
                    Theme.badgeSize(badge, "x", getFontMetrics(badge)).height);
        }

        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(titleFont());
            return new Dimension(Math.max(fm.stringWidth(title), detailWidth()) + 22, fm.getHeight() + detailHeight() + 14);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            g2.setColor(highlighted ? Theme.t.accentSoft : Theme.t.header);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.t.grid);
            g2.fillRect(w - 1, 0, 1, h);
            g2.setColor(Theme.t.border);
            g2.fillRect(0, h - 1, w, 1);
            if (highlighted) {
                g2.setColor(Theme.t.accent);
                g2.fillRect(0, h - 2, w, 2);
            }
            Theme.smooth(g2);
            boolean right = definition != null && GridModels.rightAligned(definition.type);
            int left = 10, width = w - 20;
            FontMetrics fm = g2.getFontMetrics(titleFont());
            int detail = detailHeight();
            int top = 2 + (h - 4 - fm.getHeight() - GAP - detail) / 2;
            g2.setFont(titleFont());
            g2.setColor(highlighted ? Theme.t.accentText : Theme.t.text);
            String shown = Theme.elide(title, fm, Math.max(0, width));
            int x = right ? left + width - fm.stringWidth(shown) : left;
            g2.drawString(shown, x, top + fm.getAscent());
            top += fm.getHeight() + GAP;
            if (definition != null) {
                x = right ? Math.max(left, left + width - detailWidth()) : left;
                Font badge = Theme.badgeFont();
                FontMetrics bm = g2.getFontMetrics(badge);
                Color[] colors = Theme.t.badgeColors(definition.type);
                String type = definition.typeName();
                int y = top + (detail - Theme.badgeSize(badge, type, bm).height) / 2;
                x += Theme.paintBadge(g2, x, y, type, badge, colors[0], colors[1], null) + GAP;
                if (shape != null && !shape.isEmpty())
                    x += Theme.paintBadge(g2, x, y, shape, badge, Theme.t.surface, Theme.t.muted, Theme.t.border) + GAP;
                if (!units().isEmpty() && x < left + width) {
                    g2.setFont(detailFont());
                    g2.setColor(Theme.t.muted);
                    FontMetrics um = g2.getFontMetrics();
                    g2.drawString(Theme.elide(units(), um, left + width - x), x,
                            top + (detail + um.getAscent() - um.getDescent()) / 2);
                }
            }
            g2.dispose();
        }
    }

    /** A column header whose band and rule continue past the last column. */
    static final class BandHeader extends JTableHeader {
        private static final long serialVersionUID = 1L;

        BandHeader(TableColumnModel model) {
            super(model);
            setOpaque(false);
        }

        protected void paintComponent(Graphics g) {
            g.setColor(Theme.t.header);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Theme.t.border);
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
            super.paintComponent(g);
        }
    }

    /** Row numbers beside a table, with a click selecting a row. */
    static final class RowHeader extends JComponent {
        private static final long serialVersionUID = 1L;

        interface MenuListener {
            void rowMenu(CellTable table, MouseEvent event, int row);
        }

        private final CellTable table;

        RowHeader(final CellTable table, final MenuListener menu) {
            this.table = table;
            setFont(Theme.tableFont());
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                private int anchor = -1;

                public void mousePressed(MouseEvent e) {
                    int row = table.rowAtPoint(new Point(0, e.getY()));
                    if (SwingUtilities.isRightMouseButton(e)) {
                        if (menu != null)
                            menu.rowMenu(table, e, row);
                        return;
                    }
                    if (row < 0 || !SwingUtilities.isLeftMouseButton(e))
                        return;
                    boolean shift = e.isShiftDown() && anchor >= 0;
                    if (!shift)
                        anchor = row;
                    table.selectRowCells(row, e.isControlDown(), shift);
                }

                public void mouseDragged(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || anchor < 0)
                        return;
                    int row = table.rowAtPoint(new Point(0, e.getY()));
                    if (row >= 0)
                        table.selectRowCells(row, false, true);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private int numberOf(int row) {
            return table.grid instanceof GridModels.ColumnModel
                    ? ((GridModels.ColumnModel) table.grid).pageRow(row) + 1
                    : row + 1;
        }

        public Dimension getPreferredSize() {
            int largest = table.grid instanceof GridModels.ColumnModel
                    ? ((GridModels.ColumnModel) table.grid).pageRowCount()
                    : table.getRowCount();
            int digits = Math.max(1, String.valueOf(Math.max(largest, 1)).length());
            FontMetrics fm = getFontMetrics(getFont());
            StringBuilder sample = new StringBuilder();
            for (int i = 0; i < digits; i++)
                sample.append('0');
            return new Dimension(fm.stringWidth(sample.toString()) + 18, table.getPreferredSize().height);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Rectangle clip = g2.getClipBounds();
            g2.setColor(Theme.t.header);
            g2.fillRect(clip.x, clip.y, clip.width, clip.height);
            int rowHeight = table.getRowHeight();
            int first = Math.max(0, clip.y / rowHeight);
            int last = Math.min(table.getRowCount() - 1, (clip.y + clip.height) / rowHeight);
            Theme.smooth(g2);
            FontMetrics fm = g2.getFontMetrics(getFont());
            Font bold = Theme.bold(getFont());
            int w = getWidth();
            for (int row = first; row <= last; row++) {
                int y = row * rowHeight;
                boolean selected = table.rowHasSelection(row);
                g2.setFont(selected ? bold : getFont());
                g2.setColor(selected ? Theme.t.accentText : Theme.t.muted);
                String text = String.valueOf(numberOf(row));
                g2.drawString(text, w - 8 - g2.getFontMetrics().stringWidth(text),
                        y + (rowHeight + fm.getAscent() - fm.getDescent()) / 2);
                g2.setColor(Theme.t.grid);
                g2.fillRect(0, y + rowHeight - 1, w, 1);
            }
            g2.setColor(Theme.t.border);
            g2.fillRect(w - 1, clip.y, 1, clip.height);
        }
    }

    /** Fill the empty corner above row numbers. */
    static JComponent corner() {
        return new JComponent() {
            private static final long serialVersionUID = 1L;

            protected void paintComponent(Graphics g) {
                g.setColor(Theme.t.header);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.t.border);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
    }
}
