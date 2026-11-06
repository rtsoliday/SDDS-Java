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

import java.util.*;
import SDDS.java.SDDS.SDDSUtil;

/**
 * Title: SDDSArray
 * Description: Provide support for storing array data.
 * Copyright: Copyright (c) 2001
 * Company: Collider Accelerator Department, Brookhaven National Laboratory
 * 
 * @author Seth Nemesure <a href=mailto:seth@bnl.gov>seth@bnl.gov</a>
 * @version 1.0 (October, 2001)
 */

public class SDDSArray {
	String arrayName;
	String arraySymbol;
	String arrayUnits;
	String arrayDescription;
	String arrayFormatString;
	String arrayGroupName;

	int arrayType;
	int arrayFieldLength;

	/** One storage array per page; see SDDSValues. */
	Object[] pages;
	SDDSEnumDefinition enumDefinition;

	int pageCount;
	int arrayDimensions;
	int[][] arrayDim;
	int[] rowCount;

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

	/**
	 * Create a new SDDS array that can be used to store array information for
	 * SDDS files
	 */
	public SDDSArray() {
		pageCount = 0;
		arrayType = 0;
		arrayDimensions = 1;
		arrayDim = new int[1][arrayDimensions];
		arrayDim[0] = null;
	}

	/**
	 * Set up the array information
	 *
	 * @param name          should be a valid name as defined by
	 *                      SDDSUtil.isValidName
	 * @param symbol        may be <code>null</code>
	 * @param units         may be <code>null</code>
	 * @param description   may be <code>null</code>
	 * @param format_string may be <code>null</code>
	 * @param group_name    may be <code>null</code>
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
	 * @param field_length  has no effect currently
	 * @param dimensions    integer
	 */

	public void setupArray(String name, String symbol, String units, String description,
			String format_string, String group_name, int type, int field_length, int dimensions) {
		arrayName = name;
		arraySymbol = symbol;
		arrayUnits = units;
		arrayDescription = description;
		arrayFormatString = format_string;
		arrayGroupName = group_name;
		arrayType = type;
		arrayFieldLength = field_length;
		arrayDimensions = dimensions;
		arrayDim = new int[1][arrayDimensions];
		arrayDim[0] = null;
	}

	/**
	 * Returns the name of the array
	 *
	 * @return The <code>String</code> value of the array name.
	 */
	public String getName() {
		return arrayName;
	}

	/**
	 * Set the name of the array
	 *
	 * @param name name of the array
	 */
	public void setName(String name) {
		arrayName = name;
	}

	/**
	 * Returns the symbol of the array
	 *
	 * @return The <code>String</code> value of the array symbol.
	 */
	public String getSymbol() {
		return arraySymbol;
	}

	/**
	 * Set the symbol of the array
	 *
	 * @param symbol symbol of the array
	 */
	public void setSymbol(String symbol) {
		arraySymbol = symbol;
	}

	/**
	 * Returns the units of the array
	 *
	 * @return The <code>String</code> value of the array units.
	 */
	public String getUnits() {
		return arrayUnits;
	}

	/**
	 * Set the units of the array
	 *
	 * @param units units of the array
	 */
	public void setUnits(String units) {
		arrayUnits = units;
	}

	/**
	 * Returns the description of the array
	 *
	 * @return The <code>String</code> value of the array description.
	 */
	public String getDescription() {
		return arrayDescription;
	}

	/**
	 * Set the description of the array
	 *
	 * @param description description of the array
	 */
	public void setDescription(String description) {
		arrayDescription = description;
	}

	/**
	 * Returns the format string of the array
	 *
	 * @return The <code>String</code> value of the array format string.
	 */
	public String getFormat_String() {
		return arrayFormatString;
	}

	/**
	 * Set the format string of the array
	 *
	 * @param format_string format string of the array
	 */
	public void setFormat_String(String format_string) {
		if ((arrayType == SDDS_ENUM) && (SDDSNamelist.blankToNull(format_string) != null))
			throw new IllegalArgumentException("Enum arrays take no format string");
		arrayFormatString = format_string;
	}

	/**
	 * Returns the group name of the array
	 *
	 * @return The <code>String</code> value of the array group name.
	 */
	public String getGroup_Name() {
		return arrayGroupName;
	}

	/**
	 * Set the group name of the array
	 *
	 * @param group_name format string of the array
	 */
	public void setGroup_Name(String group_name) {
		arrayGroupName = group_name;
	}

	/**
	 * Returns the type of the array
	 *
	 * @return The <code>int</code> value of the array type.
	 */
	public int getType() {
		return arrayType;
	}

	/**
	 * Returns the field length of the array
	 *
	 * @return The <code>int</code> value of the array field length.
	 */
	public int getField_Length() {
		return arrayFieldLength;
	}

	/**
	 * Set the field length of the array
	 *
	 * @param field_length field length of the array
	 */
	public void setField_Length(int field_length) {
		if ((arrayType == SDDS_ENUM) && (field_length != 0))
			throw new IllegalArgumentException("Enum arrays take no field length");
		arrayFieldLength = field_length;
	}

	/**
	 * Returns the number of dimensions in the array
	 *
	 * @return The <code>int</code> value of the dimensions.
	 */
	public int getDimensions() {
		return arrayDimensions;
	}

		/**
	 * Returns the number of elements in each dimension of the array
	 *
	 * @param page index of page
	 * @return The <code>int</code> array values.
	 */
	public int[] getDim(int page) {
		if ((arrayDim == null) || (page < 1) || (page > arrayDim.length))
			return null;
		return arrayDim[page - 1];
	}

	/**
	 * Set the number of elements in each dimension of the array
	 *
	 * @param page index of page
	 * @param dim  format string of the array
	 */
	public void setDim(int page, int[] dim) {
		if ((page > 0) && (page <= pageCount)) {
			growPages(page);
			arrayDim[page - 1] = dim;
		} else if (page == pageCount + 1) {
			growPages(page);
			arrayDim[page - 1] = dim;
		}
	}

	/**
	 * Returns the number of pages that the array contains
	 *
	 * @return The <code>int</code> value of the number of pages the array contains.
	 */
	public int getPageCount() {
		return pageCount;
	}

	/**
	 * Returns the number of rows a page contains
	 *
	 * @return The <code>int</code> value of the number of pages the array
	 *         contains.<br>
	 *         <code>-1</code> if the page value is invalid.
	 */
	public int getRowCount(int page) {
		if ((rowCount == null) || (page < 1) || (page > rowCount.length))
			return -1;
		return rowCount[page - 1];
	}

	/**
	 * Returns the dictionary of an enum array
	 *
	 * @return the dictionary, or <code>null</code> if this array is not an enum
	 */
	public SDDSEnumDefinition getEnumDefinition() {
		return enumDefinition;
	}

	/**
	 * Returns the canonical mapping text of an enum array
	 *
	 * @return the text of the <code>enum_values</code> attribute, or
	 *         <code>null</code> if this array is not an enum
	 */
	public String getEnumValues() {
		return enumDefinition == null ? null : enumDefinition.getValues();
	}

	/**
	 * Give a new enum array its dictionary. A dictionary cannot be replaced this way;
	 * use {@link #convert} for that.
	 *
	 * @param definition the dictionary
	 * @throws IllegalArgumentException if this is not a new enum array
	 */
	public void setEnumDefinition(SDDSEnumDefinition definition) {
		if ((arrayType != SDDS_ENUM) || (enumDefinition != null) || (definition == null))
			throw new IllegalArgumentException("A dictionary can only be given to a new enum array");
		enumDefinition = definition;
	}

	private void growPages(int count) {
		if (pages == null) {
			pages = new Object[count];
			rowCount = new int[count];
		} else if (pages.length < count) {
			pages = Arrays.copyOf(pages, count);
			rowCount = Arrays.copyOf(rowCount, count);
		}
		if (arrayDim == null)
			arrayDim = new int[count][];
		else if (arrayDim.length < count)
			arrayDim = Arrays.copyOf(arrayDim, count);
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
		growPages(pageCount + 1);
		for (int i = pageCount; i >= page; i--) {
			pages[i] = pages[i - 1];
			rowCount[i] = rowCount[i - 1];
			arrayDim[i] = arrayDim[i - 1];
		}
		pages[page - 1] = null;
		rowCount[page - 1] = 0;
		arrayDim[page - 1] = new int[arrayDimensions];
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
		for (int i = page; i < pageCount; i++) {
			pages[i - 1] = pages[i];
			rowCount[i - 1] = rowCount[i];
			arrayDim[i - 1] = arrayDim[i];
		}
		pages = Arrays.copyOf(pages, pageCount - 1);
		rowCount = Arrays.copyOf(rowCount, pageCount - 1);
		arrayDim = Arrays.copyOf(arrayDim, Math.max(pageCount - 1, 1));
		if (pageCount == 1)
			arrayDim[0] = null;
		pageCount--;
		return true;
	}

	/**
	 * Create blank pages up to the last page specified
	 *
	 * @param lastpage last page to create blank page
	 */
	public void makeBlankPages(int lastpage) {
		if (lastpage > pageCount) {
			growPages(lastpage);
			for (int i = pageCount; i < lastpage; i++)
				if (arrayDim[i] == null)
					arrayDim[i] = new int[arrayDimensions];
			pageCount = lastpage;
		}
	}

	/**
	 * Write the header to a file using the format of SDDS protocol 1 to 5.
	 *
	 * @param dos a valid DataOutputStream
	 * @return <code>true</code> if the header was successfully written<br>
	 *         <code>false</code> if an error occured
	 */
	public boolean writeHeader(DataOutputStream dos) {
		return writeHeader(dos, arrayType == SDDS_ENUM ? 6 : 5);
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
		if (arrayName == null)
			return false;
		try {
			if (version == 6) {
				String line = "&array " + SDDSNamelist.field6("name", arrayName)
						+ SDDSNamelist.field6("symbol", arraySymbol) + SDDSNamelist.field6("units", arrayUnits)
						+ SDDSNamelist.field6("description", arrayDescription)
						+ SDDSNamelist.field6("format_string", SDDSNamelist.blankToNull(arrayFormatString))
						+ SDDSNamelist.field6("group_name", arrayGroupName) + "type="
						+ SDDSUtil.getTypeName(arrayType) + ", field_length=0, dimensions="
						+ arrayDimensions + ", " + SDDSNamelist.field6("enum_values", getEnumValues()) + " &end\n";
				dos.write(SDDSNamelist.finish6(line));
			} else {
				dos.writeBytes("&array " + SDDSNamelist.legacyField("name", arrayName)
						+ SDDSNamelist.legacyField("symbol", SDDSNamelist.blankToNull(arraySymbol))
						+ SDDSNamelist.legacyField("units", SDDSNamelist.blankToNull(arrayUnits))
						+ SDDSNamelist.legacyField("description", SDDSNamelist.blankToNull(arrayDescription))
						+ SDDSNamelist.legacyField("format_string", SDDSNamelist.blankToNull(arrayFormatString))
						+ SDDSNamelist.legacyField("group_name", SDDSNamelist.blankToNull(arrayGroupName))
						+ SDDSNamelist.legacyField("type", SDDSUtil.getTypeName(arrayType))
						+ (arrayDimensions != 1 ? "dimensions=" + arrayDimensions + ", " : "") + " &end\n");
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Replace the values of a page.
	 *
	 * @param data  values in storage form
	 * @param count number of values
	 * @param page  the page of the array, must be greater then <code>0</code>
	 */
	void store(Object data, int count, int page) {
		if (page < 1)
			return;
		growPages(page);
		page--;
		pages[page] = SDDSValues.copyOf(data, count);
		rowCount[page] = count;
		if (arrayDim[page] == null)
			arrayDim[page] = new int[arrayDimensions];
		if (pageCount < page + 1)
			pageCount = page + 1;
	}

	/**
	 * Set the values of the array at a chosen page
	 *
	 * @param v    a long precision number array. For a ulong64 array these are the
	 *             raw 64 bits. For an enum array these are codes.
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a code is not mapped by an enum array
	 */
	public void setValues(long v[], int page) {
		if (!SDDSUtil.isValidType(arrayType))
			return;
		store(SDDSValues.fromLongs(arrayType, v, enumDefinition), v.length, page);
	}

	/**
	 * Set the values of the array at a chosen page
	 *
	 * @param v    a double precision floating-point number array
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if this is an enum array
	 */
	public void setValues(double v[], int page) {
		if (!SDDSUtil.isValidType(arrayType))
			return;
		store(SDDSValues.fromDoubles(arrayType, v), v.length, page);
	}

	/**
	 * Set the values of the array at a chosen page
	 *
	 * @param v    a string value array. For an enum array these are decimal codes.
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a value is not a code mapped by an enum
	 *                                  array
	 */
	public void setValues(String v[], int page) {
		if (!SDDSUtil.isValidType(arrayType))
			return;
		store(SDDSValues.fromStrings(arrayType, v, enumDefinition), v.length, page);
	}

	/**
	 * Set the values of a longdouble array at a chosen page, without any loss of
	 * precision
	 *
	 * @param v    a long double array
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if this is not a longdouble array
	 */
	public void setValues(SDDSLongDouble v[], int page) {
		if (arrayType != SDDS_LONGDOUBLE)
			throw new IllegalArgumentException("Array " + arrayName + " is not a longdouble array");
		store(v, v.length, page);
	}

	/**
	 * Set the labels of an enum array at a chosen page
	 *
	 * @param labels labels from the dictionary
	 * @param page   the page of the array, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a label is not in the dictionary
	 */
	public void setLabels(String labels[], int page) {
		if (enumDefinition == null)
			throw new IllegalArgumentException("Array " + arrayName + " is not an enum array");
		store(SDDSValues.convert(SDDS_STRING, null, SDDS_ENUM, enumDefinition, labels, labels.length), labels.length,
				page);
	}

	/**
	 * Set enum values where some may not have been chosen yet, as an interactive program
	 * needs to. A chosen code is checked. A <code>null</code> is stored as code 0, with
	 * no dictionary check, so the file cannot be written until it has a mapped code.
	 *
	 * @param codes codes, with <code>null</code> for a value not chosen yet
	 * @param page  the page of the array, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a code is not mapped
	 */
	public void setEnumCells(Integer codes[], int page) {
		if (enumDefinition == null)
			throw new IllegalArgumentException("Array " + arrayName + " is not an enum array");
		store(SDDSValues.enumCells(enumDefinition, codes), codes.length, page);
	}

	/**
	 * Returns the codes of an enum array on a chosen page
	 *
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @return the codes, or <code>null</code> if this is not an enum array or the page
	 *         has no data
	 */
	public int[] getEnumCodes(int page) {
		if ((arrayType != SDDS_ENUM) || (page < 1) || (page > pageCount) || (pages[page - 1] == null))
			return null;
		long[] data = (long[]) pages[page - 1];
		int[] codes = new int[rowCount[page - 1]];
		for (int i = 0; i < codes.length; i++)
			codes[i] = (int) data[i];
		return codes;
	}

	/**
	 * Returns the labels of an enum array on a chosen page
	 *
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @return the labels, with <code>null</code> for any value whose code is not
	 *         mapped, or <code>null</code> if this is not an enum array or the page
	 *         has no data
	 */
	public String[] getEnumLabels(int page) {
		int[] codes = getEnumCodes(page);
		if (codes == null)
			return null;
		String[] labels = new String[codes.length];
		for (int i = 0; i < codes.length; i++)
			labels[i] = enumDefinition.codeToLabel(codes[i]);
		return labels;
	}

	/**
	 * Find the first value on a page that an enum array cannot write
	 *
	 * @param page the page of the array
	 * @return the element, starting from <code>1</code>, or <code>0</code> if all are
	 *         valid
	 */
	int firstInvalidElement(int page) {
		if ((arrayType != SDDS_ENUM) || (page < 1) || (page > pageCount) || (pages[page - 1] == null))
			return 0;
		long[] data = (long[]) pages[page - 1];
		for (int i = 0; i < rowCount[page - 1]; i++)
			if ((enumDefinition == null) || !enumDefinition.hasCode(data[i]))
				return i + 1;
		return 0;
	}

	/** Returns the product of the dimensions of a page. */
	private long elementCount(int page) {
		if ((arrayDim == null) || (page >= arrayDim.length) || (arrayDim[page] == null))
			return 0;
		long count = 1;
		for (int i = 0; i < arrayDimensions; i++)
			count *= arrayDim[page][i];
		return count;
	}

	/**
	 * Check that the dimensions of a page describe its values
	 *
	 * @param page the page of the array, must be greater then <code>0</code>
	 * @return <code>true</code> if they do
	 */
	boolean dimensionsMatch(int page) {
		if ((page < 1) || (page > pageCount))
			return false;
		page--;
		if ((arrayDim != null) && (page < arrayDim.length) && (arrayDim[page] != null)
				&& (arrayDim[page].length != arrayDimensions))
			return false;
		long values = (pages[page] == null) ? 0 : rowCount[page];
		return elementCount(page) == values;
	}

	/**
	 * Convert this array in place between enum, long and string. Every value is
	 * checked before any is changed.
	 *
	 * @param targetType <code>SDDS_ENUM</code>, <code>SDDS_LONG</code> or
	 *                   <code>SDDS_STRING</code>
	 * @param target     dictionary for an enum target, otherwise <code>null</code>
	 * @throws IllegalArgumentException if the conversion is not allowed or a value
	 *                                  cannot be converted
	 */
	public void convert(int targetType, SDDSEnumDefinition target) {
		if (!SDDSValues.isConversion(arrayType, targetType) || ((targetType == SDDS_ENUM) && (target == null)))
			throw new IllegalArgumentException("Invalid enum conversion for array " + arrayName);
		Object[] converted = new Object[pageCount];
		for (int i = 0; i < pageCount; i++)
			if ((pages != null) && (pages[i] != null))
				converted[i] = SDDSValues.convert(arrayType, enumDefinition, targetType, target, pages[i],
						rowCount[i]);
		pages = converted;
		rowCount = rowCount == null ? new int[pageCount] : Arrays.copyOf(rowCount, pageCount);
		arrayType = targetType;
		enumDefinition = target;
		arrayFormatString = null;
		arrayFieldLength = 0;
	}

	/**
	 * Copy the values of another array of the same type
	 *
	 * @param source array to copy from
	 */
	void copyValues(SDDSArray source) {
		pageCount = source.pageCount;
		pages = source.pages == null ? null : new Object[source.pages.length];
		rowCount = source.rowCount == null ? null : source.rowCount.clone();
		arrayDim = source.arrayDim == null ? null : new int[source.arrayDim.length][];
		for (int i = 0; (pages != null) && (i < pages.length); i++)
			if (source.pages[i] != null)
				pages[i] = SDDSValues.copyOf(source.pages[i], Array.getLength(source.pages[i]));
		for (int i = 0; (arrayDim != null) && (i < arrayDim.length); i++)
			if (source.arrayDim[i] != null)
				arrayDim[i] = source.arrayDim[i].clone();
	}

	/**
	 * Write the values of the array for a chosen page
	 *
	 * @param dos   a valid DataOutputStream
	 * @param page  the page of the array, must be greater then <code>0</code>
	 * @param ascii set <code>true</code> to write ascii column data,
	 *              <code>false</code> to write big endian binary array data
	 * @return <code>true</code> if the values were successfully written<br>
	 *         <code>false</code> if an invalid page was encountered or if
	 *         the array type is undefined.
	 */
	public boolean writeValues(DataOutputStream dos, int page, boolean ascii) {
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || !dimensionsMatch(page))
			return false;
		page--;
		try {
			int[] dim = arrayDim[page];
			if (ascii) {
				StringBuilder line = new StringBuilder();
				for (int i = 0; i < arrayDimensions; i++)
					line.append(dim == null ? 0 : dim[i]).append(' ');
				dos.writeBytes(line.append('\n').toString());
				if (elementCount(page) == 0)
					return true;
				for (int i = 0; i < rowCount[page]; i++)
					dos.writeBytes(SDDSValues.asciiText(arrayType, pages[page], i) + " ");
				dos.writeBytes("\n");
			} else {
				for (int i = 0; i < arrayDimensions; i++)
					dos.writeInt(dim == null ? 0 : dim[i]);
				if (elementCount(page) == 0)
					return true;
				for (int i = 0; i < rowCount[page]; i++)
					SDDSValues.writeBinary(dos, arrayType, pages[page], i, true);
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Write the values of the array for a chosen page
	 *
	 * @param ledos a valid DataOutputStream
	 * @param page  the page of the array, must be greater then <code>0</code>
	 * @return <code>true</code> if the values were successfully written<br>
	 *         <code>false</code> if an invalid page was encountered or if
	 *         the array type is undefined.
	 */
	public boolean writeValues(LEDataOutputStream ledos, int page) {
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || !dimensionsMatch(page))
			return false;
		page--;
		try {
			int[] dim = arrayDim[page];
			for (int i = 0; i < arrayDimensions; i++)
				ledos.writeInt(dim == null ? 0 : dim[i]);
			if (elementCount(page) == 0)
				return true;
			for (int i = 0; i < rowCount[page]; i++)
				SDDSValues.writeBinary(ledos, arrayType, pages[page], i, false);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Write the little endian binary value of the array for a chosen page
	 *
	 * @param ledos a valid LEDataOutputStream
	 * @param page  the page of the array, must be greater then <code>0</code>
	 * @return <code>true</code> if the value was successfully written<br>
	 *         <code>false</code> if an invalid page was encountered or if
	 *         the array type is undefined.
	 */
	public boolean writeElement(LEDataOutputStream ledos, int page) {
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || (pages[page - 1] == null))
			return false;
		page--;
		try {
			for (int i = 0; i < rowCount[page]; i++)
				SDDSValues.writeBinary(ledos, arrayType, pages[page], i, false);
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	/**
	 * Get the values of the array for a chosen page. An enum array gives
	 * {@link SDDSEnumValue} objects and a ulong64 array gives <code>Long</code>
	 * objects, or <code>BigInteger</code> objects for values too large for a
	 * <code>Long</code>.
	 *
	 * @param page          the page of the array, must be greater then
	 *                      <code>0</code>
	 * @param string_quotes <code>true</code> if quotes are to be added around
	 *                      strings<br>
	 *                      <code>false</code> if raw strings are to be outputed.
	 * @return Object array of data
	 */
	public Object[] getValues(int page, boolean string_quotes) {
		if ((pageCount == 0) || (page < 1) || (page > pageCount) || (pages[page - 1] == null))
			return null;
		page--;
		Object[] values = new Object[rowCount[page]];
		for (int i = 0; i < values.length; i++)
			values[i] = SDDSValues.box(arrayType, pages[page], i, enumDefinition, string_quotes);
		return values;
	}

}
