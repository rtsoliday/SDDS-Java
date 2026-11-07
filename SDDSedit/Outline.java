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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The sidebar: a summary of the file and a filterable list of its definitions. */
final class Outline extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String[] GROUPS = { "Parameters", "Columns", "Arrays" };

    /** Told when the user picks a definition. */
    interface Listener {
        void outlineNavigate(int kind, int index);
    }

    private static final class Row {
        int kind;
        int index = -1;
        String name;
        String type;
        Color dot;
    }

    private final Listener listener;
    private final JLabel fileIcon = new JLabel();
    private final JLabel fileName = Flat.plainText(new JLabel());
    private final JLabel description = Flat.plainText(new JLabel());
    private final JLabel pagesValue = new JLabel("-");
    private final JLabel rowsValue = new JLabel("-");
    private final JLabel versionValue = new JLabel("-");
    private final Flat.SearchField filter = new Flat.SearchField("Filter names", Theme.t.surface);
    private final RowList list = new RowList();
    private final boolean[] expanded = { true, true, true };
    private final List<List<Definition>> definitions = new ArrayList<List<Definition>>();
    private final List<Row> rows = new ArrayList<Row>();
    private int selectedKind = -1, selectedIndex = -1;

    Outline(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        setBackground(Theme.t.chrome);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.t.border));
        setMinimumSize(new Dimension(170, 100));
        setPreferredSize(new Dimension(230, 100));
        for (int i = 0; i < 3; i++)
            definitions.add(new ArrayList<Definition>());
        add(buildSummary(), BorderLayout.NORTH);
        JPanel lower = new JPanel(new BorderLayout());
        lower.setOpaque(false);
        JPanel filterBox = new JPanel(new BorderLayout());
        filterBox.setOpaque(false);
        filterBox.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        Flat.keepKeys(filter);
        filter.setToolTipText("Show only parameters, columns and arrays whose names contain this text");
        filter.setPreferredSize(new Dimension(100, 28));
        filterBox.add(filter, BorderLayout.CENTER);
        lower.add(filterBox, BorderLayout.NORTH);
        JScrollPane pane = Flat.scrollPane(new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        pane.getViewport().setBackground(Theme.t.chrome);
        lower.add(pane, BorderLayout.CENTER);
        add(lower, BorderLayout.CENTER);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                rebuildRows();
            }

            public void removeUpdate(DocumentEvent e) {
                rebuildRows();
            }

            public void changedUpdate(DocumentEvent e) {
                rebuildRows();
            }
        });
    }

    private JComponent buildSummary() {
        JPanel summary = new JPanel();
        summary.setOpaque(false);
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.t.border),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JPanel fileRow = new JPanel(new BorderLayout(10, 0));
        fileRow.setOpaque(false);
        fileRow.setAlignmentX(LEFT_ALIGNMENT);
        Flat.Card tile = new Flat.Card(new GridBagLayout(), Theme.t.accentSoft, null, 16);
        Dimension tileSize = new Dimension(32, 32);
        tile.setPreferredSize(tileSize);
        tile.setMinimumSize(tileSize);
        fileIcon.setIcon(Icons.FILE.icon(18, Theme.t.accent));
        tile.add(fileIcon);
        JPanel tileBox = new JPanel(new GridBagLayout());
        tileBox.setOpaque(false);
        tileBox.add(tile);
        fileRow.add(tileBox, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(0, 1));
        text.setOpaque(false);
        fileName.setFont(Theme.bold(Theme.uiFont()));
        fileName.setForeground(Theme.t.text);
        description.setFont(Theme.small(Theme.uiFont()));
        description.setForeground(Theme.t.muted);
        fileName.setMinimumSize(new Dimension(0, 0));
        description.setMinimumSize(new Dimension(0, 0));
        text.add(fileName);
        text.add(description);
        fileRow.add(text, BorderLayout.CENTER);
        fileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        summary.add(fileRow);
        summary.add(Box.createVerticalStrut(10));
        JPanel stats = new JPanel(new GridLayout(1, 3, 6, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(LEFT_ALIGNMENT);
        stats.add(statBox(pagesValue, "Pages"));
        stats.add(statBox(rowsValue, "Rows"));
        stats.add(statBox(versionValue, "Version"));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        summary.add(stats);
        return summary;
    }

    private JComponent statBox(JLabel value, String caption) {
        Flat.Card box = new Flat.Card(new GridLayout(0, 1), Theme.t.surface, Theme.t.border, 12);
        box.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        value.setFont(Theme.bold(Theme.uiFont()));
        value.setForeground(Theme.t.text);
        JLabel label = new JLabel(caption);
        label.setFont(Theme.small(Theme.uiFont()));
        label.setForeground(Theme.t.muted);
        box.add(value);
        box.add(label);
        return box;
    }

    /** Refresh the summary numbers. */
    void updateSummary(Document doc, int rowCount) {
        boolean loaded = doc != null && !doc.pages.isEmpty();
        String file = doc == null ? "No file open" : doc.path == null ? "Untitled" : new File(doc.path).getName();
        fileName.setText(file);
        fileName.setToolTipText(doc == null || doc.path == null ? null : Flat.plainTip(doc.path));
        String text = "";
        if (doc != null && doc.description != null && !doc.description.isEmpty())
            text = doc.description;
        else if (doc != null && doc.contents != null && !doc.contents.isEmpty())
            text = doc.contents;
        description.setText(text);
        description.setVisible(!text.isEmpty());
        description.setToolTipText(doc != null && doc.contents != null && !doc.contents.isEmpty()
                ? "Contents: " + doc.contents
                : null);
        pagesValue.setText(loaded ? String.format("%,d", doc.pages.size()) : "-");
        rowsValue.setText(loaded && !doc.columns.isEmpty() ? String.format("%,d", rowCount) : "-");
        versionValue.setText(doc != null ? "SDDS" + doc.version : "-");
    }

    /** Refresh the list of definitions. */
    void rebuild(Document doc) {
        for (List<Definition> list : definitions)
            list.clear();
        if (doc != null) {
            definitions.get(0).addAll(doc.parameters);
            definitions.get(1).addAll(doc.columns);
            definitions.get(2).addAll(doc.arrays);
        }
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        String wanted = filter.getText().toLowerCase(java.util.Locale.ROOT);
        for (int kind = 0; kind < 3; kind++) {
            Row group = new Row();
            group.kind = kind;
            group.name = GROUPS[kind];
            group.type = String.format("%,d", definitions.get(kind).size());
            rows.add(group);
            if (!expanded[kind])
                continue;
            List<Definition> list = definitions.get(kind);
            for (int i = 0; i < list.size(); i++) {
                Definition d = list.get(i);
                if (!wanted.isEmpty() && !d.name.toLowerCase(java.util.Locale.ROOT).contains(wanted))
                    continue;
                Row row = new Row();
                row.kind = kind;
                row.index = i;
                row.name = d.name;
                row.type = d.typeName();
                row.dot = Theme.t.markerColor(d.type);
                rows.add(row);
            }
        }
        list.revalidate();
        list.repaint();
    }

    /** Highlight the definition holding the current cell. */
    void select(int kind, int index) {
        selectedKind = kind;
        selectedIndex = index;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind == kind && row.index == index)
                list.scrollRectToVisible(new Rectangle(0, i * RowList.ROW, 10, RowList.ROW));
        }
        list.repaint();
    }

    private final class RowList extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;
        static final int ROW = 25;
        private int hover = -1;

        RowList() {
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    int i = e.getY() / ROW;
                    if (i < 0 || i >= rows.size() || !SwingUtilities.isLeftMouseButton(e))
                        return;
                    Row row = rows.get(i);
                    if (row.index < 0) {
                        expanded[row.kind] = !expanded[row.kind];
                        rebuildRows();
                    } else {
                        listener.outlineNavigate(row.kind, row.index);
                    }
                }

                public void mouseMoved(MouseEvent e) {
                    int i = e.getY() / ROW;
                    if (i != hover) {
                        hover = i;
                        repaint();
                    }
                    Row row = i >= 0 && i < rows.size() ? rows.get(i) : null;
                    setToolTipText(row != null && row.index >= 0 ? Flat.plainTip(row.name) : null);
                }

                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        public Dimension getPreferredSize() {
            return new Dimension(100, rows.size() * ROW);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return ROW;
        }

        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > rows.size() * ROW;
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Theme.t.chrome);
            g2.fillRect(0, 0, getWidth(), getHeight());
            Theme.smooth(g2);
            Font plain = Theme.uiFont(), bold = Theme.bold(plain);
            int w = getWidth();
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int y = i * ROW;
                boolean selected = row.index >= 0 && row.kind == selectedKind && row.index == selectedIndex;
                if (selected) {
                    g2.setColor(Theme.t.accentSoft);
                    g2.fillRect(0, y, w, ROW);
                } else if (i == hover) {
                    g2.setColor(Theme.t.hover);
                    g2.fillRect(0, y, w, ROW);
                }
                g2.setFont(row.index < 0 ? bold : plain);
                FontMetrics fm = g2.getFontMetrics();
                int base = y + (ROW + fm.getAscent() - fm.getDescent()) / 2;
                int typeWidth = fm.stringWidth(row.type);
                int textLeft;
                if (row.index < 0) {
                    (expanded[row.kind] ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT).icon(10, Theme.t.muted).paintIcon(this,
                            g2, 6, y + (ROW - 10) / 2);
                    textLeft = 21;
                } else {
                    g2.setColor(row.dot);
                    g2.fill(new RoundRectangle2D.Float(22.5f, y + (ROW - 8) / 2 + 0.5f, 7, 7, 4, 4));
                    textLeft = 37;
                }
                g2.setColor(selected ? Theme.t.accentText : Theme.t.text);
                g2.drawString(Theme.elide(row.name, fm, Math.max(0, w - textLeft - typeWidth - 16)), textLeft, base);
                g2.setFont(plain);
                g2.setColor(selected ? Theme.t.accentText : Theme.t.muted);
                g2.drawString(row.type, w - 8 - g2.getFontMetrics().stringWidth(row.type), base);
            }
            g2.dispose();
        }
    }
}
