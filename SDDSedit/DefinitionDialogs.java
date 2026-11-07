/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSpinnerUI;

import SDDS.java.SDDS.SDDSEnumDefinition;
import SDDS.java.SDDS.SDDSUtil;

/**
 * The dialogs that edit parameter, column and array definitions. Each returns true if
 * it changed the document, and changes nothing until the user has accepted and every
 * check has passed.
 */
final class DefinitionDialogs {
    /** The three kinds of definition. */
    enum Kind {
        PARAMETER("parameter", "Parameter"), COLUMN("column", "Column"), ARRAY("array", "Array");

        final String word;
        final String title;

        Kind(String word, String title) {
            this.word = word;
            this.title = title;
        }

        List<Definition> list(Document doc) {
            return this == PARAMETER ? doc.parameters : this == COLUMN ? doc.columns : doc.arrays;
        }
    }

    /** The types a user can choose for a definition, in the order the editor offers them. */
    static final int[] TYPES = { SDDSUtil.SDDS_SHORT, SDDSUtil.SDDS_USHORT, SDDSUtil.SDDS_LONG, SDDSUtil.SDDS_ULONG,
            SDDSUtil.SDDS_LONG64, SDDSUtil.SDDS_ULONG64, SDDSUtil.SDDS_FLOAT, SDDSUtil.SDDS_DOUBLE,
            SDDSUtil.SDDS_LONGDOUBLE, SDDSUtil.SDDS_STRING, SDDSUtil.SDDS_CHARACTER };

    private static final int NEW_LENGTH_LIMIT = 1000000;
    private static final int MAX_DIMENSIONS = 1000000;
    private static final int NEW_ARRAY_SIZE = 5;

    private DefinitionDialogs() {
    }

    /* ---- shared widgets, also used by the New File dialog ---- */

    /** A field with the surface color behind it and grey text while it is empty. */
    static class TextBox extends JTextField {
        private static final long serialVersionUID = 1L;
        private String placeholder;

        TextBox(String text) {
            super(text);
            Flat.field(this);
        }

        void setPlaceholder(String text) {
            placeholder = text;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            g2.setColor(isEnabled() ? Theme.t.surface : Theme.t.win);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            if (getText().isEmpty() && placeholder != null) {
                g2.setColor(Theme.t.muted);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                Insets insets = getInsets();
                g2.drawString(placeholder, insets.left, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static Icon chevron(final boolean up) {
        return new Icon() {
            public int getIconWidth() {
                return 10;
            }

            public int getIconHeight() {
                return 5;
            }

            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.smooth(g2);
                g2.setColor(c.isEnabled() ? Theme.t.muted : Theme.t.border);
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D.Float path = new Path2D.Float();
                path.moveTo(x + 1, y + (up ? 4.5f : 0.5f));
                path.lineTo(x + 5, y + (up ? 0.5f : 4.5f));
                path.lineTo(x + 9, y + (up ? 4.5f : 0.5f));
                g2.draw(path);
                g2.dispose();
            }
        };
    }

    private static JButton arrow(boolean up) {
        JButton button = new JButton(chevron(up));
        button.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(false);
        return button;
    }

    /** A whole number field with the editor's look, like Qt's QSpinBox. */
    static JSpinner spinner(int value, int minimum, int maximum) {
        final JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(Math.max(minimum, Math.min(maximum, value)), minimum, maximum, 1)) {
            private static final long serialVersionUID = 1L;

            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.smooth(g2);
                g2.setColor(isEnabled() ? Theme.t.surface : Theme.t.win);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        spinner.setUI(new BasicSpinnerUI() {
            protected Component createNextButton() {
                JButton button = arrow(true);
                installNextButtonListeners(button);
                return button;
            }

            protected Component createPreviousButton() {
                JButton button = arrow(false);
                installPreviousButtonListeners(button);
                return button;
            }
        });
        /* The default format would show large values with grouping commas. */
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        final JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        field.setColumns(7);
        field.setHorizontalAlignment(JTextField.LEFT);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setOpaque(false);
        field.setForeground(Theme.t.text);
        field.setCaretColor(Theme.t.text);
        field.setSelectionColor(Theme.t.accentSoft);
        field.setSelectedTextColor(Theme.t.text);
        spinner.setOpaque(false);
        spinner.getEditor().setOpaque(false);
        /* Flat's border follows the focus of the component it is given, here the inner field. */
        spinner.setBorder(new Flat.FieldBorder(10, 6) {
            private static final long serialVersionUID = 1L;

            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                super.paintBorder(field, g, x, y, width, height);
            }
        });
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) {
                spinner.repaint();
            }

            public void focusLost(java.awt.event.FocusEvent e) {
                spinner.repaint();
            }
        });
        /* As typed, so that whatever shows the value keeps up, as with a QSpinBox. */
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void typed() {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        commit(spinner);
                    }
                });
            }

            public void insertUpdate(DocumentEvent e) {
                typed();
            }

            public void removeUpdate(DocumentEvent e) {
                typed();
            }

            public void changedUpdate(DocumentEvent e) {
                typed();
            }
        });
        return spinner;
    }

    /** Take what is typed in a spinner, or keep its last good value if that is not a number in range. */
    static void commit(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException e) {
            /* A QSpinBox cannot hold such text either. */
        }
    }

    static int value(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    /** A row of dialog buttons with nothing to their right. */
    static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0)
                row.add(Box.createHorizontalStrut(8));
            row.add(buttons[i]);
        }
        return row;
    }

    /**
     * True if text survives a save. Lone surrogates from U+DC80 are how the library
     * keeps bytes that are not UTF-8, and it writes any other lone surrogate as '?'.
     */
    static boolean encodable(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0)
                return false;
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)))
                i++;
            else if (Character.isSurrogate(c) && !(c >= 0xdc80 && c <= 0xdcff))
                return false;
        }
        return true;
    }

    private static String brief(String text) {
        String t = text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return t.length() <= 80 ? t : t.substring(0, 80) + "\u2026";
    }

    private static void warn(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "SDDS", JOptionPane.WARNING_MESSAGE);
    }

    /* ---- forms ---- */

    /** A modal form of labelled rows above OK and Cancel. */
    private static final class Form extends JDialog {
        private static final long serialVersionUID = 1L;
        private final JPanel grid = new JPanel(new GridBagLayout());
        private int line;
        private boolean accepted;

        Form(Window owner, String title) {
            super(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            grid.setBorder(Flat.pad(14, 16, 12, 16));
        }

        JLabel row(String label, JComponent field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = line++;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 0, 8, 12);
            JLabel text = new JLabel(label);
            text.setLabelFor(field);
            grid.add(text, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 8, 0);
            grid.add(field, c);
            return text;
        }

        /** A label above its field, as QInputDialog lays them out. */
        JLabel stacked(String label, JComponent field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = line++;
            c.gridwidth = 2;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 0, 4, 0);
            JLabel text = new JLabel(label);
            text.setLabelFor(field);
            grid.add(text, c);
            c.gridy = line++;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 10, 0);
            grid.add(field, c);
            return text;
        }

        void note(JComponent text) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = line++;
            c.gridwidth = 2;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 0, 10, 0);
            grid.add(text, c);
        }

        TextBox text(String label, String value) {
            TextBox field = new TextBox(value == null ? "" : value);
            row(label, field);
            return field;
        }

        boolean run() {
            JButton ok = Flat.primaryButton("OK");
            JButton cancel = Flat.button("Cancel");
            ok.addActionListener(e -> {
                accepted = true;
                dispose();
            });
            cancel.addActionListener(e -> dispose());
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = line++;
            c.gridwidth = 2;
            c.anchor = GridBagConstraints.EAST;
            c.insets = new Insets(4, 0, 0, 0);
            grid.add(buttonRow(cancel, ok), c);
            setContentPane(grid);
            Flat.dialogKeys(this, ok);
            pack();
            setLocationRelativeTo(getOwner());
            setVisible(true);
            return accepted;
        }
    }

    /** The radio buttons that choose a type. An enum's mapping fixes its type, so only that one is enabled. */
    private static final class TypeChoice {
        final JPanel panel = new JPanel();
        private final ButtonGroup group = new ButtonGroup();
        private final Map<Integer, JRadioButton> buttons = new HashMap<Integer, JRadioButton>();

        TypeChoice(int current, boolean isEnum) {
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            panel.setOpaque(false);
            for (int type : TYPES)
                add(type, !isEnum);
            if (isEnum)
                add(SDDSUtil.SDDS_ENUM, true);
            JRadioButton checked = buttons.get(Integer.valueOf(current));
            if (checked != null)
                checked.setSelected(true);
        }

        private void add(int type, boolean enabled) {
            String name = SDDSUtil.getTypeName(type);
            JRadioButton button = new JRadioButton(type == SDDSUtil.SDDS_LONGDOUBLE ? "long double" : name);
            button.setOpaque(false);
            button.setEnabled(enabled);
            group.add(button);
            panel.add(button);
            buttons.put(Integer.valueOf(type), button);
        }

        int selected() {
            for (Map.Entry<Integer, JRadioButton> entry : buttons.entrySet())
                if (entry.getValue().isSelected())
                    return entry.getKey().intValue();
            return 0;
        }
    }

    /** The fields of a definition's form, for an existing definition or a new one. */
    private static final class Fields {
        final Form form;
        final Kind kind;
        final TextBox name, symbol, units, description, format;
        final TextBox fixed, group, mapping;
        final JSpinner length, dimensions;
        final TypeChoice type;

        Fields(Window owner, String title, Kind kind, Definition d, int lengthLimit, int newType) {
            this.kind = kind;
            form = new Form(owner, title);
            boolean isEnum = d != null && d.isEnum();
            name = form.text("Name", d == null ? "" : d.name);
            symbol = form.text("Symbol", d == null ? "" : d.symbol);
            units = form.text("Units", d == null ? "" : d.units);
            description = form.text("Description", d == null ? "" : d.description);
            format = form.text("Format", d == null ? "" : d.format);
            fixed = kind == Kind.PARAMETER ? form.text("Fixed value", d == null ? "" : d.fixedValue) : null;
            group = kind == Kind.ARRAY ? form.text("Group", d == null ? "" : d.groupName) : null;
            if (kind == Kind.PARAMETER) {
                length = null;
            } else {
                length = spinner(d == null ? 0 : d.fieldLength, -lengthLimit, lengthLimit);
                form.row("Field length", length);
            }
            if (kind == Kind.ARRAY && d != null) {
                dimensions = spinner(d.dimensions, 1, MAX_DIMENSIONS);
                form.row("Dimensions", dimensions);
            } else {
                dimensions = null;
            }
            type = new TypeChoice(d == null ? newType : d.type, isEnum);
            form.row("Type", type.panel);
            mapping = isEnum ? form.text("Enum mapping", d.enumDefinition.getValues()) : null;
            if (isEnum) {
                format.setEnabled(false);
                if (length != null)
                    length.setEnabled(false);
            }
        }

        boolean run() {
            boolean accepted = form.run();
            if (length != null)
                commit(length);
            if (dimensions != null)
                commit(dimensions);
            return accepted;
        }

        /** Warn about the first field that cannot be kept, as the Qt editor checks them. */
        boolean acceptable(Component owner, List<Definition> list, int self) {
            String problem = Document.nameProblem(name.getText(), kind.word, list, self);
            if (problem != null) {
                warn(owner, problem);
                return false;
            }
            for (TextBox field : new TextBox[] { symbol, units, description, format, group }) {
                if (field != null && !encodable(field.getText())) {
                    warn(owner, "Text contains a NUL character or characters that cannot be saved in this system's"
                            + " character encoding: " + brief(field.getText()));
                    return false;
                }
            }
            int chosen = type.selected();
            if (fixed != null && !fixed.getText().isEmpty()
                    && (!Cells.valid(fixed.getText(), chosen) || !encodable(fixed.getText()))) {
                warn(owner, "Fixed value is invalid for type " + SDDSUtil.getTypeName(chosen));
                return false;
            }
            if (length != null && value(length) < 0 && chosen != SDDSUtil.SDDS_STRING) {
                warn(owner, "A negative field length is allowed only for string data");
                return false;
            }
            /* The Qt editor saves such a format; this library refuses to define it, so it could not be saved. */
            String text = format.getText();
            if (chosen != SDDSUtil.SDDS_ENUM && !text.trim().isEmpty() && !SDDSUtil.verifyPrintfFormat(text, chosen)) {
                warn(owner, "Format string '" + brief(text) + "' is not valid for type " + SDDSUtil.getTypeName(chosen));
                return false;
            }
            return true;
        }
    }

    /* ---- values ---- */

    private static String[] cells(Document.Page page, Kind kind, int index) {
        switch (kind) {
            case PARAMETER:
                return new String[] { page.parameters[index] };
            case COLUMN:
                return page.columns[index];
            default:
                return page.arrays[index].values;
        }
    }

    private static String where(Kind kind, String name, int position) {
        switch (kind) {
            case PARAMETER:
                return "parameter '" + name + "'";
            case COLUMN:
                return "column '" + name + "', row " + (position + 1);
            default:
                return "array '" + name + "', element " + (position + 1);
        }
    }

    /** Explain the first value of a definition that a check refuses, or return null if it refuses none. */
    private static String firstProblem(Document doc, Kind kind, int index, Function<String, String> check) {
        String name = kind.list(doc).get(index).name;
        for (int number = 0; number < doc.pages.size(); number++) {
            String[] values = cells(doc.pages.get(number), kind, index);
            for (int i = 0; i < values.length; i++) {
                String reason = check.apply(values[i]);
                if (reason != null)
                    return "Page " + (number + 1) + ": " + where(kind, name, i) + " " + reason;
            }
        }
        return null;
    }

    /**
     * Replacing a mapping keeps every cell's label, and so gives it the code the new
     * mapping has for that label. A label in use must still be declared. A new fixed
     * label replaces a parameter's value on every page, so the old one need not survive.
     */
    private static String undeclaredState(Document doc, Kind kind, int index, final SDDSEnumDefinition map,
            boolean newFixedLabel) {
        if (kind == Kind.PARAMETER && newFixedLabel)
            return null;
        return firstProblem(doc, kind, index, new Function<String, String>() {
            public String apply(String cell) {
                return !cell.isEmpty() && map.labelToCode(cell) == null ? usesState(cell) : null;
            }
        });
    }

    private static String usesState(String label) {
        return "uses state '" + brief(label) + "', which the new enum mapping does not declare";
    }

    private static String fixedText(String text, int type) {
        return Cells.numeric(type) ? Cells.canonical(text.trim(), type) : text;
    }

    private static boolean same(String stored, String text) {
        return (stored == null ? "" : stored).equals(text);
    }

    private static String edited(String stored, String text) {
        return same(stored, text) ? stored : text.isEmpty() ? null : text;
    }

    private static int[] withDimensions(int[] dims, int count) {
        int[] result = Arrays.copyOf(dims, count);
        for (int i = dims.length; i < count; i++)
            result[i] = 1;
        return result;
    }

    /** A time display applies only to a column that can still show one. */
    private static void pruneTimeMode(Definition d) {
        if (d.timeMode != Definition.TimeMode.RAW && !d.canDisplayTime())
            d.timeMode = Definition.TimeMode.RAW;
    }

    /* ---- attributes ---- */

    /**
     * Edit one definition's attributes.
     *
     * @return true if the document changed
     */
    static boolean editAttributes(Window owner, Document doc, Kind kind, int index) {
        List<Definition> list = kind.list(doc);
        if (index < 0 || index >= list.size())
            return false;
        Definition d = list.get(index);
        Fields f = new Fields(owner, kind.title + " Attributes", kind, d, Integer.MAX_VALUE, d.type);
        if (!f.run() || !f.acceptable(owner, list, index))
            return false;

        String name = f.name.getText(), symbol = f.symbol.getText(), units = f.units.getText();
        String description = f.description.getText(), format = f.format.getText();
        String group = f.group == null ? null : f.group.getText();
        String fixed = f.fixed == null ? null : f.fixed.getText();
        String mapping = f.mapping == null ? null : f.mapping.getText();
        int type = f.type.selected();
        int length = f.length == null ? d.fieldLength : value(f.length);
        int dimensions = f.dimensions == null ? d.dimensions : value(f.dimensions);

        if (kind == Kind.ARRAY) {
            for (Document.Page page : doc.pages) {
                if (Document.product(withDimensions(page.arrays[index].dims, dimensions)) < 0) {
                    warn(owner, "Array dimensions are too large.");
                    return false;
                }
            }
        }

        boolean mappingChanged = mapping != null && !mapping.equals(d.enumDefinition.getValues());
        if (!mappingChanged && same(d.name, name) && same(d.symbol, symbol) && same(d.units, units)
                && same(d.description, description) && same(d.format, format)
                && (group == null || same(d.groupName, group)) && (fixed == null || same(d.fixedValue, fixed))
                && d.fieldLength == length && d.dimensions == dimensions && d.type == type)
            return false;

        SDDSEnumDefinition map = d.enumDefinition;
        if (mappingChanged) {
            try {
                map = new SDDSEnumDefinition(mapping);
            } catch (IllegalArgumentException e) {
                warn(owner, "Invalid enum mapping");
                return false;
            }
            String problem = undeclaredState(doc, kind, index, map, fixed != null && !fixed.isEmpty());
            if (problem != null) {
                warn(owner, problem);
                return false;
            }
        }
        if (type == SDDSUtil.SDDS_ENUM && fixed != null && !fixed.isEmpty() && map.labelToCode(fixed) == null) {
            warn(owner, "Fixed enum value must be a declared label");
            return false;
        }

        d.name = name;
        d.symbol = edited(d.symbol, symbol);
        d.units = edited(d.units, units);
        d.description = edited(d.description, description);
        d.format = edited(d.format, format);
        d.enumDefinition = map;
        if (group != null)
            d.groupName = edited(d.groupName, group);
        if (fixed != null) {
            /* A fixed empty string stays fixed, while clearing other text leaves page values. */
            boolean stays = !fixed.isEmpty() || (type != SDDSUtil.SDDS_ENUM && d.fixedValue != null && d.fixedValue.isEmpty());
            d.fixedValue = stays ? fixedText(fixed, type) : null;
            if (stays)
                for (Document.Page page : doc.pages)
                    page.parameters[index] = d.fixedValue;
        }
        if (kind == Kind.ARRAY && dimensions != d.dimensions) {
            String fill = Cells.newCell(d);
            for (Document.Page page : doc.pages) {
                Document.ArrayCells cells = page.arrays[index];
                if (cells.dims.length == dimensions)
                    continue;
                int[] newDims = withDimensions(cells.dims, dimensions);
                cells.values = Document.reshape(cells.values, cells.dims, newDims, fill);
                cells.dims = newDims;
            }
            d.dimensions = dimensions;
        }
        d.fieldLength = length;
        d.type = type;
        pruneTimeMode(d);
        return true;
    }

    /* ---- type ---- */

    /** Keep the width when a type change would leave a negative field length invalid. */
    private static int fieldLengthForType(int fieldLength, int type) {
        return fieldLength < 0 && type != SDDSUtil.SDDS_STRING ? -fieldLength : fieldLength;
    }

    /**
     * Change a definition's type. Values are held as text, so they are not converted.
     * An enum's type cannot be changed here; its mapping is edited in Attributes.
     *
     * @return true if the document changed
     */
    static boolean changeType(Window owner, Document doc, Kind kind, int index) {
        List<Definition> list = kind.list(doc);
        if (index < 0 || index >= list.size())
            return false;
        final Definition d = list.get(index);
        if (d.isEnum()) {
            JOptionPane.showMessageDialog(owner,
                    "Edit the enum mapping in Attributes. Convert enum values explicitly before changing their type.",
                    "Enum", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        final Form form = new Form(owner, kind.title + " Type");
        DefaultComboBoxModel<String> names = new DefaultComboBoxModel<String>();
        for (int type : TYPES)
            names.addElement(SDDSUtil.getTypeName(type));
        final JComboBox<String> box = Flat.combo(new JComboBox<String>(names), true);
        box.setSelectedItem(d.typeName());
        form.stacked("Type", box);
        form.setMinimumSize(new Dimension(320, 0));
        if (!form.run())
            return false;
        int target = SDDSUtil.identifyType((String) box.getSelectedItem());
        if (target == d.type)
            return false;
        /* As in the Qt editor, values that do not suit the new type are reported when saving. */
        d.type = target;
        if (kind != Kind.PARAMETER)
            d.fieldLength = fieldLengthForType(d.fieldLength, target);
        /* The library refuses to define a format that does not suit the type, so it could not be saved. */
        if (d.format != null && !d.format.trim().isEmpty() && !SDDSUtil.verifyPrintfFormat(d.format, target))
            d.format = null;
        pruneTimeMode(d);
        return true;
    }

    /**
     * Add a definition at the end of its list. New values are blank, a parameter's
     * being its fixed value if it has one, and a new array holds five elements.
     *
     * @return true if the document changed
     */
    static boolean insert(Window owner, Document doc, Kind kind) {
        List<Definition> list = kind.list(doc);
        int newType = kind == Kind.PARAMETER ? SDDSUtil.SDDS_STRING : SDDSUtil.SDDS_DOUBLE;
        Fields f = new Fields(owner, "New " + kind.title, kind, null, NEW_LENGTH_LIMIT, newType);
        if (!f.run() || f.name.getText().isEmpty() || !f.acceptable(owner, list, -1))
            return false;

        Definition d = new Definition(f.name.getText(), f.type.selected());
        d.symbol = edited(null, f.symbol.getText());
        d.units = edited(null, f.units.getText());
        d.description = edited(null, f.description.getText());
        d.format = edited(null, f.format.getText());
        if (f.group != null)
            d.groupName = edited(null, f.group.getText());
        if (f.length != null)
            d.fieldLength = value(f.length);
        String value = Cells.newCell(d);
        if (f.fixed != null && !f.fixed.getText().isEmpty()) {
            d.fixedValue = fixedText(f.fixed.getText(), d.type);
            value = d.fixedValue;
        }
        list.add(d);
        for (Document.Page page : doc.pages) {
            switch (kind) {
                case PARAMETER:
                    page.parameters = Arrays.copyOf(page.parameters, page.parameters.length + 1);
                    page.parameters[page.parameters.length - 1] = value;
                    break;
                case COLUMN:
                    /* Without a column to match, a page has no rows. */
                    page.rows = page.columns.length > 0 ? page.columns[0].length : 0;
                    String[] column = new String[page.rows];
                    Arrays.fill(column, value);
                    page.columns = Arrays.copyOf(page.columns, page.columns.length + 1);
                    page.columns[page.columns.length - 1] = column;
                    break;
                default:
                    String[] values = new String[NEW_ARRAY_SIZE];
                    Arrays.fill(values, value);
                    page.arrays = Arrays.copyOf(page.arrays, page.arrays.length + 1);
                    page.arrays[page.arrays.length - 1] = new Document.ArrayCells(values, new int[] { NEW_ARRAY_SIZE });
            }
        }
        return true;
    }
}
