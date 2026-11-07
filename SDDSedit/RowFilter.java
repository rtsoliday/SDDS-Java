/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import SDDS.java.SDDS.SDDSUtil;

/**
 * The expression language of Filter/View Rows. It has comparisons, <code>&amp;&amp;</code>,
 * <code>||</code>, <code>!</code> and parentheses over column names, <code>[quoted names]</code>,
 * <code>row</code> or <code>i</code>, numbers, quoted strings, <code>true</code> and
 * <code>false</code>. Integers and decimals compare exactly, with no rounding.
 */
final class RowFilter {
    private static final int MAX_NESTING = 256;
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    /** A filter that cannot be applied. */
    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        Failure(String message) {
            super(message);
        }
    }

    private enum Kind {
        INVALID, END, IDENTIFIER, NUMBER, STRING, LPAREN, RPAREN, AND, OR, NOT, EQ, NE, LT, LE, GT, GE
    }

    private static final class Value {
        String text = "";
        boolean hasNumber;
        boolean categorical;
    }

    /** Supplies a row's cell for a name, or <code>false</code> for an unknown name. */
    private interface Resolver {
        boolean resolve(String name, boolean columnOnly, Value out);
    }

    private final String input;
    private final Resolver resolver;
    private int pos;
    private int depth;
    private Kind kind;
    private String tokenText;
    private boolean tokenBracketed;

    private RowFilter(String expression, Resolver resolver) {
        this.input = expression;
        this.resolver = resolver;
        next();
    }

    /**
     * Work out which rows of a page pass a filter.
     *
     * @return one entry per row
     * @throws Failure with the message to show, if the filter is invalid
     */
    static boolean[] evaluate(String expression, Document doc, int pageIndex) throws Failure {
        final Document.Page page = doc.pages.get(pageIndex);
        final List<Definition> columns = doc.columns;
        final Map<String, Integer> exact = new HashMap<String, Integer>();
        final Map<String, Integer> folded = new HashMap<String, Integer>();
        for (int c = 0; c < columns.size(); c++) {
            String name = columns.get(c).name.trim();
            if (name.isEmpty())
                continue;
            exact.put(name, c);
            if (!folded.containsKey(name.toLowerCase(java.util.Locale.ROOT)))
                folded.put(name.toLowerCase(java.util.Locale.ROOT), c);
        }
        boolean[] pass = new boolean[page.rows];
        for (int r = 0; r < page.rows; r++) {
            final int row = r;
            Resolver resolver = new Resolver() {
                public boolean resolve(String name, boolean columnOnly, Value out) {
                    String lowered = name.toLowerCase(java.util.Locale.ROOT);
                    if (!columnOnly && (lowered.equals("row") || lowered.equals("i"))) {
                        out.text = String.valueOf(row);
                        return true;
                    }
                    Integer column = exact.get(name);
                    if (column == null)
                        column = folded.get(lowered);
                    if (column == null)
                        return false;
                    Definition d = columns.get(column);
                    out.categorical = d.isEnum();
                    out.text = page.columns[column][row];
                    if (d.isNumeric() && out.text.trim().isEmpty())
                        out.text = "0";
                    return true;
                }
            };
            RowFilter parser = new RowFilter(expression, resolver);
            try {
                pass[r] = parser.parse();
            } catch (Failure e) {
                throw new Failure("Row filter error at row " + (r + 1) + ": "
                        + (e.getMessage().isEmpty() ? "invalid expression" : e.getMessage()));
            }
        }
        return pass;
    }

    /** The default expression for a column, quoting a name that would mean something else. */
    static String defaultExpression(String column) {
        String lowered = column.toLowerCase(java.util.Locale.ROOT);
        if (!column.matches("[A-Za-z_][A-Za-z0-9_]*") || lowered.equals("row") || lowered.equals("i")
                || lowered.equals("true") || lowered.equals("false"))
            column = "[" + column + "]";
        return column + " > 0";
    }

    private boolean parse() throws Failure {
        if (kind == Kind.INVALID)
            throw new Failure(tokenText);
        boolean value = parseOr();
        if (kind == Kind.INVALID)
            throw new Failure(tokenText);
        if (kind != Kind.END)
            throw new Failure("Unexpected token '" + tokenText + "'");
        return value;
    }

    private boolean parseOr() throws Failure {
        boolean left = parseAnd();
        while (kind == Kind.OR) {
            next();
            boolean right = parseAnd();
            left = left || right;
        }
        return left;
    }

    private boolean parseAnd() throws Failure {
        boolean left = parseUnary();
        while (kind == Kind.AND) {
            next();
            boolean right = parseUnary();
            left = left && right;
        }
        return left;
    }

    private boolean parseUnary() throws Failure {
        if (depth >= MAX_NESTING)
            throw new Failure("Filter expression is nested too deeply");
        depth++;
        try {
            if (kind == Kind.NOT) {
                next();
                return !parseUnary();
            }
            return parsePrimary();
        } finally {
            depth--;
        }
    }

    private boolean parsePrimary() throws Failure {
        if (kind == Kind.LPAREN) {
            next();
            boolean inner = parseOr();
            if (kind != Kind.RPAREN)
                throw new Failure("Missing closing ')' in filter expression");
            next();
            return inner;
        }
        Value left = parseValue();
        Kind op = kind;
        if (op == Kind.EQ || op == Kind.NE || op == Kind.LT || op == Kind.LE || op == Kind.GT || op == Kind.GE) {
            next();
            return compare(left, op, parseValue());
        }
        return truth(left);
    }

    private static boolean hasNumber(String text) {
        return !text.trim().isEmpty() && Cells.valid(text, SDDSUtil.SDDS_LONGDOUBLE);
    }

    private Value parseValue() throws Failure {
        Value out = new Value();
        if (kind == Kind.STRING) {
            out.text = tokenText;
            out.hasNumber = hasNumber(out.text);
            next();
            return out;
        }
        if (kind == Kind.NUMBER) {
            out.text = tokenText;
            out.hasNumber = hasNumber(out.text);
            if (!out.hasNumber)
                throw new Failure("Invalid numeric literal '" + out.text + "'");
            next();
            return out;
        }
        if (kind == Kind.IDENTIFIER) {
            String lowered = tokenBracketed ? "" : tokenText.toLowerCase(java.util.Locale.ROOT);
            if (lowered.equals("true") || lowered.equals("false")) {
                out.text = lowered.equals("true") ? "1" : "0";
                out.hasNumber = true;
                next();
                return out;
            }
            if (!resolver.resolve(tokenText, tokenBracketed, out))
                throw new Failure("Unknown row variable/column '" + tokenText + "'");
            out.hasNumber = hasNumber(out.text);
            next();
            return out;
        }
        throw new Failure("Expected value in filter expression");
    }

    private static double numberOf(String text) {
        return Cells.parseDouble(text.trim());
    }

    private static boolean truth(Value value) {
        String t = value.text.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.hasNumber) {
            if (DECIMAL.matcher(t).matches())
                return new BigDecimal(t.endsWith(".") ? t.substring(0, t.length() - 1) : t).signum() != 0;
            return true;
        }
        return !(t.isEmpty() || t.equals("0") || t.equals("false") || t.equals("no") || t.equals("off"));
    }

    private static int compareBytes(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < a.length && i < b.length; i++) {
            if (a[i] != b[i])
                return (a[i] & 0xff) - (b[i] & 0xff);
        }
        return a.length - b.length;
    }

    private static BigInteger integerOf(String text) {
        String t = text.trim();
        if (!INTEGER.matcher(t).matches())
            return null;
        return new BigInteger(t.startsWith("+") ? t.substring(1) : t);
    }

    private static BigDecimal decimalOf(String text) {
        String t = text.trim();
        if (!DECIMAL.matcher(t).matches())
            return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean test(Kind op, int comparison) {
        switch (op) {
            case EQ:
                return comparison == 0;
            case NE:
                return comparison != 0;
            case LT:
                return comparison < 0;
            case LE:
                return comparison <= 0;
            case GT:
                return comparison > 0;
            case GE:
                return comparison >= 0;
            default:
                return false;
        }
    }

    private static boolean compare(Value left, Kind op, Value right) {
        boolean categorical = left.categorical || right.categorical;
        if (categorical)
            return test(op, compareBytes(left.text, right.text));
        BigInteger x = integerOf(left.text), y = integerOf(right.text);
        if (x != null && y != null)
            return test(op, x.compareTo(y));
        if (left.hasNumber && right.hasNumber) {
            BigDecimal p = decimalOf(left.text), q = decimalOf(right.text);
            if (p != null && q != null)
                return test(op, p.compareTo(q));
            /* NaN and infinities follow the floating point rules. */
            double a = numberOf(left.text), b = numberOf(right.text);
            switch (op) {
                case EQ:
                    return a == b;
                case NE:
                    return a != b;
                case LT:
                    return a < b;
                case LE:
                    return a <= b;
                case GT:
                    return a > b;
                case GE:
                    return a >= b;
                default:
                    return false;
            }
        }
        return test(op, left.text.compareTo(right.text));
    }

    /* ---- tokens ---- */

    private void token(Kind kind, String text) {
        this.kind = kind;
        this.tokenText = text;
        this.tokenBracketed = false;
    }

    private boolean readNumber() {
        int start = pos;
        boolean seenDigit = false, seenDot = false;
        if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-'))
            pos++;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                seenDigit = true;
                pos++;
            } else if (c == '.' && !seenDot) {
                seenDot = true;
                pos++;
            } else if ((c == 'e' || c == 'E') && seenDigit) {
                int look = pos + 1;
                if (look < input.length() && (input.charAt(look) == '+' || input.charAt(look) == '-'))
                    look++;
                boolean digits = false;
                while (look < input.length() && Character.isDigit(input.charAt(look))) {
                    digits = true;
                    look++;
                }
                if (!digits)
                    break;
                pos = look;
            } else {
                break;
            }
        }
        if (!seenDigit) {
            pos = start;
            return false;
        }
        token(Kind.NUMBER, input.substring(start, pos));
        return true;
    }

    private void readString(char quote) {
        pos++;
        StringBuilder value = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == quote) {
                token(Kind.STRING, value.toString());
                return;
            }
            if (c == '\\' && pos < input.length()) {
                char e = input.charAt(pos++);
                value.append(e == 'n' ? '\n' : e == 'r' ? '\r' : e == 't' ? '\t' : e);
            } else {
                value.append(c);
            }
        }
        token(Kind.INVALID, "Unterminated string literal");
    }

    /* SDDS names may contain brackets, so nested pairs belong to the name. */
    private void readBracketed() {
        pos++;
        int start = pos, level = 1;
        for (; pos < input.length(); pos++) {
            char c = input.charAt(pos);
            if (c == '[')
                level++;
            else if (c == ']' && --level == 0)
                break;
        }
        if (pos >= input.length()) {
            token(Kind.INVALID, "Unterminated [column] name");
            return;
        }
        token(Kind.IDENTIFIER, input.substring(start, pos).trim());
        tokenBracketed = true;
        pos++;
    }

    private void next() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos)))
            pos++;
        if (pos >= input.length()) {
            token(Kind.END, "");
            return;
        }
        char c = input.charAt(pos);
        if (Character.isLetter(c) || c == '_') {
            int start = pos++;
            while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_'))
                pos++;
            token(Kind.IDENTIFIER, input.substring(start, pos));
            return;
        }
        if (c == '[') {
            readBracketed();
            return;
        }
        /* A stray sign or dot is reported, so "X ->= 1" is not read as "X >= 1". */
        if ((Character.isDigit(c) || c == '.' || c == '+' || c == '-') && readNumber())
            return;
        if (c == '"' || c == '\'') {
            readString(c);
            return;
        }
        for (Object[] pair : TWO_CHARACTER) {
            if (input.startsWith((String) pair[0], pos)) {
                pos += 2;
                token((Kind) pair[1], (String) pair[0]);
                return;
            }
        }
        pos++;
        switch (c) {
            case '(':
                token(Kind.LPAREN, "(");
                return;
            case ')':
                token(Kind.RPAREN, ")");
                return;
            case '!':
                token(Kind.NOT, "!");
                return;
            case '<':
                token(Kind.LT, "<");
                return;
            case '>':
                token(Kind.GT, ">");
                return;
            default:
                token(Kind.INVALID, "Unexpected character '" + c + "'");
        }
    }

    private static final Object[][] TWO_CHARACTER = { { "&&", Kind.AND }, { "||", Kind.OR }, { "==", Kind.EQ },
            { "!=", Kind.NE }, { "<=", Kind.LE }, { ">=", Kind.GE } };
}
