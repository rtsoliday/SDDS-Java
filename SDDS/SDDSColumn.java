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
import java.util.Arrays;
//import hb.format.*;

/**
 * The SDDS Column object is used by the SDDS File object.
 * It was not the intention of the author to use this object
 * directly but it is still supported. After initializing the
 * SDDS Column the setupColumn method must be used to
 * give the column a name and a valid data type (long, short,
 * float, double, string, character). The setValues method is
 * used next to give the column values for different
 * pages. The writeValues method or the writeElement method
 * can be used to print the column values for a particular page.
 *
 * @author Robert Soliday
 *         <a href=mailto:soliday@aps.anl.gov>soliday@aps.anl.gov</a>
 * @version 1.0 (November 1, 1999)
 * @since JDK 1.2
 */
public class SDDSColumn {
	String columnName;
	String columnSymbol;
	String columnUnits;
	String columnDescription;
	String columnFormatString;
	int columnType;
	int columnFieldLength;
	/** One storage array per page; see SDDSValues. */
	Object[] pages;
	SDDSEnumDefinition enumDefinition;
	int pageCount;
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
	 * Create a new SDDS column that can be used to store column information for
	 * SDDS files.
	 */
	public SDDSColumn() {
		pageCount = 0;
		columnType = 0;
	}

	/**
	 * Set up the column information.
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
	 * @param field_length  has no effect currently
	 */
	public void setupColumn(String name, String symbol, String units, String description, String format_string,
			int type, int field_length) {
		columnName = name;
		columnSymbol = symbol;
		columnUnits = units;
		columnDescription = description;
		columnFormatString = format_string;
		columnType = type;
		columnFieldLength = field_length;
	}

	/**
	 * Returns the name of the column
	 *
	 * @return The <code>String</code> value of the column name.
	 */
	public String getName() {
		return columnName;
	}

	/**
	 * Set the name of the column
	 *
	 * @param name name of the column
	 */
	public void setName(String name) {
		columnName = name;
	}

	/**
	 * Returns the symbol of the column
	 *
	 * @return The <code>String</code> value of the column symbol.
	 */
	public String getSymbol() {
		return columnSymbol;
	}

	/**
	 * Set the symbol of the column
	 *
	 * @param symbol symbol of the column
	 */
	public void setSymbol(String symbol) {
		columnSymbol = symbol;
	}

	/**
	 * Returns the units of the column
	 *
	 * @return The <code>String</code> value of the column units.
	 */
	public String getUnits() {
		return columnUnits;
	}

	/**
	 * Set the units of the column
	 *
	 * @param units units of the column
	 */
	public void setUnits(String units) {
		columnUnits = units;
	}

	/**
	 * Returns the description of the column
	 *
	 * @return The <code>String</code> value of the column description.
	 */
	public String getDescription() {
		return columnDescription;
	}

	/**
	 * Set the description of the column
	 *
	 * @param description description of the column
	 */
	public void setDescription(String description) {
		columnDescription = description;
	}

	/**
	 * Returns the format string of the column
	 *
	 * @return The <code>String</code> value of the column format string.
	 */
	public String getFormat_String() {
		return columnFormatString;
	}

	/**
	 * Set the format string of the column
	 *
	 * @param format_string format string of the column
	 */
	public void setFormat_String(String format_string) {
		if ((columnType == SDDS_ENUM) && (SDDSNamelist.blankToNull(format_string) != null))
			throw new IllegalArgumentException("Enum columns take no format string");
		columnFormatString = format_string;
	}

	/**
	 * Returns the type of the column
	 *
	 * @return The <code>int</code> value of the column type.
	 */
	public int getType() {
		return columnType;
	}

	/**
	 * Returns the field length of the column
	 *
	 * @return The <code>int</code> value of the column field length.
	 */
	public int getField_Length() {
		return columnFieldLength;
	}

	/**
	 * Set the field length of the column
	 *
	 * @param field_length field length of the column
	 */
	public void setField_Length(int field_length) {
		if ((columnType == SDDS_ENUM) && (field_length != 0))
			throw new IllegalArgumentException("Enum columns take no field length");
		columnFieldLength = field_length;
	}

		/**
	 * Returns the number of pages that the column contains
	 *
	 * @return The <code>int</code> value of the number of pages the column
	 *         contains.
	 */
	public int getPageCount() {
		return pageCount;
	}

	/**
	 * Returns the number of rows a page contains
	 *
	 * @return The <code>int</code> value of the number of pages the column
	 *         contains.<br>
	 *         <code>-1</code> if the page value is invalid.
	 */
	public int getRowCount(int page) {
		if ((rowCount == null) || (page < 1) || (page > rowCount.length))
			return -1;
		return rowCount[page - 1];
	}

	/**
	 * Returns the dictionary of an enum column
	 *
	 * @return the dictionary, or <code>null</code> if this column is not an enum
	 */
	public SDDSEnumDefinition getEnumDefinition() {
		return enumDefinition;
	}

	/**
	 * Returns the canonical mapping text of an enum column
	 *
	 * @return the text of the <code>enum_values</code> attribute, or
	 *         <code>null</code> if this column is not an enum
	 */
	public String getEnumValues() {
		return enumDefinition == null ? null : enumDefinition.getValues();
	}

	/**
	 * Give a new enum column its dictionary. A dictionary cannot be replaced this way;
	 * use {@link #convert} for that.
	 *
	 * @param definition the dictionary
	 * @throws IllegalArgumentException if this is not a new enum column
	 */
	public void setEnumDefinition(SDDSEnumDefinition definition) {
		if ((columnType != SDDS_ENUM) || (enumDefinition != null) || (definition == null))
			throw new IllegalArgumentException("A dictionary can only be given to a new enum column");
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
		}
		pages[page - 1] = null;
		rowCount[page - 1] = 0;
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
		}
		pages = Arrays.copyOf(pages, pageCount - 1);
		rowCount = Arrays.copyOf(rowCount, pageCount - 1);
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
			pageCount = lastpage;
		}
	}

	/**
	 * Deletes the value of the column at a chosen page and at a chosen row
	 *
	 * @param page the page of the column, must be greater then <code>0</code>
	 * @param row  the row of the column, must be greater then <code>0</code>
	 */
	public void deleteRow(int page, int row) {
		if ((page < 1) || (row < 1) || (rowCount == null) || (page > rowCount.length))
			return;
		page--;
		int rows = rowCount[page];
		if ((rows == 0) || (row > rows) || (pages[page] == null))
			return;
		System.arraycopy(pages[page], row, pages[page], row - 1, rows - row);
		rowCount[page]--;
	}

	/**
	 * Copy stored values into a page, growing it as needed.
	 *
	 * @param data     values in storage form
	 * @param count    number of values to copy
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 */
	void store(Object data, int count, int page, int startRow) {
		if ((page < 1) || (startRow < 1))
			return;
		growPages(page);
		page--;
		startRow--;
		int rowsNeeded = count + startRow;
		Object current = pages[page];
		if (current == null)
			current = SDDSValues.newArray(columnType, rowsNeeded);
		else if (Array.getLength(current) < rowsNeeded)
			current = SDDSValues.copyOf(current, rowsNeeded);
		System.arraycopy(data, 0, current, startRow, count);
		pages[page] = current;
		if (rowCount[page] < rowsNeeded)
			rowCount[page] = rowsNeeded;
		if (pageCount < page + 1)
			pageCount = page + 1;
	}

	/**
	 * Set the values of the column at a chosen page starting at the chosen row
	 *
	 * @param v        a long precision number array. For a ulong64 column these are
	 *                 the raw 64 bits. For an enum column these are codes.
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a code is not mapped by an enum column
	 */
	public void setValues(long v[], int page, int startRow) {
		if (!SDDSUtil.isValidType(columnType))
			return;
		store(SDDSValues.fromLongs(columnType, v, enumDefinition), v.length, page, startRow);
	}

	/**
	 * Set the values of the column at a chosen page starting at the chosen row
	 *
	 * @param v        a double precision floating-point number array
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if this is an enum column
	 */
	public void setValues(double v[], int page, int startRow) {
		if (!SDDSUtil.isValidType(columnType))
			return;
		store(SDDSValues.fromDoubles(columnType, v), v.length, page, startRow);
	}

	/**
	 * Set the values of the column at a chosen page starting at the chosen row
	 *
	 * @param v        a string value array. For an enum column these are decimal
	 *                 codes.
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a value is not a code mapped by an enum
	 *                                  column
	 */
	public void setValues(String v[], int page, int startRow) {
		if (!SDDSUtil.isValidType(columnType))
			return;
		store(SDDSValues.fromStrings(columnType, v, enumDefinition), v.length, page, startRow);
	}

	/**
	 * Set the values of a longdouble column at a chosen page starting at the chosen
	 * row, without any loss of precision
	 *
	 * @param v        a long double array
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if this is not a longdouble column
	 */
	public void setValues(SDDSLongDouble v[], int page, int startRow) {
		if (columnType != SDDS_LONGDOUBLE)
			throw new IllegalArgumentException("Column " + columnName + " is not a longdouble column");
		store(v, v.length, page, startRow);
	}

	/**
	 * Set the labels of an enum column at a chosen page starting at the chosen row
	 *
	 * @param labels   labels from the dictionary
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a label is not in the dictionary
	 */
	public void setLabels(String labels[], int page, int startRow) {
		store(SDDSValues.convert(SDDS_STRING, null, SDDS_ENUM, requireDictionary(), labels, labels.length),
				labels.length, page, startRow);
	}

	private SDDSEnumDefinition requireDictionary() {
		if (enumDefinition == null)
			throw new IllegalArgumentException("Column " + columnName + " is not an enum column");
		return enumDefinition;
	}

	/**
	 * Set enum values where some may not have been chosen yet, as an interactive program
	 * needs to. A chosen code is checked. A <code>null</code> is stored as code 0, with
	 * no dictionary check, so the file cannot be written until it has a mapped code.
	 *
	 * @param codes    codes, with <code>null</code> for a value not chosen yet
	 * @param page     the page of the column, must be greater then <code>0</code>
	 * @param startRow the row of the column, must be greater then <code>0</code>
	 * @throws IllegalArgumentException if a code is not mapped
	 */
	public void setEnumCells(Integer codes[], int page, int startRow) {
		store(SDDSValues.enumCells(requireDictionary(), codes), codes.length, page, startRow);
	}

	/**
	 * Extend a page to a number of rows holding zero values, with no dictionary check.
	 * This is how a program represents enum values that have not been chosen yet. The
	 * file cannot be written until they hold mapped codes.
	 *
	 * @param page the page of the column, must be greater then <code>0</code>
	 * @param rows number of rows the page should have
	 */
	public void setBlankRows(int page, int rows) {
		int current = Math.max(getRowCount(page), 0);
		if ((page >= 1) && (rows > current))
			store(SDDSValues.newArray(columnType, rows - current), rows - current, page, current + 1);
	}

	/**
	 * Returns the codes of an enum column on a chosen page
	 *
	 * @param page the page of the column, must be greater then <code>0</code>
	 * @return the codes, or <code>null</code> if this is not an enum column or the
	 *         page has no data
	 */
	public int[] getEnumCodes(int page) {
		if ((columnType != SDDS_ENUM) || (page < 1) || (page > pageCount))
			return null;
		long[] data = (long[]) pages[page - 1];
		if (data == null)
			return null;
		int[] codes = new int[rowCount[page - 1]];
		for (int i = 0; i < codes.length; i++)
			codes[i] = (int) data[i];
		return codes;
	}

	/**
	 * Returns the labels of an enum column on a chosen page
	 *
	 * @param page the page of the column, must be greater then <code>0</code>
	 * @return the labels, with <code>null</code> for any value whose code is not
	 *         mapped, or <code>null</code> if this is not an enum column or the page
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
	 * Find the first value on a page that an enum column cannot write
	 *
	 * @param page the page of the column
	 * @return the row, starting from <code>1</code>, or <code>0</code> if all are
	 *         valid
	 */
	int firstInvalidRow(int page) {
		if ((columnType != SDDS_ENUM) || (page < 1) || (page > pageCount) || (pages[page - 1] == null))
			return 0;
		long[] data = (long[]) pages[page - 1];
		for (int i = 0; i < rowCount[page - 1]; i++)
			if ((enumDefinition == null) || !enumDefinition.hasCode(data[i]))
				return i + 1;
		return 0;
	}

	/**
	 * Convert this column in place between enum, long and string. Every value is
	 * checked before any is changed.
	 *
	 * @param targetType <code>SDDS_ENUM</code>, <code>SDDS_LONG</code> or
	 *                   <code>SDDS_STRING</code>
	 * @param target     dictionary for an enum target, otherwise <code>null</code>
	 * @throws IllegalArgumentException if the conversion is not allowed or a value
	 *                                  cannot be converted
	 */
	public void convert(int targetType, SDDSEnumDefinition target) {
		if (!SDDSValues.isConversion(columnType, targetType) || ((targetType == SDDS_ENUM) && (target == null)))
			throw new IllegalArgumentException("Invalid enum conversion for column " + columnName);
		Object[] converted = new Object[pageCount];
		for (int i = 0; i < pageCount; i++)
			if ((pages != null) && (pages[i] != null))
				converted[i] = SDDSValues.convert(columnType, enumDefinition, targetType, target, pages[i],
						rowCount[i]);
		pages = converted;
		rowCount = rowCount == null ? new int[pageCount] : Arrays.copyOf(rowCount, pageCount);
		columnType = targetType;
		enumDefinition = target;
		columnFormatString = null;
		columnFieldLength = 0;
	}

	/**
	 * Copy the values of another column of the same type
	 *
	 * @param source column to copy from
	 */
	void copyValues(SDDSColumn source) {
		pageCount = source.pageCount;
		pages = source.pages == null ? null : new Object[source.pages.length];
		rowCount = source.rowCount == null ? null : source.rowCount.clone();
		for (int i = 0; (pages != null) && (i < pages.length); i++)
			if (source.pages[i] != null)
				pages[i] = SDDSValues.copyOf(source.pages[i], Array.getLength(source.pages[i]));
	}

	/**
	 * Write the header to a file using the format of SDDS protocol 1 to 5.
	 *
	 * @param dos a valid DataOutputStream
	 * @return <code>true</code> if the header was successfully written<br>
	 *         <code>false</code> if an error occured
	 */
	public boolean writeHeader(DataOutputStream dos) {
		return writeHeader(dos, columnType == SDDS_ENUM ? 6 : 5);
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
		if (columnName == null)
			return false;
		try {
			if (version == 6) {
				String line = "&column " + SDDSNamelist.field6("name", columnName)
						+ SDDSNamelist.field6("symbol", columnSymbol) + SDDSNamelist.field6("units", columnUnits)
						+ SDDSNamelist.field6("description", columnDescription)
						+ SDDSNamelist.field6("format_string", SDDSNamelist.blankToNull(columnFormatString)) + "type="
						+ SDDSUtil.getTypeName(columnType) + ", field_length=0, "
						+ SDDSNamelist.field6("enum_values", getEnumValues()) + " &end\n";
				dos.write(SDDSNamelist.finish6(line));
			} else {
				dos.writeBytes("&column " + SDDSNamelist.legacyField("name", columnName)
						+ SDDSNamelist.legacyField("symbol", SDDSNamelist.blankToNull(columnSymbol))
						+ SDDSNamelist.legacyField("units", SDDSNamelist.blankToNull(columnUnits))
						+ SDDSNamelist.legacyField("description", SDDSNamelist.blankToNull(columnDescription))
						+ SDDSNamelist.legacyField("format_string", SDDSNamelist.blankToNull(columnFormatString))
						+ SDDSNamelist.legacyField("type", SDDSUtil.getTypeName(columnType)) + " &end\n");
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private boolean validRow(int page, int row) {
		return (pageCount != 0) && (page >= 1) && (page <= pageCount) && (row >= 1) && (row <= rowCount[page - 1]);
	}

	/**
	 * Write the value of the column for a chosen page and row
	 *
	 * @param dos   a valid DataOutputStream
	 * @param page  the page of the column, must be greater then <code>0</code>
	 * @param row   the row of the column, must be greater then <code>0</code>
	 * @param ascii set <code>true</code> to write ascii column data,
	 *              <code>false</code> to write big endian binary column data
	 * @return <code>true</code> if the value was successfully written<br>
	 *         <code>false</code> if an invalid page or row was encountered or if
	 *         the column type is undefined.
	 */
	public boolean writeElement(DataOutputStream dos, int page, int row, boolean ascii) {
		if (!validRow(page, row))
			return false;
		try {
			if (ascii)
				dos.writeBytes(SDDSValues.asciiText(columnType, pages[page - 1], row - 1) + " ");
			else
				SDDSValues.writeBinary(dos, columnType, pages[page - 1], row - 1, true);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Write the little endian binary value of the column for a chosen page and row
	 *
	 * @param ledos a valid LEDataOutputStream
	 * @param page  the page of the column, must be greater then <code>0</code>
	 * @param row   the row of the column, must be greater then <code>0</code>
	 * @return <code>true</code> if the value was successfully written<br>
	 *         <code>false</code> if an invalid page or row was encountered or if
	 *         the column type is undefined.
	 */
	public boolean writeElement(LEDataOutputStream ledos, int page, int row) {
		if (!validRow(page, row))
			return false;
		try {
			SDDSValues.writeBinary(ledos, columnType, pages[page - 1], row - 1, false);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Write the values of the column for a chosen page
	 *
	 * @param dos  a valid DataOutputStream
	 * @param page the page of the column, must be greater then <code>0</code>
	 * @return <code>true</code> if the values were successfully written<br>
	 *         <code>false</code> if an invalid page was encountered or if
	 *         the column type is undefined.
	 */
	public boolean writeColumn(DataOutputStream dos, int page) {
		if ((pageCount == 0) || (page < 1) || (page > pageCount))
			return false;
		page--;
		try {
			for (int i = 0; i < rowCount[page]; i++)
				dos.writeBytes(SDDSValues.asciiText(columnType, pages[page], i) + "\n");
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Get the values of the column for a chosen page. An enum column gives
	 * {@link SDDSEnumValue} objects and a ulong64 column gives <code>Long</code>
	 * objects, or <code>BigInteger</code> objects for values too large for a
	 * <code>Long</code>.
	 *
	 * @param page          the page of the column, must be greater then
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
			values[i] = SDDSValues.box(columnType, pages[page], i, enumDefinition, string_quotes);
		return values;
	}

}
