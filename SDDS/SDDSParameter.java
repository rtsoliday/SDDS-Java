/*************************************************************************\
* Copyright (c) 2002 The University of Chicago, as Operator of Argonne
* National Laboratory.
* Copyright (c) 2002 The Regents of the University of California, as
* Operator of Los Alamos National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution. 
\*************************************************************************/

package SDDS.java.SDDS;

import java.io.*;
import java.lang.reflect.*;

/**
 * The SDDS Parameter object is used by the SDDS File object.
 * It was not the intention of the author to use this object
 * directly but it is still supported. After initializing the
 * SDDS Parameter the setupParameter method must be used to
 * give the parameter a name and a valid data type (long, short,
 * float, double, string, character). The setValue method is
 * used next to give the parameter values for different
 * pages. The writeValue method can be used to print
 * the value for a particular page.
 *
 * @author Robert Soliday
 *         <a href=mailto:soliday@aps.anl.gov>soliday@aps.anl.gov</a>
 * @version 1.0 (November 1, 1999)
 * @since JDK 1.2
 */
public class SDDSParameter {
	String parameterName;
	String parameterSymbol;
	String parameterUnits;
	String parameterDescription;
	String parameterFormatString;
	int parameterType;
	String parameterFixedValue;
	/** One value per page, in a storage array; see SDDSValues. */
	Object values;
	SDDSEnumDefinition enumDefinition;
	int pageCount;
	final int SDDS_LONGDOUBLE = 1;
	final int SDDS_DOUBLE = 2;
	final int SDDS_FLOAT = 3;
	final int SDDS_LONG64 = 4;
	final int SDDS_ULONG64 = 5;
	final int SDDS_LONG = 6;
	final int SDDS_ULONG = 7;
	final int SDDS_SHORT = 8;
	final int SDDS_USHORT = 9;
	final int SDDS_STRING = 10;
	final int SDDS_CHARACTER = 11;
	final int SDDS_ENUM = 12;
	boolean fixed;

	/**
	 * Create a new SDDS parameter that can be used to store parameter information
	 * for SDDS files.
	 */
	public SDDSParameter() {
		pageCount = 0;
		parameterType = 0;
		fixed = false;
	}

	/**
	 * Set up the parameter information.
	 *
	 * @param name          should be a valid name as defined by
	 *                      SDDSUtil.isValidName
	 * @param symbol        may be <code>null</code>
	 * @param units         may be <code>null</code>
	 * @param description   may be <code>null</code>
	 * @param format_string may be <code>null</code>
	 * @param type          valid values are:
	 *                      <ul>
	 *                      <li><code>1</code> for longdouble</li>
	 *                      <li><code>2</code> for double</li>
	 *                      <li><code>3</code> for float</li>
	 *                      <li><code>4</code> for long64</li>
	 *                      <li><code>5</code> for ulong64</li>
	 *                      <li><code>6</code> for long</li>
	 *                      <li><code>7</code> for ulong</li>
	 *                      <li><code>8</code> for short</li>
	 *                      <li><code>9</code> for ushort</li>
	 *                      <li><code>10</code> for string</li>
	 *                      <li><code>11</code> for character</li>
	 *                      <li><code>12</code> for enum</li>
	 *                      </ul>
	 * @param fixed_value   may be <code>null</code>
	 */
	public void setupParameter(String name, String symbol, String units, String description, String format_string,
			int type, String fixed_value) {
		parameterName = name;
		parameterSymbol = symbol;
		parameterUnits = units;
		parameterDescription = description;
		parameterFormatString = format_string;
		parameterType = type;
		parameterFixedValue = fixed_value;
		if (fixed_value == null)
			fixed = false;
		else
			fixed = true;
	}

	/**
	 * Returns the name of the parameter
	 *
	 * @return The <code>String</code> value of the parameter name.
	 */
	public String getName() {
		return parameterName;
	}

	/**
	 * Set the parameter name
	 *
	 * @param name name of the parameter
	 */
	public void setName(String name) {
		parameterName = name;
	}

	/**
	 * Returns the symbol of the parameter
	 *
	 * @return The <code>String</code> value of the parameter symbol.
	 */
	public String getSymbol() {
		return parameterSymbol;
	}

	/**
	 * Set the parameter symbol
	 *
	 * @param symbol symbol of the parameter
	 */
	public void setSymbol(String symbol) {
		parameterSymbol = symbol;
	}

	/**
	 * Returns the units of the parameter
	 *
	 * @return The <code>String</code> value of the parameter units.
	 */
	public String getUnits() {
		return parameterUnits;
	}

	/**
	 * Set the parameter units
	 *
	 * @param units units of the parameter
	 */
	public void setUnits(String units) {
		parameterUnits = units;
	}

	/**
	 * Returns the description of the parameter
	 *
	 * @return The <code>String</code> value of the parameter description.
	 */
	public String getDescription() {
		return parameterDescription;
	}

	/**
	 * Set the parameter description
	 *
	 * @param description description of the parameter
	 */
	public void setDescription(String description) {
		parameterDescription = description;
	}

	/**
	 * Returns the format string of the parameter
	 *
	 * @return The <code>String</code> value of the parameter format string.
	 */
	public String getFormat_String() {
		return parameterFormatString;
	}

	/**
	 * Set the parameter format string
	 *
	 * @param format_string format string of the parameter
	 */
	public void setFormat_String(String format_string) {
		if ((parameterType == SDDS_ENUM) && (SDDSNamelist.blankToNull(format_string) != null))
			throw new IllegalArgumentException("Enum parameters take no format string");
		parameterFormatString = format_string;
	}

	/**
	 * Returns the type of the parameter
	 *
	 * @return The <code>int</code> value of the parameter type.
	 */
	public int getType() {
		return parameterType;
	}

		/**
	 * Returns the fixed value of the parameter
	 *
	 * @return The <code>String</code> value of the parameter fixed value.
	 */
	public String getFixed_Value() {
		return parameterFixedValue;
	}

	/**
	 * Set the fixed value of the parameter. The fixed value of an enum parameter is a
	 * complete decimal code from its dictionary.
	 *
	 * @param fixed_value fixed_value of the parameter
	 * @throws IllegalArgumentException if this is an enum parameter and the value is
	 *                                  not a mapped code
	 */
	public void setFixed_Value(String fixed_value) {
		if ((parameterType == SDDS_ENUM) && (fixed_value != null))
			checkFixedEnum(enumDefinition, fixed_value);
		parameterFixedValue = fixed_value;
		fixed = fixed_value != null;
		if (!fixed && SDDSUtil.isValidType(parameterType))
			grow(Math.max(pageCount, 1));
	}

	private static void checkFixedEnum(SDDSEnumDefinition definition, String text) {
		Integer code = SDDSEnumDefinition.parseCode(text);
		if ((code == null) || (definition == null) || !definition.hasCode(code.intValue()))
			throw new IllegalArgumentException("Invalid fixed enum parameter code");
	}

	/**
	 * Returns the number of pages that the parameter contains
	 *
	 * @return The <code>int</code> value of the number of pages the parameter
	 *         contains.
	 */
	public int getPageCount() {
		return pageCount;
	}

	/**
	 * Returns the dictionary of an enum parameter
	 *
	 * @return the dictionary, or <code>null</code> if this parameter is not an enum
	 */
	public SDDSEnumDefinition getEnumDefinition() {
		return enumDefinition;
	}

	/**
	 * Returns the canonical mapping text of an enum parameter
	 *
	 * @return the text of the <code>enum_values</code> attribute, or
	 *         <code>null</code> if this parameter is not an enum
	 */
	public String getEnumValues() {
		return enumDefinition == null ? null : enumDefinition.getValues();
	}

	/**
	 * Give a new enum parameter its dictionary. A dictionary cannot be replaced this
	 * way; use {@link #convert} for that.
	 *
	 * @param definition the dictionary
	 * @throws IllegalArgumentException if this is not a new enum parameter, or if its
	 *                                  fixed value is not a mapped code
	 */
	public void setEnumDefinition(SDDSEnumDefinition definition) {
		if ((parameterType != SDDS_ENUM) || (enumDefinition != null) || (definition == null))
			throw new IllegalArgumentException("A dictionary can only be given to a new enum parameter");
		if (parameterFixedValue != null)
			checkFixedEnum(definition, parameterFixedValue);
		enumDefinition = definition;
	}

	private void grow(int count) {
		if (values == null)
			values = SDDSValues.newArray(parameterType, count);
		else if (Array.getLength(values) < count)
			values = SDDSValues.copyOf(values, count);
	}

	/**
	 * Inserts a blank page at the specified index
	 *
	 * @param page index of page to create insert
	 * @return <code>true</code> on success<br>
	 *         <code>false</code> on error.
	 */
	public boolean insertPage(int page) {
		if (page == pageCount + 1) {
			makeBlankPages(page);
			return true;
		} else if ((page < 1) || (page > pageCount + 1)) {
			return false;
		}
		if (parameterFixedValue != null) {
			pageCount++;
			return true;
		}
		grow(pageCount + 1);
		System.arraycopy(values, page - 1, values, page, pageCount - page + 1);
		if (parameterType == SDDS_STRING)
			((String[]) values)[page - 1] = "";
		else if (parameterType == SDDS_CHARACTER)
			((char[]) values)[page - 1] = '0';
		else if (parameterType == SDDS_LONGDOUBLE)
			((SDDSLongDouble[]) values)[page - 1] = null;
		else if (values instanceof long[])
			((long[]) values)[page - 1] = 0;
		else
			((double[]) values)[page - 1] = 0;
		pageCount++;
		return true;
	}

	/**
	 * Delete a page at the specified index
	 *
	 * @param page index of page to delete
	 * @return <code>true</code> on success<br>
	 *         <code>false</code> on error.
	 */
	public boolean deletePage(int page) {
		if ((page < 1) || (page > pageCount))
			return false;
		if (parameterFixedValue != null) {
			pageCount--;
			return true;
		}
		if ((values != null) && (Array.getLength(values) >= pageCount)) {
			System.arraycopy(values, page, values, page - 1, pageCount - page);
			values = SDDSValues.copyOf(values, pageCount - 1);
		}
		pageCount--;
		return true;
	}

	/**
	 * Create blank pages up to the last page specified
	 *
	 * @param lastpage last page to create blank page
	 */
	public void makeBlankPages(int lastpage) {
		if ((lastpage > pageCount) && SDDSUtil.isValidType(parameterType)) {
			if (parameterFixedValue == null)
				grow(lastpage);
			pageCount = lastpage;
		}
	}

	/**
	 * Store one value in a page.
	 *
	 * @param data values in storage form, the first of which is stored
	 * @param page the page of the parameter, must be greater then <code>0</code>
	 */
	void store(Object data, int page) {
		if ((page < 1) || !SDDSUtil.isValidType(parameterType))
			return;
		if (parameterFixedValue == null) {
			grow(page);
			System.arraycopy(data, 0, values, page - 1, 1);
		}
		if (page > pageCount)
			pageCount = page;
	}

	/**
	 * Set the value of the parameter at a chosen page
	 *
	 * @param value a long precision number. For a ulong64 parameter this is the raw
	 *              64 bits. For an enum parameter this is a code.
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a code is not mapped by an enum parameter
	 */
	public void setValue(long value, int page) {
		if (!SDDSUtil.isValidType(parameterType))
			return;
		if (parameterFixedValue == null)
			store(SDDSValues.fromLongs(parameterType, new long[] { value }, enumDefinition), page);
		else
			store(null, page);
	}

	/**
	 * Set the value of the parameter at a chosen page
	 *
	 * @param value a double precision floating-point number
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if this is an enum parameter
	 */
	public void setValue(double value, int page) {
		if (!SDDSUtil.isValidType(parameterType))
			return;
		if (parameterFixedValue == null)
			store(SDDSValues.fromDoubles(parameterType, new double[] { value }), page);
		else
			store(null, page);
	}

	/**
	 * Set the value of the parameter at a chosen page
	 *
	 * @param value a string value. For an enum parameter this is a decimal code.
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if the value is not a code mapped by an enum
	 *                                  parameter
	 */
	public void setValue(String value, int page) {
		if (!SDDSUtil.isValidType(parameterType))
			return;
		if (parameterFixedValue == null)
			store(SDDSValues.fromStrings(parameterType, new String[] { value }, enumDefinition), page);
		else
			store(null, page);
	}

	/**
	 * Set the value of a longdouble parameter at a chosen page, without any loss of
	 * precision
	 *
	 * @param value a long double
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if this is not a longdouble parameter
	 */
	public void setValue(SDDSLongDouble value, int page) {
		if (parameterType != SDDS_LONGDOUBLE)
			throw new IllegalArgumentException("Parameter " + parameterName + " is not a longdouble parameter");
		store(new SDDSLongDouble[] { value }, page);
	}

	/**
	 * Set the label of an enum parameter at a chosen page
	 *
	 * @param label a label from the dictionary
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if the label is not in the dictionary
	 */
	public void setLabel(String label, int page) {
		if (enumDefinition == null)
			throw new IllegalArgumentException("Parameter " + parameterName + " is not an enum parameter");
		store(SDDSValues.convert(SDDS_STRING, null, SDDS_ENUM, enumDefinition, new String[] { label }, 1), page);
	}

	/**
	 * Set an enum value that may not have been chosen yet, as an interactive program
	 * needs to. A chosen code is checked. A <code>null</code> is stored as code 0, with
	 * no dictionary check, so the file cannot be written until it has a mapped code.
	 *
	 * @param code the code, or <code>null</code> for a value not chosen yet
	 * @param page the page of the parameter, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if the code is not mapped
	 */
	public void setEnumCell(Integer code, int page) {
		if (enumDefinition == null)
			throw new IllegalArgumentException("Parameter " + parameterName + " is not an enum parameter");
		store(SDDSValues.enumCells(enumDefinition, new Integer[] { code }), page);
	}

	/**
	 * Returns the code of an enum parameter on a chosen page
	 *
	 * @param page the page of the parameter, must be greater then <code>0</code>
	 * @return the code, or <code>null</code> if this is not an enum parameter or the
	 *         page does not exist
	 */
	public Integer getEnumCode(int page) {
		if (parameterType != SDDS_ENUM)
			return null;
		if (parameterFixedValue != null)
			return SDDSEnumDefinition.parseCode(parameterFixedValue);
		if ((page < 1) || (page > pageCount) || (values == null))
			return null;
		return Integer.valueOf((int) ((long[]) values)[page - 1]);
	}

	/**
	 * Returns the label of an enum parameter on a chosen page
	 *
	 * @param page the page of the parameter, must be greater then <code>0</code>
	 * @return the label, or <code>null</code> if there is no mapped value
	 */
	public String getEnumLabel(int page) {
		Integer code = getEnumCode(page);
		return code == null ? null : enumDefinition.codeToLabel(code.intValue());
	}

	/**
	 * Check that an enum parameter can be written for a page
	 *
	 * @param page the page of the parameter
	 * @return <code>true</code> if this is not an enum parameter or the value is
	 *         mapped
	 */
	boolean isValidEnumPage(int page) {
		if (parameterType != SDDS_ENUM)
			return true;
		if (enumDefinition == null)
			return false;
		if ((page > pageCount) && (parameterFixedValue == null))
			return true;
		return getEnumLabel(page) != null;
	}

	/**
	 * Convert this parameter in place between enum, long and string. Every value is
	 * checked before any is changed.
	 *
	 * @param targetType <code>SDDS_ENUM</code>, <code>SDDS_LONG</code> or
	 *                   <code>SDDS_STRING</code>
	 * @param target     dictionary for an enum target, otherwise <code>null</code>
	 * @throws IllegalArgumentException if the conversion is not allowed or a value
	 *                                  cannot be converted
	 */
	public void convert(int targetType, SDDSEnumDefinition target) {
		if (!SDDSValues.isConversion(parameterType, targetType) || ((targetType == SDDS_ENUM) && (target == null)))
			throw new IllegalArgumentException("Invalid enum conversion for parameter " + parameterName);
		String newFixed = null;
		Object converted = null;
		if (parameterFixedValue != null) {
			newFixed = convertFixed(targetType, target);
		} else if (values != null) {
			converted = SDDSValues.convert(parameterType, enumDefinition, targetType, target, values, pageCount);
		}
		values = converted;
		parameterFixedValue = newFixed;
		parameterType = targetType;
		enumDefinition = target;
		parameterFormatString = null;
	}

	/** Returns the fixed value in the form the target type needs. */
	private String convertFixed(int targetType, SDDSEnumDefinition target) {
		String label = null;
		int code = 0;
		if (parameterType == SDDS_ENUM) {
			Integer parsed = SDDSEnumDefinition.parseCode(parameterFixedValue);
			label = parsed == null ? null : enumDefinition.codeToLabel(parsed.intValue());
			if (label == null)
				throw new IllegalArgumentException("Invalid fixed enum parameter code");
			code = parsed.intValue();
		} else if (parameterType == SDDS_STRING) {
			label = (String) getValue(1, false);
		} else {
			Integer parsed = SDDSEnumDefinition.parseCode(parameterFixedValue);
			if (parsed == null)
				throw new IllegalArgumentException("Invalid fixed enum parameter code");
			code = parsed.intValue();
		}
		if (targetType == SDDS_ENUM) {
			if (parameterType != SDDS_LONG) {
				Integer mapped = target.labelToCode(label);
				if (mapped == null)
					throw new IllegalArgumentException("Enum conversion requires every label in the target mapping");
				code = mapped.intValue();
			} else if (!target.hasCode(code)) {
				throw new IllegalArgumentException("Invalid fixed enum parameter code");
			}
			return Integer.toString(code);
		}
		if (targetType == SDDS_LONG)
			return Integer.toString(code);
		String escaped = escapeLegacyFixedString(label);
		/* The legacy reader accepts a definition line of up to 1024 bytes including its newline. */
		if (legacyHeader(escaped, "string").length() > 1024)
			throw new IllegalArgumentException(
					"Fixed enum label is too long for a string fixed value in an SDDS1-5 header; make the parameter page data first");
		return escaped;
	}

	/**
	 * Escape a label as a legacy string fixed value. Only bytes that the legacy header
	 * reader would alter are written as octal escapes.
	 */
	private static String escapeLegacyFixedString(String label) {
		String raw = SDDSText.toRaw(label);
		int first = 0, last = raw.length();
		while ((first < last) && (raw.charAt(first) == ' '))
			first++;
		while ((last > first) && (raw.charAt(last - 1) == ' '))
			last--;
		StringBuilder out = new StringBuilder(raw.length() + 8);
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if ((c < 0x20) || (c == 0x7f) || (c == '\\') || (c == '"') || (c == '!') || (c == '&')
					|| ((c == ' ') && ((i < first) || (i >= last)))) {
				String octal = Integer.toOctalString(c);
				out.append('\\').append("000", 0, 3 - octal.length()).append(octal);
			} else {
				out.append(c);
			}
		}
		return SDDSText.fromRaw(out.toString());
	}

	/**
	 * Copy the values of another parameter of the same type
	 *
	 * @param source parameter to copy from
	 */
	void copyValues(SDDSParameter source) {
		pageCount = source.pageCount;
		values = source.values == null ? null : SDDSValues.copyOf(source.values, Array.getLength(source.values));
	}

	/**
	 * Write the header to a file using the format of SDDS protocol 1 to 5.
	 *
	 * @param dos a valid DataOutputStream
	 * @return <code>true</code> if the header was successfully written<br>
	 *         <code>false</code> if an error occured
	 */
	public boolean writeHeader(DataOutputStream dos) {
		return writeHeader(dos, parameterType == SDDS_ENUM ? 6 : 5);
	}

	private String legacyHeader(String fixedValue, String typeName) {
		return "&parameter " + SDDSNamelist.legacyField("name", parameterName)
				+ SDDSNamelist.legacyField("symbol", SDDSNamelist.blankToNull(parameterSymbol))
				+ SDDSNamelist.legacyField("units", SDDSNamelist.blankToNull(parameterUnits))
				+ SDDSNamelist.legacyField("description", SDDSNamelist.blankToNull(parameterDescription))
				+ SDDSNamelist.legacyField("format_string", SDDSNamelist.blankToNull(parameterFormatString))
				+ SDDSNamelist.legacyField("type", typeName)
				+ SDDSNamelist.legacyField("fixed_value", fixedValue) + "&end\n";
	}

	/**
	 * Write the header to a file.
	 *
	 * @param dos     a valid DataOutputStream
	 * @param version protocol version of the file being written
	 * @return <code>true</code> if the header was successfully written<br>
	 *         <code>false</code> if an error occured
	 */
	public boolean writeHeader(DataOutputStream dos, int version) {
		if (parameterName == null)
			return false;
		try {
			if (version == 6) {
				String line = "&parameter " + SDDSNamelist.field6("name", parameterName)
						+ SDDSNamelist.field6("symbol", parameterSymbol) + SDDSNamelist.field6("units", parameterUnits)
						+ SDDSNamelist.field6("description", parameterDescription)
						+ SDDSNamelist.field6("format_string", SDDSNamelist.blankToNull(parameterFormatString))
						+ "type=" + SDDSUtil.getTypeName(parameterType) + ", "
						+ SDDSNamelist.field6("fixed_value", parameterFixedValue)
						+ SDDSNamelist.field6("enum_values", getEnumValues()) + " &end\n";
				dos.write(SDDSNamelist.finish6(line));
			} else {
				dos.writeBytes(legacyHeader(parameterFixedValue, SDDSUtil.getTypeName(parameterType)));
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Write the value of the parameter for a chosen page
	 *
	 * @param dos   a valid DataOutputStream
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @param ascii set <code>true</code> to write ascii parameters,
	 *              <code>false</code> to write big endian binary parameters
	 * @return <code>true</code> if the value was successfully written<br>
	 *         <code>false</code> if an invalid page was encountered or if
	 *         the parameter type is undefined.
	 */
	public boolean writeValue(DataOutputStream dos, int page, boolean ascii) {
		if (parameterFixedValue != null)
			return true;
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || (values == null))
			return false;
		try {
			if (ascii)
				dos.writeBytes(SDDSValues.asciiText(parameterType, values, page - 1) + "\n");
			else
				SDDSValues.writeBinary(dos, parameterType, values, page - 1, true);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Write the little endian binary value of the parameter for a chosen page
	 *
	 * @param ledos a valid LEDataOutputStream
	 * @param page  the page of the parameter, must be greater then <code>0</code>
	 * @return <code>true</code> if the value was successfully written<br>
	 *         <code>false</code> if an invalid page was encountered or if
	 *         the parameter type is undefined.
	 */
	public boolean writeValue(LEDataOutputStream ledos, int page) {
		if (parameterFixedValue != null)
			return true;
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || (values == null))
			return false;
		try {
			SDDSValues.writeBinary(ledos, parameterType, values, page - 1, false);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Read the fixed value as the C library does, which interprets escapes in strings.
	 *
	 * @return storage holding the value, or <code>null</code> if the text is invalid
	 */
	private Object scanFixedValue() {
		Object data = SDDSValues.newArray(parameterType, 1);
		String raw = SDDSText.toRaw(parameterFixedValue);
		try {
			if (parameterType == SDDS_STRING) {
				String text = raw;
				if ((text.length() > 0) && (text.charAt(text.length() - 1) == '\r'))
					text = text.substring(0, text.length() - 1);
				if ((text.length() > 0) && (text.charAt(0) == '"'))
					text = SDDSText.nextToken(text, new int[] { 0 });
				((String[]) data)[0] = SDDSText.fromRaw(SDDSText.endAtNul(SDDSText.interpretEscapes(text)));
			} else {
				String token = SDDSText.nextToken(raw, new int[] { 0 });
				if (token == null)
					return null;
				SDDSValues.scan(parameterType, parameterType == SDDS_ENUM ? raw : token, data, 0, enumDefinition);
			}
		} catch (IllegalArgumentException e) {
			return null;
		}
		return data;
	}

	/**
	 * Check that a fixed value can be read as a value of this parameter's type
	 *
	 * @return <code>true</code> if it can, or if there is no fixed value
	 */
	boolean isValidFixedValue() {
		return (parameterFixedValue == null) || (scanFixedValue() != null);
	}

	/**
	 * Get the values of the parameter for a chosen page. An enum parameter gives an
	 * {@link SDDSEnumValue} and a ulong64 parameter gives a <code>Long</code>, or a
	 * <code>BigInteger</code> for a value too large for a <code>Long</code>.
	 *
	 * @param page          the page of the parameter, must be greater then
	 *                      <code>0</code>
	 * @param string_quotes <code>true</code> if quotes are to be added around
	 *                      strings<br>
	 *                      <code>false</code> if raw strings are to be outputed.
	 * @return Object containing parameter data
	 */
	public Object getValue(int page, boolean string_quotes) {
		if (parameterFixedValue != null) {
			Object data = scanFixedValue();
			return data == null ? null : SDDSValues.box(parameterType, data, 0, enumDefinition, string_quotes);
		}
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || (values == null))
			return null;
		return SDDSValues.box(parameterType, values, page - 1, enumDefinition, string_quotes);
	}
}
