/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.View;

import SDDS.java.SDDS.SDDSUtil;
import SDDS.java.SDDSedit.DefinitionDialogs.Kind;
import SDDS.java.SDDSedit.DefinitionDialogs.TextBox;

/**
 * Setup for File &gt; New. Parameters, columns and arrays each get a card like the main
 * window's panels, with one row of fields per definition. Problems are reported beside
 * the buttons while typing, and Create stays disabled until every definition can be
 * written.
 */
final class NewFileDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    /** Largest column row count or array element count offered for a new file. */
    private static final int MAX_ELEMENTS = 1000000;
    private static final int MAX_NAME_BYTES = 512;
    private static final int HEADER_HEIGHT = 38;
    /** In proportion to the 3:2:2 stretch of the fields, so that they line up from card to card. */
    private static final int[] FIELD_WIDTHS = { 150, 0, 100, 100, 0 };
    private static final double[] FIELD_WEIGHTS = { 3, 0, 2, 2, 0 };

    private static final class Row {
        TextBox name;
        JComboBox<String> type;
        TextBox units;
        TextBox detail; // Parameter value or array shape; null for columns.
        Flat.ToolButton remove;
    }

    private static final class Section {
        final Kind kind;
        Flat.ToolButton add;
        Flat.Chip count;
        JComponent rule;
        JPanel body;
        final List<Row> rows = new ArrayList<Row>();

        Section(Kind kind) {
            this.kind = kind;
        }
    }

    /** The cards never scroll sideways, and fill the view when they are shorter than it. */
    private static final class Cards extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        Cards() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }

        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /** Text that wraps to its width and asks for only the height that takes. */
    private static final class Status extends JTextArea {
        private static final long serialVersionUID = 1L;

        Status() {
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(BorderFactory.createEmptyBorder());
            setFont(Theme.uiFont());
        }

        public Dimension getPreferredSize() {
            View root = getUI().getRootView(this);
            root.setSize(getWidth() > 0 ? getWidth() : Short.MAX_VALUE, Short.MAX_VALUE);
            return new Dimension(100, (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)));
        }
    }

    private final Section[] sections = new Section[Kind.values().length];
    private final TextBox descriptionEdit = new TextBox("");
    private final JSpinner rowsBox = DefinitionDialogs.spinner(1, 0, MAX_ELEMENTS);
    private final Cards cards = new Cards();
    private final Status status = new Status();
    private final JButton createButton = Flat.primaryButton("Create");
    private final JScrollPane scroll;
    private String problem;
    private boolean accepted;

    private NewFileDialog(Frame owner) {
        super(owner, "New SDDS File", Dialog.ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(Flat.pad(14, 16, 12, 16));

        JPanel descriptionRow = new JPanel(new BorderLayout(8, 0));
        JLabel descriptionLabel = new JLabel("Description");
        descriptionLabel.setLabelFor(descriptionEdit);
        descriptionEdit.setPlaceholder("Optional; saved in the file header");
        descriptionRow.add(descriptionLabel, BorderLayout.WEST);
        descriptionRow.add(descriptionEdit, BorderLayout.CENTER);
        content.add(descriptionRow, BorderLayout.NORTH);

        for (Kind kind : Kind.values())
            buildSection(kind);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = Kind.values().length;
        c.weighty = 1;
        cards.add(Box.createGlue(), c);
        scroll = Flat.scrollPane(new JScrollPane(cards, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        scroll.getViewport().setBackground(Theme.t.win);
        content.add(scroll, BorderLayout.CENTER);

        JButton cancel = Flat.button("Cancel");
        cancel.addActionListener(e -> dispose());
        createButton.addActionListener(e -> {
            DefinitionDialogs.commit(rowsBox);
            if (check()) {
                accepted = true;
                dispose();
            }
        });
        JPanel footer = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.SOUTH;
        c.insets = new Insets(0, 0, 0, 12);
        footer.add(status, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.anchor = GridBagConstraints.SOUTH;
        footer.add(DefinitionDialogs.buttonRow(cancel, createButton), c);
        content.add(footer, BorderLayout.SOUTH);
        status.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                status.revalidate();
            }
        });

        onChange(descriptionEdit);
        rowsBox.addChangeListener(e -> check());
        setContentPane(content);
        Flat.dialogKeys(this, createButton);

        for (Section section : sections)
            relayout(section);
        final Row first = addDefinition(sections[Kind.COLUMN.ordinal()]);
        addWindowListener(new WindowAdapter() {
            /* Opening in a window of its own clears focus, so put the cursor back in the newest name. */
            public void windowOpened(WindowEvent e) {
                first.name.requestFocusInWindow();
                first.name.selectAll();
            }
        });

        pack();
        /* Wide enough for comfortable names and units, and tall enough for the starting definitions. */
        int fontHeight = getFontMetrics(Theme.uiFont()).getHeight();
        setSize(Math.max(getWidth(), 44 * fontHeight), getHeight());
        /* The cards never scroll sideways, so the window must keep their headers whole. */
        Insets frame = getInsets();
        setMinimumSize(new Dimension(cards.getMinimumSize().width + scroll.getVerticalScrollBar().getPreferredSize().width
                + 32 + frame.left + frame.right, 240));
    }

    /**
     * Ask for the layout of a new file.
     *
     * @param owner the window the dialog belongs to
     * @return a new document with one page and no path, or <code>null</code> if cancelled
     */
    static Document show(java.awt.Frame owner) {
        NewFileDialog dialog = new NewFileDialog(owner);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return dialog.accepted ? dialog.build() : null;
    }

    /* ---- building ---- */

    private void onChange(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                check();
            }

            public void removeUpdate(DocumentEvent e) {
                check();
            }

            public void changedUpdate(DocumentEvent e) {
                check();
            }
        });
    }

    private static JComponent bare(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private void buildSection(Kind kind) {
        final Section section = new Section(kind);
        sections[kind.ordinal()] = section;

        JPanel header = new JPanel() {
            private static final long serialVersionUID = 1L;

            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, HEADER_HEIGHT);
            }
        };
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(Flat.pad(0, 12, 0, 8));
        String[] titles = { "Parameters", "Columns", "Arrays" };
        String[] hints = { "One value per page", "Rows of tabular data", "Multidimensional data" };
        String[] addTips = { "Add a parameter", "Add a column", "Add an array" };
        JLabel title = new JLabel(titles[kind.ordinal()]);
        title.setFont(Theme.bold(Theme.uiFont()));
        header.add(title);
        header.add(Box.createHorizontalStrut(8));
        section.count = new Flat.Chip();
        header.add(section.count);
        header.add(Box.createHorizontalStrut(8));
        JLabel hint = Flat.muted(hints[kind.ordinal()]);
        hint.setMinimumSize(new Dimension(0, 0)); // The first thing to give way.
        hint.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        header.add(hint);
        if (kind == Kind.COLUMN) {
            JLabel rowsLabel = new JLabel("Rows");
            rowsLabel.setLabelFor(rowsBox);
            rowsBox.setToolTipText("Number of rows in every column");
            rowsBox.setMaximumSize(rowsBox.getPreferredSize());
            header.add(Box.createHorizontalStrut(8));
            header.add(rowsLabel);
            header.add(Box.createHorizontalStrut(8));
            header.add(rowsBox);
            header.add(Box.createHorizontalStrut(4));
        }
        header.add(Box.createHorizontalStrut(8));
        section.add = Flat.panelAction("Add " + kind.word, Icons.PLUS, addTips[kind.ordinal()]);
        section.add.setFocusable(true);
        section.add.addActionListener(e -> addDefinition(section));
        header.add(section.add);

        section.rule = Flat.rule(false);
        JComponent top = bare(new BorderLayout());
        top.add(header, BorderLayout.CENTER);
        top.add(section.rule, BorderLayout.SOUTH);

        section.body = new JPanel();
        GridBagLayout grid = new GridBagLayout();
        grid.columnWidths = FIELD_WIDTHS;
        grid.columnWeights = FIELD_WEIGHTS;
        section.body.setLayout(grid);
        section.body.setOpaque(false);
        section.body.setBorder(Flat.pad(8, 12, 10, 8));

        Flat.Card card = new Flat.Card(new BorderLayout(), Theme.t.surface, Theme.t.border, 16);
        card.add(top, BorderLayout.NORTH);
        card.add(section.body, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = kind.ordinal();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, kind == Kind.ARRAY ? 0 : 10, 0);
        cards.add(card, c);
    }

    /** Fields take their width from the grid alone, so that their text does not move the columns. */
    private static <T extends JComponent> T sized(T field) {
        field.setPreferredSize(new Dimension(1, field.getPreferredSize().height));
        return field;
    }

    /** Append a definition with an unused name and put the cursor in its name. */
    private Row addDefinition(final Section section) {
        final Kind kind = section.kind;
        String name = null;
        for (int suffix = 1; name == null; suffix++) {
            name = kind.title + suffix;
            for (Row row : section.rows)
                if (row.name.getText().equals(name))
                    name = null;
        }

        final Row row = new Row();
        row.name = sized(new TextBox(name));
        String[] typeNames = new String[DefinitionDialogs.TYPES.length];
        for (int i = 0; i < typeNames.length; i++)
            typeNames[i] = SDDSUtil.getTypeName(DefinitionDialogs.TYPES[i]);
        row.type = Flat.combo(new JComboBox<String>(typeNames), true);
        row.type.setSelectedItem(SDDSUtil.getTypeName(kind == Kind.PARAMETER ? SDDSUtil.SDDS_STRING : SDDSUtil.SDDS_DOUBLE));
        row.units = sized(new TextBox(""));
        if (kind == Kind.PARAMETER) {
            row.detail = sized(new TextBox(""));
            row.detail.setPlaceholder(placeholderForType(row));
        } else if (kind == Kind.ARRAY) {
            row.detail = sized(new TextBox("5"));
            row.detail.setPlaceholder("5 or 3x4");
        }
        row.remove = new Flat.ToolButton(null, Icons.CLOSE.icon(14, Theme.t.muted), "Remove this " + kind.word);
        row.remove.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        row.remove.radius = 10;
        row.remove.setFocusable(true);

        onChange(row.name);
        onChange(row.units);
        if (row.detail != null)
            onChange(row.detail);
        row.type.addActionListener(e -> {
            if (kind == Kind.PARAMETER)
                row.detail.setPlaceholder(placeholderForType(row));
            check();
        });
        row.remove.addActionListener(e -> removeDefinition(section, row));

        section.rows.add(row);
        relayout(section);
        row.name.requestFocusInWindow();
        row.name.selectAll();
        SwingUtilities.invokeLater(() -> {
            if (row.name.getParent() != null) {
                growToFit();
                validate();
                row.name.scrollRectToVisible(new Rectangle(0, 0, row.name.getWidth(), row.name.getHeight()));
            }
        });
        return row;
    }

    private void removeDefinition(Section section, Row row) {
        int index = section.rows.indexOf(row);
        section.rows.remove(row);
        relayout(section);
        if (!section.rows.isEmpty())
            section.rows.get(Math.min(index, section.rows.size() - 1)).name.requestFocusInWindow();
        else
            section.add.requestFocusInWindow();
    }

    private static GridBagConstraints cell(int x, int y, int width, int right, int below) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, below, right);
        return c;
    }

    /** Place the rows in order below the headings, and show the card body only when it has rows. */
    private void relayout(Section section) {
        Kind kind = section.kind;
        JPanel body = section.body;
        body.removeAll();
        boolean any = !section.rows.isEmpty();
        int gap = 8, below = 6;
        body.add(Flat.muted("Name"), cell(0, 0, 1, gap, below));
        body.add(Flat.muted("Type"), cell(1, 0, 1, gap, below));
        body.add(Flat.muted("Units"), cell(2, 0, kind == Kind.COLUMN ? 2 : 1, gap, below));
        if (kind != Kind.COLUMN)
            body.add(Flat.muted(kind == Kind.PARAMETER ? "Initial value" : "Shape"), cell(3, 0, 1, gap, below));
        for (int i = 0; i < section.rows.size(); i++) {
            Row row = section.rows.get(i);
            int line = i + 1;
            int last = i == section.rows.size() - 1 ? 0 : below;
            body.add(row.name, cell(0, line, 1, gap, last));
            body.add(row.type, cell(1, line, 1, gap, last));
            body.add(row.units, cell(2, line, row.detail != null ? 1 : 2, gap, last));
            if (row.detail != null)
                body.add(row.detail, cell(3, line, 1, gap, last));
            body.add(row.remove, cell(4, line, 1, 0, last));
        }
        section.rule.setVisible(any);
        body.setVisible(any);
        section.count.setText(String.valueOf(section.rows.size()));
        if (kind == Kind.COLUMN)
            rowsBox.setEnabled(any);
        cards.revalidate();
        cards.repaint();
        check();
    }

    /** Grow the window with its contents, up to a comfortable height, before it scrolls. */
    private void growToFit() {
        if (!isShowing())
            return;
        int needed = cards.getPreferredSize().height - scroll.getViewport().getHeight();
        Rectangle screen = getGraphicsConfiguration().getBounds();
        int limit = Math.min(44 * getFontMetrics(Theme.uiFont()).getHeight(), screen.height * 9 / 10);
        limit = Math.max(limit, getHeight());
        if (needed > 0 && getHeight() < limit) {
            setSize(getWidth(), Math.min(getHeight() + needed, limit));
            /* Keep the buttons on the screen. */
            int bottom = getY() + getHeight() - (screen.y + screen.height);
            if (bottom > 0)
                setLocation(getX(), Math.max(screen.y, getY() - bottom));
        }
    }

    /* ---- checking ---- */

    private static int typeOf(Row row) {
        return SDDSUtil.identifyType((String) row.type.getSelectedItem());
    }

    private static String placeholderForType(Row row) {
        int type = typeOf(row);
        return type == SDDSUtil.SDDS_STRING ? "empty" : type == SDDSUtil.SDDS_CHARACTER ? "space" : "0";
    }

    private static String counted(int n, String one, String many) {
        return n == 1 ? one : n + " " + many;
    }

    private String summary() {
        List<String> parts = new ArrayList<String>();
        int parameters = sections[Kind.PARAMETER.ordinal()].rows.size();
        int columns = sections[Kind.COLUMN.ordinal()].rows.size();
        int arrays = sections[Kind.ARRAY.ordinal()].rows.size();
        if (parameters > 0)
            parts.add(counted(parameters, "1 parameter", "parameters"));
        if (columns > 0)
            parts.add(counted(columns, "1 column", "columns") + " \u00d7 "
                    + counted(DefinitionDialogs.value(rowsBox), "1 row", "rows"));
        if (arrays > 0)
            parts.add(counted(arrays, "1 array", "arrays"));
        if (parts.isEmpty())
            return "Creates an empty file with one page.";
        return "Creates one page with " + String.join(", ", parts) + ".";
    }

    /** Read sizes such as "5", "3x4" or "2, 3, 4". */
    private static int[] parseShape(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty())
            return null;
        String[] parts = trimmed.split("\\s*[xX*,\u00d7]\\s*|\\s+", -1);
        int[] dims = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                dims[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (dims[i] < 0)
                return null;
        }
        return dims;
    }

    private void mark(JComponent field, String message) {
        boolean invalid = message != null;
        if (Boolean.TRUE.equals(field.getClientProperty("invalid")) != invalid) {
            field.putClientProperty("invalid", Boolean.valueOf(invalid));
            field.repaint();
        }
        field.setToolTipText(message);
        if (invalid && problem == null)
            problem = message;
    }

    /** Mark fields that cannot be saved and report the first problem; true when Create may proceed. */
    private boolean check() {
        problem = null;
        String cannotSave = "contains characters that cannot be saved in this system's encoding";
        mark(descriptionEdit,
                DefinitionDialogs.encodable(descriptionEdit.getText()) ? null : "The description " + cannotSave);
        for (Section section : sections) {
            Kind kind = section.kind;
            Set<String> names = new HashSet<String>();
            for (int i = 0; i < section.rows.size(); i++) {
                Row row = section.rows.get(i);
                String name = row.name.getText();
                String nameProblem = null;
                if (name.isEmpty())
                    nameProblem = "Enter a name for " + kind.word + " " + (i + 1);
                else if (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES)
                    nameProblem = "The name of " + kind.word + " " + (i + 1) + " is longer than " + MAX_NAME_BYTES + " bytes";
                else if (!SDDSUtil.isValidName(name))
                    nameProblem = "\u201c" + name + "\u201d is not a valid " + kind.word + " name";
                else if (names.contains(name))
                    nameProblem = "More than one " + kind.word + " is named \u201c" + name + "\u201d";
                names.add(name);
                mark(row.name, nameProblem);
                mark(row.units, DefinitionDialogs.encodable(row.units.getText()) ? null
                        : "The units of \u201c" + name + "\u201d " + cannotSave);
                if (kind == Kind.PARAMETER) {
                    int type = typeOf(row);
                    String value = row.detail.getText();
                    boolean good = Cells.valid(value, type) && (type != SDDSUtil.SDDS_STRING || DefinitionDialogs.encodable(value));
                    mark(row.detail, good ? null : "\u201c" + value + "\u201d is not a valid " + SDDSUtil.getTypeName(type)
                            + " value for \u201c" + name + "\u201d");
                } else if (kind == Kind.ARRAY) {
                    int[] dims = parseShape(row.detail.getText());
                    String shapeProblem = null;
                    if (dims == null)
                        shapeProblem = "Enter the shape of \u201c" + name + "\u201d as sizes such as 5 or 3x4";
                    else if (Document.product(dims) < 0 || Document.product(dims) > MAX_ELEMENTS)
                        shapeProblem = "\u201c" + name + "\u201d can have at most " + MAX_ELEMENTS + " elements";
                    mark(row.detail, shapeProblem);
                }
            }
        }
        status.setText(problem == null ? summary() : problem);
        status.setForeground(problem == null ? Theme.t.muted : Theme.t.warn);
        status.revalidate();
        createButton.setEnabled(problem == null);
        return problem == null;
    }

    /* ---- the result ---- */

    /** Value given to new elements when no value is entered. */
    private static String defaultValue(int type) {
        return type == SDDSUtil.SDDS_STRING ? "" : type == SDDSUtil.SDDS_CHARACTER ? " " : "0";
    }

    private Document build() {
        Document doc = new Document();
        String description = descriptionEdit.getText();
        doc.description = description.isEmpty() ? null : description;
        Document.Page page = new Document.Page();
        List<Row> columns = sections[Kind.COLUMN.ordinal()].rows;
        page.rows = columns.isEmpty() ? 0 : DefinitionDialogs.value(rowsBox);
        page.parameters = new String[sections[Kind.PARAMETER.ordinal()].rows.size()];
        page.columns = new String[columns.size()][];
        page.arrays = new Document.ArrayCells[sections[Kind.ARRAY.ordinal()].rows.size()];
        for (Section section : sections) {
            for (int i = 0; i < section.rows.size(); i++) {
                Row row = section.rows.get(i);
                Definition d = new Definition(row.name.getText(), typeOf(row));
                String units = row.units.getText();
                d.units = units.isEmpty() ? null : units;
                String value = defaultValue(d.type);
                switch (section.kind) {
                    case PARAMETER:
                        String text = row.detail.getText();
                        if (!text.isEmpty())
                            value = d.isNumeric() ? text.trim() : text;
                        page.parameters[i] = value;
                        doc.parameters.add(d);
                        break;
                    case COLUMN:
                        page.columns[i] = new String[page.rows];
                        Arrays.fill(page.columns[i], value);
                        doc.columns.add(d);
                        break;
                    default:
                        int[] dims = parseShape(row.detail.getText());
                        String[] values = new String[Document.product(dims)];
                        Arrays.fill(values, value);
                        page.arrays[i] = new Document.ArrayCells(values, dims);
                        d.dimensions = dims.length;
                        doc.arrays.add(d);
                }
            }
        }
        doc.pages.add(page);
        return doc;
    }
}
