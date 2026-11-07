/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.table.*;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.JTextComponent;

import SDDS.java.SDDS.SDDSEnumDefinition;
import SDDS.java.SDDS.SDDSLongDouble;
import SDDS.java.SDDS.SDDSUtil;

/**
 * A separate, nonmodal window showing a two-dimensional slice of an array on the main
 * editor's current page. Edits go through the main editor's shared data and undo history.
 */
final class ArrayViewer extends JDialog {
    /** What the viewer needs from the main window. */
    interface Host {
        Document document();

        int currentPage(); // 0-based

        /** Replace elements of an array on the current page as one undoable step. Returns false if nothing was applied. */
        boolean editArrayCells(int arrayIndex, int[] flatIndexes, String[] texts, String undoLabel);

        void undo();

        void redo();

        boolean canUndo();

        boolean canRedo();

        void showMessage(String text); // status bar message

        /** The main window forgets this viewer when it closes. */
        void viewerClosed(ArrayViewer viewer);
    }

    /**
     * Clipboard contents that keep what tab and newline text cannot: cells holding tabs or
     * newlines, and <code>null</code> for an unselected cell, which a paste leaves unchanged.
     * Other programs see only the text.
     */
    static final class CellsTransfer implements Transferable {
        private static final DataFlavor FLAVOR = localFlavor();
        final String text;
        final String[][] cells;

        CellsTransfer(String text, String[][] cells) {
            this.text = text;
            this.cells = cells;
        }

        private static DataFlavor localFlavor() {
            try {
                return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + CellsTransfer.class.getName(),
                        "SDDS cells", CellsTransfer.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }

        /** The cells on the clipboard, if this text is what they were copied as, or <code>null</code>. */
        static String[][] onClipboard(String text) {
            try {
                Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (contents != null && contents.isDataFlavorSupported(FLAVOR)) {
                    CellsTransfer copied = (CellsTransfer) contents.getTransferData(FLAVOR);
                    if (copied.text.equals(text))
                        return copied.cells;
                }
            } catch (IllegalStateException | UnsupportedFlavorException | IOException e) {
                /* The text is still usable. */
            }
            return null;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { FLAVOR, DataFlavor.stringFlavor };
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (FLAVOR.equals(flavor))
                return this;
            if (DataFlavor.stringFlavor.equals(flavor))
                return text;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    private static final Color MISSING = new Color(160, 160, 160);
    private static final Color[] STOPS = { new Color(68, 1, 84), new Color(59, 82, 139), new Color(33, 145, 140),
            new Color(94, 201, 98), new Color(253, 231, 37) };
    private static final int COLUMN_WIDTH = 110;
    private static final int SLICES_MAXIMUM_HEIGHT = 155;
    private static final String SELECT_A_CELL = "Select a cell to see its complete array index and value.";

    private final Host host;
    private final String name;
    private int arrayIndex;
    private Definition definition;
    /** The array on the current page, or <code>null</code> if that page has none. */
    private Document.ArrayCells cells;
    private int page;
    private int[] dims = new int[0];

    private int[] fixed = new int[0];
    private int rowAxis = -1;
    private int columnAxis = -1;
    private boolean settingAxes;
    private boolean closed;

    private boolean heatmap;
    private boolean heatmapRangeValid;
    private BigDecimal heatmapMinimum = BigDecimal.ZERO;
    private BigDecimal heatmapMaximum = BigDecimal.ONE;
    private BigDecimal heatmapSpan = BigDecimal.ONE;
    private boolean fixedRangeValid;
    private BigDecimal fixedMinimum = BigDecimal.ZERO;
    private BigDecimal fixedMaximum = BigDecimal.ONE;
    private int scale;

    private final SliceModel model = new SliceModel();
    private final WrapLabel summary = new WrapLabel();
    private final WrapLabel heatmapStatus = new WrapLabel();
    private final WrapLabel notice = new WrapLabel();
    private final WrapLabel selection = new WrapLabel();
    private final Flat.ToolButton undoButton = toolButton("Undo", new Runnable() {
        public void run() {
            undo();
        }
    });
    private final Flat.ToolButton redoButton = toolButton("Redo", new Runnable() {
        public void run() {
            redo();
        }
    });
    private final Combo rowChoice = new Combo();
    private final Combo columnChoice = new Combo();
    private final JCheckBox heatmapChoice = new JCheckBox("Heatmap", new CheckIcon());
    private final Combo heatmapScale = new Combo("Current slice", "Fixed range");
    private final JTextField rangeMinimum = rangeField();
    private final JTextField rangeMaximum = rangeField();
    private final JButton rangeApply = Flat.button("Apply");
    private final JLabel legendMinimum = new JLabel();
    private final JLabel legendMaximum = new JLabel();
    private final JComponent heatmapLegend = row(1, legendMinimum, new ColorBar(), legendMaximum,
            new JLabel("Gray: missing / nonfinite"));
    private final JPanel sliceControls = new JPanel(new VerticalStack(6));
    private final JScrollPane slicesPane = new SlicesPane(sliceControls);
    private final Grid grid = new Grid();
    private final RowHeader rowHeader = new RowHeader();
    private final Timer heatmapRefresh = new Timer(0, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            updateHeatmap();
        }
    });

    ArrayViewer(Host host, int arrayIndex) {
        super((Window) null, ModalityType.MODELESS);
        this.host = host;
        name = host.document().arrays.get(arrayIndex).name;
        readState();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(900, 620);
        setMinimumSize(new Dimension(720, 480));
        setLocationByPlatform(true);
        buildWindow();
        connect();
        refresh();
        undoStateChanged();
    }

    /* ---- the interface to the main window ---- */

    /** The data, page, shape or definitions changed. */
    void documentChanged() {
        if (closed)
            return;
        int oldPage = page, oldType = definition.type, oldElements = cells == null ? -1 : cells.values.length;
        int[] oldDims = dims;
        if (!readState()) {
            /* The array may come back with an undo, so stay open and say what happened. */
            refresh();
            return;
        }
        int elements = cells == null ? -1 : cells.values.length;
        if (oldPage == page && oldType == definition.type && oldElements == elements && Arrays.equals(oldDims, dims)) {
            showSummary();
            model.refreshValues();
            updateSelection();
            // Coalesce the edits of a batch into one range scan.
            if (heatmapChoice.isSelected() && definition.isNumeric())
                heatmapRefresh.restart();
        } else {
            refresh();
        }
    }

    /** Refresh the enabled state of Undo and Redo. */
    void undoStateChanged() {
        undoButton.setEnabled(host.canUndo());
        redoButton.setEnabled(host.canRedo());
    }

    /** The name of the array, which is what identifies it. */
    String arrayName() {
        return name;
    }

    /** Commit an open cell editor. The main window should do this before it undoes or restructures anything. */
    void finishEditing() {
        if (grid.isEditing())
            grid.getCellEditor().stopCellEditing();
    }

    JTable table() {
        return grid;
    }

    /* ---- the window ---- */

    private void buildWindow() {
        JPanel content = new JPanel();
        JScrollPane gridPane = Flat.scrollPane(new JScrollPane(grid));
        VerticalStack stack = new VerticalStack(6);
        stack.stretch = gridPane;
        content.setLayout(stack);
        content.setBorder(Flat.pad(9, 9, 9, 9));
        setContentPane(content);

        JPanel toolbar = new JPanel();
        toolbar.setOpaque(false);
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.add(toolButton("Copy selection", new Runnable() {
            public void run() {
                copySelection(false);
            }
        }));
        toolbar.add(toolButton("Paste", new Runnable() {
            public void run() {
                pasteClipboard();
            }
        }));
        toolbar.add(toolButton("Copy slice", new Runnable() {
            public void run() {
                copySelection(true);
            }
        }));
        toolbar.add(Box.createHorizontalStrut(6));
        JComponent separator = Flat.rule(true);
        toolbar.add(separator);
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(undoButton);
        toolbar.add(redoButton);
        toolbar.add(Box.createHorizontalGlue());

        rowChoice.setName("arrayRowDimension");
        columnChoice.setName("arrayColumnDimension");
        heatmapChoice.setName("arrayHeatmap");
        heatmapChoice.setOpaque(false);
        heatmapChoice.setFocusPainted(false);
        heatmapChoice.setBorder(Flat.pad(0, 0, 0, 0));
        heatmapScale.setName("arrayHeatmapScale");
        rangeMinimum.setName("arrayHeatmapMinimum");
        rangeMaximum.setName("arrayHeatmapMaximum");
        rangeApply.setName("arrayHeatmapApply");
        legendMinimum.setName("arrayHeatmapLegendMinimum");
        legendMaximum.setName("arrayHeatmapLegendMaximum");
        heatmapStatus.setName("arrayHeatmapStatus");
        grid.setName("arraySliceGrid");

        sliceControls.setBorder(Flat.pad(9, 9, 9, 9));
        sliceControls.setOpaque(false);

        gridPane.setBorder(BorderFactory.createLineBorder(Theme.t.border));
        gridPane.setRowHeaderView(rowHeader);
        gridPane.getRowHeader().setBackground(Theme.t.header);
        gridPane.getVerticalScrollBar().setUnitIncrement(grid.getRowHeight());
        gridPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, new CornerButton());
        JPanel lowerCorner = new JPanel(), upperCorner = new JPanel();
        lowerCorner.setBackground(Theme.t.surface);
        upperCorner.setBackground(Theme.t.header);
        gridPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, lowerCorner);
        gridPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, upperCorner);

        content.add(summary);
        content.add(toolbar);
        content.add(row(4, new JLabel("Rows:"), rowChoice, new JLabel("Columns:"), columnChoice,
                Box.createHorizontalGlue(), new JLabel("All indices start at 0")));
        content.add(row(7, heatmapChoice, heatmapScale, new JLabel("Min:"), rangeMinimum, new JLabel("Max:"), rangeMaximum,
                rangeApply, Box.createHorizontalGlue()));
        content.add(heatmapLegend);
        content.add(heatmapStatus);
        content.add(slicesPane);
        content.add(notice);
        content.add(gridPane);
        content.add(selection);
        content.add(new JLabel("Edits are shared with the main editor. Save the SDDS file to keep them."));
    }

    private void connect() {
        ActionListener axisChosen = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!settingAxes)
                    changeAxis(e.getSource() == rowChoice, ((JComboBox<?>) e.getSource()).getSelectedIndex());
            }
        };
        rowChoice.addActionListener(axisChosen);
        columnChoice.addActionListener(axisChosen);
        heatmapRefresh.setRepeats(false);
        heatmapChoice.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                finishEditing();
                updateHeatmap();
            }
        });
        heatmapScale.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                scaleChosen();
            }
        });
        ActionListener apply = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applyHeatmapRange();
            }
        };
        rangeApply.addActionListener(apply);
        rangeMinimum.addActionListener(apply);
        rangeMaximum.addActionListener(apply);

        int menu = menuMask();
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), "viewerUndo", new Runnable() {
            public void run() {
                if (!typingInField())
                    undo();
            }
        });
        Runnable redo = new Runnable() {
            public void run() {
                if (!typingInField())
                    redo();
            }
        };
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), "viewerRedo", redo);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | InputEvent.SHIFT_DOWN_MASK), "viewerRedo", redo);
        Flat.dialogKeys(this, null);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                finishEditing();
                closed = true;
                dispose();
            }

            public void windowClosed(WindowEvent e) {
                closed = true;
                heatmapRefresh.stop();
                host.viewerClosed(ArrayViewer.this);
            }
        });
        // A cell editor is committed when its window loses focus, as it is when its cell does.
        addWindowFocusListener(new WindowAdapter() {
            public void windowLostFocus(WindowEvent e) {
                finishEditing();
            }
        });
    }

    private void bindKey(KeyStroke key, String command, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, command);
        getRootPane().getActionMap().put(command, keyAction(action));
    }

    private static Action keyAction(final Runnable action) {
        return new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        };
    }

    private static int menuMask() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static boolean typingInField() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent;
    }

    private static Flat.ToolButton toolButton(String text, final Runnable action) {
        Flat.ToolButton button = new Flat.ToolButton(text, null, text);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        return button;
    }

    private static JTextField rangeField() {
        JTextField field = Flat.field(new JTextField() {
            protected void paintComponent(Graphics g) {
                fillRounded(g, this);
                super.paintComponent(g);
            }
        });
        field.setDisabledTextColor(Theme.t.muted);
        field.setToolTipText("A finite number; scientific notation is supported.");
        field.setPreferredSize(new Dimension(155, field.getPreferredSize().height));
        field.setMinimumSize(field.getPreferredSize());
        return field;
    }

    /** A row like a horizontal box layout, in which one item takes the spare width. */
    private static JPanel row(int stretch, Component... items) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        for (int i = 0; i < items.length; i++) {
            GridBagConstraints at = new GridBagConstraints();
            at.gridx = i;
            at.weightx = i == stretch ? 1 : 0;
            at.fill = i == stretch ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            at.insets = new Insets(0, i == 0 ? 0 : 6, 0, 0);
            row.add(items[i], at);
        }
        return row;
    }

    /** Paint the fill that a rounded {@link Flat.FieldBorder} surrounds. */
    private static void fillRounded(Graphics g, Component c) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.smooth(g2);
        g2.setColor(c.isEnabled() ? Theme.t.surface : Theme.t.win);
        g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 10, 10));
        g2.dispose();
    }

    /* ---- following the document ---- */

    /** Find the array by name on the current page. Returns false if no array has that name now. */
    private boolean readState() {
        Document document = host.document();
        int found = -1;
        for (int i = 0; document != null && i < document.arrays.size() && found < 0; i++)
            if (document.arrays.get(i).name.equals(name))
                found = i;
        if (found < 0) {
            page = host.currentPage();
            cells = null;
            dims = new int[0];
            return false;
        }
        arrayIndex = found;
        definition = document.arrays.get(found);
        page = host.currentPage();
        cells = null;
        if (page >= 0 && page < document.pages.size() && found < document.pages.get(page).arrays.length)
            cells = document.pages.get(page).arrays[found];
        dims = cells == null ? new int[0] : cells.dims.clone();
        return true;
    }

    /** Refresh everything, keeping valid axes and clamping slices that no longer fit. */
    private void refresh() {
        int count = dims.length, oldRow = rowAxis, oldColumn = columnAxis;
        if (rowAxis < 0 || rowAxis >= count)
            rowAxis = count > 1 ? count - 2 : (count == 1 ? 0 : -1);
        if (count < 2)
            columnAxis = -1;
        else if (columnAxis < 0 || columnAxis >= count || columnAxis == rowAxis)
            columnAxis = rowAxis == count - 1 ? count - 2 : count - 1;
        boolean adjusted = oldRow >= 0 && (oldRow != rowAxis || oldColumn != columnAxis);
        fixed = Arrays.copyOf(fixed, count);
        for (int i = 0; i < count; i++) {
            int clamped = Math.max(0, Math.min(fixed[i], dims[i] - 1));
            adjusted = adjusted || clamped != fixed[i];
            fixed[i] = clamped;
        }
        settingAxes = true;
        rowChoice.removeAllItems();
        columnChoice.removeAllItems();
        for (int i = 0; i < count; i++) {
            rowChoice.addItem("Dimension " + i);
            columnChoice.addItem("Dimension " + i);
        }
        if (rowAxis >= 0)
            rowChoice.setSelectedIndex(rowAxis);
        if (count == 1) {
            columnChoice.removeAllItems();
            columnChoice.addItem("Single column");
        } else if (columnAxis >= 0) {
            columnChoice.setSelectedIndex(columnAxis);
        }
        settingAxes = false;
        rowChoice.setEnabled(count > 1);
        columnChoice.setEnabled(count > 1);
        showSummary();
        notice.say(adjusted ? "Slice selection adjusted to fit this page's array shape." : "");
        rebuildSliceControls();
        applySlice(true);
    }

    private void showSummary() {
        setTitle("Array Viewer \u2014 " + name);
        StringBuilder shape = new StringBuilder();
        for (int i = 0; i < dims.length; i++)
            shape.append(i > 0 ? " \u00d7 " : "").append(dims[i]);
        String units = definition.units == null || definition.units.isEmpty() ? "" : "    Units: " + definition.units;
        summary.say("Array: " + name + "    Page: " + (page + 1) + "    Shape: " + shape + units);
    }

    /** Choosing the axis that the other combo box shows swaps them, so two dimensions never overlap. */
    private void changeAxis(boolean rows, int axis) {
        if (axis < 0 || dims.length < 2 || axis == (rows ? rowAxis : columnAxis))
            return;
        finishEditing();
        if (rows) {
            if (axis == columnAxis)
                columnAxis = rowAxis;
            rowAxis = axis;
        } else {
            if (axis == rowAxis)
                rowAxis = columnAxis;
            columnAxis = axis;
        }
        settingAxes = true;
        rowChoice.setSelectedIndex(rowAxis);
        columnChoice.setSelectedIndex(columnAxis);
        settingAxes = false;
        notice.say("");
        rebuildSliceControls();
        applySlice(false);
    }

    /** Each dimension that is not displayed has a spin box and a matching slider. */
    private void rebuildSliceControls() {
        sliceControls.removeAll();
        for (int i = 0; i < fixed.length; i++)
            if (i != rowAxis && i != columnAxis)
                sliceControls.add(sliceRow(i));
        if (sliceControls.getComponentCount() == 0)
            sliceControls.add(new JLabel("The complete array is displayed."));
        slicesPane.invalidate();
        getContentPane().revalidate();
        sliceControls.repaint();
    }

    private JComponent sliceRow(final int axis) {
        int last = Math.max(0, dims[axis] - 1);
        final Spin spin = new Spin(fixed[axis], last);
        spin.setName("arraySliceIndex" + axis);
        final JSlider slider = new JSlider(0, last, fixed[axis]);
        slider.setUI(new SliderUI(slider));
        slider.setOpaque(false);
        spin.setEnabled(dims[axis] > 0);
        slider.setEnabled(dims[axis] > 0);
        slider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                spin.setValue(slider.getValue());
            }
        });
        spin.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int value = ((Number) spin.getValue()).intValue();
                slider.setValue(value);
                changeSlice(axis, value);
            }
        });
        slider.addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                slider.setValue(slider.getValue() - 3 * e.getWheelRotation());
            }
        });
        return row(2, new JLabel("Dimension " + axis + ":"), spin, slider);
    }

    private void changeSlice(int axis, int value) {
        if (fixed[axis] == value)
            return;
        finishEditing();
        fixed[axis] = value;
        notice.say("");
        applySlice(false);
    }

    /** Show the grid for the current axes and slices, reusing the selection only if it was asked to and still fits. */
    private void applySlice(boolean keepSelection) {
        int oldRows = model.rows, oldColumns = model.columns;
        grid.removeEditor();
        model.configure();
        if (model.columns != grid.getColumnCount())
            grid.setColumnCount(model.columns);
        if (keepSelection && oldRows == model.rows && oldColumns == model.columns)
            model.refreshValues();
        else
            model.fireTableDataChanged();
        rowHeader.revalidate();
        rowHeader.repaint();
        grid.getTableHeader().repaint();
        updateHeatmap();
        if (cells == null)
            notice.say("This array is unavailable. It may have been deleted or renamed.");
        else if (cells.values.length == 0)
            notice.say("This array is empty on the current page.");
        else if (model.rows == 0)
            notice.say("The array shape does not match its stored elements.");
        updateSelection();
    }

    private int leadRow() {
        int row = grid.getSelectionModel().getLeadSelectionIndex();
        return row < model.rows ? row : -1;
    }

    private int leadColumn() {
        int column = grid.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        return column < model.columns ? column : -1;
    }

    private void updateSelection() {
        int row = leadRow(), column = leadColumn();
        selection.say(row >= 0 && column >= 0 ? model.describe(row, column) : SELECT_A_CELL);
    }

    /* ---- heatmap ---- */

    /**
     * A finite number, or <code>null</code>. BigDecimal does the work of the Qt editor's
     * long double: 64-bit integers and long doubles are compared without narrowing them.
     */
    private static BigDecimal number(String text) {
        String t = text.trim();
        int first = !t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-') ? 1 : 0;
        if (first >= t.length())
            return null;
        // Arrays can hold many NaNs, and a failed parse is slow.
        char c = t.charAt(first);
        if (c != '.' && (c < '0' || c > '9'))
            return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A heatmap limit: a number that a long double can hold. */
    private static BigDecimal limit(String text) {
        return Cells.valid(text, SDDSUtil.SDDS_LONGDOUBLE) ? number(text) : null;
    }

    /** Text to a count of significant digits, written as C's <code>%g</code> writes it. */
    private static String significant(BigDecimal value, int digits) {
        if (value.signum() == 0)
            return "0";
        BigDecimal rounded = value.abs().round(new MathContext(digits, RoundingMode.HALF_EVEN)).stripTrailingZeros();
        String shown = rounded.unscaledValue().toString();
        int point = shown.length() - rounded.scale();
        StringBuilder out = new StringBuilder(value.signum() < 0 ? "-" : "");
        if (point > -4 && point <= digits) {
            if (point <= 0) {
                out.append("0.");
                for (int i = point; i < 0; i++)
                    out.append('0');
                out.append(shown);
            } else if (point >= shown.length()) {
                out.append(shown);
                for (int i = shown.length(); i < point; i++)
                    out.append('0');
            } else {
                out.append(shown, 0, point).append('.').append(shown, point, shown.length());
            }
        } else {
            int exponent = point - 1;
            out.append(shown.charAt(0));
            if (shown.length() > 1)
                out.append('.').append(shown, 1, shown.length());
            out.append('e').append(exponent < 0 ? '-' : '+');
            if (Math.abs(exponent) < 10)
                out.append('0');
            out.append(Math.abs(exponent));
        }
        return out.toString();
    }

    /**
     * Show a limit as the array's cells show values (-6.9999 for a double, not every digit of
     * its parsed text), with more digits when that would not convert back to exactly this
     * limit, so that applying unchanged text never moves the range.
     */
    private String rangeText(BigDecimal value) {
        String text = null;
        int type = definition.type;
        if (type == SDDSUtil.SDDS_DOUBLE)
            text = Cells.text(value.doubleValue());
        else if (type == SDDSUtil.SDDS_FLOAT)
            text = Cells.text(value.floatValue());
        else if (type == SDDSUtil.SDDS_LONGDOUBLE)
            text = Cells.text(SDDSLongDouble.valueOf(value));
        else if (Cells.integer(type) && value.stripTrailingZeros().scale() <= 0)
            text = value.toBigInteger().toString();
        BigDecimal back = text == null ? null : number(text);
        return back != null && back.compareTo(value) == 0 ? text : significant(value, value.precision());
    }

    private static Color heatmapColor(double fraction) {
        double position = Math.max(0, Math.min(1, fraction)) * 4;
        int first = Math.min(3, (int) position);
        double mix = position - first;
        Color a = STOPS[first], b = STOPS[first + 1];
        return new Color((int) Math.round(a.getRed() * (1 - mix) + b.getRed() * mix),
                (int) Math.round(a.getGreen() * (1 - mix) + b.getGreen() * mix),
                (int) Math.round(a.getBlue() * (1 - mix) + b.getBlue() * mix));
    }

    /** The background of a cell with this text while the heatmap is on. */
    private Color heatmapCell(String text) {
        BigDecimal value = heatmapRangeValid ? number(text) : null;
        if (value == null)
            return MISSING;
        if (value.compareTo(heatmapMinimum) < 0)
            return heatmapColor(0);
        if (value.compareTo(heatmapMaximum) > 0)
            return heatmapColor(1);
        if (heatmapSpan.signum() == 0)
            return heatmapColor(0.5);
        return heatmapColor(value.subtract(heatmapMinimum, MathContext.DECIMAL64)
                .divide(heatmapSpan, MathContext.DECIMAL64).doubleValue());
    }

    private void scaleChosen() {
        int chosen = heatmapScale.getSelectedIndex();
        if (chosen == scale)
            return;
        scale = chosen;
        finishEditing();
        if (scale == 1) {
            if (!fixedRangeValid) {
                BigDecimal[] range = model.finiteRange();
                fixedMinimum = range == null ? BigDecimal.ZERO : range[0];
                fixedMaximum = range == null ? BigDecimal.ONE : range[1];
                fixedRangeValid = true;
            }
            rangeMinimum.setText(rangeText(fixedMinimum));
            rangeMaximum.setText(rangeText(fixedMaximum));
        }
        updateHeatmap();
    }

    /** Scan for automatic bounds only when they are needed; fixed bounds survive page and slice changes. */
    private void updateHeatmap() {
        heatmapRefresh.stop();
        boolean numeric = definition.isNumeric() && cells != null;
        boolean enabled = numeric && heatmapChoice.isSelected();
        boolean fixedScale = scale == 1;
        heatmapChoice.setEnabled(numeric);
        heatmapChoice.setToolTipText(numeric ? "Color numerical cells while keeping their values editable."
                : "Heatmaps are available for numeric arrays only.");
        heatmapScale.setEnabled(enabled);
        rangeMinimum.setEnabled(enabled);
        rangeMaximum.setEnabled(enabled);
        rangeMinimum.setEditable(fixedScale);
        rangeMaximum.setEditable(fixedScale);
        rangeApply.setEnabled(enabled && fixedScale);
        heatmapLegend.setVisible(enabled);
        heatmapStatus.say("");
        BigDecimal minimum = fixedMinimum, maximum = fixedMaximum;
        boolean valid = false;
        if (enabled) {
            if (fixedScale) {
                valid = fixedRangeValid;
            } else {
                BigDecimal[] range = model.finiteRange();
                valid = range != null;
                if (valid) {
                    minimum = range[0];
                    maximum = range[1];
                }
                rangeMinimum.setText(valid ? rangeText(minimum) : "");
                rangeMaximum.setText(valid ? rangeText(maximum) : "");
            }
            legendMinimum.setText(valid ? significant(minimum, 8) : "No range");
            legendMaximum.setText(valid ? significant(maximum, 8) : "");
            legendMinimum.setToolTipText(valid ? rangeText(minimum) : null);
            legendMaximum.setToolTipText(valid ? rangeText(maximum) : null);
            if (!valid)
                heatmapStatus.say("This slice has no finite numeric values to scale.");
            else if (fixedScale)
                heatmapStatus.say("Fixed range: values outside the bounds use the end colors.");
            else if (minimum.compareTo(maximum) == 0)
                heatmapStatus.say("All finite values are equal; the middle color is used.");
        }
        heatmap = enabled;
        heatmapRangeValid = valid;
        heatmapMinimum = minimum;
        heatmapMaximum = maximum;
        heatmapSpan = maximum.subtract(minimum, MathContext.DECIMAL64);
        getContentPane().revalidate();
        grid.repaint();
    }

    /** Reject invalid limits without changing the active color range. */
    private void applyHeatmapRange() {
        if (!rangeApply.isEnabled())
            return;
        BigDecimal minimum = limit(rangeMinimum.getText()), maximum = limit(rangeMaximum.getText());
        if (minimum == null || maximum == null || minimum.compareTo(maximum) > 0) {
            heatmapStatus.say("Enter finite limits with minimum less than or equal to maximum. The previous range is still active.");
            return;
        }
        fixedMinimum = minimum;
        fixedMaximum = maximum;
        fixedRangeValid = true;
        updateHeatmap();
    }

    /* ---- editing ---- */

    private void undo() {
        if (undoButton.isEnabled()) {
            finishEditing();
            host.undo();
            undoStateChanged();
        }
    }

    private void redo() {
        if (redoButton.isEnabled()) {
            finishEditing();
            host.redo();
            undoStateChanged();
        }
    }

    private void applyEdits(int[] indexes, String[] texts, String label) {
        if (host.editArrayCells(arrayIndex, indexes, texts, label)) {
            documentChanged();
            undoStateChanged();
        }
    }

    private void commitCell(int row, int column, String text) {
        String old = model.raw(row, column);
        if (text.equals(old))
            return;
        if (!Cells.valid(text, definition)) {
            JOptionPane.showMessageDialog(this, Cells.problem(definition), "SDDS", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String value = Cells.canonical(text, definition.type);
        if (!value.equals(old))
            applyEdits(new int[] { model.flat(row, column) }, new String[] { value }, "Edit Cell");
    }

    /** Copy tab and newline text. Unselected cells inside the selection's bounds are blank. */
    void copySelection(boolean wholeSlice) {
        finishEditing();
        int[] bounds = wholeSlice ? new int[] { 0, 0, model.rows - 1, model.columns - 1 } : grid.selectionBounds();
        if (bounds == null || bounds[2] < bounds[0] || bounds[3] < bounds[1])
            return;
        StringBuilder text = new StringBuilder();
        String[][] copied = new String[bounds[2] - bounds[0] + 1][bounds[3] - bounds[1] + 1];
        for (int r = bounds[0]; r <= bounds[2]; r++) {
            if (r > bounds[0])
                text.append('\n');
            for (int c = bounds[1]; c <= bounds[3]; c++) {
                if (c > bounds[1])
                    text.append('\t');
                if (wholeSlice || grid.isCellSelected(r, c)) {
                    String value = model.raw(r, c);
                    copied[r - bounds[0]][c - bounds[1]] = value;
                    text.append(value);
                }
            }
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new CellsTransfer(text.toString(), copied), null);
        } catch (IllegalStateException e) {
            host.showMessage("The clipboard is not available");
        }
    }

    /** The clipboard's text, which is blank if it holds no text, or <code>null</code> if it cannot be read. */
    private String clipboardText() {
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor))
                return "";
            return (String) contents.getTransferData(DataFlavor.stringFlavor);
        } catch (IllegalStateException | UnsupportedFlavorException | IOException e) {
            host.showMessage("The clipboard is not available");
            return null;
        }
    }

    private void pasteClipboard() {
        String text = clipboardText();
        if (text != null)
            pasteText(text);
    }

    /** Check the entire rectangle before applying it as one undo step at the current cell. */
    boolean pasteText(String text) {
        finishEditing();
        int startRow = leadRow(), startColumn = leadColumn();
        if (startRow < 0 || startColumn < 0)
            return false;
        String[][] rows = CellsTransfer.onClipboard(text);
        if (rows == null) {
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            rows = new String[lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length][];
            for (int r = 0; r < rows.length; r++)
                rows[r] = lines[r].split("\t", -1);
        }
        for (int r = 0; r < rows.length; r++) {
            if (rows[r].length == 0 || rows[r].length != rows[0].length || rows[r].length > model.columns - startColumn
                    || r >= model.rows - startRow) {
                notice.say("Paste must be a rectangle that fits within the displayed slice.");
                return false;
            }
            for (String value : rows[r]) {
                if (value != null && !Cells.valid(value, definition)) {
                    notice.say("Paste contains a value incompatible with the array's data type. No cells were changed.");
                    return false;
                }
            }
        }
        // An unchanged paste must not become an empty undo step, which would discard Redo.
        int count = 0;
        int[] indexes = new int[rows.length * rows[0].length];
        String[] texts = new String[indexes.length];
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                String value = rows[r][c];
                if (value != null && !value.equals(model.raw(startRow + r, startColumn + c))) {
                    indexes[count] = model.flat(startRow + r, startColumn + c);
                    texts[count++] = value;
                }
            }
        }
        if (count > 0)
            applyEdits(Arrays.copyOf(indexes, count), Arrays.copyOf(texts, count), "Paste array slice");
        notice.say("");
        return true;
    }

    private DefaultCellEditor textEditor() {
        final JTextField field = Flat.field(new JTextField() {
            /* A double-click places the caret instead of selecting a word. */
            protected void processMouseEvent(MouseEvent e) {
                super.processMouseEvent(e.getClickCount() != 2 ? e
                        : new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(), e.getX(), e.getY(), 1,
                                e.isPopupTrigger(), e.getButton()));
            }
        });
        field.setFont(Theme.tableFont());
        // A text cell can hold newlines, and an editor that drops them would change it.
        field.getDocument().putProperty("filterNewlines", Boolean.FALSE);
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()), "viewerPaste");
        field.getActionMap().put("viewerPaste", keyAction(new Runnable() {
            public void run() {
                String text = clipboardText();
                if (text != null && (text.indexOf('\t') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0))
                    pasteText(text);
                else
                    field.paste();
            }
        }));
        return new DefaultCellEditor(field) {
            public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row, int column) {
                field.setText(model.raw(row, column));
                field.selectAll();
                return field;
            }
        };
    }

    private DefaultCellEditor enumEditor() {
        SDDSEnumDefinition states = definition.enumDefinition;
        int count = states == null ? 0 : states.size();
        final String[] labels = new String[count];
        String[] choices = new String[count];
        for (int i = 0; i < count; i++) {
            labels[i] = states.getEntryLabel(i);
            choices[i] = labels[i] + " (" + states.getEntryCode(i) + ")";
        }
        final JComboBox<String> box = Flat.combo(new JComboBox<String>(choices), true);
        box.setFont(Theme.tableFont());
        DefaultCellEditor editor = new DefaultCellEditor(box) {
            private String original = "";

            public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row, int column) {
                original = model.raw(row, column);
                box.setSelectedIndex(Arrays.asList(labels).indexOf(original));
                return box;
            }

            public Object getCellEditorValue() {
                int chosen = box.getSelectedIndex();
                return chosen < 0 ? original : labels[chosen];
            }
        };
        editor.setClickCountToStart(2);
        return editor;
    }

    /* ---- the grid ---- */

    /** Maps grid cells to the stored flat array, in which the last dimension is the fastest, without copying data. */
    private final class SliceModel extends AbstractTableModel {
        int rows, columns;
        private int base, rowStride, columnStride;

        void configure() {
            boolean valid = cells != null && rowAxis >= 0 && rowAxis < dims.length && columnAxis >= -1
                    && columnAxis < dims.length && columnAxis != rowAxis && fixed.length == dims.length;
            long elements = 1;
            for (int i = 0; i < dims.length && valid; i++) {
                if (dims[i] <= 0 || elements > Integer.MAX_VALUE / dims[i] || fixed[i] < 0 || fixed[i] >= dims[i])
                    valid = false;
                else
                    elements *= dims[i];
            }
            valid = valid && elements == cells.values.length;
            rows = valid ? dims[rowAxis] : 0;
            columns = valid ? (columnAxis < 0 ? 1 : dims[columnAxis]) : 0;
            base = rowStride = columnStride = 0;
            int stride = 1;
            for (int i = dims.length - 1; i >= 0 && valid; i--) {
                if (i == rowAxis)
                    rowStride = stride;
                else if (i == columnAxis)
                    columnStride = stride;
                else
                    base += fixed[i] * stride;
                stride *= dims[i];
            }
        }

        public int getRowCount() {
            return rows;
        }

        public int getColumnCount() {
            return columns;
        }

        public Object getValueAt(int row, int column) {
            return Cells.display(raw(row, column), definition);
        }

        public boolean isCellEditable(int row, int column) {
            return true;
        }

        int flat(int row, int column) {
            return base + row * rowStride + column * columnStride;
        }

        /** The stored text of a cell. */
        String raw(int row, int column) {
            String[] values = cells.values;
            int k = flat(row, column);
            return k < values.length && values[k] != null ? values[k] : "";
        }

        /** The complete zero-based index and the value of a cell. */
        String describe(int row, int column) {
            StringBuilder text = new StringBuilder(name).append('[');
            for (int i = 0; i < fixed.length; i++) {
                if (i > 0)
                    text.append(", ");
                text.append(i == rowAxis ? row : i == columnAxis ? column : fixed[i]);
            }
            return text.append("] = ").append(raw(row, column)).toString();
        }

        void refreshValues() {
            if (rows > 0)
                fireTableRowsUpdated(0, rows - 1);
        }

        /** The smallest and largest finite values of this slice, or <code>null</code> if it has none. */
        BigDecimal[] finiteRange() {
            BigDecimal minimum = null, maximum = null;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    BigDecimal value = number(raw(r, c));
                    if (value == null)
                        continue;
                    if (minimum == null || value.compareTo(minimum) < 0)
                        minimum = value;
                    if (maximum == null || value.compareTo(maximum) > 0)
                        maximum = value;
                }
            }
            return minimum == null ? null : new BigDecimal[] { minimum, maximum };
        }
    }

    /** One step of a selection: cells that were selected, or deselected, in this order. */
    private static final class Range {
        int top, left, bottom, right;
        final boolean add;

        Range(boolean add) {
            this.add = add;
        }

        void set(int top, int left, int bottom, int right) {
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }

        boolean contains(int row, int column) {
            return row >= top && row <= bottom && column >= left && column <= right;
        }
    }

    /**
     * The table. Its selection can be any union of rectangles, as in the Qt editor, which
     * JTable's row and column selection models cannot hold. Those models only keep the
     * current cell (the lead) and the anchor here.
     */
    private final class Grid extends JTable {
        private final List<Range> ranges = new ArrayList<Range>();
        private final int[] lineAnchors = { -1, -1 };
        private final CellRenderer renderer = new CellRenderer();
        private final DefaultCellEditor textEditor = textEditor();

        Grid() {
            super(ArrayViewer.this.model, new DefaultTableColumnModel());
            setFont(Theme.tableFont());
            setRowHeight(getFontMetrics(getFont()).getHeight() + 8);
            setAutoResizeMode(AUTO_RESIZE_OFF);
            setCellSelectionEnabled(true);
            setFillsViewportHeight(true);
            setSurrendersFocusOnKeystroke(true);
            putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            setBackground(Theme.t.surface);
            setForeground(Theme.t.text);
            setGridColor(Theme.t.border);
            JTableHeader header = getTableHeader();
            header.setReorderingAllowed(false);
            header.setBackground(Theme.t.header);
            header.setDefaultRenderer(new HeaderRenderer());
            LineSelector columnsSelector = new LineSelector(false);
            header.addMouseListener(columnsSelector);
            header.addMouseMotionListener(columnsSelector);
            ListSelectionListener moved = new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    updateSelection();
                }
            };
            getSelectionModel().addListSelectionListener(moved);
            getColumnModel().getSelectionModel().addListSelectionListener(moved);
            installKeys();
        }

        private void installKeys() {
            InputMap keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            ActionMap actions = getActionMap();
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask()), "copy");
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()), "paste");
            actions.put("copy", keyAction(new Runnable() {
                public void run() {
                    copySelection(false);
                }
            }));
            actions.put("paste", keyAction(new Runnable() {
                public void run() {
                    pasteClipboard();
                }
            }));
            actions.put("selectNextColumnCell", keyAction(new Runnable() {
                public void run() {
                    stepCell(1);
                }
            }));
            actions.put("selectPreviousColumnCell", keyAction(new Runnable() {
                public void run() {
                    stepCell(-1);
                }
            }));
            // Enter opens the editor rather than moving down.
            Action startEditing = actions.get("startEditing");
            actions.put("selectNextRowCell", startEditing);
            actions.put("selectPreviousRowCell", startEditing);
        }

        /** Tab moves through the cells, wrapping around, and keeps editing if it was. */
        private void stepCell(int step) {
            boolean wasEditing = isEditing();
            finishEditing();
            int rows = getRowCount(), columns = getColumnCount();
            if (rows == 0 || columns == 0)
                return;
            long at = Math.floorMod(Math.max(leadRow(), 0) * (long) columns + Math.max(leadColumn(), 0) + step,
                    (long) rows * columns);
            int row = (int) (at / columns), column = (int) (at % columns);
            changeSelection(row, column, false, false);
            if (wasEditing && editCellAt(row, column))
                getEditorComponent().requestFocus();
        }

        /** JTable would otherwise open an editor for any key with no binding, such as Ctrl+Q or F5. */
        protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                char c = e.getKeyChar();
                int commands = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
                boolean text = c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c) && (e.getModifiersEx() & commands) == 0;
                putClientProperty("JTable.autoStartsEdit", Boolean.valueOf(text || isEditing()));
            }
            return super.processKeyBinding(ks, e, condition, pressed);
        }

        public TableCellRenderer getCellRenderer(int row, int column) {
            return renderer;
        }

        public TableCellEditor getCellEditor(int row, int column) {
            return definition.isEnum() ? enumEditor() : textEditor;
        }

        public String getToolTipText(MouseEvent e) {
            int row = rowAtPoint(e.getPoint()), column = columnAtPoint(e.getPoint());
            return row < 0 || column < 0 ? null : model.describe(row, column);
        }

        /** The editor goes first: a warning about its text must not find it still open. */
        public void editingStopped(ChangeEvent e) {
            TableCellEditor editor = getCellEditor();
            if (editor == null)
                return;
            String text = String.valueOf(editor.getCellEditorValue());
            int row = editingRow, column = editingColumn;
            removeEditor();
            commitCell(row, column, text);
        }

        void setColumnCount(int count) {
            // Rebuilding a large model is much faster than removing its columns one at a time.
            DefaultTableColumnModel next = new DefaultTableColumnModel();
            next.setSelectionModel(getColumnModel().getSelectionModel());
            next.setColumnSelectionAllowed(true);
            for (int i = 0; i < count; i++)
                next.addColumn(new TableColumn(i, COLUMN_WIDTH));
            setColumnModel(next);
        }

        public boolean isCellSelected(int row, int column) {
            for (int i = ranges.size() - 1; i >= 0; i--)
                if (ranges.get(i).contains(row, column))
                    return ranges.get(i).add;
            return false;
        }

        private void selectionChanged() {
            repaint();
            getTableHeader().repaint();
            rowHeader.repaint();
        }

        public void changeSelection(int row, int column, boolean toggle, boolean extend) {
            boolean add = !toggle || !isCellSelected(row, column);
            if (!toggle && !extend)
                ranges.clear();
            super.changeSelection(row, column, false, extend);
            if (!extend || ranges.isEmpty())
                ranges.add(new Range(add));
            int anchorRow = getSelectionModel().getAnchorSelectionIndex();
            int anchorColumn = getColumnModel().getSelectionModel().getAnchorSelectionIndex();
            ranges.get(ranges.size() - 1).set(Math.min(anchorRow, row), Math.min(anchorColumn, column),
                    Math.max(anchorRow, row), Math.max(anchorColumn, column));
            selectionChanged();
        }

        public void clearSelection() {
            // JTable's constructor comes here before these exist.
            if (ranges != null) {
                ranges.clear();
                lineAnchors[0] = lineAnchors[1] = -1;
                selectionChanged();
            }
            super.clearSelection();
        }

        public void selectAll() {
            super.selectAll();
            ranges.clear();
            if (getRowCount() > 0 && getColumnCount() > 0) {
                Range all = new Range(true);
                all.set(0, 0, getRowCount() - 1, getColumnCount() - 1);
                ranges.add(all);
            }
            selectionChanged();
        }

        /** The first row and column and the last row and column that are selected, or <code>null</code>. */
        int[] selectionBounds() {
            int top = Integer.MAX_VALUE, left = Integer.MAX_VALUE, bottom = -1, right = -1;
            boolean exact = true;
            for (Range range : ranges) {
                if (range.add) {
                    top = Math.min(top, range.top);
                    left = Math.min(left, range.left);
                    bottom = Math.max(bottom, range.bottom);
                    right = Math.max(right, range.right);
                } else {
                    exact = false;
                }
            }
            if (bottom < 0)
                return null;
            if (exact)
                return new int[] { top, left, bottom, right };
            // A deselection may have cut into the bounds.
            int firstRow = Integer.MAX_VALUE, firstColumn = Integer.MAX_VALUE, lastRow = -1, lastColumn = -1;
            for (int r = top; r <= bottom; r++) {
                for (int c = left; c <= right; c++) {
                    if (isCellSelected(r, c)) {
                        firstRow = Math.min(firstRow, r);
                        firstColumn = Math.min(firstColumn, c);
                        lastRow = Math.max(lastRow, r);
                        lastColumn = Math.max(lastColumn, c);
                    }
                }
            }
            return lastRow < 0 ? null : new int[] { firstRow, firstColumn, lastRow, lastColumn };
        }

        /** True if a header section should stand out because the selection reaches it. */
        boolean lineHighlighted(boolean rows, int line) {
            for (Range range : ranges)
                if (range.add && line >= (rows ? range.top : range.left) && line <= (rows ? range.bottom : range.right))
                    return true;
            return false;
        }

        private boolean lineSelected(boolean rows, int line) {
            int length = rows ? getColumnCount() : getRowCount();
            for (int i = 0; i < length; i++)
                if (!(rows ? isCellSelected(line, i) : isCellSelected(i, line)))
                    return false;
            return true;
        }

        void pressLine(boolean rows, int line, boolean toggle, boolean extend) {
            if (getRowCount() == 0 || getColumnCount() == 0)
                return;
            requestFocusInWindow();
            boolean add = !toggle || extend || !lineSelected(rows, line);
            if (!toggle && !extend)
                ranges.clear();
            if (!extend || lineAnchors[rows ? 0 : 1] < 0)
                lineAnchors[rows ? 0 : 1] = line;
            if (!extend || ranges.isEmpty())
                ranges.add(new Range(add));
            dragLine(rows, line);
        }

        void dragLine(boolean rows, int line) {
            int anchor = lineAnchors[rows ? 0 : 1], low = Math.min(anchor, line), high = Math.max(anchor, line);
            Range range = ranges.get(ranges.size() - 1);
            if (rows)
                range.set(low, 0, high, getColumnCount() - 1);
            else
                range.set(0, low, getRowCount() - 1, high);
            Rectangle visible = getVisibleRect();
            int row = rows ? line : Math.max(rowAtPoint(new Point(0, visible.y)), 0);
            int column = rows ? Math.max(columnAtPoint(new Point(visible.x, 0)), 0) : line;
            super.changeSelection(row, column, false, false);
            selectionChanged();
        }
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        private final Border plain = Flat.pad(0, 6, 0, 6);
        private final Border onUnselected = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.t.accent),
                Flat.pad(0, 5, 0, 5));
        private final Border onSelected = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.t.onAccent),
                Flat.pad(0, 5, 0, 5));

        CellRenderer() {
            // A string array's cell is its text, even if that text looks like HTML.
            putClientProperty("html.disable", Boolean.TRUE);
            setFont(Theme.tableFont());
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row,
                int column) {
            setText((String) value);
            setHorizontalAlignment(definition.isNumeric() ? RIGHT : LEFT);
            if (selected) {
                setBackground(Theme.t.accent);
                setForeground(Theme.t.onAccent);
            } else if (heatmap) {
                Color fill = heatmapCell(model.raw(row, column));
                setBackground(fill);
                int gray = (fill.getRed() * 11 + fill.getGreen() * 16 + fill.getBlue() * 5) / 32;
                setForeground(gray < 140 ? Color.WHITE : Color.BLACK);
            } else {
                setBackground(Theme.t.surface);
                setForeground(Theme.t.text);
            }
            setBorder(!focus ? plain : selected ? onSelected : onUnselected);
            return this;
        }
    }

    private final class HeaderRenderer extends DefaultTableCellRenderer {
        private final Font normal = Theme.uiFont();
        private final Font strong = Theme.bold(Theme.uiFont());

        HeaderRenderer() {
            setHorizontalAlignment(CENTER);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.t.border),
                    Flat.pad(3, 4, 3, 4)));
            setBackground(Theme.t.header);
            setForeground(Theme.t.text);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row,
                int column) {
            setText(String.valueOf(column));
            setFont(grid.lineHighlighted(false, column) ? strong : normal);
            return this;
        }
    }

    /** Selects whole rows or columns from their headers, and lets go if a column's edge is being dragged instead. */
    private final class LineSelector extends MouseAdapter {
        private final boolean rows;
        private boolean selecting;

        LineSelector(boolean rows) {
            this.rows = rows;
        }

        private int lineAt(Point p) {
            if (!rows)
                return grid.getTableHeader().columnAtPoint(p);
            return model.rows == 0 ? -1 : Math.max(0, Math.min(model.rows - 1, p.y / grid.getRowHeight()));
        }

        public void mousePressed(MouseEvent e) {
            selecting = false;
            int line = lineAt(e.getPoint());
            if (!SwingUtilities.isLeftMouseButton(e) || line < 0 || (!rows && grid.getTableHeader().getResizingColumn() != null))
                return;
            finishEditing();
            selecting = true;
            grid.pressLine(rows, line, (e.getModifiersEx() & menuMask()) != 0, e.isShiftDown());
        }

        public void mouseDragged(MouseEvent e) {
            int line = lineAt(e.getPoint());
            if (selecting && line >= 0 && grid.getRowCount() > 0 && grid.getColumnCount() > 0)
                grid.dragLine(rows, line);
        }
    }

    /** Row numbers, painted only where they show, so the row count does not matter. */
    private final class RowHeader extends JComponent {
        RowHeader() {
            LineSelector selector = new LineSelector(true);
            addMouseListener(selector);
            addMouseMotionListener(selector);
            setFont(Theme.uiFont());
        }

        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.bold(getFont()));
            int width = fm.stringWidth(String.valueOf(Math.max(model.rows - 1, 0))) + 16;
            return new Dimension(Math.max(width, 30), (int) Math.min((long) model.rows * grid.getRowHeight(), Integer.MAX_VALUE));
        }

        protected void paintComponent(Graphics g) {
            Rectangle clip = g.getClipBounds();
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(Theme.t.header);
            g2.fillRect(clip.x, clip.y, clip.width, clip.height);
            int height = grid.getRowHeight();
            int last = Math.min(model.rows - 1, (clip.y + clip.height) / height);
            Font normal = getFont(), strong = Theme.bold(normal);
            for (int r = clip.y / height; r <= last; r++) {
                g2.setColor(Theme.t.border);
                g2.drawLine(0, (r + 1) * height - 1, getWidth(), (r + 1) * height - 1);
                g2.setFont(grid.lineHighlighted(true, r) ? strong : normal);
                g2.setColor(Theme.t.text);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(String.valueOf(r), 8, r * height + (height + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.setColor(Theme.t.border);
            g2.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
            g2.dispose();
        }
    }

    /** The square above the row numbers, which selects everything. */
    private final class CornerButton extends JPanel {
        CornerButton() {
            setBackground(Theme.t.header);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.t.border));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    finishEditing();
                    grid.selectAll();
                    grid.requestFocusInWindow();
                }
            });
        }
    }

    /* ---- small widgets ---- */

    /** Plain text that wraps, and takes no height while it is blank. */
    private static final class WrapLabel extends JTextArea {
        WrapLabel() {
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setHighlighter(null);
            setBorder(null);
            setFont(Theme.uiFont());
            setForeground(Theme.t.text);
            setCursor(Cursor.getDefaultCursor());
        }

        void say(String text) {
            if (!text.equals(getText())) {
                setText(text);
                revalidate();
            }
        }

        public Dimension getPreferredSize() {
            return getText().isEmpty() ? new Dimension(0, 0) : super.getPreferredSize();
        }
    }

    /** Stacks the visible children at the full width, each at the height it wants, and the stretching one takes what is left. */
    private static final class VerticalStack implements LayoutManager {
        private final int gap;
        Component stretch;

        VerticalStack(int gap) {
            this.gap = gap;
        }

        public void addLayoutComponent(String name, Component component) {
        }

        public void removeLayoutComponent(Component component) {
        }

        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int width = 0, height = 0, shown = 0;
            for (Component c : parent.getComponents()) {
                if (c.isVisible()) {
                    Dimension size = c.getPreferredSize();
                    width = Math.max(width, size.width);
                    height += size.height;
                    shown++;
                }
            }
            return new Dimension(width + insets.left + insets.right,
                    height + Math.max(shown - 1, 0) * gap + insets.top + insets.bottom);
        }

        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = parent.getWidth() - insets.left - insets.right;
            int spare = parent.getHeight() - insets.top - insets.bottom;
            Component[] children = parent.getComponents();
            int[] heights = new int[children.length];
            for (int i = 0; i < children.length; i++) {
                if (children[i].isVisible()) {
                    // Wrapped text works out its height from its width.
                    children[i].setSize(width, Math.max(children[i].getHeight(), 1));
                    heights[i] = children[i] == stretch ? 0 : children[i].getPreferredSize().height;
                    spare -= heights[i] + gap;
                }
            }
            int y = insets.top;
            for (int i = 0; i < children.length; i++) {
                if (children[i].isVisible()) {
                    int height = children[i] == stretch ? Math.max(spare + gap, 0) : heights[i];
                    children[i].setBounds(insets.left, y, width, height);
                    y += height + gap;
                }
            }
        }
    }

    /** The scrolling list of slice controls, which grows with its contents up to a limit. */
    private static final class SlicesPane extends JScrollPane {
        SlicesPane(JComponent view) {
            super(view);
            Flat.scrollPane(this);
            setBorder(BorderFactory.createLineBorder(Theme.t.border));
            getViewport().setBackground(Theme.t.win);
        }

        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.height = Math.min(size.height, SLICES_MAXIMUM_HEIGHT);
            return size;
        }
    }

    private static final class Combo extends JComboBox<String> {
        Combo(String... items) {
            super(items);
            Flat.combo(this, true);
        }

        /** BasicComboBoxUI lays out the arrow wider, by its insets, than it measures it, which would cut off the text. */
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width += 6;
            return size;
        }

        protected void paintComponent(Graphics g) {
            fillRounded(g, this);
            super.paintComponent(g);
        }
    }

    /** A spin box with a flat outline and arrows. It commits numbers as they are typed, as Qt's does. */
    private static final class Spin extends JSpinner {
        private JFormattedTextField text;

        Spin(int value, int maximum) {
            super(new SpinnerNumberModel(value, 0, maximum, 1));
            setUI(new SpinUI());
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(this, "0");
            setEditor(editor);
            editor.setOpaque(false);
            text = editor.getTextField();
            text.setColumns(Math.max(text.getColumns(), 4));
            text.setHorizontalAlignment(JTextField.LEFT);
            text.setOpaque(false);
            text.setBorder(Flat.pad(0, 5, 0, 0));
            text.setFont(Theme.uiFont());
            text.setForeground(Theme.t.text);
            text.setDisabledTextColor(Theme.t.muted);
            text.setCaretColor(Theme.t.text);
            text.setSelectionColor(Theme.t.accentSoft);
            text.setSelectedTextColor(Theme.t.text);
            JFormattedTextField.AbstractFormatter formatter = text.getFormatter();
            if (formatter instanceof DefaultFormatter)
                ((DefaultFormatter) formatter).setCommitsOnValidEdit(true);
            text.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
            setOpaque(false);
            setBorder(new Flat.FieldBorder(10, 0));
            addMouseWheelListener(new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e) {
                    int next = ((Number) getValue()).intValue() - e.getWheelRotation();
                    if (isEnabled() && next >= 0 && next <= maximumValue())
                        setValue(next);
                }
            });
        }

        private int maximumValue() {
            return ((Number) ((SpinnerNumberModel) getModel()).getMaximum()).intValue();
        }

        /** The outline shows the focus of the text inside. */
        public boolean isFocusOwner() {
            return text != null && text.isFocusOwner();
        }

        protected void paintComponent(Graphics g) {
            fillRounded(g, this);
            super.paintComponent(g);
        }
    }

    private static final class SpinUI extends BasicSpinnerUI {
        private JButton step(final boolean up) {
            final Icon down = Icons.CHEVRON_DOWN.icon(10, Theme.t.muted);
            JButton button = new JButton(new Icon() {
                public int getIconWidth() {
                    return 10;
                }

                public int getIconHeight() {
                    return 10;
                }

                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    if (up)
                        g2.rotate(Math.PI, x + 5, y + 5);
                    down.paintIcon(c, g2, x, y);
                    g2.dispose();
                }
            });
            button.setBorder(Flat.pad(0, 3, 0, 4));
            button.setContentAreaFilled(false);
            button.setFocusPainted(false);
            button.setFocusable(false);
            button.setOpaque(false);
            return button;
        }

        protected Component createNextButton() {
            JButton button = step(true);
            button.setName("Spinner.nextButton");
            installNextButtonListeners(button);
            return button;
        }

        protected Component createPreviousButton() {
            JButton button = step(false);
            button.setName("Spinner.previousButton");
            installPreviousButtonListeners(button);
            return button;
        }
    }

    private static final class SliderUI extends BasicSliderUI {
        SliderUI(JSlider slider) {
            super(slider);
        }

        protected Dimension getThumbSize() {
            return new Dimension(14, 14);
        }

        public void paintFocus(Graphics g) {
        }

        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            int y = trackRect.y + trackRect.height / 2 - 2;
            g2.setColor(Theme.t.border);
            g2.fill(new RoundRectangle2D.Float(trackRect.x, y, trackRect.width, 4, 4, 4));
            if (slider.isEnabled()) {
                g2.setColor(Theme.t.accent);
                g2.fill(new RoundRectangle2D.Float(trackRect.x, y, thumbRect.x + thumbRect.width / 2 - trackRect.x, 4, 4, 4));
            }
            g2.dispose();
        }

        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            Ellipse2D thumb = new Ellipse2D.Float(thumbRect.x + 0.5f, thumbRect.y + 0.5f, thumbRect.width - 1, thumbRect.height - 1);
            g2.setColor(slider.isEnabled() ? Theme.t.surface : Theme.t.win);
            g2.fill(thumb);
            g2.setColor(slider.isEnabled() && slider.isFocusOwner() ? Theme.t.accent : Theme.t.muted);
            g2.draw(thumb);
            g2.dispose();
        }
    }

    /** The legend's scale, the same palette as the cells. */
    private static final class ColorBar extends JComponent {
        ColorBar() {
            setMinimumSize(new Dimension(100, 16));
            setPreferredSize(new Dimension(100, 16));
        }

        protected void paintComponent(Graphics g) {
            if (getWidth() > 0) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new LinearGradientPaint(0, 0, getWidth(), 0, new float[] { 0, 0.25f, 0.5f, 0.75f, 1 }, STOPS));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        }
    }

    private static final class CheckIcon implements Icon {
        public int getIconWidth() {
            return 16;
        }

        public int getIconHeight() {
            return 16;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton button = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.translate(x, y);
            RoundRectangle2D box = new RoundRectangle2D.Float(1.5f, 1.5f, 13, 13, 5, 5);
            g2.setColor(button.isEnabled() ? Theme.t.surface : Theme.t.win);
            g2.fill(box);
            g2.setColor(button.isFocusOwner() ? Theme.t.accent : Theme.t.muted);
            g2.draw(box);
            if (button.isSelected()) {
                Path2D.Float mark = new Path2D.Float();
                mark.moveTo(4.5f, 8.2f);
                mark.lineTo(7f, 10.7f);
                mark.lineTo(11.5f, 5.3f);
                g2.setColor(button.isEnabled() ? Theme.t.text : Theme.t.muted);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(mark);
            }
            g2.dispose();
        }
    }
}
