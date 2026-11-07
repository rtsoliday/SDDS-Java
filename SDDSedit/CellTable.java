/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;

import SDDS.java.SDDS.SDDSEnumDefinition;

/**
 * A table with the editing and selection of the Qt editor's tables. Selections can be
 * disconnected with Ctrl-click and ranges with Shift or a drag. A plain click edits a
 * cell when the button is released. A right-click keeps the selection.
 */
final class CellTable extends JTable {
    private static final long serialVersionUID = 1L;

    /** What the main window hears from a table. */
    interface Listener {
        void cellMenu(CellTable table, MouseEvent event, int row, int column);

        /** A double-click on a read-only cell. */
        void labelDoubleClicked(CellTable table, int row, int column);

        void currentChanged(CellTable table);

        void selectionChanged(CellTable table);

        void tableFocused(CellTable table);

        /** Text with tabs or newlines was pasted into a cell's editor. */
        void pasteIntoTable(CellTable table);
    }

    /** Maps a click on a read-only label to the value cell it stands for. */
    interface SelectionMapper {
        /** @return {row, column} of the cell to select, or <code>null</code> to select nothing */
        int[] map(int row, int column);
    }

    private static final class Range {
        final int row0, row1, column0, column1;
        final boolean add;

        Range(int row0, int row1, int column0, int column1, boolean add) {
            this.row0 = Math.min(row0, row1);
            this.row1 = Math.max(row0, row1);
            this.column0 = Math.min(column0, column1);
            this.column1 = Math.max(column0, column1);
            this.add = add;
        }
    }

    final GridModels.Base grid;
    private final Listener listener;
    private SelectionMapper mapper;
    private final List<Range> ranges = new ArrayList<Range>();
    private int anchorRow = -1, anchorColumn = -1;
    private boolean anchorAdds = true;

    private int pressRow = -1, pressColumn = -1;
    private Point pressPoint;
    private boolean dragged;
    private boolean labelClick;
    private final CellEditorImpl editor = new CellEditorImpl();
    private final Renderer renderer = new Renderer();

    CellTable(GridModels.Base model, Listener listener) {
        super(model);
        this.grid = model;
        this.listener = listener;
        setFont(Theme.tableFont());
        FontMetrics fm = getFontMetrics(getFont());
        setRowHeight(fm.getHeight() + 7);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setAutoResizeMode(AUTO_RESIZE_OFF);
        setFillsViewportHeight(true);
        setBackground(Theme.t.surface);
        setForeground(Theme.t.text);
        setSelectionBackground(Theme.t.accentSoft);
        setSelectionForeground(Theme.t.text);
        setCellSelectionEnabled(true);
        setSurrendersFocusOnKeystroke(true);
        setTransferHandler(null);
        setDragEnabled(false);
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        setDefaultRenderer(Object.class, renderer);
        setDefaultEditor(Object.class, editor);
        installKeys();
        installMouse();
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                CellTable.this.listener.tableFocused(CellTable.this);
                repaint();
            }

            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    void setSelectionMapper(SelectionMapper mapper) {
        this.mapper = mapper;
    }

    private void installKeys() {
        InputMap keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        for (int code : new int[] { KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_X, KeyEvent.VK_A }) {
            keys.remove(KeyStroke.getKeyStroke(code, InputEvent.CTRL_DOWN_MASK));
            keys.put(KeyStroke.getKeyStroke(code, InputEvent.CTRL_DOWN_MASK), "none");
        }
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "selectAllCells");
        getActionMap().put("selectAllCells", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                selectAllCells();
            }
        });
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "none");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeTable");
        getActionMap().put("escapeTable", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                if (isEditing())
                    removeEditor();
                else
                    clearCellSelection();
            }
        });
    }

    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        /* Only keys that type something may start an edit. */
        boolean plain = !e.isControlDown() && !e.isAltDown() && !e.isMetaDown()
                && e.getKeyChar() != KeyEvent.CHAR_UNDEFINED && e.getKeyCode() != KeyEvent.VK_DELETE
                && e.getKeyCode() != KeyEvent.VK_ENTER && e.getKeyCode() != KeyEvent.VK_ESCAPE
                && e.getKeyCode() != KeyEvent.VK_TAB;
        if (!isEditing())
            putClientProperty("JTable.autoStartsEdit", plain ? Boolean.TRUE : Boolean.FALSE);
        return super.processKeyBinding(ks, e, condition, pressed);
    }

    private void installMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int row = rowAtPoint(e.getPoint()), column = columnAtPoint(e.getPoint());
                if (SwingUtilities.isRightMouseButton(e)) {
                    finishEditing();
                    if (row >= 0 && column >= 0) {
                        int[] cell = mapped(row, column);
                        if (cell != null) {
                            if (isCellSelected(cell[0], cell[1]))
                                moveLead(cell[0], cell[1]);
                            else
                                changeSelection(cell[0], cell[1], false, false);
                        }
                    }
                    listener.cellMenu(CellTable.this, e, row, column);
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressRow = row;
                    pressColumn = column;
                    pressPoint = e.getPoint();
                    dragged = false;
                    labelClick = row >= 0 && column >= 0 && mapper != null && !isCellEditable(row, column);
                    requestFocusInWindow();
                }
            }

            public void mouseDragged(MouseEvent e) {
                if (pressPoint != null && !dragged
                        && Math.abs(e.getX() - pressPoint.x) + Math.abs(e.getY() - pressPoint.y) >= 5)
                    dragged = true;
            }

            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || pressPoint == null)
                    return;
                pressPoint = null;
                boolean noModifiers = (e.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
                        | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0;
                if (dragged || !noModifiers || pressRow < 0 || pressColumn < 0 || !isCellEditable(pressRow, pressColumn))
                    return;
                startEditing(pressRow, pressColumn);
            }

            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && labelClick
                        && pressRow >= 0 && pressColumn >= 0)
                    listener.labelDoubleClicked(CellTable.this, pressRow, pressColumn);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Open the editor of a cell, with all of its text selected. */
    void startEditing(int row, int column) {
        if (editCellAt(row, column, null)) {
            Component c = getEditorComponent();
            if (c != null)
                c.requestFocusInWindow();
        }
    }

    public boolean editCellAt(int row, int column, EventObject event) {
        /* Clicks start an edit from the listener above, when the button is released. */
        if (event instanceof MouseEvent)
            return false;
        boolean started = super.editCellAt(row, column, event);
        if (started && event instanceof KeyEvent)
            editor.selectAll();
        return started;
    }

    /** Open the editor of a cell with part of its text selected. */
    void editAndSelect(int row, int column, int start, int length) {
        startEditing(row, column);
        if (getEditorComponent() instanceof JTextField)
            ((JTextField) getEditorComponent()).select(start, start + length);
    }

    /** Commit an open editor. */
    void finishEditing() {
        if (isEditing())
            getCellEditor().stopCellEditing();
    }

    public TableCellEditor getCellEditor(int row, int column) {
        return editor;
    }

    public TableCellRenderer getCellRenderer(int row, int column) {
        return renderer;
    }

    public void tableChanged(TableModelEvent e) {
        if (e != null && e.getFirstRow() == TableModelEvent.HEADER_ROW && ranges != null)
            clearRanges();
        super.tableChanged(e);
    }

    /* ---- selection ---- */

    private int[] mapped(int row, int column) {
        return mapper == null ? new int[] { row, column } : mapper.map(row, column);
    }

    public void changeSelection(int row, int column, boolean toggle, boolean extend) {
        int[] cell = mapped(row, column);
        if (cell == null)
            return;
        row = cell[0];
        column = cell[1];
        if (toggle && !extend) {
            anchorAdds = !isCellSelected(row, column);
            ranges.add(new Range(row, row, column, column, anchorAdds));
            anchorRow = row;
            anchorColumn = column;
        } else if (extend && anchorRow >= 0) {
            if (!ranges.isEmpty())
                ranges.remove(ranges.size() - 1);
            ranges.add(new Range(anchorRow, row, anchorColumn, column, anchorAdds));
        } else {
            ranges.clear();
            anchorAdds = true;
            ranges.add(new Range(row, row, column, column, true));
            anchorRow = row;
            anchorColumn = column;
        }
        moveLead(row, column);
        if (getAutoscrolls()) {
            Rectangle rect = getCellRect(row, column, false);
            scrollRectToVisible(rect);
        }
        listener.selectionChanged(this);
        repaint();
    }

    private void moveLead(int row, int column) {
        super.changeSelection(row, column, false, false);
        listener.currentChanged(this);
    }

    /** Select a whole column, as a click on its header does. */
    void selectColumnCells(int column, boolean toggle, boolean extend) {
        selectCells(0, Integer.MAX_VALUE, column, column, toggle, extend, true);
    }

    /** Select a whole row, as a click on its number does. */
    void selectRowCells(int row, boolean toggle, boolean extend) {
        selectCells(row, row, 0, Integer.MAX_VALUE, toggle, extend, false);
    }

    private void selectCells(int r0, int r1, int c0, int c1, boolean toggle, boolean extend, boolean columnWise) {
        finishEditing();
        int leadRow = r0 == 0 && r1 == Integer.MAX_VALUE ? Math.max(getCurrentRow(), 0) : r0;
        int leadColumn = c0 == 0 && c1 == Integer.MAX_VALUE ? Math.max(getCurrentColumn(), 0) : c0;
        if (extend && anchorRow >= 0) {
            if (!ranges.isEmpty())
                ranges.remove(ranges.size() - 1);
            if (columnWise)
                ranges.add(new Range(0, Integer.MAX_VALUE, anchorColumn, c0, anchorAdds));
            else
                ranges.add(new Range(anchorRow, r0, 0, Integer.MAX_VALUE, anchorAdds));
        } else {
            /* A Ctrl-click on a whole row or column that is already selected removes it. */
            boolean add = !toggle || !(columnWise ? columnFullySelected(c0) : rowFullySelected(r0));
            if (!toggle)
                ranges.clear();
            ranges.add(new Range(r0, r1, c0, c1, add));
            anchorRow = leadRow;
            anchorColumn = leadColumn;
            anchorAdds = add;
        }
        if (getRowCount() > 0 && leadRow < getRowCount() && leadColumn < getColumnCount())
            moveLead(leadRow, leadColumn);
        requestFocusInWindow();
        listener.selectionChanged(this);
        repaint();
    }

    /** Select every cell, or every value cell of the parameter grid. */
    void selectAllCells() {
        finishEditing();
        if (mapper == null) {
            ranges.clear();
            ranges.add(new Range(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, true));
            listener.selectionChanged(this);
            repaint();
            return;
        }
        boolean first = true;
        for (int row = 0; row < getRowCount(); row++) {
            for (int column = 0; column < getColumnCount(); column++) {
                if (isCellEditable(row, column)) {
                    changeSelection(row, column, !first, false);
                    first = false;
                }
            }
        }
    }

    /** True if every cell of a row is selected. */
    boolean rowFullySelected(int row) {
        for (int column = 0; column < getColumnCount(); column++)
            if (!isCellSelected(row, column))
                return false;
        return getColumnCount() > 0;
    }

    /** True if every cell of a column is selected. */
    boolean columnFullySelected(int column) {
        for (int row = 0; row < getRowCount(); row++)
            if (!isCellSelected(row, column))
                return false;
        return getRowCount() > 0;
    }

    /**
     * Keep the selection and current cell on the same rows after rows were hidden or shown.
     *
     * @param map        the new row of each old row, or -1 for one now hidden; order is kept
     * @param lead       the current cell's old row, or -1
     * @param leadColumn the current cell's column, or -1
     */
    void remapRows(int[] map, int lead, int leadColumn) {
        List<Range> kept = new ArrayList<Range>();
        for (Range r : ranges) {
            if (r.row0 == 0 && r.row1 == Integer.MAX_VALUE) {
                kept.add(r);
                continue;
            }
            int start = -1, end = -1;
            for (int row = r.row0; row <= r.row1 && row < map.length; row++) {
                int to = map[row];
                if (to < 0)
                    continue;
                if (start >= 0 && to == end + 1) {
                    end = to;
                    continue;
                }
                if (start >= 0)
                    kept.add(new Range(start, end, r.column0, r.column1, r.add));
                start = end = to;
            }
            if (start >= 0)
                kept.add(new Range(start, end, r.column0, r.column1, r.add));
        }
        ranges.clear();
        ranges.addAll(kept);
        anchorRow = anchorRow >= 0 && anchorRow < map.length ? map[anchorRow] : -1;
        int row = lead >= 0 && lead < map.length ? map[lead] : -1;
        if (row >= 0 && leadColumn >= 0)
            moveLead(row, leadColumn);
        listener.selectionChanged(this);
        repaint();
    }

    /** Set the selection to exactly one cell and scroll to it. */
    void selectCell(int row, int column) {
        finishEditing();
        changeSelection(row, column, false, false);
    }

    /** Set the selection to a rectangle of cells. */
    void selectRectangle(int row0, int row1, int column0, int column1) {
        ranges.clear();
        ranges.add(new Range(row0, row1, column0, column1, true));
        anchorRow = row0;
        anchorColumn = column0;
        anchorAdds = true;
        if (row1 < getRowCount() && column1 < getColumnCount())
            moveLead(row1, column1);
        listener.selectionChanged(this);
        repaint();
    }

    private void clearRanges() {
        ranges.clear();
        anchorRow = anchorColumn = -1;
        anchorAdds = true;
    }

    /** Clear the selection and the current cell. */
    void clearCellSelection() {
        clearRanges();
        clearSelection();
        getSelectionModel().setAnchorSelectionIndex(-1);
        getSelectionModel().setLeadSelectionIndex(-1);
        getColumnModel().getSelectionModel().setAnchorSelectionIndex(-1);
        getColumnModel().getSelectionModel().setLeadSelectionIndex(-1);
        listener.currentChanged(this);
        listener.selectionChanged(this);
        repaint();
    }

    public boolean isCellSelected(int row, int column) {
        for (int i = ranges.size() - 1; i >= 0; i--) {
            Range r = ranges.get(i);
            if (row >= r.row0 && row <= r.row1 && column >= r.column0 && column <= r.column1)
                return r.add;
        }
        return false;
    }

    boolean columnHasSelection(int column) {
        for (Range r : ranges)
            if (r.add && column >= r.column0 && column <= r.column1 && getRowCount() > r.row0)
                return true;
        return false;
    }

    boolean rowHasSelection(int row) {
        for (Range r : ranges)
            if (r.add && row >= r.row0 && row <= r.row1)
                return true;
        return false;
    }

    /** True if any cell is selected. */
    boolean hasCellSelection() {
        return !selectedCells().isEmpty();
    }

    /** Columns holding a selected cell, in increasing order. */
    TreeSet<Integer> selectedColumnSet() {
        TreeSet<Integer> columns = new TreeSet<Integer>();
        for (int[] cell : selectedCells())
            columns.add(cell[1]);
        return columns;
    }

    /** Selected cells as {row, column}, sorted by row and then column. */
    List<int[]> selectedCells() {
        List<int[]> cells = new ArrayList<int[]>();
        if (ranges.isEmpty() || getRowCount() == 0 || getColumnCount() == 0)
            return cells;
        int firstRow = Integer.MAX_VALUE, lastRow = -1, firstColumn = Integer.MAX_VALUE, lastColumn = -1;
        for (Range r : ranges) {
            firstRow = Math.min(firstRow, r.row0);
            lastRow = Math.max(lastRow, r.row1);
            firstColumn = Math.min(firstColumn, r.column0);
            lastColumn = Math.max(lastColumn, r.column1);
        }
        lastRow = Math.min(lastRow, getRowCount() - 1);
        lastColumn = Math.min(lastColumn, getColumnCount() - 1);
        for (int row = firstRow; row <= lastRow; row++)
            for (int column = firstColumn; column <= lastColumn; column++)
                if (isCellSelected(row, column))
                    cells.add(new int[] { row, column });
        return cells;
    }

    /** The current cell's row, or -1. */
    int getCurrentRow() {
        int row = getSelectionModel().getLeadSelectionIndex();
        return row >= getRowCount() ? -1 : row;
    }

    /** The current cell's column, or -1. */
    int getCurrentColumn() {
        int column = getColumnModel().getSelectionModel().getLeadSelectionIndex();
        return column >= getColumnCount() ? -1 : column;
    }

    /* ---- sizing ---- */

    /** Set column widths from the first rows and the header, as a bounded sizing pass. */
    void sizeColumnsToContents() {
        JTableHeader header = getTableHeader();
        int rows = Math.min(getRowCount(), 200);
        FontMetrics fm = getFontMetrics(getFont());
        for (int c = 0; c < getColumnCount(); c++) {
            int width = 0;
            if (header != null) {
                Component h = header.getDefaultRenderer().getTableCellRendererComponent(this,
                        getColumnModel().getColumn(c).getHeaderValue(), false, false, -1, c);
                width = h.getPreferredSize().width;
            }
            for (int r = 0; r < rows; r++)
                width = Math.max(width, fm.stringWidth(String.valueOf(getValueAt(r, c))) + 12);
            TableColumn column = getColumnModel().getColumn(c);
            column.setPreferredWidth(Math.min(Math.max(width, 44), 640));
            column.setWidth(column.getPreferredWidth());
        }
    }

    /* ---- painting ---- */

    /** Paints a cell with the Qt editor's badges, hatching and grid lines. */
    private final class Renderer extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private String text = "";
        private int row, column;
        private boolean selected, focused, padding, right, label;
        private Font textFont;
        private Color textColor;
        private int badge;

        Renderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            this.row = row;
            this.column = column;
            text = value == null ? "" : value.toString();
            selected = isSelected;
            focused = row == getCurrentRow() && column == getCurrentColumn() && CellTable.this.isFocusOwner();
            padding = grid.isPadding(row, column);
            badge = 0;
            label = false;
            textFont = getFont();
            textColor = Theme.t.text;
            Definition d = grid.definition(row, column);
            right = d != null && GridModels.rightAligned(d.type);
            if (grid instanceof GridModels.ParameterModel) {
                int field = ((GridModels.ParameterModel) grid).fieldAt(column);
                if (d != null && field != GridModels.ParameterModel.VALUE) {
                    right = false;
                    label = true;
                    textFont = Theme.uiFont();
                    if (field == GridModels.ParameterModel.TYPE)
                        badge = d.type;
                    else if (field != GridModels.ParameterModel.NAME)
                        textColor = Theme.t.muted;
                }
            }
            return this;
        }

        public Font getFont() {
            return CellTable.this.getFont();
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth(), h = getHeight();
            g2.setColor(selected ? Theme.t.accentSoft : row % 2 == 1 ? Theme.t.zebra : Theme.t.surface);
            g2.fillRect(0, 0, w, h);
            if (padding) {
                Theme.paintHatch(g2, new Rectangle(0, 0, w, h), Theme.t.hatch, 0, 0);
            } else if (badge != 0) {
                Color[] colors = Theme.t.badgeColors(badge);
                Font font = Theme.badgeFont();
                FontMetrics fm = g2.getFontMetrics(font);
                Dimension size = Theme.badgeSize(font, text, fm);
                Theme.paintBadge(g2, 8, (h - size.height) / 2 + 1, text, font, colors[0], colors[1], null);
            } else if (!text.isEmpty()) {
                Theme.smooth(g2);
                g2.setFont(textFont);
                FontMetrics fm = g2.getFontMetrics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                String shown = Theme.elide(text, fm, w - 8);
                int x = right ? w - 4 - fm.stringWidth(shown) : 4;
                g2.setColor(textColor);
                g2.drawString(shown, x, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            if (focused) {
                g2.setColor(Theme.t.accent);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(1, 1, w - 2, h - 2);
            } else {
                g2.setColor(Theme.t.grid);
                g2.fillRect(0, h - 1, w, 1);
                boolean strong = grid instanceof GridModels.ParameterModel
                        && ((GridModels.ParameterModel) grid).isGroupEnd(column);
                g2.setColor(strong ? Theme.t.border : Theme.t.gridSoft);
                g2.fillRect(w - 1, 0, 1, h);
            }
        }

        /* Rendering stamps are only painted, so these need nothing. */
        public void invalidate() {
        }

        public void validate() {
        }

        public void revalidate() {
        }

        public void repaint(long tm, int x, int y, int width, int height) {
        }

        public void repaint() {
        }
    }

    /** Edits a cell with a text field, or a combo box of labels for an enum. */
    private final class CellEditorImpl extends AbstractCellEditor implements TableCellEditor {
        private static final long serialVersionUID = 1L;
        private final EditorField field = new EditorField();
        private final JComboBox<String> combo = new JComboBox<String>();
        private String[] labels = new String[0];
        private boolean usingCombo;
        private boolean loading;

        CellEditorImpl() {
            field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.t.accent, 2),
                    BorderFactory.createEmptyBorder(0, 2, 0, 2)));
            field.setFont(Theme.tableFont());
            field.setBackground(Theme.t.surface);
            field.setForeground(Theme.t.text);
            field.setCaretColor(Theme.t.text);
            field.setSelectionColor(Theme.t.accentSoft);
            field.setSelectedTextColor(Theme.t.text);
            field.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    stopCellEditing();
                }
            });
            Flat.combo(combo, false);
            combo.setBackground(Theme.t.surface);
            combo.setOpaque(true);
            combo.setBorder(BorderFactory.createLineBorder(Theme.t.accent, 2));
            combo.setFont(Theme.tableFont());
            combo.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!loading && "comboBoxChanged".equals(e.getActionCommand()) && !combo.isPopupVisible())
                        stopCellEditing();
                }
            });
            combo.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                }

                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            if (isEditing() && usingCombo)
                                stopCellEditing();
                        }
                    });
                }

                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
            });
        }

        void selectAll() {
            if (!usingCombo)
                field.selectAll();
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            Definition d = grid.definition(row, column);
            String raw = grid.raw(row, column);
            usingCombo = d != null && d.isEnum() && d.enumDefinition != null && d.enumDefinition.size() > 0;
            if (usingCombo) {
                SDDSEnumDefinition mapping = d.enumDefinition;
                loading = true;
                combo.removeAllItems();
                labels = new String[mapping.size()];
                int current = -1;
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = mapping.getEntryLabel(i);
                    combo.addItem(labels[i] + " (" + mapping.getEntryCode(i) + ")");
                    if (labels[i].equals(raw))
                        current = i;
                }
                combo.setSelectedIndex(current);
                loading = false;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (isEditing() && usingCombo && combo.isShowing()) {
                            combo.requestFocusInWindow();
                            combo.showPopup();
                        }
                    }
                });
                return combo;
            }
            field.reset(raw);
            return field;
        }

        public Object getCellEditorValue() {
            if (usingCombo) {
                int i = combo.getSelectedIndex();
                return i >= 0 && i < labels.length ? labels[i] : "";
            }
            return field.getText();
        }

        public boolean isCellEditable(EventObject event) {
            return true;
        }
    }

    /** The text editor. A second click places the caret instead of selecting a word. */
    private final class EditorField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final javax.swing.undo.UndoManager history = Flat.keepKeys(this);
        private boolean clicked;

        EditorField() {
            /* A cell can hold a newline, and showing it must not change it. */
            getDocument().putProperty("filterNewlines", Boolean.FALSE);
        }

        void reset(String text) {
            setText(text);
            selectAll();
            history.discardAllEdits();
            clicked = false;
        }

        public void paste() {
            String text = null;
            try {
                Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                        .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                text = data == null ? null : data.toString();
            } catch (Exception e) {
                /* Nothing usable on the clipboard. */
            }
            if (text != null && (text.indexOf('\t') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0))
                listener.pasteIntoTable(CellTable.this);
            else
                super.paste();
        }

        protected void processMouseEvent(MouseEvent e) {
            if (e.getClickCount() == 2 && !clicked) {
                if (e.getID() == MouseEvent.MOUSE_RELEASED)
                    clicked = true;
                e = new MouseEvent(this, e.getID(), e.getWhen(), e.getModifiersEx(), e.getX(), e.getY(), 1,
                        e.isPopupTrigger(), e.getButton());
            }
            super.processMouseEvent(e);
        }
    }
}
