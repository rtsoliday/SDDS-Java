/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigInteger;

/**
 * Typed value storage and encoding shared by the SDDS parameter, array and column
 * objects. Values of one element on one page are held in a primitive array chosen
 * by type: <code>long[]</code> for every integer type and for enums,
 * <code>double[]</code> for float and double, <code>String[]</code> for strings,
 * <code>char[]</code> for characters (one byte each) and
 * <code>SDDSLongDouble[]</code> for long doubles. A <code>ulong64</code> is held as
 * its raw 64 bits.
 */
final class SDDSValues {
    private SDDSValues() {
    }

    /** Allocate storage for one element. */
    static Object newArray(int type, int size) {
        switch (type) {
            case SDDSUtil.SDDS_SHORT:
            case SDDSUtil.SDDS_USHORT:
            case SDDSUtil.SDDS_LONG:
            case SDDSUtil.SDDS_ULONG:
            case SDDSUtil.SDDS_LONG64:
            case SDDSUtil.SDDS_ULONG64:
            case SDDSUtil.SDDS_ENUM:
                return new long[size];
            case SDDSUtil.SDDS_FLOAT:
            case SDDSUtil.SDDS_DOUBLE:
                return new double[size];
            case SDDSUtil.SDDS_LONGDOUBLE:
                return new SDDSLongDouble[size];
            case SDDSUtil.SDDS_STRING:
                return new String[size];
            case SDDSUtil.SDDS_CHARACTER:
                return new char[size];
            default:
                throw new IllegalArgumentException("Unknown data type " + type);
        }
    }

    /** Check that integers are complete, mapped codes of a dictionary. */
    static void checkEnumCodes(SDDSEnumDefinition definition, long[] codes, int count) {
        if (definition == null)
            throw new IllegalArgumentException("Missing enum dictionary");
        for (int i = 0; i < count; i++)
            if (!definition.hasCode(codes[i]))
                throw new IllegalArgumentException("Unmapped enum code " + codes[i] + " at value " + i);
    }

    /** Convert codes to storage, leaving a value that has not been chosen as code 0. */
    static long[] enumCells(SDDSEnumDefinition definition, Integer[] codes) {
        long[] out = new long[codes.length];
        for (int i = 0; i < codes.length; i++) {
            if (codes[i] == null)
                continue;
            if (!definition.hasCode(codes[i].intValue()))
                throw new IllegalArgumentException("Unmapped enum code " + codes[i] + " at value " + i);
            out[i] = codes[i].intValue();
        }
        return out;
    }

    private static long maskInteger(int type, long v) {
        switch (type) {
            case SDDSUtil.SDDS_USHORT:
                return v & 0xffffL;
            case SDDSUtil.SDDS_ULONG:
                return v & 0xffffffffL;
            default:
                return v;
        }
    }

    /** Convert integers to storage. Enum codes are checked against the dictionary. */
    static Object fromLongs(int type, long[] v, SDDSEnumDefinition definition) {
        Object out = newArray(type, v.length);
        switch (type) {
            case SDDSUtil.SDDS_ENUM:
                checkEnumCodes(definition, v, v.length);
                System.arraycopy(v, 0, out, 0, v.length);
                break;
            case SDDSUtil.SDDS_SHORT:
            case SDDSUtil.SDDS_USHORT:
            case SDDSUtil.SDDS_LONG:
            case SDDSUtil.SDDS_ULONG:
            case SDDSUtil.SDDS_LONG64:
            case SDDSUtil.SDDS_ULONG64:
                for (int i = 0; i < v.length; i++)
                    ((long[]) out)[i] = maskInteger(type, v[i]);
                break;
            case SDDSUtil.SDDS_FLOAT:
            case SDDSUtil.SDDS_DOUBLE:
                for (int i = 0; i < v.length; i++)
                    ((double[]) out)[i] = (double) v[i];
                break;
            case SDDSUtil.SDDS_LONGDOUBLE:
                for (int i = 0; i < v.length; i++)
                    ((SDDSLongDouble[]) out)[i] = SDDSLongDouble.valueOf(v[i]);
                break;
            case SDDSUtil.SDDS_STRING:
                for (int i = 0; i < v.length; i++)
                    ((String[]) out)[i] = Long.toString(v[i]);
                break;
            case SDDSUtil.SDDS_CHARACTER:
                for (int i = 0; i < v.length; i++)
                    ((char[]) out)[i] = Long.toString(v[i]).charAt(0);
                break;
        }
        return out;
    }

    /** Convert floating point values to storage. Enums reject numeric assignment. */
    static Object fromDoubles(int type, double[] v) {
        Object out = newArray(type, v.length);
        switch (type) {
            case SDDSUtil.SDDS_ENUM:
                throw new IllegalArgumentException(
                        "Enum values are categorical; a numeric value requires an explicit integer code");
            case SDDSUtil.SDDS_ULONG64:
                for (int i = 0; i < v.length; i++) {
                    long l = (long) v[i];
                    ((long[]) out)[i] = l < 0 ? 0 : l;
                }
                break;
            case SDDSUtil.SDDS_SHORT:
            case SDDSUtil.SDDS_USHORT:
            case SDDSUtil.SDDS_LONG:
            case SDDSUtil.SDDS_ULONG:
            case SDDSUtil.SDDS_LONG64:
                for (int i = 0; i < v.length; i++)
                    ((long[]) out)[i] = maskInteger(type, (long) v[i]);
                break;
            case SDDSUtil.SDDS_FLOAT:
            case SDDSUtil.SDDS_DOUBLE:
                System.arraycopy(v, 0, out, 0, v.length);
                break;
            case SDDSUtil.SDDS_LONGDOUBLE:
                for (int i = 0; i < v.length; i++)
                    ((SDDSLongDouble[]) out)[i] = SDDSLongDouble.valueOf(v[i]);
                break;
            case SDDSUtil.SDDS_STRING:
                for (int i = 0; i < v.length; i++)
                    ((String[]) out)[i] = Double.toString(v[i]);
                break;
            case SDDSUtil.SDDS_CHARACTER:
                for (int i = 0; i < v.length; i++)
                    ((char[]) out)[i] = Double.toString(v[i]).charAt(0);
                break;
        }
        return out;
    }

    /**
     * Convert text to storage. Enums take complete decimal codes and are checked.
     * For other numeric types text that does not parse becomes zero, which is what
     * this library has always done for these setters.
     */
    /** The value of text that C reads as NaN or an infinity, in any case, or null for other text. */
    private static Double nonFinite(String text) {
        String s = text.trim().toLowerCase();
        boolean negative = s.startsWith("-");
        if (negative || s.startsWith("+"))
            s = s.substring(1);
        if (s.equals("nan"))
            return Double.NaN;
        if (s.equals("inf") || s.equals("infinity"))
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        return null;
    }

    static Object fromStrings(int type, String[] v, SDDSEnumDefinition definition) {
        Object out = newArray(type, v.length);
        for (int i = 0; i < v.length; i++) {
            String s = v[i];
            switch (type) {
                case SDDSUtil.SDDS_ENUM: {
                    Integer code = SDDSEnumDefinition.parseCode(s);
                    if (code == null)
                        throw new IllegalArgumentException("Invalid enum code \"" + s + "\" at value " + i);
                    ((long[]) out)[i] = code.intValue();
                    break;
                }
                case SDDSUtil.SDDS_SHORT:
                case SDDSUtil.SDDS_USHORT:
                case SDDSUtil.SDDS_LONG:
                case SDDSUtil.SDDS_ULONG:
                case SDDSUtil.SDDS_LONG64:
                    try {
                        ((long[]) out)[i] = maskInteger(type, Long.parseLong(s));
                    } catch (Exception e) {
                        ((long[]) out)[i] = 0;
                    }
                    break;
                case SDDSUtil.SDDS_ULONG64:
                    try {
                        ((long[]) out)[i] = Long.parseUnsignedLong(s);
                    } catch (Exception e) {
                        ((long[]) out)[i] = 0;
                    }
                    break;
                case SDDSUtil.SDDS_FLOAT:
                case SDDSUtil.SDDS_DOUBLE:
                    try {
                        Double nonFinite = nonFinite(s);
                        ((double[]) out)[i] = nonFinite != null ? nonFinite
                                : type == SDDSUtil.SDDS_FLOAT ? Float.parseFloat(s) : Double.parseDouble(s);
                    } catch (Exception e) {
                        ((double[]) out)[i] = 0;
                    }
                    break;
                case SDDSUtil.SDDS_LONGDOUBLE:
                    try {
                        ((SDDSLongDouble[]) out)[i] = SDDSLongDouble.parse(s);
                    } catch (Exception e) {
                        ((SDDSLongDouble[]) out)[i] = SDDSLongDouble.valueOf(0L);
                    }
                    break;
                case SDDSUtil.SDDS_STRING:
                    ((String[]) out)[i] = s;
                    break;
                case SDDSUtil.SDDS_CHARACTER:
                    /* Also accepts the octal form that the getters give for these values. */
                    ((char[]) out)[i] = SDDSUtil.returnCharValueFromOctalCode(s);
                    break;
            }
        }
        if (type == SDDSUtil.SDDS_ENUM)
            checkEnumCodes(definition, (long[]) out, v.length);
        return out;
    }

    /**
     * Scan one raw ASCII token into storage, following the C scan rules.
     *
     * @throws IllegalArgumentException if the token is invalid for the type
     */
    static void scan(int type, String token, Object data, int index, SDDSEnumDefinition definition) {
        switch (type) {
            case SDDSUtil.SDDS_ENUM: {
                Integer code = SDDSEnumDefinition.parseCode(token);
                if (code == null)
                    throw new IllegalArgumentException("Invalid enum code \"" + token + "\"");
                if (definition == null || !definition.hasCode(code.intValue()))
                    throw new IllegalArgumentException("Unmapped enum code " + code);
                ((long[]) data)[index] = code.intValue();
                break;
            }
            case SDDSUtil.SDDS_SHORT:
                ((long[]) data)[index] = (short) SDDSText.scanLong(token);
                break;
            case SDDSUtil.SDDS_USHORT:
                ((long[]) data)[index] = SDDSText.scanUnsignedLong(token) & 0xffffL;
                break;
            case SDDSUtil.SDDS_LONG:
                ((long[]) data)[index] = (int) SDDSText.scanLong(token);
                break;
            case SDDSUtil.SDDS_ULONG:
                ((long[]) data)[index] = SDDSText.scanUnsignedLong(token) & 0xffffffffL;
                break;
            case SDDSUtil.SDDS_LONG64:
                ((long[]) data)[index] = SDDSText.scanLong(token);
                break;
            case SDDSUtil.SDDS_ULONG64:
                ((long[]) data)[index] = SDDSText.scanUnsignedLong(token);
                break;
            case SDDSUtil.SDDS_FLOAT:
                ((double[]) data)[index] = SDDSText.parseFloat32(SDDSText.floatPrefix(token));
                break;
            case SDDSUtil.SDDS_DOUBLE:
                ((double[]) data)[index] = SDDSText.parseFloatPrefix(SDDSText.floatPrefix(token));
                break;
            case SDDSUtil.SDDS_LONGDOUBLE:
                ((SDDSLongDouble[]) data)[index] = SDDSLongDouble.parse(token);
                break;
            case SDDSUtil.SDDS_STRING:
                ((String[]) data)[index] = SDDSText.fromRaw(SDDSText.endAtNul(SDDSText.interpretEscapes(token)));
                break;
            case SDDSUtil.SDDS_CHARACTER: {
                String s = SDDSText.interpretEscapes(token);
                ((char[]) data)[index] = s.isEmpty() ? 0 : s.charAt(0);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown data type " + type);
        }
    }

    /** Read one binary value into storage. */
    static void readBinary(DataInput in, int type, Object data, int index, boolean bigEndian,
            SDDSEnumDefinition definition) throws IOException {
        switch (type) {
            case SDDSUtil.SDDS_ENUM: {
                int code = in.readInt();
                if (definition == null || !definition.hasCode(code))
                    throw new IOException("Unmapped enum code " + code);
                ((long[]) data)[index] = code;
                break;
            }
            case SDDSUtil.SDDS_SHORT:
                ((long[]) data)[index] = in.readShort();
                break;
            case SDDSUtil.SDDS_USHORT:
                ((long[]) data)[index] = in.readShort() & 0xffffL;
                break;
            case SDDSUtil.SDDS_LONG:
                ((long[]) data)[index] = in.readInt();
                break;
            case SDDSUtil.SDDS_ULONG:
                ((long[]) data)[index] = in.readInt() & 0xffffffffL;
                break;
            case SDDSUtil.SDDS_LONG64:
            case SDDSUtil.SDDS_ULONG64:
                ((long[]) data)[index] = in.readLong();
                break;
            case SDDSUtil.SDDS_FLOAT:
                ((double[]) data)[index] = in.readFloat();
                break;
            case SDDSUtil.SDDS_DOUBLE:
                ((double[]) data)[index] = in.readDouble();
                break;
            case SDDSUtil.SDDS_LONGDOUBLE: {
                byte[] b = new byte[SDDSLongDouble.storageBytes()];
                in.readFully(b);
                ((SDDSLongDouble[]) data)[index] = SDDSLongDouble.fromBytes(b, bigEndian);
                break;
            }
            case SDDSUtil.SDDS_STRING: {
                int size = in.readInt();
                /* Reported like running out of data, because that is often the cause. */
                if (size < 0)
                    throw new java.io.EOFException("Invalid string length " + size);
                byte[] b = new byte[size];
                in.readFully(b);
                /* A string ends at a NUL, as it does in C. */
                int end = 0;
                while (end < size && b[end] != 0)
                    end++;
                ((String[]) data)[index] = SDDSText.decode(b, 0, end);
                break;
            }
            case SDDSUtil.SDDS_CHARACTER:
                ((char[]) data)[index] = (char) (in.readByte() & 0xff);
                break;
            default:
                throw new IOException("Unknown data type " + type);
        }
    }

    /** Write one stored value in binary. */
    static void writeBinary(DataOutput out, int type, Object data, int index, boolean bigEndian) throws IOException {
        switch (type) {
            case SDDSUtil.SDDS_SHORT:
            case SDDSUtil.SDDS_USHORT:
                out.writeShort((short) ((long[]) data)[index]);
                break;
            case SDDSUtil.SDDS_LONG:
            case SDDSUtil.SDDS_ULONG:
            case SDDSUtil.SDDS_ENUM:
                out.writeInt((int) ((long[]) data)[index]);
                break;
            case SDDSUtil.SDDS_LONG64:
            case SDDSUtil.SDDS_ULONG64:
                out.writeLong(((long[]) data)[index]);
                break;
            case SDDSUtil.SDDS_FLOAT:
                out.writeInt(Float.floatToRawIntBits((float) ((double[]) data)[index]));
                break;
            case SDDSUtil.SDDS_DOUBLE:
                out.writeLong(Double.doubleToRawLongBits(((double[]) data)[index]));
                break;
            case SDDSUtil.SDDS_LONGDOUBLE: {
                SDDSLongDouble v = ((SDDSLongDouble[]) data)[index];
                out.write((v == null ? SDDSLongDouble.valueOf(0L) : v).toBytes(bigEndian));
                break;
            }
            case SDDSUtil.SDDS_STRING: {
                String s = ((String[]) data)[index];
                byte[] b = s == null ? new byte[0] : SDDSText.encode(s);
                out.writeInt(b.length);
                out.write(b);
                break;
            }
            case SDDSUtil.SDDS_CHARACTER:
                /* one byte, to be compatible with the other SDDS implementations */
                out.writeByte(((char[]) data)[index] & 0xff);
                break;
            default:
                throw new IOException("Unknown data type " + type);
        }
    }

    /** Format one stored value for an ASCII page, as a raw string. */
    static String asciiText(int type, Object data, int index) {
        switch (type) {
            case SDDSUtil.SDDS_SHORT:
                return Short.toString((short) ((long[]) data)[index]);
            case SDDSUtil.SDDS_USHORT:
                return Long.toString(((long[]) data)[index] & 0xffffL);
            case SDDSUtil.SDDS_LONG:
            case SDDSUtil.SDDS_ENUM:
                return Integer.toString((int) ((long[]) data)[index]);
            case SDDSUtil.SDDS_ULONG:
                return Long.toString(((long[]) data)[index] & 0xffffffffL);
            case SDDSUtil.SDDS_LONG64:
                return Long.toString(((long[]) data)[index]);
            case SDDSUtil.SDDS_ULONG64:
                return Long.toUnsignedString(((long[]) data)[index]);
            case SDDSUtil.SDDS_FLOAT: {
                double value = ((double[]) data)[index];
                return negativeNaN(value) ? "-NaN" : Float.toString((float) value);
            }
            case SDDSUtil.SDDS_DOUBLE: {
                double value = ((double[]) data)[index];
                return negativeNaN(value) ? "-NaN" : Double.toString(value);
            }
            case SDDSUtil.SDDS_LONGDOUBLE: {
                SDDSLongDouble v = ((SDDSLongDouble[]) data)[index];
                return (v == null ? SDDSLongDouble.valueOf(0L) : v).toString();
            }
            case SDDSUtil.SDDS_STRING:
                return asciiString(((String[]) data)[index]);
            case SDDSUtil.SDDS_CHARACTER:
                return asciiCharacter(((char[]) data)[index]);
            default:
                throw new IllegalArgumentException("Unknown data type " + type);
        }
    }

    private static boolean negativeNaN(double value) {
        return Double.isNaN(value) && (Double.doubleToRawLongBits(value) < 0);
    }

    /**
     * Quote and escape a string value the way the C writer does. The difference is that
     * a backslash is written in octal, because the C reader's search for a closing quote
     * loses track of doubled backslashes.
     */
    static String asciiString(String value) {
        String raw = value == null ? "" : SDDSText.toRaw(value);
        boolean quotes = raw.isEmpty();
        for (int i = 0; i < raw.length() && !quotes; i++)
            quotes = SDDSText.isSpace(raw.charAt(i));
        StringBuilder out = new StringBuilder(raw.length() + 8);
        if (quotes)
            out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\')
                out.append("\\134");
            else if (c == '!' || c == '"')
                out.append('\\').append(c);
            else if (c == ' ' || (c > 32 && c < 127))
                out.append(c);
            else
                appendOctal(out, c);
        }
        if (quotes)
            out.append('"');
        return out.toString();
    }

    private static void appendOctal(StringBuilder out, int c) {
        String octal = Integer.toOctalString(c & 0xff);
        out.append('\\').append("000", 0, 3 - octal.length()).append(octal);
    }

    /** Format a character value for an ASCII page. */
    static String asciiCharacter(char value) {
        int c = value & 0xff;
        StringBuilder out = new StringBuilder(4);
        if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
            out.append((char) c);
        else
            appendOctal(out, c);
        return out.toString();
    }

    /** Returns the value of a ulong64, or a BigInteger when it does not fit a Long. */
    static Object boxUnsigned64(long v) {
        return v >= 0 ? (Object) Long.valueOf(v) : new BigInteger(Long.toUnsignedString(v));
    }

    /** Returns one stored value as the object handed to callers of the getters. */
    static Object box(int type, Object data, int index, SDDSEnumDefinition definition, boolean quotes) {
        switch (type) {
            case SDDSUtil.SDDS_ENUM: {
                int code = (int) ((long[]) data)[index];
                String label = definition == null ? null : definition.codeToLabel(code);
                String text = label == null ? null : definition.formatLabel(code, quotes);
                return new SDDSEnumValue(code, label, text);
            }
            case SDDSUtil.SDDS_SHORT:
                return Short.valueOf((short) ((long[]) data)[index]);
            case SDDSUtil.SDDS_USHORT:
                return Integer.valueOf((int) (((long[]) data)[index] & 0xffff));
            case SDDSUtil.SDDS_LONG:
            case SDDSUtil.SDDS_LONG64:
                return Long.valueOf(((long[]) data)[index]);
            case SDDSUtil.SDDS_ULONG:
                return Long.valueOf(((long[]) data)[index] & 0xffffffffL);
            case SDDSUtil.SDDS_ULONG64:
                return boxUnsigned64(((long[]) data)[index]);
            case SDDSUtil.SDDS_FLOAT:
                return Float.valueOf((float) ((double[]) data)[index]);
            case SDDSUtil.SDDS_DOUBLE:
                return Double.valueOf(((double[]) data)[index]);
            case SDDSUtil.SDDS_LONGDOUBLE: {
                SDDSLongDouble v = ((SDDSLongDouble[]) data)[index];
                return v == null ? SDDSLongDouble.valueOf(0L) : v;
            }
            case SDDSUtil.SDDS_STRING: {
                String s = ((String[]) data)[index];
                if (s == null)
                    s = "";
                return quotes ? SDDSUtil.prepareString(s) : s;
            }
            case SDDSUtil.SDDS_CHARACTER: {
                char c = ((char[]) data)[index];
                if (Character.isLetterOrDigit(c))
                    return Character.toString(c);
                String octal = Integer.toOctalString(c);
                StringBuilder out = new StringBuilder("\\");
                for (int i = octal.length(); i < 3; i++)
                    out.append('0');
                return out.append(octal).toString();
            }
            default:
                return null;
        }
    }

    /** Copy storage, to the given length. */
    static Object copyOf(Object data, int length) {
        Object out = Array.newInstance(data.getClass().getComponentType(), length);
        System.arraycopy(data, 0, out, 0, Math.min(length, Array.getLength(data)));
        return out;
    }

    /**
     * Convert stored values between enum, long and string (the only conversions SDDS6
     * defines for enums). All values are checked before anything is returned.
     */
    static Object convert(int sourceType, SDDSEnumDefinition source, int targetType, SDDSEnumDefinition target,
            Object data, int count) {
        Object out = newArray(targetType, count);
        for (int i = 0; i < count; i++) {
            int code = 0;
            String label = null;
            if (sourceType == SDDSUtil.SDDS_ENUM || sourceType == SDDSUtil.SDDS_LONG) {
                long stored = ((long[]) data)[i];
                if (targetType == SDDSUtil.SDDS_ENUM && (stored < Integer.MIN_VALUE || stored > Integer.MAX_VALUE))
                    throw new IllegalArgumentException("Unmapped enum code " + stored + " at value " + i);
                code = (int) stored;
            }
            if (sourceType == SDDSUtil.SDDS_ENUM) {
                label = source.codeToLabel(code);
                if (label == null)
                    throw new IllegalArgumentException("Unmapped enum code " + code + " at value " + i);
            } else if (sourceType == SDDSUtil.SDDS_STRING) {
                label = ((String[]) data)[i];
            }
            if (targetType == SDDSUtil.SDDS_ENUM) {
                if (sourceType != SDDSUtil.SDDS_LONG) {
                    Integer mapped = label == null ? null : target.labelToCode(label);
                    if (mapped == null)
                        throw new IllegalArgumentException(
                                "Enum conversion requires every label in the target mapping: \"" + label + "\"");
                    code = mapped.intValue();
                } else if (!target.hasCode(code)) {
                    throw new IllegalArgumentException("Unmapped enum code " + code + " at value " + i);
                }
                ((long[]) out)[i] = code;
            } else if (targetType == SDDSUtil.SDDS_LONG) {
                ((long[]) out)[i] = code;
            } else {
                ((String[]) out)[i] = label;
            }
        }
        return out;
    }

    /** True if a conversion is one that SDDS6 allows. */
    static boolean isConversion(int sourceType, int targetType) {
        if (sourceType == SDDSUtil.SDDS_ENUM)
            return targetType == SDDSUtil.SDDS_ENUM || targetType == SDDSUtil.SDDS_LONG
                    || targetType == SDDSUtil.SDDS_STRING;
        return targetType == SDDSUtil.SDDS_ENUM
                && (sourceType == SDDSUtil.SDDS_LONG || sourceType == SDDSUtil.SDDS_STRING);
    }
}
