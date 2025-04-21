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
	public final static int SDDS_NUM_TYPES = 11;
	public static String[] SDDS_type_name = new String[11];

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
	 * Convert elements to float precision
	 * 
	 * @param array a numerical Object array
	 * @return a long precision number array
	 */
	public static long[] castArrayAsLong(Object[] array, int type) {
		int i, j;
		long[] v;
		i = Array.getLength(array);
		v = new long[i];
		switch (type) {
			case SDDS_SHORT:
				for (j = 0; j < i; j++) {
					v[j] = (long) (((Short) array[j]).shortValue());
				}
				break;
			case SDDS_USHORT:
				for (j = 0; j < i; j++) {
					v[j] = (long) (((Integer) array[j]).intValue());
				}
				break;
			case SDDS_LONG:
				for (j = 0; j < i; j++) {
					v[j] = ((Long) array[j]).longValue();
				}
				break;
			case SDDS_ULONG:
				for (j = 0; j < i; j++) {
					v[j] = ((Long) array[j]).longValue();
				}
				break;
			case SDDS_LONG64:
				for (j = 0; j < i; j++) {
					v[j] = ((Long) array[j]).longValue();
				}
				break;
			case SDDS_ULONG64:
				/*
				 * ULong type does not exists in java. This could result in invalid data for
				 * huge numbers
				 */
				for (j = 0; j < i; j++) {
					v[j] = ((Long) array[j]).longValue();
				}
				break;
			case SDDS_FLOAT:
				for (j = 0; j < i; j++) {
					v[j] = (long) (((Float) array[j]).floatValue());
				}
				break;
			case SDDS_DOUBLE:
				for (j = 0; j < i; j++) {
					v[j] = (long) (((Double) array[j]).doubleValue());
				}
				break;
			case SDDS_LONGDOUBLE:
				System.err.println("error: longdouble not supported in SDDSJava (SDDSUtil.castArrayAsLong)");
				return null;
			default:
				System.err.println("error: unable to create float array from Object array (SDDSUtil.castArrayAsLong)");
				return null;
		}
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
	 * Convert elements to float precision
	 * 
	 * @param array a numerical Object array
	 * @return a float precision number array
	 */
	public static float[] castArrayAsFloat(Object[] array, int type) {
		int i, j;
		float[] v;
		i = Array.getLength(array);
		v = new float[i];
		switch (type) {
			case SDDS_SHORT:
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Short) array[j]).shortValue());
				}
				break;
			case SDDS_USHORT:
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Integer) array[j]).intValue());
				}
				break;
			case SDDS_LONG:
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_ULONG:
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_LONG64:
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_ULONG64:
				/*
				 * ULong type does not exists in java. This could result in invalid data for
				 * huge numbers
				 */
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_FLOAT:
				for (j = 0; j < i; j++) {
					v[j] = ((Float) array[j]).floatValue();
				}
				break;
			case SDDS_DOUBLE:
				for (j = 0; j < i; j++) {
					v[j] = (float) (((Double) array[j]).doubleValue());
				}
				break;
			case SDDS_LONGDOUBLE:
				System.err.println("error: longdouble not supported in SDDSJava (SDDSUtil.castArrayAsFloat)");
				return null;
			default:
				System.err.println("error: unable to create float array from Object array (SDDSUtil.castArrayAsFloat)");
				return null;
		}
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
	 * Convert elements to double precision
	 * 
	 * @param array a numerical Object array
	 * @return a double precision number array
	 */
	public static double[] castArrayAsDouble(Object[] array, int type) {
		int i, j;
		double[] v;
		i = Array.getLength(array);
		v = new double[i];
		switch (type) {
			case SDDS_SHORT:
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Short) array[j]).shortValue());
				}
				break;
			case SDDS_USHORT:
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Integer) array[j]).intValue());
				}
				break;
			case SDDS_LONG:
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_ULONG:
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_LONG64:
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_ULONG64:
				/*
				 * ULong type does not exists in java. This could result in invalid data for
				 * huge numbers
				 */
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Long) array[j]).longValue());
				}
				break;
			case SDDS_FLOAT:
				for (j = 0; j < i; j++) {
					v[j] = (double) (((Float) array[j]).floatValue());
				}
				break;
			case SDDS_DOUBLE:
				for (j = 0; j < i; j++) {
					v[j] = ((Double) array[j]).doubleValue();
				}
				break;
			case SDDS_LONGDOUBLE:
				System.err.println("error: longdouble not supported in SDDSJava (SDDSUtil.castArrayAsDouble)");
				return null;
			default:
				System.err
						.println("error: unable to create double array from Object array (SDDSUtil.castArrayAsDouble)");
				return null;
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
	 * Verify that format string is valid.
	 * 
	 * @param s    format string
	 * @param type valid data type
	 * @return <code>true</code> if valid
	 *         <code>false</code> if not valid
	 */
	public static boolean verifyPrintfFormat(String s, int type) {
		/*
		 * Parameters p = new Parameters();
		 * try {
		 * switch(type) {
		 * case SDDS_SHORT:
		 * case SDDS_LONG:
		 * case SDDS_FLOAT:
		 * case SDDS_DOUBLE:
		 * if (Format.sprintf(s, p.add(1)) == null)
		 * return false;
		 * break;
		 * case SDDS_STRING:
		 * if (Format.sprintf(s, p.add("test")) == null)
		 * return false;
		 * break;
		 * case SDDS_CHARACTER:
		 * if (Format.sprintf(s, p.add('t')) == null)
		 * return false;
		 * break;
		 * default:
		 * return false;
		 * }
		 * return true;
		 * } catch (Exception e) {
		 * return false;
		 * }
		 */
		return true;
	}

	/**
	 * Determine if string is a valid name.
	 *
	 * @param name name to be tested for validity, the first
	 *             character should be a letter or <code>.:</code>
	 *             every other character of the name should be
	 *             a letter, digit, or one of the following:
	 *             <code>{@literal @}:#+%-._${@literal &}/[]</code>
	 * @return <code>true</code> if valid<br>
	 *         <code>false</code> if invalid<br>
	 */
	public static boolean isValidName(String name) {
		char c;
		int i, j;
		String validChars = "@:#+%-._$&/[]";
		String startChars = ".:";
		i = name.length();
		if (i == 0)
			return false;
		c = name.charAt(0);
		if (!Character.isLetter(c)) {
			if (startChars.indexOf(c) == -1)
				return false;
		}
		for (j = 1; j < i; j++) {
			c = name.charAt(j);
			if (!Character.isLetterOrDigit(c)) {
				if (validChars.indexOf(c) == -1)
					return false;
			}
		}
		return true;
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
	 *             </ul>
	 * @return type name if valid<br>
	 *         <code>null</code> if invalid<br>
	 */
	public static String getTypeName(int type) {
		SDDS_type_name[0] = "longdouble";
		SDDS_type_name[1] = "double";
		SDDS_type_name[2] = "float";
		SDDS_type_name[3] = "long64";
		SDDS_type_name[4] = "ulong64";
		SDDS_type_name[5] = "long";
		SDDS_type_name[6] = "ulong";
		SDDS_type_name[7] = "short";
		SDDS_type_name[8] = "ushort";
		SDDS_type_name[9] = "string";
		SDDS_type_name[10] = "character";

		if (!isValidType(type))
			return null;
		return SDDS_type_name[type - 1];
	}

	/**
	 * Get integer data type value from data type name.
	 *
	 * @param typeName valid values are:
	 *                 <ul>
	 *                 <li><code>"longdouble"</code></li>
	 *                 <li><code>"double"</code></li>
	 *                 <li><code>"float"</code></li>
	 *                 <li><code>"long64"</code></li>
	 *                 <li><code>"ulong6"</code></li>
	 *                 <li><code>"long"</code></li>
	 *                 <li><code>"ulong"</code></li>
	 *                 <li><code>"short"</code></li>
	 *                 <li><code>"ushort"</code></li>
	 *                 <li><code>"string"</code></li>
	 *                 <li><code>"character"</code></li>
	 *                 </ul>
	 * @return <code>int</code> type if valid<br>
	 *         <code>0</code> if invalid<br>
	 */
	public static int identifyType(String typeName) {
		int i;
		SDDS_type_name[0] = "longdouble";
		SDDS_type_name[1] = "double";
		SDDS_type_name[2] = "float";
		SDDS_type_name[3] = "long64";
		SDDS_type_name[4] = "ulong64";
		SDDS_type_name[5] = "long";
		SDDS_type_name[6] = "ulong";
		SDDS_type_name[7] = "short";
		SDDS_type_name[8] = "ushort";
		SDDS_type_name[9] = "string";
		SDDS_type_name[10] = "character";
		for (i = 0; 1 < SDDS_NUM_TYPES; i++) {
			if (typeName.toLowerCase().equals(SDDS_type_name[i]))
				return i + 1;
		}
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
