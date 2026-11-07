/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.table.AbstractTableModel;

import SDDS.java.SDDS.SDDSUtil;

/** The table models of the three data panels. All of them read the document directly. */
final class GridModels {
    private GridModels() {
    }

    /** What a model needs from the main window. */
    interface Host {
        Document document();

        /** The page shown, 0-based, or -1 with no document. */
        int page();

        /** A cell editor finished with new text for a cell. */
        void cellEdited(Base model, int row, int column, String text);
    }

    /** Behavior common to the panel models. */
    abstract static class Base extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        final Host host;

        Base(Host host) {
            this.host = host;
        }

        abstract DefinitionDialogs.Kind kind();

        /** Index in its definition list of the item in a cell, or -1 for none. */
        abstract int item(int row, int column);

        /** Position within its item of the value in a cell: page row, array element, or 0. */
        abstract int element(int row, int column);

        /** The stored text of a cell. */
        abstract String raw(int row, int column);

        /** Change the stored text of a value cell. */
        abstract void store(int item, int element, int page, String text);

        /** Read the stored text of a value directly. */
        abstract String stored(int item, int element, int page);

        final Definition definition(int row, int column) {
            int item = item(row, column);
            return item < 0 ? null : definitions().get(item);
        }

        abstract List<Definition> definitions();

        /** True if a cell holds the hatching past the end of a shorter array. */
        boolean isPadding(int row, int column) {
            return false;
        }

        final Document.Page currentPage() {
            int page = host.page();
            return page < 0 ? null : host.document().pages.get(page);
        }

        public String getColumnName(int column) {
            return column < definitions().size() ? definitions().get(column).name : "";
        }

        public Object getValueAt(int row, int column) {
            Definition d = definition(row, column);
            return d == null ? "" : Cells.display(raw(row, column), d);
        }

        public void setValueAt(Object value, int row, int column) {
            host.cellEdited(this, row, column, value == null ? "" : value.toString());
        }

        public boolean isCellEditable(int row, int column) {
            return item(row, column) >= 0 && !isPadding(row, column);
        }
    }

    /** Parameters wrapped into side-by-side groups so short lists use the panel width. */
    static final class ParameterModel extends Base {
        private static final long serialVersionUID = 1L;
        static final int NAME = 0, TYPE = 1, UNITS = 2, VALUE = 3, DESCRIPTION = 4;
        private int[] fields = { NAME, TYPE, VALUE };
        private int groups = 1;

        ParameterModel(Host host) {
            super(host);
        }

        DefinitionDialogs.Kind kind() {
            return DefinitionDialogs.Kind.PARAMETER;
        }

        List<Definition> definitions() {
            return host.page() < 0 ? java.util.Collections.<Definition>emptyList() : host.document().parameters;
        }

        /** Change the group count and optional fields. Returns true if they changed. */
        boolean setLayout(int groupCount, boolean units, boolean description) {
            int[] wanted = units && description ? new int[] { NAME, TYPE, UNITS, VALUE, DESCRIPTION }
                    : units ? new int[] { NAME, TYPE, UNITS, VALUE }
                            : description ? new int[] { NAME, TYPE, VALUE, DESCRIPTION } : new int[] { NAME, TYPE, VALUE };
            groupCount = Math.max(1, groupCount);
            if (groupCount == groups && java.util.Arrays.equals(wanted, fields))
                return false;
            groups = groupCount;
            fields = wanted;
            fireTableStructureChanged();
            return true;
        }

        int groupCount() {
            return groups;
        }

        int fieldCount() {
            return fields.length;
        }

        int fieldAt(int column) {
            return column >= 0 ? fields[column % fields.length] : -1;
        }

        int rowsPerGroup() {
            int count = definitions().size();
            return count > 0 ? (count + groups - 1) / groups : 0;
        }

        public int getRowCount() {
            return rowsPerGroup();
        }

        public int getColumnCount() {
            return groups * fields.length;
        }

        /** The parameter in a cell, or -1 for an empty cell. */
        int parameter(int row, int column) {
            int parameter = column / fields.length * rowsPerGroup() + row;
            return parameter < definitions().size() ? parameter : -1;
        }

        /** The cell showing a parameter's value, as {row, column}. */
        int[] valueCell(int parameter) {
            int per = rowsPerGroup();
            int position = 0;
            for (int i = 0; i < fields.length; i++)
                if (fields[i] == VALUE)
                    position = i;
            return new int[] { parameter % per, parameter / per * fields.length + position };
        }

        boolean isGroupEnd(int column) {
            return (column + 1) % fields.length == 0 && column + 1 < getColumnCount();
        }

        int item(int row, int column) {
            return parameter(row, column);
        }

        int element(int row, int column) {
            return 0;
        }

        String raw(int row, int column) {
            int parameter = parameter(row, column);
            if (parameter < 0)
                return "";
            return fieldAt(column) == VALUE ? currentPage().parameters[parameter] : "";
        }

        String stored(int item, int element, int page) {
            return host.document().pages.get(page).parameters[item];
        }

        void store(int item, int element, int page, String text) {
            Document doc = host.document();
            Definition d = doc.parameters.get(item);
            /* A fixed value is shown on every page, and an edit changes the definition. */
            if (d.fixedValue != null) {
                d.fixedValue = text;
                for (Document.Page p : doc.pages)
                    p.parameters[item] = text;
            } else {
                doc.pages.get(page).parameters[item] = text;
            }
        }

        public Object getValueAt(int row, int column) {
            int parameter = parameter(row, column);
            if (parameter < 0)
                return "";
            Definition d = definitions().get(parameter);
            switch (fieldAt(column)) {
                case NAME:
                    return d.name;
                case TYPE:
                    return d.typeName();
                case UNITS:
                    return d.units == null ? "" : d.units;
                case DESCRIPTION:
                    return d.description == null ? "" : d.description;
                default:
                    return Cells.display(currentPage().parameters[parameter], d);
            }
        }

        public boolean isCellEditable(int row, int column) {
            return parameter(row, column) >= 0 && fieldAt(column) == VALUE;
        }

        public String getColumnName(int column) {
            switch (fieldAt(column)) {
                case NAME:
                    return "Name";
                case TYPE:
                    return "Type";
                case UNITS:
                    return "Units";
                case VALUE:
                    return "Value";
                case DESCRIPTION:
                    return "Description";
                default:
                    return "";
            }
        }
    }

    /** The rows of a page's columns, showing only those a row filter passes. */
    static final class ColumnModel extends Base {
        private static final long serialVersionUID = 1L;
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern(" zzz");
        private static final BigDecimal LOWEST = new BigDecimal("-62135596800000");
        private static final BigDecimal HIGHEST = new BigDecimal("253402300799999");
        /** Page rows to show, or <code>null</code> for all of them. */
        private int[] visible;

        ColumnModel(Host host) {
            super(host);
        }

        DefinitionDialogs.Kind kind() {
            return DefinitionDialogs.Kind.COLUMN;
        }

        List<Definition> definitions() {
            return host.page() < 0 ? java.util.Collections.<Definition>emptyList() : host.document().columns;
        }

        void setVisibleRows(int[] rows) {
            visible = rows;
        }

        boolean filtered() {
            return visible != null;
        }

        /** All rows on the page, shown or not. */
        int pageRowCount() {
            Document.Page page = currentPage();
            return page == null || definitions().isEmpty() ? 0 : page.rows;
        }

        int pageRow(int row) {
            return visible == null ? row : visible[row];
        }

        /** The table row showing a page row, or -1 if it is filtered out. */
        int viewRow(int pageRow) {
            if (visible == null)
                return pageRow;
            int found = java.util.Arrays.binarySearch(visible, pageRow);
            return found < 0 ? -1 : found;
        }

        public int getRowCount() {
            return visible == null ? pageRowCount() : visible.length;
        }

        public int getColumnCount() {
            return definitions().size();
        }

        int item(int row, int column) {
            return column;
        }

        int element(int row, int column) {
            return pageRow(row);
        }

        String raw(int row, int column) {
            return currentPage().columns[column][pageRow(row)];
        }

        String stored(int item, int element, int page) {
            return host.document().pages.get(page).columns[item][element];
        }

        void store(int item, int element, int page, String text) {
            host.document().pages.get(page).columns[item][element] = text;
        }

        public Object getValueAt(int row, int column) {
            Definition d = definitions().get(column);
            String text = Cells.display(raw(row, column), d);
            return d.timeMode == Definition.TimeMode.RAW || !d.canDisplayTime() ? text : timeText(text, d.timeMode);
        }

        /** The text that column search looks in: stored values, with enums as they are shown. */
        String searchText(int row, int column) {
            Definition d = definitions().get(column);
            return d.isEnum() ? Cells.display(raw(row, column), d) : raw(row, column);
        }

        /** Format epoch seconds to milliseconds, leaving what cannot be shown as it is. */
        static String timeText(String text, Definition.TimeMode mode) {
            String t = text.trim();
            if (mode == Definition.TimeMode.RAW || t.isEmpty() || !Cells.validDouble(t))
                return text;
            BigDecimal seconds;
            try {
                seconds = new BigDecimal(t);
            } catch (NumberFormatException e) {
                return text;
            }
            BigDecimal milliseconds = seconds.movePointRight(3).setScale(0, RoundingMode.HALF_UP);
            if (milliseconds.compareTo(LOWEST) < 0 || milliseconds.compareTo(HIGHEST) > 0)
                return text;
            Instant instant = Instant.ofEpochMilli(milliseconds.longValue());
            if (mode == Definition.TimeMode.UTC)
                return FORMAT.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)) + " UTC";
            ZonedDateTime local = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
            if (local.getYear() < 1 || local.getYear() > 9999)
                return text;
            return FORMAT.format(local) + ZONE.format(local);
        }
    }

    /** The elements of a page's arrays, one column per array in stored order. */
    static final class ArrayModel extends Base {
        private static final long serialVersionUID = 1L;

        ArrayModel(Host host) {
            super(host);
        }

        DefinitionDialogs.Kind kind() {
            return DefinitionDialogs.Kind.ARRAY;
        }

        List<Definition> definitions() {
            return host.page() < 0 ? java.util.Collections.<Definition>emptyList() : host.document().arrays;
        }

        public int getRowCount() {
            return host.page() < 0 ? 0 : host.document().longestArray(host.page());
        }

        public int getColumnCount() {
            return definitions().size();
        }

        int item(int row, int column) {
            return column;
        }

        int element(int row, int column) {
            return row;
        }

        boolean isPadding(int row, int column) {
            return row >= currentPage().arrays[column].values.length;
        }

        String raw(int row, int column) {
            String[] values = currentPage().arrays[column].values;
            return row < values.length ? values[row] : "";
        }

        String stored(int item, int element, int page) {
            return host.document().pages.get(page).arrays[item].values[element];
        }

        void store(int item, int element, int page, String text) {
            host.document().pages.get(page).arrays[item].values[element] = text;
        }

        public Object getValueAt(int row, int column) {
            return isPadding(row, column) ? "" : Cells.display(raw(row, column), definitions().get(column));
        }
    }

    /** True if text cells in this type are right aligned. */
    static boolean rightAligned(int type) {
        return SDDSUtil.isNumericType(type);
    }
}
