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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import SDDS.java.SDDS.SDDSLongDouble;
import SDDS.java.SDDS.SDDSUtil;

/**
 * The expressions of Apply Numerical Expression and Fill Series. Integer cells compute
 * exactly when they can. Otherwise they use floating point only while that cannot round
 * an integer. Long double cells keep their extra digits through the four basic operators.
 */
final class Expression {
    private static final int MAX_NESTING = 256;
    private static final double INTEGER_LIMIT = Math.scalb(1.0, 52);
    private static final BigInteger MAX_MAGNITUDE = new BigInteger("18446744073709551615");
    private static final Pattern X_NAME = Pattern.compile("(?i)\\bx\\b");
    private static final Pattern A_NAME = Pattern.compile("(?i)\\ba\\b");
    private static final Pattern TOKENS = Pattern.compile("\\$\\{(x|a|i|row|col|dr|dc)\\}");

    /** An expression that cannot be used. */
    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        Failure(String message) {
            super(message);
        }
    }

    /** The values an expression can name. */
    static final class Context {
        String x = "";
        String a = "";
        int i, row, col, dr, dc;
    }

    /** Thrown inside a parser for anything outside what it can compute. */
    private static final class Stop extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Stop() {
            super(null, null, false, false);
        }
    }

    /** The arithmetic that one grammar is computed with. */
    private abstract static class Arithmetic<T> {
        final Context context;

        Arithmetic(Context context) {
            this.context = context;
        }

        abstract T literal(String text);

        abstract T fromInt(int value);

        abstract T x();

        abstract T a();

        abstract T constant(String name);

        abstract T add(T left, T right);

        abstract T subtract(T left, T right);

        abstract T multiply(T left, T right);

        abstract T divide(T left, T right);

        abstract T power(T base, T exponent);

        abstract T negate(T value);

        abstract T function(String name, T argument);
    }

    /** The one expression grammar. */
    private static final class Parser<T> {
        final String text;
        final Arithmetic<T> math;
        int pos;
        int depth;

        Parser(String text, Arithmetic<T> math) {
            this.text = text;
            this.math = math;
        }

        T parse() {
            skipSpace();
            T value = expression();
            skipSpace();
            if (pos != text.length())
                throw new Stop();
            return value;
        }

        private T expression() {
            T left = term();
            while (true) {
                skipSpace();
                if (match('+'))
                    left = math.add(left, term());
                else if (match('-'))
                    left = math.subtract(left, term());
                else
                    return left;
            }
        }

        private T term() {
            T left = unary();
            while (true) {
                skipSpace();
                if (match('*'))
                    left = math.multiply(left, unary());
                else if (match('/'))
                    left = math.divide(left, unary());
                else
                    return left;
            }
        }

        /* '^' binds tighter than a leading sign, and its exponent may have a sign. */
        private T unary() {
            if (depth >= MAX_NESTING)
                throw new Stop();
            depth++;
            try {
                skipSpace();
                if (match('+'))
                    return unary();
                if (match('-'))
                    return math.negate(unary());
                T base = primary();
                skipSpace();
                if (match('^'))
                    base = math.power(base, unary());
                return base;
            } finally {
                depth--;
            }
        }

        private T primary() {
            skipSpace();
            if (match('(')) {
                T value = expression();
                skipSpace();
                if (!match(')'))
                    throw new Stop();
                return value;
            }
            if (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.'))
                return math.literal(numberToken());
            if (pos < text.length() && (Character.isLetter(text.charAt(pos)) || text.charAt(pos) == '_')) {
                int start = pos;
                while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_'))
                    pos++;
                String name = text.substring(start, pos).toLowerCase(java.util.Locale.ROOT);
                skipSpace();
                if (match('(')) {
                    T argument = expression();
                    skipSpace();
                    if (!match(')'))
                        throw new Stop();
                    return math.function(name, argument);
                }
                return variable(name);
            }
            throw new Stop();
        }

        private T variable(String name) {
            switch (name) {
                case "x":
                    return math.x();
                case "a":
                    return math.a();
                case "i":
                    return math.fromInt(math.context.i);
                case "row":
                    return math.fromInt(math.context.row);
                case "col":
                    return math.fromInt(math.context.col);
                case "dr":
                    return math.fromInt(math.context.dr);
                case "dc":
                    return math.fromInt(math.context.dc);
                default:
                    return math.constant(name);
            }
        }

        private String numberToken() {
            int start = pos;
            boolean digits = false;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
                digits = true;
            }
            if (pos < text.length() && text.charAt(pos) == '.') {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                    digits = true;
                }
            }
            if (!digits)
                throw new Stop();
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                int look = pos + 1;
                if (look < text.length() && (text.charAt(look) == '+' || text.charAt(look) == '-'))
                    look++;
                int first = look;
                while (look < text.length() && Character.isDigit(text.charAt(look)))
                    look++;
                if (look > first)
                    pos = look;
            }
            return text.substring(start, pos);
        }

        private void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos)))
                pos++;
        }

        private boolean match(char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }
    }

    /** Integers of up to 64 bits of magnitude, with a sign. Anything else stops. */
    private static final class Exact extends Arithmetic<BigInteger> {
        final BigInteger x, a;

        Exact(Context context, BigInteger x, BigInteger a) {
            super(context);
            this.x = x;
            this.a = a;
        }

        private static BigInteger check(BigInteger value) {
            if (value.abs().compareTo(MAX_MAGNITUDE) > 0)
                throw new Stop();
            return value;
        }

        BigInteger literal(String text) {
            if (!text.matches("\\d+"))
                throw new Stop();
            return check(new BigInteger(text));
        }

        BigInteger fromInt(int value) {
            return BigInteger.valueOf(value);
        }

        BigInteger x() {
            if (x == null)
                throw new Stop();
            return x;
        }

        BigInteger a() {
            if (a == null)
                throw new Stop();
            return a;
        }

        BigInteger constant(String name) {
            throw new Stop();
        }

        BigInteger add(BigInteger left, BigInteger right) {
            return check(left.add(right));
        }

        BigInteger subtract(BigInteger left, BigInteger right) {
            return check(left.subtract(right));
        }

        BigInteger multiply(BigInteger left, BigInteger right) {
            return check(left.multiply(right));
        }

        BigInteger divide(BigInteger left, BigInteger right) {
            if (right.signum() == 0 || left.mod(right.abs()).signum() != 0)
                throw new Stop();
            return left.divide(right);
        }

        BigInteger power(BigInteger base, BigInteger exponent) {
            throw new Stop();
        }

        BigInteger negate(BigInteger value) {
            return value.negate();
        }

        BigInteger function(String name, BigInteger argument) {
            if (name.equals("abs"))
                return argument.abs();
            if (name.equals("floor") || name.equals("ceil"))
                return argument;
            throw new Stop();
        }
    }

    /** Doubles. For an integer cell, anything big enough to have rounded stops. */
    private static final class Floating extends Arithmetic<Double> {
        final boolean integerCell;
        final double x, a;

        Floating(Context context, double x, double a, boolean integerCell) {
            super(context);
            this.x = x;
            this.a = a;
            this.integerCell = integerCell;
        }

        private Double check(double value) {
            if (integerCell && !Double.isNaN(value) && !Double.isInfinite(value) && Math.abs(value) >= INTEGER_LIMIT)
                throw new Stop();
            return value;
        }

        Double literal(String text) {
            String t = text.endsWith(".") ? text + "0" : text;
            if (!Cells.validDouble(t))
                throw new Stop();
            double value = Double.parseDouble(t);
            if (integerCell && Math.abs(value) >= INTEGER_LIMIT) {
                if (!t.matches("\\d+") || !fitsDouble(new BigInteger(t)))
                    throw new Stop();
            }
            return value;
        }

        Double fromInt(int value) {
            return (double) value;
        }

        Double x() {
            return x;
        }

        Double a() {
            return a;
        }

        Double constant(String name) {
            if (name.equals("pi"))
                return Math.PI;
            if (name.equals("e"))
                return Math.E;
            throw new Stop();
        }

        Double add(Double left, Double right) {
            return check(left + right);
        }

        Double subtract(Double left, Double right) {
            return check(left - right);
        }

        Double multiply(Double left, Double right) {
            return check(left * right);
        }

        Double divide(Double left, Double right) {
            if (right == 0)
                throw new Stop();
            return check(left / right);
        }

        Double power(Double base, Double exponent) {
            return check(Math.pow(base, exponent));
        }

        Double negate(Double value) {
            return -value;
        }

        Double function(String name, Double argument) {
            return check(applyFunction(name, argument));
        }
    }

    private static double applyFunction(String name, double value) {
        switch (name) {
            case "abs":
                return Math.abs(value);
            case "sqrt":
                return Math.sqrt(value);
            case "sin":
                return Math.sin(value);
            case "cos":
                return Math.cos(value);
            case "tan":
                return Math.tan(value);
            case "log":
                return Math.log(value);
            case "exp":
                return Math.exp(value);
            case "floor":
                return Math.floor(value);
            case "ceil":
                return Math.ceil(value);
            default:
                throw new Stop();
        }
    }

    /** Decimals for long double cells, so the four operators keep all of their digits. */
    private static final class Decimal extends Arithmetic<BigDecimal> {
        final BigDecimal x, a;

        Decimal(Context context, BigDecimal x, BigDecimal a) {
            super(context);
            this.x = x;
            this.a = a;
        }

        BigDecimal literal(String text) {
            try {
                return new BigDecimal(text.endsWith(".") ? text + "0" : text);
            } catch (NumberFormatException e) {
                throw new Stop();
            }
        }

        BigDecimal fromInt(int value) {
            return BigDecimal.valueOf(value);
        }

        BigDecimal x() {
            return x;
        }

        BigDecimal a() {
            return a;
        }

        BigDecimal constant(String name) {
            if (name.equals("pi"))
                return new BigDecimal("3.14159265358979323846264338327950288");
            if (name.equals("e"))
                return new BigDecimal("2.71828182845904523536028747135266250");
            throw new Stop();
        }

        BigDecimal add(BigDecimal left, BigDecimal right) {
            return left.add(right, MathContext.DECIMAL128);
        }

        BigDecimal subtract(BigDecimal left, BigDecimal right) {
            return left.subtract(right, MathContext.DECIMAL128);
        }

        BigDecimal multiply(BigDecimal left, BigDecimal right) {
            return left.multiply(right, MathContext.DECIMAL128);
        }

        BigDecimal divide(BigDecimal left, BigDecimal right) {
            if (right.signum() == 0)
                throw new Stop();
            return left.divide(right, MathContext.DECIMAL128);
        }

        private static BigDecimal decimal(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value))
                throw new Stop();
            return new BigDecimal(value);
        }

        BigDecimal power(BigDecimal base, BigDecimal exponent) {
            return decimal(Math.pow(base.doubleValue(), exponent.doubleValue()));
        }

        BigDecimal negate(BigDecimal value) {
            return value.negate();
        }

        BigDecimal function(String name, BigDecimal argument) {
            if (name.equals("abs"))
                return argument.abs();
            return decimal(applyFunction(name, argument.doubleValue()));
        }
    }

    private Expression() {
    }

    /** True if an integer has at most 53 significant bits, so a double holds it exactly. */
    private static boolean fitsDouble(BigInteger value) {
        BigInteger magnitude = value.abs();
        return magnitude.signum() == 0 || magnitude.bitLength() - magnitude.getLowestSetBit() <= 53;
    }

    private static BigInteger exactInteger(String text) {
        String t = text.trim();
        if (t.isEmpty())
            return BigInteger.ZERO;
        if (!t.matches("[+-]?\\d+"))
            return null;
        BigInteger value = new BigInteger(t.startsWith("+") ? t.substring(1) : t);
        return value.abs().compareTo(MAX_MAGNITUDE) > 0 ? null : value;
    }

    private static double toDouble(String text) {
        String t = text.trim();
        return t.isEmpty() ? 0 : Cells.parseDouble(t);
    }

    /** True if text is a number, or blank. */
    static boolean isNumber(String text) {
        return Cells.valid(text, SDDSUtil.SDDS_LONGDOUBLE);
    }

    private static BigDecimal decimalOf(String text) {
        String t = text.trim();
        if (t.isEmpty())
            return BigDecimal.ZERO;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Text for a computed value in a cell of a type; an out-of-range value then fails validation. */
    private static String resultText(double value, int type) {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            if (type == SDDSUtil.SDDS_FLOAT) {
                if (Math.abs(value) <= Float.MAX_VALUE) {
                    float narrowed = (float) value;
                    if (value == 0 || narrowed != 0)
                        return Cells.text(narrowed);
                }
            } else if (Cells.integer(type) && value == Math.floor(value)) {
                BigInteger exact = new BigDecimal(value).toBigInteger();
                if (exact.abs().compareTo(MAX_MAGNITUDE) <= 0)
                    return exact.toString();
            }
        }
        return Cells.text(value);
    }

    /**
     * Evaluate an expression for a cell.
     *
     * @param expression the expression
     * @param context    the values it can name
     * @param type       the type of the cell
     * @return the cell's new text, not yet checked against the type
     * @throws Failure with the message to show
     */
    static String evaluate(String expression, Context context, int type) throws Failure {
        if (!isNumber(context.x))
            throw new Failure("Cell contains invalid numeric value: " + context.x);
        boolean integerCell = Cells.integer(type);
        if (integerCell) {
            BigInteger x = exactInteger(context.x), a = exactInteger(context.a);
            try {
                return new Parser<BigInteger>(expression, new Exact(context, x, a)).parse().toString();
            } catch (Stop stop) {
                /* Not plain integer arithmetic; try floating point below. */
            }
            boolean usesX = X_NAME.matcher(expression).find(), usesA = A_NAME.matcher(expression).find();
            if ((usesX && x != null && !fitsDouble(x)) || (usesA && a != null && !fitsDouble(a)))
                throw new Failure("This expression would lose precision in a 64-bit integer operand. "
                        + "Use integer literals with +, -, *, exact division, or abs, "
                        + "or change the destination type to floating point.");
        }
        if (type == SDDSUtil.SDDS_LONGDOUBLE) {
            BigDecimal x = decimalOf(context.x), a = decimalOf(context.a);
            if (x != null && a != null) {
                try {
                    BigDecimal result = new Parser<BigDecimal>(expression, new Decimal(context, x, a)).parse();
                    SDDSLongDouble value = SDDSLongDouble.valueOf(result);
                    return value.isInfinite() ? result.toString() : Cells.text(value);
                } catch (Stop | ArithmeticException stop) {
                    /* Non-finite or too extreme for decimals; use floating point. */
                }
            }
        }
        double a = isNumber(context.a) ? toDouble(context.a) : 0;
        double result;
        try {
            result = new Parser<Double>(expression, new Floating(context, toDouble(context.x), a, integerCell)).parse();
        } catch (Stop stop) {
            throw new Failure(integerCell
                    ? "The expression is invalid or cannot be evaluated without losing integer precision."
                    : "Failed to evaluate expression.");
        }
        if (integerCell && !Double.isNaN(result) && !Double.isInfinite(result) && Math.abs(result) >= INTEGER_LIMIT)
            throw new Failure("This integer result is too large to verify using floating-point arithmetic. "
                    + "Use an exact integer expression.");
        return resultText(result, type);
    }

    /**
     * The value of one cell of a Fill Series.
     *
     * @param start start text
     * @param step  step text
     * @param i     position in the series
     * @param type  type of the cell
     * @return the cell's new text, not yet checked against the type
     * @throws Failure with the message to show
     */
    static String series(String start, String step, int i, int type) throws Failure {
        if (Cells.integer(type)) {
            BigInteger first = exactInteger(start), increment = exactInteger(step);
            if (first != null && increment != null) {
                BigInteger product = increment.multiply(BigInteger.valueOf(i));
                BigInteger value = first.add(product);
                if (product.abs().compareTo(MAX_MAGNITUDE) <= 0 && value.abs().compareTo(MAX_MAGNITUDE) <= 0)
                    return value.toString();
            }
            Context context = new Context();
            context.i = i;
            String expression = "(" + (start.trim().isEmpty() ? "0" : start) + ") + ("
                    + (step.trim().isEmpty() ? "0" : step) + ") * i";
            try {
                double result = new Parser<Double>(expression, new Floating(context, 0, 0, true)).parse();
                return resultText(result, type);
            } catch (Stop stop) {
                throw new Failure("This series cannot be evaluated without losing integer precision. "
                        + "Use integer start and step values, or change the destination type to floating point.");
            }
        }
        if (type == SDDSUtil.SDDS_LONGDOUBLE) {
            BigDecimal first = decimalOf(start), increment = decimalOf(step);
            if (first != null && increment != null) {
                BigDecimal result = first.add(increment.multiply(BigDecimal.valueOf(i)), MathContext.DECIMAL128);
                SDDSLongDouble value = SDDSLongDouble.valueOf(result);
                return value.isInfinite() ? result.toString() : Cells.text(value);
            }
        }
        return resultText(toDouble(start) + toDouble(step) * i, type);
    }

    /** Replace the tokens of a text formula. Text from a cell is not scanned again. */
    static String applyTemplate(String template, String x, String a, int i, int row, int col, int dr, int dc) {
        Matcher matcher = TOKENS.matcher(template);
        StringBuilder out = new StringBuilder();
        int offset = 0;
        while (matcher.find()) {
            out.append(template, offset, matcher.start());
            switch (matcher.group(1)) {
                case "x":
                    out.append(x);
                    break;
                case "a":
                    out.append(a);
                    break;
                case "i":
                    out.append(i);
                    break;
                case "row":
                    out.append(row);
                    break;
                case "col":
                    out.append(col);
                    break;
                case "dr":
                    out.append(dr);
                    break;
                default:
                    out.append(dc);
                    break;
            }
            offset = matcher.end();
        }
        return out.append(template, offset, template.length()).toString();
    }
}
