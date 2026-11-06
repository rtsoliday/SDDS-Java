/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Byte-level text handling shared by the SDDS readers and writers.
 *
 * <p>
 * An SDDS file is bytes. The scanning rules here follow the C library and work on a
 * "raw" string in which each char holds one byte, so that octal escapes can produce
 * individual bytes of a UTF-8 sequence. A raw value becomes an ordinary Java string
 * only once it is complete. Strings are UTF-8, and a byte that is not part of valid
 * UTF-8 is kept as the unpaired surrogate <code>U+DC00 + byte</code>, so every file
 * round trips to exactly the same bytes.
 */
final class SDDSText {
    private SDDSText() {
    }

    /** Decode file bytes to a Java string. */
    static String decode(byte[] b, int off, int len) {
        StringBuilder out = new StringBuilder(len);
        int end = off + len;
        for (int i = off; i < end;) {
            int c = b[i] & 0xff;
            if (c < 0x80) {
                out.append((char) c);
                i++;
                continue;
            }
            int n = c >= 0xc2 && c <= 0xdf ? 1 : c >= 0xe0 && c <= 0xef ? 2 : c >= 0xf0 && c <= 0xf4 ? 3 : 0;
            int value = n == 1 ? c & 31 : n == 2 ? c & 15 : c & 7;
            boolean valid = n > 0 && i + n < end;
            for (int k = 1; valid && k <= n; k++) {
                int next = b[i + k] & 0xff;
                if ((next & 0xc0) != 0x80)
                    valid = false;
                value = (value << 6) | (next & 63);
            }
            if (valid && ((n == 2 && value < 0x800) || (n == 3 && value < 0x10000) || value > 0x10ffff
                    || (value >= 0xd800 && value <= 0xdfff)))
                valid = false;
            if (!valid) {
                out.append((char) (0xdc00 + c));
                i++;
            } else {
                out.appendCodePoint(value);
                i += n + 1;
            }
        }
        return out.toString();
    }

    /** Encode a Java string to file bytes, the inverse of {@link #decode}. */
    static byte[] encode(String s) {
        boolean ascii = true;
        for (int i = 0; i < s.length() && ascii; i++)
            ascii = s.charAt(i) < 0x80;
        if (ascii)
            return s.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                out.write(c);
            } else if (c < 0x800) {
                out.write(0xc0 | (c >> 6));
                out.write(0x80 | (c & 63));
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                int v = Character.toCodePoint(c, s.charAt(++i));
                out.write(0xf0 | (v >> 18));
                out.write(0x80 | ((v >> 12) & 63));
                out.write(0x80 | ((v >> 6) & 63));
                out.write(0x80 | (v & 63));
            } else if (c >= 0xdc80 && c <= 0xdcff) {
                out.write(c - 0xdc00);
            } else if (Character.isSurrogate(c)) {
                out.write('?');
            } else {
                out.write(0xe0 | (c >> 12));
                out.write(0x80 | ((c >> 6) & 63));
                out.write(0x80 | (c & 63));
            }
        }
        return out.toByteArray();
    }

    /** Convert a raw (one byte per char) string to a Java string. */
    static String fromRaw(String raw) {
        if (raw == null)
            return null;
        byte[] b = raw.getBytes(StandardCharsets.ISO_8859_1);
        return decode(b, 0, b.length);
    }

    /** Convert a Java string to a raw (one byte per char) string. */
    static String toRaw(String s) {
        if (s == null)
            return null;
        return new String(encode(s), StandardCharsets.ISO_8859_1);
    }

    /**
     * Read one line, including its newline, as a raw string.
     *
     * @return the line, or <code>null</code> at end of input
     */
    static String readRawLine(InputStream in) throws IOException {
        StringBuilder line = null;
        int c;
        while ((c = in.read()) != -1) {
            if (line == null)
                line = new StringBuilder(128);
            line.append((char) c);
            if (c == '\n')
                break;
        }
        return line == null ? null : line.toString();
    }

    static boolean isSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }

    static boolean isBlank(String s, int from) {
        for (int i = from; i < s.length(); i++)
            if (!isSpace(s.charAt(i)))
                return false;
        return true;
    }

    /** True if the position falls inside a quoted section (SDDS_IsQuoted). */
    static boolean isQuoted(CharSequence s, int position) {
        if (s.charAt(position) == '"')
            return true;
        boolean inside = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\'))
                inside = !inside;
            else if (i == position)
                return inside;
        }
        return false;
    }

    /** Remove an inline comment, honoring quotes and the escape <code>\!</code>. */
    static String cutOutComments(String line) {
        StringBuilder s = null;
        int i = 0;
        int n = line.length();
        CharSequence view = line;
        while (i < n) {
            char c = view.charAt(i);
            if (c == '"' && (i == 0 || view.charAt(i - 1) != '\\')) {
                i++;
                while (i < n && (view.charAt(i) != '"' || view.charAt(i - 1) == '\\'))
                    i++;
                if (i >= n)
                    return view.toString();
                i++;
                continue;
            }
            if (c == '!') {
                if (i != 0 && view.charAt(i - 1) == '\\') {
                    if (s == null) {
                        s = new StringBuilder(line);
                        view = s;
                    }
                    s.deleteCharAt(i - 1);
                    n--;
                } else {
                    boolean newline = view.charAt(n - 1) == '\n';
                    return view.subSequence(0, i).toString() + (newline ? "\n" : "");
                }
            }
            i++;
        }
        return view.toString();
    }

    /**
     * Extract the next token from a raw string (SDDS_GetToken).
     *
     * @param s      raw string
     * @param cursor position in the string, updated past the token
     * @return the token, which may be empty, or <code>null</code> if no token is left
     */
    static String nextToken(String s, int[] cursor) {
        int i = cursor[0];
        int n = s.length();
        while (i < n && isSpace(s.charAt(i)))
            i++;
        if (i >= n) {
            cursor[0] = i;
            return null;
        }
        String token;
        if (s.charAt(i) == '"') {
            int start = i + 1;
            int escaped;
            do {
                i++;
                escaped = -1;
                if (i + 1 < n && s.charAt(i) == '\\' && s.charAt(i + 1) == '\\') {
                    escaped = i + 1;
                    i += 2;
                }
            } while (i < n && (s.charAt(i) != '"' || (s.charAt(i - 1) == '\\' && i - 1 != escaped)));
            token = s.substring(start, Math.min(i, n));
            if (i < n)
                i++;
        } else {
            int start = i;
            do {
                i++;
                if (i < n && s.charAt(i) == '"' && s.charAt(i - 1) != '\\') {
                    i++;
                    while (i < n && !(s.charAt(i) == '"' && s.charAt(i - 1) != '\\'))
                        i++;
                }
            } while (i < n && !isSpace(s.charAt(i)));
            token = s.substring(start, Math.min(i, n));
        }
        cursor[0] = Math.min(i, n);
        return token;
    }

    /** Translate escape sequences in a raw string (SDDS_InterpretEscapes). */
    static String interpretEscapes(String s) {
        if (s.indexOf('\\') < 0)
            return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i++);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i >= n) {
                out.append('\\');
                break;
            }
            c = s.charAt(i);
            switch (c) {
                case 'n':
                    out.append('\n');
                    i++;
                    break;
                case 't':
                    out.append('\t');
                    i++;
                    break;
                case 'b':
                    out.append('\b');
                    i++;
                    break;
                case 'r':
                    out.append('\r');
                    i++;
                    break;
                case 'f':
                    out.append('\f');
                    i++;
                    break;
                case 'v':
                    out.append('\u000b');
                    i++;
                    break;
                case 'a':
                    out.append('\u0007');
                    i++;
                    break;
                case '\\':
                case '\'':
                case '"':
                case '?':
                case '!':
                    out.append(c);
                    i++;
                    break;
                default:
                    if (c >= '0' && c <= '9') {
                        int value = 0, count = 0;
                        while (count < 3 && i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                            value = 8 * value + (s.charAt(i++) - '0');
                            count++;
                        }
                        out.append((char) (value & 0xff));
                    } else {
                        out.append('\\');
                    }
            }
        }
        return out.toString();
    }

    /** Remove leading and trailing whitespace (SDDS_RemovePadding). */
    static String removePadding(String s) {
        int start = 0, end = s.length();
        while (start < end && isSpace(s.charAt(start)))
            start++;
        while (end > start && isSpace(s.charAt(end - 1)))
            end--;
        return s.substring(start, end);
    }

    /**
     * Find the numeric prefix that C's floating point scan conversions read.
     *
     * @return the prefix without leading whitespace, or <code>null</code> if none
     */
    static String floatPrefix(String s) {
        int i = 0, n = s.length();
        while (i < n && isSpace(s.charAt(i)))
            i++;
        int start = i;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-'))
            i++;
        if (s.regionMatches(true, i, "infinity", 0, 8))
            return s.substring(start, i + 8);
        if (s.regionMatches(true, i, "inf", 0, 3)) {
            /* Having started on "infinity" the scan cannot back up to "inf". */
            if (i + 3 < n && Character.toLowerCase(s.charAt(i + 3)) == 'i')
                return null;
            return s.substring(start, i + 3);
        }
        if (s.regionMatches(true, i, "nan", 0, 3))
            return s.substring(start, i + 3);
        if (i + 1 < n && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            int j = i + 2, digits = 0;
            while (j < n && Character.digit(s.charAt(j), 16) >= 0) {
                j++;
                digits++;
            }
            if (j < n && s.charAt(j) == '.') {
                j++;
                while (j < n && Character.digit(s.charAt(j), 16) >= 0) {
                    j++;
                    digits++;
                }
            }
            if (digits > 0) {
                int k = j;
                if (k < n && (s.charAt(k) == 'p' || s.charAt(k) == 'P')) {
                    k++;
                    if (k < n && (s.charAt(k) == '+' || s.charAt(k) == '-'))
                        k++;
                    int first = k;
                    while (k < n && s.charAt(k) >= '0' && s.charAt(k) <= '9')
                        k++;
                    if (k > first)
                        j = k;
                }
                return s.substring(start, j);
            }
            return null;
        }
        int digits = 0;
        while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
            digits++;
        }
        if (i < n && s.charAt(i) == '.') {
            i++;
            while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
                digits++;
            }
        }
        if (digits == 0)
            return null;
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            int k = i + 1;
            if (k < n && (s.charAt(k) == '+' || s.charAt(k) == '-'))
                k++;
            int first = k;
            while (k < n && s.charAt(k) >= '0' && s.charAt(k) <= '9')
                k++;
            if (k > first)
                i = k;
        }
        return s.substring(start, i);
    }

    /**
     * Parse a value from {@link #floatPrefix}.
     *
     * @throws NumberFormatException if the prefix is <code>null</code>
     */
    static double parseFloatPrefix(String prefix) {
        if (prefix == null)
            throw new NumberFormatException("no number");
        int i = prefix.charAt(0) == '+' || prefix.charAt(0) == '-' ? 1 : 0;
        boolean negative = prefix.charAt(0) == '-';
        char first = Character.toLowerCase(prefix.charAt(i));
        if (first == 'n')
            return negative ? Double.longBitsToDouble(0xfff8000000000000L) : Double.NaN;
        if (first == 'i')
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (prefix.indexOf('x') >= 0 || prefix.indexOf('X') >= 0) {
            if (prefix.indexOf('p') < 0 && prefix.indexOf('P') < 0)
                prefix = prefix + "p0";
        }
        return Double.parseDouble(prefix);
    }

    /**
     * Parse a single precision value from {@link #floatPrefix}, which rounds once,
     * directly from the text, as C's <code>%f</code> does.
     *
     * @throws NumberFormatException if the prefix is <code>null</code>
     */
    static float parseFloat32(String prefix) {
        if (prefix == null)
            throw new NumberFormatException("no number");
        char first = prefix.charAt(0) == '+' || prefix.charAt(0) == '-' ? prefix.charAt(1) : prefix.charAt(0);
        first = Character.toLowerCase(first);
        if (first == 'n' || first == 'i')
            return (float) parseFloatPrefix(prefix);
        if ((prefix.indexOf('x') >= 0 || prefix.indexOf('X') >= 0) && prefix.indexOf('p') < 0 && prefix.indexOf('P') < 0)
            prefix = prefix + "p0";
        return Float.parseFloat(prefix);
    }

    /** Cut a raw string at a NUL, where a C string would end. */
    static String endAtNul(String raw) {
        int nul = raw.indexOf(0);
        return nul < 0 ? raw : raw.substring(0, nul);
    }

    /**
     * Read an integer the way C's <code>%d</code> family does, taking the longest
     * prefix of digits. A value beyond the 64-bit range saturates.
     *
     * @throws NumberFormatException if the text does not begin with an integer
     */
    static long scanLong(String s) {
        int i = 0, n = s.length();
        while (i < n && isSpace(s.charAt(i)))
            i++;
        boolean negative = false;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-'))
            negative = s.charAt(i++) == '-';
        int first = i;
        long value = 0;
        boolean overflow = false;
        while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            int digit = s.charAt(i++) - '0';
            if (value > (Long.MAX_VALUE - digit) / 10)
                overflow = true;
            else
                value = value * 10 + digit;
        }
        if (i == first)
            throw new NumberFormatException(s);
        if (overflow)
            return negative ? Long.MIN_VALUE : Long.MAX_VALUE;
        return negative ? -value : value;
    }

    /**
     * Read an unsigned 64-bit integer the way C's <code>%lu</code> does.
     *
     * @return the raw 64 bits of the value
     * @throws NumberFormatException if the text does not begin with an integer
     */
    static long scanUnsignedLong(String s) {
        int i = 0, n = s.length();
        while (i < n && isSpace(s.charAt(i)))
            i++;
        boolean negative = false;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-'))
            negative = s.charAt(i++) == '-';
        int first = i;
        while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9')
            i++;
        if (i == first)
            throw new NumberFormatException(s);
        long value;
        try {
            value = Long.parseUnsignedLong(s.substring(first, i));
        } catch (NumberFormatException e) {
            /* An overflow gives the largest value, whatever the sign. */
            return -1L;
        }
        return negative ? -value : value;
    }
}
