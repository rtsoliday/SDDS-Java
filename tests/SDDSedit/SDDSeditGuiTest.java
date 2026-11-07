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
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;

/**
 * Drives SDDSedit in a real window. This needs a display, so it is a separate target
 * from the library tests. Run it through <code>make test-gui</code>, which uses a
 * private virtual display. An optional argument names a PNG file for a screenshot.
 */
public class SDDSeditGuiTest {
    static int passed = 0, failed = 0;
    static sddsEdit editor;
    static Path work;

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
        if (o instanceof int[])
            return Arrays.toString((int[]) o);
        if (o instanceof Object[])
            return Arrays.toString((Object[]) o);
        return String.valueOf(o);
    }

    static String makeFile(String file) {
        String name = work.resolve(file).toString();
        SDDSFile f = new SDDSFile(name);
        f.setMode(true);
        f.setDescription("Beam position study", "orbit");
        f.defineParameter("Energy", null, "GeV", "beam energy", null, SDDSUtil.SDDS_DOUBLE, null);
        f.defineParameter("Station", null, null, "station name", null, SDDSUtil.SDDS_STRING, null);
        f.defineParameter("RunNumber", SDDSUtil.SDDS_LONG);
        f.defineEnumParameter("Mode", "0:OFF,10:ON,-1:FAULT");
        f.defineEnumParameter("FixedMode", null, null, null, null, "0:OFF,10:ON,-1:FAULT", "10");
        f.defineParameter("Fixed", null, null, null, null, SDDSUtil.SDDS_LONG, "7");
        f.defineArray("gain", SDDSUtil.SDDS_DOUBLE, 1);
        f.defineArray("tag", SDDSUtil.SDDS_STRING, 1);
        f.defineArray("grid", SDDSUtil.SDDS_LONG, 2);
        f.defineColumn("Time", null, "s", null, null, SDDSUtil.SDDS_DOUBLE, 0);
        f.defineColumn("x", null, "mm", null, null, SDDSUtil.SDDS_DOUBLE, 0);
        f.defineColumn("y", null, "mm", null, null, SDDSUtil.SDDS_FLOAT, 0);
        f.defineColumn("index", SDDSUtil.SDDS_LONG);
        f.defineColumn("name", SDDSUtil.SDDS_STRING);
        f.defineColumn("flag", SDDSUtil.SDDS_CHARACTER);
        f.defineEnumColumn("state", "0:OFF,10:ON,-1:FAULT");
        f.defineColumn("big", SDDSUtil.SDDS_ULONG64);
        f.defineColumn("ld", SDDSUtil.SDDS_LONGDOUBLE);
        for (int page = 1; page <= 3; page++) {
            f.setParameter(0, 7.25, page);
            f.setParameter(1, "S" + page, page);
            f.setParameter(2, 1000 + page, page);
            f.setParameter(3, page == 1 ? 10 : -1, page);
            f.setArrayDim(page, 0, new int[] { 3 });
            f.setArray(0, new double[] { 1.5, 2.5, 3.5 }, page);
            f.setArrayDim(page, 1, new int[] { 2 });
            f.setArray(1, new String[] { "alpha", "beta" }, page);
            f.setArrayDim(page, 2, new int[] { 2, 2 });
            f.setArray(2, new int[] { 1, 2, 3, 4 }, page);
            f.setColumn(0, new double[] { 1.7e9, 1700000060, 1700000120, 1700000180 }, page, 1);
            f.setColumn(1, new double[] { 0.1, 0.25, -0.5, 0.001 }, page, 1);
            f.setColumn(2, new float[] { 1, 2, 3, 4 }, page, 1);
            f.setColumn(3, new int[] { 1, 2, 3, 4 }, page, 1);
            f.setColumn(4, new String[] { "BPM 10", "BPM 9", "BPM 2", "BPM 1" }, page, 1);
            f.setColumn(5, new char[] { 'a', 'b', 'c', 'd' }, page, 1);
            f.setColumn(6, new int[] { 0, 10, -1, 0 }, page, 1);
            f.setColumn(7, new String[] { "18446744073709551615", "9007199254740993", "2", "3" }, page, 1);
            f.setColumn(8, new Object[] { SDDSLongDouble.parse("1.00000000000000000011"), Double.valueOf(2),
                    Double.valueOf(3), Double.valueOf(4) }, page, 1);
        }
        check("make the test file: " + f.getErrors(), f.writeFile());
        return name;
    }

    public static void main(String[] args) throws Exception {
        /* Run as a child by testNoDisplay: a report that cannot show its dialog must not repeat. */
        if (args.length > 0 && args[0].equals("--report-failure")) {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> sddsEdit.reportFailure(error));
            sddsEdit.reportFailure(new IllegalStateException("reported once"));
            Thread.sleep(1500);
            System.exit(0);
        }
        String dir = System.getenv("SDDS_TEST_DIR");
        work = dir != null ? Paths.get(dir) : Paths.get(System.getProperty("sdds.tests", "tests"), "build/artifacts");
        Files.createDirectories(work);
        final String name = makeFile("editor.sdds");

        final Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Theme.install();
                sddsEdit.quietDialogs = true;
                editor = new sddsEdit();
                editor.setVisible(true);
                editor.validate();
                drive(name);
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null)
            throw failure[0];

        testRealInput();
        testNoDisplay();

        if (args.length > 0) {
            Thread.sleep(800);
            Rectangle area = editor.getBounds();
            BufferedImage image = new Robot()
                    .createScreenCapture(area.intersection(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())));
            ImageIO.write(image, "png", new File(args[0]));
        }
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Run the program with no usable display: it must stop by itself and say why, briefly. */
    static String[] runChild(String display, String... command) throws Exception {
        List<String> line = new ArrayList<String>();
        line.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        line.addAll(Arrays.asList(command));
        ProcessBuilder builder = new ProcessBuilder(line);
        if (display == null)
            builder.environment().remove("DISPLAY");
        else
            builder.environment().put("DISPLAY", display);
        builder.environment().remove("WAYLAND_DISPLAY");
        Path log = work.resolve("child.log");
        Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        long size = Files.size(log);
        String text = size < 100000 ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "(" + size + " bytes)";
        return new String[] { finished ? String.valueOf(process.exitValue()) : "still running", text };
    }

    static void testNoDisplay() throws Exception {
        String classes = System.getProperty("java.class.path");
        String[][] cases = { { null, "-cp", classes, "SDDS.java.SDDSedit.sddsEdit" },
                { ":98765", "-cp", classes, "SDDS.java.SDDSedit.sddsEdit" },
                { ":98765", "-Djava.awt.headless=true", "-cp", classes, "SDDS.java.SDDSedit.sddsEdit" } };
        for (String[] c : cases) {
            String[] result = runChild(c[0], Arrays.copyOfRange(c, 1, c.length));
            String label = "with DISPLAY=" + c[0] + (c.length > 4 ? " and headless" : "");
            equal(label + " the editor exits with an error", "1", result[0]);
            check(label + " it explains: " + result[1], result[1].contains("needs a graphical display"));
            check(label + " in a few lines: " + result[1], result[1].split("\n").length <= 3);
        }
        String[] report = runChild(null, "-Djava.awt.headless=true", "-cp", classes, "SDDS.java.SDDSedit.SDDSeditGuiTest", "--report-failure");
        equal("a failure report ends", "0", report[0]);
        check("and does not repeat: " + report[1].length() + " characters",
                report[1].split("reported once", -1).length == 2 && !report[1].contains("HeadlessException"));
    }

    /** Run something on the Swing thread and return what it found. */
    static <T> T onSwing(java.util.concurrent.Callable<T> work) throws Exception {
        final Object[] result = new Object[1];
        final Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                result[0] = work.call();
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null)
            throw failure[0];
        @SuppressWarnings("unchecked")
        T value = (T) result[0];
        return value;
    }

    static void moveToCell(Robot robot, CellTable table, int row, int column) throws Exception {
        java.awt.Point point = onSwing(() -> {
            Rectangle r = table.getCellRect(row, column, false);
            java.awt.Point p = new java.awt.Point(r.x + r.width / 2, r.y + r.height / 2);
            SwingUtilities.convertPointToScreen(p, table);
            return p;
        });
        robot.mouseMove(point.x, point.y);
    }

    static void clickCell(Robot robot, CellTable table, int row, int column) throws Exception {
        moveToCell(robot, table, row, column);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(120);
        robot.waitForIdle();
    }

    static void press(Robot robot, int... keys) {
        for (int key : keys)
            robot.keyPress(key);
        for (int i = keys.length - 1; i >= 0; i--)
            robot.keyRelease(keys[i]);
        robot.waitForIdle();
        robot.delay(60);
        robot.waitForIdle();
    }

    static void chooseMenu(int menu, String text) {
        SwingUtilities.invokeLater(() -> {
            javax.swing.JMenu m = editor.getJMenuBar().getMenu(menu);
            for (int i = 0; i < m.getItemCount(); i++)
                if (m.getItem(i) != null && text.equals(m.getItem(i).getText()))
                    m.getItem(i).doClick();
        });
    }

    static <T> T findIn(java.awt.Container c, Class<T> type) {
        for (java.awt.Component k : c.getComponents()) {
            if (type.isInstance(k))
                return type.cast(k);
            if (k instanceof java.awt.Container) {
                T found = findIn((java.awt.Container) k, type);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    /* Answers by value, because the standard buttons are labeled in the user's language. */
    static void answer(Robot robot, java.awt.Dialog dialog, Object value) throws Exception {
        final javax.swing.JOptionPane pane = findIn(dialog, javax.swing.JOptionPane.class);
        onSwing(() -> {
            pane.setValue(value);
            return null;
        });
        robot.delay(150);
        robot.waitForIdle();
    }

    static void pickFile(Robot robot, java.awt.Dialog dialog, String path) throws Exception {
        final javax.swing.JFileChooser chooser = findIn(dialog, javax.swing.JFileChooser.class);
        onSwing(() -> {
            chooser.setSelectedFile(new File(path));
            chooser.approveSelection();
            return null;
        });
        robot.delay(150);
        robot.waitForIdle();
    }

    /* Real file dialogs and prompts, which quiet mode does not reach. */
    static void testFileDialogs(Robot robot) throws Exception {
        sddsEdit.quietDialogs = false;
        try {
            String out = work.resolve("dialogs.sdds").toString();
            Files.deleteIfExists(Paths.get(out));
            chooseMenu(0, "Save as...");
            pickFile(robot, waitForDialog(robot), out);
            check("Save as writes the chosen file", Files.exists(Paths.get(out)));
            equal("and it becomes the document's path", out, onSwing(() -> editor.doc.path));
            long stamp = Files.getLastModifiedTime(Paths.get(out)).toMillis();
            robot.delay(30);
            chooseMenu(0, "Save as...");
            pickFile(robot, waitForDialog(robot), out);
            java.awt.Dialog ask = waitForDialog(robot);
            check("replacing a file asks first", ask != null && findIn(ask, javax.swing.JOptionPane.class) != null);
            answer(robot, ask, Integer.valueOf(javax.swing.JOptionPane.NO_OPTION));
            equal("declining keeps the file", stamp, Files.getLastModifiedTime(Paths.get(out)).toMillis());

            String csv = work.resolve("dialogs.csv").toString();
            Files.deleteIfExists(Paths.get(csv));
            chooseMenu(0, "Export CSV");
            pickFile(robot, waitForDialog(robot), csv);
            java.awt.Dialog refused = waitForDialog(robot);
            String message = String.valueOf(findIn(refused, javax.swing.JOptionPane.class).getMessage());
            check("CSV explains why enums stop it: " + message, message.contains("Convert enum"));
            equal("under the Qt editor's title", "Enum export", refused.getTitle());
            answer(robot, refused, Integer.valueOf(javax.swing.JOptionPane.OK_OPTION));
            check("and writes nothing", !Files.exists(Paths.get(csv)));

            onSwing(() -> {
                editor.columnModel.setValueAt("5", 0, 1);
                return null;
            });
            chooseMenu(0, "Open");
            java.awt.Dialog prompt = waitForDialog(robot);
            check("unsaved changes are asked about", prompt != null && findIn(prompt, javax.swing.JOptionPane.class) != null);
            answer(robot, prompt, "Cancel");
            check("Cancel keeps them", onSwing(() -> editor.dirty));
            onSwing(() -> {
                editor.undo();
                editor.dirty = false;
                return null;
            });
        } finally {
            sddsEdit.quietDialogs = true;
        }
    }

    static java.awt.Dialog waitForDialog(Robot robot) throws Exception {
        for (int i = 0; i < 100; i++) {
            java.awt.Dialog found = onSwing(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows())
                    if (w instanceof java.awt.Dialog && w.isShowing() && ((java.awt.Dialog) w).isModal())
                        return (java.awt.Dialog) w;
                return null;
            });
            if (found != null) {
                robot.delay(200);
                robot.waitForIdle();
                return found;
            }
            robot.delay(50);
        }
        return null;
    }

    /** Real mouse and keyboard events, which go through focus handling and key bindings. */
    static void testRealInput() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(20);
        final CellTable table = editor.columnTable;
        int index = onSwing(() -> column("index"));
        clickCell(robot, table, 1, index);
        check("a real click edits", onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_4, KeyEvent.VK_ENTER);
        equal("typing replaces the selected text", "4", onSwing(() -> cell(1, "index")));
        check("Enter closes the editor", !onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        equal("Ctrl+Z undoes", "2", onSwing(() -> cell(1, "index")));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_Z);
        equal("Ctrl+Shift+Z redoes", "4", onSwing(() -> cell(1, "index")));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_Z);

        press(robot, KeyEvent.VK_DOWN);
        equal("an arrow key moves the current cell", 2, onSwing(() -> table.getCurrentRow()));
        check("without editing", !onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_7);
        check("a typed key starts an edit", onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_ESCAPE);
        check("Escape cancels it", !onSwing(() -> table.isEditing()));
        equal("and keeps the value", "3", onSwing(() -> cell(2, "index")));
        check("the first Escape keeps the selection", onSwing(() -> !table.selectedCells().isEmpty()));
        press(robot, KeyEvent.VK_ESCAPE);
        check("a second Escape clears it", onSwing(() -> table.selectedCells().isEmpty()));

        clickCell(robot, table, 3, index);
        press(robot, KeyEvent.VK_ESCAPE);
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_C);
        check("Ctrl+C does not start an edit", !onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_DELETE);
        equal("Delete blanks the cell", "", onSwing(() -> cell(3, "index")));
        check("and does not start an edit", !onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        equal("undo the delete", "4", onSwing(() -> cell(3, "index")));

        clickCell(robot, table, 1, index);
        press(robot, KeyEvent.VK_ESCAPE);
        press(robot, KeyEvent.VK_ENTER);
        check("Enter on a selected cell does not edit", !onSwing(() -> table.isEditing()));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        equal("Ctrl+A selects all", onSwing(() -> table.getRowCount() * table.getColumnCount()), onSwing(() -> table.selectedCells().size()));
        clickCell(robot, table, 1, index);
        press(robot, KeyEvent.VK_ESCAPE);

        /* Keys in other fields stay there. */
        onSwing(() -> {
            editor.columnModel.setValueAt("5", 1, index);
            return null;
        });
        java.awt.Point field = onSwing(() -> {
            java.awt.Point p = new java.awt.Point(editor.columnSearch.getWidth() / 2, editor.columnSearch.getHeight() / 2);
            SwingUtilities.convertPointToScreen(p, editor.columnSearch);
            return p;
        });
        robot.mouseMove(field.x, field.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        press(robot, KeyEvent.VK_Q);
        equal("the search box takes typing", "q", onSwing(() -> editor.columnSearch.getText()));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        equal("Ctrl+Z undoes the typing", "", onSwing(() -> editor.columnSearch.getText()));
        equal("and not the document", "5", onSwing(() -> cell(1, "index")));
        press(robot, KeyEvent.VK_DELETE);
        equal("Delete there leaves the cells alone", "5", onSwing(() -> cell(1, "index")));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_O);
        check("Ctrl+Shift+O hides the outline from a text field", !onSwing(() -> editor.outline.isVisible()));
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_O);
        check("and shows it again", onSwing(() -> editor.outline.isVisible()));
        onSwing(() -> {
            editor.undo();
            return null;
        });

        /* F-004: digits typed into a dialog's number field stay in order, and one Enter accepts them. */
        final Integer[] typedRows = new Integer[1];
        SwingUtilities.invokeLater(() -> typedRows[0] = EditorDialogs.number(editor, "Insert Rows", "Number of rows", 1, 1, 1000000));
        waitForDialog(robot);
        press(robot, KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        press(robot, KeyEvent.VK_2);
        press(robot, KeyEvent.VK_5);
        press(robot, KeyEvent.VK_0);
        press(robot, KeyEvent.VK_ENTER);
        robot.delay(200);
        robot.waitForIdle();
        equal("typing 250 gives 250", Integer.valueOf(250), onSwing(() -> typedRows[0]));
        SwingUtilities.invokeLater(() -> typedRows[0] = EditorDialogs.number(editor, "Insert Rows", "Number of rows", 1, 1, 1000000));
        waitForDialog(robot);
        press(robot, KeyEvent.VK_3);
        press(robot, KeyEvent.VK_7);
        press(robot, KeyEvent.VK_ENTER);
        robot.delay(200);
        robot.waitForIdle();
        equal("typing straight away replaces the number", Integer.valueOf(37), onSwing(() -> typedRows[0]));

        /* F-005: the definition dialogs block an open array viewer. */
        onSwing(() -> {
            editor.openArrayViewer(0);
            return null;
        });
        check("a viewer belongs to the main window", onSwing(() -> editor.viewers().get(0).getOwner() == editor));
        SwingUtilities.invokeLater(() -> editor.insert(DefinitionDialogs.Kind.COLUMN));
        java.awt.Dialog insertDialog = waitForDialog(robot);
        check("the insert dialog opens", insertDialog != null);
        equal("and blocks every other window", java.awt.Dialog.ModalityType.APPLICATION_MODAL, insertDialog.getModalityType());
        onSwing(() -> {
            insertDialog.dispose();
            return null;
        });
        robot.delay(100);
        onSwing(() -> {
            for (ArrayViewer viewer : new ArrayList<ArrayViewer>(editor.viewers()))
                viewer.dispose();
            return null;
        });

        testFileDialogs(robot);

        /* Closing Search keeps what was typed into its match. */
        onSwing(() -> {
            EditorDialogs.SearchReplace dialog = new EditorDialogs.SearchReplace(editor, editor, editor.columnModel, column("name"),
                    "Search Column", false, true);
            dialog.setVisible(true);
            editor.focusMatch(editor.columnModel, 0, column("name"), 0, 3);
            ((JTextField) table.getEditorComponent()).setText("typed");
            dialog.dispose();
            return null;
        });
        robot.waitForIdle();
        robot.delay(100);
        robot.waitForIdle();
        equal("closing Search commits the edit", "typed", onSwing(() -> cell(0, "name")));
        onSwing(() -> {
            editor.undo();
            return null;
        });
        clickCell(robot, table, 2, index);
        press(robot, KeyEvent.VK_ESCAPE);

        /* A drag selects a range and does not edit. */
        moveToCell(robot, table, 0, index);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        moveToCell(robot, table, 2, index + 1);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(100);
        equal("a drag selects a range", 6, onSwing(() -> table.selectedCells().size()));
        check("a drag does not edit", !onSwing(() -> table.isEditing()));
        onSwing(() -> {
            table.clearCellSelection();
            return null;
        });
    }

    static int column(String name) {
        for (int i = 0; i < editor.doc.columns.size(); i++)
            if (editor.doc.columns.get(i).name.equals(name))
                return i;
        throw new IllegalArgumentException(name);
    }

    static String cell(int row, String name) {
        return String.valueOf(editor.columnModel.getValueAt(row, column(name)));
    }

    static String[] columnTexts(String name) {
        String[] out = new String[editor.columnModel.getRowCount()];
        for (int i = 0; i < out.length; i++)
            out[i] = cell(i, name);
        return out;
    }

    static int[] parameterValueCell(int parameter) {
        return editor.parameterModel.valueCell(parameter);
    }

    static String parameter(int index) {
        int[] c = parameterValueCell(index);
        return String.valueOf(editor.parameterModel.getValueAt(c[0], c[1]));
    }

    static void mouse(CellTable table, int id, int row, int column, int modifiers, int button, int clicks) {
        Rectangle r = table.getCellRect(row, column, false);
        table.dispatchEvent(new MouseEvent(table, id, System.currentTimeMillis(), modifiers, r.x + 3, r.y + 3, clicks, false, button));
    }

    static void click(CellTable table, int row, int column, int modifiers) {
        int down = modifiers | InputEvent.BUTTON1_DOWN_MASK;
        mouse(table, MouseEvent.MOUSE_PRESSED, row, column, down, MouseEvent.BUTTON1, 1);
        mouse(table, MouseEvent.MOUSE_RELEASED, row, column, modifiers, MouseEvent.BUTTON1, 1);
        mouse(table, MouseEvent.MOUSE_CLICKED, row, column, modifiers, MouseEvent.BUTTON1, 1);
    }

    static void drive(String name) throws Exception {
        testLoad(name);
        testCellEdits();
        testSelection();
        testCopyPaste();
        testFormulas();
        testRowFilter();
        testRows();
        testColumnSearch();
        testDefinitions();
        testPages();
        testArrays();
        testTimeDisplay();
        testSaving(name);
        testReviewFindings(name);
        testAuditFindings(name);
        testAuditMedium(name);
        testAuditLow(name);
        testEdgeFiles();
        testExpressionLanguage();
        testNumberText();
        testPlotArguments();
        testLargeGridLayout();
        editor.loadFile(name);
    }

    static void testLoad(String name) {
        check("the file loads", editor.loadFile(name));
        equal("title", "editor.sdds - SDDS Editor", editor.getTitle());
        equal("parameter count", 6, editor.doc.parameters.size());
        equal("column count", 9, editor.doc.columns.size());
        equal("array count", 3, editor.doc.arrays.size());
        equal("pages", 3, editor.pageCombo.getItemCount());
        equal("version", 6, editor.doc.version);
        check("binary file shows binary", editor.binaryButton.isSelected() && !editor.asciiButton.isSelected());
        equal("status", "Saved", editor.modifiedLabel.getText());
        equal("message", "Loaded " + name, editor.statusMessage.getText());
        equal("chip", "9 \u00d7 4 rows", editor.columnPanel.chip.getText());
        equal("parameter chip", "6", editor.parameterPanel.chip.getText());
        equal("shortest double keeps Qt's form", "1.7e+09", cell(0, "Time"));
        equal("exact seconds", "1700000060", cell(1, "Time"));
        equal("double", "0.001", cell(3, "x"));
        equal("float", "1", cell(0, "y"));
        equal("string", "BPM 1", cell(3, "name"));
        equal("character", "a", cell(0, "flag"));
        equal("enum label and code", "ON (10)", cell(1, "state"));
        equal("negative enum code", "FAULT (-1)", cell(2, "state"));
        equal("ulong64", "18446744073709551615", cell(0, "big"));
        equal("long double keeps digits", "1.0000000000000000001", cell(0, "ld"));
        equal("parameter enum", "ON (10)", parameter(3));
        equal("fixed enum", "ON (10)", parameter(4));
        equal("fixed value", "7", parameter(5));
        equal("array", "2.5", String.valueOf(editor.arrayModel.getValueAt(1, 0)));
        check("past the end of a short array", editor.arrayModel.isPadding(2, 1));
        check("padding cannot be edited", !editor.arrayModel.isCellEditable(2, 1));
    }

    static void testCellEdits() {
        int x = column("x"), state = column("state"), index = column("index");
        editor.columnModel.setValueAt("2.50", 0, x);
        equal("edit shows the shortest form", "2.5", cell(0, "x"));
        check("edit marks modified", editor.dirty);
        equal("modified status", "Modified", editor.modifiedLabel.getText());
        equal("undo text", "Edit Cell", editor.undo.undoText());
        editor.undo();
        equal("undo", "0.1", cell(0, "x"));
        editor.redo();
        equal("redo", "2.5", cell(0, "x"));
        editor.undo();

        editor.columnModel.setValueAt("nonsense", 0, index);
        equal("invalid number is refused", "1", cell(0, "index"));
        editor.columnModel.setValueAt("99999999999", 0, index);
        equal("overflow is refused", "1", cell(0, "index"));
        editor.columnModel.setValueAt("1.5", 0, index);
        equal("fraction in an integer is refused", "1", cell(0, "index"));
        editor.columnModel.setValueAt("ab", 0, column("flag"));
        equal("two characters are refused", "a", cell(0, "flag"));
        editor.columnModel.setValueAt("MISSING", 0, state);
        equal("undeclared label is refused", "OFF (0)", cell(0, "state"));
        editor.columnModel.setValueAt("FAULT", 0, state);
        equal("declared label is accepted", "FAULT (-1)", cell(0, "state"));
        editor.undo();

        int[] fixed = parameterValueCell(5);
        editor.parameterModel.setValueAt("8", fixed[0], fixed[1]);
        equal("fixed value edit changes the definition", "8", editor.doc.parameters.get(5).fixedValue);
        equal("every page shows it", "8", editor.doc.pages.get(2).parameters[5]);
        editor.undo();
        equal("undo restores it", "7", editor.doc.parameters.get(5).fixedValue);
    }

    static void testSelection() throws Exception {
        CellTable table = editor.columnTable;
        click(table, 1, 1, 0);
        check("a click opens the editor when the button is released", table.isEditing());
        check("a text editor opened", table.getEditorComponent() instanceof JTextField);
        JTextField field = (JTextField) table.getEditorComponent();
        equal("the editor holds the stored text", "0.25", field.getText());
        equal("its text is selected", "0.25", field.getSelectedText());
        field.setText("0.75");
        table.finishEditing();
        equal("finishing commits the edit", "0.75", cell(1, "x"));
        editor.undo();

        click(table, 2, 6, 0);
        check("an enum opens a combo box", table.getEditorComponent() instanceof JComboBox);
        equal("with every label", 3, ((JComboBox<?>) table.getEditorComponent()).getItemCount());
        table.removeEditor();

        table.clearCellSelection();
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        click(table, 0, 0, ctrl);
        click(table, 2, 1, ctrl);
        check("Ctrl-click does not edit", !table.isEditing());
        check("first of a disconnected selection", table.isCellSelected(0, 0));
        check("second of a disconnected selection", table.isCellSelected(2, 1));
        check("cells between are not selected", !table.isCellSelected(1, 0) && !table.isCellSelected(1, 1));
        equal("two cells", 2, table.selectedCells().size());
        click(table, 2, 1, ctrl);
        check("Ctrl-click again deselects", !table.isCellSelected(2, 1));
        check("and leaves the other", table.isCellSelected(0, 0));

        table.changeSelection(1, 1, false, false);
        table.changeSelection(3, 2, false, true);
        equal("Shift extends to a rectangle", 6, table.selectedCells().size());
        mouse(table, MouseEvent.MOUSE_PRESSED, 2, 2, InputEvent.BUTTON3_DOWN_MASK, MouseEvent.BUTTON3, 1);
        equal("right-click keeps the selection", 6, table.selectedCells().size());
        equal("right-click moves the current cell", 2, table.getCurrentRow());
        javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
        table.getActionMap().get("escapeTable").actionPerformed(null);
        equal("Escape clears the selection", 0, table.selectedCells().size());
        equal("and the current cell", -1, table.getCurrentRow());

        table.selectColumnCells(1, false, false);
        equal("a header click selects a column", 4, table.selectedCells().size());
        check("the header shows it", table.columnHasSelection(1));
        table.selectRowCells(2, false, false);
        equal("a row number selects a row", 9, table.selectedCells().size());
        check("the row number shows it", table.rowHasSelection(2));
        table.clearCellSelection();

        CellTable parameters = editor.parameterTable;
        int[] value = parameterValueCell(0);
        click(parameters, value[0], value[1] - 1, 0);
        check("a click on a label selects the value", parameters.isCellSelected(value[0], value[1]));
        check("but does not edit it", !parameters.isEditing());
        parameters.clearCellSelection();
    }

    static void setClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    static void testCopyPaste() throws Exception {
        CellTable table = editor.columnTable;
        setClipboard("11\t22\n33");
        table.changeSelection(0, 3, false, false);
        editor.pasteInto(table);
        equal("paste fills the cell", "11", cell(0, "index"));
        equal("and the next row", "33", cell(1, "index"));
        equal("a string column takes text", "22", cell(0, "name"));
        equal("undo text", "Paste", editor.undo.undoText());
        editor.undo();
        equal("undo removes the whole paste", "1", cell(0, "index"));
        equal("and the other column", "BPM 10", cell(0, "name"));

        setClipboard("7\tbad\tbad");
        table.changeSelection(0, 2, false, false);
        editor.pasteInto(table);
        equal("an invalid cell is skipped", "1", cell(0, "index"));
        equal("a valid number is pasted", "7", cell(0, "y"));
        equal("valid text is pasted", "bad", cell(0, "name"));
        editor.undo();

        table.changeSelection(0, 3, false, false);
        table.changeSelection(1, 4, true, false);
        editor.copyFrom(table);
        table.changeSelection(2, 3, false, false);
        editor.pasteInto(table);
        equal("the first copied cell lands", "1", cell(2, "index"));
        equal("a copied gap leaves its target alone", "BPM 2", cell(2, "name"));
        equal("the other gap too", "4", cell(3, "index"));
        equal("the second copied cell lands", "BPM 9", cell(3, "name"));
        editor.undo();

        table.clearCellSelection();
        table.changeSelection(0, 1, false, false);
        table.changeSelection(0, 6, true, false);
        editor.clearCellsIn(table);
        equal("delete blanks a number", "", cell(0, "x"));
        equal("an enum cannot be blanked", "OFF (0)", cell(0, "state"));
        check("and a message says so", editor.statusMessage.getText().contains("enum cell"));
        editor.undo();
        equal("undo delete", "0.1", cell(0, "x"));
        table.clearCellSelection();
    }

    static void selectColumn(CellTable table, int column) {
        table.selectColumnCells(column, false, false);
    }

    static void testFormulas() {
        CellTable table = editor.columnTable;
        selectColumn(table, column("index"));
        editor.applySeries(table, "10", "5");
        equal("fill series", new String[] { "10", "15", "20", "25" }, columnTexts("index"));
        editor.undo();

        sddsEdit.lastWarning = null;
        selectColumn(table, column("name"));
        editor.applySeries(table, "1", "1");
        equal("fill refuses text cells", "Selected cells include non-numeric fields.", sddsEdit.lastWarning);

        selectColumn(table, column("big"));
        editor.applyExpressionText(table, "x - 1");
        equal("64-bit integers stay exact", "18446744073709551614", cell(0, "big"));
        equal("above 2^53", "9007199254740992", cell(1, "big"));
        editor.undo();
        sddsEdit.lastWarning = null;
        editor.applyExpressionText(table, "x * 0.5");
        check("rounding a 64-bit integer is refused", sddsEdit.lastWarning != null && sddsEdit.lastWarning.contains("lose precision"));
        equal("and nothing changed", "18446744073709551615", cell(0, "big"));

        selectColumn(table, column("ld"));
        editor.applyExpressionText(table, "x + 0");
        equal("long double keeps its digits", "1.0000000000000000001", cell(0, "ld"));
        editor.applyExpressionText(table, "x + 1");
        equal("long double arithmetic", "3", cell(1, "ld"));
        editor.undo();

        selectColumn(table, column("x"));
        editor.applyExpressionText(table, "a + i * 2");
        equal("anchor and index", new String[] { "0.1", "2.1", "4.1", "6.1" }, columnTexts("x"));
        editor.undo();
        editor.applyExpressionText(table, "row + col + dr");
        equal("position variables", "1", cell(0, "x"));
        equal("row offset", "7", cell(3, "x"));
        editor.undo();

        selectColumn(table, column("name"));
        editor.applyTemplateText(table, "${x}/${i}");
        equal("text formula", "BPM 10/0", cell(0, "name"));
        equal("second row", "BPM 9/1", cell(1, "name"));
        editor.undo();

        selectColumn(table, column("index"));
        sddsEdit.lastWarning = null;
        editor.applyTemplateText(table, "${x}.5");
        check("text that does not fit is refused", sddsEdit.lastWarning != null);
        equal("and nothing changed", "1", cell(0, "index"));
        table.clearCellSelection();
    }

    static void testRowFilter() {
        CellTable table = editor.columnTable;
        editor.setRowFilter("x > 0");
        equal("filter hides rows", 3, table.getRowCount());
        equal("row numbers stay", 3, editor.columnModel.pageRow(2));
        check("toolbar and status follow", editor.rowFilterActive);
        editor.setRowFilter("x > 0 && index != 1");
        equal("and", 2, table.getRowCount());
        editor.setRowFilter("!(x > 0) || row == 0");
        equal("not, or, row", 2, table.getRowCount());
        editor.setRowFilter("state == \"ON\"");
        equal("an enum compares by label", 1, table.getRowCount());
        equal("the right row", "2", String.valueOf(editor.columnModel.pageRow(0) + 1));
        editor.setRowFilter("state == \"10\"");
        equal("not by code", 0, table.getRowCount());
        editor.setRowFilter("big == 18446744073709551615");
        equal("integers compare exactly", 1, table.getRowCount());
        editor.setRowFilter("big > 18446744073709551614");
        equal("just below the top", 1, table.getRowCount());
        editor.setRowFilter("[name] == \"BPM 9\"");
        equal("a quoted name", 1, table.getRowCount());
        editor.setRowFilter("X > 0");
        equal("case falls back when nothing matches exactly", 3, table.getRowCount());
        editor.setRowFilter("index");
        equal("truth of a value", 4, table.getRowCount());

        editor.setRowFilter("x > 0");
        int row = 0;
        editor.columnModel.setValueAt("-5", row, column("x"));
        equal("an edit that fails the filter hides the row", 2, table.getRowCount());
        editor.undo();
        equal("undo shows it again", 3, table.getRowCount());

        sddsEdit.lastWarning = null;
        editor.setRowFilter("nothing > 0");
        check("an unknown name is an error", sddsEdit.lastWarning != null && sddsEdit.lastWarning.contains("Unknown row variable/column 'nothing'"));
        check("and turns the filter off", !editor.rowFilterActive);
        equal("all rows show", 4, table.getRowCount());
        editor.setRowFilter("x ->= 1");
        check("a stray sign is an error", !editor.rowFilterActive);

        editor.setRowFilter("x > 0");
        editor.clearRowFilter();
        equal("clearing shows all rows", 4, table.getRowCount());
        equal("defaults quote odd names", "[a b] > 0", RowFilter.defaultExpression("a b"));
        equal("and row variables", "[row] > 0", RowFilter.defaultExpression("row"));
        equal("plain names stay", "x > 0", RowFilter.defaultExpression("x"));
    }

    static void testRows() {
        CellTable table = editor.columnTable;
        table.changeSelection(1, 0, false, false);
        editor.insertRowsAt(2);
        equal("rows are inserted after the current row", 6, editor.doc.pages.get(0).rows);
        equal("blank number", "", cell(2, "x"));
        equal("a new enum cell starts at code 0", "OFF (0)", cell(2, "state"));
        equal("the old row moved", "-0.5", cell(4, "x"));
        equal("undo text", "Insert 2 Rows", editor.undo.undoText());
        editor.undo();
        equal("undo", 4, editor.doc.pages.get(0).rows);

        table.selectRowCells(1, false, false);
        editor.deleteRows();
        equal("the row is deleted", 3, table.getRowCount());
        equal("the next row moved up", "-0.5", cell(1, "x"));
        editor.undo();
        equal("undo restores the row", "0.25", cell(1, "x"));

        editor.setRowFilter("x < 0");
        table.selectRowCells(0, false, false);
        editor.deleteRows();
        equal("hidden rows are kept", 3, editor.doc.pages.get(0).rows);
        editor.undo();
        editor.clearRowFilter();

        editor.sortColumn(column("x"), true);
        equal("sort ascending", new String[] { "-0.5", "0.001", "0.1", "0.25" }, columnTexts("x"));
        equal("whole rows move", "BPM 2", cell(0, "name"));
        editor.undo();
        editor.sortColumn(column("x"), false);
        equal("sort descending", "0.25", cell(0, "x"));
        editor.undo();
        editor.sortColumn(column("name"), true);
        equal("names sort naturally", new String[] { "BPM 1", "BPM 2", "BPM 9", "BPM 10" }, columnTexts("name"));
        editor.undo();
        editor.sortColumn(column("state"), true);
        equal("enums sort by label", "FAULT (-1)", cell(0, "state"));
        editor.undo();
        equal("undo sort", "0.1", cell(0, "x"));
    }

    static void testColumnSearch() {
        CellTable table = editor.columnTable;
        table.clearCellSelection();
        editor.clearColumnSelection();
        equal("placeholder", "Find in all columns...", editor.columnSearch.placeholder);
        editor.columnSearch.setText("bpm 1");
        editor.findInColumnPanel();
        equal("finds regardless of case", 0, table.getCurrentRow());
        equal("in the name column", column("name"), table.getCurrentColumn());
        editor.findInColumnPanel();
        equal("the next match", 3, table.getCurrentRow());
        editor.findInColumnPanel();
        equal("wraps", 0, table.getCurrentRow());
        equal("a match does not narrow the search", "Find in all columns...", editor.columnSearch.placeholder);
        table.clearCellSelection();
        table.selectColumnCells(column("x"), false, false);
        equal("a selected column narrows it", "Find in x...", editor.columnSearch.placeholder);
        editor.columnSearch.setText("nothing like this");
        editor.findInColumnPanel();
        check("not found says so", editor.statusMessage.getText().contains("not found"));
        editor.columnSearch.setText("");
        table.clearCellSelection();
    }

    static void testDefinitions() {
        List<String> before = new ArrayList<String>();
        for (Definition d : editor.doc.columns)
            before.add(d.name);
        int[] order = new int[before.size()];
        for (int i = 0; i < order.length; i++)
            order[i] = i;
        order[0] = 1;
        order[1] = 0;
        editor.reorder(DefinitionDialogs.Kind.COLUMN, order);
        equal("reordering moves definitions", "x", editor.doc.columns.get(0).name);
        equal("and values", "0.1", String.valueOf(editor.columnModel.getValueAt(0, 0)));
        editor.undo();
        equal("undo reorder", "Time", editor.doc.columns.get(0).name);

        editor.moveParameters(0, 1);
        equal("move a parameter down", "Station", editor.doc.parameters.get(0).name);
        equal("its value moves", "S1", editor.doc.pages.get(0).parameters[0]);
        editor.undo();
        equal("undo move", "Energy", editor.doc.parameters.get(0).name);

        editor.deleteItems(DefinitionDialogs.Kind.COLUMN, Arrays.asList(column("y"), column("flag")));
        equal("columns are deleted", 7, editor.doc.columns.size());
        equal("on every page", 7, editor.doc.pages.get(2).columns.length);
        equal("undo text", "Delete 2 Columns", editor.undo.undoText());
        editor.undo();
        equal("undo delete", 9, editor.doc.columns.size());
        editor.deleteItems(DefinitionDialogs.Kind.PARAMETER, Arrays.asList(1));
        equal("a parameter is deleted", 5, editor.doc.parameters.size());
        editor.undo();
        editor.deleteItems(DefinitionDialogs.Kind.ARRAY, Arrays.asList(1));
        equal("an array is deleted", 2, editor.doc.arrays.size());
        editor.undo();
        equal("undo array delete", "tag", editor.doc.arrays.get(1).name);
    }

    static void testPages() {
        equal("first page", 0, editor.page);
        editor.columnModel.setValueAt("5", 0, column("x"));
        check("something to undo", editor.undo.canUndo());
        editor.showPage(1);
        equal("page two", 1, editor.page);
        check("history does not cross pages", !editor.undo.canUndo());
        equal("page two has its own values", "0.1", cell(0, "x"));
        equal("its parameter", "S2", parameter(1));
        editor.showPage(0);
        equal("the edit stayed on its page", "5", cell(0, "x"));

        editor.clonePage();
        equal("cloning adds a page", 4, editor.doc.pages.size());
        equal("and shows it", 1, editor.page);
        equal("with the same values", "5", cell(0, "x"));
        editor.undo();
        equal("undo clone", 3, editor.doc.pages.size());
        equal("back on the first page", 0, editor.page);

        editor.insertPage();
        equal("inserting adds a page", 4, editor.doc.pages.size());
        equal("rows follow the current page", 4, editor.doc.pages.get(1).rows);
        equal("cells are blank", "", cell(0, "x"));
        equal("enums start at code 0", "OFF (0)", cell(0, "state"));
        equal("fixed values show", "7", parameter(5));
        editor.deletePage();
        equal("deleting removes it", 3, editor.doc.pages.size());
        editor.undo();
        equal("undo delete", 4, editor.doc.pages.size());
        editor.undo();
        equal("undo insert", 3, editor.doc.pages.size());
        editor.undo();
    }

    static void testArrays() {
        editor.columnModel.setValueAt("0.1", 0, column("x"));
        Document.ArrayCells grid = editor.doc.pages.get(0).arrays[2];
        editor.resizeArrayTo(2, new int[] { 3, 2 });
        equal("a resize keeps coordinates", new String[] { "1", "2", "3", "4", "", "" }, editor.doc.pages.get(0).arrays[2].values);
        editor.undo();
        equal("undo resize", new int[] { 2, 2 }, editor.doc.pages.get(0).arrays[2].dims);
        editor.resizeArrayTo(2, new int[] { 2, 3 });
        equal("growing the last dimension", new String[] { "1", "2", "", "3", "4", "" }, editor.doc.pages.get(0).arrays[2].values);
        editor.undo();

        editor.openArrayViewer(2);
        equal("a viewer opens", 1, editor.viewers().size());
        ArrayViewer viewer = editor.viewers().get(0);
        equal("on the array", "grid", viewer.arrayName());
        editor.openArrayViewer(2);
        equal("each request opens another", 2, editor.viewers().size());
        editor.viewers().get(1).dispose();
        editor.viewerClosed(editor.viewers().get(1));
        check("cells can be edited from a viewer", editor.editArrayCells(2, new int[] { 3 }, new String[] { "44" }, "Edit Cell"));
        equal("the document follows", "44", editor.doc.pages.get(0).arrays[2].values[3]);
        equal("and the table", "44", String.valueOf(editor.arrayModel.getValueAt(3, 2)));
        editor.undo();
        equal("undo is shared", "4", editor.doc.pages.get(0).arrays[2].values[3]);
        editor.deleteItems(DefinitionDialogs.Kind.ARRAY, Arrays.asList(2));
        equal("a viewer stays open when its array goes", 1, editor.viewers().size());
        equal("and shows nothing", 0, viewer.table().getRowCount());
        editor.undo();
        check("undo brings the array back to the viewer", viewer.table().getRowCount() > 0);
        viewer.dispose();
        editor.viewerClosed(viewer);
    }

    static void testTimeDisplay() {
        Definition time = editor.doc.columns.get(column("Time"));
        check("a column named Time in seconds can show dates", time.canDisplayTime());
        check("other names cannot", !editor.doc.columns.get(column("x")).canDisplayTime());
        editor.setTimeDisplay(time, Definition.TimeMode.UTC);
        equal("UTC", "2023-11-14 22:13:20.000 UTC", cell(0, "Time"));
        equal("second row", "2023-11-14 22:14:20.000 UTC", cell(1, "Time"));
        check("not a change to the file", editor.doc.pages.get(0).columns[column("Time")][0].equals("1.7e+09"));
        editor.setTimeDisplay(time, Definition.TimeMode.LOCAL);
        check("local has a zone", cell(0, "Time").matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.000 .+"));
        editor.setTimeDisplay(time, Definition.TimeMode.RAW);
        equal("raw", "1.7e+09", cell(0, "Time"));
        equal("out of range stays raw", "1e+300", GridModels.ColumnModel.timeText("1e+300", Definition.TimeMode.UTC));
    }

    static void testSaving(String name) throws Exception {
        editor.undo.clear();
        editor.showPage(1);
        editor.columnModel.setValueAt("-7.5", 2, column("x"));
        Path saved = work.resolve("saved.sdds");
        editor.setSaveFormat(true);
        check("save", editor.writeFile(saved.toString()));
        check("saved state", !editor.dirty);
        SDDSFile back = new SDDSFile(saved.toString());
        check("it reads back: " + back.getErrors(), back.readFile());
        equal("all pages", 3, back.pageCount());
        check("ASCII", back.asciiFile);
        equal("the edit is there", -7.5, back.getColumnValues(column("x"), 2, false)[2]);
        equal("enums keep codes", new int[] { 0, 10, -1, 0 }, back.getColumnEnumCodes(column("state"), 2));
        equal("exact integers", "18446744073709551615", String.valueOf(back.getColumnValues(column("big"), 1, false)[0]));
        equal("long double digits", "1.0000000000000000001", Cells.text((SDDSLongDouble) back.getColumnValues(column("ld"), 1, false)[0]));
        equal("fixed value kept", "7", back.getParameterFixedValue(5));
        equal("description", "Beam position study", back.descriptionText);
        equal("array shape", new int[] { 2, 2 }, back.getArrayDim(1, 2));

        editor.setSaveFormat(false);
        check("save binary", editor.writeFile(saved.toString()));
        SDDSFile binary = new SDDSFile(saved.toString());
        check("binary reads back: " + binary.getErrors(), binary.readFile());
        check("binary", !binary.asciiFile);
        equal("values", -7.5, binary.getColumnValues(column("x"), 2, false)[2]);

        byte[] good = Files.readAllBytes(saved);
        editor.doc.pages.get(1).columns[column("index")][0] = "not a number";
        sddsEdit.lastWarning = null;
        check("an invalid value fails the save", !editor.writeFile(saved.toString()));
        check("and says why", sddsEdit.lastWarning != null && sddsEdit.lastWarning.contains("column 'index' row 1"));
        check("the file is untouched", Arrays.equals(good, Files.readAllBytes(saved)));
        check("no staging folder is left", Files.list(work).noneMatch(p -> p.getFileName().toString().startsWith(".sddsedit-")));
        editor.doc.pages.get(1).columns[column("index")][0] = "1";

        Path version = work.resolve("data.009");
        Files.deleteIfExists(version);
        Files.deleteIfExists(work.resolve("data.010"));
        Path link = work.resolve("data-link.sdds");
        Files.deleteIfExists(link);
        Files.copy(saved, version);
        Files.createSymbolicLink(link, version.toAbsolutePath());
        check("save through a versioned link", editor.writeFile(link.toString()));
        check("a new version exists", Files.exists(work.resolve("data.010")));
        check("the old one stays", Arrays.equals(good, Files.readAllBytes(version)));
        check("the link is still a link", Files.isSymbolicLink(link));
        equal("and points at the new version", work.resolve("data.010").toAbsolutePath().normalize(), Files.readSymbolicLink(link));
        check("no stray link is left", Files.exists(link, LinkOption.NOFOLLOW_LINKS));

        Path csv = work.resolve("export.csv");
        sddsEdit.lastWarning = null;
        try {
            editor.doc.writeCSV(csv.toString());
            check("CSV refuses enums", false);
        } catch (Document.Failure e) {
            check("CSV refuses enums", e.getMessage().contains("enum"));
        }
        Document plain = new Document();
        Definition column = new Definition("a,b", SDDSUtil.SDDS_STRING);
        plain.columns.add(column);
        Document.Page page = new Document.Page();
        page.rows = 1;
        page.columns = new String[][] { { "he said \"hi\"" } };
        plain.pages.add(page);
        plain.writeCSV(csv.toString());
        equal("CSV quotes", "Columns\n\"a,b\"\n\"he said \"\"hi\"\"\"\n\n", new String(Files.readAllBytes(csv), StandardCharsets.UTF_8));
        editor.loadFile(name);
    }

    /** Findings of the review against the Qt editor, so that they stay fixed. */
    static void testReviewFindings(String name) throws Exception {
        testNonFinite();

        editor.loadFile(name);
        CellTable table = editor.columnTable;
        int nameColumn = column("name"), x = column("x");

        editor.doc.pages.get(0).columns[nameColumn][0] = "two\nlines";
        editor.undo.clear();
        click(table, 0, nameColumn, 0);
        equal("the editor keeps a newline", "two\nlines", ((JTextField) table.getEditorComponent()).getText());
        table.finishEditing();
        equal("clicking a cell does not change it", "two\nlines", editor.doc.pages.get(0).columns[nameColumn][0]);
        check("or add to the history", !editor.undo.canUndo());
        editor.doc.pages.get(0).columns[nameColumn][0] = "BPM 10";

        editor.setRowFilter("x < 0.2");
        equal("three rows pass", 3, table.getRowCount());
        table.startEditing(1, x);
        ((JTextField) table.getEditorComponent()).setText("42");
        editor.clearRowFilter();
        equal("clearing a filter commits to the row being edited", "42", cell(2, "x"));
        equal("and no other", "0.25", cell(1, "x"));
        editor.undo();

        table.selectColumnCells(editor.doc.columns.size() - 1, false, false);
        editor.deleteItems(DefinitionDialogs.Kind.COLUMN, Arrays.asList(editor.doc.columns.size() - 1));
        equal("a deleted column leaves the search scope", "Find in all columns...", editor.columnSearch.placeholder);
        editor.columnSearch.setText("BPM");
        editor.findInColumnPanel();
        equal("and the search still works", nameColumn, table.getCurrentColumn());
        editor.columnSearch.setText("");
        editor.undo();

        table.clearCellSelection();
        table.selectColumnCells(1, false, false);
        table.selectColumnCells(1, true, false);
        equal("Ctrl-click on a selected header deselects", 0, table.selectedCells().size());
        table.selectRowCells(1, false, false);
        table.selectRowCells(1, true, false);
        equal("and on a selected row number", 0, table.selectedCells().size());

        table.getActionMap().get("selectAllCells").actionPerformed(null);
        equal("Ctrl+A selects every cell", 36, table.selectedCells().size());
        editor.parameterTable.getActionMap().get("selectAllCells").actionPerformed(null);
        equal("and every parameter value", 6, editor.parameterTable.selectedCells().size());
        editor.parameterTable.clearCellSelection();
        table.clearCellSelection();

        table.changeSelection(2, 1, false, false);
        table.changeSelection(3, 1, true, false);
        editor.setRowFilter("index != 1");
        check("a filter keeps the selection on its rows", table.isCellSelected(1, 1) && table.isCellSelected(2, 1));
        equal("and the current cell", 2, table.getCurrentRow());
        editor.columnModel.setValueAt("1", 1, column("index"));
        equal("an edit that hides a row", 2, table.getRowCount());
        check("leaves the other selected", table.isCellSelected(1, 1));
        editor.undo();
        editor.clearRowFilter();
        check("clearing it does too", table.isCellSelected(3, 1));
        table.clearCellSelection();

        Definition time = editor.doc.columns.get(column("Time"));
        editor.sortColumn(x, true);
        editor.setTimeDisplay(time, Definition.TimeMode.UTC);
        editor.undo();
        check("undo keeps a later time display", cell(0, "Time").endsWith(" UTC"));
        table.clearCellSelection();
        editor.columnSearch.setText("2023");
        sddsEdit.lastWarning = null;
        editor.findInColumnPanel();
        check("search looks in stored values, not dates", editor.statusMessage.getText().contains("not found"));
        editor.columnSearch.setText("1700000060");
        editor.findInColumnPanel();
        equal("so a stored value is found", 1, table.getCurrentRow());
        editor.columnSearch.setText("");
        editor.setTimeDisplay(time, Definition.TimeMode.RAW);
        table.clearCellSelection();

        table.changeSelection(0, column("index"), false, false);
        table.startEditing(0, column("index"));
        setClipboard("5\t6\n7");
        ((JTextField) table.getEditorComponent()).paste();
        check("pasting cells into an editor closes it", !table.isEditing());
        equal("and fills the cell", "5", cell(0, "index"));
        equal("its neighbor", "6", cell(0, "name"));
        equal("and the next row", "7", cell(1, "index"));
        editor.undo();

        JMenu view = editor.getJMenuBar().getMenu(2);
        view.getItem(6).doClick();
        check("the View menu collapses a panel", !editor.columnPanel.isExpanded());
        check("which stays in the window", editor.columnPanel.isVisible());
        editor.columnPanel.toggle.doClick();
        check("the chevron updates the menu", view.getItem(6).isSelected());

        for (ArrayViewer open : new ArrayList<ArrayViewer>(editor.viewers()))
            open.dispose();
        editor.viewers().clear();
        editor.arrayTable.clearCellSelection();
        editor.openCurrentArrayViewer();
        equal("with no current cell the first array opens", "gain", editor.viewers().get(0).arrayName());
        ArrayViewer first = editor.viewers().get(0);
        first.dispose();
        editor.viewerClosed(first);

        /* A chain of links is followed all the way, and nothing but the real file changes. */
        Path real = work.resolve("chain-real.sdds"), inner = work.resolve("chain-inner"), outer = work.resolve("chain-outer");
        for (Path path : new Path[] { real, inner, outer })
            Files.deleteIfExists(path);
        check("save the real file", editor.writeFile(real.toString()));
        byte[] old = Files.readAllBytes(real);
        Files.createSymbolicLink(inner, real.toAbsolutePath());
        Files.createSymbolicLink(outer, inner.toAbsolutePath());
        editor.columnModel.setValueAt("99", 0, x);
        check("save through two links", editor.writeFile(outer.toString()));
        check("the outer link stays", Files.isSymbolicLink(outer));
        check("the inner link stays", Files.isSymbolicLink(inner));
        check("the real file changes", !Arrays.equals(old, Files.readAllBytes(real)));
        editor.undo();

        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(real, java.nio.file.StandardOpenOption.WRITE);
                java.nio.channels.FileLock lock = channel.lock()) {
            sddsEdit.lastWarning = null;
            check("a locked file is not replaced", !editor.writeFile(real.toString()));
            equal("and it says so", "Output file is locked", sddsEdit.lastWarning);
        }
        check("saving works again without the lock", editor.writeFile(real.toString()));

        Document plain = new Document();
        plain.columns.add(new Definition("a", SDDSUtil.SDDS_DOUBLE));
        Document.Page page = new Document.Page();
        page.rows = 1;
        page.columns = new String[][] { { "1" } };
        plain.pages.add(page);
        Path csv = work.resolve("perm.csv"), probe = work.resolve("perm-probe"), real2 = work.resolve("perm-real.csv"), csvLink = work.resolve("perm-link.csv");
        for (Path path : new Path[] { csv, probe, real2, csvLink })
            Files.deleteIfExists(path);
        Files.createFile(probe);
        plain.writeCSV(csv.toString());
        equal("a new CSV file gets the usual permissions", Files.getPosixFilePermissions(probe), Files.getPosixFilePermissions(csv));
        Files.setPosixFilePermissions(csv, java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw----"));
        plain.writeCSV(csv.toString());
        equal("an existing one keeps its own", java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw----"), Files.getPosixFilePermissions(csv));
        Files.write(real2, new byte[] { 'x' });
        Files.createSymbolicLink(csvLink, real2.toAbsolutePath());
        plain.writeCSV(csvLink.toString());
        check("a CSV link stays a link", Files.isSymbolicLink(csvLink));
        check("and its target is written", Files.size(real2) > 1);
        check("no CSV staging folder is left", Files.list(work).noneMatch(p -> p.getFileName().toString().startsWith(".sddsedit-")));

        Document many = new Document();
        Document.Page manyPage = new Document.Page();
        manyPage.parameters = new String[30];
        for (int i = 0; i < 30; i++) {
            many.parameters.add(new Definition("p" + i, SDDSUtil.SDDS_LONG));
            manyPage.parameters[i] = String.valueOf(i);
        }
        many.pages.add(manyPage);
        editor.setDocument(many);
        editor.validate();
        editor.layoutParameterGrid();
        int groups = editor.parameterModel.groupCount();
        int[] cell = parameterValueCell(0);
        editor.parameterTable.startEditing(cell[0], cell[1]);
        ((JTextField) editor.parameterTable.getEditorComponent()).setText("999");
        java.awt.Dimension size = editor.getSize();
        editor.setSize(groups > 2 ? 640 : 1270, size.height);
        editor.validate();
        editor.layoutParameterGrid();
        check("the grid wrapped differently", editor.parameterModel.groupCount() != groups);
        equal("a reflow keeps what was typed", "999", manyPage.parameters[0]);
        editor.setSize(size);
        editor.validate();
    }

    static void testAuditFindings(String name) throws Exception {
        CellTable table = editor.columnTable;
        /* F-001: a read-only file is left alone by every way of writing it. */
        Path readOnly = work.resolve("readonly.sdds");
        Files.deleteIfExists(readOnly);
        editor.loadFile(name);
        check("save the file to protect", editor.writeFile(readOnly.toString()));
        byte[] protectedBytes = Files.readAllBytes(readOnly);
        Files.setPosixFilePermissions(readOnly, java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
        editor.columnModel.setValueAt("31", 0, column("x"));
        sddsEdit.lastWarning = null;
        check("saving over a read-only file fails", !editor.writeFile(readOnly.toString()));
        check("and says why", sddsEdit.lastWarning != null && sddsEdit.lastWarning.contains("not writable"));
        check("the read-only file is untouched", Arrays.equals(protectedBytes, Files.readAllBytes(readOnly)));
        check("the document stays unsaved", editor.dirty);
        Document plain = new Document();
        plain.columns.add(new Definition("a", SDDSUtil.SDDS_DOUBLE));
        Document.Page page = new Document.Page();
        page.rows = 1;
        page.columns = new String[][] { { "1" } };
        plain.pages.add(page);
        try {
            plain.writeCSV(readOnly.toString());
            check("CSV refuses a read-only file", false);
        } catch (Document.Failure e) {
            check("CSV refuses a read-only file", e.getMessage().contains("not writable"));
        }
        check("CSV leaves it untouched", Arrays.equals(protectedBytes, Files.readAllBytes(readOnly)));
        Files.setPosixFilePermissions(readOnly, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        check("a writable file saves as before", editor.writeFile(readOnly.toString()));
        editor.undo();

        /* F-003: HDF export replaces nothing unless the program succeeded. */
        Path h5 = work.resolve("export.h5");
        byte[] old = "existing file".getBytes(StandardCharsets.UTF_8);
        java.util.concurrent.atomic.AtomicReference<Process> running = new java.util.concurrent.atomic.AtomicReference<Process>();
        String program = Document.hdfProgram;
        try {
            for (String[] stand : new String[][] { { "/bin/false", "exit status" }, { "/bin/true", "could not write" },
                    { "/no/such/program", "could not be started" } }) {
                Files.write(h5, old);
                Document.hdfProgram = stand[0];
                try {
                    plain.exportHDF(h5.toString(), running);
                    check("HDF export with " + stand[0] + " reports a failure", false);
                } catch (Document.Failure e) {
                    check("HDF export with " + stand[0] + " reports a failure: " + e.getMessage(), e.getMessage().contains(stand[1]));
                }
                check("and leaves the existing file", Arrays.equals(old, Files.readAllBytes(h5)));
            }
            Document.hdfProgram = program;
            try {
                plain.exportHDF(work.resolve("no-such-folder").resolve("x.h5").toString(), running);
                check("HDF export to a missing folder fails", false);
            } catch (Document.Failure e) {
                check("HDF export to a missing folder fails", e.getMessage().contains("staging"));
            }
            String bin = System.getenv("SDDS_BIN");
            Path tool = bin != null ? Paths.get(bin, "sdds2hdf") : Paths.get("../../SDDS/bin/Linux-x86_64/sdds2hdf");
            if (Files.isExecutable(tool)) {
                Document.hdfProgram = tool.toString();
                plain.exportHDF(h5.toString(), running);
                check("the real program replaces the file", !Arrays.equals(old, Files.readAllBytes(h5)) && Files.size(h5) > 100);
                Document big = new Document();
                big.columns.add(new Definition("u", SDDSUtil.SDDS_ULONG64));
                Document.Page bigPage = new Document.Page();
                bigPage.rows = 1;
                bigPage.columns = new String[][] { { "18446744073709551615" } };
                big.pages.add(bigPage);
                Files.write(h5, old);
                try {
                    big.exportHDF(h5.toString(), running);
                    check("a type the program cannot write is reported", false);
                } catch (Document.Failure e) {
                    check("a type the program cannot write is reported", e.getMessage().contains("sdds2hdf"));
                }
                check("and the file is kept", Arrays.equals(old, Files.readAllBytes(h5)));
            }
        } finally {
            Document.hdfProgram = program;
        }
        check("no HDF staging folder is left", Files.list(work).noneMatch(p -> p.getFileName().toString().startsWith(".sddsedit-")));

        /* F-002: text from a file is never treated as HTML. */
        String html = work.resolve("html.sdds").toString();
        SDDSFile f = new SDDSFile(html);
        f.setMode(false);
        f.setDescription("<html><b>bold</b></html>", "<html><b>contents</b></html>");
        f.defineEnumColumn("e", null, null, null, null, "0:<html>A,1:B", 0);
        f.setColumn(0, new int[] { 0, 1 }, 1, 1);
        check("write a file with HTML text: " + f.getErrors(), f.writeFile());
        check("open it", editor.loadFile(html));
        List<javax.swing.JLabel> labels = new ArrayList<javax.swing.JLabel>();
        collectLabels(editor.outline, labels);
        int seen = 0;
        for (javax.swing.JLabel label : labels) {
            if (label.getText() != null && label.getText().startsWith("<html>")) {
                seen++;
                check("no HTML view for " + label.getText(), label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) == null);
            }
        }
        equal("the description is on show", 1, seen);
        editor.columnTable.startEditing(0, 0);
        JComboBox<?> combo = (JComboBox<?>) editor.columnTable.getEditorComponent();
        java.awt.Component shown = ((javax.swing.ListCellRenderer<Object>) (javax.swing.ListCellRenderer<?>) combo.getRenderer())
                .getListCellRendererComponent(new javax.swing.JList<Object>(), combo.getItemAt(0), 0, false, false);
        check("an enum label stays plain text", ((javax.swing.JComponent) shown).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) == null);
        editor.columnTable.removeEditor();
        equal("a tooltip stays plain text too", " <html>x", Flat.plainTip("<html>x"));
        equal("ordinary tooltips are unchanged", "x", Flat.plainTip("x"));
    }

    /** Checks for the medium-severity findings of the release audit. */
    static void testAuditMedium(String name) throws Exception {
        /* F-007: undo history stays within its limit, losing only the oldest steps. */
        check("the editor limits its history", editor.loadFile(name));
        UndoStack history = editor.undo;
        long oneStep = 2 * editor.doc.referenceBytes();
        history.setLimit(oneStep * 3);
        for (int i = 0; i < 6; i++)
            editor.sortColumn(column("x"), i % 2 == 0);
        equal("the oldest steps are dropped", 3, history.undoCount());
        String sorted = cell(0, "x");
        editor.undo();
        check("the newest step can still be undone", !cell(0, "x").equals(sorted));
        editor.redo();
        equal("and redone", sorted, cell(0, "x"));
        history.setLimit(oneStep / 2);
        editor.sortColumn(column("name"), true);
        equal("the newest step is always kept", 1, history.undoCount());
        history.setLimit(Long.MAX_VALUE);
        history.clear();
        check("a document has a size to measure", editor.doc.referenceBytes() > 0);
        java.io.PrintStream stderr = System.err;
        System.setErr(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        try {
            sddsEdit.reportFailure(new OutOfMemoryError("test"));
            check("running out of memory is explained", sddsEdit.lastWarning != null && sddsEdit.lastWarning.contains("ran out of memory"));
            sddsEdit.reportFailure(new IllegalStateException("test"));
            check("other errors are named", sddsEdit.lastWarning != null && sddsEdit.lastWarning.contains("IllegalStateException"));
        } finally {
            System.setErr(stderr);
        }
        editor.loadFile(name);

        /* F-010: the version agrees wherever it is written. */
        String manifest = AboutDialog.version();
        if (manifest != null) {
            String spec = new String(Files.readAllBytes(Paths.get(System.getProperty("sdds.tests", "tests"), "../rpm/SDDSJava.spec")), StandardCharsets.UTF_8);
            check("the jar's version matches the RPM's: " + manifest, spec.contains("Version:\t" + manifest + "\n"));
            String tcl = new String(Files.readAllBytes(Paths.get(System.getProperty("sdds.tests", "tests"), "../rpm/build.tcl")), StandardCharsets.UTF_8);
            check("and the RPM script's", tcl.contains("set version " + manifest + "\n"));
            String notes = new String(Files.readAllBytes(Paths.get(System.getProperty("sdds.tests", "tests"), "../RELEASE_NOTES.md")), StandardCharsets.UTF_8);
            check("and the release notes cover it", notes.contains("## " + manifest + "\n"));
        }

        /* F-012, F-013: the viewer follows a type change, and long text leaves room for the grid. */
        Document doc = new Document();
        Definition array = new Definition("a", SDDSUtil.SDDS_DOUBLE);
        doc.arrays.add(array);
        Document.Page page = new Document.Page();
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 20000; i++)
            longText.append(i % 50 == 49 ? '\n' : 'x');
        page.arrays = new Document.ArrayCells[] { new Document.ArrayCells(new String[] { "1", "2" }, new int[] { 2 }) };
        doc.pages.add(page);
        editor.setDocument(doc);
        editor.openArrayViewer(0);
        ArrayViewer viewer = editor.viewers().get(0);
        viewer.setSize(900, 620);
        viewer.validate();
        javax.swing.JCheckBox heatmap = findCheckBox(viewer, "Heatmap");
        check("a numeric array can have a heatmap", heatmap != null && heatmap.isEnabled());
        array.type = SDDSUtil.SDDS_STRING;
        viewer.documentChanged();
        check("a string array cannot", !heatmap.isEnabled());
        array.type = SDDSUtil.SDDS_DOUBLE;
        viewer.documentChanged();
        check("a numeric array can again", heatmap.isEnabled());
        array.type = SDDSUtil.SDDS_STRING;
        page.arrays[0].values[0] = longText.toString();
        viewer.documentChanged();
        int gridHeight = viewer.table().getParent().getHeight();
        viewer.table().changeSelection(0, 0, false, false);
        viewer.documentChanged();
        viewer.validate();
        check("selecting long text leaves the grid its room: " + viewer.table().getParent().getHeight() + " of " + gridHeight,
                viewer.table().getParent().getHeight() >= gridHeight - 60);
        viewer.dispose();
        editor.viewerClosed(viewer);

        /* F-014: tooltips describe definitions, without treating file text as HTML. */
        editor.loadFile(name);
        JTableHeader header = editor.columnTable.getTableHeader();
        Rectangle first = header.getHeaderRect(1);
        String tip = header.getToolTipText(new MouseEvent(header, MouseEvent.MOUSE_MOVED, 0, 0, first.x + 2, first.y + 2, 0, false));
        check("a column header names its column: " + tip, tip != null && tip.contains("x (double)") && tip.contains("Units: mm"));
        Definition time = editor.doc.columns.get(column("Time"));
        editor.setTimeDisplay(time, Definition.TimeMode.UTC);
        check("and its time display", time.toolTip().contains("Time display: UTC"));
        editor.setTimeDisplay(time, Definition.TimeMode.RAW);
        Definition odd = new Definition("n", SDDSUtil.SDDS_DOUBLE);
        odd.description = "<img src=x>";
        check("text in a tooltip is escaped", odd.toolTip().contains("&lt;img src=x&gt;"));
        CellTable parameters = editor.parameterTable;
        Rectangle nameCell = parameters.getCellRect(0, 0, false);
        equal("a parameter's name shows its description", "beam energy",
                parameters.getToolTipText(new MouseEvent(parameters, MouseEvent.MOUSE_MOVED, 0, 0, nameCell.x + 2, nameCell.y + 2, 0, false)));
        Rectangle typeCell = parameters.getCellRect(0, 1, false);
        equal("its type says how to change it", "Double-click to change the type",
                parameters.getToolTipText(new MouseEvent(parameters, MouseEvent.MOUSE_MOVED, 0, 0, typeCell.x + 2, typeCell.y + 2, 0, false)));
    }

    /** Checks for the low-severity findings of the release audit. */
    static void testAuditLow(String name) throws Exception {
        /* F-015: names and keywords do not depend on the user's locale. */
        java.util.Locale saved = java.util.Locale.getDefault();
        java.util.Locale.setDefault(new java.util.Locale("tr"));
        try {
            Definition time = new Definition("TIME", SDDSUtil.SDDS_DOUBLE);
            check("TIME can show dates in any locale", time.canDisplayTime());
            Expression.Context c = new Expression.Context();
            c.x = "2";
            equal("PI in capitals", "3.141592653589793", run("PI", c, SDDSUtil.SDDS_DOUBLE));
            editor.loadFile(name);
            editor.setRowFilter("TRUE");
            equal("TRUE in capitals", 4, editor.columnTable.getRowCount());
            editor.clearRowFilter();
        } finally {
            java.util.Locale.setDefault(saved);
        }

        /* F-023: a shape that cannot be saved is refused early. */
        check("sizes too large despite a zero", Document.shapeTooLarge(new int[] { 2147483647, 2147483647, 0 }));
        check("a large empty shape is fine", !Document.shapeTooLarge(new int[] { 0, 2147483647 }));
        check("an ordinary shape is fine", !Document.shapeTooLarge(new int[] { 3, 4 }));

        /* F-024: changing axes does not pile up listeners. */
        Document doc = new Document();
        Definition array = new Definition("a", SDDSUtil.SDDS_DOUBLE);
        array.dimensions = 3;
        doc.arrays.add(array);
        Document.Page page = new Document.Page();
        page.arrays = new Document.ArrayCells[] { new Document.ArrayCells(new String[24], new int[] { 2, 3, 4 }) };
        Arrays.fill(page.arrays[0].values, "1");
        doc.pages.add(page);
        editor.setDocument(doc);
        editor.openArrayViewer(0);
        ArrayViewer viewer = editor.viewers().get(0);
        JComboBox<?> axis = findCombo(viewer, "Dimension 0");
        check("the viewer has axis choices", axis != null);
        javax.swing.DefaultListSelectionModel selection = (javax.swing.DefaultListSelectionModel) viewer.table().getColumnModel().getSelectionModel();
        int before = selection.getListSelectionListeners().length;
        for (int i = 0; i < 20; i++)
            axis.setSelectedIndex(i % 3);
        check("listeners stay level: " + before + " then " + selection.getListSelectionListeners().length,
                selection.getListSelectionListeners().length <= before);
        viewer.dispose();
        editor.viewerClosed(viewer);
        editor.loadFile(name);
    }

    static JComboBox<?> findCombo(java.awt.Container c, String item) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof JComboBox) {
                JComboBox<?> box = (JComboBox<?>) k;
                for (int i = 0; i < box.getItemCount(); i++)
                    if (item.equals(String.valueOf(box.getItemAt(i))))
                        return box;
            }
            if (k instanceof java.awt.Container) {
                JComboBox<?> found = findCombo((java.awt.Container) k, item);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    static javax.swing.JCheckBox findCheckBox(java.awt.Container c, String text) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.JCheckBox && text.equals(((javax.swing.JCheckBox) k).getText()))
                return (javax.swing.JCheckBox) k;
            if (k instanceof java.awt.Container) {
                javax.swing.JCheckBox found = findCheckBox((java.awt.Container) k, text);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    static void collectLabels(java.awt.Component c, List<javax.swing.JLabel> out) {
        if (c instanceof javax.swing.JLabel)
            out.add((javax.swing.JLabel) c);
        if (c instanceof java.awt.Container)
            for (java.awt.Component k : ((java.awt.Container) c).getComponents())
                collectLabels(k, out);
    }

    static void testNonFinite() throws Exception {
        String file = work.resolve("nonfinite.sdds").toString();
        SDDSFile f = new SDDSFile(file);
        f.setMode(true);
        f.defineParameter("p", SDDSUtil.SDDS_DOUBLE);
        f.defineArray("a", SDDSUtil.SDDS_FLOAT, 1);
        f.defineColumn("d", SDDSUtil.SDDS_DOUBLE);
        f.defineColumn("f", SDDSUtil.SDDS_FLOAT);
        f.setParameter(0, Double.NEGATIVE_INFINITY, 1);
        f.setArrayDim(1, 0, new int[] { 2 });
        f.setArray(0, new float[] { Float.NaN, Float.POSITIVE_INFINITY }, 1);
        f.setColumn(0, new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.5 }, 1, 1);
        f.setColumn(1, new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 2.5f }, 1, 1);
        check("write NaN and infinities: " + f.getErrors(), f.writeFile());
        check("they open", editor.loadFile(file));
        equal("NaN shows as C does", "nan", cell(0, "d"));
        equal("infinity", "inf", cell(1, "d"));
        for (boolean ascii : new boolean[] { false, true }) {
            editor.setSaveFormat(ascii);
            check("save them", editor.writeFile(file));
            SDDSFile back = new SDDSFile(file);
            check("they read back: " + back.getErrors(), back.readFile());
            Object[] d = back.getColumnValues(0, 1, false), s = back.getColumnValues(1, 1, false);
            check("double NaN survives a save", ((Double) d[0]).isNaN());
            equal("double infinity", Double.POSITIVE_INFINITY, d[1]);
            equal("negative infinity", Double.NEGATIVE_INFINITY, d[2]);
            equal("an ordinary number", 1.5, d[3]);
            check("float NaN", ((Float) s[0]).isNaN());
            equal("float infinity", Float.POSITIVE_INFINITY, s[1]);
            equal("parameter", Double.NEGATIVE_INFINITY, back.getParameterValue(0, 1, false));
            Object[] a = back.getArrayValues(0, 1, false);
            check("array NaN", ((Float) a[0]).isNaN());
            equal("array infinity", Float.POSITIVE_INFINITY, a[1]);
            check("reopen", editor.loadFile(file));
        }
        editor.columnModel.setValueAt("Infinity", 3, 0);
        equal("any C spelling can be typed", "inf", cell(3, "d"));
        editor.undo();
    }

    static void testEdgeFiles() throws Exception {
        String empty = work.resolve("nopages.sdds").toString();
        SDDSFile f = new SDDSFile(empty);
        f.setMode(false);
        f.defineParameter("p", SDDSUtil.SDDS_DOUBLE);
        f.defineColumn("c", SDDSUtil.SDDS_DOUBLE);
        check("write a file with no pages: " + f.getErrors(), f.writeFile());
        Document before = editor.doc;
        sddsEdit.lastWarning = null;
        check("a file with no pages is refused", !editor.loadFile(empty));
        equal("and says why", "File contains no pages", sddsEdit.lastWarning);
        check("the open file stays", editor.doc == before);

        String only = work.resolve("onlyparameters.sdds").toString();
        SDDSFile p = new SDDSFile(only);
        p.setMode(false);
        p.defineParameter("p", SDDSUtil.SDDS_DOUBLE);
        p.setParameter(0, 2.5, 1);
        check("write a file of parameters: " + p.getErrors(), p.writeFile());
        check("it opens", editor.loadFile(only));
        check("the empty panels are collapsed", !editor.columnPanel.isExpanded() && !editor.arrayPanel.isExpanded());
        check("the parameter panel is open", editor.parameterPanel.isExpanded());
        check("and it saves", editor.writeFile(only));

        Document blank = new Document();
        blank.pages.add(new Document.Page());
        editor.setDocument(blank);
        equal("a blank document has no path", null, editor.doc.path);
        equal("its name", "Untitled", editor.pathLabel.getText());
        check("every panel is open", editor.columnPanel.isExpanded() && editor.arrayPanel.isExpanded() && editor.parameterPanel.isExpanded());
        String nothing = work.resolve("nothing.sdds").toString();
        check("a document with nothing defined saves", editor.writeFile(nothing));
        check("and opens again", editor.loadFile(nothing));
        equal("with one page", 1, editor.doc.pages.size());
    }

    static String run(String expression, Expression.Context context, int type) {
        try {
            return Expression.evaluate(expression, context, type);
        } catch (Expression.Failure e) {
            return "failed";
        }
    }

    static void testExpressionLanguage() {
        Expression.Context c = new Expression.Context();
        c.x = "3";
        c.a = "2";
        int d = SDDSUtil.SDDS_DOUBLE, l = SDDSUtil.SDDS_LONG;
        equal("precedence", "7", run("1 + 2 * 3", c, d));
        equal("a sign binds looser than a power", "-4", run("-2^2", c, d));
        equal("power is right associative", "512", run("2^3^2", c, d));
        equal("a signed exponent", "0.5", run("2^-1", c, d));
        equal("functions", "3", run("sqrt(9)", c, d));
        equal("case does not matter", "3", run("X", c, d));
        equal("pi", "3.141592653589793", run("pi", c, d));
        equal("division by zero fails", "failed", run("1/0", c, d));
        equal("trailing text fails", "failed", run("1 2", c, d));
        equal("unknown name fails", "failed", run("q", c, d));
        equal("deep nesting fails cleanly", "failed", run(new String(new char[5000]).replace('\0', '('), c, d));
        equal("exact integer division", "2", run("6/3", c, l));
        equal("an integer cell takes floating point", "2", run("floor(2.5)", c, l));
        equal("a float result in an integer cell stays text", "2.5", run("x - 0.5", c, l));
        equal("a huge literal in an integer cell fails", "failed", run("100000000000000000000000 - 100000000000000000000000", c, l));
        c.x = "bad";
        equal("a bad cell fails", "failed", run("x", c, d));
        try {
            equal("a series", "12", Expression.series("10", "1", 2, l));
            equal("a double series", "1.5", Expression.series("0.5", "0.5", 2, d));
            equal("an exact series", "18446744073709551615", Expression.series("18446744073709551613", "1", 2, SDDSUtil.SDDS_ULONG64));
        } catch (Expression.Failure e) {
            check("series: " + e.getMessage(), false);
        }
        equal("templates are read once", "${i}x", Expression.applyTemplate("${x}x", "${i}", "", 5, 0, 0, 0, 0));
    }

    static void testNumberText() {
        equal("large round number", "1e+05", Cells.text(100000.0));
        equal("one digit more", "100001", Cells.text(100001.0));
        equal("small number", "0.001", Cells.text(0.001));
        equal("smaller", "1e-05", Cells.text(0.00001));
        equal("negative zero", "-0", Cells.text(-0.0));
        equal("not a number", "nan", Cells.text(Double.NaN));
        equal("infinity", "-inf", Cells.text(Double.NEGATIVE_INFINITY));
        equal("double shortest", "0.1", Cells.text(0.1));
        equal("float shortest", "0.1", Cells.text(0.1f));
        equal("float digits", "1.2345679", Cells.text(1.23456789f));
        check("long double round trip", Cells.valid(Cells.text(SDDSLongDouble.parse("3.14159265358979323846")), SDDSUtil.SDDS_LONGDOUBLE));
        check("short limit", !Cells.valid("70000", SDDSUtil.SDDS_SHORT) && Cells.valid("-32768", SDDSUtil.SDDS_SHORT));
        check("ushort limit", !Cells.valid("-1", SDDSUtil.SDDS_USHORT) && Cells.valid("65535", SDDSUtil.SDDS_USHORT));
        check("ulong64 limit", Cells.valid("18446744073709551615", SDDSUtil.SDDS_ULONG64) && !Cells.valid("18446744073709551616", SDDSUtil.SDDS_ULONG64));
        check("blank is a number", Cells.valid("", SDDSUtil.SDDS_DOUBLE));
        check("nan is a number", Cells.valid("nan", SDDSUtil.SDDS_DOUBLE));
        check("double overflow", !Cells.valid("1e999", SDDSUtil.SDDS_DOUBLE));
        check("float overflow", !Cells.valid("1e39", SDDSUtil.SDDS_FLOAT));
        check("a string can hold anything but NUL", Cells.valid("a b", SDDSUtil.SDDS_STRING) && !Cells.valid("a\0b", SDDSUtil.SDDS_STRING));
        equal("fixed text escapes", "a\\040b\\042", SDDSUtil.fixedValueText("a b\"", SDDSUtil.SDDS_STRING));
        equal("blank fixed number", "0", SDDSUtil.fixedValueText(" ", SDDSUtil.SDDS_DOUBLE));
    }

    static void testPlotArguments() {
        equal("plot a column", Arrays.asList("sddsplot", "-split=page", "-sep=page", "f", "-col=x"),
                sddsEdit.plotArguments("f", "x", false, false));
        equal("against Time", Arrays.asList("sddsplot", "-split=page", "-sep=page", "f", "-col=Time,x", "-tick=xtime"),
                sddsEdit.plotArguments("f", "x", false, true));
        equal("an array", Arrays.asList("sddsplot", "-split=page", "-sep=page", "f", "-arraynames=gain"),
                sddsEdit.plotArguments("f", "gain", true, false));
        equal("wildcards are escaped", "-col=Q\\[0\\]", sddsEdit.plotArguments("f", "Q[0]", false, false).get(4));
    }

    static void testLargeGridLayout() {
        Document big = new Document();
        Document.Page page = new Document.Page();
        page.parameters = new String[30];
        for (int i = 0; i < 30; i++) {
            big.parameters.add(new Definition("p" + i, SDDSUtil.SDDS_LONG));
            page.parameters[i] = String.valueOf(i);
        }
        big.pages.add(page);
        editor.setDocument(big);
        editor.validate();
        editor.layoutParameterGrid();
        check("many parameters wrap into groups", editor.parameterModel.groupCount() > 1);
        int groups = editor.parameterModel.groupCount();
        check("at most six", groups <= 6);
        equal("rows follow", (30 + groups - 1) / groups, editor.parameterModel.getRowCount());
        int[] last = editor.parameterModel.valueCell(29);
        equal("the last parameter is reachable", 29, editor.parameterModel.parameter(last[0], last[1]));
        equal("its value", "29", String.valueOf(editor.parameterModel.getValueAt(last[0], last[1])));
        check("a group end draws a strong line", editor.parameterModel.isGroupEnd(editor.parameterModel.fieldCount() - 1));
    }
}
