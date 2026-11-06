/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

import java.util.Comparator;
import java.util.HashMap;

/**
 * An immutable SDDS6 categorical dictionary: a checked mapping between signed
 * 32-bit codes and labels. It implements the logical grammar of the
 * <code>enum_values</code> attribute, a comma-separated list of
 * <code>code:label</code> pairs. Declaration order and label whitespace are
 * preserved, codes and labels are unique, and labels are nonempty, valid
 * Unicode without NUL. Matching is exact and does not normalize Unicode.
 *
 * <p>
 * The logical escapes are <code>\,</code> <code>\:</code> <code>\\</code>
 * <code>\"</code> <code>\n</code> <code>\r</code> and <code>\t</code>.
 * The namelist escaping used in a file header is a separate, outer layer that is
 * handled when a header is read or written. The logical text passed here has
 * already had that outer layer removed.
 */
public final class SDDSEnumDefinition {
    /** Default limit on one SDDS6 layout command, in bytes. */
    public static final long DEFAULT_LAYOUT_LIMIT = 16L * 1024L * 1024L;
    /** Default limit on one decoded logical mapping, in bytes. */
    public static final long DEFAULT_MAPPING_LIMIT = 1024L * 1024L;

    private static final ThreadLocal<long[]> LIMITS = ThreadLocal
            .withInitial(() -> new long[] { DEFAULT_LAYOUT_LIMIT, DEFAULT_MAPPING_LIMIT });

    /** Orders labels by their exact UTF-8 bytes, which is also Unicode code point order. */
    public static final Comparator<String> LABEL_ORDER = new Comparator<String>() {
        public int compare(String a, String b) {
            int i = 0, j = 0;
            while (i < a.length() && j < b.length()) {
                int x = a.codePointAt(i), y = b.codePointAt(j);
                if (x != y)
                    return x < y ? -1 : 1;
                i += Character.charCount(x);
                j += Character.charCount(y);
            }
            return (i < a.length() ? 1 : 0) - (j < b.length() ? 1 : 0);
        }
    };

    private final int[] codes;
    private final String[] labels;
    private final String text;
    private final HashMap<Integer, String> labelByCode;
    private final HashMap<String, Integer> codeByLabel;

    /**
     * Set the limits used by this thread for subsequent SDDS6 reads and dictionary
     * construction.
     *
     * @param layoutBytes  limit on one layout command, below
     *                     <code>Integer.MAX_VALUE</code>
     * @param mappingBytes limit on one mapping, not above <code>layoutBytes</code>
     * @return <code>true</code> if the limits are valid and were set
     */
    public static boolean setReaderLimits(long layoutBytes, long mappingBytes) {
        if (layoutBytes <= 0 || mappingBytes <= 0 || layoutBytes >= Integer.MAX_VALUE || mappingBytes > layoutBytes)
            return false;
        long[] limits = LIMITS.get();
        limits[0] = layoutBytes;
        limits[1] = mappingBytes;
        return true;
    }

    /** Returns this thread's SDDS6 layout command limit in bytes. */
    public static long getLayoutLimit() {
        return LIMITS.get()[0];
    }

    /** Returns this thread's decoded mapping limit in bytes. */
    public static long getMappingLimit() {
        return LIMITS.get()[1];
    }

    /**
     * Parse and validate a logical mapping such as
     * <code>0:OFF,10:ON,-1:FAULT</code>.
     *
     * @param values logical mapping text
     * @throws IllegalArgumentException if the mapping is invalid
     */
    public SDDSEnumDefinition(String values) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("Invalid enum mapping");
        long limit = getMappingLimit();
        if (utf8Length(values) > limit)
            throw new IllegalArgumentException("Enum mapping exceeds configured byte limit");
        java.util.ArrayList<Integer> codeList = new java.util.ArrayList<Integer>();
        java.util.ArrayList<String> labelList = new java.util.ArrayList<String>();
        labelByCode = new HashMap<Integer, String>();
        codeByLabel = new HashMap<String, Integer>();
        StringBuilder canonical = new StringBuilder(values.length());
        int p = 0, n = values.length();
        while (p < n) {
            int start = p;
            char c = values.charAt(p);
            if (c == '-') {
                if (p + 1 >= n || !isDigit(values.charAt(p + 1)))
                    throw invalid(values);
                p++;
            } else if (!isDigit(c)) {
                throw invalid(values);
            }
            while (p < n && isDigit(values.charAt(p)))
                p++;
            if (p >= n || values.charAt(p) != ':')
                throw invalid(values);
            Integer code = parseDigits(values.substring(start, p));
            if (code == null)
                throw invalid(values);
            p++;
            StringBuilder label = new StringBuilder();
            while (p < n && values.charAt(p) != ',') {
                char ch = values.charAt(p++);
                if (ch == '\\') {
                    if (p >= n)
                        throw invalid(values);
                    ch = values.charAt(p++);
                    switch (ch) {
                        case ',':
                        case ':':
                        case '\\':
                        case '"':
                            break;
                        case 'n':
                            ch = '\n';
                            break;
                        case 'r':
                            ch = '\r';
                            break;
                        case 't':
                            ch = '\t';
                            break;
                        default:
                            throw invalid(values);
                    }
                } else if (ch == ':' || ch == '"' || ch < 32 || ch == 127) {
                    throw invalid(values);
                }
                label.append(ch);
            }
            String decoded = label.toString();
            if (decoded.isEmpty() || !isValidUnicode(decoded))
                throw invalid(values);
            if (labelByCode.containsKey(code) || codeByLabel.containsKey(decoded))
                throw new IllegalArgumentException("Duplicate enum code or label");
            labelByCode.put(code, decoded);
            codeByLabel.put(decoded, code);
            codeList.add(code);
            labelList.add(decoded);
            if (canonical.length() > 0)
                canonical.append(',');
            canonical.append(code.intValue()).append(':');
            appendEscaped(canonical, decoded);
            if (p < n && values.charAt(p) == ',') {
                p++;
                if (p >= n)
                    throw invalid(values);
            }
        }
        text = canonical.toString();
        if (utf8Length(text) > limit)
            throw new IllegalArgumentException("Canonical enum mapping exceeds configured byte limit");
        codes = new int[codeList.size()];
        labels = new String[codes.length];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = codeList.get(i).intValue();
            labels[i] = labelList.get(i);
        }
    }

    private static IllegalArgumentException invalid(String values) {
        return new IllegalArgumentException("Invalid enum mapping");
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }

    /** Parse an optional minus sign and decimal digits, or return null when out of range. */
    private static Integer parseDigits(String digits) {
        long value = 0;
        boolean negative = digits.charAt(0) == '-';
        for (int i = negative ? 1 : 0; i < digits.length(); i++) {
            value = 10 * value + (digits.charAt(i) - '0');
            if (value > 2147483648L)
                return null;
        }
        if (negative)
            value = -value;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            return null;
        return Integer.valueOf((int) value);
    }

    private static void appendEscaped(StringBuilder out, String label) {
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            switch (c) {
                case ',':
                case ':':
                case '\\':
                case '"':
                    out.append('\\').append(c);
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
    }

    /** True when the text has no NUL and every surrogate is part of a valid pair. */
    private static boolean isValidUnicode(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0)
                return false;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1)))
                    return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    private static long utf8Length(String s) {
        long length = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80)
                length++;
            else if (c < 0x800)
                length += 2;
            else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                length += 4;
                i++;
            } else
                length += 3;
        }
        return length;
    }

    /**
     * Parse one complete enum code, the grammar shared by fixed values and ASCII
     * data: optional surrounding whitespace, an optional minus sign and decimal
     * digits. A leading plus sign is rejected.
     *
     * @param text text to parse
     * @return the code, or <code>null</code> if the text is not a complete code
     */
    public static Integer parseCode(String text) {
        if (text == null)
            return null;
        int i = 0, n = text.length();
        while (i < n && isSpace(text.charAt(i)))
            i++;
        int start = i;
        if (i < n && text.charAt(i) == '-')
            i++;
        int firstDigit = i;
        while (i < n && isDigit(text.charAt(i)))
            i++;
        if (i == firstDigit)
            return null;
        int end = i;
        while (i < n && isSpace(text.charAt(i)))
            i++;
        if (i != n)
            return null;
        return parseDigits(text.substring(start, end));
    }

    /** Returns the canonical logical mapping text. */
    public String getValues() {
        return text;
    }

    /** Returns the canonical logical mapping text. */
    public String toString() {
        return text;
    }

    /** Returns the number of states. */
    public int size() {
        return codes.length;
    }

    /**
     * Returns the code of a declared entry.
     *
     * @param index entry index in declaration order
     */
    public int getEntryCode(int index) {
        return codes[index];
    }

    /**
     * Returns the label of a declared entry.
     *
     * @param index entry index in declaration order
     */
    public String getEntryLabel(int index) {
        return labels[index];
    }

    /** Returns true if the code is mapped. */
    public boolean hasCode(long code) {
        return code >= Integer.MIN_VALUE && code <= Integer.MAX_VALUE
                && labelByCode.containsKey(Integer.valueOf((int) code));
    }

    /**
     * Resolve a code.
     *
     * @return the label, or <code>null</code> when the code is unmapped
     */
    public String codeToLabel(int code) {
        return labelByCode.get(Integer.valueOf(code));
    }

    /**
     * Resolve a label. The result is an object so that no integer has to be reserved
     * as an error value, because any code may be negative.
     *
     * @return the code, or <code>null</code> when the label is unknown
     */
    public Integer labelToCode(String label) {
        return codeByLabel.get(label);
    }

    /**
     * Format a mapped label, quoted exactly as a string value would be (only when
     * needed).
     *
     * @param code   a mapped code
     * @param quotes <code>true</code> to apply string quoting
     * @return the label, or <code>null</code> when the code is unmapped
     */
    public String formatLabel(int code, boolean quotes) {
        String label = codeToLabel(code);
        if (label == null || !quotes)
            return label;
        boolean space = false, quote = false;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (isSpace(c))
                space = true;
            else if (c == '"')
                quote = true;
        }
        if (!space && !quote)
            return label;
        StringBuilder out = new StringBuilder(label.length() + 8);
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c == '"' && (i == 0 || label.charAt(i - 1) != '\\'))
                out.append('\\');
            out.append(c);
        }
        if (!space)
            return out.toString();
        boolean blank = true;
        for (int i = 0; i < label.length() && blank; i++)
            blank = isSpace(label.charAt(i));
        if (blank)
            return "\"\"";
        return "\"" + out + "\"";
    }

    /**
     * Explicitly remap codes by exact labels, validating all of the input before
     * producing any output.
     *
     * @param target dictionary to map into
     * @param input  codes in this dictionary
     * @return codes in the target dictionary
     * @throws IllegalArgumentException if a code is unmapped or a label is absent
     *                                  from the target
     */
    public int[] remap(SDDSEnumDefinition target, int[] input) {
        if (target == null)
            throw new IllegalArgumentException("Enum remapping requires a target mapping");
        int[] output = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            String label = codeToLabel(input[i]);
            Integer code = label == null ? null : target.labelToCode(label);
            if (code == null)
                throw new IllegalArgumentException(
                        "Enum remapping requires every source value's label in the target mapping");
            output[i] = code.intValue();
        }
        return output;
    }

    /** Mappings are equal when their code/label associations are, in any order. */
    public boolean equals(Object other) {
        if (other == this)
            return true;
        if (!(other instanceof SDDSEnumDefinition))
            return false;
        return labelByCode.equals(((SDDSEnumDefinition) other).labelByCode);
    }

    public int hashCode() {
        return labelByCode.hashCode();
    }
}
