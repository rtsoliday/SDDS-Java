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
import java.util.List;
import javax.swing.*;

/** The small prompts of the editor, and its search and replace window. */
final class EditorDialogs {
    private EditorDialogs() {
    }

    /** A modal dialog with a body and a row of buttons on the right. */
    private static final class Prompt extends JDialog {
        private static final long serialVersionUID = 1L;
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        boolean accepted;

        Prompt(Window owner, String title, JComponent body) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setBackground(Theme.t.win);
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            body.setOpaque(false);
            content.add(body, BorderLayout.CENTER);
            buttons.setOpaque(false);
            content.add(buttons, BorderLayout.SOUTH);
            setContentPane(content);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }

        void addOkCancel() {
            JButton ok = Flat.primaryButton("OK"), cancel = Flat.button("Cancel");
            ok.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    accepted = true;
                    dispose();
                }
            });
            cancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    dispose();
                }
            });
            buttons.add(ok);
            buttons.add(cancel);
            Flat.dialogKeys(this, ok);
        }

        boolean run() {
            pack();
            setLocationRelativeTo(getOwner());
            setVisible(true);
            return accepted;
        }
    }

    private static JPanel formPanel(String[] labels, JComponent[] fields) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            c.anchor = GridBagConstraints.LINE_START;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(i == 0 ? 0 : 6, 0, 0, 10);
            form.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(i == 0 ? 0 : 6, 0, 0, 0);
            form.add(fields[i], c);
        }
        return form;
    }

    /** Ask for several pieces of text. Returns <code>null</code> if cancelled. */
    static String[] form(Window owner, String title, String[] labels, String[] values) {
        JComponent[] fields = new JComponent[labels.length];
        for (int i = 0; i < labels.length; i++)
            fields[i] = Flat.field(new JTextField(values[i], 22));
        Prompt prompt = new Prompt(owner, title, formPanel(labels, fields));
        prompt.addOkCancel();
        if (!prompt.run())
            return null;
        String[] out = new String[labels.length];
        for (int i = 0; i < labels.length; i++)
            out[i] = ((JTextField) fields[i]).getText();
        return out;
    }

    /** Ask for one piece of text, with the label above the field. */
    static String text(Window owner, String title, String label, String value) {
        JPanel body = new JPanel(new BorderLayout(0, 6));
        JTextField field = Flat.field(new JTextField(value, 40));
        body.add(new JLabel(label), BorderLayout.NORTH);
        body.add(field, BorderLayout.CENTER);
        Prompt prompt = new Prompt(owner, title, body);
        prompt.addOkCancel();
        return prompt.run() ? field.getText() : null;
    }

    /** Ask for a whole number within a range. Returns <code>null</code> if cancelled. */
    static Integer number(Window owner, String title, String label, int value, int low, int high) {
        JSpinner spinner = DefinitionDialogs.spinner(value, low, high);
        Prompt prompt = new Prompt(owner, title, formPanel(new String[] { label }, new JComponent[] { spinner }));
        prompt.addOkCancel();
        if (!prompt.run())
            return null;
        DefinitionDialogs.commit(spinner);
        return DefinitionDialogs.value(spinner);
    }

    /** Ask for an array's new dimensions. Returns <code>null</code> if cancelled. */
    static int[] dimensions(Window owner, int[] current, int count) {
        String[] labels = new String[count];
        JComponent[] fields = new JComponent[count];
        for (int i = 0; i < count; i++) {
            labels[i] = "Dim " + (i + 1);
            fields[i] = DefinitionDialogs.spinner(i < current.length ? current[i] : 1, 0, Integer.MAX_VALUE);
        }
        Prompt prompt = new Prompt(owner, "Resize Array", formPanel(labels, fields));
        prompt.addOkCancel();
        if (!prompt.run())
            return null;
        int[] dims = new int[count];
        for (int i = 0; i < count; i++) {
            DefinitionDialogs.commit((JSpinner) fields[i]);
            dims[i] = DefinitionDialogs.value((JSpinner) fields[i]);
        }
        return dims;
    }

    /** The choice made in the Filter/View Rows dialog. */
    static final class FilterChoice {
        boolean cleared;
        String expression;
    }

    /** Ask for a row filter. Returns <code>null</code> if cancelled. */
    static FilterChoice filter(Window owner, String value) {
        JPanel body = new JPanel(new BorderLayout(0, 6));
        JTextField field = Flat.field(new JTextField(value, 50));
        body.add(new JLabel("Expression (non-destructive view filter):"), BorderLayout.NORTH);
        body.add(field, BorderLayout.CENTER);
        body.add(new JLabel("Examples: X>0 && Status==\"OK\"    or    [Beam Current] >= 100"), BorderLayout.SOUTH);
        final Prompt prompt = new Prompt(owner, "Filter/View Rows", body);
        final FilterChoice choice = new FilterChoice();
        prompt.addOkCancel();
        JButton clear = Flat.button("Clear");
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                choice.cleared = true;
                prompt.accepted = true;
                prompt.dispose();
            }
        });
        prompt.buttons.add(clear);
        if (!prompt.run())
            return null;
        choice.expression = field.getText().trim();
        return choice;
    }

    /** Ask for a numerical expression, with the reference shown below it. */
    static String expression(Window owner, String value) {
        JPanel body = new JPanel(new BorderLayout(0, 6));
        JTextField field = Flat.field(new JTextField(value, 60));
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(new JLabel("Expression:"), BorderLayout.NORTH);
        top.add(field, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);
        JTextArea help = new JTextArea("Variables reference\n" + " - x: current cell value\n"
                + " - a: anchor value (first selected cell)\n" + " - i: index in selected cells (0-based)\n"
                + " - row, col: absolute row/column index (0-based)\n" + " - dr, dc: row/column offset from anchor\n\n"
                + "Functions: abs, sqrt, sin, cos, tan, log, exp, floor, ceil\n\n" + "Examples\n" + " - x + 10\n"
                + " - a + i*0.5\n" + " - x * (1 + dr*0.01)\n" + " - sin(x) * exp(-i/10)");
        help.setEditable(false);
        help.setFont(Theme.tableFont());
        help.setBackground(Theme.t.surface);
        help.setForeground(Theme.t.text);
        help.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane pane = Flat.scrollPane(new JScrollPane(help));
        pane.setBorder(BorderFactory.createLineBorder(Theme.t.border));
        pane.setPreferredSize(new Dimension(860, 300));
        body.add(pane, BorderLayout.CENTER);
        Prompt prompt = new Prompt(owner, "Apply Numerical Expression", body);
        prompt.addOkCancel();
        return prompt.run() ? field.getText().trim() : null;
    }

    /** Choose one of several items. Returns the choice or <code>null</code> if cancelled. */
    static String choose(Window owner, String title, String label, String[] items, String current) {
        JComboBox<String> box = Flat.combo(new JComboBox<String>(items), true);
        box.setSelectedItem(current);
        Prompt prompt = new Prompt(owner, title, formPanel(new String[] { label }, new JComponent[] { box }));
        prompt.addOkCancel();
        return prompt.run() ? (String) box.getSelectedItem() : null;
    }

    /** Find and replace in one column or array. */
    static final class SearchReplace extends JDialog {
        private static final long serialVersionUID = 1L;

        /** What the window does through the main window. */
        interface Host {
            void flushEdits();

            /** Apply edits as one undo step. Arrays of element positions and new text. */
            boolean applyTexts(GridModels.Base model, int item, int[] elements, String[] texts, String label);

            /** Show a match in the table, with its text selected in the cell's editor. */
            void focusMatch(GridModels.Base model, int viewRow, int item, int start, int length);

            /** Warn that the text cannot be stored in the cell. */
            void warnInvalid(Definition definition);

            void closeMatchEditor(GridModels.Base model);

            /** The view rows of selected cells of this item, for Replace Selected. */
            int[] selectedRows(GridModels.Base model, int item);
        }

        private static final class Match {
            final int row, start;

            Match(int row, int start) {
                this.row = row;
                this.start = start;
            }
        }

        private static String lastPattern = "", lastReplacement = "";
        private final Host host;
        private final GridModels.Base model;
        private final int item;
        private final Definition definition;
        private final JTextField pattern = Flat.field(new JTextField(lastPattern, 30));
        private final JTextField replacement = Flat.field(new JTextField(lastReplacement, 30));
        private final List<Match> matches = new ArrayList<Match>();
        private int matchIndex = -1;

        SearchReplace(Window owner, Host host, GridModels.Base model, int item, String title, boolean modal,
                boolean replaceSelected) {
            super(owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
            this.host = host;
            this.model = model;
            this.item = item;
            this.definition = model.definitions().get(item);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setBackground(Theme.t.win);
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            JPanel form = formPanel(new String[] { "Find", "Replace With" }, new JComponent[] { pattern, replacement });
            form.setOpaque(false);
            content.add(form, BorderLayout.CENTER);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            row.setOpaque(false);
            JButton search = Flat.primaryButton("Search");
            search.addActionListener(e -> runSearch(true, true));
            row.add(search);
            JButton replace = Flat.button("Replace");
            replace.addActionListener(e -> replaceCurrent());
            row.add(replace);
            if (replaceSelected) {
                JButton selected = Flat.button("Replace Selected");
                selected.addActionListener(e -> replaceSelected());
                row.add(selected);
            }
            JButton all = Flat.button("Replace All");
            all.addActionListener(e -> replaceAll());
            row.add(all);
            JButton previous = Flat.button("Previous");
            previous.addActionListener(e -> step(-1));
            row.add(previous);
            JButton next = Flat.button("Next");
            next.addActionListener(e -> step(1));
            row.add(next);
            JButton close = Flat.button("Close");
            close.addActionListener(e -> dispose());
            row.add(close);
            content.add(row, BorderLayout.SOUTH);
            setContentPane(content);
            Flat.dialogKeys(this, search);
            pattern.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    invalidateMatches();
                }

                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    invalidateMatches();
                }

                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    invalidateMatches();
                }
            });
            addWindowListener(new WindowAdapter() {
                public void windowClosed(WindowEvent e) {
                    /* What was typed into the match's editor is an edit like any other. */
                    SearchReplace.this.host.flushEdits();
                }
            });
            pack();
            setLocationRelativeTo(owner);
        }

        /** The cells changed, so found positions may no longer be right. */
        void invalidateMatches() {
            matches.clear();
            matchIndex = -1;
        }

        private void focusMatch() {
            if (matchIndex < 0 || matchIndex >= matches.size())
                return;
            Match m = matches.get(matchIndex);
            host.focusMatch(model, m.row, item, m.start, pattern.getText().length());
        }

        private String cell(int row) {
            return model.raw(row, column());
        }

        private int column() {
            return item;
        }

        private void runSearch(boolean showInfo, boolean refocus) {
            host.flushEdits();
            String text = pattern.getText();
            invalidateMatches();
            host.closeMatchEditor(model);
            if (text.isEmpty())
                return;
            lastPattern = text;
            lastReplacement = replacement.getText();
            for (int r = 0; r < model.getRowCount(); r++) {
                if (model.isPadding(r, column()))
                    continue;
                String value = cell(r);
                for (int pos = value.indexOf(text); pos >= 0; pos = value.indexOf(text, pos + text.length()))
                    matches.add(new Match(r, pos));
            }
            if (!matches.isEmpty()) {
                matchIndex = 0;
                if (refocus)
                    focusMatch();
            } else if (showInfo) {
                JOptionPane.showMessageDialog(this, "No matches found", "Search", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void step(int delta) {
            host.flushEdits();
            if (matches.isEmpty()) {
                runSearch(true, delta > 0);
                if (delta > 0 || matches.isEmpty())
                    return;
            }
            matchIndex = (matchIndex + delta + matches.size()) % matches.size();
            focusMatch();
        }

        private void replaceCurrent() {
            host.flushEdits();
            if (matches.isEmpty())
                runSearch(true, true);
            if (matches.isEmpty() || matchIndex < 0)
                return;
            Match m = matches.get(matchIndex);
            String text = pattern.getText();
            String value = cell(m.row);
            if (text.isEmpty() || !value.startsWith(text, m.start)) {
                runSearch(true, true);
                return;
            }
            String changed = value.substring(0, m.start) + replacement.getText() + value.substring(m.start + text.length());
            if (!Cells.valid(changed, definition)) {
                host.warnInvalid(definition);
                return;
            }
            host.closeMatchEditor(model);
            host.applyTexts(model, item, new int[] { model.element(m.row, column()) }, new String[] { changed },
                    "Replace");
            runSearch(true, true);
        }

        /** Replace in the listed rows. Returns how many replacements were made. */
        private int replaceRows(int[] rows, String label) {
            String text = pattern.getText();
            String with = replacement.getText();
            List<Integer> elements = new ArrayList<Integer>();
            List<String> texts = new ArrayList<String>();
            int replaced = 0;
            boolean warned = false;
            for (int row : rows) {
                if (model.isPadding(row, column()))
                    continue;
                String original = cell(row), value = original;
                int count = 0;
                for (int pos = value.indexOf(text); pos >= 0; pos = value.indexOf(text, pos + with.length())) {
                    value = value.substring(0, pos) + with + value.substring(pos + text.length());
                    count++;
                }
                if (count == 0 || value.equals(original))
                    continue;
                if (Cells.valid(value, definition)) {
                    elements.add(model.element(row, column()));
                    texts.add(value);
                    replaced += count;
                } else if (!warned) {
                    warned = true;
                    host.warnInvalid(definition);
                }
            }
            if (!elements.isEmpty()) {
                int[] positions = new int[elements.size()];
                for (int i = 0; i < positions.length; i++)
                    positions[i] = elements.get(i);
                host.closeMatchEditor(model);
                host.applyTexts(model, item, positions, texts.toArray(new String[0]), label);
            }
            return replaced;
        }

        private void replaceAll() {
            host.flushEdits();
            if (matches.isEmpty())
                runSearch(true, true);
            if (matches.isEmpty() || pattern.getText().isEmpty())
                return;
            int[] rows = new int[model.getRowCount()];
            for (int i = 0; i < rows.length; i++)
                rows[i] = i;
            int replaced = replaceRows(rows, "Replace All");
            runSearch(replaced == 0, true);
        }

        private void replaceSelected() {
            host.flushEdits();
            if (pattern.getText().isEmpty())
                return;
            int[] rows = host.selectedRows(model, item);
            if (rows.length == 0)
                return;
            int replaced = replaceRows(rows, "Replace Selected");
            runSearch(replaced == 0, false);
        }
    }
}
