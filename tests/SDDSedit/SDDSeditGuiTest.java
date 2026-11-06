/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import SDDS.java.SDDS.*;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

/**
 * Drives SDDSedit in a real window to check its handling of SDDS6 enums. This needs a
 * display, so it is a separate target from the library tests. Run it through
 * <code>make test-gui</code>, which uses a private virtual display.
 */
public class SDDSeditGuiTest {
    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static void equal(String name, Object expected, Object actual) {
        boolean ok = Objects.deepEquals(expected, actual);
        check(name + "\n  expected: " + text(expected) + "\n  actual:   " + text(actual), ok);
    }

    static String text(Object o) {
        return o instanceof int[] ? Arrays.toString((int[]) o) : String.valueOf(o);
    }

    static String[] choices(TableCellEditor editor) {
        if (!(editor instanceof DefaultCellEditor))
            return null;
        Object component = ((DefaultCellEditor) editor).getComponent();
        if (!(component instanceof JComboBox))
            return null;
        JComboBox box = (JComboBox) component;
        String[] items = new String[box.getItemCount()];
        for (int i = 0; i < items.length; i++)
            items[i] = box.getItemAt(i).toString();
        return items;
    }

    public static void main(String[] args) throws Exception {
        String dir = System.getenv("SDDS_TEST_DIR");
        Path work = dir != null ? Paths.get(dir) : Paths.get(System.getProperty("sdds.tests", "tests"), "build/artifacts");
        Files.createDirectories(work);
        String name = work.resolve("editor.sdds").toString();

        SDDSFile f = new SDDSFile(name);
        f.setMode(true);
        f.defineEnumParameter("state", "0:OFF,10:ON,-1:FAULT");
        f.defineEnumParameter("fixedState", null, null, null, null, "0:OFF,10:ON,-1:FAULT", "10");
        f.defineParameter("note", SDDSUtil.SDDS_STRING);
        f.defineEnumArray("arrayState", "0:OFF,10:ON,-1:FAULT", 1);
        f.defineArray("numbers", SDDSUtil.SDDS_DOUBLE, 1);
        f.defineEnumColumn("mode", "s", "u", "column description", null, "0:OFF,10:ON,-1:FAULT", 0);
        f.defineEnumColumn("noZero", "1:01,2:1,3:雪 flake");
        f.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
        f.defineColumn("big", SDDSUtil.SDDS_ULONG64);
        f.defineColumn("c", SDDSUtil.SDDS_CHARACTER);
        f.defineColumn("ld", SDDSUtil.SDDS_LONGDOUBLE);
        for (int page = 1; page <= 2; page++) {
            f.setParameter(0, page == 1 ? 10 : -1, page);
            f.setParameter(2, "page " + page, page);
            f.setArrayDim(page, 0, new int[] { 2 });
            f.setArray(0, new int[] { 10, -1 }, page);
            f.setArrayDim(page, 1, new int[] { 3 });
            f.setArray(1, new double[] { 1, 2, 3 }, page);
            f.setColumn(0, new int[] { 0, 10, -1 }, page, 1);
            f.setColumn(1, new int[] { 1, 2, 3 }, page, 1);
            f.setColumn(2, new double[] { 1.5, 2.5, 3.5 }, page, 1);
            f.setColumn(3, new String[] { "18446744073709551615", "1", "2" }, page, 1);
            f.setColumn(4, new char[] { ' ', 'a', '!' }, page, 1);
            f.setColumn(5, new Object[] { SDDSLongDouble.parse("1.00000000000000000011"), Double.valueOf(2), Double.valueOf(3) }, page, 1);
        }
        check("make the test file: " + f.getErrors(), f.writeFile());

        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                drive(name);
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null)
            throw failure[0];

        if (args.length > 0) {
            Thread.sleep(800);
            Rectangle area = sddsEdit.frame.getBounds();
            BufferedImage image = new Robot().createScreenCapture(area.intersection(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())));
            ImageIO.write(image, "png", new File(args[0]));
        }
        System.out.println(passed + " passed, " + failed + " failed");
        if (args.length == 0 || failed != 0)
            System.exit(failed == 0 ? 0 : 1);
        System.exit(0);
    }

    static int columnOf(String name) {
        for (int i = 0; i < sddsEdit.column_dtm.getColumnCount(); i++)
            if (sddsEdit.column_dtm.getColumnName(i).equals(name))
                return i;
        throw new IllegalStateException(name);
    }

    static void drive(String name) throws Exception {
        sddsEdit frame = new sddsEdit();
        sddsEdit.frame = frame;
        frame.pack();
        frame.setSize(1100, 700);
        frame.setVisible(true);
        frame.loadFile(name);

        // Values show both the label and the code.
        equal("parameter cell", "ON (10)", sddsEdit.parameter_dtm.getValueAt(0, 0));
        equal("fixed parameter cell", "ON (10)", sddsEdit.parameter_dtm.getValueAt(1, 0));
        equal("column cell", "FAULT (-1)", sddsEdit.column_dtm.getValueAt(2, columnOf("mode")));
        equal("array cell", "ON (10)", sddsEdit.array_dtm.getValueAt(0, 0));
        equal("non-ASCII label", "雪 flake (3)", sddsEdit.column_dtm.getValueAt(2, columnOf("noZero")));
        equal("ulong64 cell", "18446744073709551615", sddsEdit.column_dtm.getValueAt(0, columnOf("big")).toString());
        equal("long double cell", "1.00000000000000000011e+00", sddsEdit.column_dtm.getValueAt(0, columnOf("ld")).toString());
        equal("column array stops at its end", null, sddsEdit.array_dtm.getValueAt(2, 0));

        // Editing offers only the states of the dictionary.
        String[] states = { "OFF (0)", "ON (10)", "FAULT (-1)" };
        equal("parameter choices", states, choices(sddsEdit.parameterTable.getCellEditor(0, 0)));
        equal("text parameter is free text", null, choices(sddsEdit.parameterTable.getCellEditor(2, 0)));
        equal("column choices", states, choices(sddsEdit.columnTable.getCellEditor(0, columnOf("mode"))));
        equal("numeric column is free text", null, choices(sddsEdit.columnTable.getCellEditor(0, columnOf("x"))));
        equal("array choices", states, choices(sddsEdit.arrayTable.getCellEditor(0, 0)));

        // Labels that look numeric stay distinct.
        equal("numeric looking choices", new String[] { "01 (1)", "1 (2)", "雪 flake (3)" },
                choices(sddsEdit.columnTable.getCellEditor(0, columnOf("noZero"))));

        // Searches compare the label as text.
        equal("search text", "FAULT", sddsEdit.searchText(2, columnOf("mode")));
        equal("search text of a label like a number", "01", sddsEdit.searchText(0, columnOf("noZero")));
        equal("search text of a number", "1.5", sddsEdit.searchText(0, columnOf("x")));
        check("glob match on a label", SDDSUtil.globMatch(sddsEdit.searchText(2, columnOf("mode")), "FA*"));

        // Editing goes back through the checked setters.
        sddsEdit.parameter_dtm.setValueAt("OFF (0)", 0, 0);
        sddsEdit.column_dtm.setValueAt("ON (10)", 0, columnOf("mode"));
        sddsEdit.array_dtm.setValueAt("OFF (0)", 1, 0);
        check("save the page: " + statusText(), frame.savePage());
        equal("parameter saved", Integer.valueOf(0), sddsEdit.sdds.getParameterEnumCode(0, 1));
        equal("fixed value kept", "10", sddsEdit.sdds.getParameterFixedValue(1));
        equal("column saved", new int[] { 10, 10, -1 }, sddsEdit.sdds.getColumnEnumCodes(0, 1));
        equal("array saved", new int[] { 10, 0 }, sddsEdit.sdds.getArrayEnumCodes(0, 1));
        equal("other page unchanged", Integer.valueOf(-1), sddsEdit.sdds.getParameterEnumCode(0, 2));

        // Moving between pages preserves everything.
        frame.clearPage();
        frame.loadPage(2);
        equal("second page", "FAULT (-1)", sddsEdit.parameter_dtm.getValueAt(0, 0));
        check("save second page", frame.savePage());
        frame.clearPage();
        frame.loadPage(1);
        equal("first page again", "OFF (0)", sddsEdit.parameter_dtm.getValueAt(0, 0));
        equal("character cell kept", "\\040", sddsEdit.column_dtm.getValueAt(0, columnOf("c")));
        check("save first page again", frame.savePage());
        Object[] characters = sddsEdit.sdds.getColumnValues(sddsEdit.sdds.getColumnIndex("c"), 1, false);
        equal("character value survives saving", "\\040", characters[0]);
        Object[] precise = sddsEdit.sdds.getColumnValues(sddsEdit.sdds.getColumnIndex("ld"), 1, false);
        equal("long double survives saving", SDDSLongDouble.parse("1.00000000000000000011"), precise[0]);
        Object[] big = sddsEdit.sdds.getColumnValues(sddsEdit.sdds.getColumnIndex("big"), 1, false);
        equal("ulong64 survives saving", "18446744073709551615", big[0].toString());

        // New rows start at code 0 when it is mapped, and otherwise blank.
        equal("new cell with code 0", "OFF (0)", sddsEdit.newColumnCell(columnOf("mode")));
        equal("new cell without code 0", "", sddsEdit.newColumnCell(columnOf("noZero")));
        equal("new cell of another type", "", sddsEdit.newColumnCell(columnOf("x")));
        equal("new array element inside the array", "OFF (0)", sddsEdit.newArrayCell(0, 1));
        equal("new array element at its end", "OFF (0)", sddsEdit.newArrayCell(0, 2));
        equal("no gap in an array", "", sddsEdit.newArrayCell(0, 3));

        // A blank value can be carried between pages, and Save names the cell.
        Object[] row = new Object[sddsEdit.column_dtm.getColumnCount()];
        for (int i = 0; i < row.length; i++)
            row[i] = sddsEdit.newColumnCell(i);
        row[columnOf("x")] = "4.5";
        row[columnOf("big")] = "3";
        row[columnOf("c")] = "z";
        row[columnOf("ld")] = "4";
        sddsEdit.column_dtm.insertRow(3, row);
        check("a blank state does not block moving around: " + statusText(), frame.savePage());
        long before = new File(name).length();
        byte[] original = Files.readAllBytes(Paths.get(name));
        check("save is refused", !sddsEdit.sdds.writeFile());
        String why = sddsEdit.sdds.getErrors();
        check("save names the cell: " + why, why.contains("column noZero") && why.contains("page 1 row 4"));
        check("the file was not harmed", Arrays.equals(original, Files.readAllBytes(Paths.get(name))) && before == original.length);
        sddsEdit.column_dtm.setValueAt("1 (2)", 3, columnOf("noZero"));
        check("save the chosen state", frame.savePage());
        equal("row added", new int[] { 1, 2, 3, 2 }, sddsEdit.sdds.getColumnEnumCodes(1, 1));
        equal("default state added", new int[] { 10, 10, -1, 0 }, sddsEdit.sdds.getColumnEnumCodes(0, 1));

        // Pages the editor inserts have the same behavior.
        check("insert a page", sddsEdit.sdds.insertPage(1));
        frame.clearPage();
        frame.loadPage(1);
        equal("new page parameter", "OFF (0)", sddsEdit.parameter_dtm.getValueAt(0, 0));
        check("save the new page: " + statusText(), frame.savePage());
        check("delete it again", sddsEdit.sdds.deletePage(1));
        frame.clearPage();
        frame.loadPage(1);

        // Replacing a dictionary goes through checked label conversion.
        int mode = sddsEdit.sdds.getColumnIndex("mode");
        check("a mapping losing a label is refused", !frame.changeEnumMapping(0, mode, "7:OFF,8:ON"));
        equal("the old mapping is still there", "0:OFF,10:ON,-1:FAULT", sddsEdit.sdds.getColumnEnumValues(mode));
        check("a valid mapping is accepted: " + statusText(), frame.changeEnumMapping(0, mode, "7:OFF,8:ON,9:FAULT"));
        equal("codes follow the labels", new int[] { 8, 8, 9, 7 }, sddsEdit.sdds.getColumnEnumCodes(mode, 1));
        equal("the table follows", "ON (8)", sddsEdit.column_dtm.getValueAt(0, columnOf("mode")));
        equal("the new choices", new String[] { "OFF (7)", "ON (8)", "FAULT (9)" }, choices(sddsEdit.columnTable.getCellEditor(0, columnOf("mode"))));
        equal("other pages follow", new int[] { 7, 8, 9 }, sddsEdit.sdds.getColumnEnumCodes(mode, 2));
        check("parameter mapping", frame.changeEnumMapping(1, 0, "1:OFF,2:ON,3:FAULT"));
        equal("parameter follows", Integer.valueOf(1), sddsEdit.sdds.getParameterEnumCode(0, 1));
        check("array mapping", frame.changeEnumMapping(2, 0, "1:OFF,2:ON,3:FAULT"));
        equal("array follows", new int[] { 2, 1 }, sddsEdit.sdds.getArrayEnumCodes(0, 1));

        check("fixed parameter mapping", frame.changeEnumMapping(1, 1, "5:OFF,6:ON,7:FAULT"));
        equal("a fixed code follows its label", "6", sddsEdit.sdds.getParameterFixedValue(1));

        // Mappings survive a save.
        String saved = name.replace(".sdds", "-saved.sdds");
        sddsEdit.sdds.setFileName(saved);
        check("save: " + statusText(), frame.savePage() && sddsEdit.sdds.writeFile());
        SDDSFile back = new SDDSFile(saved);
        check("read the save: " + back.getErrors(), back.readFile());
        equal("mapping saved", "7:OFF,8:ON,9:FAULT", back.getColumnEnumValues(back.getColumnIndex("mode")));
        equal("non-ASCII mapping saved", "1:01,2:1,3:雪 flake", back.getColumnEnumValues(back.getColumnIndex("noZero")));
        equal("fixed saved", "6", back.getParameterFixedValue(1));
        equal("version saved", 6, back.layoutVersion);
        equal("binary kept", false, back.asciiFile);

        // The attribute dialog follows the type of the element.
        sddsEditAttributesDialog dialog = new sddsEditAttributesDialog(frame, 2);
        dialog.setInsertMode(2);
        check("string by default", !dialog.fields[7].isEnabled() && dialog.fields[5].isEnabled());
        dialog.setType(SDDSUtil.SDDS_ENUM);
        check("enum has values and no format or width", dialog.fields[7].isEnabled() && !dialog.fields[4].isEnabled() && !dialog.fields[5].isEnabled());
        dialog.setEnumValues("0:A");
        equal("mapping field", "0:A", dialog.fields[7].getText());
        dialog.setType(SDDSUtil.SDDS_DOUBLE);
        check("double button chosen", dialog.typeButtons[0].isSelected() && !dialog.fields[7].isEnabled() && dialog.fields[4].isEnabled());
        dialog.setType(SDDSUtil.SDDS_CHARACTER);
        check("character button chosen", dialog.typeButtons[9].isSelected());
        dialog.setType(SDDSUtil.SDDS_LONGDOUBLE);
        check("longdouble button chosen", dialog.typeButtons[11].isSelected());
        dialog.dispose();
        sddsEditAttributesDialog parameterDialog = new sddsEditAttributesDialog(frame, 1);
        parameterDialog.setInsertMode(1);
        parameterDialog.setType(SDDSUtil.SDDS_ENUM);
        check("an enum parameter can have a fixed value", parameterDialog.fields[5].isEnabled());
        parameterDialog.dispose();

        // Back to the original file for the picture.
        frame.loadFile(name);
    }

    static String statusText() {
        return sddsEdit.textArea.getText();
    }
}
