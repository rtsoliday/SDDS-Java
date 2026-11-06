/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

/**
 * One SDDS6 enum value as returned by the generic value getters. It is deliberately
 * not a {@link Number}, so a categorical code cannot be mistaken for a measurement:
 * numeric use requires asking for the code explicitly. <code>toString()</code> gives
 * the label.
 */
public final class SDDSEnumValue {
    private final int code;
    private final String label;
    private final String text;

    SDDSEnumValue(int code, String label, String text) {
        this.code = code;
        this.label = label;
        this.text = text;
    }

    /** Returns the signed 32-bit code. */
    public int getCode() {
        return code;
    }

    /**
     * Returns the exact label, or <code>null</code> if this value is unset and its
     * code is not in the dictionary.
     */
    public String getLabel() {
        return label;
    }

    /** Returns the display label, or an empty string for an unset value. */
    public String toString() {
        return text == null ? "" : text;
    }

    public boolean equals(Object other) {
        if (!(other instanceof SDDSEnumValue))
            return false;
        SDDSEnumValue o = (SDDSEnumValue) other;
        return o.code == code && (label == null ? o.label == null : label.equals(o.label));
    }

    public int hashCode() {
        return code;
    }
}
