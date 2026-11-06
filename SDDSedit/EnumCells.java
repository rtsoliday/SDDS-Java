/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.util.HashMap;
import java.util.Map;

import SDDS.java.SDDS.*;

/**
 * How the editor shows SDDS6 enum values. A table cell holds <code>label (code)</code>,
 * which shows both and cannot be confused with another state, even when a label looks
 * like a number. A blank cell is a value nobody has chosen yet.
 */
final class EnumCells {
    private final SDDSEnumDefinition definition;
    private final String[] choices;
    private final Map<String, Integer> codes = new HashMap<String, Integer>();
    private final Map<String, String> labels = new HashMap<String, String>();

    EnumCells(SDDSEnumDefinition definition) {
        this.definition = definition;
        choices = new String[definition.size()];
        for (int i = 0; i < choices.length; i++) {
            int code = definition.getEntryCode(i);
            String label = definition.getEntryLabel(i);
            choices[i] = text(label, code);
            codes.put(choices[i], Integer.valueOf(code));
            labels.put(choices[i], label);
        }
    }

    private static String text(String label, int code) {
        return label + " (" + code + ")";
    }

    /** Returns the cell text of a value, which is blank for a value not chosen yet. */
    static String text(SDDSEnumValue value) {
        return value.getLabel() == null ? "" : text(value.getLabel(), value.getCode());
    }

    /** Returns what a table cell should hold for a value from the library. */
    static Object cell(Object value) {
        return value instanceof SDDSEnumValue ? text((SDDSEnumValue) value) : value;
    }

    /** Returns the states to choose from, in the order of the dictionary. */
    String[] getChoices() {
        return choices.clone();
    }

    /**
     * Returns the cell for a value the editor creates: the state of code 0 when the
     * dictionary maps it, and otherwise blank so that a state has to be chosen.
     */
    String defaultCell() {
        String label = definition.codeToLabel(0);
        return label == null ? "" : text(label, 0);
    }

    /** True for a cell that holds no state. */
    static boolean isBlank(Object cell) {
        return (cell == null) || cell.toString().isEmpty();
    }

    /**
     * Returns the code in a cell.
     *
     * @return the code, or <code>null</code> if the cell does not hold a state
     */
    Integer code(Object cell) {
        return cell == null ? null : codes.get(cell.toString());
    }

    /**
     * Returns the label in a cell, for comparing labels as text.
     *
     * @return the label, or the cell's own text if it does not hold a state
     */
    String label(Object cell) {
        String text = cell == null ? "" : cell.toString();
        String label = labels.get(text);
        return label == null ? text : label;
    }
}
