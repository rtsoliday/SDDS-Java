/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing and formatting of SDDS layout commands. SDDS6 has its own bounded grammar
 * with defined escapes; SDDS1 to SDDS5 keep the legacy grammar, in which backslashes
 * have no meaning at this level. All text here is raw, one byte per char.
 */
final class SDDSNamelist {
    static final List<String> DESCRIPTION_FIELDS = Arrays.asList("text", "contents");
    static final List<String> COLUMN_FIELDS = Arrays.asList("name", "symbol", "units", "description", "format_string",
            "type", "field_length", "enum_values");
    static final List<String> PARAMETER_FIELDS = Arrays.asList("name", "symbol", "units", "description",
            "format_string", "type", "fixed_value", "enum_values");
    static final List<String> ARRAY_FIELDS = Arrays.asList("name", "symbol", "units", "description", "format_string",
            "group_name", "type", "field_length", "dimensions", "enum_values");
    static final List<String> DATA_FIELDS = Arrays.asList("mode", "lines_per_row", "no_row_counts", "fixed_row_count",
            "additional_header_lines", "column_major_order", "endian");
    static final List<String> INCLUDE_FIELDS = Arrays.asList("filename");

    private SDDSNamelist() {
    }

    /** Fields that hold integers in the SDDS6 grammar. Others are strings or keywords. */
    private static boolean isIntegerField(String field) {
        return field.equals("field_length") || field.equals("dimensions") || field.equals("lines_per_row")
                || field.equals("no_row_counts") || field.equals("fixed_row_count")
                || field.equals("additional_header_lines") || field.equals("column_major_order");
    }

    /** Parse the fields of a command with the legacy grammar (SDDS_ParseNamelist). */
    static Map<String, String> parseLegacy(String text, List<String> fields) throws IOException {
        Map<String, String> values = new HashMap<String, String>();
        int end = text.length();
        for (int p = end - 4; p >= 0; p--) {
            if (text.charAt(p) == '&' && (p == 0 || text.charAt(p - 1) != '\\') && text.startsWith("&end", p)
                    && !SDDSText.isQuoted(text, p)) {
                end = p;
                break;
            }
        }
        String s = text.substring(0, end);
        int p = 0, n = s.length();
        while (p < n && s.charAt(p) == ' ')
            p++;
        if (p < n && s.charAt(p) == '&') {
            while (p < n && s.charAt(p) != ' ')
                p++;
        }
        while (p < n && !SDDSText.isBlank(s, p)) {
            while (p < n && (s.charAt(p) == ' ' || s.charAt(p) == ',' || s.charAt(p) == '\t'))
                p++;
            if (p >= n)
                break;
            int tagStart = p;
            while (p < n && s.charAt(p) != ' ' && s.charAt(p) != '\t' && s.charAt(p) != '=')
                p++;
            String tag = s.substring(tagStart, p);
            if (p >= n)
                throw new IOException("Namelist error: Field name " + tag + " ends string");
            if (s.charAt(p) != '=') {
                while (p < n && (s.charAt(p) == ' ' || s.charAt(p) == '\t'))
                    p++;
                if (p >= n || s.charAt(p) != '=')
                    throw new IOException("Namelist error: Field name " + tag + " ends string");
            }
            p++;
            while (p < n && (s.charAt(p) == ' ' || s.charAt(p) == '\t'))
                p++;
            if (p >= n)
                throw new IOException("Namelist error: Field name " + tag + " ends string");
            if (tag.isEmpty())
                throw new IOException("Namelist error: Field name is blank");
            String value;
            if (s.charAt(p) == '"' && s.charAt(p - 1) != '\\') {
                p++;
                int start = p;
                while (p < n && !(s.charAt(p) == '"' && s.charAt(p - 1) != '\\'))
                    p++;
                value = s.substring(start, p);
                if (p < n)
                    p++;
            } else {
                int start = p;
                while (p < n && s.charAt(p) != ' ' && s.charAt(p) != ',' && s.charAt(p) != '\t')
                    p++;
                value = s.substring(start, p);
                if (p < n)
                    p++;
            }
            if (!fields.contains(tag))
                throw new IOException("Unrecognized namelist tag given: " + tag);
            values.put(tag, value);
        }
        return values;
    }

    /** Parse the fields of a command with the SDDS6 grammar (SDDS6_ParseNamelist). */
    static Map<String, String> parse6(String s, List<String> fields) throws IOException {
        Map<String, String> values = new HashMap<String, String>();
        int p = 0, n = s.length();
        while (true) {
            while (p < n && (s.charAt(p) == ' ' || s.charAt(p) == ',' || s.charAt(p) == '\t' || s.charAt(p) == '\r'
                    || s.charAt(p) == '\n'))
                p++;
            if (p >= n || s.startsWith("&end", p))
                break;
            int keyStart = p;
            while (p < n && s.charAt(p) != '=' && s.charAt(p) != ' ' && s.charAt(p) != '\t')
                p++;
            if (p >= n)
                throw invalid6();
            String key = s.substring(keyStart, p);
            if (s.charAt(p) != '=') {
                while (p < n && (s.charAt(p) == ' ' || s.charAt(p) == '\t'))
                    p++;
                if (p >= n || s.charAt(p) != '=')
                    throw invalid6();
            }
            p++;
            while (p < n && (s.charAt(p) == ' ' || s.charAt(p) == '\t'))
                p++;
            boolean quoted = p < n && s.charAt(p) == '"';
            boolean closed = false;
            if (quoted)
                p++;
            StringBuilder value = new StringBuilder();
            while (p < n) {
                char c = s.charAt(p++);
                if (quoted && c == '"') {
                    closed = true;
                    break;
                }
                if (!quoted && (c == ',' || c == ' ' || c == '\t' || c == '\r' || c == '\n'))
                    break;
                if (c == '\\') {
                    if (p >= n)
                        throw invalid6();
                    c = s.charAt(p++);
                    if (c == 'n')
                        c = '\n';
                    else if (c == 'r')
                        c = '\r';
                    else if (c == 't')
                        c = '\t';
                    else if (c >= '0' && c <= '7') {
                        int number = c - '0', digits = 1;
                        while (digits < 3 && p < n && s.charAt(p) >= '0' && s.charAt(p) <= '7') {
                            number = 8 * number + (s.charAt(p++) - '0');
                            digits++;
                        }
                        if (number == 0 || number > 255)
                            throw invalid6();
                        c = (char) number;
                    } else if (c != '\\' && c != '"' && c != '!')
                        throw invalid6();
                }
                value.append(c);
            }
            if (quoted && !closed)
                throw invalid6();
            if (!fields.contains(key) || values.containsKey(key))
                throw invalid6();
            String v = value.toString();
            if (isIntegerField(key)) {
                long number;
                try {
                    /* Leading whitespace is allowed and trailing whitespace is not. */
                    int first = 0;
                    while (first < v.length() && SDDSText.isSpace(v.charAt(first)))
                        first++;
                    String digits = v.substring(first);
                    number = Long.parseLong(digits.startsWith("+") ? digits.substring(1) : digits);
                } catch (NumberFormatException e) {
                    throw invalid6();
                }
                boolean isShort = key.equals("column_major_order");
                if (isShort ? (number < Short.MIN_VALUE || number > Short.MAX_VALUE)
                        : (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE))
                    throw invalid6();
            }
            values.put(key, v);
        }
        return values;
    }

    private static IOException invalid6() {
        return new IOException("Invalid or duplicate SDDS6 namelist field/escape");
    }

    /** Read an integer field the way the legacy grammar's <code>%d</code> does. */
    static int integerField(Map<String, String> fields, String name, int defaultValue) throws IOException {
        String value = fields.get(name);
        /* An empty value leaves the default, since a scan that reaches its end is not a failure. */
        if ((value == null) || SDDSText.isBlank(value, 0))
            return defaultValue;
        try {
            return (int) SDDSText.scanLong(value);
        } catch (NumberFormatException e) {
            throw new IOException("Problem scanning int32_t value for namelist");
        }
    }

    /** Format one field for a legacy header (SDDS_PrintNamelistField). */
    static String legacyField(String name, String value) {
        if (value == null)
            return "";
        String raw = SDDSText.toRaw(value);
        if (raw.isEmpty())
            return name + "=\"\", ";
        StringBuilder escaped = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"')
                escaped.append('\\');
            escaped.append(c);
        }
        boolean quotes = raw.contains("&end");
        for (int i = 0; i < raw.length() && !quotes; i++)
            quotes = " ,*$\t\n\b!".indexOf(raw.charAt(i)) >= 0;
        return quotes ? name + "=\"" + escaped + "\", " : name + "=" + escaped + ", ";
    }

    /** Treat blank metadata as absent, as the legacy writers do (SDDS_BlankToNull). */
    static String blankToNull(String value) {
        return value == null || SDDSText.isBlank(value, 0) ? null : value;
    }

    /** Format one string field for an SDDS6 header (SDDS6_CreateNamelist). */
    static String field6(String name, String value) {
        if (value == null)
            return "";
        String raw = SDDSText.toRaw(value);
        StringBuilder out = new StringBuilder(raw.length() + name.length() + 8);
        out.append(name).append("=\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' || c == '"' || c == '!')
                out.append('\\');
            if (c < 32 || c == 127) {
                String octal = Integer.toOctalString(c);
                out.append('\\').append("000", 0, 3 - octal.length()).append(octal);
            } else {
                out.append(c);
            }
        }
        out.append("\", ");
        return out.toString();
    }

    /**
     * Write a finished raw command, checking the SDDS6 layout limit.
     *
     * @return the bytes to write
     */
    static byte[] finish6(String command) throws IOException {
        if (command.length() >= SDDSEnumDefinition.getLayoutLimit())
            throw new IOException("Unable to serialize bounded SDDS6 namelist");
        return command.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}
