/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import SDDS.java.SDDS.SDDSEnumDefinition;
import SDDS.java.SDDS.SDDSUtil;

/** The definition of one parameter, column or array in the editor's document. */
final class Definition {
    /** How a numeric Time column is shown. This never changes the file. */
    enum TimeMode {
        RAW, LOCAL, UTC
    }

    String name;
    String symbol;
    String units;
    String description;
    String format;
    int type;
    int fieldLength;
    /** A parameter's fixed value as it is shown, or <code>null</code> if it has none. */
    String fixedValue;
    String groupName;
    int dimensions = 1;
    SDDSEnumDefinition enumDefinition;
    TimeMode timeMode = TimeMode.RAW;

    Definition(String name, int type) {
        this.name = name;
        this.type = type;
    }

    Definition copy() {
        Definition d = new Definition(name, type);
        d.symbol = symbol;
        d.units = units;
        d.description = description;
        d.format = format;
        d.fieldLength = fieldLength;
        d.fixedValue = fixedValue;
        d.groupName = groupName;
        d.dimensions = dimensions;
        d.enumDefinition = enumDefinition;
        d.timeMode = timeMode;
        return d;
    }

    String typeName() {
        return SDDSUtil.getTypeName(type);
    }

    boolean isEnum() {
        return type == SDDSUtil.SDDS_ENUM;
    }

    boolean isNumeric() {
        return Cells.numeric(type);
    }

    /** The tooltip describing a definition. */
    String toolTip() {
        StringBuilder tip = new StringBuilder("<html>").append(escape(name)).append(" (").append(typeName()).append(')');
        if (units != null && !units.isEmpty())
            tip.append("<br>Units: ").append(escape(units));
        if (description != null && !description.isEmpty())
            tip.append("<br>").append(escape(description));
        return tip.append("</html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A numeric column with Time in its name, and blank or second units, can show dates. */
    boolean canDisplayTime() {
        if (!isNumeric() || !name.toLowerCase().contains("time"))
            return false;
        if (units == null || units.trim().isEmpty() || units.trim().equals("s"))
            return true;
        String u = units.trim().toLowerCase();
        return u.equals("sec") || u.equals("secs") || u.equals("second") || u.equals("seconds");
    }
}
