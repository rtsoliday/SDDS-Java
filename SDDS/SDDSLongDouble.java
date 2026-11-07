/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * An exact SDDS <code>longdouble</code> value. SDDS binary files hold these as x87
 * 80-bit extended precision numbers in 16 bytes, which Java has no primitive for.
 * Keeping the bits unchanged means reading and rewriting a file loses no precision,
 * including for NaN and infinite values. A little endian value is the 64-bit
 * significand, then the sign and exponent, then six bytes of padding. In a big endian
 * file the C library reverses only the first twelve of those bytes, giving two bytes
 * of padding, the sign and exponent, the significand and four more bytes of padding.
 */
public final class SDDSLongDouble extends Number implements Comparable<SDDSLongDouble> {
    private static final long serialVersionUID = 1L;
    private static final int EXPONENT_MASK = 0x7fff;
    private static final int BIAS = 16383;
    private static final int DECIMAL_DIGITS = 21;
    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    /** Sign in bit 15 and the biased exponent in bits 0 to 14. */
    private final int signExponent;
    /** The 64-bit significand, including the explicit integer bit. */
    private final long significand;

    private SDDSLongDouble(int signExponent, long significand) {
        this.signExponent = signExponent & 0xffff;
        this.significand = significand;
    }

    /**
     * Returns the number of bytes one value occupies in a binary file. This is 16,
     * or 8 when the <code>SDDS_LONGDOUBLE_64BITS</code> environment variable asks for
     * the older files that stored these values as 64-bit floats.
     */
    public static int storageBytes() {
        return (System.getenv("SDDS_LONGDOUBLE_64BITS") != null) && !cHasExtendedPrecision() ? 8 : 16;
    }

    /**
     * True where the C library has x87 long doubles and so ignores
     * <code>SDDS_LONGDOUBLE_64BITS</code>, which keeps a file readable the same way by
     * both libraries on one computer.
     */
    private static boolean cHasExtendedPrecision() {
        String arch = System.getProperty("os.arch", "");
        boolean x86 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x86") || arch.equals("i386");
        return x86 && !System.getProperty("os.name", "").startsWith("Windows");
    }

    /**
     * Decode one value as stored in a binary file.
     *
     * @param b         bytes, <code>storageBytes()</code> long
     * @param bigEndian <code>true</code> for big endian data
     */
    public static SDDSLongDouble fromBytes(byte[] b, boolean bigEndian) {
        if (b.length == 8) {
            long bits = 0;
            for (int i = 0; i < 8; i++)
                bits = (bits << 8) | (b[bigEndian ? i : 7 - i] & 0xffL);
            return valueOf(Double.longBitsToDouble(bits));
        }
        long significand = 0;
        for (int i = 0; i < 8; i++)
            significand = (significand << 8) | (b[bigEndian ? 4 + i : 7 - i] & 0xffL);
        int signExponent = bigEndian ? ((b[2] & 0xff) << 8) | (b[3] & 0xff) : ((b[9] & 0xff) << 8) | (b[8] & 0xff);
        return new SDDSLongDouble(signExponent, significand);
    }

    /**
     * Encode this value as stored in a binary file.
     *
     * @param bigEndian <code>true</code> for big endian data
     * @return <code>storageBytes()</code> bytes
     */
    public byte[] toBytes(boolean bigEndian) {
        int size = storageBytes();
        byte[] b = new byte[size];
        if (size == 8) {
            long bits = Double.doubleToRawLongBits(doubleValue());
            for (int i = 0; i < 8; i++)
                b[bigEndian ? 7 - i : i] = (byte) (bits >>> (8 * i));
            return b;
        }
        for (int i = 0; i < 8; i++)
            b[bigEndian ? 11 - i : i] = (byte) (significand >>> (8 * i));
        b[bigEndian ? 3 : 8] = (byte) signExponent;
        b[bigEndian ? 2 : 9] = (byte) (signExponent >>> 8);
        return b;
    }

    /** Returns the exact long double value of a double. */
    public static SDDSLongDouble valueOf(double d) {
        long bits = Double.doubleToRawLongBits(d);
        int sign = bits < 0 ? 0x8000 : 0;
        int exponent = (int) ((bits >>> 52) & 0x7ff);
        long fraction = bits & 0xfffffffffffffL;
        if (exponent == 0x7ff)
            return new SDDSLongDouble(sign | EXPONENT_MASK,
                    fraction == 0 ? Long.MIN_VALUE : 0xc000000000000000L | (fraction << 11));
        if (exponent == 0) {
            if (fraction == 0)
                return new SDDSLongDouble(sign, 0);
            int shift = Long.numberOfLeadingZeros(fraction);
            return new SDDSLongDouble(sign | (BIAS - 1022 - (shift - 11)), fraction << shift);
        }
        return new SDDSLongDouble(sign | (exponent - 1023 + BIAS), Long.MIN_VALUE | (fraction << 11));
    }

    /** Returns the long double value of an integer. */
    public static SDDSLongDouble valueOf(long v) {
        if (v == 0)
            return new SDDSLongDouble(0, 0);
        boolean negative = v < 0;
        long magnitude = negative ? -v : v;
        int shift = Long.numberOfLeadingZeros(magnitude);
        return new SDDSLongDouble((negative ? 0x8000 : 0) | (BIAS + 63 - shift), magnitude << shift);
    }

    /** Returns the correctly rounded long double value of a decimal number. */
    public static SDDSLongDouble valueOf(BigDecimal v) {
        return round(v.signum() < 0, v.abs());
    }

    /**
     * Parse a number the way C's <code>%Lf</code> conversion does: it reads the
     * longest numeric prefix and accepts <code>nan</code> and <code>inf</code> in any
     * case.
     *
     * @throws NumberFormatException if the text does not begin with a number
     */
    public static SDDSLongDouble parse(String text) {
        String number = SDDSText.floatPrefix(text);
        if (number == null)
            throw new NumberFormatException(text);
        boolean negative = number.charAt(0) == '-';
        String body = number.charAt(0) == '-' || number.charAt(0) == '+' ? number.substring(1) : number;
        char first = Character.toLowerCase(body.charAt(0));
        if (first == 'n')
            return new SDDSLongDouble((negative ? 0x8000 : 0) | EXPONENT_MASK, 0xc000000000000000L);
        if (first == 'i')
            return new SDDSLongDouble((negative ? 0x8000 : 0) | EXPONENT_MASK, Long.MIN_VALUE);
        if (body.length() > 1 && first == '0' && Character.toLowerCase(body.charAt(1)) == 'x')
            return parseHexadecimal(negative, body.substring(2));
        try {
            return round(negative, new BigDecimal(body));
        } catch (NumberFormatException e) {
            /* An exponent too large even for BigDecimal is an overflow or an underflow. */
            String lower = body.toLowerCase(java.util.Locale.ROOT);
            int e10 = lower.indexOf('e');
            boolean zero = lower.substring(0, e10 < 0 ? lower.length() : e10).replace(".", "").replace("0", "").isEmpty();
            if (zero || (e10 >= 0 && lower.startsWith("-", e10 + 1)))
                return new SDDSLongDouble(negative ? 0x8000 : 0, 0);
            return new SDDSLongDouble((negative ? 0x8000 : 0) | EXPONENT_MASK, Long.MIN_VALUE);
        }
    }

    /** Read hexadecimal digits with an optional fraction and binary exponent, exactly. */
    private static SDDSLongDouble parseHexadecimal(boolean negative, String text) {
        int p = Math.max(text.indexOf('p'), text.indexOf('P'));
        String digits = p < 0 ? text : text.substring(0, p);
        int point = digits.indexOf('.');
        int fraction = point < 0 ? 0 : digits.length() - point - 1;
        BigInteger mantissa = new BigInteger(digits.replace(".", ""), 16);
        long exponent = -4L * fraction;
        if (p >= 0)
            exponent += Math.max(Math.min(SDDSText.scanLong(text.substring(p + 1)), 100000), -100000);
        if (mantissa.signum() == 0 || exponent + mantissa.bitLength() < -17000)
            return new SDDSLongDouble(negative ? 0x8000 : 0, 0);
        if (exponent + mantissa.bitLength() > 17000)
            return new SDDSLongDouble((negative ? 0x8000 : 0) | EXPONENT_MASK, Long.MIN_VALUE);
        BigDecimal value = exponent >= 0 ? new BigDecimal(mantissa.shiftLeft((int) exponent))
                : new BigDecimal(mantissa.multiply(BigInteger.valueOf(5).pow((int) -exponent)), (int) -exponent);
        return round(negative, value);
    }

    /** Round a nonnegative decimal to 64 significant bits, ties to even. */
    private static SDDSLongDouble round(boolean negative, BigDecimal v) {
        int sign = negative ? 0x8000 : 0;
        if (v.signum() == 0)
            return new SDDSLongDouble(sign, 0);
        long magnitude = (long) v.precision() - (long) v.scale();
        if (magnitude > 5000)
            return new SDDSLongDouble(sign | EXPONENT_MASK, Long.MIN_VALUE);
        if (magnitude < -5000)
            return new SDDSLongDouble(sign, 0);
        BigInteger numerator = v.unscaledValue();
        BigInteger denominator = BigInteger.ONE;
        if (v.scale() > 0)
            denominator = BigInteger.TEN.pow(v.scale());
        else
            numerator = numerator.multiply(BigInteger.TEN.pow(-v.scale()));
        int shift = 66 - (numerator.bitLength() - denominator.bitLength());
        BigInteger[] qr = shift >= 0 ? numerator.shiftLeft(shift).divideAndRemainder(denominator)
                : numerator.divideAndRemainder(denominator.shiftLeft(-shift));
        BigInteger quotient = qr[0];
        boolean sticky = qr[1].signum() != 0;
        int bits = quotient.bitLength();
        int biased = bits - 1 - shift + BIAS;
        int drop = bits - 64;
        if (biased <= 0) {
            drop += 1 - biased;
            biased = 0;
        }
        if (drop > bits + 1)
            return new SDDSLongDouble(sign, 0);
        BigInteger kept = quotient.shiftRight(drop);
        boolean half = quotient.testBit(drop - 1);
        boolean lower = sticky || quotient.getLowestSetBit() < drop - 1;
        if (half && (lower || kept.testBit(0)))
            kept = kept.add(BigInteger.ONE);
        if (kept.bitLength() > 64) {
            kept = kept.shiftRight(1);
            biased++;
        } else if (biased == 0 && kept.testBit(63)) {
            biased = 1;
        }
        if (biased >= EXPONENT_MASK)
            return new SDDSLongDouble(sign | EXPONENT_MASK, Long.MIN_VALUE);
        return new SDDSLongDouble(sign | biased, kept.longValue());
    }

    /** Returns true if this value is not a number. */
    public boolean isNaN() {
        return (signExponent & EXPONENT_MASK) == EXPONENT_MASK && (significand << 1) != 0;
    }

    /** Returns true if this value is positive or negative infinity. */
    public boolean isInfinite() {
        return (signExponent & EXPONENT_MASK) == EXPONENT_MASK && (significand << 1) == 0;
    }

    private boolean isNegative() {
        return (signExponent & 0x8000) != 0;
    }

    /**
     * Returns the exact decimal value.
     *
     * @throws NumberFormatException if the value is NaN or infinite
     */
    public BigDecimal toBigDecimal() {
        if (isNaN() || isInfinite())
            throw new NumberFormatException("Not a finite number");
        BigInteger unsigned = new BigInteger(Long.toUnsignedString(significand));
        if (unsigned.signum() == 0)
            return BigDecimal.ZERO;
        int biased = signExponent & EXPONENT_MASK;
        int exponent = (biased == 0 ? 1 : biased) - BIAS - 63;
        BigDecimal result;
        if (exponent >= 0)
            result = new BigDecimal(unsigned.shiftLeft(exponent));
        else
            result = new BigDecimal(unsigned.multiply(BigInteger.valueOf(5).pow(-exponent)), -exponent);
        return isNegative() ? result.negate() : result;
    }

    public double doubleValue() {
        if (isNaN())
            return Double.NaN;
        if (isInfinite())
            return isNegative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        double result;
        if (significand == 0) {
            result = 0;
        } else {
            int biased = signExponent & EXPONENT_MASK;
            int exponent = (biased == 0 ? 1 : biased) - BIAS - 63;
            /* Below the normal range a double has fewer bits, so round just once from the exact value. */
            if (exponent + 64 - Long.numberOfLeadingZeros(significand) < -1021)
                return toBigDecimal().doubleValue();
            long top = significand >>> 11;
            long rest = significand & 0x7ff;
            if (rest > 0x400 || (rest == 0x400 && (top & 1) != 0))
                top++;
            result = Math.scalb((double) top, Math.max(Math.min(exponent + 11, 4000), -4000));
        }
        return isNegative() ? -result : result;
    }

    public float floatValue() {
        if (isNaN() || isInfinite())
            return (float) doubleValue();
        return toBigDecimal().floatValue();
    }

    /** Returns the value truncated toward zero, limited to the range of a long. */
    public long longValue() {
        if (isNaN())
            return 0;
        if (isInfinite())
            return isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
        if (significand == 0)
            return 0;
        int biased = signExponent & EXPONENT_MASK;
        int exponent = (biased == 0 ? 1 : biased) - BIAS - 63;
        if (exponent >= 0)
            return isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
        if (exponent < -63)
            return 0;
        long magnitude = significand >>> -exponent;
        if (magnitude < 0)
            return isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
        return isNegative() ? -magnitude : magnitude;
    }

    /** Returns the value truncated toward zero, limited to the range of an int. */
    public int intValue() {
        return (int) Math.max(Math.min(longValue(), Integer.MAX_VALUE), Integer.MIN_VALUE);
    }

    /** Numeric order, with all NaN values after everything else. */
    public int compareTo(SDDSLongDouble other) {
        if (isNaN() || other.isNaN())
            return (isNaN() ? 1 : 0) - (other.isNaN() ? 1 : 0);
        if (isInfinite() || other.isInfinite())
            return Double.compare(doubleValue(), other.doubleValue());
        return toBigDecimal().compareTo(other.toBigDecimal());
    }

    public boolean equals(Object other) {
        if (!(other instanceof SDDSLongDouble))
            return false;
        SDDSLongDouble o = (SDDSLongDouble) other;
        return o.signExponent == signExponent && o.significand == significand;
    }

    public int hashCode() {
        return signExponent * 31 + Long.hashCode(significand);
    }

    /**
     * Format like C's <code>%.20Le</code>. Twenty-one significant digits identify any
     * 64-bit significand, so the text parses back to the same bits.
     */
    public String toString() {
        String sign = isNegative() ? "-" : "";
        if (isNaN())
            return sign + "nan";
        if (isInfinite())
            return sign + "inf";
        if (significand == 0)
            return sign + "0.00000000000000000000e+00";
        BigDecimal rounded = toBigDecimal().abs().round(new MathContext(DECIMAL_DIGITS, RoundingMode.HALF_EVEN));
        StringBuilder digits = new StringBuilder(rounded.unscaledValue().toString());
        int exponent = digits.length() - 1 - rounded.scale();
        while (digits.length() < DECIMAL_DIGITS)
            digits.append('0');
        digits.setLength(DECIMAL_DIGITS);
        int magnitude = Math.abs(exponent);
        return sign + digits.charAt(0) + "." + digits.substring(1) + "e" + (exponent < 0 ? "-" : "+")
                + (magnitude < 10 ? "0" : "") + magnitude;
    }
}
