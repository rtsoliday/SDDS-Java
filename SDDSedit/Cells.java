/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.regex.Pattern;

import SDDS.java.SDDS.*;

/**
 * The text form of values in table cells. The editor holds every value as text, as the
 * Qt editor does, and checks it against the SDDS type. Floating point values show the
 * fewest digits that convert back to exactly the same number.
 */
final class Cells {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern NONFINITE = Pattern.compile("(?i)[+-]?(nan|inf(inity)?)");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    private Cells() {
    }

    /* ---- formatting ---- */

    private static String nonFinite(double value) {
        if (Double.isNaN(value))
            return "nan";
        return value > 0 ? "inf" : "-inf";
    }

    /** Write digits with an exponent, in the C style of at least two exponent digits. */
    private static String scientific(String digits, int decpt) {
        StringBuilder out = new StringBuilder();
        out.append(digits.charAt(0));
        if (digits.length() > 1)
            out.append('.').append(digits, 1, digits.length());
        int exponent = decpt - 1;
        out.append('e').append(exponent < 0 ? '-' : '+');
        exponent = Math.abs(exponent);
        if (exponent < 10)
            out.append('0');
        return out.append(exponent).toString();
    }

    private static String decimal(String digits, int decpt) {
        StringBuilder out = new StringBuilder();
        if (decpt <= 0) {
            out.append("0.");
            for (int i = 0; i < -decpt; i++)
                out.append('0');
            out.append(digits);
        } else if (decpt >= digits.length()) {
            out.append(digits);
            for (int i = digits.length(); i < decpt; i++)
                out.append('0');
        } else {
            out.append(digits, 0, decpt).append('.').append(digits, decpt, digits.length());
        }
        return out.toString();
    }

    /** Text for a positive number with no precision limit, using an exponent when that is shorter. */
    private static String shortestForm(String digits, int decpt) {
        int bias = 4;
        boolean useDecimal = decpt <= 0 ? 1 - decpt <= bias : decpt <= digits.length() + bias;
        return useDecimal ? decimal(digits, decpt) : scientific(digits, decpt);
    }

    /** The fewest digits that convert back to the same double. */
    static String text(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            return nonFinite(value);
        if (value == 0)
            return Double.doubleToRawLongBits(value) < 0 ? "-0" : "0";
        double magnitude = Math.abs(value);
        BigDecimal number = new BigDecimal(Double.toString(magnitude)).stripTrailingZeros();
        while (number.precision() > 1) {
            BigDecimal shorter = number.round(new MathContext(number.precision() - 1, RoundingMode.HALF_EVEN));
            if (shorter.doubleValue() != magnitude)
                break;
            number = shorter.stripTrailingZeros();
        }
        String digits = number.unscaledValue().toString();
        return (value < 0 ? "-" : "") + shortestForm(digits, digits.length() - number.scale());
    }

    /** A number to a fixed count of significant digits, like C's <code>%g</code>. */
    private static String gForm(BigDecimal magnitude, int precision) {
        BigDecimal rounded = magnitude.round(new MathContext(precision, RoundingMode.HALF_EVEN)).stripTrailingZeros();
        String digits = rounded.unscaledValue().toString();
        int decpt = digits.length() - rounded.scale();
        return decpt > -4 && decpt <= precision ? decimal(digits, decpt) : scientific(digits, decpt);
    }

    /** The fewest digits that convert back to the same float. */
    static String text(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            return nonFinite(value);
        if (value == 0)
            return Float.floatToRawIntBits(value) < 0 ? "-0" : "0";
        BigDecimal magnitude = new BigDecimal(Math.abs((double) value));
        String sign = value < 0 ? "-" : "";
        for (int precision = 6; precision < 9; precision++) {
            String text = gForm(magnitude, precision);
            if (Float.parseFloat(text) == Math.abs(value))
                return sign + text;
        }
        return sign + gForm(magnitude, 9);
    }

    /** The fewest digits that convert back to the same long double. */
    static String text(SDDSLongDouble value) {
        if (value.isNaN() || value.isInfinite())
            return nonFinite(value.doubleValue());
        BigDecimal number = value.toBigDecimal();
        if (number.signum() == 0)
            return value.toString().startsWith("-") ? "-0" : "0";
        BigDecimal magnitude = number.abs();
        String sign = number.signum() < 0 ? "-" : "";
        for (int precision = 1; precision < 21; precision++) {
            String text = gForm(magnitude, precision);
            if (SDDSLongDouble.parse(sign + text).equals(value))
                return sign + text;
        }
        return sign + gForm(magnitude, 21);
    }

    /** Text for a value from the library's getters. */
    static String fromValue(Object value, int type) {
        if (value == null)
            return "";
        if (value instanceof SDDSEnumValue) {
            String label = ((SDDSEnumValue) value).getLabel();
            return label == null ? "" : label;
        }
        if (value instanceof Double)
            return text(((Double) value).doubleValue());
        if (value instanceof Float)
            return text(((Float) value).floatValue());
        if (value instanceof SDDSLongDouble)
            return text((SDDSLongDouble) value);
        if (type == SDDSUtil.SDDS_CHARACTER) {
            char c = SDDSUtil.returnCharValueFromOctalCode(value.toString());
            return c == 0 ? "" : String.valueOf(c);
        }
        return value.toString();
    }

    /** Text for cells the editor creates; an enum starts at code 0, or blank if that is unmapped. */
    static String newCell(Definition definition) {
        if (definition.type == SDDSUtil.SDDS_ENUM) {
            String label = definition.enumDefinition.codeToLabel(0);
            return label == null ? "" : label;
        }
        return "";
    }

    /** The shown form of a cell: <code>label (code)</code> for enums, the fewest digits for floats. */
    static String display(String text, Definition definition) {
        if (text == null)
            return "";
        if (definition.type == SDDSUtil.SDDS_ENUM) {
            Integer code = definition.enumDefinition == null ? null : definition.enumDefinition.labelToCode(text);
            return code == null ? text : text + " (" + code + ")";
        }
        return canonical(text, definition.type);
    }

    /** Show an edited number in its shortest form, if it is valid. */
    static String canonical(String text, int type) {
        if (text.isEmpty())
            return text;
        try {
            if (type == SDDSUtil.SDDS_DOUBLE && valid(text, type))
                return text(parseDouble(text.trim()));
            if (type == SDDSUtil.SDDS_FLOAT && valid(text, type))
                return text(parseFloat(text.trim()));
        } catch (NumberFormatException e) {
            return text;
        }
        return text;
    }

    /* ---- checking ---- */

    /** True if the type holds numbers. */
    static boolean numeric(int type) {
        return SDDSUtil.isNumericType(type);
    }

    /** True if the type holds integers. */
    static boolean integer(int type) {
        return numeric(type) && type != SDDSUtil.SDDS_DOUBLE && type != SDDSUtil.SDDS_FLOAT
                && type != SDDSUtil.SDDS_LONGDOUBLE;
    }

    private static boolean decimalIsZero(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'e' || c == 'E')
                break;
            if (c >= '1' && c <= '9')
                return false;
        }
        return true;
    }

    static double parseDouble(String text) {
        if (NONFINITE.matcher(text).matches()) {
            boolean negative = text.startsWith("-");
            if (text.toLowerCase().contains("nan"))
                return Double.NaN;
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Double.parseDouble(text);
    }

    static float parseFloat(String text) {
        if (NONFINITE.matcher(text).matches())
            return (float) parseDouble(text);
        return Float.parseFloat(text);
    }

    /** True if text is a complete number that a double can hold without overflow or vanishing. */
    static boolean validDouble(String text) {
        if (NONFINITE.matcher(text).matches())
            return true;
        if (!DECIMAL.matcher(text).matches())
            return false;
        double value = Double.parseDouble(text);
        return !Double.isInfinite(value) && (value != 0 || decimalIsZero(text));
    }

    private static boolean validFloat(String text) {
        if (NONFINITE.matcher(text).matches())
            return true;
        if (!DECIMAL.matcher(text).matches())
            return false;
        float value = Float.parseFloat(text);
        return !Float.isInfinite(value) && (value != 0 || decimalIsZero(text));
    }

    private static boolean validLongDouble(String text) {
        if (NONFINITE.matcher(text).matches())
            return true;
        if (!DECIMAL.matcher(text).matches())
            return false;
        SDDSLongDouble value = SDDSLongDouble.parse(text);
        return !value.isInfinite() && (value.toBigDecimal().signum() != 0 || decimalIsZero(text));
    }

    private static boolean inRange(String text, long low, BigInteger high) {
        if (!INTEGER.matcher(text).matches())
            return false;
        BigInteger value = new BigInteger(text.startsWith("+") ? text.substring(1) : text);
        return value.compareTo(BigInteger.valueOf(low)) >= 0 && value.compareTo(high) <= 0;
    }

    /** True if the text is valid for a type. Blank is valid for numbers, and means zero. */
    static boolean valid(String text, int type) {
        if (type != SDDSUtil.SDDS_CHARACTER && text.indexOf(0) >= 0)
            return false;
        if (numeric(type)) {
            String trimmed = text.trim();
            if (trimmed.isEmpty())
                return true;
            switch (type) {
                case SDDSUtil.SDDS_LONGDOUBLE:
                    return validLongDouble(trimmed);
                case SDDSUtil.SDDS_DOUBLE:
                    return validDouble(trimmed);
                case SDDSUtil.SDDS_FLOAT:
                    return validFloat(trimmed);
                case SDDSUtil.SDDS_USHORT:
                    return inRange(trimmed, 0, BigInteger.valueOf(65535));
                case SDDSUtil.SDDS_ULONG:
                    return inRange(trimmed, 0, BigInteger.valueOf(4294967295L));
                case SDDSUtil.SDDS_ULONG64:
                    return inRange(trimmed, 0, new BigInteger("18446744073709551615"));
                case SDDSUtil.SDDS_SHORT:
                    return inRange(trimmed, -32768, BigInteger.valueOf(32767));
                case SDDSUtil.SDDS_LONG:
                    return inRange(trimmed, Integer.MIN_VALUE, BigInteger.valueOf(Integer.MAX_VALUE));
                case SDDSUtil.SDDS_LONG64:
                    return inRange(trimmed, Long.MIN_VALUE, BigInteger.valueOf(Long.MAX_VALUE));
                default:
                    return false;
            }
        }
        if (type == SDDSUtil.SDDS_CHARACTER)
            return text.isEmpty() || (text.length() == 1 && text.charAt(0) <= 255);
        return true;
    }

    /** True if the text is valid for a definition, which for an enum means a declared label. */
    static boolean valid(String text, Definition definition) {
        if (definition.type == SDDSUtil.SDDS_ENUM)
            return definition.enumDefinition != null && definition.enumDefinition.labelToCode(text) != null;
        return valid(text, definition.type);
    }

    /** Explain why text is invalid for a definition. */
    static String problem(Definition definition) {
        if (definition.type == SDDSUtil.SDDS_ENUM)
            return "Value must match a declared enum label";
        if (definition.type == SDDSUtil.SDDS_CHARACTER)
            return "Character field must contain one Latin-1 character (one byte)";
        if (numeric(definition.type))
            return "Invalid numeric value";
        return "Field must not contain a NUL character";
    }

    /**
     * Compare two numeric cells. NaN and invalid text sort after valid numbers.
     *
     * @return the comparison, or 0 for equal values
     */
    static int compareNumbers(String a, String b, boolean ascending) {
        BigDecimal x = sortValue(a), y = sortValue(b);
        if (x == null || y == null)
            return x == null ? (y == null ? 0 : 1) : -1;
        int result = x.compareTo(y);
        return ascending ? result : -result;
    }

    private static final BigDecimal HUGE = new BigDecimal("1e100000");

    private static BigDecimal sortValue(String text) {
        String t = text.trim();
        if (t.isEmpty())
            return BigDecimal.ZERO;
        if (DECIMAL.matcher(t).matches()) {
            try {
                return new BigDecimal(t.endsWith(".") ? t.substring(0, t.length() - 1) : t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (NONFINITE.matcher(t).matches() && !t.toLowerCase().contains("nan"))
            return t.startsWith("-") ? HUGE.negate() : HUGE;
        return null;
    }
}
