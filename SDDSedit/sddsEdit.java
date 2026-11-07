/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import SDDS.java.SDDS.SDDSUtil;

/**
 * The SDDS Editor window. It shows the parameters, columns and arrays of one page of
 * an SDDS file for editing, and looks and behaves like the Qt <code>sddseditor</code>.
 *
 * @author Robert Soliday
 */
public final class sddsEdit extends JFrame implements GridModels.Host, ArrayViewer.Host, CellTable.Listener,
        Outline.Listener, EditorDialogs.SearchReplace.Host, Panels.RowHeader.MenuListener {
    private static final long serialVersionUID = 1L;
    private static final String MIDDLE_DOT = "\u00b7";

    /** Exit the program when this window closes. Only the command line program does. */
    private static boolean standalone;
    /** Record warnings instead of showing them, so tests do not stop at a dialog. */
    static boolean quietDialogs;
    /** The last warning recorded while dialogs are quiet. */
    static String lastWarning;

    Document doc;
    int page = -1;
    boolean dirty;

    final GridModels.ParameterModel parameterModel = new GridModels.ParameterModel(this);
    final GridModels.ColumnModel columnModel = new GridModels.ColumnModel(this);
    final GridModels.ArrayModel arrayModel = new GridModels.ArrayModel(this);
    final CellTable parameterTable = new CellTable(parameterModel, this);
    final CellTable columnTable = new CellTable(columnModel, this);
    final CellTable arrayTable = new CellTable(arrayModel, this);
    private final JScrollPane parameterScroll, columnScroll, arrayScroll;
    private final Panels.RowHeader columnRows, arrayRows;
    final Panels.DataPanel parameterPanel = new Panels.DataPanel("Parameters");
    final Panels.DataPanel columnPanel = new Panels.DataPanel("Columns");
    final Panels.DataPanel arrayPanel = new Panels.DataPanel("Arrays");
    final Panels.PanelStack stack = new Panels.PanelStack();
    final Outline outline = new Outline(this);
    private CellTable lastTable;

    final UndoStack undo = new UndoStack(new Runnable() {
        public void run() {
            undoChanged();
        }
    });

    /* Filter state. */
    String rowFilterExpression = "";
    boolean rowFilterActive;
    private String lastRowFilterExpression = "";
    private int visibleRows;
    private final Flat.Card filterChip = new Flat.Card(new FlowLayout(FlowLayout.LEFT, 0, 0), Theme.t.accentSoft, null, 22) {
        private static final long serialVersionUID = 1L;

        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 22);
        }

        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    };
    private final Flat.ToolButton filterChipText = new Flat.ToolButton("", Icons.FILTER.icon(12, Theme.t.accentText),
            "Edit the row filter");

    /* Remembered dialog values. */
    private String lastFillStart = "0", lastFillStep = "1", lastExpression = "x", lastTextFormula = "${x}";
    private int lastRowCount = 1;

    /* Column search. */
    final Flat.SearchField columnSearch = new Flat.SearchField("Find in all columns...", Theme.t.win);
    private int searchMatchRow = -1, searchMatchColumn = -1;
    private List<Integer> searchColumns = new ArrayList<Integer>();
    private boolean selectingSearchMatch;

    /* Toolbar. */
    private final JPanel toolBar = new JPanel();
    final JComboBox<String> pageCombo = new JComboBox<String>();
    private final JLabel pageCountLabel = Flat.muted("");
    private final Flat.ToolButton previousPage = new Flat.ToolButton(null, Icons.PREV.icon(16, Theme.t.text), "Previous page");
    private final Flat.ToolButton nextPage = new Flat.ToolButton(null, Icons.NEXT.icon(16, Theme.t.text), "Next page");
    private final Flat.ToolButton undoButton = new Flat.ToolButton(null, Icons.UNDO.icon(16, Theme.t.text), "Undo (Ctrl+Z)");
    private final Flat.ToolButton redoButton = new Flat.ToolButton(null, Icons.REDO.icon(16, Theme.t.text), "Redo (Ctrl+Shift+Z)");
    private final Flat.ToolButton filterButton = new Flat.ToolButton("Filter rows", Icons.FILTER.icon(16, Theme.t.text),
            "Filter the column rows shown (Ctrl+Shift+R)");
    final Flat.ToolButton asciiButton = new Flat.ToolButton("ASCII", null, "Save as ASCII SDDS");
    final Flat.ToolButton binaryButton = new Flat.ToolButton("Binary", null, "Save as binary SDDS");
    private JMenuItem undoItem, redoItem;
    private final JCheckBoxMenuItem[] panelItems = new JCheckBoxMenuItem[3];
    private final java.util.Map<String, Definition.TimeMode> timeChoices = new java.util.HashMap<String, Definition.TimeMode>();
    private boolean loadingPages;

    /* Status bar. */
    private final JPanel statusBar = new JPanel();
    final JLabel modifiedLabel = statusLabel();
    final JLabel pathLabel = statusLabel();
    private final JLabel pageStatusLabel = statusLabel();
    private final JLabel cellStatusLabel = statusLabel();
    private final JLabel rowsStatusLabel = statusLabel();
    final JLabel statusMessage = statusLabel();
    private final JLabel undoStatusLabel = statusLabel();
    private final BadgeButton messagesButton = new BadgeButton();
    private final javax.swing.Timer statusTimer;
    private int unreadMessages;
    private final JTextArea messages = new JTextArea();
    private final JPanel messagesPanel = new JPanel(new BorderLayout());
    private JCheckBoxMenuItem messagesItem;

    private final JSplitPane body;
    private boolean sizingPending = true;
    private boolean layingOutParameters;
    private int lastParameterWidth = -1;
    private boolean parameterPanelFitted;
    private int lastParameterFitHeight;
    private final List<ArrayViewer> viewers = new ArrayList<ArrayViewer>();
    private EditorDialogs.SearchReplace searchDialog;
    private JFileChooser chooser;
    private final String[] commandLine;

    /* ---- construction ---- */

    /** Make a window with no file open. */
    public sddsEdit() {
        this(new String[0]);
    }

    private sddsEdit(String[] args) {
        super("SDDS Editor");
        commandLine = args;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        undo.setLimit(Runtime.getRuntime().maxMemory() / 4);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
        statusTimer = new javax.swing.Timer(8000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessage.setText("");
            }
        });
        statusTimer.setRepeats(false);

        parameterScroll = tableScroll(parameterTable);
        parameterScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        columnScroll = tableScroll(columnTable);
        arrayScroll = tableScroll(arrayTable);
        columnRows = new Panels.RowHeader(columnTable, this);
        arrayRows = new Panels.RowHeader(arrayTable, null);
        columnScroll.setRowHeaderView(columnRows);
        arrayScroll.setRowHeaderView(arrayRows);
        columnScroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, Panels.corner());
        arrayScroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, Panels.corner());
        for (JScrollPane pane : new JScrollPane[] { columnScroll, arrayScroll })
            pane.getRowHeader().setBackground(Theme.t.header);

        parameterTable.setRowHeight(parameterTable.getRowHeight() + 2);
        parameterTable.setSelectionMapper(new CellTable.SelectionMapper() {
            public int[] map(int row, int column) {
                int parameter = parameterModel.parameter(row, column);
                return parameter < 0 ? null : parameterModel.valueCell(parameter);
            }
        });
        JTableHeader parameterHeader = new Panels.BandHeader(parameterTable.getColumnModel());
        parameterHeader.setReorderingAllowed(false);
        parameterHeader.setResizingAllowed(false);
        parameterHeader.setDefaultRenderer(new Panels.PlainHeaderRenderer());
        parameterTable.setTableHeader(parameterHeader);
        parameterScroll.setColumnHeaderView(parameterHeader);
        installColumnHeader(columnTable, columnScroll);
        installColumnHeader(arrayTable, arrayScroll);
        parameterScroll.getViewport().addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (parameterScroll.getViewport().getWidth() != lastParameterWidth)
                    layoutParameterGrid();
            }
        });

        setJMenuBar(buildMenus());
        buildToolBar();
        buildPanels();
        buildStatusBar();
        buildMessages();
        JPanel dataArea = new JPanel(new BorderLayout());
        dataArea.setBackground(Theme.t.win);
        dataArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        dataArea.add(stack, BorderLayout.CENTER);
        body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, outline, dataArea);
        body.setUI(new BasicSplitPaneUI() {
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private static final long serialVersionUID = 1L;

                    public void paint(Graphics g) {
                        g.setColor(Theme.t.win);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        body.setBorder(BorderFactory.createEmptyBorder());
        body.setDividerSize(3);
        body.setResizeWeight(0);
        body.setContinuousLayout(true);
        body.setDividerLocation(230);
        JPanel center = new JPanel(new BorderLayout());
        center.add(body, BorderLayout.CENTER);
        center.add(messagesPanel, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Theme.t.win);
        content.add(toolBar, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        setContentPane(content);
        installGlobalKeys();
        setSize(1200, 800);
        setLocationByPlatform(true);
        stack.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (sizingPending && stack.getHeight() > 0) {
                    sizingPending = false;
                    updatePanelSizing();
                }
            }
        });
        populate();
        undoChanged();
        updateTitle();
    }

    private static JLabel statusLabel() {
        JLabel label = Flat.plainText(new JLabel());
        label.setForeground(Theme.t.muted);
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        return label;
    }

    private JScrollPane tableScroll(CellTable table) {
        JScrollPane pane = Flat.scrollPane(new JScrollPane(table));
        pane.getViewport().setBackground(Theme.t.surface);
        pane.setBackground(Theme.t.surface);
        pane.setOpaque(true);
        return pane;
    }

    private void installColumnHeader(final CellTable table, JScrollPane pane) {
        final JTableHeader header = new Panels.BandHeader(table.getColumnModel());
        header.setReorderingAllowed(true);
        header.setDefaultRenderer(new Panels.TwoLineHeaderRenderer(table));
        table.setTableHeader(header);
        pane.setColumnHeaderView(header);
        header.addMouseListener(new MouseAdapter() {
            private boolean resizing;

            public void mousePressed(MouseEvent e) {
                resizing = header.getResizingColumn() != null;
                if (SwingUtilities.isRightMouseButton(e)) {
                    int column = header.columnAtPoint(e.getPoint());
                    if (column < 0)
                        return;
                    if (!table.columnHasSelection(column))
                        table.selectColumnCells(column, false, false);
                    showDefinitionMenu(table, e, table.convertColumnIndexToModel(column));
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e))
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            checkHeaderReorder(table);
                        }
                    });
            }

            public void mouseClicked(MouseEvent e) {
                if (resizing || !SwingUtilities.isLeftMouseButton(e))
                    return;
                int column = header.columnAtPoint(e.getPoint());
                if (column < 0)
                    return;
                if (e.getClickCount() == 2)
                    changeType(table == columnTable ? DefinitionDialogs.Kind.COLUMN : DefinitionDialogs.Kind.ARRAY, column);
                else
                    table.selectColumnCells(column, e.isControlDown(), e.isShiftDown());
            }
        });
    }

    private JMenuItem item(JComponent menu, String text, String keys, final Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (keys != null)
            item.setAccelerator(KeyStroke.getKeyStroke(keys));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        menu.add(item);
        return item;
    }

    private JMenuBar buildMenus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        item(file, "New...", "ctrl N", this::newFile);
        item(file, "Open", "ctrl O", this::openFile);
        file.addSeparator();
        item(file, "Save", "ctrl S", this::saveFile);
        item(file, "Save as...", "ctrl shift S", this::saveFileAs);
        item(file, "Export HDF", "ctrl shift H", this::exportHDF);
        item(file, "Export CSV", "ctrl shift C", this::exportCSV);
        file.addSeparator();
        item(file, "Restart", "ctrl R", this::restart);
        item(file, "Quit", "ctrl Q", this::quit);
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        undoItem = item(edit, "Undo", "ctrl Z", this::undo);
        redoItem = item(edit, "Redo", "ctrl shift Z", this::redo);
        edit.addSeparator();
        JMenu parameter = new JMenu("Parameter");
        item(parameter, "Attributes", null, () -> editAttributes(DefinitionDialogs.Kind.PARAMETER));
        item(parameter, "Insert", null, () -> insert(DefinitionDialogs.Kind.PARAMETER));
        item(parameter, "Delete", null, () -> deleteDefinitions(DefinitionDialogs.Kind.PARAMETER));
        edit.add(parameter);
        JMenu column = new JMenu("Column");
        item(column, "Attributes", null, () -> editAttributes(DefinitionDialogs.Kind.COLUMN));
        item(column, "Insert", null, () -> insert(DefinitionDialogs.Kind.COLUMN));
        item(column, "Delete", null, () -> deleteDefinitions(DefinitionDialogs.Kind.COLUMN));
        edit.add(column);
        JMenu array = new JMenu("Array");
        item(array, "Open Array Viewer...", null, this::openCurrentArrayViewer);
        item(array, "Attributes", null, () -> editAttributes(DefinitionDialogs.Kind.ARRAY));
        item(array, "Insert", null, () -> insert(DefinitionDialogs.Kind.ARRAY));
        item(array, "Delete", null, () -> deleteDefinitions(DefinitionDialogs.Kind.ARRAY));
        edit.add(array);
        edit.addSeparator();
        JMenu rows = new JMenu("Column Rows");
        item(rows, "Insert", null, this::insertRows);
        item(rows, "Delete", null, this::deleteRows);
        item(rows, "Filter/View...", "ctrl shift R", this::filterRows);
        item(rows, "Clear Filter/View", null, this::clearRowFilter);
        edit.add(rows);
        JMenu formula = new JMenu("Formula / Fill");
        item(formula, "Fill Series...", "ctrl shift F", () -> fillSeries(focusedTable()));
        item(formula, "Apply Numerical Expression...", "ctrl shift E", () -> applyExpression(focusedTable()));
        item(formula, "Apply Text Formula...", "ctrl shift M", () -> applyTextFormula(focusedTable()));
        edit.add(formula);
        JMenu pageMenu = new JMenu("Page");
        item(pageMenu, "Insert and clone current page", null, this::clonePage);
        item(pageMenu, "Insert", null, this::insertPage);
        item(pageMenu, "Delete", null, this::deletePage);
        edit.add(pageMenu);
        bar.add(edit);

        JMenu view = new JMenu("View");
        check(view, "Toolbar", null, true, on -> toolBar.setVisible(on));
        check(view, "Status Bar", null, true, on -> statusBar.setVisible(on));
        messagesItem = check(view, "Messages", "ctrl shift L", false, this::setMessagesVisible);
        check(view, "Outline", "ctrl shift O", true, on -> {
            outline.setVisible(on);
            body.setDividerSize(on ? 3 : 0);
            if (on)
                body.setDividerLocation(230);
        });
        view.addSeparator();
        panelItems[0] = check(view, "Parameters", null, true, on -> setPanelExpanded(parameterPanel, on));
        panelItems[1] = check(view, "Columns", null, true, on -> setPanelExpanded(columnPanel, on));
        panelItems[2] = check(view, "Arrays", null, true, on -> setPanelExpanded(arrayPanel, on));
        bar.add(view);

        JMenu info = new JMenu("Info");
        item(info, "About", null, () -> AboutDialog.showAbout(this));
        item(info, "Help", "F1", () -> HelpDialog.showHelp(this));
        bar.add(info);
        return bar;
    }

    private interface Toggle {
        void set(boolean on);
    }

    private JCheckBoxMenuItem check(JMenu menu, String text, String keys, boolean on, final Toggle toggle) {
        final JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, on);
        if (keys != null)
            item.setAccelerator(KeyStroke.getKeyStroke(keys));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                toggle.set(item.isSelected());
                revalidate();
            }
        });
        menu.add(item);
        return item;
    }

    private void setPanelExpanded(Panels.DataPanel panel, boolean on) {
        panel.setExpanded(on);
        stack.panelsChanged();
    }

    private void syncPanelItems() {
        panelItems[0].setSelected(parameterPanel.isExpanded());
        panelItems[1].setSelected(columnPanel.isExpanded());
        panelItems[2].setSelected(arrayPanel.isExpanded());
    }

    private Flat.ToolButton toolButton(String text, Icons icon, String tip, final Runnable action) {
        Flat.ToolButton button = new Flat.ToolButton(text, icon.icon(16, Theme.t.text), tip);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JComponent toolSeparator() {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        holder.add(Flat.rule(true), BorderLayout.CENTER);
        holder.setMaximumSize(new Dimension(13, Integer.MAX_VALUE));
        return holder;
    }

    private void buildToolBar() {
        toolBar.setBackground(Theme.t.chrome);
        toolBar.setLayout(new BoxLayout(toolBar, BoxLayout.X_AXIS));
        toolBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.t.border),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        toolBar.add(toolButton("New", Icons.FILE, "Create a new SDDS file (Ctrl+N)", this::newFile));
        toolBar.add(toolButton("Open", Icons.OPEN, "Open an SDDS file (Ctrl+O)", this::openFile));
        toolBar.add(toolButton("Save", Icons.SAVE, "Save (Ctrl+S)", this::saveFile));
        toolBar.add(toolSeparator());
        undoButton.addActionListener(e -> undo());
        redoButton.addActionListener(e -> redo());
        toolBar.add(undoButton);
        toolBar.add(redoButton);
        toolBar.add(toolSeparator());

        Flat.Card stepper = new Flat.Card(null, Theme.t.surface, Theme.t.border, 14);
        stepper.setLayout(new BoxLayout(stepper, BoxLayout.X_AXIS));
        stepper.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        previousPage.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        nextPage.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        previousPage.radius = 0;
        nextPage.radius = 0;
        previousPage.addActionListener(e -> {
            if (pageCombo.getSelectedIndex() > 0)
                pageCombo.setSelectedIndex(pageCombo.getSelectedIndex() - 1);
        });
        nextPage.addActionListener(e -> {
            if (pageCombo.getSelectedIndex() + 1 < pageCombo.getItemCount())
                pageCombo.setSelectedIndex(pageCombo.getSelectedIndex() + 1);
        });
        Flat.combo(pageCombo, false);
        pageCombo.setToolTipText("Current page");
        pageCombo.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 0));
        pageCombo.setPrototypeDisplayValue("Page 0000");
        pageCombo.addActionListener(e -> {
            if (!loadingPages && pageCombo.getSelectedIndex() >= 0)
                pageChanged(pageCombo.getSelectedIndex());
        });
        pageCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 8));
        stepper.add(previousPage);
        stepper.add(Flat.rule(true));
        stepper.add(pageCombo);
        stepper.add(pageCountLabel);
        stepper.add(Flat.rule(true));
        stepper.add(nextPage);
        stepper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JPanel stepperBox = new JPanel(new GridBagLayout());
        stepperBox.setOpaque(false);
        stepperBox.add(stepper);
        stepperBox.setMaximumSize(stepperBox.getPreferredSize());
        toolBar.add(stepperBox);
        toolBar.add(toolSeparator());

        filterButton.checkable = true;
        filterButton.addActionListener(e -> {
            filterRows();
            updateFilterIndicator();
        });
        toolBar.add(filterButton);
        toolBar.add(toolButton("Plot", Icons.PLOT, "Plot the current column or numeric array with sddsplot", this::plotCurrent));
        toolBar.add(toolButton("Array viewer", Icons.GRID, "Open the current array in the array viewer",
                this::openCurrentArrayViewer));
        toolBar.add(Box.createHorizontalGlue());
        JLabel format = Flat.muted("Save format");
        format.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8));
        toolBar.add(format);
        Flat.Card formatSwitch = new Flat.Card(new FlowLayout(FlowLayout.LEFT, 2, 0), Theme.t.header, Theme.t.border, 14);
        formatSwitch.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        for (Flat.ToolButton button : new Flat.ToolButton[] { asciiButton, binaryButton }) {
            button.checkable = true;
            button.setForeground(Theme.t.muted);
            button.checkedBg = Theme.t.surface;
            button.checkedFg = Theme.t.text;
            button.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
            button.radius = 10;
            formatSwitch.add(button);
        }
        asciiButton.setSelected(true);
        asciiButton.addActionListener(e -> setSaveFormat(true));
        binaryButton.addActionListener(e -> setSaveFormat(false));
        JPanel formatBox = new JPanel(new GridBagLayout());
        formatBox.setOpaque(false);
        formatBox.add(formatSwitch);
        formatBox.setMaximumSize(formatBox.getPreferredSize());
        toolBar.add(formatBox);
    }

    void setSaveFormat(boolean ascii) {
        asciiButton.setSelected(ascii);
        binaryButton.setSelected(!ascii);
        if (doc != null) {
            doc.ascii = ascii;
            markDirty();
        }
    }

    private void buildPanels() {
        parameterPanel.addHeaderStretch();
        parameterPanel.addHeaderWidget(panelAction("Insert", Icons.PLUS, "Insert a parameter",
                () -> insert(DefinitionDialogs.Kind.PARAMETER)));
        parameterPanel.addHeaderWidget(panelAction("Attributes", Icons.SLIDERS, "Edit the current parameter's attributes",
                () -> attributesButton(DefinitionDialogs.Kind.PARAMETER)));
        parameterPanel.setBody(parameterScroll);

        filterChip.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
        Flat.plainText(filterChipText);
        filterChipText.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        filterChipText.setForeground(Theme.t.accentText);
        filterChipText.hoverBg = Theme.t.accentSoft;
        filterChipText.addActionListener(e -> filterRows());
        Flat.ToolButton chipClear = new Flat.ToolButton(null, Icons.CLOSE.icon(11, Theme.t.accentText), "Clear the row filter");
        chipClear.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        chipClear.hoverBg = Theme.t.accentSoft;
        chipClear.addActionListener(e -> clearRowFilter());
        filterChip.add(filterChipText);
        filterChip.add(chipClear);
        filterChip.setVisible(false);
        columnPanel.addLeadingWidget(filterChip);
        columnPanel.addHeaderStretch();
        Flat.keepKeys(columnSearch);
        columnSearch.setToolTipText("Find text in selected columns, or all columns if none are selected (Enter for next match)");
        Dimension searchSize = new Dimension(220, 26);
        columnSearch.setPreferredSize(searchSize);
        columnSearch.setMaximumSize(searchSize);
        columnSearch.setMinimumSize(new Dimension(80, 26));
        columnSearch.addActionListener(e -> findInColumnPanel());
        columnSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                searchMatchRow = -1;
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                searchMatchRow = -1;
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                searchMatchRow = -1;
            }
        });
        columnPanel.addHeaderWidget(columnSearch);
        columnPanel.addHeaderWidget(panelAction("Clear selection", Icons.CLOSE,
                "Clear selected column cells to search all columns (Esc in the table)", this::clearColumnSelection));
        columnPanel.addHeaderWidget(panelAction("Insert", Icons.PLUS, "Insert a column",
                () -> insert(DefinitionDialogs.Kind.COLUMN)));
        columnPanel.addHeaderWidget(panelAction("Attributes", Icons.SLIDERS, "Edit the current column's attributes",
                () -> attributesButton(DefinitionDialogs.Kind.COLUMN)));
        columnPanel.setBody(columnScroll);

        arrayPanel.addHeaderStretch();
        arrayPanel.addHeaderWidget(panelAction("Open in viewer", Icons.GRID, "Open the current array in the array viewer",
                this::openCurrentArrayViewer));
        arrayPanel.addHeaderWidget(panelAction("Insert", Icons.PLUS, "Insert an array",
                () -> insert(DefinitionDialogs.Kind.ARRAY)));
        arrayPanel.addHeaderWidget(panelAction("Attributes", Icons.SLIDERS, "Edit the current array's attributes",
                () -> attributesButton(DefinitionDialogs.Kind.ARRAY)));
        arrayPanel.setBody(arrayScroll);

        stack.addPanel(parameterPanel);
        stack.addPanel(columnPanel);
        stack.addPanel(arrayPanel);
        stack.setStretch(0, 0);
        Runnable toggled = () -> {
            stack.panelsChanged();
            syncPanelItems();
        };
        parameterPanel.setOnToggle(toggled);
        columnPanel.setOnToggle(toggled);
        arrayPanel.setOnToggle(toggled);
    }

    private JComponent panelAction(String text, Icons icon, String tip, final Runnable action) {
        Flat.ToolButton button = Flat.panelAction(text, icon, tip);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void buildStatusBar() {
        statusBar.setBackground(Theme.t.chrome);
        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
        statusBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.t.border),
                BorderFactory.createEmptyBorder(3, 6, 3, 14)));
        statusBar.add(modifiedLabel);
        statusBar.add(Box.createHorizontalStrut(6));
        statusBar.add(pathLabel);
        statusBar.add(Box.createHorizontalStrut(6));
        statusBar.add(pageStatusLabel);
        statusBar.add(Box.createHorizontalStrut(6));
        statusBar.add(cellStatusLabel);
        statusBar.add(Box.createHorizontalStrut(6));
        statusBar.add(rowsStatusLabel);
        statusBar.add(Box.createHorizontalStrut(6));
        statusMessage.setMinimumSize(new Dimension(0, 0));
        statusMessage.setPreferredSize(new Dimension(0, statusMessage.getPreferredSize().height));
        statusMessage.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        statusBar.add(statusMessage);
        statusBar.add(undoStatusLabel);
        statusBar.add(Box.createHorizontalStrut(4));
        messagesButton.addActionListener(e -> {
            messagesItem.setSelected(messagesButton.isSelected());
            setMessagesVisible(messagesButton.isSelected());
        });
        statusBar.add(messagesButton);
    }

    private void buildMessages() {
        messages.setEditable(false);
        Flat.keepKeys(messages);
        messages.setMargin(new Insets(4, 10, 4, 10));
        messages.setFont(Theme.tableFont());
        messages.setBackground(Theme.t.surface);
        messages.setForeground(Theme.t.text);
        JScrollPane pane = Flat.scrollPane(new JScrollPane(messages));
        pane.setPreferredSize(new Dimension(100, 130));
        JPanel title = new JPanel(new BorderLayout());
        title.setBackground(Theme.t.chrome);
        title.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.t.border),
                BorderFactory.createEmptyBorder(2, 10, 2, 6)));
        JLabel label = new JLabel("Messages");
        label.setFont(Theme.bold(Theme.uiFont()));
        title.add(label, BorderLayout.WEST);
        Flat.ToolButton close = new Flat.ToolButton(null, Icons.CLOSE.icon(12, Theme.t.muted), "Hide the message log");
        close.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        close.addActionListener(e -> {
            messagesItem.setSelected(false);
            setMessagesVisible(false);
        });
        title.add(close, BorderLayout.EAST);
        messagesPanel.add(title, BorderLayout.NORTH);
        messagesPanel.add(pane, BorderLayout.CENTER);
        messagesPanel.setVisible(false);
    }

    private void setMessagesVisible(boolean on) {
        messagesPanel.setVisible(on);
        messagesButton.setSelected(on);
        if (on)
            unreadMessages = 0;
        updateStatus();
        revalidate();
    }

    private void installGlobalKeys() {
        JComponent root = getRootPane();
        InputMap keys = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        keys.put(KeyStroke.getKeyStroke("ctrl C"), "copyCells");
        keys.put(KeyStroke.getKeyStroke("ctrl V"), "pasteCells");
        keys.put(KeyStroke.getKeyStroke("DELETE"), "deleteCells");
        root.getActionMap().put("copyCells", action(this::copy));
        root.getActionMap().put("pasteCells", action(this::paste));
        root.getActionMap().put("deleteCells", action(this::deleteCells));
    }

    private static Action action(final Runnable run) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
    }

    /** The Messages button, with a count badge in its right padding. */
    private final class BadgeButton extends Flat.ToolButton {
        private static final long serialVersionUID = 1L;
        private int count;

        BadgeButton() {
            super("Messages", Icons.TERMINAL.icon(13, Theme.t.text), "Show or hide the message log (Ctrl+Shift+L)");
            checkable = true;
            radius = 10;
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        }

        void setBadge(int value) {
            if (value != count) {
                count = value;
                setBorder(BorderFactory.createEmptyBorder(2, 8, 2, value > 0 ? 34 : 8));
                revalidate();
                repaint();
            }
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(Theme.t.surface);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
            g2.setColor(Theme.t.border);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 10, 10));
            g2.dispose();
            super.paintComponent(g);
            if (count > 0) {
                Graphics2D g3 = (Graphics2D) g.create();
                Theme.smooth(g3);
                String text = count > 99 ? "99+" : String.valueOf(count);
                Font font = Theme.bold(Theme.small(Theme.uiFont()));
                FontMetrics fm = g3.getFontMetrics(font);
                int h = fm.getHeight() + 1, w = Math.max(h, fm.stringWidth(text) + 8);
                int x = getWidth() - w - 7, y = (getHeight() - h) / 2;
                g3.setColor(Theme.t.accent);
                g3.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
                g3.setFont(font);
                g3.setColor(Theme.t.onAccent);
                g3.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + fm.getAscent());
                g3.dispose();
            }
        }
    }

    /* ---- what models and viewers ask of the window ---- */

    public Document document() {
        return doc;
    }

    public int page() {
        return doc == null || doc.pages.isEmpty() ? -1 : page;
    }

    public int currentPage() {
        return page;
    }

    public Window window() {
        return this;
    }

    public boolean canUndo() {
        return undo.canUndo();
    }

    public boolean canRedo() {
        return undo.canRedo();
    }

    public void undo() {
        flushEdits();
        undo.undo();
    }

    public void redo() {
        flushEdits();
        undo.redo();
    }

    public void showMessage(String text) {
        message(text);
    }

    public void viewerClosed(ArrayViewer viewer) {
        viewers.remove(viewer);
    }

    public boolean editArrayCells(int arrayIndex, int[] flatIndexes, String[] texts, String undoLabel) {
        flushEdits();
        return applyTexts(arrayModel, arrayIndex, flatIndexes, texts, undoLabel);
    }

    private void undoChanged() {
        boolean canUndo = undo.canUndo(), canRedo = undo.canRedo();
        undoItem.setEnabled(canUndo);
        redoItem.setEnabled(canRedo);
        undoButton.setEnabled(canUndo);
        redoButton.setEnabled(canRedo);
        for (ArrayViewer viewer : new ArrayList<ArrayViewer>(viewers))
            viewer.undoStateChanged();
        updateStatus();
    }

    /* ---- messages and warnings ---- */

    /** Add a line to the message log and show it in the status bar for a while. */
    void message(String text) {
        messages.append(text + "\n");
        statusMessage.setText(text);
        statusMessage.setToolTipText(Flat.plainTip(text));
        statusTimer.restart();
        if (!messagesPanel.isVisible())
            unreadMessages++;
        updateStatus();
    }

    private void warn(String title, String text) {
        if (quietDialogs) {
            lastWarning = text;
            return;
        }
        JOptionPane.showMessageDialog(this, text, title, JOptionPane.WARNING_MESSAGE);
    }

    private void inform(String title, String text) {
        if (quietDialogs) {
            lastWarning = text;
            return;
        }
        JOptionPane.showMessageDialog(this, text, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void markDirty() {
        dirty = true;
        updateTitle();
    }

    private void updateTitle() {
        String title = "SDDS Editor";
        if (doc != null && doc.path != null)
            title = new File(doc.path).getName() + " - " + title;
        if (dirty)
            title += " *";
        setTitle(title);
        updateStatus();
    }

    private static String elideMiddle(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width)
            return text;
        int keep = text.length() / 2;
        while (keep > 1 && fm.stringWidth(text.substring(0, keep) + "\u2026" + text.substring(text.length() - keep)) > width)
            keep--;
        return text.substring(0, keep) + "\u2026" + text.substring(text.length() - keep);
    }

    private static String number(int value) {
        return String.format("%,d", value);
    }

    private static Icon dot(final Color color) {
        return new Icon() {
            public int getIconWidth() {
                return 8;
            }

            public int getIconHeight() {
                return 8;
            }

            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.smooth(g2);
                g2.setColor(color);
                g2.fillOval(x, y, 7, 7);
                g2.dispose();
            }
        };
    }

    /** Refresh the toolbar page controls, panel counts and status bar readouts. */
    void updateStatus() {
        if (messagesButton == null || outline == null)
            return;
        boolean loaded = doc != null && !doc.pages.isEmpty();
        int pageCount = loaded ? doc.pages.size() : 0;
        previousPage.setEnabled(loaded && page > 0);
        nextPage.setEnabled(loaded && page + 1 < pageCount);
        pageCombo.setEnabled(loaded);
        pageCountLabel.setText(loaded ? "of " + number(pageCount) : "");

        int parameters = doc != null ? doc.parameters.size() : 0;
        int columns = doc != null ? doc.columns.size() : 0;
        int arrays = doc != null ? doc.arrays.size() : 0;
        int rows = columnModel.pageRowCount();
        parameterPanel.setCountText(number(parameters));
        columnPanel.setCountText(columns > 0 ? number(columns) + " \u00d7 " + number(rows) + " rows" : number(columns));
        arrayPanel.setCountText(number(arrays));

        modifiedLabel.setVisible(doc != null);
        modifiedLabel.setIcon(dot(dirty ? Theme.t.warn : Theme.t.ok));
        modifiedLabel.setText(dirty ? "Modified" : "Saved");
        modifiedLabel.setForeground(dirty ? Theme.t.warn : Theme.t.muted);
        modifiedLabel.setFont(dirty ? Theme.bold(Theme.uiFont()) : Theme.uiFont());
        String path = doc == null ? "No file open" : doc.path == null ? "Untitled" : doc.path;
        pathLabel.setToolTipText(doc == null || doc.path == null ? null : Flat.plainTip(doc.path));
        pathLabel.setText(elideMiddle(path, pathLabel.getFontMetrics(pathLabel.getFont()), 360));
        pageStatusLabel.setText(loaded ? "Page " + number(page + 1) + " of " + number(pageCount) : "");
        pageStatusLabel.setVisible(loaded);

        String cell = "";
        if (loaded && lastTable != null && lastTable.getCurrentRow() >= 0 && lastTable.getCurrentColumn() >= 0) {
            int row = lastTable.getCurrentRow(), column = lastTable.getCurrentColumn();
            Definition d = lastTable.grid.definition(row, column);
            if (d != null) {
                if (lastTable == parameterTable)
                    cell = "Parameter " + d.name + " " + MIDDLE_DOT + " " + d.typeName();
                else if (lastTable == columnTable)
                    cell = "Row " + number(columnModel.pageRow(row) + 1) + " " + MIDDLE_DOT + " " + d.name + " "
                            + MIDDLE_DOT + " " + d.typeName();
                else
                    cell = "Element " + number(row + 1) + " " + MIDDLE_DOT + " " + d.name + " " + MIDDLE_DOT + " "
                            + d.typeName();
            }
        }
        cellStatusLabel.setText(cell);
        cellStatusLabel.setVisible(!cell.isEmpty());
        if (columns > 0 && rowFilterActive)
            rowsStatusLabel.setText(number(visibleRows) + " of " + number(rows) + " rows");
        else if (columns > 0)
            rowsStatusLabel.setText(number(rows) + " rows");
        else
            rowsStatusLabel.setText("");
        rowsStatusLabel.setVisible(columns > 0);
        String undoText = undo.canUndo() ? undo.undoText() : "";
        undoStatusLabel.setText(undoText.isEmpty() ? "" : "Undo: " + undoText);
        undoStatusLabel.setVisible(!undoText.isEmpty());
        messagesButton.setBadge(unreadMessages);
        outline.updateSummary(doc, rows);
    }

    private void updateFilterIndicator() {
        boolean active = rowFilterActive && !rowFilterExpression.trim().isEmpty();
        filterButton.setSelected(active);
        if (active) {
            FontMetrics fm = filterChipText.getFontMetrics(filterChipText.getFont());
            String shown = Theme.elide(rowFilterExpression.trim().replaceAll("\\s+", " "), fm, 260);
            filterChipText.setText(shown + " " + MIDDLE_DOT + " " + number(visibleRows) + " shown");
            filterChipText.setToolTipText("<html>Row filter: " + rowFilterExpression.replace("&", "&amp;").replace("<", "&lt;")
                    + "<br>Click to edit</html>");
        }
        filterChip.setVisible(active);
        columnPanel.revalidate();
        updateStatus();
    }

    /* ---- table events ---- */

    public void currentChanged(CellTable table) {
        lastTable = table;
        syncOutline();
        updateStatus();
    }

    public void selectionChanged(CellTable table) {
        if (table.getTableHeader() != null)
            table.getTableHeader().repaint();
        if (table == columnTable) {
            columnRows.repaint();
            updateColumnSearchScope();
        } else if (table == arrayTable) {
            arrayRows.repaint();
        }
    }

    public void tableFocused(CellTable table) {
        lastTable = table;
        updateStatus();
    }

    public void labelDoubleClicked(CellTable table, int row, int column) {
        int parameter = parameterModel.parameter(row, column);
        if (parameter < 0)
            return;
        if (parameterModel.fieldAt(column) == GridModels.ParameterModel.TYPE)
            changeType(DefinitionDialogs.Kind.PARAMETER, parameter);
        else
            editAttributesAt(DefinitionDialogs.Kind.PARAMETER, parameter);
    }

    public void cellMenu(CellTable table, MouseEvent event, int row, int column) {
        if (row < 0 || column < 0)
            return;
        if (table == parameterTable) {
            int parameter = parameterModel.parameter(row, column);
            if (parameter >= 0)
                showParameterMenu(event, parameter);
        } else {
            showDefinitionMenu(table, event, column);
        }
    }

    private void syncOutline() {
        if (lastTable == null)
            return;
        int kind = lastTable == parameterTable ? 0 : lastTable == columnTable ? 1 : 2;
        int row = lastTable.getCurrentRow(), column = lastTable.getCurrentColumn();
        int index = row >= 0 && column >= 0 ? lastTable.grid.item(row, column) : -1;
        outline.select(kind, index);
    }

    public void outlineNavigate(int kind, int index) {
        Panels.DataPanel panel = kind == 0 ? parameterPanel : kind == 1 ? columnPanel : arrayPanel;
        if (!panel.isExpanded()) {
            panel.setExpanded(true);
            stack.panelsChanged();
            syncPanelItems();
        }
        CellTable table = kind == 0 ? parameterTable : kind == 1 ? columnTable : arrayTable;
        table.requestFocusInWindow();
        if (kind == 0) {
            int[] cell = parameterModel.valueCell(index);
            table.selectCell(cell[0], cell[1]);
        } else if (table.getRowCount() > 0) {
            int row = kind == 1 && table.getCurrentRow() >= 0 ? table.getCurrentRow() : 0;
            table.selectCell(row, index);
        } else {
            table.scrollRectToVisible(table.getCellRect(0, index, true));
        }
        lastTable = table;
        updateStatus();
    }

    /** The table holding the keyboard focus, or null if something else in the window has it. */
    CellTable focusedTable() {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus == null)
            return lastTable;
        for (CellTable table : new CellTable[] { parameterTable, columnTable, arrayTable })
            if (focus == table || SwingUtilities.isDescendingFrom(focus, table))
                return table;
        return null;
    }

    public void pasteIntoTable(CellTable table) {
        pasteInto(table);
    }

    /** Commit any open cell editor. */
    public void flushEdits() {
        parameterTable.finishEditing();
        columnTable.finishEditing();
        arrayTable.finishEditing();
        for (ArrayViewer viewer : new ArrayList<ArrayViewer>(viewers))
            viewer.finishEditing();
    }

    /* ---- cell edits ---- */

    private static final class Edit {
        final int item, element;
        final String oldText, newText;

        Edit(int item, int element, String oldText, String newText) {
            this.item = item;
            this.element = element;
            this.oldText = oldText;
            this.newText = newText;
        }
    }

    private final class CellCommand implements UndoStack.Command {
        private final GridModels.Base model;
        private final int commandPage;
        private final List<Edit> edits;
        private final String label;

        CellCommand(GridModels.Base model, int commandPage, List<Edit> edits, String label) {
            this.model = model;
            this.commandPage = commandPage;
            this.edits = edits;
            this.label = label;
        }

        public void undo() {
            for (int i = edits.size() - 1; i >= 0; i--) {
                Edit e = edits.get(i);
                model.store(e.item, e.element, commandPage, e.oldText);
            }
            cellsChanged(model);
            markDirty();
        }

        public void redo() {
            for (Edit e : edits)
                model.store(e.item, e.element, commandPage, e.newText);
            cellsChanged(model);
            markDirty();
        }

        public String text() {
            return label;
        }

        public long size() {
            return edits.size() * 100L;
        }
    }

    private void cellsChanged(GridModels.Base model) {
        if (model.getRowCount() > 0)
            model.fireTableRowsUpdated(0, model.getRowCount() - 1);
        if (model == parameterModel) {
            /* Values and definitions change the widths each group needs. */
            SwingUtilities.invokeLater(this::layoutParameterGrid);
        } else if (model == columnModel && rowFilterActive) {
            refreshRowFilter(false);
        }
        if (searchDialog != null)
            searchDialog.invalidateMatches();
        searchMatchRow = -1;
        viewersChanged();
    }

    private void viewersChanged() {
        for (ArrayViewer viewer : new ArrayList<ArrayViewer>(viewers)) {
            viewer.documentChanged();
            /* A viewer closes itself when its array is gone. */
            if (!viewer.isDisplayable())
                viewers.remove(viewer);
        }
    }

    private boolean applyEdits(GridModels.Base model, List<Edit> edits, String label) {
        List<Edit> real = new ArrayList<Edit>();
        for (Edit e : edits)
            if (!e.oldText.equals(e.newText))
                real.add(e);
        if (real.isEmpty())
            return false;
        undo.push(new CellCommand(model, page, real, label));
        return true;
    }

    public boolean applyTexts(GridModels.Base model, int item, int[] elements, String[] texts, String label) {
        List<Edit> edits = new ArrayList<Edit>();
        for (int i = 0; i < elements.length; i++)
            edits.add(new Edit(item, elements[i], model.stored(item, elements[i], page), texts[i]));
        return applyEdits(model, edits, label);
    }

    public void cellEdited(GridModels.Base model, int row, int column, String text) {
        final Definition d = model.definition(row, column);
        if (d == null)
            return;
        if (model.raw(row, column).equals(text))
            return;
        if (!Cells.valid(text, d)) {
            /* Later, because an editor is still closing. */
            SwingUtilities.invokeLater(() -> warnInvalid(d));
            return;
        }
        String value = Cells.canonical(text, d.type);
        applyTexts(model, model.item(row, column), new int[] { model.element(row, column) }, new String[] { value }, "Edit Cell");
    }

    public void warnInvalid(Definition definition) {
        warn("SDDS", Cells.problem(definition));
    }

    /** A cell that the edit tools act on, in the coordinates that formulas see. */
    private static final class Target {
        final Definition definition;
        final GridModels.Base model;
        final int item, element, row, column;
        final String raw;

        Target(CellTable table, int viewRow, int viewColumn) {
            model = table.grid;
            definition = model.definition(viewRow, viewColumn);
            item = model.item(viewRow, viewColumn);
            element = model.element(viewRow, viewColumn);
            boolean parameter = model instanceof GridModels.ParameterModel;
            row = parameter ? item : element;
            column = parameter ? 0 : item;
            raw = model.raw(viewRow, viewColumn);
        }
    }

    /** The editable selected cells, or the current cell if none is selected, by row and column. */
    private List<Target> editableTargets(CellTable table) {
        List<Target> targets = new ArrayList<Target>();
        if (table == null)
            return targets;
        for (int[] cell : table.selectedCells())
            if (table.grid.isCellEditable(cell[0], cell[1]))
                targets.add(new Target(table, cell[0], cell[1]));
        if (targets.isEmpty() && table.getCurrentRow() >= 0 && table.getCurrentColumn() >= 0
                && table.grid.isCellEditable(table.getCurrentRow(), table.getCurrentColumn()))
            targets.add(new Target(table, table.getCurrentRow(), table.getCurrentColumn()));
        Collections.sort(targets, new Comparator<Target>() {
            public int compare(Target a, Target b) {
                return a.row != b.row ? Integer.compare(a.row, b.row) : Integer.compare(a.column, b.column);
            }
        });
        return targets;
    }

    /* ---- copy, paste, delete ---- */

    /** Copy the selected cells. Gaps in the selection are blank, and a paste here leaves them alone. */
    private void copy() {
        copyFrom(focusedTable());
    }

    /** Copy the selected cells of a table. */
    void copyFrom(CellTable table) {
        if (table == null)
            return;
        flushEdits();
        List<int[]> cells = new ArrayList<int[]>();
        for (int[] cell : table.selectedCells()) {
            if (table == parameterTable) {
                if (parameterModel.parameter(cell[0], cell[1]) >= 0)
                    cells.add(new int[] { parameterModel.parameter(cell[0], cell[1]), 0 });
            } else {
                cells.add(cell);
            }
        }
        if (cells.isEmpty())
            return;
        int minRow = Integer.MAX_VALUE, maxRow = -1, minColumn = Integer.MAX_VALUE, maxColumn = -1;
        java.util.Set<Long> selected = new java.util.HashSet<Long>();
        for (int[] cell : cells) {
            minRow = Math.min(minRow, cell[0]);
            maxRow = Math.max(maxRow, cell[0]);
            minColumn = Math.min(minColumn, cell[1]);
            maxColumn = Math.max(maxColumn, cell[1]);
            selected.add(((long) cell[0] << 32) | cell[1]);
        }
        StringBuilder text = new StringBuilder();
        /* A null marks a gap, so a paste within the editor leaves that target cell alone. */
        String[][] copied = new String[maxRow - minRow + 1][maxColumn - minColumn + 1];
        for (int r = minRow; r <= maxRow; r++) {
            if (r > minRow)
                text.append('\n');
            for (int c = minColumn; c <= maxColumn; c++) {
                if (c > minColumn)
                    text.append('\t');
                if (selected.contains(((long) r << 32) | c)) {
                    String value = table == parameterTable ? parameterModel.stored(r, 0, page) : table.grid.raw(r, c);
                    copied[r - minRow][c - minColumn] = value;
                    text.append(value);
                }
            }
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new ArrayViewer.CellsTransfer(text.toString(), copied), null);
        } catch (IllegalStateException e) {
            message("The clipboard is busy");
        }
    }

    private String clipboardText() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data == null ? null : data.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Paste tab-separated rows into successive rows and columns from the current cell. */
    private void paste() {
        pasteInto(focusedTable());
    }

    /** Paste into a table from its current cell. */
    void pasteInto(CellTable table) {
        if (table == null)
            return;
        flushEdits();
        int startRow = table.getCurrentRow(), startColumn = table.getCurrentColumn();
        if (startRow < 0 || startColumn < 0)
            return;
        boolean parameters = table == parameterTable;
        if (parameters) {
            int parameter = parameterModel.parameter(startRow, startColumn);
            if (parameter < 0)
                return;
            startRow = parameter;
            startColumn = 0;
        }
        String text = clipboardText();
        if (text == null)
            return;
        String[][] rows = ArrayViewer.CellsTransfer.onClipboard(text);
        if (rows == null) {
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            int lineCount = lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
            rows = new String[lineCount][];
            for (int r = 0; r < lineCount; r++)
                rows[r] = lines[r].split("\t", -1);
        }
        boolean multiple = rows.length > 1 || rows[0].length > 1;
        boolean warned = false;
        List<Edit> edits = new ArrayList<Edit>();
        for (int r = 0; r < rows.length; r++) {
            String[] fields = rows[r];
            for (int c = 0; c < fields.length; c++) {
                if (fields[c] == null)
                    continue;
                int row = startRow + r, column = startColumn + c;
                Definition d;
                int item, element;
                String current;
                if (parameters) {
                    if (column != 0 || row >= doc.parameters.size())
                        continue;
                    d = doc.parameters.get(row);
                    item = row;
                    element = 0;
                    current = parameterModel.stored(row, 0, page);
                } else {
                    if (row >= table.getRowCount() || column >= table.getColumnCount()
                            || !table.grid.isCellEditable(row, column))
                        continue;
                    d = table.grid.definition(row, column);
                    item = table.grid.item(row, column);
                    element = table.grid.element(row, column);
                    current = table.grid.raw(row, column);
                }
                if (Cells.valid(fields[c], d)) {
                    edits.add(new Edit(item, element, current, fields[c]));
                } else if (!multiple || !warned) {
                    warned = true;
                    warnInvalid(d);
                }
            }
        }
        applyEdits(table.grid, edits, "Paste");
    }

    /** Blank the selected cells. An enum value cannot be blanked. */
    private void deleteCells() {
        clearCellsIn(focusedTable());
    }

    /** Blank the selected cells of a table. */
    void clearCellsIn(CellTable table) {
        if (table == null)
            return;
        flushEdits();
        List<Edit> edits = new ArrayList<Edit>();
        int enumCells = 0;
        for (int[] cell : table.selectedCells()) {
            if (!table.grid.isCellEditable(cell[0], cell[1]))
                continue;
            Target t = new Target(table, cell[0], cell[1]);
            if (t.definition.isEnum())
                enumCells++;
            else
                edits.add(new Edit(t.item, t.element, t.raw, ""));
        }
        applyEdits(table.grid, edits, "Delete Cells");
        if (enumCells > 0)
            message("Delete left " + enumCells + " enum cell(s) unchanged; an enum value must be one of its declared states.");
    }

    /* ---- formula tools ---- */

    private List<Target> formulaTargets(CellTable table, String title) {
        flushEdits();
        List<Target> targets = editableTargets(table);
        if (targets.isEmpty())
            inform(title, "Select one or more cells first.");
        return targets;
    }

    /** Fill the selected cells with evenly spaced numbers. */
    void fillSeries(CellTable table) {
        List<Target> targets = formulaTargets(table, "Fill Series");
        if (targets.isEmpty())
            return;
        String[] values = EditorDialogs.form(this, "Fill Series", new String[] { "Start", "Step" },
                new String[] { lastFillStart, lastFillStep });
        if (values == null)
            return;
        applySeries(table, values[0], values[1]);
    }

    /** Apply a Fill Series to the selected cells, once its values are known. */
    void applySeries(CellTable table, String start, String step) {
        List<Target> targets = editableTargets(table);
        if (!Expression.isNumber(start) || !Expression.isNumber(step)) {
            warn("Fill Series", "Start and step must be valid numbers.");
            return;
        }
        lastFillStart = start;
        lastFillStep = step;
        List<Edit> edits = new ArrayList<Edit>();
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (!t.definition.isNumeric()) {
                warn("Fill Series", "Selected cells include non-numeric fields.");
                return;
            }
            String text;
            try {
                text = Expression.series(start, step, i, t.definition.type);
            } catch (Expression.Failure e) {
                warn("Fill Series", e.getMessage());
                return;
            }
            if (!Cells.valid(text, t.definition)) {
                warnInvalid(t.definition);
                return;
            }
            edits.add(new Edit(t.item, t.element, t.raw, text));
        }
        applyEdits(table.grid, edits, "Fill Series");
    }

    /** Replace the selected cells with the value of an expression. */
    void applyExpression(CellTable table) {
        List<Target> targets = formulaTargets(table, "Apply Numerical Expression");
        if (targets.isEmpty())
            return;
        String expression = EditorDialogs.expression(this, lastExpression);
        if (expression == null || expression.isEmpty())
            return;
        applyExpressionText(table, expression);
    }

    /** Apply an expression to the selected cells, once it is known. */
    void applyExpressionText(CellTable table, String expression) {
        List<Target> targets = editableTargets(table);
        lastExpression = expression;
        Target anchor = targets.get(0);
        List<Edit> edits = new ArrayList<Edit>();
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (!t.definition.isNumeric()) {
                warn("Apply Numerical Expression", "Selected cells include non-numeric fields.");
                return;
            }
            Expression.Context context = new Expression.Context();
            context.x = t.raw;
            context.a = anchor.raw;
            context.i = i;
            context.row = t.row;
            context.col = t.column;
            context.dr = t.row - anchor.row;
            context.dc = t.column - anchor.column;
            String text;
            try {
                text = Expression.evaluate(expression, context, t.definition.type);
            } catch (Expression.Failure e) {
                warn("Apply Numerical Expression", e.getMessage());
                return;
            }
            if (!Cells.valid(text, t.definition)) {
                warnInvalid(t.definition);
                return;
            }
            edits.add(new Edit(t.item, t.element, t.raw, text));
        }
        applyEdits(table.grid, edits, "Apply Numerical Expression");
    }

    /** Replace the selected cells using a text template. */
    void applyTextFormula(CellTable table) {
        List<Target> targets = formulaTargets(table, "Apply Text Formula");
        if (targets.isEmpty())
            return;
        String template = EditorDialogs.text(this, "Apply Text Formula",
                "Template (tokens: ${x}, ${a}, ${i}, ${row}, ${col}, ${dr}, ${dc}):", lastTextFormula);
        if (template == null || template.isEmpty())
            return;
        applyTemplateText(table, template);
    }

    /** Apply a text template to the selected cells, once it is known. */
    void applyTemplateText(CellTable table, String template) {
        List<Target> targets = editableTargets(table);
        lastTextFormula = template;
        Target anchor = targets.get(0);
        List<Edit> edits = new ArrayList<Edit>();
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            String text = Expression.applyTemplate(template, t.raw, anchor.raw, i, t.row, t.column, t.row - anchor.row,
                    t.column - anchor.column);
            if (!Cells.valid(text, t.definition)) {
                warnInvalid(t.definition);
                return;
            }
            edits.add(new Edit(t.item, t.element, t.raw, text));
        }
        applyEdits(table.grid, edits, "Apply Text Formula");
    }

    /* ---- whole document ---- */

    private final class StructuralCommand implements UndoStack.Command {
        private final Document before, after;
        private final int pageBefore, pageAfter;
        private final String label;

        StructuralCommand(Document before, int pageBefore, Document after, int pageAfter, String label) {
            this.before = before;
            this.pageBefore = pageBefore;
            this.after = after;
            this.pageAfter = pageAfter;
            this.label = label;
        }

        private void apply(Document state, int statePage) {
            doc.restore(state);
            /* The time display is not part of the data, so later choices stay. */
            for (Definition column : doc.columns)
                if (timeChoices.containsKey(column.name))
                    column.timeMode = column.canDisplayTime() ? timeChoices.get(column.name) : Definition.TimeMode.RAW;
            page = statePage;
            loadPageCombo();
            populate();
            markDirty();
        }

        public void undo() {
            apply(before, pageBefore);
        }

        public void redo() {
            apply(after, pageAfter);
        }

        public String text() {
            return label;
        }

        public long size() {
            return before.referenceBytes() + after.referenceBytes();
        }
    }

    /** Finish a structural change: refresh everything and record it for undo. */
    private void structuralDone(Document before, int pageBefore, String label) {
        loadPageCombo();
        populate();
        markDirty();
        undo.pushDone(new StructuralCommand(before, pageBefore, doc.snapshot(), page, label));
    }

    private void loadPageCombo() {
        loadingPages = true;
        pageCombo.removeAllItems();
        if (doc != null)
            for (int i = 0; i < doc.pages.size(); i++)
                pageCombo.addItem("Page " + (i + 1));
        if (doc != null && !doc.pages.isEmpty()) {
            page = Math.max(0, Math.min(page, doc.pages.size() - 1));
            pageCombo.setSelectedIndex(page);
        }
        loadingPages = false;
    }

    private void pageChanged(int value) {
        if (doc == null || value >= doc.pages.size())
            return;
        flushEdits();
        /* Older commands name cells by position, so history does not cross pages. */
        if (value != page)
            undo.clear();
        page = value;
        populate();
    }

    /** Show a page, 0-based. */
    void showPage(int value) {
        pageCombo.setSelectedIndex(value);
    }

    /** Refresh every model and panel from the document. */
    void populate() {
        if (searchDialog != null)
            searchDialog.dispose();
        parameterTable.removeEditor();
        columnTable.removeEditor();
        arrayTable.removeEditor();
        parameterModel.fireTableStructureChanged();
        lastParameterWidth = -1;
        layoutParameterGrid();
        columnModel.setVisibleRows(null);
        columnModel.fireTableStructureChanged();
        refreshRowFilter(false);
        columnTable.sizeColumnsToContents();
        arrayModel.fireTableStructureChanged();
        arrayTable.sizeColumnsToContents();
        columnRows.revalidate();
        columnRows.repaint();
        arrayRows.revalidate();
        arrayRows.repaint();
        updateColumnSearchScope();
        updatePanelSizing();
        outline.rebuild(doc);
        boolean ascii = doc == null || doc.ascii;
        asciiButton.setSelected(ascii);
        binaryButton.setSelected(!ascii);
        updateStatus();
        viewersChanged();
    }

    /** Choose how many parameter groups fit side by side and size their fields. */
    void layoutParameterGrid() {
        if (layingOutParameters)
            return;
        layingOutParameters = true;
        try {
            int count = doc != null && page() >= 0 ? doc.parameters.size() : 0;
            FontMetrics name = parameterTable.getFontMetrics(Theme.uiFont());
            FontMetrics value = parameterTable.getFontMetrics(parameterTable.getFont());
            Font typeFont = Theme.badgeFont();
            FontMetrics typeMetrics = parameterTable.getFontMetrics(typeFont);
            int nameWidth = name.stringWidth("Name"), typeWidth = 0, unitsWidth = 0, valueWidth = 0, descriptionWidth = 0;
            boolean anyUnits = false, anyDescription = false;
            for (int i = 0; i < count; i++) {
                Definition d = doc.parameters.get(i);
                anyUnits = anyUnits || (d.units != null && !d.units.isEmpty());
                anyDescription = anyDescription || (d.description != null && !d.description.isEmpty());
                if (i >= 5000)
                    continue;
                nameWidth = Math.max(nameWidth, name.stringWidth(d.name));
                typeWidth = Math.max(typeWidth, Theme.badgeSize(typeFont, d.typeName(), typeMetrics).width);
                if (d.units != null)
                    unitsWidth = Math.max(unitsWidth, name.stringWidth(d.units));
                if (d.description != null)
                    descriptionWidth = Math.max(descriptionWidth, name.stringWidth(d.description));
                valueWidth = Math.max(valueWidth, value.stringWidth(Cells.display(doc.pages.get(page).parameters[i], d)));
            }
            int pad = 20;
            nameWidth = Math.min(nameWidth + pad, 280);
            typeWidth = Math.max(typeWidth, name.stringWidth("Type")) + pad;
            unitsWidth = anyUnits ? Math.min(Math.max(unitsWidth, name.stringWidth("Units")) + pad, 160) : 0;
            valueWidth = Math.min(Math.max(valueWidth + pad, 90), 320);
            descriptionWidth = anyDescription ? Math.min(Math.max(descriptionWidth + pad, 140), 360) : 0;
            int groupWidth = nameWidth + typeWidth + unitsWidth + valueWidth + descriptionWidth;
            int viewport = parameterScroll.getViewport().getWidth();
            int available = Math.max(groupWidth, viewport);
            int maxGroups = count > 0 ? Math.max(1, Math.min(6, (count + 2) / 3)) : 1;
            int groups = Math.max(1, Math.min(maxGroups, available / groupWidth));
            int rowsBefore = parameterModel.getRowCount();

            /* Reflowing resets the grid, so keep the selected parameters and current cell. */
            List<Integer> selected = new ArrayList<Integer>();
            for (int[] cell : parameterTable.selectedCells())
                if (parameterModel.parameter(cell[0], cell[1]) >= 0)
                    selected.add(parameterModel.parameter(cell[0], cell[1]));
            int current = parameterTable.getCurrentRow() >= 0 && parameterTable.getCurrentColumn() >= 0
                    ? parameterModel.parameter(parameterTable.getCurrentRow(), parameterTable.getCurrentColumn())
                    : -1;
            /* A reflow removes an open editor, so what was typed is committed first. */
            if (groups != parameterModel.groupCount())
                parameterTable.finishEditing();
            if (parameterModel.setLayout(groups, anyUnits, anyDescription)) {
                boolean first = true;
                for (int parameter : selected) {
                    int[] cell = parameterModel.valueCell(parameter);
                    parameterTable.changeSelection(cell[0], cell[1], !first, false);
                    first = false;
                }
                if (current >= 0 && current < count) {
                    int[] cell = parameterModel.valueCell(current);
                    if (selected.isEmpty())
                        parameterTable.getSelectionModel().setLeadSelectionIndex(cell[0]);
                }
            }
            int spare = Math.max(0, viewport - groups * groupWidth) / groups;
            for (int c = 0; c < parameterModel.getColumnCount() && c < parameterTable.getColumnCount(); c++) {
                int width;
                switch (parameterModel.fieldAt(c)) {
                    case GridModels.ParameterModel.NAME:
                        width = nameWidth;
                        break;
                    case GridModels.ParameterModel.TYPE:
                        width = typeWidth;
                        break;
                    case GridModels.ParameterModel.UNITS:
                        width = unitsWidth;
                        break;
                    case GridModels.ParameterModel.VALUE:
                        width = valueWidth + (anyDescription ? spare / 2 : spare);
                        break;
                    default:
                        width = descriptionWidth + spare - spare / 2;
                        break;
                }
                TableColumn column = parameterTable.getColumnModel().getColumn(c);
                column.setMinWidth(0);
                column.setPreferredWidth(width);
                column.setWidth(width);
            }
            parameterTable.revalidate();
            parameterTable.repaint();
            parameterTable.getTableHeader().repaint();
            lastParameterWidth = viewport;
            if (parameterModel.getRowCount() != rowsBefore && doc != null && parameterPanelFitted
                    && stack.size(0) == lastParameterFitHeight)
                updatePanelSizing();
        } finally {
            layingOutParameters = false;
        }
    }

    /** Collapse panels with no definitions and share the height among the others. */
    private void updatePanelSizing() {
        int parameters = page() >= 0 ? doc.parameters.size() : 0;
        int columns = page() >= 0 ? doc.columns.size() : 0;
        int arrays = page() >= 0 ? doc.arrays.size() : 0;
        boolean any = parameters > 0 || columns > 0 || arrays > 0;
        parameterPanel.setExpanded(!any || parameters > 0);
        columnPanel.setExpanded(!any || columns > 0);
        arrayPanel.setExpanded(!any || arrays > 0);
        syncPanelItems();
        boolean columnData = columns > 0 && columnModel.getRowCount() > 0;
        boolean arrayData = arrays > 0 && arrayModel.getRowCount() > 0;
        boolean fit = parameters > 0 && (columnData || arrayData);
        stack.setStretch(0, columnData || arrayData ? 0 : 1);
        stack.setStretch(1, columnData ? 1 : 0);
        stack.setStretch(2, arrayData ? 1 : 0);
        if ((!columnData && !arrayData) || stack.getHeight() <= 0) {
            sizingPending = stack.getHeight() <= 0;
            stack.panelsChanged();
            return;
        }
        int[] sizes = { stack.size(0), stack.size(1), stack.size(2) };
        int columnWeight = Math.max(1, sizes[1]), arrayWeight = Math.max(1, sizes[2]);
        if (parameters == 0)
            sizes[0] = parameterPanel.collapsedHeight();
        if (columns == 0)
            sizes[1] = columnPanel.collapsedHeight();
        if (arrays == 0)
            sizes[2] = arrayPanel.collapsedHeight();
        int available = stack.available();
        if (fit) {
            int height = parameterModel.getRowCount() * parameterTable.getRowHeight()
                    + parameterTable.getTableHeader().getPreferredSize().height + parameterPanel.chromeHeight() + 2;
            sizes[0] = Math.min(height, Math.max(sizes[0], available / 3));
        }
        int remaining = Math.max(0, available - sizes[0] - (columnData ? 0 : sizes[1]) - (arrayData ? 0 : sizes[2]));
        if (columnData && arrayData) {
            sizes[1] = (int) ((long) remaining * columnWeight / (columnWeight + arrayWeight));
            sizes[2] = remaining - sizes[1];
        } else {
            sizes[columnData ? 1 : 2] = remaining;
        }
        stack.setSizes(sizes);
        parameterPanelFitted = fit;
        lastParameterFitHeight = sizes[0];
    }

    /* ---- files ---- */

    private JFileChooser fileChooser() {
        if (chooser == null)
            chooser = new JFileChooser(new File("."));
        return chooser;
    }

    private String chooseFile(String title, boolean save, String suggested, String description, String... extensions) {
        JFileChooser files = fileChooser();
        files.setDialogTitle(title);
        files.resetChoosableFileFilters();
        files.setFileFilter(new FileNameExtensionFilter(description, extensions));
        files.setSelectedFile(suggested == null ? new File("") : new File(suggested));
        int result = save ? files.showSaveDialog(this) : files.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
            return null;
        File file = files.getSelectedFile();
        if (save && file.exists() && !confirmOverwrite(file))
            return null;
        return file.getPath();
    }

    private boolean confirmOverwrite(File file) {
        return JOptionPane.showConfirmDialog(this, file.getName() + " already exists.\nDo you want to replace it?", "SDDS",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Ask whether to save changes. Returns false if the user cancels. */
    private boolean maybeSave() {
        flushEdits();
        if (!dirty || quietDialogs)
            return true;
        Object[] options = { "Save", "Discard", "Cancel" };
        int answer = JOptionPane.showOptionDialog(this, "The document has been modified.\nDo you want to save your changes?",
                "SDDS", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (answer == 0) {
            saveFile();
            return !dirty;
        }
        return answer == 1;
    }

    private void newFile() {
        flushEdits();
        Document created = NewFileDialog.show(this);
        if (created == null || !maybeSave())
            return;
        created.ascii = asciiButton.isSelected();
        setDocument(created);
        markDirty();
        message("Created a new document. Save to choose its filename.");
    }

    private void openFile() {
        if (!maybeSave())
            return;
        String path = chooseFile("Open SDDS", false, null, "SDDS Files (*.sdds *.sdds.xz *.sdds.gz)", "sdds", "xz", "gz");
        if (path != null)
            loadFile(path);
    }

    private interface Slow<T> {
        T run() throws Exception;
    }

    /**
     * Do slow work off the Swing thread. If it takes more than a moment, a modal note says
     * what is happening, so nothing can change the document meanwhile.
     *
     * @param text   the note
     * @param slow   the work
     * @param cancel what a Cancel button does, or <code>null</code> for no button
     * @return the result of the work
     * @throws java.util.concurrent.ExecutionException if the work fails
     * @throws InterruptedException                    if this thread is interrupted
     */
    private <T> T whileBusy(String text, final Slow<T> slow, final Runnable cancel)
            throws java.util.concurrent.ExecutionException, InterruptedException {
        final JDialog progress = new JDialog(this, "SDDS", Dialog.ModalityType.APPLICATION_MODAL);
        SwingWorker<T, Void> work = new SwingWorker<T, Void>() {
            protected T doInBackground() throws Exception {
                return slow.run();
            }

            protected void done() {
                progress.dispose();
            }
        };
        work.execute();
        try {
            return work.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException stillGoing) {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            panel.add(new JLabel(text), BorderLayout.NORTH);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            panel.add(bar, BorderLayout.CENTER);
            if (cancel != null) {
                JButton stop = Flat.button("Cancel");
                stop.addActionListener(e -> cancel.run());
                JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                row.setOpaque(false);
                row.add(stop);
                panel.add(row, BorderLayout.SOUTH);
            }
            progress.setContentPane(panel);
            progress.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            progress.pack();
            progress.setSize(Math.max(progress.getWidth(), 360), progress.getHeight());
            progress.setLocationRelativeTo(this);
            if (!work.isDone())
                progress.setVisible(true);
            return work.get();
        }
    }

    /**
     * Open a file, showing progress if it takes more than a moment.
     *
     * @param path file name
     * @return true if it was read
     */
    public boolean loadFile(final String path) {
        Document loaded;
        try {
            loaded = whileBusy("Loading " + new File(path).getName() + "\u2026", () -> Document.read(path), null);
        } catch (java.util.concurrent.ExecutionException e) {
            String reason = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.toString();
            message("Failed to open " + path + ": " + reason);
            warn("SDDS", reason.equals("File contains no pages") ? reason : "Failed to open file\n" + reason);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        loaded.path = path;
        setDocument(loaded);
        message("Loaded " + path);
        return true;
    }

    /** Show a new document, forgetting the old one. */
    void setDocument(Document loaded) {
        for (ArrayViewer viewer : new ArrayList<ArrayViewer>(viewers))
            viewer.dispose();
        viewers.clear();
        doc = loaded;
        page = 0;
        rowFilterActive = false;
        rowFilterExpression = "";
        timeChoices.clear();
        lastTable = null;
        parameterTable.clearCellSelection();
        columnTable.clearCellSelection();
        arrayTable.clearCellSelection();
        undo.clear();
        loadPageCombo();
        lastParameterWidth = -1;
        populate();
        dirty = false;
        updateFilterIndicator();
        updateTitle();
    }

    private void ensureDocument() {
        if (doc != null)
            return;
        Document created = new Document();
        created.pages.add(new Document.Page());
        created.ascii = asciiButton.isSelected();
        setDocument(created);
    }

    /** Save to the current file, or ask for one. */
    void saveFile() {
        if (doc == null)
            return;
        flushEdits();
        if (doc.path == null) {
            saveFileAs();
            return;
        }
        writeFile(doc.path);
    }

    private void saveFileAs() {
        if (doc == null)
            return;
        flushEdits();
        String path = chooseFile("Save SDDS", true, doc.path, "SDDS Files (*.sdds)", "sdds");
        if (path != null && writeFile(path)) {
            doc.path = path;
            updateTitle();
        }
    }

    /** Write the document to a file. */
    boolean writeFile(String path) {
        try {
            String written = doc.save(path);
            dirty = false;
            reportSaved(written);
            updateTitle();
            return true;
        } catch (Document.Failure e) {
            message("Save failed: " + e.getMessage());
            warn("SDDS", e.getMessage());
            return false;
        }
    }

    /**
     * Post the message for a finished save or export, including access its replacement could
     * not keep. That goes in the Saved message, which would otherwise replace a separate one at once.
     */
    void reportSaved(String path) {
        if (doc.accessNotKept == null)
            message("Saved " + path);
        else
            message("Saved " + path + ", but could not keep the group and permissions of " + doc.accessNotKept);
    }

    private String exportPath(String suffix) {
        if (doc == null || doc.path == null)
            return null;
        String name = doc.path;
        int dot = name.lastIndexOf('.');
        int slash = name.lastIndexOf(File.separatorChar);
        return (dot > slash ? name.substring(0, dot) : name) + suffix;
    }

    private void exportCSV() {
        if (doc == null)
            return;
        flushEdits();
        String path = chooseFile("Export CSV", true, exportPath(".csv"), "CSV Files (*.csv)", "csv");
        if (path == null)
            return;
        try {
            doc.writeCSV(path);
            reportSaved(path);
        } catch (Document.Failure e) {
            warn(doc.hasEnums() ? "Enum export" : "SDDS", e.getMessage());
        }
    }

    /** HDF export needs a native library, so it runs the SDDS toolkit's own converter. */
    private void exportHDF() {
        if (doc == null)
            return;
        flushEdits();
        if (doc.hasEnums()) {
            warn("Enum export", "Convert enum elements to integer codes or label strings before exporting to this format.");
            return;
        }
        final String path = chooseFile("Save HDF", true, exportPath(".h5"), "HDF Files (*.h5 *.hdf)", "h5", "hdf");
        if (path == null)
            return;
        final java.util.concurrent.atomic.AtomicReference<Process> running = new java.util.concurrent.atomic.AtomicReference<Process>();
        try {
            whileBusy("Exporting " + new File(path).getName() + "\u2026", () -> {
                doc.exportHDF(path, running);
                return null;
            }, () -> {
                Process process = running.get();
                if (process != null)
                    process.destroy();
            });
            reportSaved(path);
        } catch (java.util.concurrent.ExecutionException e) {
            String reason = e.getCause() != null ? e.getCause().getMessage() : e.toString();
            message("HDF export failed: " + reason);
            warn("SDDS", reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteTree(Path directory) {
        File[] files = directory.toFile().listFiles();
        if (files != null)
            for (File file : files)
                file.delete();
        directory.toFile().delete();
    }

    private void restart() {
        if (!maybeSave())
            return;
        List<String> command = new ArrayList<String>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(sddsEdit.class.getName());
        command.addAll(Arrays.asList(commandLine));
        try {
            new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            warn("SDDS", "Failed to restart: " + e.getMessage());
            return;
        }
        dirty = false;
        System.exit(0);
    }

    /** Close the window, asking first about unsaved changes. */
    void quit() {
        if (!maybeSave())
            return;
        for (ArrayViewer viewer : new ArrayList<ArrayViewer>(viewers))
            viewer.dispose();
        dispose();
        if (standalone)
            System.exit(0);
    }

    /* ---- definitions ---- */

    private List<Definition> definitionsOf(DefinitionDialogs.Kind kind) {
        return kind == DefinitionDialogs.Kind.PARAMETER ? doc.parameters
                : kind == DefinitionDialogs.Kind.COLUMN ? doc.columns : doc.arrays;
    }

    private CellTable tableOf(DefinitionDialogs.Kind kind) {
        return kind == DefinitionDialogs.Kind.PARAMETER ? parameterTable
                : kind == DefinitionDialogs.Kind.COLUMN ? columnTable : arrayTable;
    }

    private static String kindName(DefinitionDialogs.Kind kind) {
        return kind == DefinitionDialogs.Kind.PARAMETER ? "Parameter" : kind == DefinitionDialogs.Kind.COLUMN ? "Column" : "Array";
    }

    private int currentItem(DefinitionDialogs.Kind kind) {
        CellTable table = tableOf(kind);
        int row = table.getCurrentRow(), column = table.getCurrentColumn();
        if (table.getRowCount() == 0)
            return column;
        return row >= 0 && column >= 0 ? table.grid.item(row, column) : -1;
    }

    private void attributesButton(DefinitionDialogs.Kind kind) {
        if (doc != null && currentItem(kind) < 0) {
            message(kind == DefinitionDialogs.Kind.PARAMETER ? "Select a parameter to edit its attributes"
                    : kind == DefinitionDialogs.Kind.COLUMN
                            ? "Select a cell in a column, or right-click its header, to edit its attributes"
                            : "Select a cell in an array, or right-click its header, to edit its attributes");
            return;
        }
        editAttributes(kind);
    }

    private void editAttributes(DefinitionDialogs.Kind kind) {
        if (doc == null)
            return;
        int index = currentItem(kind);
        if (index < 0) {
            attributesButton(kind);
            return;
        }
        editAttributesAt(kind, index);
    }

    private void editAttributesAt(DefinitionDialogs.Kind kind, int index) {
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        if (DefinitionDialogs.editAttributes(this, doc, kind, index))
            structuralDone(before, pageBefore, "Edit " + kindName(kind) + " Attributes");
    }

    private void changeType(DefinitionDialogs.Kind kind, int index) {
        if (doc == null)
            return;
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        if (DefinitionDialogs.changeType(this, doc, kind, index))
            structuralDone(before, pageBefore, "Change " + kindName(kind) + " Type");
    }

    /** Add a definition, making a document first if there is none. */
    void insert(DefinitionDialogs.Kind kind) {
        ensureDocument();
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        if (DefinitionDialogs.insert(this, doc, kind))
            structuralDone(before, pageBefore, "Insert " + kindName(kind));
    }

    /** The selected items of a panel, or the given one if it is outside the selection. */
    private List<Integer> itemsOrFallback(DefinitionDialogs.Kind kind, int fallback) {
        CellTable table = tableOf(kind);
        TreeSet<Integer> selected = new TreeSet<Integer>();
        for (int[] cell : table.selectedCells()) {
            int item = table.grid.item(cell[0], cell[1]);
            if (item >= 0)
                selected.add(item);
        }
        if (fallback >= 0 && !selected.contains(fallback)) {
            selected.clear();
            selected.add(fallback);
        }
        if (selected.isEmpty() && currentItem(kind) >= 0)
            selected.add(currentItem(kind));
        return new ArrayList<Integer>(selected);
    }

    private void deleteDefinitions(DefinitionDialogs.Kind kind) {
        if (doc != null)
            deleteItems(kind, itemsOrFallback(kind, -1));
    }

    private static <T> T[] without(T[] items, TreeSet<Integer> removed) {
        List<T> kept = new ArrayList<T>();
        for (int i = 0; i < items.length; i++)
            if (!removed.contains(i))
                kept.add(items[i]);
        return kept.toArray(Arrays.copyOf(items, 0));
    }

    /** Remove definitions and their values on every page. */
    void deleteItems(DefinitionDialogs.Kind kind, List<Integer> items) {
        if (items.isEmpty())
            return;
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        TreeSet<Integer> removed = new TreeSet<Integer>(items);
        List<Definition> list = definitionsOf(kind);
        for (int index : removed.descendingSet())
            list.remove(index);
        for (Document.Page p : doc.pages) {
            if (kind == DefinitionDialogs.Kind.PARAMETER)
                p.parameters = without(p.parameters, removed);
            else if (kind == DefinitionDialogs.Kind.COLUMN)
                p.columns = without(p.columns, removed);
            else
                p.arrays = without(p.arrays, removed);
        }
        String name = kindName(kind);
        structuralDone(before, pageBefore, items.size() == 1 ? "Delete " + name : "Delete " + items.size() + " " + name + "s");
    }

    private static <T> T[] permuted(T[] items, int[] order) {
        T[] out = items.clone();
        for (int i = 0; i < order.length; i++)
            out[i] = items[order[i]];
        return out;
    }

    /** Reorder definitions so that new position i holds old item order[i]. */
    void reorder(DefinitionDialogs.Kind kind, int[] order) {
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        List<Definition> list = definitionsOf(kind);
        List<Definition> old = new ArrayList<Definition>(list);
        for (int i = 0; i < order.length; i++)
            list.set(i, old.get(order[i]));
        for (Document.Page p : doc.pages) {
            if (kind == DefinitionDialogs.Kind.PARAMETER)
                p.parameters = permuted(p.parameters, order);
            else if (kind == DefinitionDialogs.Kind.COLUMN)
                p.columns = permuted(p.columns, order);
            else
                p.arrays = permuted(p.arrays, order);
        }
        structuralDone(before, pageBefore, "Reorder " + kindName(kind) + "s");
    }

    /** A dragged header has been dropped. Make the document match the new order. */
    private void checkHeaderReorder(CellTable table) {
        int count = table.getColumnCount();
        int[] order = new int[count];
        boolean moved = false;
        for (int i = 0; i < count; i++) {
            order[i] = table.getColumnModel().getColumn(i).getModelIndex();
            moved = moved || order[i] != i;
        }
        if (moved)
            reorder(table == columnTable ? DefinitionDialogs.Kind.COLUMN : DefinitionDialogs.Kind.ARRAY, order);
    }

    void moveParameters(int parameter, int delta) {
        List<Integer> rows = itemsOrFallback(DefinitionDialogs.Kind.PARAMETER, parameter);
        int count = doc.parameters.size();
        if (rows.isEmpty() || (delta < 0 && rows.get(0) == 0) || (delta > 0 && rows.get(rows.size() - 1) == count - 1))
            return;
        int[] order = new int[count];
        for (int i = 0; i < count; i++)
            order[i] = i;
        if (delta < 0) {
            for (int r : rows) {
                int swap = order[r];
                order[r] = order[r - 1];
                order[r - 1] = swap;
            }
        } else {
            for (int i = rows.size() - 1; i >= 0; i--) {
                int r = rows.get(i), swap = order[r];
                order[r] = order[r + 1];
                order[r + 1] = swap;
            }
        }
        reorder(DefinitionDialogs.Kind.PARAMETER, order);
        boolean first = true;
        for (int r : rows) {
            int[] cell = parameterModel.valueCell(r + delta);
            parameterTable.changeSelection(cell[0], cell[1], !first, false);
            first = false;
        }
    }

    /* ---- context menus ---- */

    private void showParameterMenu(MouseEvent event, final int parameter) {
        final List<Integer> rows = itemsOrFallback(DefinitionDialogs.Kind.PARAMETER, parameter);
        JPopupMenu menu = new JPopupMenu();
        item(menu, "Attributes...", null, () -> editAttributesAt(DefinitionDialogs.Kind.PARAMETER, parameter));
        item(menu, "Change Type...", null, () -> changeType(DefinitionDialogs.Kind.PARAMETER, parameter));
        menu.addSeparator();
        item(menu, "Move Up", null, () -> moveParameters(parameter, -1)).setEnabled(rows.get(0) > 0);
        item(menu, "Move Down", null, () -> moveParameters(parameter, 1))
                .setEnabled(rows.get(rows.size() - 1) < doc.parameters.size() - 1);
        menu.addSeparator();
        item(menu, "Delete", null, () -> deleteItems(DefinitionDialogs.Kind.PARAMETER, rows));
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private void addFormulaItems(JPopupMenu menu, final CellTable table) {
        item(menu, "Fill Series...", "ctrl shift F", () -> fillSeries(table));
        item(menu, "Apply Numerical Expression...", "ctrl shift E", () -> applyExpression(table));
        item(menu, "Apply Text Formula...", "ctrl shift M", () -> applyTextFormula(table));
    }

    private void showDefinitionMenu(CellTable table, MouseEvent event, final int index) {
        if (table == columnTable)
            showColumnMenu(event, index);
        else
            showArrayMenu(event, index);
    }

    private void showColumnMenu(MouseEvent event, final int column) {
        if (doc == null || column < 0 || column >= doc.columns.size())
            return;
        final DefinitionDialogs.Kind kind = DefinitionDialogs.Kind.COLUMN;
        final List<Integer> columns = itemsOrFallback(kind, column);
        final Definition d = doc.columns.get(column);
        JPopupMenu menu = new JPopupMenu();
        item(menu, "Attributes...", null, () -> editAttributesAt(kind, column));
        if (d.canDisplayTime()) {
            JMenu time = new JMenu("Time display");
            time.setToolTipText("Interpret this column as Unix epoch seconds (display only).");
            ButtonGroup group = new ButtonGroup();
            String[] labels = { "Epoch (raw)", "Date/time \u2014 Local", "Date/time \u2014 UTC" };
            Definition.TimeMode[] modes = Definition.TimeMode.values();
            for (int i = 0; i < 3; i++) {
                final Definition.TimeMode mode = modes[i];
                JRadioButtonMenuItem choice = new JRadioButtonMenuItem(labels[i], d.timeMode == mode);
                choice.addActionListener(e -> setTimeDisplay(d, mode));
                group.add(choice);
                time.add(choice);
            }
            menu.add(time);
        }
        menu.addSeparator();
        item(menu, "Plot from file", null, () -> plotData(column, false));
        item(menu, "Sort ascending", null, () -> sortColumn(column, true));
        item(menu, "Sort descending", null, () -> sortColumn(column, false));
        item(menu, "Search/Replace", null, () -> searchItem(columnModel, column));
        menu.addSeparator();
        item(menu, "Filter/View...", "ctrl shift R", this::filterRows);
        item(menu, "Clear Filter/View", null, this::clearRowFilter);
        menu.addSeparator();
        addFormulaItems(menu, columnTable);
        menu.addSeparator();
        item(menu, "Delete", null, () -> deleteItems(kind, columns));
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    void setTimeDisplay(Definition d, Definition.TimeMode mode) {
        d.timeMode = mode;
        timeChoices.put(d.name, mode);
        if (columnModel.getRowCount() > 0)
            columnModel.fireTableRowsUpdated(0, columnModel.getRowCount() - 1);
        columnTable.sizeColumnsToContents();
        columnTable.getTableHeader().repaint();
    }

    public void rowMenu(CellTable table, MouseEvent event, int row) {
        if (row < 0)
            return;
        JPopupMenu menu = new JPopupMenu();
        item(menu, "Insert", null, () -> {
            if (columnTable.getColumnCount() > 0)
                columnTable.changeSelection(row, 0, false, false);
            insertRows();
        });
        item(menu, "Delete", null, () -> {
            if (!columnTable.rowFullySelected(row))
                columnTable.selectRowCells(row, false, false);
            deleteRows();
        });
        menu.addSeparator();
        item(menu, "Filter/View...", "ctrl shift R", this::filterRows);
        item(menu, "Clear Filter/View", null, this::clearRowFilter);
        menu.addSeparator();
        addFormulaItems(menu, columnTable);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private void showArrayMenu(MouseEvent event, final int array) {
        if (doc == null || array < 0 || array >= doc.arrays.size())
            return;
        final DefinitionDialogs.Kind kind = DefinitionDialogs.Kind.ARRAY;
        final List<Integer> arrays = itemsOrFallback(kind, array);
        JPopupMenu menu = new JPopupMenu();
        item(menu, "Attributes...", null, () -> editAttributesAt(kind, array));
        item(menu, "Open Array Viewer...", null, () -> openArrayViewer(array));
        item(menu, "Plot", null, () -> plotData(array, true)).setEnabled(doc.arrays.get(array).isNumeric());
        item(menu, "Search", null, () -> searchItem(arrayModel, array));
        item(menu, "Resize", null, () -> resizeArray(array));
        menu.addSeparator();
        addFormulaItems(menu, arrayTable);
        menu.addSeparator();
        item(menu, "Delete", null, () -> deleteItems(kind, arrays));
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    /* ---- rows ---- */

    private void insertRows() {
        if (doc == null)
            return;
        if (doc.columns.isEmpty()) {
            inform("Insert Rows", "Insert a column before inserting rows.");
            return;
        }
        flushEdits();
        Integer count = EditorDialogs.number(this, "Insert Rows", "Number of rows", lastRowCount, 1, 1000000);
        if (count != null)
            insertRowsAt(count);
    }

    /** Insert rows after the current one, or at the end if there is none. */
    void insertRowsAt(int count) {
        lastRowCount = count;
        Document before = doc.snapshot();
        int pageBefore = page;
        Document.Page p = doc.pages.get(page);
        int current = columnTable.getCurrentRow();
        int position = current >= 0 && current < columnModel.getRowCount() ? columnModel.pageRow(current) + 1 : p.rows;
        for (int c = 0; c < doc.columns.size(); c++) {
            String[] cells = new String[p.rows + count];
            System.arraycopy(p.columns[c], 0, cells, 0, position);
            Arrays.fill(cells, position, position + count, Cells.newCell(doc.columns.get(c)));
            System.arraycopy(p.columns[c], position, cells, position + count, p.rows - position);
            p.columns[c] = cells;
        }
        p.rows += count;
        columnTable.clearCellSelection();
        structuralDone(before, pageBefore, "Insert " + count + " Rows");
    }

    /** Delete rows with a selected cell. Rows hidden by a filter are never touched. */
    void deleteRows() {
        if (doc == null)
            return;
        flushEdits();
        TreeSet<Integer> rows = new TreeSet<Integer>();
        for (int[] cell : columnTable.selectedCells())
            rows.add(columnModel.pageRow(cell[0]));
        if (rows.isEmpty())
            return;
        Document before = doc.snapshot();
        int pageBefore = page;
        Document.Page p = doc.pages.get(page);
        int kept = p.rows - rows.size();
        for (int c = 0; c < doc.columns.size(); c++) {
            String[] cells = new String[kept];
            int to = 0;
            for (int r = 0; r < p.rows; r++)
                if (!rows.contains(r))
                    cells[to++] = p.columns[c][r];
            p.columns[c] = cells;
        }
        p.rows = kept;
        columnTable.clearCellSelection();
        structuralDone(before, pageBefore, "Delete " + rows.size() + " Rows");
    }

    /* The natural order of strcmp_nh in the SDDS toolkit: digit runs compare by length first. */
    static int compareNatural(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
        int i = 0;
        for (; i < a.length && i < b.length; i++) {
            boolean n1 = a[i] >= '0' && a[i] <= '9', n2 = b[i] >= '0' && b[i] <= '9';
            if (n1 && n2) {
                int k = 1;
                while (i + k < a.length && i + k < b.length) {
                    boolean n3 = a[i + k] >= '0' && a[i + k] <= '9', n4 = b[i + k] >= '0' && b[i + k] <= '9';
                    if (n3 && !n4)
                        return 1;
                    if (!n3 && n4)
                        return -1;
                    if (!n3)
                        break;
                    k++;
                }
                if (i + k >= a.length && i + k < b.length && b[i + k] >= '0' && b[i + k] <= '9')
                    return -1;
                if (i + k >= b.length && i + k < a.length && a[i + k] >= '0' && a[i + k] <= '9')
                    return 1;
            }
            if (n1 == n2) {
                if ((a[i] & 0xff) != (b[i] & 0xff))
                    return (a[i] & 0xff) < (b[i] & 0xff) ? -1 : 1;
            } else {
                return n1 ? 1 : -1;
            }
        }
        return Integer.compare(a.length - i, b.length - i);
    }

    private static int compareBytes(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < a.length && i < b.length; i++)
            if (a[i] != b[i])
                return (a[i] & 0xff) - (b[i] & 0xff);
        return a.length - b.length;
    }

    /** Sort the page's rows by one column, keeping equal rows in order. */
    void sortColumn(int column, final boolean ascending) {
        if (doc == null || page() < 0)
            return;
        flushEdits();
        final Document.Page p = doc.pages.get(page);
        final String[] cells = p.columns[column];
        final int type = doc.columns.get(column).type;
        Integer[] order = new Integer[p.rows];
        for (int i = 0; i < order.length; i++)
            order[i] = i;
        Arrays.sort(order, new Comparator<Integer>() {
            public int compare(Integer x, Integer y) {
                String a = cells[x], b = cells[y];
                if (SDDSUtil.isNumericType(type))
                    return Cells.compareNumbers(a, b, ascending);
                int result = type == SDDSUtil.SDDS_ENUM ? compareBytes(a, b)
                        : type == SDDSUtil.SDDS_STRING ? compareNatural(a, b) : a.compareTo(b);
                return ascending ? result : -result;
            }
        });
        boolean reordered = false;
        for (int i = 0; i < order.length; i++)
            reordered = reordered || order[i] != i;
        if (!reordered)
            return;
        Document before = doc.snapshot();
        for (int c = 0; c < p.columns.length; c++) {
            String[] sorted = new String[p.rows];
            for (int i = 0; i < p.rows; i++)
                sorted[i] = p.columns[c][order[i]];
            p.columns[c] = sorted;
        }
        structuralDone(before, page, "Sort Rows");
    }

    /* ---- row filter ---- */

    private void filterRows() {
        if (doc == null) {
            inform("Row Filter", "Load a file first.");
            return;
        }
        if (doc.columns.isEmpty()) {
            inform("Row Filter", "No columns available to filter.");
            return;
        }
        flushEdits();
        String expression = rowFilterExpression;
        if (expression.isEmpty()) {
            int current = columnTable.getCurrentColumn();
            if (current < 0 && !columnTable.selectedColumnSet().isEmpty())
                current = columnTable.selectedColumnSet().first();
            expression = current >= 0 ? RowFilter.defaultExpression(doc.columns.get(current).name) : lastRowFilterExpression;
        }
        EditorDialogs.FilterChoice choice = EditorDialogs.filter(this, expression);
        if (choice == null)
            return;
        if (choice.cleared || choice.expression.isEmpty()) {
            clearRowFilter();
            return;
        }
        setRowFilter(choice.expression);
    }

    /** Show only the rows that pass an expression. */
    void setRowFilter(String expression) {
        flushEdits();
        rowFilterExpression = expression;
        lastRowFilterExpression = expression;
        rowFilterActive = true;
        refreshRowFilter(true);
    }

    /** Show all rows again. */
    void clearRowFilter() {
        flushEdits();
        rowFilterActive = false;
        rowFilterExpression = "";
        refreshRowFilter(false);
        message("Row filter cleared");
    }

    private void setVisibleRows(int[] visible) {
        int[] oldRows = new int[columnModel.getRowCount()];
        for (int row = 0; row < oldRows.length; row++)
            oldRows[row] = columnModel.pageRow(row);
        int lead = columnTable.getCurrentRow(), leadColumn = columnTable.getCurrentColumn();
        columnModel.setVisibleRows(visible);
        columnModel.fireTableDataChanged();
        int[] map = new int[oldRows.length];
        for (int row = 0; row < map.length; row++)
            map[row] = columnModel.viewRow(oldRows[row]);
        columnTable.remapRows(map, lead, leadColumn);
        columnRows.revalidate();
        columnRows.repaint();
    }

    private void refreshRowFilter(boolean showErrors) {
        int total = columnModel.pageRowCount();
        if (!rowFilterActive || rowFilterExpression.trim().isEmpty() || page() < 0) {
            if (columnModel.filtered())
                setVisibleRows(null);
            visibleRows = total;
        } else {
            try {
                boolean[] pass = RowFilter.evaluate(rowFilterExpression, doc, page);
                int count = 0;
                for (boolean row : pass)
                    if (row)
                        count++;
                int[] visible = new int[count];
                int to = 0;
                for (int r = 0; r < pass.length; r++)
                    if (pass[r])
                        visible[to++] = r;
                boolean same = columnModel.filtered() && columnModel.getRowCount() == count;
                for (int r = 0; same && r < count; r++)
                    same = columnModel.pageRow(r) == visible[r];
                if (!same)
                    setVisibleRows(visible);
                visibleRows = count;
                if (showErrors)
                    message("Row filter active: " + count + "/" + total + " rows visible");
            } catch (RowFilter.Failure e) {
                rowFilterActive = false;
                setVisibleRows(null);
                visibleRows = total;
                updateFilterIndicator();
                if (showErrors)
                    warn("Row Filter", e.getMessage());
                message("Row filter disabled: " + e.getMessage());
                return;
            }
        }
        if (searchDialog != null)
            searchDialog.invalidateMatches();
        updateFilterIndicator();
    }

    /* ---- column search ---- */

    void clearColumnSelection() {
        flushEdits();
        columnTable.clearCellSelection();
        updateColumnSearchScope();
    }

    private void updateColumnSearchScope() {
        if (selectingSearchMatch)
            return;
        searchMatchRow = -1;
        searchColumns = new ArrayList<Integer>(columnTable.selectedColumnSet());
        if (searchColumns.size() == 1 && doc != null)
            columnSearch.placeholder = "Find in " + doc.columns.get(searchColumns.get(0)).name + "...";
        else
            columnSearch.placeholder = searchColumns.isEmpty() ? "Find in all columns..." : "Find in selected columns...";
        columnSearch.repaint();
    }

    /** Select successive matches in row order, wrapping within the search scope. */
    void findInColumnPanel() {
        String needle = columnSearch.getText().toLowerCase(java.util.Locale.ROOT);
        int rows = columnModel.getRowCount();
        if (needle.isEmpty() || doc == null || rows <= 0 || columnModel.getColumnCount() <= 0)
            return;
        flushEdits();
        List<Integer> columns = searchColumns;
        if (columns.isEmpty()) {
            columns = new ArrayList<Integer>();
            for (int c = 0; c < columnModel.getColumnCount(); c++)
                columns.add(c);
        }
        int startRow = searchMatchRow >= 0 ? searchMatchRow : 0;
        int startColumn = searchMatchRow >= 0 ? columns.indexOf(searchMatchColumn) : -1;
        for (long offset = 0; offset <= rows; offset++) {
            int row = (int) ((startRow + offset) % rows);
            int first = offset == 0 ? startColumn + 1 : 0;
            int end = offset == rows ? startColumn + 1 : columns.size();
            for (int c = first; c < end; c++) {
                int column = columns.get(c);
                if (columnModel.searchText(row, column).toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    selectingSearchMatch = true;
                    columnTable.selectCell(row, column);
                    selectingSearchMatch = false;
                    searchMatchRow = row;
                    searchMatchColumn = column;
                    centerOn(columnTable, row, column);
                    lastTable = columnTable;
                    updateStatus();
                    return;
                }
            }
        }
        message("\"" + columnSearch.getText() + "\" not found in the searched columns");
    }

    private static void centerOn(CellTable table, int row, int column) {
        Rectangle cell = table.getCellRect(row, column, true);
        Rectangle view = table.getVisibleRect();
        cell.y = Math.max(0, cell.y + cell.height / 2 - view.height / 2);
        cell.height = view.height;
        table.scrollRectToVisible(cell);
    }

    /* ---- search and replace ---- */

    private void searchItem(GridModels.Base model, int item) {
        flushEdits();
        if (searchDialog != null)
            searchDialog.dispose();
        boolean array = model == arrayModel;
        final EditorDialogs.SearchReplace dialog = new EditorDialogs.SearchReplace(this, this, model, item,
                array ? "Search Array" : "Search Column", array, !array);
        searchDialog = dialog;
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                if (searchDialog == dialog)
                    searchDialog = null;
            }
        });
        dialog.setVisible(true);
    }

    public void focusMatch(GridModels.Base model, int viewRow, int item, int start, int length) {
        CellTable table = model == columnModel ? columnTable : arrayTable;
        table.removeEditor();
        table.selectCell(viewRow, item);
        centerOn(table, viewRow, item);
        Definition d = model.definitions().get(item);
        if (!d.isEnum())
            table.editAndSelect(viewRow, item, start, length);
    }

    public void closeMatchEditor(GridModels.Base model) {
        (model == columnModel ? columnTable : arrayTable).removeEditor();
    }

    public int[] selectedRows(GridModels.Base model, int item) {
        CellTable table = model == columnModel ? columnTable : arrayTable;
        List<Integer> rows = new ArrayList<Integer>();
        for (int[] cell : table.selectedCells())
            if (cell[1] == item)
                rows.add(cell[0]);
        int[] out = new int[rows.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = rows.get(i);
        return out;
    }

    /* ---- arrays ---- */

    private void resizeArray(int array) {
        flushEdits();
        Document.ArrayCells cells = doc.pages.get(page).arrays[array];
        Definition d = doc.arrays.get(array);
        int[] dims = EditorDialogs.dimensions(this, cells.dims, d.dimensions);
        if (dims != null)
            resizeArrayTo(array, dims);
    }

    /** Give an array new dimensions on this page, keeping elements in their places. */
    void resizeArrayTo(int array, int[] dims) {
        Document.ArrayCells cells = doc.pages.get(page).arrays[array];
        Definition d = doc.arrays.get(array);
        int size = Document.product(dims);
        if (size < 0 || Document.shapeTooLarge(dims)) {
            warn("SDDS", "Array dimensions are too large.");
            return;
        }
        if (Arrays.equals(dims, cells.dims) && size == cells.values.length)
            return;
        Document before = doc.snapshot();
        cells.values = Document.reshape(cells.values, cells.dims, dims, Cells.newCell(d));
        cells.dims = dims;
        structuralDone(before, page, "Resize Array");
    }

    void openCurrentArrayViewer() {
        if (doc == null)
            return;
        int column = arrayTable.getCurrentColumn();
        if (column < 0 && !doc.arrays.isEmpty())
            column = 0;
        openArrayViewer(column);
    }

    /** Open a new viewer on an array. Several can show different slices of one array. */
    void openArrayViewer(int array) {
        if (doc == null || array < 0 || array >= doc.arrays.size())
            return;
        flushEdits();
        ArrayViewer viewer = new ArrayViewer(this, array);
        viewer.getRootPane().registerKeyboardAction(e -> saveFile(), KeyStroke.getKeyStroke("ctrl S"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        viewers.add(viewer);
        viewer.setVisible(true);
    }

    /** The open array viewers. */
    List<ArrayViewer> viewers() {
        return viewers;
    }

    /* ---- plotting ---- */

    private void plotCurrent() {
        CellTable table = focusedTable();
        if (table == null)
            table = lastTable;
        if (table == null || table == parameterTable || table.getCurrentRow() < 0 || table.getCurrentColumn() < 0) {
            message("Select a cell in the column or array to plot");
            return;
        }
        plotData(table.getCurrentColumn(), table == arrayTable);
    }

    /** The arguments of sddsplot for one column or array of a snapshot file. */
    /** The program that plots. Tests name a stand-in for it. */
    static String plotProgram = "sddsplot";

    /**
     * Copies of the document that plots read. sddsplot hands a plot to mpl_qt and exits, and
     * mpl_qt runs sddsplot on the copy again to zoom, so a copy must outlast sddsplot. Each
     * stays until no running program names it, or the editor closes.
     */
    static final List<Path> plotSnapshots = Collections.synchronizedList(new ArrayList<Path>());
    private static boolean plotCleanupSet;

    static List<String> plotArguments(String file, String name, boolean array, boolean hasTime) {
        StringBuilder literal = new StringBuilder();
        for (char c : name.toCharArray()) {
            /* sddsplot reads these as wildcards; escaped, the name matches only itself. */
            if (c == '*' || c == '?' || c == '[' || c == ']')
                literal.append('\\');
            literal.append(c);
        }
        List<String> args = new ArrayList<String>(Arrays.asList(plotProgram, "-split=page", "-sep=page", file));
        if (array) {
            args.add("-arraynames=" + literal);
        } else if (hasTime) {
            args.add("-col=Time," + literal);
            args.add("-tick=xtime");
        } else {
            args.add("-col=" + literal);
        }
        return args;
    }

    void plotData(int index, boolean array) {
        if (doc == null)
            return;
        flushEdits();
        Definition d = (array ? doc.arrays : doc.columns).get(index);
        if (array && !d.isNumeric()) {
            message("Select a numeric array to plot");
            return;
        }
        sweepPlotSnapshots();
        Path file;
        try {
            Path snapshot = Files.createTempDirectory("sddsedit-plot");
            keepPlotSnapshot(snapshot);
            file = snapshot.resolve("plot.sdds");
            doc.writeTo(file.toString(), true);
        } catch (IOException | Document.Failure e) {
            sweepPlotSnapshots();
            warn("SDDS", e.getMessage());
            return;
        }
        boolean hasTime = false;
        for (Definition column : doc.columns)
            hasTime = hasTime || column.name.equals("Time");
        try {
            new ProcessBuilder(plotArguments(file.toString(), d.name, array, hasTime)).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            sweepPlotSnapshots();
            warn("SDDS", "Failed to start sddsplot");
        }
    }

    private static void keepPlotSnapshot(Path directory) {
        plotSnapshots.add(directory);
        if (!plotCleanupSet) {
            plotCleanupSet = true;
            Runtime.getRuntime().addShutdownHook(new Thread(sddsEdit::deletePlotSnapshots, "plot snapshot cleanup"));
        }
    }

    /** Delete the snapshots that no running program names. */
    static void sweepPlotSnapshots() {
        List<String> running = new ArrayList<String>();
        ProcessHandle.allProcesses().forEach(p -> p.info().arguments().ifPresent(a -> running.add(String.join(" ", a))));
        synchronized (plotSnapshots) {
            for (Iterator<Path> snapshots = plotSnapshots.iterator(); snapshots.hasNext();) {
                Path directory = snapshots.next();
                boolean used = false;
                for (String line : running)
                    used = used || line.contains(directory.toString());
                if (!used) {
                    deleteTree(directory);
                    snapshots.remove();
                }
            }
        }
    }

    /** Delete every snapshot. A plot left open can still be viewed, but can no longer zoom. */
    static void deletePlotSnapshots() {
        synchronized (plotSnapshots) {
            for (Path directory : plotSnapshots)
                deleteTree(directory);
            plotSnapshots.clear();
        }
    }

    /* ---- pages ---- */

    void clonePage() {
        if (doc == null || page() < 0)
            return;
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        doc.pages.add(page + 1, doc.pages.get(page).copy());
        page++;
        structuralDone(before, pageBefore, "Clone Page");
    }

    void insertPage() {
        ensureDocument();
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        Document.Page fresh = new Document.Page();
        Document.Page current = page() >= 0 ? doc.pages.get(page) : null;
        fresh.parameters = new String[doc.parameters.size()];
        for (int i = 0; i < fresh.parameters.length; i++) {
            Definition d = doc.parameters.get(i);
            fresh.parameters[i] = d.fixedValue != null ? d.fixedValue : Cells.newCell(d);
        }
        fresh.rows = current == null ? 0 : current.rows;
        fresh.columns = new String[doc.columns.size()][];
        for (int c = 0; c < fresh.columns.length; c++) {
            fresh.columns[c] = new String[fresh.rows];
            Arrays.fill(fresh.columns[c], Cells.newCell(doc.columns.get(c)));
        }
        fresh.arrays = new Document.ArrayCells[doc.arrays.size()];
        for (int a = 0; a < fresh.arrays.length; a++) {
            Definition d = doc.arrays.get(a);
            int[] dims;
            if (current != null) {
                dims = current.arrays[a].dims.clone();
            } else {
                dims = new int[d.dimensions];
                Arrays.fill(dims, 1);
            }
            int size = Document.product(dims);
            if (size < 0) {
                warn("SDDS", "Array dimensions are too large.");
                return;
            }
            String[] values = new String[size];
            Arrays.fill(values, Cells.newCell(d));
            fresh.arrays[a] = new Document.ArrayCells(values, dims);
        }
        int position = page() >= 0 ? page + 1 : 0;
        doc.pages.add(position, fresh);
        page = position;
        structuralDone(before, pageBefore, "Insert Page");
    }

    void deletePage() {
        if (doc == null || doc.pages.size() <= 1)
            return;
        flushEdits();
        Document before = doc.snapshot();
        int pageBefore = page;
        doc.pages.remove(page);
        page = Math.min(page, doc.pages.size() - 1);
        structuralDone(before, pageBefore, "Delete Page");
    }

    /* ---- program ---- */

    private static boolean reportingFailure;

    /** Say that something went wrong, because a program started from a desktop has no visible error stream. */
    static void reportFailure(Throwable error) {
        final String text = error instanceof OutOfMemoryError
                ? "Java ran out of memory, so the last action did not finish.\n"
                        + "Save your work to a new file, then start the editor again with a larger -Xmx setting."
                : "An internal error stopped the last action:\n" + error;
        error.printStackTrace();
        if (quietDialogs) {
            lastWarning = text;
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (reportingFailure)
                return;
            reportingFailure = true;
            try {
                JOptionPane.showMessageDialog(null, text, "SDDS", JOptionPane.ERROR_MESSAGE);
            } catch (RuntimeException | Error dialogFailed) {
                /* The error is already on stderr. Reporting this one too could repeat forever. */
            } finally {
                reportingFailure = false;
            }
        });
    }

    /** Stop with a clear message if there is no display to open a window on. */
    private static void requireDisplay() {
        String problem = null;
        try {
            if (GraphicsEnvironment.isHeadless())
                problem = "none is available";
            else
                GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        } catch (HeadlessException | AWTError | LinkageError e) {
            problem = e.getMessage() != null ? e.getMessage().trim() : e.toString();
        }
        if (problem != null) {
            System.err.println("SDDSedit needs a graphical display: " + problem);
            System.err.println("Check that DISPLAY is set, or connect with ssh -X.");
            System.exit(1);
        }
    }

    /**
     * Run the editor.
     *
     * @param args optionally the name of a file to open
     */
    public static void main(final String[] args) {
        standalone = true;
        requireDisplay();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> reportFailure(error));
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Theme.install();
                sddsEdit editor = new sddsEdit(args);
                editor.setVisible(true);
                if (args.length >= 1)
                    editor.loadFile(args[0]);
            }
        });
    }
}
