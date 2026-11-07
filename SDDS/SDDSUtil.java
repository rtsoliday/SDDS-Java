/*************************************************************************\
* Copyright (c) 2002 The University of Chicago, as Operator of Argonne
* National Laboratory.
* Copyright (c) 2002 The Regents of the University of California, as
* Operator of Los Alamos National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution. 
\*************************************************************************/

package SDDS.java.SDDS;

import java.lang.*;
import java.lang.reflect.*;
import java.util.*;
//import hb.format.*;

/**
 * The SDDS Utility object is used for miscellaneous static methods. It it
 * reqired by the SDDS File, SDDS Parameter, and SDDS Column object.
 *
 * @author Robert Soliday
 *         <a href=mailto:soliday@aps.anl.gov>soliday@aps.anl.gov</a>
 * @version 1.0 (November 1, 1999)
 * @since JDK 1.2
 */
public class SDDSUtil {
	public final static int SDDS_LONGDOUBLE = 1;
	public final static int SDDS_DOUBLE = 2;
	public final static int SDDS_FLOAT = 3;
	public final static int SDDS_LONG64 = 4;
	public final static int SDDS_ULONG64 = 5;
	public final static int SDDS_LONG = 6;
	public final static int SDDS_ULONG = 7;
	public final static int SDDS_SHORT = 8;
	public final static int SDDS_USHORT = 9;
	public final static int SDDS_STRING = 10;
	public final static int SDDS_CHARACTER = 11;
	public final static int SDDS_ENUM = 12;
	public final static int SDDS_NUM_TYPES = 12;
	/** The newest SDDS protocol version this library reads and writes. */
	public final static int SDDS_VERSION = 6;
	public static String[] SDDS_type_name = { "longdouble", "double", "float", "long64", "ulong64", "long", "ulong",
			"short", "ushort", "string", "character", "enum" };

	/**
	 * Convert elements to long precision
	 * 
	 * @param array a short precision number array
	 * @return a long precision number array
	 */
	public static long[] castArrayAsLong(short[] array) {
		int i, j;
		long[] v;
		i = Array.getLength(array);
		v = new long[i];
		for (j = 0; j < i; j++)
			v[j] = (long) array[j];
		return v;
	}

	/**
	 * Convert elements to long precision. Enums are categorical and are rejected;
	 * see {@link #castArrayAsEnumCodes} to ask for their codes explicitly.
	 *
	 * @param array a numerical Object array
	 * @return a long precision number array
	 */
	public static long[] castArrayAsLong(Object[] array, int type) {
		if (!isNumericType(type)) {
			System.err.println("error: unable to create long array from Object array (SDDSUtil.castArrayAsLong)");
			return null;
		}
		long[] v = new long[array.length];
		for (int j = 0; j < array.length; j++)
			v[j] = ((Number) array[j]).longValue();
		return v;
	}

	/**
	 * Convert elements to long precision
	 * 
	 * @param array a int precision number array
	 * @return a long precision number array
	 */
	public static long[] castArrayAsLong(int[] array) {
		int i, j;
		long[] v;
		i = Array.getLength(array);
		v = new long[i];
		for (j = 0; j < i; j++)
			v[j] = (long) array[j];
		return v;
	}

	/**
	 * Convert elements to float precision. Enums are categorical and are rejected.
	 *
	 * @param array a numerical Object array
	 * @return a float precision number array
	 */
	public static float[] castArrayAsFloat(Object[] array, int type) {
		if (!isNumericType(type)) {
			System.err.println("error: unable to create float array from Object array (SDDSUtil.castArrayAsFloat)");
			return null;
		}
		float[] v = new float[array.length];
		for (int j = 0; j < array.length; j++)
			v[j] = ((Number) array[j]).floatValue();
		return v;
	}

	/**
	 * Convert elements to double precision
	 * 
	 * @param array a float precision number array
	 * @return a double precision number array
	 */
	public static double[] castArrayAsDouble(float[] array) {
		int i, j;
		double[] v;
		i = Array.getLength(array);
		v = new double[i];
		for (j = 0; j < i; j++)
			v[j] = (double) array[j];
		return v;
	}

	/**
	 * Convert elements to double precision. Enums are categorical and are rejected.
	 *
	 * @param array a numerical Object array
	 * @return a double precision number array
	 */
	public static double[] castArrayAsDouble(Object[] array, int type) {
		if (!isNumericType(type)) {
			System.err.println("error: unable to create double array from Object array (SDDSUtil.castArrayAsDouble)");
			return null;
		}
		double[] v = new double[array.length];
		for (int j = 0; j < array.length; j++)
			v[j] = ((Number) array[j]).doubleValue();
		return v;
	}

	/**
	 * Convert numerical elements to exact long doubles
	 *
	 * @param array an Object array holding numbers or SDDSLongDouble objects
	 * @return a long double array
	 */
	public static SDDSLongDouble[] castArrayAsLongDouble(Object[] array) {
		SDDSLongDouble[] v = new SDDSLongDouble[array.length];
		for (int j = 0; j < array.length; j++) {
			if (array[j] instanceof SDDSLongDouble)
				v[j] = (SDDSLongDouble) array[j];
			else if ((array[j] instanceof Long) || (array[j] instanceof Integer) || (array[j] instanceof Short))
				v[j] = SDDSLongDouble.valueOf(((Number) array[j]).longValue());
			else
				v[j] = SDDSLongDouble.valueOf(((Number) array[j]).doubleValue());
		}
		return v;
	}

	/**
	 * Explicitly get enum codes from values. This is the one cast that accepts enum
	 * values, and the codes it gives are not measurements.
	 *
	 * @param array an Object array holding SDDSEnumValue objects, integers or complete
	 *              decimal codes as strings
	 * @return the codes
	 * @throws IllegalArgumentException if an element is not a code
	 */
	public static long[] castArrayAsEnumCodes(Object[] array) {
		long[] v = new long[array.length];
		for (int j = 0; j < array.length; j++) {
			if (array[j] instanceof SDDSEnumValue) {
				v[j] = ((SDDSEnumValue) array[j]).getCode();
			} else if ((array[j] instanceof Integer) || (array[j] instanceof Long) || (array[j] instanceof Short)) {
				v[j] = ((Number) array[j]).longValue();
			} else {
				Integer code = array[j] instanceof String ? SDDSEnumDefinition.parseCode((String) array[j]) : null;
				if (code == null)
					throw new IllegalArgumentException("Invalid enum code at value " + j);
				v[j] = code.intValue();
			}
		}
		return v;
	}

	/**
	 * Convert elements to Strings
	 * 
	 * @param array a char array
	 * @return a String array
	 */
	public static String[] castArrayAsString(char[] array) {
		int i, j;
		String[] v;
		char[] c = new char[1];
		i = Array.getLength(array);
		v = new String[i];
		for (j = 0; j < i; j++) {
			c[0] = array[j];
			v[j] = new String(c);
		}
		return v;
	}

	/**
	 * Convert elements to double precision
	 * 
	 * @param array a alpha Object array
	 * @return a String array
	 */
	public static String[] castArrayAsString(Object[] array, int type) {
		int i, j;
		String[] v;
		i = Array.getLength(array);
		v = new String[i];
		switch (type) {
			case SDDS_STRING:
			case SDDS_CHARACTER:
				for (j = 0; j < i; j++) {
					v[j] = ((String) array[j]);
				}
				break;
			default:
				System.err
						.println("error: unable to create String array from Object array (SDDSUtil.castArrayAsString)");
				return null;
		}
		return v;
	}

	/**
	 * Prepare string to be written to a file
	 * 
	 * @param data string to be prepared
	 * @return a string that has special characters prefixed with a backslash
	 */
	public static String prepareString(String data) {
		int i, j;
		char c[] = { '\\', '!', '\"' };
		if (data == null)
			return null;
		for (j = 0; j < 3; j++) {
			i = data.indexOf(c[j]);
			while (i != -1) {
				data = data.substring(0, i) + "\\" + data.substring(i);
				i = data.indexOf(c[j], i + 2);
			}
		}
		i = data.indexOf('$');
		if (i != -1) {
			data = "\"" + data + "\"";
			return data;
		}
		i = data.length();
		if (i == 0)
			return "\"\"";
		for (j = 0; j < i; j++) {
			if (Character.isWhitespace(data.charAt(j))) {
				data = "\"" + data + "\"";
				break;
			}
		}
		return data;
	}

	/**
	 * Returns the text to store as a parameter's fixed value for a value as a user sees
	 * it. Numbers are stored as they are, with blank meaning zero. In strings and
	 * characters, bytes that the header reader would alter are written as octal escapes.
	 *
	 * @param value the value
	 * @param type  the parameter's data type
	 * @return text for <code>SDDSFile.defineParameter</code>
	 */
	public static String fixedValueText(String value, int type) {
		if (isNumericType(type))
			return value.trim().isEmpty() ? "0" : value;
		byte[] bytes;
		if (type == SDDS_CHARACTER)
			bytes = value.isEmpty() ? new byte[] { 0 } : new byte[] { (byte) value.charAt(0) };
		else
			bytes = SDDSText.encode(value);
		StringBuilder out = new StringBuilder();
		for (byte b : bytes) {
			int c = b & 0xff;
			if (c <= 32 || c > 126 || c == '\\' || c == '"' || c == '!' || c == '&') {
				String octal = Integer.toOctalString(c);
				out.append('\\').append("000", 0, 3 - octal.length()).append(octal);
			} else {
				out.append((char) c);
			}
		}
		return out.toString();
	}

	/**
	 * Verify that format string is valid, by the same rules as the C library. Enums
	 * take no format string, so none is valid for them.
	 *
	 * @param s    format string
	 * @param type valid data type
	 * @return <code>true</code> if valid
	 *         <code>false</code> if not valid
	 */
	public static boolean verifyPrintfFormat(String s, int type) {
		if (s == null)
			return false;
		if ((type == SDDS_STRING) && verifyStringFormat(s))
			return true;
		int percent = -1;
		int from = 0;
		while (true) {
			percent = s.indexOf('%', from);
			if ((percent < 0) || (percent + 1 >= s.length()) || (s.charAt(percent + 1) != '%'))
				break;
			from = percent + 1;
		}
		if ((percent < 0) || (percent + 1 >= s.length()))
			return false;
		String f = s.substring(percent + 1);
		int len;
		switch (type) {
			case SDDS_LONGDOUBLE:
			case SDDS_DOUBLE:
			case SDDS_FLOAT:
				len = firstOf(f, "fegEG");
				if (len == f.length())
					return false;
				return (len == 0) || (spanOf(f, "-+.0123456789 ") >= len);
			case SDDS_LONG:
			case SDDS_LONG64:
				len = firstOf(f, "d");
				if (len == f.length())
					return false;
				return (len == 1) || (spanOf(f, "-+.0123456789 ") >= len - 1);
			case SDDS_ULONG:
			case SDDS_ULONG64:
				len = firstOf(f, "u");
				if (len == f.length())
					return false;
				return (len == 1) || (spanOf(f, "-+.0123456789 ") >= len - 1);
			case SDDS_SHORT:
				len = firstOf(f, "d");
				if ((len == f.length()) || (len == 0) || (f.charAt(len - 1) != 'h'))
					return false;
				return (len == 1) || (spanOf(f, "-+.0123456789 ") >= len - 1);
			case SDDS_USHORT:
				len = firstOf(f, "u");
				if ((len == f.length()) || (len == 0) || (f.charAt(len - 1) != 'h'))
					return false;
				return (len == 1) || (spanOf(f, "-+.0123456789 ") >= len - 1);
			case SDDS_STRING:
				len = firstOf(f, "s");
				if (len == f.length())
					return false;
				return (len == 0) || (spanOf(f, "-0123456789") >= len);
			case SDDS_CHARACTER:
				return firstOf(f, "c") == 0;
			default:
				return false;
		}
	}

	private static int firstOf(String s, String characters) {
		for (int i = 0; i < s.length(); i++)
			if (characters.indexOf(s.charAt(i)) >= 0)
				return i;
		return s.length();
	}

	private static int spanOf(String s, String characters) {
		for (int i = 0; i < s.length(); i++)
			if (characters.indexOf(s.charAt(i)) < 0)
				return i;
		return s.length();
	}

	private static boolean verifyStringFormat(String format) {
		int conversions = 0;
		int i = 0, n = format.length();
		while (i < n) {
			if (format.charAt(i++) != '%')
				continue;
			if ((i < n) && (format.charAt(i) == '%')) {
				i++;
				continue;
			}
			if ((i < n) && (format.charAt(i) == '-'))
				i++;
			while ((i < n) && Character.isDigit(format.charAt(i)))
				i++;
			if ((i < n) && (format.charAt(i) == '.')) {
				i++;
				while ((i < n) && Character.isDigit(format.charAt(i)))
					i++;
			}
			if ((i >= n) || (format.charAt(i) != 's') || (conversions++ > 0))
				return false;
			i++;
		}
		return conversions == 1;
	}

	/**
	 * Determine if string is a valid name.
	 *
	 * @param name name to be tested for validity, the first
	 *             character should be an ASCII letter or <code>.:</code>
	 *             every other character of the name should be
	 *             an ASCII letter, digit, or one of the following:
	 *             <code>{@literal @}:#+%-._${@literal &}/[]</code>
	 * @return <code>true</code> if valid<br>
	 *         <code>false</code> if invalid<br>
	 */
	public static boolean isValidName(String name) {
		char c;
		int i, j;
		String validChars = "@:#+%-._$&/[]";
		String startChars = ".:";
		if (name == null)
			return false;
		i = name.length();
		if (i == 0)
			return false;
		c = name.charAt(0);
		if (!isAsciiLetter(c)) {
			if (startChars.indexOf(c) == -1)
				return false;
		}
		for (j = 1; j < i; j++) {
			c = name.charAt(j);
			if (!isAsciiLetter(c) && ((c < '0') || (c > '9'))) {
				if (validChars.indexOf(c) == -1)
					return false;
			}
		}
		return true;
	}

	private static boolean isAsciiLetter(char c) {
		return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
	}

	/**
	 * Determine if data type is numeric. Enums are categorical and are not.
	 *
	 * @param type data type
	 * @return <code>true</code> for integer and floating point types
	 */
	public static boolean isNumericType(int type) {
		return (type >= SDDS_LONGDOUBLE) && (type <= SDDS_USHORT);
	}

	/**
	 * Determine if data type is a valid type.
	 *
	 * @param type valid values are:
	 *             <ul>
	 *             <li><code>1</code> for longdouble</li>
	 *             <li><code>2</code> for double</li>
	 *             <li><code>3</code> for float</li>
	 *             <li><code>4</code> for long64</li>
	 *             <li><code>5</code> for ulong64</li>
	 *             <li><code>6</code> for long</li>
	 *             <li><code>7</code> for ulong</li>
	 *             <li><code>8</code> for short</li>
	 *             <li><code>9</code> for ushort</li>
	 *             <li><code>10</code> for string</li>
	 *             <li><code>11</code> for character</li>
	 *             <li><code>12</code> for enum</li>
	 *             </ul>
	 * @return <code>true</code> if valid<br>
	 *         <code>false</code> if invalid<br>
	 */
	public static boolean isValidType(int type) {
		if ((type >= 1) && (type <= SDDS_NUM_TYPES)) {
			return true;
		}
		return false;
	}

	/**
	 * Get data type name from integer data type value.
	 *
	 * @param type valid values are:
	 *             <ul>
	 *             <li><code>1</code> for longdouble</li>
	 *             <li><code>2</code> for double</li>
	 *             <li><code>3</code> for float</li>
	 *             <li><code>4</code> for long64</li>
	 *             <li><code>5</code> for ulong64</li>
	 *             <li><code>6</code> for long</li>
	 *             <li><code>7</code> for ulong</li>
	 *             <li><code>8</code> for short</li>
	 *             <li><code>9</code> for ushort</li>
	 *             <li><code>10</code> for string</li>
	 *             <li><code>11</code> for character</li>
	 *             <li><code>12</code> for enum</li>
	 *             </ul>
	 * @return type name if valid<br>
	 *         <code>null</code> if invalid<br>
	 */
	public static String getTypeName(int type) {
		if (!isValidType(type))
			return null;
		return SDDS_type_name[type - 1];
	}

	/**
	 * Get integer data type value from data type name, ignoring case.
	 *
	 * @param typeName valid values are:
	 *                 <ul>
	 *                 <li><code>"longdouble"</code></li>
	 *                 <li><code>"double"</code></li>
	 *                 <li><code>"float"</code></li>
	 *                 <li><code>"long64"</code></li>
	 *                 <li><code>"ulong64"</code></li>
	 *                 <li><code>"long"</code></li>
	 *                 <li><code>"ulong"</code></li>
	 *                 <li><code>"short"</code></li>
	 *                 <li><code>"ushort"</code></li>
	 *                 <li><code>"string"</code></li>
	 *                 <li><code>"character"</code></li>
	 *                 <li><code>"enum"</code></li>
	 *                 </ul>
	 * @return <code>int</code> type if valid<br>
	 *         <code>0</code> if invalid<br>
	 */
	public static int identifyType(String typeName) {
		return typeName == null ? 0 : identifyTypeExactly(typeName.toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * Get integer data type value from a data type name as spelled in a file header,
	 * which is case sensitive.
	 *
	 * @param typeName type name
	 * @return <code>int</code> type if valid<br>
	 *         <code>0</code> if invalid<br>
	 */
	public static int identifyTypeExactly(String typeName) {
		for (int i = 0; i < SDDS_NUM_TYPES; i++) {
			if (SDDS_type_name[i].equals(typeName))
				return i + 1;
		}
		return 0;
	}

	/**
	 * Resize array while keeping existing elements
	 *
	 * @param array     array to be resized
	 * @param offset    offset index
	 * @param newLength new length of array
	 * @return new array of different size
	 */
	public static Object resize(Object array, int offset, int newLength) {
		Class c = array.getClass();
		if (!c.isArray()) {
			throw new IllegalArgumentException("Unable to resize because variable is not an array");
		}
		Object newArray = Array.newInstance(c.getComponentType(), newLength);
		int oldLength = Array.getLength(array);
		System.arraycopy(array, offset, newArray, 0, Math.min(oldLength - offset, newLength));
		return newArray;
	}

	/**
	 * Resize array while keeping existing elements
	 *
	 * @param array     array to be resized
	 * @param newLength new length of array
	 * @return new array of different size
	 */
	public static Object resize(Object array, int newLength) {
		return resize(array, 0, newLength);
	}

	/**
	 * Compare two strings up to a given length
	 *
	 * @param s1     first string to compare
	 * @param s2     second string to compare
	 * @param length length to check strings
	 */
	public static boolean strncmp(String s1, String s2, int length) {
		if ((s1 == null) || (s2 == null))
			return false;
		if (s1.length() < length)
			return false;
		return s2.equals(s1.substring(0, length));
	}

	/**
	 * Return the substring from the index to the end of the string
	 *
	 * @param s1    initial string
	 * @param index index of string
	 */
	public static String strchop(String s1, int index) {
		int end;
		if (s1 == null)
			return "";
		end = s1.length();
		if (end <= index)
			return "";
		return s1.substring(index, end);
	}

	/**
	 * Search for \ and ! in the string
	 *
	 * @param s1 string to remove comments
	 */
	public static String removeComments(String s1) {
		int i, j, end;
		char[] s;
		boolean insideString = false;
		if (s1 == null)
			return null;
		end = s1.length();
		s = new char[end];
		s1.getChars(0, end, s, 0);
		i = 0;
		while (i < end) {
			if (s[i] == '\\') {
				for (j = i + 1; j < end; j++)
					s[j - 1] = s[j];
				end--;
			} else if (s[i] == '"') {
				insideString = !insideString;
			} else if ((s[i] == '!') && (!insideString)) {
				return new String(s, 0, i);
			}
			i++;
		}
		return new String(s, 0, end);
	}

	/**
	 * Remove backslashes from the string
	 *
	 * @param s1 string to remove backslashes from
	 */
	public static String removeBackSlashes(String s1) {
		int i, j, end;
		char[] s;
		if (s1 == null)
			return null;
		end = s1.length();
		s = new char[end];
		s1.getChars(0, end, s, 0);
		i = 0;
		while (i < end) {
			if (s[i] == '\\') {
				for (j = i + 1; j < end; j++)
					s[j - 1] = s[j];
				end--;
			}
			i++;
		}
		return new String(s, 0, end);
	}

	/**
	 * Return character from octal code
	 *
	 * @param s1 string with octal code
	 */
	public static char returnCharValueFromOctalCode(String s1) {
		Integer n;
		if (s1 == null)
			return '0';
		if (s1.length() == 0)
			return '0';
		if (s1.charAt(0) != '\\')
			return s1.charAt(0);
		try {
			n = Integer.valueOf(s1.substring(1, 4), 8);
		} catch (Exception e) {
			return s1.charAt(0);
		}
		return (char) n.intValue();
	}

	/**
	 * Parse the command line into a string array
	 *
	 * @param args command line arguments
	 * @return two dimensional string array
	 */
	public static String[][] parseCommandLine(String[] args) {
		StringTokenizer st;
		int i, j, k, length;
		String[][] results;
		i = Array.getLength(args);
		results = new String[i][];
		for (j = 0; j < i; j++) {
			st = new StringTokenizer(args[j], "=,");
			length = st.countTokens();
			results[j] = new String[length];
			for (k = 0; k < length; k++) {
				results[j][k] = st.nextToken();
			}
		}
		return results;
	}

	/**
	 * Compare two strings to see if one starts with the other
	 * 
	 * @param value1 compare beginning with value2
	 * @param value2 compare entire string with beginning of value1
	 */
	public static boolean compare(String value1, String value2) {
		return (value1.regionMatches(false, 0, value2, 0, value1.length()));
	}

	/**
	 * Do a glob style string match
	 *
	 * @param s1      String to test for glob style matching
	 * @param pattern Pattern used for glob style matching
	 * 
	 * @return <code>true</code> if it does match<br>
	 *         <code>false</code> if it does not match<br>
	 */
	public static boolean globMatch(String s1, String pattern) {
		int i1, i2, index, indexStart, indexEnd;
		String temp, substring;
		boolean beginning = true;
		i1 = s1.length();
		i2 = pattern.length();
		if ((i1 == 0) && (i2 == 0))
			return true;
		if ((i1 == 0) || (i2 == 0))
			return false;
		index = 0;
		indexStart = 0;
		indexEnd = pattern.indexOf('*', indexStart);
		temp = s1;
		while (indexEnd != -1) {
			substring = pattern.substring(indexStart, indexEnd);
			if (substring.length() != 0) {
				index = temp.indexOf(substring);
				if ((index == -1) || (beginning && (index != 0)))
					return false;
				temp = temp.substring(index + substring.length(), temp.length());
			}
			beginning = false;
			indexStart = indexEnd + 1;
			indexEnd = pattern.indexOf('*', indexStart);
		}
		substring = pattern.substring(indexStart, pattern.length());
		if (substring.length() != 0) {
			index = temp.indexOf(substring);
			if ((index == -1) || (beginning && (index != 0)))
				return false;
			if (index + substring.length() != temp.length())
				return false;
		}
		return true;
	}
}
