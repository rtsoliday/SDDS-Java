/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

import SDDS.java.SDDS.*;
import java.io.*;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * SDDS6 conformance tests for the Java library. The fixtures in fixtures/enums are
 * the ones shared by the C and C++ readers: files named valid-* must be accepted and
 * files named invalid-* must be rejected. When the SDDS toolkit is available the
 * tests also exchange files with the C programs in both directions.
 *
 * Environment: SDDS_BIN is the directory of the C programs (by default
 * ../SDDS/bin/Linux-x86_64 when it exists), and SDDS_TEST_DIR is where generated
 * files go (by default tests/build/artifacts).
 */
public class SDDS6ConformanceTest {
    static int passed = 0, failed = 0;
    static Path work;
    static Path bin;

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
        if (ok)
            passed++;
        else {
            failed++;
            if (expected instanceof String && actual instanceof String && ((String) expected).indexOf('\n') >= 0) {
                String[] x = ((String) expected).split("\n", -1), y = ((String) actual).split("\n", -1);
                int i = 0;
                while (i < x.length && i < y.length && x[i].equals(y[i]))
                    i++;
                System.out.println("FAIL: " + name + " differs at line " + (i + 1) + "\n  expected: "
                        + (i < x.length ? x[i] : "<end>") + "\n  actual:   " + (i < y.length ? y[i] : "<end>"));
                return;
            }
            System.out.println("FAIL: " + name + "\n  expected: " + describe(expected) + "\n  actual:   " + describe(actual));
        }
    }

    static String describe(Object o) {
        if (o != null && o.getClass().isArray()) {
            StringBuilder s = new StringBuilder("[");
            for (int i = 0; i < Array.getLength(o); i++)
                s.append(i > 0 ? ", " : "").append(describe(Array.get(o, i)));
            return s.append("]").toString();
        }
        return String.valueOf(o);
    }

    static boolean rejects(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        Path tests = Paths.get(System.getProperty("sdds.tests", "tests"));
        String workDir = System.getenv("SDDS_TEST_DIR");
        work = workDir != null ? Paths.get(workDir) : tests.resolve("build/artifacts");
        Files.createDirectories(work);
        String binDir = System.getenv("SDDS_BIN");
        Path defaultBin = tests.resolve("../../SDDS/bin/Linux-x86_64");
        if (binDir != null)
            bin = Paths.get(binDir);
        else if (Files.isDirectory(defaultBin))
            bin = defaultBin;

        fixtures(tests.resolve("fixtures/enums"));
        dictionary();
        fixtureContents(tests.resolve("fixtures/enums"));
        namelist();
        setters();
        nonFiniteText();
        turkishLocale();
        readErrors();
        conversions();
        versions();
        longDouble();
        unsigned();
        strings();
        roundTrips();
        compression();
        edgePages();
        fieldLengthOverflow();
        nulInAsciiData();
        damagedCounts();
        largePages();
        failedOpensClose();
        if (bin != null) {
            cToJava();
            javaToC();
            cEnumInterchange();
            agreement(tests.resolve("fixtures/enums"));
            readerDetails();
            writerDetails();
        } else {
            System.out.println("SKIP: C toolkit not found; set SDDS_BIN to run the interchange tests");
        }
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Every shared fixture is accepted or rejected as its manifest says. */
    static void fixtures(Path dir) throws IOException {
        int count = 0;
        for (String line : Files.readAllLines(dir.resolve("manifest.txt"))) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 2)
                continue;
            boolean valid = parts[0].equals("1");
            SDDSFile file = new SDDSFile(dir.resolve(parts[1]).toString());
            boolean read = file.readFile();
            String errors = file.getErrors();
            check("fixture " + parts[1] + (valid ? " must be accepted: " + errors : " must be rejected"), read == valid);
            if (!valid)
                check("fixture " + parts[1] + " reports why", !errors.isEmpty());
            count++;
        }
        check("the fixture manifest was read", count == 41);
    }

    static void fixtureContents(Path dir) {
        SDDSFile f = read(dir.resolve("valid-escapes.sdds").toString());
        SDDSEnumDefinition d = f.getColumnEnumDefinition(0);
        equal("escaped label", "a,b:c\\d\"e\n\r\t\u96ea ", d.codeToLabel(10));
        equal("escaped canonical text", "0:OFF,10:a\\,b\\:c\\\\d\\\"e\\n\\r\\t\u96ea ,-1:FAULT", d.getValues());
        equal("value with escapes", new int[] { 10 }, f.getColumnEnumCodes(0, 1));

        f = read(dir.resolve("valid-fixed.sdds").toString());
        equal("fixed parameter code", Integer.valueOf(-1), f.getParameterEnumCode(0, 1));
        equal("fixed parameter label", "FAULT", f.getParameterEnumLabel(0, 1));
        equal("fixed parameter is not page data", "-1", f.getParameterFixedValue(0));
        equal("fixed file has one page", 1, f.pageCount());
        equal("fixed generic getter", "FAULT", f.getParameterValue(0, 1, false).toString());

        f = read(dir.resolve("valid-limits.sdds").toString());
        equal("limits", new int[] { Integer.MIN_VALUE }, f.getColumnEnumCodes(0, 1));
        equal("limits mapping", "-2147483648:MIN,2147483647:MAX", f.getColumnEnumValues(0));

        f = read(dir.resolve("valid-multiline.sdds").toString());
        equal("multiline command", new String[] { "ON" }, f.getColumnEnumLabels(0, 1));

        f = read(dir.resolve("valid-inline-comment.sdds").toString());
        equal("inline comment", new String[] { "ON" }, f.getColumnEnumLabels(0, 1));

        f = read(dir.resolve("valid-large.sdds").toString());
        check("large dictionary", f.getColumnEnumDefinition(0).size() >= 1000);
    }

    static SDDSFile read(String name) {
        SDDSFile f = new SDDSFile(name);
        if (!f.readFile())
            throw new IllegalStateException("cannot read " + name + ": " + f.getErrors());
        return f;
    }

    /** The logical dictionary grammar of the specification. */
    static void dictionary() {
        SDDSEnumDefinition d = new SDDSEnumDefinition("0:OFF,10:ON,-1:FAULT");
        equal("size", 3, d.size());
        equal("declaration order", -1, d.getEntryCode(2));
        equal("label", "FAULT", d.codeToLabel(-1));
        equal("code", Integer.valueOf(10), d.labelToCode("ON"));
        equal("unknown code", null, d.codeToLabel(5));
        equal("unknown label", null, d.labelToCode("on"));
        equal("canonical", "0:OFF,10:ON,-1:FAULT", d.getValues());
        check("equal in any order", d.equals(new SDDSEnumDefinition("-1:FAULT,0:OFF,10:ON")));
        equal("hash in any order", d.hashCode(), new SDDSEnumDefinition("-1:FAULT,0:OFF,10:ON").hashCode());
        check("different mapping", !d.equals(new SDDSEnumDefinition("0:OFF,10:ON,-2:FAULT")));
        equal("leading zeros normalize", "7:A", new SDDSEnumDefinition("007:A").getValues());
        equal("label whitespace kept", " a ", new SDDSEnumDefinition("1: a ").codeToLabel(1));
        equal("logical example", "a,b:c\\d", new SDDSEnumDefinition("10:a\\,b\\:c\\\\d").codeToLabel(10));
        equal("numeric labels stay distinct", Integer.valueOf(2), new SDDSEnumDefinition("1:1,2:01").labelToCode("01"));
        equal("supplementary characters", "\ud83d\ude00", new SDDSEnumDefinition("1:\ud83d\ude00").codeToLabel(1));
        String[] invalid = { "", "0:A,0:B", "0:A,1:A", "0:", "0:A\\q", "2147483648:A", "-2147483649:A", "0:A:B", "0:A,",
                "x:A", ":A", "+1:A", " 1:A", "1:A, 2:B", "1", "1:\"x", "0:A\\", "1:a\nb", "1:a\rb", "1:a\tb", "1:\u0000x",
                "1:\ud83d", "1:\ude00x", "1:\udcc0", "1:\u007f" };
        for (String s : invalid)
            check("invalid mapping " + s, rejects(() -> new SDDSEnumDefinition(s)));
        equal("parse", Integer.valueOf(-5), SDDSEnumDefinition.parseCode(" -5 "));
        for (String s : new String[] { "+5", "5 6", "5x", "5.0", "", " ", "-", "2147483648", "-2147483649" })
            equal("parse rejects " + s, null, SDDSEnumDefinition.parseCode(s));
        equal("parse minimum", Integer.valueOf(Integer.MIN_VALUE), SDDSEnumDefinition.parseCode("-2147483648"));

        SDDSEnumDefinition q = new SDDSEnumDefinition("1:a b,2:c\\\"d,3:e");
        equal("quoted when needed", "\"a b\"", q.formatLabel(1, true));
        equal("embedded quote", "c\\\"d", q.formatLabel(2, true));
        equal("no quotes needed", "e", q.formatLabel(3, true));
        equal("quotes not requested", "a b", q.formatLabel(1, false));

        equal("remap", new int[] { 8, 7 }, new SDDSEnumDefinition("1:OFF,2:ON").remap(new SDDSEnumDefinition("7:OFF,8:ON"), new int[] { 2, 1 }));
        check("remap needs every label", rejects(() -> new SDDSEnumDefinition("1:OFF,2:ON").remap(new SDDSEnumDefinition("7:OFF"), new int[] { 1, 2 })));

        String[] sorted = { "\uffff", "\ud83d\ude00", "a" };
        Arrays.sort(sorted, SDDSEnumDefinition.LABEL_ORDER);
        equal("label sort is UTF-8 byte order", new String[] { "a", "\uffff", "\ud83d\ude00" }, sorted);

        check("limits validated", !SDDSEnumDefinition.setReaderLimits(0, 1) && !SDDSEnumDefinition.setReaderLimits(10, 20)
                && !SDDSEnumDefinition.setReaderLimits(Integer.MAX_VALUE, 10));
        check("limits accepted", SDDSEnumDefinition.setReaderLimits(100000, 16));
        check("mapping limit applies", rejects(() -> new SDDSEnumDefinition("1:aaaaaaaaaaaaaaaaaaaaaa")));
        SDDSEnumDefinition.setReaderLimits(SDDSEnumDefinition.DEFAULT_LAYOUT_LIMIT, SDDSEnumDefinition.DEFAULT_MAPPING_LIMIT);
    }

    static String file(String text) throws IOException {
        return bytesFile(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    static int counter = 0;

    static String bytesFile(byte[] content) throws IOException {
        Path p = work.resolve("generated-" + (counter++) + ".sdds");
        Files.write(p, content);
        return p.toString();
    }

    static boolean reads(String text) throws IOException {
        SDDSFile f = new SDDSFile(file(text));
        boolean ok = f.readFile();
        f.getErrors();
        return ok;
    }

    /** Header rules beyond the shared fixtures. */
    static void namelist() throws IOException {
        check("SDDS7 is not understood", !reads("SDDS7\n&data mode=ascii &end\n"));
        check("not an SDDS file", !reads("hello\n"));
        check("no data command", !reads("SDDS6\n&column name=a, type=long &end\n"));
        check("unterminated command", !reads("SDDS6\n&column name=a, type=long\n"));
        check("unknown command", !reads("SDDS6\n&bogus name=a, type=long &end\n&data mode=ascii &end\n"));
        check("unknown field", !reads("SDDS6\n&column name=a, type=long, foo=1 &end\n&data mode=ascii &end\n"));
        check("unknown field in SDDS5", !reads("SDDS5\n&column name=a, type=long, foo=1 &end\n&data mode=ascii &end\n"));
        check("unknown escape", !reads("SDDS6\n&column name=\"a\\q\", type=long &end\n&data mode=ascii &end\n"));
        check("octal zero", !reads("SDDS6\n&column name=\"a\\000\", type=long &end\n&data mode=ascii &end\n"));
        check("bad mode", !reads("SDDS6\n&data mode=text &end\n"));
        check("missing mode", !reads("SDDS6\n&data &end\n"));
        check("bad endian", !reads("SDDS6\n&data mode=binary, endian=middle &end\n"));
        check("format on an enum", !reads("SDDS6\n&column name=a, type=enum, format_string=\"%d\", enum_values=\"0:A\" &end\n&data mode=ascii &end\n"));
        check("field length on an enum", !reads("SDDS6\n&column name=a, type=enum, field_length=4, enum_values=\"0:A\" &end\n&data mode=ascii &end\n"));
        check("blank format on an enum is no format", reads("SDDS6\n&column name=a, type=enum, format_string=\" \", enum_values=\"0:A\" &end\n&data mode=ascii &end\n"));
        check("bad name", !reads("SDDS6\n&column name=\"1a\", type=long &end\n&data mode=ascii &end\n"));
        check("both byte orders", !reads("SDDS5\n!# big-endian\n!# little-endian\n&column name=a, type=long &end\n&data mode=binary &end\n"));
        check("enum_values on a primitive in SDDS5", !reads("SDDS5\n&column name=a, type=long, enum_values=\"0:A\" &end\n&data mode=ascii &end\n"));
        check("uppercase type in SDDS5", !reads("SDDS5\n&column name=a, type=DOUBLE &end\n&data mode=ascii &end\n"));

        // An exclamation mark or &end inside a quoted value is not a delimiter.
        SDDSFile f = read(file("SDDS6\n&description text=\"a!b &end c\\\\d\" &end\n&column name=a, type=long, units=\"m\\\"s\" &end\n&data mode=ascii &end\n1\n5\n"));
        equal("SDDS6 description", "a!b &end c\\d", f.descriptionText);
        equal("SDDS6 units", "m\"s", f.getColumnUnits(0));

        // Legacy text keeps backslashes, as the C reader does.
        f = read(file("SDDS5\n&description text=\"a!b &end\" &end\n&column name=a, type=long, units=\"m\\\"s\", symbol=\"\\mu\" &end\n&data mode=ascii &end\n1\n5\n"));
        equal("SDDS5 description", "a!b &end", f.descriptionText);
        equal("SDDS5 keeps escaped quote", "m\\\"s", f.getColumnUnits(0));
        equal("SDDS5 keeps backslash", "\\mu", f.getColumnSymbol(0));

        f = read(file("SDDS1\n! comment\n&column\n name=a,\n type=long,\n&end\n&associate name=x, filename=y, path=z, sdds=1 &end\n&data mode=ascii &end\n! c\n2\n1\n2 ! inline\n"));
        equal("legacy multiline and associate", new Object[] { 1L, 2L }, f.getColumnValues(0, 1, false));

        // &include brings in header commands.
        Path inc = work.resolve("included.hdr");
        Files.write(inc, "&column name=b, type=enum, enum_values=\"3:C\" &end\n".getBytes(StandardCharsets.ISO_8859_1));
        f = read(file("SDDS6\n&include filename=\"" + inc + "\" &end\n&data mode=ascii &end\n1\n3\n"));
        equal("included enum", new String[] { "C" }, f.getColumnEnumLabels(0, 1));

        // Header limits.
        SDDSEnumDefinition.setReaderLimits(64, 64);
        check("layout limit", !reads("SDDS6\n&column name=a, type=long, description=\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\" &end\n&data mode=ascii &end\n"));
        SDDSEnumDefinition.setReaderLimits(SDDSEnumDefinition.DEFAULT_LAYOUT_LIMIT, SDDSEnumDefinition.DEFAULT_MAPPING_LIMIT);
    }

    static SDDSFile layoutWithEnums() {
        SDDSFile f = new SDDSFile();
        f.defineEnumParameter("p", "0:OFF,10:ON,-1:FAULT");
        f.defineEnumArray("a", "0:OFF,10:ON,-1:FAULT", 1);
        f.defineEnumColumn("c", "0:OFF,10:ON,-1:FAULT");
        return f;
    }

    /** Assignments validate membership and numbers are not codes. */
    /* Type names and special values are ASCII, so a locale's own case rules must not change them. */
    static void turkishLocale() {
        java.util.Locale saved = java.util.Locale.getDefault();
        java.util.Locale.setDefault(new java.util.Locale("tr"));
        try {
            nonFiniteText();
            equal("a type name in capitals", SDDSUtil.SDDS_STRING, SDDSUtil.identifyType("STRING"));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    /* A file that cannot be read says why. */
    static void readErrors() {
        SDDSFile missing = new SDDSFile(work.resolve("no-such-file.sdds").toString());
        check("a missing file fails", !missing.readFile());
        String text = missing.getErrors();
        check("and says so: " + text, text.contains("does not exist"));
        SDDSFile folder = new SDDSFile(work.toString());
        check("a folder fails", !folder.readFile());
        text = folder.getErrors();
        check("and says so: " + text, text.contains("is a directory"));
        try {
            String name = work.resolve("truncated.sdds").toString();
            SDDSFile f = new SDDSFile(name);
            f.setMode(true);
            f.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
            f.setColumn(0, new double[] { 1, 2, 3 }, 1, 1);
            check("write a file to truncate", f.writeFile());
            byte[] all = Files.readAllBytes(Paths.get(name));
            Files.write(Paths.get(name), Arrays.copyOf(all, all.length - 5));
            SDDSFile cut = new SDDSFile(name);
            check("a truncated file fails", !cut.readFile());
            text = cut.getErrors();
            check("and says so: " + text, text.contains("ends too soon"));
        } catch (java.io.IOException e) {
            check("truncated file test: " + e, false);
        }
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** A field length near the limit must not overflow when added to the position in the line. */
    static void fieldLengthOverflow() throws IOException {
        Path file = work.resolve("field-length.sdds");
        for (int length : new int[] { 2147483647, -2147483647 }) {
            String before = "SDDS1\n&column name=a, type=double, &end\n&column name=b, type=string, field_length=";
            String after = ", &end\n&data mode=ascii, &end\n1\n123 abc\n";
            Files.write(file, bytes(before + (length < 0 ? -100 : 100) + after));
            SDDSFile fits = new SDDSFile(file.toString());
            check("a field that fits reads", fits.readFile());
            Files.write(file, bytes(before + length + after));
            try {
                SDDSFile f = new SDDSFile(file.toString());
                check("field_length " + length + " reads: " + f.getErrors(), f.readFile());
                equal("field_length " + length + " gives the rest of the line", fits.getColumnValues(1, 1, false),
                        f.getColumnValues(1, 1, false));
            } catch (RuntimeException e) {
                check("field_length " + length + " must not throw: " + e, false);
            }
        }
    }

    /* A NUL in ascii data is damage. Reading on past it would give a shortened value with no error. */
    static void nulInAsciiData() throws IOException {
        String two = "&column name=x, type=double, &end\n&column name=y, type=double, &end\n&data mode=ascii, &end\n1\n";
        String[][] refused = {
                { "a double", two + "1.5\0\0 2\n" },
                { "digits after a NUL", two + "2878\0\0\0\u00805 2\n" },
                { "an integer", "&column name=x, type=long, &end\n&column name=y, type=long, &end\n&data mode=ascii, &end\n1\n12\0 2\n" },
                { "a string in a column", "&column name=x, type=string, &end\n&column name=y, type=long, &end\n&data mode=ascii, &end\n1\n\"ab\0cd\" 2\n" },
                { "an array", "&array name=x, type=double, dimensions=1, &end\n&data mode=ascii, &end\n2\n1.5\0 3\n" },
                { "a numeric parameter", "&parameter name=x, type=double, &end\n&data mode=ascii, &end\n1.5\0\n" },
                { "a string parameter", "&parameter name=x, type=string, &end\n&data mode=ascii, &end\nab\0cd\n" },
                { "a row count", "&column name=x, type=double, &end\n&data mode=ascii, &end\n2\0\n1\n2\n" },
                { "an array size", "&array name=x, type=double, dimensions=1, &end\n&data mode=ascii, &end\n2\0\n1 2\n" },
        };
        Path file = work.resolve("nul.sdds");
        for (String[] c : refused) {
            Files.write(file, bytes("SDDS1\n" + c[1]));
            SDDSFile f = new SDDSFile(file.toString());
            check("a NUL in " + c[0] + " is refused", !f.readFile());
            String text = f.getErrors();
            check("and says so: " + text, text.contains("NUL"));
        }
        /* It changes no value, so this is read, although the C reader refuses the file. */
        Files.write(file, bytes("SDDS1\n" + two + "1.5 2 ! a note \0 here\n"));
        SDDSFile comment = new SDDSFile(file.toString());
        check("a NUL in a comment is harmless", comment.readFile());
        equal("and the values stand", new Object[] { Double.valueOf(1.5) }, comment.getColumnValues(0, 1, false));
        /* An escape is not damage. It ends the string, as in C. */
        Files.write(file, bytes("SDDS1\n&column name=s, type=string, &end\n&data mode=ascii, &end\n1\n\"ab\\000cd\"\n"));
        SDDSFile escaped = new SDDSFile(file.toString());
        check("an escaped NUL ends a string", escaped.readFile());
        equal("at that point", new Object[] { "ab" }, escaped.getColumnValues(0, 1, false));
    }

    private static byte[] little(byte[] head, int... values) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(head.length + values.length * 4 + 3)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(head);
        for (int v : values)
            b.putInt(v);
        return b.array();
    }

    /** A damaged count in a small file must not demand all that memory before any data is seen. */
    static void damagedCounts() throws IOException {
        int huge = 2147483000;
        String le = "SDDS1\n!# little-endian\n";
        Object[][] damaged = {
                { "row count", little(bytes(le + "&column name=x, type=double, &end\n&data mode=binary, &end\n"), huge) },
                { "row count, column major", little(bytes(le + "&column name=x, type=double, &end\n&data mode=binary, column_major_order=1, &end\n"), huge) },
                { "array size", little(bytes(le + "&array name=x, type=double, dimensions=1, &end\n&data mode=binary, &end\n"), 0, huge) },
                { "string length", little(bytes(le + "&column name=x, type=string, &end\n&data mode=binary, &end\n"), 1, huge) },
        };
        Path file = work.resolve("damaged.sdds");
        for (Object[] c : damaged) {
            Files.write(file, (byte[]) c[1]);
            SDDSFile f = new SDDSFile(file.toString());
            check("a damaged " + c[0] + " fails", !f.readFile());
            String text = f.getErrors();
            check("because the data ended, not for lack of memory: " + text, text.contains("ends too soon"));
        }
        Files.write(file, bytes("SDDS1\n&array name=x, type=double, dimensions=1, &end\n&data mode=ascii, &end\n" + huge + "\n1 2\n"));
        SDDSFile ascii = new SDDSFile(file.toString());
        check("a damaged ascii array size fails", !ascii.readFile());
        String text = ascii.getErrors();
        check("because the data ended, not for lack of memory: " + text, text.contains("ends prematurely"));
    }

    /** Pages larger than the first allocation grow to the right size in every layout. */
    static void largePages() {
        int n = 200000;
        double[] x = new double[n];
        int[] id = new int[n];
        String[] s = new String[n];
        for (int i = 0; i < n; i++) {
            x[i] = i * 0.5;
            id[i] = i;
            s[i] = "r" + i;
        }
        String name = path("large.sdds");
        for (String layout : new String[] { "binary", "column major", "ascii" }) {
            SDDSFile w = new SDDSFile(name);
            w.setMode(!layout.equals("ascii"));
            w.columnMajorOrder = layout.equals("column major");
            w.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
            w.defineColumn("id", SDDSUtil.SDDS_LONG);
            w.defineColumn("s", SDDSUtil.SDDS_STRING);
            w.defineArray("a", SDDSUtil.SDDS_DOUBLE, 1);
            w.setColumn(0, x, 1, 1);
            w.setColumn(1, id, 1, 1);
            w.setColumn(2, s, 1, 1);
            w.setArrayDim(1, 0, new int[] { n });
            w.setArray(0, x, 1);
            check("write large " + layout, w.writeFile());
            SDDSFile r = read(name);
            Object[] rx = r.getColumnValues(0, 1, false), rid = r.getColumnValues(1, 1, false), rs = r.getColumnValues(2, 1, false),
                    ra = r.getArrayValues(0, 1, false);
            equal("large " + layout + " rows", n, rx.length);
            equal("large " + layout + " array", n, ra.length);
            boolean same = rid.length == n && rs.length == n;
            for (int i = 0; same && i < n; i++)
                same = ((Number) rx[i]).doubleValue() == x[i] && ((Number) rid[i]).intValue() == i && s[i].equals(rs[i])
                        && ((Number) ra[i]).doubleValue() == x[i];
            check("large " + layout + " values", same);
        }
    }

    static int openFiles() {
        return new File("/proc/self/fd").list().length;
    }

    /** A file that cannot be opened must not leave its descriptor for the garbage collector. */
    static void failedOpensClose() throws IOException {
        if (!Files.isDirectory(Paths.get("/proc/self/fd"))) {
            System.out.println("SKIP: no /proc/self/fd to count open files");
            return;
        }
        Path gz = work.resolve("bad-open.sdds.gz"), xz = work.resolve("bad-open.sdds.xz");
        Files.write(gz, new byte[] { 0x1f, (byte) 0x8b, 9, 9, 9 });
        Files.write(xz, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 });
        for (Path p : new Path[] { gz, xz }) {
            int before = openFiles();
            boolean refused = true;
            for (int i = 0; i < 100; i++)
                refused = refused && !new SDDSFile(p.toString()).readFile();
            check(p.getFileName() + " is refused", refused);
            int open = openFiles() - before;
            check(p.getFileName() + " leaves nothing open: " + open, open < 10);
        }
        if (Files.exists(Paths.get("/dev/full"))) {
            for (String suffix : new String[] { "", ".gz", ".xz" }) {
                Path full = work.resolve("full.sdds" + suffix);
                Files.deleteIfExists(full);
                Files.createSymbolicLink(full, Paths.get("/dev/full"));
                int before = openFiles();
                boolean refused = true;
                for (int i = 0; i < 100; i++) {
                    SDDSFile w = new SDDSFile(full.toString());
                    w.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
                    w.setColumn(0, new double[] { 1 }, 1, 1);
                    refused = refused && !w.writeFile();
                }
                check("a full disk is reported for " + suffix, refused);
                int open = openFiles() - before;
                check("and leaves nothing open for " + suffix + ": " + open, open < 10);
            }
        }
    }

    /* Text reaches doubles and floats as C reads it, and never silently becomes 0. */
    static void nonFiniteText() {
        SDDSFile f = new SDDSFile();
        f.defineColumn("d", SDDSUtil.SDDS_DOUBLE);
        f.defineColumn("f", SDDSUtil.SDDS_FLOAT);
        f.defineParameter("p", SDDSUtil.SDDS_DOUBLE);
        String[] text = { "nan", "NaN", "-nan", "inf", "+inf", "-inf", "Infinity", "-INFINITY", "2.5" };
        check("set doubles from text: " + f.getErrors(), f.setColumn(0, text, 1, 1));
        check("set floats from text: " + f.getErrors(), f.setColumn(1, text, 1, 1));
        check("set a parameter from text: " + f.getErrors(), f.setParameter(0, "-inf", 1));
        Object[] d = f.getColumnValues(0, 1, false), s = f.getColumnValues(1, 1, false);
        for (int i = 0; i < 3; i++) {
            check("double from " + text[i], ((Double) d[i]).isNaN());
            check("float from " + text[i], ((Float) s[i]).isNaN());
        }
        for (int i = 3; i < 8; i++) {
            double expected = text[i].startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            equal("double from " + text[i], expected, d[i]);
            equal("float from " + text[i], (float) expected, s[i]);
        }
        equal("an ordinary double", 2.5, d[8]);
        equal("an ordinary float", 2.5f, s[8]);
        equal("a parameter", Double.NEGATIVE_INFINITY, f.getParameterValue(0, 1, false));
    }

    static void setters() {
        SDDSFile f = layoutWithEnums();
        check("set column codes", f.setColumn(0, new int[] { 0, 10, -1 }, 1, 1));
        check("set column long codes", f.setColumn(0, new long[] { 0L }, 1, 1));
        check("set column short codes", f.setColumn(0, new short[] { 10 }, 1, 2));
        check("set column by name", f.setColumn("c", new int[] { -1 }, 1, 3));
        equal("column codes", new int[] { 0, 10, -1 }, f.getColumnEnumCodes(0, 1));
        equal("column labels", new String[] { "OFF", "ON", "FAULT" }, f.getColumnEnumLabels(0, 1));
        check("unmapped column code", !f.setColumn(0, new int[] { 42 }, 1, 1));
        check("error recorded", f.getErrors().contains("Unmapped enum code 42"));
        equal("failed set changes nothing", new int[] { 0, 10, -1 }, f.getColumnEnumCodes(0, 1));
        check("code too wide", !f.setColumn(0, new long[] { 4294967296L + 10 }, 1, 1));
        check("double rejected", !f.setColumn(0, new double[] { 10.0 }, 1, 1));
        check("float rejected", !f.setColumn(0, new float[] { 10f }, 1, 1));
        check("element double rejected", !f.setColumnElement(0, 10.0, 1, 1));
        check("element code", f.setColumnElement(0, 10, 1, 1));
        check("string is a strict code", f.setColumn(0, new String[] { " -1 " }, 1, 1));
        check("string label is not a code", !f.setColumn(0, new String[] { "ON" }, 1, 1));
        check("plus sign rejected", !f.setColumn(0, new String[] { "+10" }, 1, 1));
        check("set labels", f.setColumnLabels(0, new String[] { "ON", "OFF" }, 1, 1));
        equal("labels stored as codes", new int[] { 10, 0, -1 }, f.getColumnEnumCodes(0, 1));
        check("unknown label", !f.setColumnLabels(0, new String[] { "on" }, 1, 1));
        check("object array of values", f.setColumn(0, f.getColumnValues(0, 1, false), 1, 1));
        check("object array of a bad type", !f.setColumn(0, new Object[] { Double.valueOf(1) }, 1, 1));
        Object[] values = f.getColumnValues(0, 1, false);
        check("generic getter gives enum values", values[0] instanceof SDDSEnumValue && !((Object) values[0] instanceof Number));
        equal("value label", "ON", values[0].toString());
        equal("value code", 10, ((SDDSEnumValue) values[0]).getCode());

        check("set parameter code", f.setParameter(0, 10, 1));
        equal("parameter code", Integer.valueOf(10), f.getParameterEnumCode(0, 1));
        equal("parameter label", "ON", f.getParameterEnumLabel(0, 1));
        check("unmapped parameter", !f.setParameter(0, 42, 1));
        check("double parameter rejected", !f.setParameter(0, 10.0, 1));
        check("string parameter code", f.setParameter(0, "-1", 1));
        check("string parameter label rejected", !f.setParameter(0, "ON", 1));
        check("parameter label", f.setParameterLabel(0, "OFF", 1));
        check("parameter bad label", !f.setParameterLabel(0, "x", 1));

        f.setArrayDim(1, 0, new int[] { 2 });
        check("set array", f.setArray(0, new int[] { 10, -1 }, 1));
        equal("array labels", new String[] { "ON", "FAULT" }, f.getArrayEnumLabels(0, 1));
        check("unmapped array", !f.setArray(0, new int[] { 7 }, 1));
        check("double array rejected", !f.setArray(0, new double[] { 1 }, 1));
        check("array labels", f.setArrayLabels(0, new String[] { "OFF", "ON" }, 1));
        check("array bad labels", !f.setArrayLabels(0, new String[] { "x" }, 1));
        check("array of objects", f.setArray(0, new Object[] { Integer.valueOf(10), "0" }, 1));
        f.getErrors();

        // Numeric casts reject enums.
        equal("cast to long", null, silently(() -> SDDSUtil.castArrayAsLong(new Object[0], SDDSUtil.SDDS_ENUM)));
        equal("cast to double", null, silently(() -> SDDSUtil.castArrayAsDouble(new Object[0], SDDSUtil.SDDS_ENUM)));
        equal("cast to float", null, silently(() -> SDDSUtil.castArrayAsFloat(new Object[0], SDDSUtil.SDDS_ENUM)));
        check("enum is not numeric", !SDDSUtil.isNumericType(SDDSUtil.SDDS_ENUM));

        // Definitions.
        SDDSFile g = new SDDSFile();
        check("primitive definer rejects enum", g.defineColumn("a", SDDSUtil.SDDS_ENUM) == -1);
        check("type name definer rejects enum", g.defineColumn("a", "enum") == -1);
        check("primitive definer rejects enum parameter", g.defineParameter("a", SDDSUtil.SDDS_ENUM) == -1);
        check("primitive definer rejects enum array", g.defineArray("a", SDDSUtil.SDDS_ENUM, 1) == -1);
        check("missing dictionary", g.defineEnumColumn("a", null) == -1);
        check("bad dictionary", g.defineEnumColumn("a", "0:A,0:B") == -1);
        check("format rejected", g.defineEnumColumn("a", null, null, null, "%d", "0:A", 0) == -1);
        check("field length rejected", g.defineEnumColumn("a", null, null, null, null, "0:A", 3) == -1);
        check("blank format accepted", g.defineEnumColumn("a", null, null, null, "  ", "0:A", 0) == 0);
        check("duplicate name", g.defineEnumColumn("a", "0:A") == -1);
        check("bad fixed", g.defineEnumParameter("p", null, null, null, null, "0:A", "1") == -1);
        check("fixed plus", g.defineEnumParameter("p", null, null, null, null, "0:A", "+0") == -1);
        check("fixed trailing", g.defineEnumParameter("p", null, null, null, null, "0:A", "0 0") == -1);
        check("good fixed", g.defineEnumParameter("p", null, null, null, null, "0:A", " 0 ") == 0);
        check("array format rejected", g.defineEnumArray("x", null, null, null, "%d", null, "0:A", 0, 1) == -1);
        check("array field length rejected", g.defineEnumArray("x", null, null, null, null, null, "0:A", 2, 1) == -1);
        g.getErrors();

        // Dictionaries, formats and widths do not change through generic mutation.
        g.setColumnFormatString(0, "%d");
        check("format mutation rejected", !g.getErrors().isEmpty());
        equal("format unchanged", null, g.getColumnFormatString(0));
        g.setColumnFormatString(0, null);
        check("clearing a format is harmless", g.getErrors().isEmpty());
        g.setColumnFieldLength(0, 8);
        check("width mutation rejected", !g.getErrors().isEmpty());
        equal("width unchanged", 0, g.getColumnFieldLength(0));
        g.setParameterFixedValue(0, "9");
        check("fixed mutation validated", !g.getErrors().isEmpty());
        equal("fixed unchanged", " 0 ", g.getParameterFixedValue(0));
        g.setParameterFixedValue(0, "0");
        check("valid fixed mutation", g.getErrors().isEmpty());

        check("export check finds enums", !g.checkEnumExport());
        check("export check says why", g.getErrors().contains("cannot preserve enums"));
        SDDSFile h = new SDDSFile();
        h.defineColumn("a", SDDSUtil.SDDS_DOUBLE);
        check("export check passes primitives", h.checkEnumExport());

        // Unset values fail validation when zero is unmapped, and nothing is substituted.
        try {
            Files.deleteIfExists(work.resolve("unset.sdds"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SDDSFile z = new SDDSFile(work.resolve("unset.sdds").toString());
        z.defineEnumColumn("c", "1:A,2:B");
        z.setColumnBlankRows(0, 1, 2);
        z.setColumn(0, new int[] { 1 }, 1, 1);
        check("unset column blocks writing", !z.writeFile());
        String why = z.getErrors();
        check("message names the cell", why.contains("column c") && why.contains("page 1 row 2"));
        check("nothing was written", !Files.exists(work.resolve("unset.sdds")));
        equal("an unset value has no label", null, ((SDDSEnumValue) z.getColumnValues(0, 1, false)[1]).getLabel());
        equal("an unset value shows blank", "", z.getColumnValues(0, 1, false)[1].toString());
        z.setColumn(0, new int[] { 2 }, 1, 2);
        check("set values can be written", z.writeFile());

        SDDSFile y = new SDDSFile(work.resolve("unset-parameter.sdds").toString());
        y.defineEnumParameter("p", "1:A");
        y.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
        y.setColumn(1 - 1, new double[] { 1 }, 1, 1);
        y.insertPage(1);
        check("blank parameter page needs a state", !y.writeFile());
        y.getErrors();

        SDDSFile w = new SDDSFile(work.resolve("mapped-zero.sdds").toString());
        w.defineEnumParameter("p", "0:Z");
        w.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
        w.setColumn(0, new double[] { 1 }, 1, 1);
        w.setParameter(0, 0, 1);
        w.insertPage(1);
        check("zero stands for an unset value when it is mapped", w.writeFile());

        // Single row appends check too.
        SDDSFile s = new SDDSFile(false);
        s.defineEnumColumn("c", "5:A,6:B");
        s.defineColumn("x", SDDSUtil.SDDS_DOUBLE);
        s.setColumn(1, new double[] { 1 }, 1, 1);
        s.setColumn(0, new int[] { 5 }, 1, 1);
        check("add code", s.addColumnElement(1, (long) 6));
        check("add short code", s.addColumnElement("c", (short) 5));
        check("add string code", s.addColumnElement("c", "6"));
        check("add unmapped", !s.addColumnElement("c", (long) 1));
        check("add label", !s.addColumnElement("c", "A"));
        check("add double", !s.addColumnElement("c", 5.0));
        equal("after adding", new int[] { 5, 6, 5, 6 }, s.getColumnEnumCodes(0, 1));
    }

    interface Producer {
        Object get();
    }

    static Object silently(Producer p) {
        PrintStream err = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        try {
            return p.get();
        } finally {
            System.setErr(err);
        }
    }

    static void conversions() throws IOException {
        SDDSFile f = layoutWithEnums();
        f.setParameter(0, 10, 1);
        f.setColumn(0, new int[] { 0, 10, -1 }, 1, 1);
        f.setArrayDim(1, 0, new int[] { 2 });
        f.setArray(0, new int[] { 10, 0 }, 1);

        check("to a target missing a label", !f.convertColumn(0, SDDSUtil.SDDS_ENUM, "7:OFF,8:ON"));
        equal("failed conversion changes nothing", new int[] { 0, 10, -1 }, f.getColumnEnumCodes(0, 1));
        check("remap", f.convertColumn(0, SDDSUtil.SDDS_ENUM, "7:OFF,8:ON,9:FAULT"));
        equal("remapped", new int[] { 7, 8, 9 }, f.getColumnEnumCodes(0, 1));
        equal("new mapping", "7:OFF,8:ON,9:FAULT", f.getColumnEnumValues(0));
        check("to labels", f.convertColumn(0, SDDSUtil.SDDS_STRING, null));
        equal("labels", SDDSUtil.SDDS_STRING, f.getColumnType(0));
        equal("labels values", new Object[] { "OFF", "ON", "FAULT" }, f.getColumnValues(0, 1, false));
        check("no mapping left", f.getColumnEnumValues(0) == null);
        check("back from labels", f.convertColumn(0, SDDSUtil.SDDS_ENUM, "1:OFF,2:ON,3:FAULT"));
        equal("codes from labels", new int[] { 1, 2, 3 }, f.getColumnEnumCodes(0, 1));
        check("to codes", f.convertColumn(0, SDDSUtil.SDDS_LONG, null));
        equal("long values", new Object[] { 1L, 2L, 3L }, f.getColumnValues(0, 1, false));
        check("from codes unmapped", !f.convertColumn(0, SDDSUtil.SDDS_ENUM, "1:OFF,2:ON"));
        check("from codes", f.convertColumn(0, SDDSUtil.SDDS_ENUM, "1:OFF,2:ON,3:FAULT"));
        check("target needs a mapping", !f.convertColumn(0, SDDSUtil.SDDS_ENUM, null));
        check("no conversion to double", !f.convertColumn(0, SDDSUtil.SDDS_DOUBLE, null));

        check("parameter remap", f.convertParameter(0, SDDSUtil.SDDS_ENUM, "2:ON,3:OFF"));
        equal("parameter remapped", Integer.valueOf(2), f.getParameterEnumCode(0, 1));
        check("parameter to string", f.convertParameter(0, SDDSUtil.SDDS_STRING, null));
        equal("parameter string", "ON", f.getParameterValue(0, 1, false));
        check("array remap", f.convertArray(0, SDDSUtil.SDDS_ENUM, "4:ON,5:OFF"));
        equal("array remapped", new int[] { 4, 5 }, f.getArrayEnumCodes(0, 1));
        check("array to long", f.convertArray(0, SDDSUtil.SDDS_LONG, null));
        equal("array long", new Object[] { 4L, 5L }, f.getArrayValues(0, 1, false));
        f.getErrors();

        // A format and a field width do not survive into an enum.
        SDDSFile g = new SDDSFile();
        g.defineColumn("s", null, null, null, "%s", SDDSUtil.SDDS_STRING, 0);
        g.setColumn(0, new String[] { "A" }, 1, 1);
        check("string to enum", g.convertColumn(0, SDDSUtil.SDDS_ENUM, "1:A"));
        equal("format cleared", null, g.getColumnFormatString(0));

        // Fixed values convert too.
        SDDSFile k = new SDDSFile();
        k.defineEnumParameter("p", null, null, null, null, "0:O K,1:a\\\"b", "0");
        check("fixed enum to string", k.convertParameter(0, SDDSUtil.SDDS_STRING, null));
        equal("fixed string value", "O K", k.getParameterValue(0, 1, false));
        check("fixed string to enum", k.convertParameter(0, SDDSUtil.SDDS_ENUM, "5:O K"));
        equal("fixed code", "5", k.getParameterFixedValue(0));
        check("fixed to long", k.convertParameter(0, SDDSUtil.SDDS_LONG, null));
        equal("fixed long", "5", k.getParameterFixedValue(0));
        check("fixed long to enum unmapped", !k.convertParameter(0, SDDSUtil.SDDS_ENUM, "6:x"));
        k.getErrors();
        SDDSFile l = new SDDSFile();
        l.defineEnumParameter("p", null, null, null, null, "0:a\\\"b \\\\c!", "0");
        check("fixed label with specials", l.convertParameter(0, SDDSUtil.SDDS_STRING, null));
        equal("fixed label round trips", "a\"b \\c!", l.getParameterValue(0, 1, false));
        char[] longLabel = new char[2000];
        Arrays.fill(longLabel, 'x');
        SDDSFile m = new SDDSFile();
        m.defineEnumParameter("p", null, null, null, null, "0:" + new String(longLabel), "0");
        check("long fixed label cannot become legacy text", !m.convertParameter(0, SDDSUtil.SDDS_STRING, null));
        check("message explains the limit", m.getErrors().contains("too long"));

        // Not after the layout has been written to an open file.
        SDDSFile n = new SDDSFile(work.resolve("streaming.sdds").toString());
        n.setNoRowCount(true);
        n.defineEnumColumn("c", "1:A");
        n.setColumn(0, new int[] { 1 }, 1, 1);
        check("streaming write", n.writeFile());
        check("no conversion once written", !n.convertColumn(0, SDDSUtil.SDDS_LONG, null));
        check("no new enum once written", n.defineEnumColumn("d", "1:A") == -1);
        n.getErrors();
        check("add a row", n.addColumnElement("c", (long) 1));
        check("write a row", n.writeRow());
        check("close", n.closeOutput());
        SDDSFile back = read(work.resolve("streaming.sdds").toString());
        check("streamed rows read back", back.getRowCount(1) >= 1);
        check("fresh after closing", n.convertColumn(0, SDDSUtil.SDDS_LONG, null));

        // Duplicating keeps dictionaries and stays independent.
        SDDSFile src = layoutWithEnums();
        src.setParameter(0, -1, 1);
        src.setColumn(0, new int[] { 10 }, 1, 1);
        src.setArrayDim(1, 0, new int[] { 1 });
        src.setArray(0, new int[] { 0 }, 1);
        SDDSFile dup = new SDDSFile();
        check("duplicate", dup.duplicateCopy(src, true, true, true));
        equal("duplicate mapping", src.getColumnEnumValues(0), dup.getColumnEnumValues(0));
        equal("duplicate column", new int[] { 10 }, dup.getColumnEnumCodes(0, 1));
        equal("duplicate parameter", Integer.valueOf(-1), dup.getParameterEnumCode(0, 1));
        equal("duplicate array", new int[] { 0 }, dup.getArrayEnumCodes(0, 1));
        dup.setColumn(0, new int[] { -1 }, 1, 1);
        equal("duplicate is independent", new int[] { 10 }, src.getColumnEnumCodes(0, 1));
    }

    static int writtenVersion(SDDSFile f, String name) throws IOException {
        f.setFileName(work.resolve(name).toString());
        if (!f.writeFile())
            throw new IllegalStateException(f.getErrors());
        byte[] b = Files.readAllBytes(work.resolve(name));
        return b[4] - '0';
    }

    /** Enum-free layouts keep the minimum version, so older readers can read them. */
    static void versions() throws IOException {
        SDDSFile f = new SDDSFile();
        f.defineColumn("a", SDDSUtil.SDDS_DOUBLE);
        equal("double", 1, writtenVersion(f, "v1.sdds"));
        f.setMode(true);
        equal("binary double", 1, writtenVersion(f, "v1b.sdds"));
        check("SDDS1 declares its byte order in a comment", new String(Files.readAllBytes(work.resolve("v1b.sdds")), StandardCharsets.ISO_8859_1).contains("!# "));
        f.defineColumn("b", SDDSUtil.SDDS_USHORT);
        equal("ushort", 2, writtenVersion(f, "v2.sdds"));
        f.columnMajorOrder = true;
        equal("column major binary", 3, writtenVersion(f, "v3.sdds"));
        f.setMode(false);
        equal("column major ascii", 2, writtenVersion(f, "v2a.sdds"));
        f.defineColumn("c", SDDSUtil.SDDS_LONGDOUBLE);
        equal("longdouble", 4, writtenVersion(f, "v4.sdds"));
        f.defineColumn("d", SDDSUtil.SDDS_ULONG64);
        equal("ulong64", 5, writtenVersion(f, "v5.sdds"));
        f.defineEnumColumn("e", "0:A");
        equal("enum", 6, writtenVersion(f, "v6.sdds"));
        f.deleteColumn("e");
        equal("enum removed", 5, writtenVersion(f, "v5b.sdds"));

        SDDSFile g = new SDDSFile();
        g.defineColumn("a", SDDSUtil.SDDS_DOUBLE);
        check("minimum version", g.setMinimumVersion(6));
        check("bad minimum version", !g.setMinimumVersion(7));
        g.getErrors();
        equal("forced SDDS6", 6, writtenVersion(g, "v6b.sdds"));
        SDDSFile back = read(work.resolve("v6b.sdds").toString());
        equal("forced SDDS6 reads back", SDDSUtil.SDDS_DOUBLE, back.getColumnType(0));
        equal("the version read", 6, back.layoutVersion);
    }

    static void longDouble() throws IOException {
        // One beyond double precision, so a double could not hold these bits.
        byte[] precise = new byte[16];
        precise[0] = 1;
        precise[7] = (byte) 0x80;
        precise[8] = (byte) 0xff;
        precise[9] = 0x3f;
        SDDSLongDouble v = SDDSLongDouble.fromBytes(precise, false);
        check("keeps every bit", Arrays.equals(precise, v.toBytes(false)));
        byte[] big = v.toBytes(true);
        equal("big endian reverses the first twelve bytes", precise[0], big[11]);
        equal("big endian sign and exponent", (byte) 0x3f, big[2]);
        equal("big endian decode", v, SDDSLongDouble.fromBytes(big, true));
        equal("text round trip", v, SDDSLongDouble.parse(v.toString()));
        equal("rounds to one", 1.0, v.doubleValue());

        double[] doubles = { 0.0, 1.0, -1.0, 0.1, 1e300, 1e-300, Double.MIN_VALUE, Double.MAX_VALUE, -2.5, 3.141592653589793 };
        for (double d : doubles) {
            equal("from double " + d, d, SDDSLongDouble.valueOf(d).doubleValue());
            equal("through text " + d, SDDSLongDouble.valueOf(d), SDDSLongDouble.parse(SDDSLongDouble.valueOf(d).toString()));
        }
        equal("exact one", "1.00000000000000000000e+00", SDDSLongDouble.valueOf(1.0).toString());
        equal("exact long", SDDSLongDouble.parse("9007199254740993"), SDDSLongDouble.valueOf(9007199254740993L));
        equal("min long", new BigDecimal(Long.MIN_VALUE), SDDSLongDouble.valueOf(Long.MIN_VALUE).toBigDecimal());
        check("exact decimal", new BigDecimal("0.5").compareTo(SDDSLongDouble.parse("0.5").toBigDecimal()) == 0);
        check("nan", SDDSLongDouble.parse("nan").isNaN() && Double.isNaN(SDDSLongDouble.parse("NaN").doubleValue()));
        check("infinity", SDDSLongDouble.parse("-inf").isInfinite() && SDDSLongDouble.parse("Infinity").doubleValue() == Double.POSITIVE_INFINITY);
        equal("overflow", "inf", SDDSLongDouble.parse("1e5000").toString());
        equal("underflow", 0.0, SDDSLongDouble.parse("1e-5000").doubleValue());
        check("largest normal round trips", SDDSLongDouble.parse("1.18973149535723176502e+4932").equals(SDDSLongDouble.parse(SDDSLongDouble.parse("1.18973149535723176502e+4932").toString())));
        check("denormal round trips", SDDSLongDouble.parse("3.64519953188247460253e-4951").toBigDecimal().signum() > 0);
        check("prefix", SDDSLongDouble.parse("1.5abc").doubleValue() == 1.5);
        check("no number", rejects(() -> SDDSLongDouble.parse("abc")));
        check("order", SDDSLongDouble.valueOf(1.0).compareTo(SDDSLongDouble.valueOf(2.0)) < 0);

        SDDSFile f = new SDDSFile(work.resolve("ld.sdds").toString());
        f.defineParameter("p", SDDSUtil.SDDS_LONGDOUBLE);
        f.defineColumn("c", SDDSUtil.SDDS_LONGDOUBLE);
        f.defineArray("a", SDDSUtil.SDDS_LONGDOUBLE, 1);
        f.setParameter(0, 2.5, 1);
        f.setColumn(0, new double[] { 1.5, 2.5 }, 1, 1);
        f.setColumn(0, new Object[] { v }, 1, 3);
        f.setArrayDim(1, 0, new int[] { 2 });
        f.setArray(0, new Object[] { v, Double.valueOf(0.25) }, 1);
        for (boolean binary : new boolean[] { false, true }) {
            for (boolean little : new boolean[] { false, true }) {
                f.setMode(binary);
                f.setEndian(little);
                check("writes long doubles", f.writeFile());
                SDDSFile b = read(work.resolve("ld.sdds").toString());
                Object[] values = b.getColumnValues(0, 1, false);
                equal("long double keeps precision, binary=" + binary + " little=" + little, v, values[2]);
                equal("long double", SDDSLongDouble.valueOf(1.5), values[0]);
                equal("long double parameter", SDDSLongDouble.valueOf(2.5), b.getParameterValue(0, 1, false));
                equal("long double array", v, b.getArrayValues(0, 1, false)[0]);
            }
        }
    }

    static void unsigned() throws IOException {
        SDDSFile f = new SDDSFile(work.resolve("u64.sdds").toString());
        f.defineParameter("p", SDDSUtil.SDDS_ULONG64);
        f.defineColumn("c", SDDSUtil.SDDS_ULONG64);
        f.defineArray("a", SDDSUtil.SDDS_ULONG64, 1);
        BigInteger top = new BigInteger("18446744073709551615");
        f.setParameter(0, "18446744073709551615", 1);
        f.setColumn(0, new String[] { "18446744073709551615", "1", "9223372036854775808" }, 1, 1);
        f.setArrayDim(1, 0, new int[] { 1 });
        f.setArray(0, new long[] { -1L }, 1);
        for (boolean binary : new boolean[] { false, true }) {
            f.setMode(binary);
            check("writes ulong64", f.writeFile());
            SDDSFile b = read(work.resolve("u64.sdds").toString());
            Object[] c = b.getColumnValues(0, 1, false);
            equal("largest value", top, c[0]);
            equal("small value stays a Long", Long.valueOf(1), c[1]);
            equal("just over the signed range", new BigInteger("9223372036854775808"), c[2]);
            equal("parameter", top, b.getParameterValue(0, 1, false));
            equal("array", top, b.getArrayValues(0, 1, false)[0]);
            equal("casts keep the raw bits", new long[] { -1L, 1L, Long.MIN_VALUE }, SDDSUtil.castArrayAsLong(c, SDDSUtil.SDDS_ULONG64));
        }
    }

    static void strings() throws IOException {
        String[] samples = { "plain", "two words", "", " ", "caf\u00e9", "\u96ea", "\ud83d\ude00", "quote\"inside", "back\\slash", "bang!x", "tab\tnew\nline", "\\n", "a\\", "'", "$x", "\u0001", "#h", "&end" };
        SDDSFile f = new SDDSFile(work.resolve("strings.sdds").toString());
        f.defineParameter("p", SDDSUtil.SDDS_STRING);
        f.defineColumn("c", SDDSUtil.SDDS_STRING);
        f.defineColumn("x", SDDSUtil.SDDS_CHARACTER);
        f.defineArray("a", SDDSUtil.SDDS_STRING, 1);
        char[] chars = new char[samples.length];
        Arrays.fill(chars, 'q');
        chars[1] = ' ';
        chars[2] = '!';
        chars[3] = '\\';
        chars[4] = '"';
        chars[5] = 0xe9;
        f.setColumn(0, samples, 1, 1);
        f.setColumn(1, chars, 1, 1);
        f.setArrayDim(1, 0, new int[] { samples.length });
        f.setArray(0, samples, 1);
        for (int page = 1; page <= 1; page++)
            f.setParameter(0, samples[1], page);
        for (boolean binary : new boolean[] { false, true }) {
            f.setMode(binary);
            check("writes strings", f.writeFile());
            SDDSFile b = read(work.resolve("strings.sdds").toString());
            equal("strings binary=" + binary, samples, Arrays.copyOf(b.getColumnValues(0, 1, false), samples.length, String[].class));
            equal("array strings binary=" + binary, samples, Arrays.copyOf(b.getArrayValues(0, 1, false), samples.length, String[].class));
            equal("parameter string", samples[1], b.getParameterValue(0, 1, false));
            Object[] x = b.getColumnValues(1, 1, false);
            equal("space character", "\\040", x[1]);
            equal("punctuation", "\\041", x[2]);
            equal("high byte", "\u00e9", x[5]);
        }

        // Bytes that are not UTF-8 stay exactly as they were.
        byte[] raw = { 'S', 'D', 'D', 'S', '5', '\n', '&', 'c', 'o', 'l', 'u', 'm', 'n', ' ', 'n', 'a', 'm', 'e', '=', 's', ',', ' ', 't', 'y', 'p', 'e', '=', 's', 't', 'r', 'i', 'n', 'g', ',', ' ', 'u', 'n', 'i', 't', 's', '=', (byte) 0xb5, 'm', ' ', '&', 'e', 'n', 'd', '\n', '&', 'd', 'a', 't', 'a', ' ', 'm', 'o', 'd', 'e', '=', 'b', 'i', 'n', 'a', 'r', 'y', ',', ' ', 'e', 'n', 'd', 'i', 'a', 'n', '=', 'l', 'i', 't', 't', 'l', 'e', ' ', '&', 'e', 'n', 'd', '\n', 1, 0, 0, 0, 2, 0, 0, 0, (byte) 0xff, 'a' };
        SDDSFile g = read(bytesFile(raw));
        g.setFileName(work.resolve("latin.sdds").toString());
        check("rewrite non UTF-8", g.writeFile());
        byte[] rewritten = Files.readAllBytes(work.resolve("latin.sdds"));
        check("non UTF-8 value kept", new String(rewritten, StandardCharsets.ISO_8859_1).endsWith("\u00ffa"));
        check("non UTF-8 header kept", new String(rewritten, StandardCharsets.ISO_8859_1).contains("units=\u00b5m"));

        // Reading ASCII the way C does.
        SDDSFile h = read(file("SDDS5\n&parameter name=p, type=string &end\n&parameter name=q, type=string &end\n&parameter name=r, type=string &end\n&column name=a, type=string &end\n&column name=b, type=character &end\n&data mode=ascii &end\nwhole line! comment\n\"first\" second\ncaf\\303\\251 \\! x\n2\n\"a \\\"b\\\" c\" \\101\nnot\\ quoted x\n"));
        equal("string parameter is the whole line", "whole line", h.getParameterValue(0, 1, false));
        equal("quoted string parameter is the token", "first", h.getParameterValue(1, 1, false));
        equal("octal escapes make bytes", "caf\u00e9 ! x", h.getParameterValue(2, 1, false));
        equal("quoted column string", "a \"b\" c", h.getColumnValues(0, 1, false)[0]);
        equal("octal character", "A", h.getColumnValues(1, 1, false)[0]);
    }

    /** A layout using every type, on several pages. */
    static SDDSFile everyType(String name) {
        SDDSFile f = new SDDSFile(name);
        f.setDescription("all types \"quoted\"", "for testing");
        int[] types = { SDDSUtil.SDDS_LONGDOUBLE, SDDSUtil.SDDS_DOUBLE, SDDSUtil.SDDS_FLOAT, SDDSUtil.SDDS_LONG64, SDDSUtil.SDDS_ULONG64, SDDSUtil.SDDS_LONG, SDDSUtil.SDDS_ULONG, SDDSUtil.SDDS_SHORT, SDDSUtil.SDDS_USHORT, SDDSUtil.SDDS_STRING, SDDSUtil.SDDS_CHARACTER };
        for (int t : types) {
            String n = SDDSUtil.getTypeName(t);
            f.defineParameter("p" + n, n, "m", "parameter " + n, null, t, null);
            f.defineArray("a" + n, n, "m", "array " + n, null, null, t, 0, 1);
            f.defineColumn("c" + n, n, "m", "column " + n, null, t, 0);
        }
        f.defineParameter("pfixed", "sym", null, null, null, SDDSUtil.SDDS_DOUBLE, "1.5");
        f.defineParameter("pstr", null, null, null, null, SDDSUtil.SDDS_STRING, "fixed text");
        f.defineArray("a2d", null, null, null, null, "group", SDDSUtil.SDDS_LONG, 0, 2);
        f.defineEnumParameter("penum", null, null, null, null, "0:OFF,10:ON,-1:FAULT", null);
        f.defineEnumParameter("pfixedenum", null, null, null, null, "0:OFF,10:ON,-1:FAULT", "10");
        f.defineEnumArray("aenum", "0:OFF,10:ON,-1:FAULT", 1);
        f.defineEnumColumn("cenum", "s", "u", "d", null, "0:OFF,10:a\\,b\\:c\\\\d\\\"e\\n\\r\\t\u96ea ,-1:FAULT", 0);
        f.defineEnumColumn("cenum2", "-7:\u96ea,3:x y");
        for (int page = 1; page <= 3; page++) {
            int rows = page == 2 ? 0 : 3 + page;
            for (int t : types) {
                String n = SDDSUtil.getTypeName(t);
                int i = f.getParameterIndex("p" + n);
                f.setParameter(i, t == SDDSUtil.SDDS_STRING ? "text " + page : Integer.toString(page * 7), page);
                int a = f.getArrayIndex("a" + n);
                f.setArrayDim(page, a, new int[] { page });
                String[] av = new String[page];
                for (int k = 0; k < page; k++)
                    av[k] = t == SDDSUtil.SDDS_CHARACTER ? "z" : Integer.toString(k + 1);
                f.setArray(a, av, page);
                int c = f.getColumnIndex("c" + n);
                if (rows == 0) {
                    f.setColumn(c, new String[0], page, 1);
                    continue;
                }
                String[] cv = new String[rows];
                for (int k = 0; k < rows; k++)
                    cv[k] = t == SDDSUtil.SDDS_CHARACTER ? "y" : t == SDDSUtil.SDDS_STRING ? "row " + k : Integer.toString(k + page);
                f.setColumn(c, cv, page, 1);
            }
            f.setArrayDim(page, f.getArrayIndex("a2d"), new int[] { 2, page });
            int[] grid = new int[2 * page];
            for (int k = 0; k < grid.length; k++)
                grid[k] = k * 11;
            f.setArray(f.getArrayIndex("a2d"), grid, page);
            f.setParameter(f.getParameterIndex("penum"), page == 1 ? 10 : -1, page);
            f.setArrayDim(page, f.getArrayIndex("aenum"), new int[] { page });
            int[] ae = new int[page];
            ae[page - 1] = -1;
            f.setArray(f.getArrayIndex("aenum"), ae, page);
            int[] c1 = new int[rows], c2 = new int[rows];
            for (int k = 0; k < rows; k++) {
                c1[k] = k % 3 == 0 ? 0 : k % 3 == 1 ? 10 : -1;
                c2[k] = k % 2 == 0 ? -7 : 3;
            }
            f.setColumn(f.getColumnIndex("cenum"), c1, page, 1);
            f.setColumn(f.getColumnIndex("cenum2"), c2, page, 1);
        }
        return f;
    }

    /** A complete text form of a file's layout and data. */
    static String dump(SDDSFile f) {
        StringBuilder s = new StringBuilder();
        s.append("description ").append(f.descriptionText).append('|').append(f.descriptionContents).append('\n');
        String[] p = f.getParameterNames(), a = f.getArrayNames(), c = f.getColumnNames();
        for (int i = 0; p != null && i < p.length; i++)
            s.append("parameter ").append(p[i]).append(' ').append(SDDSUtil.getTypeName(f.getParameterType(i))).append(' ').append(f.getParameterSymbol(i)).append(' ').append(f.getParameterUnits(i)).append(' ').append(f.getParameterDescription(i)).append(' ').append(f.getParameterFixedValue(i)).append(' ').append(f.getParameterEnumValues(i)).append('\n');
        for (int i = 0; a != null && i < a.length; i++)
            s.append("array ").append(a[i]).append(' ').append(SDDSUtil.getTypeName(f.getArrayType(i))).append(' ').append(f.getArrayDimensions(i)).append(' ').append(f.getArrayGroupName(i)).append(' ').append(f.getArrayEnumValues(i)).append('\n');
        for (int i = 0; c != null && i < c.length; i++)
            s.append("column ").append(c[i]).append(' ').append(SDDSUtil.getTypeName(f.getColumnType(i))).append(' ').append(f.getColumnSymbol(i)).append(' ').append(f.getColumnUnits(i)).append(' ').append(f.getColumnDescription(i)).append(' ').append(f.getColumnEnumValues(i)).append('\n');
        int pages = f.pageCount();
        s.append("pages ").append(pages).append('\n');
        for (int page = 1; page <= pages; page++) {
            s.append("page ").append(page).append(" rows ").append(f.getRowCount(page)).append('\n');
            for (int i = 0; p != null && i < p.length; i++)
                s.append(p[i]).append('=').append(valueText(f.getParameterValue(i, page, false))).append('\n');
            for (int i = 0; a != null && i < a.length; i++) {
                s.append(a[i]).append(Arrays.toString(f.getArrayDim(page, i))).append('=');
                Object[] v = f.getArrayValues(i, page, false);
                for (int k = 0; v != null && k < v.length; k++)
                    s.append(valueText(v[k])).append(';');
                s.append('\n');
            }
            for (int i = 0; c != null && i < c.length; i++) {
                s.append(c[i]).append('=');
                Object[] v = f.getColumnValues(i, page, false);
                for (int k = 0; v != null && k < v.length; k++)
                    s.append(valueText(v[k])).append(';');
                s.append('\n');
            }
        }
        return s.toString();
    }

    static String valueText(Object o) {
        if (o instanceof SDDSEnumValue)
            return ((SDDSEnumValue) o).getCode() + ":" + ((SDDSEnumValue) o).getLabel();
        return String.valueOf(o);
    }

    /** Every encoding reads back to the same content. */
    static void roundTrips() throws IOException {
        String expected = dump(everyType(null));
        for (boolean binary : new boolean[] { false, true }) {
            for (boolean little : new boolean[] { false, true }) {
                for (boolean columnMajor : new boolean[] { false, true }) {
                    if (!binary && (columnMajor || little))
                        continue;
                    for (String ext : new String[] { "", ".gz", ".xz", ".lzma" }) {
                        String name = work.resolve("all-" + (binary ? "b" : "a") + (little ? "l" : "B") + (columnMajor ? "c" : "r") + ".sdds" + ext).toString();
                        String label = new File(name).getName();
                        SDDSFile f = everyType(name);
                        f.setMode(binary);
                        f.setEndian(little);
                        f.columnMajorOrder = columnMajor;
                        check("write " + label + " " , f.writeFile());
                        equal("is SDDS6 " + label, 6, f.layoutVersion);
                        SDDSFile b = new SDDSFile(name);
                        boolean ok = b.readFile();
                        check("read " + label + ": " + b.getErrors(), ok);
                        if (ok)
                            equal("content of " + label, expected, dump(b));
                    }
                }
            }
        }

        // Writing what was read gives the same bytes.
        SDDSFile once = everyType(work.resolve("first.sdds").toString());
        once.setMode(true);
        once.writeFile();
        SDDSFile back = read(work.resolve("first.sdds").toString());
        back.setFileName(work.resolve("second.sdds").toString());
        check("rewrite", back.writeFile());
        check("rewrite gives the same bytes", Arrays.equals(Files.readAllBytes(work.resolve("first.sdds")), Files.readAllBytes(work.resolve("second.sdds"))));

        // Through a stream too.
        SDDSFile s = new SDDSFile(Files.newInputStream(work.resolve("first.sdds")));
        check("read a stream: " + s.getErrors(), s.readFile());
        equal("stream content", expected, dump(s));
    }

    static void compression() throws IOException {
        // Data that compresses well, data that does not, and enough to span LZMA2 chunks.
        byte[][] payloads = new byte[4][];
        payloads[0] = new byte[0];
        payloads[1] = "hello hello hello hello hello hello".getBytes(StandardCharsets.ISO_8859_1);
        Random r = new Random(1234);
        payloads[2] = new byte[300000];
        r.nextBytes(payloads[2]);
        for (int i = 0; i < payloads[2].length; i++)
            payloads[2][i] = payloads[2][i] == 0 ? 1 : payloads[2][i];
        payloads[3] = new byte[9000000];
        for (int i = 0; i < payloads[3].length; i++)
            payloads[3][i] = (byte) ((i % 251 == 0) ? 1 + r.nextInt(255) : 1 + ((i / 4096) & 0x3f));
        for (int i = 0; i < payloads.length; i++) {
            String name = work.resolve("xz-" + i + ".sdds.xz").toString();
            SDDSFile f = new SDDSFile(name);
            f.defineColumn("s", SDDSUtil.SDDS_STRING);
            f.setMode(true);
            String[] text = { new String(payloads[i], StandardCharsets.ISO_8859_1) };
            f.setColumn(0, text, 1, 1);
            check("write large xz " + i, f.writeFile());
            SDDSFile b = read(name);
            equal("read large xz " + i, text[0], b.getColumnValues(0, 1, false)[0]);
        }
        check("corrupt xz is an error", !readsFile(corrupted(work.resolve("xz-1.sdds.xz")), ".xz"));
    }

    static String corrupted(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        b[b.length / 2] ^= 0x55;
        Path out = work.resolve("corrupt.sdds.xz");
        Files.write(out, b);
        return out.toString();
    }

    static boolean readsFile(String name, String ext) {
        SDDSFile f = new SDDSFile(name);
        boolean ok = f.readFile();
        f.getErrors();
        return ok;
    }

    /** Page structures that are easy to get wrong. */
    static void edgePages() throws IOException {
        // Empty pages survive, including trailing ones, in every encoding.
        for (boolean binary : new boolean[] { false, true }) {
            String name = work.resolve("empty-" + binary + ".sdds").toString();
            SDDSFile f = new SDDSFile(name);
            f.defineColumn("d", SDDSUtil.SDDS_DOUBLE);
            f.defineColumn("s", SDDSUtil.SDDS_STRING);
            f.setMode(binary);
            f.setColumn(0, new double[] { 1 }, 1, 1);
            f.setColumn(1, new String[] { "a" }, 1, 1);
            f.makeBlankPages(3);
            check("write empty pages", f.writeFile());
            SDDSFile b = read(name);
            equal("empty pages count binary=" + binary, 3, b.pageCount());
            equal("empty page rows", 0, b.getRowCount(3));
        }
        // A header with no pages is a valid file in both modes.
        for (String mode : new String[] { "ascii", "binary" }) {
            SDDSFile f = new SDDSFile(file("SDDS6\n&column name=a, type=enum, enum_values=\"0:A\" &end\n&data mode=" + mode + " &end\n"));
            check("no pages " + mode + ": " + f.getErrors(), f.readFile());
            equal("no pages count", 0, f.pageCount());
        }
        // Only fixed values: one page in ascii.
        SDDSFile fx = read(file("SDDS6\n&parameter name=p, type=enum, enum_values=\"0:A\", fixed_value=\"0\" &end\n&data mode=ascii &end\n"));
        equal("only fixed values has one page", 1, fx.pageCount());
        equal("only fixed value", "A", fx.getParameterEnumLabel(0, 1));

        // No row counts ends a page at a blank line or the end of the file.
        SDDSFile nr = read(file("SDDS1\n&column name=a, type=long &end\n&column name=b, type=string &end\n&data mode=ascii, no_row_counts=1 &end\n1 x\n2 y\n\n3 z\n"));
        equal("no row counts pages", 2, nr.pageCount());
        equal("no row counts rows", 2, nr.getRowCount(1));
        equal("no row counts last page", Long.valueOf(3), nr.getColumnValues(0, 2, false)[0]);

        // Lines per row and additional header lines.
        SDDSFile lp = read(file("SDDS1\n&column name=a, type=long &end\n&column name=b, type=long &end\n&data mode=ascii, lines_per_row=2, additional_header_lines=2 &end\nignored\nalso ignored\n2\n1\n2\n3\n4\n"));
        equal("lines per row", new Object[] { 2L, 4L }, lp.getColumnValues(1, 1, false));
        check("wrong lines per row", !reads("SDDS1\n&column name=a, type=long &end\n&column name=b, type=long &end\n&data mode=ascii &end\n1\n1\n2\n"));

        // Fixed width fields.
        SDDSFile fw = read(file("SDDS1\n&column name=a, type=long, field_length=3 &end\n&column name=b, type=string, field_length=-4 &end\n&data mode=ascii &end\n1\n12 ab  \n"));
        equal("fixed field number", Long.valueOf(12), fw.getColumnValues(0, 1, false)[0]);
        equal("fixed field string", "ab", fw.getColumnValues(1, 1, false)[0]);

        // Arrays that span lines, are empty, or have several dimensions.
        SDDSFile ar = read(file("SDDS1\n&array name=a, type=long, dimensions=2 &end\n&array name=e, type=double &end\n&data mode=ascii &end\n2 2 ! comment\n1 2\n3 4\n0\n"));
        equal("array across lines", new Object[] { 1L, 2L, 3L, 4L }, ar.getArrayValues(0, 1, false));
        equal("array dims", new int[] { 2, 2 }, ar.getArrayDim(1, 0));
        equal("empty array", new Object[0], ar.getArrayValues(1, 1, false));
        check("negative dimension", !reads("SDDS1\n&array name=a, type=long &end\n&data mode=ascii &end\n-1\n"));
        check("array data missing", !reads("SDDS1\n&array name=a, type=long &end\n&data mode=ascii &end\n2\n1\n"));

        // Truncated data.
        check("truncated rows", !reads("SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n3\n1\n"));
        check("bad row count", !reads("SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\nmany\n1\n"));
        SDDSFile tr = read(file("SDDS1\n!# fixed-rowcount\n&column name=a, type=long &end\n&data mode=ascii &end\n3\n1\n"));
        equal("fixed row count recovers", 1, tr.getRowCount(1));
        SDDSFile tp = read(file("SDDS1\n&parameter name=p, type=long &end\n&column name=a, type=long &end\n&data mode=ascii &end\n1\n1\n5\n7\n"));
        equal("page cut off before its row count does not exist", 1, tp.pageCount());
        byte[] bin = "SDDS6\n&column name=a, type=enum, enum_values=\"1:A\" &end\n&data mode=binary, endian=little &end\n".getBytes(StandardCharsets.ISO_8859_1);
        check("truncated binary", !readsBytes(concat(bin, new byte[] { 2, 0, 0, 0, 1, 0, 0, 0 })));
        check("unmapped binary", !readsBytes(concat(bin, new byte[] { 1, 0, 0, 0, 2, 0, 0, 0 })));
        check("binary after a page", readsBytes(concat(bin, new byte[] { 1, 0, 0, 0, 1, 0, 0, 0 })));
        check("negative binary rows", !readsBytes(concat(bin, new byte[] { -1, -1, -1, -1 })));
        SDDSFile wide = read(bytesFile(concat(bin, new byte[] { 0, 0, 0, (byte) 0x80, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0 })));
        equal("64-bit row count", 1, wide.getRowCount(1));
        check("too many rows for Java", !readsBytes(concat(bin, new byte[] { 0, 0, 0, (byte) 0x80, 0, 0, 0, 0, 1, 0, 0, 0 })));

        // Unset array dimensions, and arrays whose dimensions disagree with their data.
        SDDSFile mismatch = new SDDSFile(work.resolve("mismatch.sdds").toString());
        mismatch.defineArray("a", SDDSUtil.SDDS_LONG, 1);
        mismatch.setArrayDim(1, 0, new int[] { 5 });
        mismatch.setArray(0, new long[] { 1, 2 }, 1);
        check("inconsistent array is not written", !mismatch.writeFile());
        mismatch.getErrors();

        // Rows and pages can be edited.
        SDDSFile e = new SDDSFile();
        e.defineEnumColumn("c", "1:A,2:B");
        e.setColumn(0, new int[] { 1, 2, 1 }, 1, 1);
        e.deleteRow(1, 2);
        equal("delete row", new int[] { 1, 1 }, e.getColumnEnumCodes(0, 1));
        check("insert page", e.insertPage(1));
        equal("pages after insert", 2, e.pageCount());
        equal("inserted page is empty", 0, e.getRowCount(1));
        equal("data moved", new int[] { 1, 1 }, e.getColumnEnumCodes(0, 2));
        check("delete page", e.deletePage(1));
        equal("data back", new int[] { 1, 1 }, e.getColumnEnumCodes(0, 1));

        // Names and formats follow the C rules.
        check("ascii name", SDDSUtil.isValidName("a.b:c#d"));
        check("non-ascii name", !SDDSUtil.isValidName("caf\u00e9"));
        check("digit first", !SDDSUtil.isValidName("1a"));
        check("double format", SDDSUtil.verifyPrintfFormat("%21.15e", SDDSUtil.SDDS_DOUBLE));
        check("wrong format", !SDDSUtil.verifyPrintfFormat("%d", SDDSUtil.SDDS_DOUBLE));
        check("long format", SDDSUtil.verifyPrintfFormat("%ld", SDDSUtil.SDDS_LONG));
        check("short format", SDDSUtil.verifyPrintfFormat("%hd", SDDSUtil.SDDS_SHORT));
        check("short needs h", !SDDSUtil.verifyPrintfFormat("%d", SDDSUtil.SDDS_SHORT));
        check("string format", SDDSUtil.verifyPrintfFormat("%-10s", SDDSUtil.SDDS_STRING));
        check("char format", SDDSUtil.verifyPrintfFormat("%c", SDDSUtil.SDDS_CHARACTER));
        check("no format for enums", !SDDSUtil.verifyPrintfFormat("%d", SDDSUtil.SDDS_ENUM));
        equal("type id", 12, SDDSUtil.SDDS_ENUM);
        equal("type name", "enum", SDDSUtil.getTypeName(12));
        equal("type lookup ignores case", 12, SDDSUtil.identifyType("ENUM"));
        equal("exact type lookup", 0, SDDSUtil.identifyTypeExactly("ENUM"));
        equal("unknown type", 0, SDDSUtil.identifyType("bogus"));
        equal("previous ids unchanged", 11, SDDSUtil.identifyType("character"));
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static boolean readsBytes(byte[] content) throws IOException {
        SDDSFile f = new SDDSFile(bytesFile(content));
        boolean ok = f.readFile();
        f.getErrors();
        return ok;
    }

    // ---- interchange with the C toolkit ----

    static class Result {
        int code;
        String output;
    }

    static Result run(Map<String, String> env, String program, String... args) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(bin.resolve(program).toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().remove("SDDS_OUTPUT_ENDIANESS");
        if (env != null)
            pb.environment().putAll(env);
        Process p = pb.start();
        Result r = new Result();
        r.output = new String(p.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        r.code = p.waitFor();
        return r;
    }

    static Result run(String program, String... args) throws Exception {
        return run(null, program, args);
    }

    static String path(String name) {
        return work.resolve(name).toString();
    }

    /** Build a file with the C tools holding every type and an enum in each element kind. */
    static String makeWithC() throws Exception {
        String[] pages = new String[2];
        for (int page = 0; page < 2; page++) {
            pages[page] = path("c-page" + page + ".sdds");
            String v = page == 0 ? "1" : "2";
            List<String> a = new ArrayList<String>(Arrays.asList(pages[page], "-ascii", "-description=C made", "-contents=for interchange"));
            String[] names = { "longdouble", "double", "float", "long64", "ulong64", "long", "ulong", "short", "ushort" };
            for (String t : names) {
                a.addAll(Arrays.asList("-parameter=p" + t + ",type=" + t, "-data=" + v));
                a.addAll(Arrays.asList("-array=a" + t + ",type=" + t, "-data=" + v + ",7"));
                a.addAll(Arrays.asList("-column=c" + t + ",type=" + t, "-data=" + v + ",8,9"));
            }
            a.addAll(Arrays.asList("-parameter=pstring,type=string", "-data=two words"));
            a.addAll(Arrays.asList("-array=astring,type=string", "-data=a b,c"));
            a.addAll(Arrays.asList("-column=cstring,type=string", "-data=x y,caf\u00e9,z"));
            a.addAll(Arrays.asList("-parameter=pchar,type=character", "-data=q"));
            a.addAll(Arrays.asList("-array=achar,type=character", "-data=a,b"));
            a.addAll(Arrays.asList("-column=cchar,type=character", "-data=e,f,g"));
            a.addAll(Arrays.asList("-parameter=penum,type=enum", "-data=10"));
            a.addAll(Arrays.asList("-array=aenum,type=enum", "-data=0,-1"));
            a.addAll(Arrays.asList("-column=cenum,type=enum", "-data=0,10,-1"));
            a.add("-enumValues=parameter,penum,0:OFF,10:ON,-1:FAULT");
            a.add("-enumValues=array,aenum,0:OFF,-1:a\\,b");
            a.add("-enumValues=column,cenum,0:OFF,10:\u96ea ,-1:x\\:y");
            Result r = run("sddsmakedataset", a.toArray(new String[0]));
            check("sddsmakedataset page " + page + ": " + r.output, r.code == 0);
        }
        String all = path("c-all.sdds");
        Result r = run("sddscombine", pages[0], pages[1], all, "-overwrite");
        check("sddscombine: " + r.output, r.code == 0);
        return all;
    }

    /** Text the C reader gives for a file, which should not depend on its encoding. */
    static String cView(String name) throws Exception {
        StringBuilder s = new StringBuilder();
        Result q = run("sddsquery", name);
        String layout = q.output;
        s.append(layout.replaceAll("(?m)^.*(SDDS protocol|data is|row counts).*\\n", "").replaceAll("\\n\\n+", "\n"));
        String[] cols = { "clongdouble", "cdouble", "cfloat", "clong64", "culong64", "clong", "culong", "cshort", "cushort", "cstring", "cchar", "cenum" };
        for (String c : cols)
            s.append(run("sdds2stream", name, "-column=" + c).output);
        s.append(run("sdds2stream", name, "-column=cenum", "-enumCodes").output);
        for (String p : new String[] { "plongdouble", "pdouble", "pfloat", "plong64", "pulong64", "plong", "pulong", "pshort", "pushort", "pstring", "pchar", "penum" })
            s.append(run("sdds2stream", name, "-parameter=" + p).output);
        for (String a : new String[] { "alongdouble", "adouble", "afloat", "along64", "aulong64", "along", "aulong", "ashort", "aushort", "astring", "achar", "aenum" })
            s.append(run("sdds2stream", name, "-array=" + a).output);
        return s.toString();
    }

    /** Every encoding the C writer can make reads the same in Java. */
    static void cToJava() throws Exception {
        String all = makeWithC();
        SDDSFile base = new SDDSFile(all);
        boolean ok = base.readFile();
        check("read C file: " + base.getErrors(), ok);
        if (!ok)
            return;
        String expected = dump(base);
        check("C enum column", expected.contains("cenum=0:OFF;10:\u96ea ;-1:x:y;"));
        check("C enum array", expected.contains("aenum[2]=0:OFF;-1:a,b;"));
        check("C enum parameter", expected.contains("penum=10:ON"));
        check("C long double", expected.contains("clongdouble=1.00000000000000000000e+00;8.00000000000000000000e+00;"));
        for (String order : new String[] { "little", "big" }) {
            Map<String, String> env = Collections.singletonMap("SDDS_OUTPUT_ENDIANESS", order);
            for (String major : new String[] { "row", "column" }) {
                for (String ext : new String[] { "", ".gz", ".xz", ".lzma" }) {
                    String out = path("c-" + order + "-" + major + ".sdds" + ext);
                    Result r = run(env, "sddsconvert", all, out, "-binary", "-majorOrder=" + major);
                    check("sddsconvert " + out + ": " + r.output, r.code == 0);
                    SDDSFile f = new SDDSFile(out);
                    boolean read = f.readFile();
                    check("read " + out + ": " + f.getErrors(), read);
                    if (read) {
                        equal("C binary " + new File(out).getName(), expected, dump(f));
                        equal("byte order of " + new File(out).getName(), order.equals("big"), f.bigEndian);
                        equal("major order of " + new File(out).getName(), major.equals("column"), f.columnMajorOrder);
                    }
                }
            }
        }
        for (String ext : new String[] { ".gz", ".xz", ".lzma" }) {
            String out = path("c-ascii.sdds" + ext);
            Result r = run("sddsconvert", all, out, "-ascii");
            check("sddsconvert ascii " + ext + ": " + r.output, r.code == 0);
            SDDSFile f = new SDDSFile(out);
            boolean read = f.readFile();
            check("read ascii " + ext + ": " + f.getErrors(), read);
            if (read)
                equal("C ascii " + ext, expected, dump(f));
        }
        // Java reads what xz itself produces, at any level, and legacy .lzma.
        Path xz = Paths.get("/usr/bin/xz");
        if (Files.isExecutable(xz)) {
            String[][] variants = { { "-0", "--stdout" }, { "-6", "--check=crc64", "--stdout" }, { "-9e", "--check=sha256", "--stdout" }, { "--format=lzma", "--stdout" }, { "--check=none", "--stdout" } };
            int n = 0;
            for (String[] v : variants) {
                List<String> cmd = new ArrayList<String>();
                cmd.add(xz.toString());
                cmd.addAll(Arrays.asList(v));
                cmd.add(path("c-little-row.sdds"));
                Process p = new ProcessBuilder(cmd).start();
                byte[] packed = p.getInputStream().readAllBytes();
                p.waitFor();
                String out = path("xz-tool-" + (n++) + ".sdds.xz");
                Files.write(Paths.get(out), packed);
                SDDSFile f = new SDDSFile(out);
                boolean read = f.readFile();
                check("read " + String.join(" ", v) + ": " + f.getErrors(), read);
                if (read)
                    equal("xz " + String.join(" ", v), expected, dump(f));
            }
            // A concatenation of two streams is one file, as for the xz tool.
            byte[] packed = Files.readAllBytes(Paths.get(path("xz-tool-1.sdds.xz")));
            String out = path("xz-two.sdds.xz");
            Files.write(Paths.get(out), concat(concat(packed, new byte[8]), packedEmpty(xz)));
            SDDSFile f = new SDDSFile(out);
            check("concatenated streams: " + f.getErrors(), f.readFile());
        }
    }

    static byte[] packedEmpty(Path xz) throws Exception {
        Process p = new ProcessBuilder(xz.toString(), "--stdout").redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
        byte[] b = p.getInputStream().readAllBytes();
        p.waitFor();
        return b;
    }

    /** What Java writes, in every encoding, reads the same in C as the C original. */
    static void javaToC() throws Exception {
        String all = path("c-all.sdds");
        String expected = cView(all);
        SDDSFile base = read(all);
        for (boolean binary : new boolean[] { false, true }) {
            for (boolean little : new boolean[] { false, true }) {
                for (boolean columnMajor : new boolean[] { false, true }) {
                    if (!binary && (columnMajor || little))
                        continue;
                    for (String ext : new String[] { "", ".gz", ".xz", ".lzma" }) {
                        String name = path("j-" + (binary ? "b" : "a") + (little ? "l" : "B") + (columnMajor ? "c" : "r") + ".sdds" + ext);
                        SDDSFile f = read(all);
                        f.setFileName(name);
                        f.setMode(binary);
                        f.setEndian(little);
                        f.columnMajorOrder = columnMajor;
                        check("Java writes " + name + ": " + f.getErrors(), f.writeFile());
                        Result r = run("sddscheck", name);
                        equal("sddscheck " + new File(name).getName(), "ok", r.output.trim());
                        equal("C reads " + new File(name).getName(), expected, cView(name));
                    }
                }
            }
        }
        Path xz = Paths.get("/usr/bin/xz");
        if (Files.isExecutable(xz)) {
            Process p = new ProcessBuilder(xz.toString(), "-t", path("j-bBr.sdds.xz")).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            check("xz -t accepts Java output: " + out, p.waitFor() == 0);
            for (int i = 0; i < 4; i++) {
                p = new ProcessBuilder(xz.toString(), "-t", path("xz-" + i + ".sdds.xz")).redirectErrorStream(true).start();
                out = new String(p.getInputStream().readAllBytes());
                check("xz -t accepts large Java output " + i + ": " + out, p.waitFor() == 0);
            }
        }
    }

    /** The two readers accept and reject the same files. */
    static void agreement(Path fixtures) throws Exception {
        String[] contents = {
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n1\n5\n\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n1\n5\n\n1\n6\n",
                "SDDS1\n&parameter name=p, type=long &end\n&data mode=ascii &end\n1\n\n2\n",
                "SDDS1\n&array name=a, type=long &end\n&data mode=ascii &end\n1\n5\n\n1\n6\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n1 \n5 extra\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n2 junk\n5\n6\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n  1\n\n5\n",
                "SDDS1\n&column name=a, type=long &end\n&column name=b, type=long &end\n&data mode=ascii &end\n1\n5\n6\n",
                "SDDS1\n&column name=a, type=long &end\n&column name=b, type=long &end\n&data mode=ascii, lines_per_row=2 &end\n1\n5\n6\n",
                "SDDS1\n&column name=a, type=long &end\n&column name=b, type=long &end\n&data mode=ascii, lines_per_row=0 &end\n2\n5\n6 7\n8\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii, no_row_counts=1 &end\n1\n2\n\n3\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n",
                "SDDS1\n&parameter name=p, type=double &end\n&data mode=ascii &end\nabc\n",
                "SDDS1\n&parameter name=p, type=double &end\n&data mode=ascii &end\n1.5abc\n",
                "SDDS1\n&parameter name=p, type=string &end\n&data mode=ascii &end\nany text here\n",
                "SDDS1\n&parameter name=p, type=string &end\n&parameter name=q, type=long &end\n&data mode=ascii &end\na\n",
                "SDDS1\n&array name=a, type=long &end\n&data mode=ascii &end\n2\n1\n",
                "SDDS1\n&array name=a, type=long &end\n&data mode=ascii &end\n-1\n",
                "SDDS1\n&array name=a, type=long, dimensions=2 &end\n&data mode=ascii &end\n2\n",
                "SDDS1\n&column name=a, type=long, foo=1 &end\n&data mode=ascii &end\n",
                "SDDS1\n&column name=a, type=DOUBLE &end\n&data mode=ascii &end\n",
                "SDDS1\n&column name=1a, type=long &end\n&data mode=ascii &end\n",
                "SDDS1\n&column name=a, type=long &end\n&column name=a, type=long &end\n&data mode=ascii &end\n",
                "SDDS1\n&column name=a, type=double, format_string=%d &end\n&data mode=ascii &end\n",
                "SDDS7\n&data mode=ascii &end\n",
                "SDDS0\n&data mode=ascii &end\n",
                "SDDS\n&data mode=ascii &end\n",
                "SDDS6\n&column name=a, type=enum, enum_values=\"0:A\" &end ,\n&data mode=ascii &end\n1\n0\n",
                "SDDS6\n&column name=a, type=enum, enum_values=\"0:A\" &end\n&data mode=ascii &end\n1\n\"0\"\n",
                "SDDS6\n&column name=a, type=enum, enum_values=\"0:A\" &end\n&data mode=ascii &end\n1\n 0 trailing\n",
                "SDDS6\n&parameter name=p, type=enum, enum_values=\"0:A\" &end\n&data mode=ascii &end\n0 trailing\n",
                "SDDS6\n&parameter name=p, type=enum, enum_values=\"0:A\" &end\n&data mode=ascii &end\n1\n",
                "SDDS6\n&parameter name=p, type=enum, enum_values=\"0:A\", fixed_value=\" 0 \" &end\n&data mode=ascii &end\n",
                "SDDS6\n&array name=a, type=enum, enum_values=\"0:A\" &end\n&data mode=ascii &end\n1\n7\n",
                "SDDS6\n&array name=a, type=enum, enum_values=\"0:A\" &end\n&data mode=ascii &end\n0\n",
                "SDDS6\n&column name=a, type=enum, enum_values=\"\\060:A\" &end\n&data mode=ascii &end\n1\n0\n",
                "SDDS6\n&column name=a, type=enum, enum_values=0:A &end\n&data mode=ascii &end\n1\n0\n",
                "SDDS6\n! comment\n&column name = a , type = enum , enum_values = \"0:A\" &end\n&data mode=ascii &end\n1\n0\n",
                "SDDS6\n&column name=a, type=enum, field_length=0, enum_values=\"0:A\" &end\n&data mode=ascii &end\n1\n0\n",
                "SDDS6\n&column name=a, type=long, field_length=x &end\n&data mode=ascii &end\n",
                "SDDS6\n&description text=\"\\000\" &end\n&data mode=ascii &end\n",
                "SDDS6\n&description text=\"\\1\" &end\n&data mode=ascii &end\n",
                "SDDS6\n&description text=\"\\!\" &end\n&data mode=ascii &end\n",
                "SDDS6\n&description text=\"a\\", 
                "SDDS6\n&description text=\"a\" &end\n&column name=x &end\n&data mode=ascii &end\n",
                "SDDS6\n&description text=\"a\", text=\"b\" &end\n&data mode=ascii &end\n",
                "SDDS5\n&description text=\"a\", text=\"b\" &end\n&data mode=ascii &end\n",
        };
        for (String content : contents) {
            String name = file(content);
            SDDSFile f = new SDDSFile(name);
            boolean java = f.readFile();
            String c = run("sddscheck", name).output.trim();
            check("the readers disagree (Java " + java + ", C " + c + "; " + f.getErrors().trim() + ") on:\n" + content, java == c.equals("ok"));
        }
        for (String line : Files.readAllLines(fixtures.resolve("manifest.txt"))) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2)
                equal("C agrees about fixture " + parts[1], parts[0].equals("1"), run("sddscheck", fixtures.resolve(parts[1]).toString()).output.trim().equals("ok"));
        }
    }

    /** The value C prints for a one-column file matches what Java read. */
    static void sameColumn(String content, String column) throws Exception {
        String name = file(content);
        SDDSFile f = read(name);
        StringBuilder java = new StringBuilder();
        for (int page = 1; page <= f.pageCount(); page++) {
            Object[] v = f.getColumnValues(f.getColumnIndex(column), page, false);
            for (int i = 0; v != null && i < v.length; i++)
                java.append(v[i]).append('\n');
        }
        equal("value of:\n" + content, run("sdds2stream", name, "-column=" + column, "-noquotes").output, java.toString());
    }

    /** Things found by comparing the two readers and writers directly. */
    static void readerDetails() throws Exception {
        // Blank formats are no format.
        String[] agree = {
                "SDDS1\n&column name=a, type=long, format_string=\"\" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS1\n&column name=a, type=long, format_string=\"  \" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS6\n&column name=a, type=long, format_string=\"\" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS1\n&parameter name=a, type=long, format_string=\"\" &end\n&data mode=ascii &end\n5\n",
                "SDDS1\n&array name=a, type=long, format_string=\"\" &end\n&data mode=ascii &end\n0\n",
                // Empty integers keep their defaults in legacy headers.
                "SDDS1\n&column name=a, type=long, field_length=\"\" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS1\n&column name=a, type=long &end\n&data mode=ascii, lines_per_row=\"\", no_row_counts=\" \" &end\n1\n5\n",
                "SDDS1\n&array name=a, type=long, dimensions=\"\" &end\n&data mode=ascii &end\n0\n",
                // But not in SDDS6, and trailing space is not part of a number there.
                "SDDS6\n&column name=a, type=long, field_length=\"\" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS6\n&column name=a, type=long, field_length=\"0 \" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS6\n&column name=a, type=long, field_length=\" 0\" &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS1\n&array name=a, type=long, dimensions=0 &end\n&data mode=ascii &end\n",
                // A fixed value that cannot be read makes its pages unreadable.
                "SDDS1\n&parameter name=p, type=double, fixed_value=abc &end\n&data mode=ascii &end\n",
                "SDDS1\n&parameter name=p, type=double, fixed_value=\"\" &end\n&data mode=ascii &end\n",
                "SDDS1\n&parameter name=p, type=character, fixed_value=\"\" &end\n&data mode=ascii &end\n",
                "SDDS1\n&parameter name=p, type=string, fixed_value=\"\" &end\n&data mode=ascii &end\n",
                "SDDS1\n&parameter name=p, type=double, fixed_value=1.5x &end\n&data mode=ascii &end\n",
                "SDDS1\n&parameter name=p, type=double, fixed_value=abc &end\n&column name=a, type=long &end\n&data mode=ascii &end\n1\n5\n",
                "SDDS1\n&parameter name=p, type=double, fixed_value=abc &end\n&data mode=binary, endian=little &end\n",
                // Number forms that a scan starts on and then cannot finish.
                "SDDS1\n&parameter name=p, type=double &end\n&data mode=ascii &end\ninfinit\n",
                "SDDS1\n&parameter name=p, type=double &end\n&data mode=ascii &end\ninfin\n",
                "SDDS1\n&parameter name=p, type=double &end\n&data mode=ascii &end\ninfx\n",
                "SDDS1\n&parameter name=p, type=double &end\n&data mode=ascii &end\n0x\n",
                "SDDS1\n&parameter name=p, type=float &end\n&data mode=ascii &end\ninfinit\n",
                "SDDS1\n&parameter name=p, type=longdouble &end\n&data mode=ascii &end\n0x\n",
                // An empty token ends a special comment.
                "SDDS1\n!# \"\" fixed-rowcount\n&column name=a, type=long &end\n&data mode=ascii &end\n3\n1\n",
                "SDDS1\n!# fixed-rowcount\n&column name=a, type=long &end\n&data mode=ascii &end\n3\n1\n",
        };
        for (String content : agree) {
            String name = file(content);
            SDDSFile f = new SDDSFile(name);
            boolean java = f.readFile();
            String c = run("sddscheck", name).output.trim();
            check("the readers disagree (Java " + java + ", C " + c + "; " + f.getErrors().trim() + ") on:\n" + content, java == c.equals("ok"));
        }

        // Strings end at a NUL.
        sameColumn("SDDS1\n&column name=s, type=string &end\n&data mode=ascii &end\n2\nab\\000cd\nx\\400y\n", "s");
        byte[] nul = concat("SDDS1\n!# little-endian\n&column name=s, type=string &end\n&data mode=binary &end\n".getBytes(StandardCharsets.ISO_8859_1),
                new byte[] { 1, 0, 0, 0, 3, 0, 0, 0, 'a', 0, 'b' });
        equal("binary string ends at a NUL", "a", read(bytesFile(nul)).getColumnValues(0, 1, false)[0]);

        // Single precision rounds once, from the text.
        SDDSFile fl = read(file("SDDS1\n&column name=f, type=float &end\n&data mode=ascii &end\n3\n1.00000017881393432617187499\n3.4028235677973366e38\n1e39\n"));
        Object[] fv = fl.getColumnValues(0, 1, false);
        equal("rounds once", Float.valueOf(1.0000001f), fv[0]);
        equal("rounds once at the top of the range", Float.valueOf(Float.MAX_VALUE), fv[1]);
        equal("overflows", Float.valueOf(Float.POSITIVE_INFINITY), fv[2]);
        SDDSFile fs = new SDDSFile();
        fs.defineColumn("f", SDDSUtil.SDDS_FLOAT);
        fs.setColumn(0, new String[] { "1.00000017881393432617187499" }, 1, 1);
        equal("setter rounds once", Float.valueOf(1.0000001f), fs.getColumnValues(0, 1, false)[0]);

        // Unsigned text that does not fit behaves as in C.
        sameColumn("SDDS2\n&column name=u, type=ulong &end\n&data mode=ascii &end\n3\n9223372036854775808\n-1\n4294967296\n", "u");
        sameColumn("SDDS2\n&column name=u, type=ushort &end\n&data mode=ascii &end\n3\n-9223372036854775809\n-1\n65536\n", "u");
        sameColumn("SDDS5\n&column name=u, type=ulong64 &end\n&data mode=ascii &end\n3\n-99999999999999999999999\n-1\n18446744073709551616\n", "u");
        sameColumn("SDDS1\n&column name=l, type=long &end\n&column name=h, type=short &end\n&data mode=ascii &end\n1\n99999999999999999999 99999\n", "h");

        // A plain file may be called .gz, as gzopen allows.
        Path plain = work.resolve("plain.sdds.gz");
        Files.write(plain, "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n1\n5\n".getBytes(StandardCharsets.ISO_8859_1));
        equal("C reads a plain .gz", "ok", run("sddscheck", plain.toString()).output.trim());
        equal("Java reads a plain .gz", Long.valueOf(5), read(plain.toString()).getColumnValues(0, 1, false)[0]);

        // The non-native declaration wins when two disagree.
        String conflict = "SDDS5\n!# big-endian\n&column name=a, type=long &end\n&data mode=binary, endian=little &end\n";
        String big = bytesFile(concat(conflict.getBytes(StandardCharsets.ISO_8859_1), new byte[] { 0, 0, 0, 1, 0, 0, 0, 7 }));
        equal("C reads conflicting declarations", "7\n", run("sdds2stream", big, "-column=a").output);
        equal("Java reads conflicting declarations", Long.valueOf(7), read(big).getColumnValues(0, 1, false)[0]);
        String conflict6 = "SDDS6\n!# little-endian\n&column name=a, type=long &end\n&data mode=binary, endian=big &end\n";
        big = bytesFile(concat(conflict6.getBytes(StandardCharsets.ISO_8859_1), new byte[] { 0, 0, 0, 1, 0, 0, 0, 7 }));
        equal("C reads conflicting SDDS6 declarations", "7\n", run("sdds2stream", big, "-column=a").output);
        equal("Java reads conflicting SDDS6 declarations", Long.valueOf(7), read(big).getColumnValues(0, 1, false)[0]);

        // An undeclared byte order is native, even for an object that read another file before.
        SDDSFile reused = new SDDSFile();
        reused.setFileName(bytesFile(concat("SDDS5\n&column name=a, type=long &end\n&data mode=binary, endian=big &end\n".getBytes(StandardCharsets.ISO_8859_1), new byte[] { 0, 0, 0, 1, 0, 0, 0, 7 })));
        check("first read", reused.readFile());
        reused.setFileName(bytesFile(concat("SDDS1\n&column name=a, type=long &end\n&data mode=binary &end\n".getBytes(StandardCharsets.ISO_8859_1), new byte[] { 1, 0, 0, 0, 7, 0, 0, 0 })));
        check("second read: " + reused.getErrors(), reused.readFile());
        equal("native data", Long.valueOf(7), reused.getColumnValues(0, 1, false)[0]);

        // XZ input stops as liblzma does.
        Path xz = Paths.get("/usr/bin/xz");
        if (Files.isExecutable(xz)) {
            byte[] one = xzBytes(xz, "SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n1\n1\n1\n2\n");
            byte[] two = xzBytes(xz, "1\n3\n");
            xzAgreement("two streams", concat(one, two));
            xzAgreement("trailing junk", concat(one, "junk".getBytes(StandardCharsets.ISO_8859_1)));
            xzAgreement("no last byte", Arrays.copyOf(one, one.length - 1));
            xzAgreement("no footer", Arrays.copyOf(one, one.length - 12));
            StringBuilder many = new StringBuilder("SDDS1\n&column name=a, type=long &end\n&data mode=ascii &end\n");
            for (int page = 0; page < 200; page++) {
                many.append("100\n");
                for (int row = 0; row < 100; row++)
                    many.append(page * row + (page ^ row) * 7919).append('\n');
            }
            byte[] long1 = xzBytes(xz, many.toString());
            String cut = work.resolve("cut.sdds.xz").toString();
            Files.write(Paths.get(cut), Arrays.copyOf(long1, long1.length / 2));
            SDDSFile partial = new SDDSFile(cut);
            boolean ok = partial.readFile();
            partial.getErrors();
            check("a file cut in half is an error", !ok);
            check("pages before the cut were read", partial.pageCount() > 50);
            String junk = work.resolve("junk.sdds.xz").toString();
            Files.write(Paths.get(junk), "SDDS1\n&data mode=ascii &end\n".getBytes(StandardCharsets.ISO_8859_1));
            SDDSFile notXz = new SDDSFile(junk);
            check("not compressed at all", !notXz.readFile());
            notXz.getErrors();
        }
    }

    static byte[] xzBytes(Path xz, String content) throws Exception {
        Path in = work.resolve("xz-input.tmp");
        Files.write(in, content.getBytes(StandardCharsets.ISO_8859_1));
        Process p = new ProcessBuilder(xz.toString(), "--stdout", in.toString()).start();
        byte[] b = p.getInputStream().readAllBytes();
        p.waitFor();
        return b;
    }

    static void xzAgreement(String what, byte[] content) throws Exception {
        String name = work.resolve("agree.sdds.xz").toString();
        Files.write(Paths.get(name), content);
        String c = run("sddscheck", name).output.trim();
        SDDSFile f = new SDDSFile(name);
        boolean java = f.readFile();
        check(what + ": the readers disagree (Java " + java + ", C " + c + "; " + f.getErrors().trim() + ")", java == c.equals("ok"));
        if (java) {
            StringBuilder values = new StringBuilder();
            for (int page = 1; page <= f.pageCount(); page++)
                for (Object v : f.getColumnValues(0, page, false))
                    values.append(v).append('\n');
            equal(what + " values", run("sdds2stream", name, "-column=a").output, values.toString());
        }
    }

    /** Things found by comparing the two writers directly. */
    static void writerDetails() throws Exception {
        // Strings that defeat the C reader's search for a closing quote when backslashes are doubled.
        String[] hard = { "a b\\", "a b\\\\", "a\\\"b c", "\\", "\\\\", "\\\"", "x\\\\\"y z", "\\ ", "a\\b" };
        SDDSFile f = new SDDSFile(path("hard.sdds"));
        f.defineColumn("s", SDDSUtil.SDDS_STRING);
        f.defineParameter("p", SDDSUtil.SDDS_STRING);
        f.setColumn(0, hard, 1, 1);
        f.setParameter(0, hard[0], 1);
        for (boolean binary : new boolean[] { false, true }) {
            f.setMode(binary);
            check("write hard strings", f.writeFile());
            equal("C accepts hard strings binary=" + binary, "ok", run("sddscheck", path("hard.sdds")).output.trim());
            String[] lines = run("sdds2stream", path("hard.sdds"), "-column=s", "-noquotes").output.split("\n");
            equal("C reads hard strings binary=" + binary, hard, lines);
            equal("Java reads hard strings binary=" + binary, hard, Arrays.copyOf(read(path("hard.sdds")).getColumnValues(0, 1, false), hard.length, String[].class));
        }

        // Never-assigned enums hold code 0, and a failure leaves an existing file alone.
        String keep = path("keep.sdds");
        byte[] before = "SDDS1\n&column name=old, type=long &end\n&data mode=ascii &end\n1\n5\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(Paths.get(keep), before);
        SDDSFile g = new SDDSFile(keep);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.defineEnumParameter("p", "0:Z,1:A");
        g.setColumn(0, new long[] { 1 }, 1, 1);
        check("an unassigned parameter with a mapped zero is written: " + g.getErrors(), g.writeFile());
        equal("C reads that zero", "Z\n", run("sdds2stream", keep, "-parameter=p").output);

        Files.write(Paths.get(keep), before);
        g = new SDDSFile(keep);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.defineEnumParameter("p", "1:A");
        g.setColumn(0, new long[] { 1 }, 1, 1);
        check("an unassigned parameter with no mapped zero is refused", !g.writeFile());
        check("the message names it", g.getErrors().contains("parameter p"));
        check("the existing file is untouched", Arrays.equals(before, Files.readAllBytes(Paths.get(keep))));

        g = new SDDSFile(keep);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.defineEnumColumn("e", "0:Z,1:A");
        g.setColumn(0, new long[] { 1, 2 }, 1, 1);
        check("an unassigned column with a mapped zero is written: " + g.getErrors(), g.writeFile());
        equal("C reads those zeros", "Z\nZ\n", run("sdds2stream", keep, "-column=e").output);

        Files.write(Paths.get(keep), before);
        g = new SDDSFile(keep);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.defineEnumColumn("e", "1:A");
        g.setColumn(0, new long[] { 1, 2 }, 1, 1);
        check("an unassigned column with no mapped zero is refused", !g.writeFile());
        check("the message names it", g.getErrors().contains("column e"));
        check("the existing file is untouched", Arrays.equals(before, Files.readAllBytes(Paths.get(keep))));

        g = new SDDSFile(keep);
        g.defineEnumArray("a", "1:A", 1);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.setColumn(0, new long[] { 1 }, 1, 1);
        check("an unassigned array has no elements: " + g.getErrors(), g.writeFile());
        equal("C reads it", "ok", run("sddscheck", keep).output.trim());

        // Other things that cannot be written also stop before the file is opened.
        Files.write(Paths.get(keep), before);
        g = new SDDSFile(keep);
        g.defineArray("a", SDDSUtil.SDDS_LONG, 1);
        g.setArrayDim(1, 0, new int[] { 5 });
        g.setArray(0, new long[] { 1 }, 1);
        check("a mismatched array is refused", !g.writeFile());
        g.getErrors();
        check("the existing file is untouched", Arrays.equals(before, Files.readAllBytes(Paths.get(keep))));
        g = new SDDSFile(keep);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.defineParameter("p", SDDSUtil.SDDS_LONG);
        g.setColumn(0, new long[] { 1 }, 1, 1);
        check("a primitive parameter with no value is refused", !g.writeFile());
        g.getErrors();
        check("the existing file is untouched", Arrays.equals(before, Files.readAllBytes(Paths.get(keep))));
        g = new SDDSFile(keep);
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.defineColumn("d", SDDSUtil.SDDS_LONG);
        g.setColumn(0, new long[] { 1 }, 1, 1);
        check("a primitive column with no value is refused", !g.writeFile());
        g.getErrors();
        check("the existing file is untouched", Arrays.equals(before, Files.readAllBytes(Paths.get(keep))));
        g = new SDDSFile(keep);
        g.defineParameter("p", null, null, null, null, SDDSUtil.SDDS_DOUBLE, "abc");
        g.defineColumn("c", SDDSUtil.SDDS_LONG);
        g.setColumn(0, new long[] { 1 }, 1, 1);
        check("an unreadable fixed value is refused", !g.writeFile());
        g.getErrors();
        check("the existing file is untouched", Arrays.equals(before, Files.readAllBytes(Paths.get(keep))));

        // Compressed and binary files with no row counts are complete when writeFile returns.
        for (String ext : new String[] { ".gz", ".xz", ".lzma" }) {
            String name = path("norows.sdds" + ext);
            SDDSFile n = new SDDSFile(name);
            n.setNoRowCount(true);
            n.defineColumn("a", SDDSUtil.SDDS_LONG);
            n.setColumn(0, new long[] { 1, 2 }, 1, 1);
            check("write no row counts " + ext, n.writeFile());
            equal("C reads no row counts " + ext, "1\n2\n", run("sdds2stream", name, "-column=a").output);
        }
        SDDSFile nb = new SDDSFile(path("norows-binary.sdds"), true);
        nb.setNoRowCount(true);
        nb.defineColumn("a", SDDSUtil.SDDS_LONG);
        nb.setColumn(0, new long[] { 1, 2 }, 1, 1);
        check("write no row counts in binary", nb.writeFile());
        equal("C reads it", "1\n2\n", run("sdds2stream", path("norows-binary.sdds"), "-column=a").output);

        // NaN keeps its sign, in both encodings.
        double negativeNaN = Double.longBitsToDouble(0xfff8000000000000L);
        SDDSFile v = new SDDSFile(path("nan.sdds"));
        v.defineColumn("d", SDDSUtil.SDDS_DOUBLE);
        v.defineColumn("f", SDDSUtil.SDDS_FLOAT);
        v.setColumn(0, new double[] { negativeNaN, Double.NaN }, 1, 1);
        v.setColumn(1, new double[] { negativeNaN, Double.NaN }, 1, 1);
        for (boolean binary : new boolean[] { false, true }) {
            v.setMode(binary);
            check("write NaN", v.writeFile());
            equal("C sees the NaN signs binary=" + binary, "-nan -nan\nnan nan\n",
                    run("sdds2stream", path("nan.sdds"), "-column=d,f").output.replaceAll(" +", " ").replaceAll("(?m)^ ", ""));
            SDDSFile b = read(path("nan.sdds"));
            check("Java keeps the NaN sign binary=" + binary, Double.doubleToRawLongBits((Double) b.getColumnValues(0, 1, false)[0]) < 0);
        }

        // Metadata changes cannot produce a file nobody can read.
        SDDSFile m = new SDDSFile();
        m.defineColumn("a", SDDSUtil.SDDS_LONG);
        m.defineColumn("b", SDDSUtil.SDDS_LONG);
        m.defineParameter("p", SDDSUtil.SDDS_DOUBLE);
        m.defineArray("x", SDDSUtil.SDDS_DOUBLE, 1);
        m.setColumnFormatString(0, "%f");
        check("wrong column format refused", !m.getErrors().isEmpty() && m.getColumnFormatString(0) == null);
        m.setParameterFormatString(0, "%d");
        check("wrong parameter format refused", !m.getErrors().isEmpty() && m.getParameterFormatString(0) == null);
        m.setArrayFormatString(0, "%d");
        check("wrong array format refused", !m.getErrors().isEmpty() && m.getArrayFormatString(0) == null);
        m.setColumnFormatString(0, "%ld");
        check("right format accepted", m.getErrors().isEmpty() && "%ld".equals(m.getColumnFormatString(0)));
        m.renameColumn("a", "1x y");
        check("invalid name refused", !m.getErrors().isEmpty() && m.getColumnIndex("a") == 0);
        m.renameColumn("a", "b");
        check("name in use refused", !m.getErrors().isEmpty() && m.getColumnIndex("a") == 0);
        m.renameColumn("a", "a");
        check("same name is fine", m.getErrors().isEmpty());
        m.renameColumn("a", "c");
        check("rename", m.getErrors().isEmpty() && m.getColumnIndex("c") == 0);
        m.renameParameter("p", "1");
        check("invalid parameter name refused", !m.getErrors().isEmpty() && m.getParameterIndex("p") == 0);
        m.renameArray("x", "1");
        check("invalid array name refused", !m.getErrors().isEmpty() && m.getArrayIndex("x") == 0);

        // Round trips through the value objects keep characters and codes.
        SDDSFile ch = new SDDSFile();
        ch.defineColumn("c", SDDSUtil.SDDS_CHARACTER);
        ch.setColumn(0, new char[] { '!', 'a', ' ' }, 1, 1);
        check("set characters from their shown form", ch.setColumn(0, ch.getColumnValues(0, 1, false), 1, 1));
        equal("characters survive", new Object[] { "\\041", "a", "\\040" }, ch.getColumnValues(0, 1, false));
        check("add a character", ch.addColumnElement(1, '?'));
        equal("characters survive adding", new Object[] { "\\041", "a", "\\040", "\\077" }, ch.getColumnValues(0, 1, false));

        // A long beyond 32 bits is not a code.
        SDDSFile w = new SDDSFile();
        w.defineColumn("l", SDDSUtil.SDDS_LONG);
        w.setColumn(0, new long[] { 4294967297L }, 1, 1);
        check("wide long is not code 1", !w.convertColumn(0, SDDSUtil.SDDS_ENUM, "1:A"));
        w.getErrors();

        // An element never given a type ignores values rather than failing.
        SDDSColumn bare = new SDDSColumn();
        bare.setValues(new long[] { 1 }, 1, 1);
        bare.setValues(new double[] { 1 }, 1, 1);
        bare.setValues(new String[] { "1" }, 1, 1);
        equal("bare column", 0, bare.getPageCount());
        SDDSParameter bareParameter = new SDDSParameter();
        bareParameter.setValue(1L, 1);
        bareParameter.setValue(1.0, 1);
        bareParameter.setValue("1", 1);
        equal("bare parameter", 0, bareParameter.getPageCount());
        SDDSArray bareArray = new SDDSArray();
        bareArray.setValues(new long[] { 1 }, 1);
        equal("bare array", 0, bareArray.getPageCount());

        // Long double conversions round once and keep integers exact.
        equal("exact to long", 9007199254740993L, SDDSLongDouble.valueOf(9007199254740993L).longValue());
        equal("negative to long", -5L, SDDSLongDouble.parse("-5.9").longValue());
        equal("small to long", 0L, SDDSLongDouble.parse("0.5").longValue());
        equal("large to long", Long.MAX_VALUE, SDDSLongDouble.parse("1e30").longValue());
        equal("to int", Integer.MAX_VALUE, SDDSLongDouble.parse("1e30").intValue());
        equal("subnormal rounds once", Double.MIN_VALUE,
                SDDSLongDouble.valueOf(new BigDecimal(BigInteger.ONE, 0).divide(new BigDecimal(BigInteger.ONE.shiftLeft(1074))).multiply(new BigDecimal("1.4999999999999999991"))).doubleValue());
        equal("to float", 1.5f, SDDSLongDouble.valueOf(1.5).floatValue());
        equal("to float below its normal range", Float.MIN_VALUE, SDDSLongDouble.parse("1.4e-45").floatValue());
        equal("hex fraction", SDDSLongDouble.parse("1.00000000000000000086736173798840354720596224069595336914"), SDDSLongDouble.parse("0x1.000000000000001p0"));
        check("hex below the double range", SDDSLongDouble.parse("0x1p-16400").toBigDecimal().signum() > 0);
        equal("hex without an exponent", SDDSLongDouble.valueOf(1.5), SDDSLongDouble.parse("0x1.8"));
        equal("enormous exponent", "inf", SDDSLongDouble.parse("1e99999999999").toString());
        equal("enormous negative exponent", "-inf", SDDSLongDouble.parse("-1e99999999999").toString());
        equal("enormous small exponent", 0.0, SDDSLongDouble.parse("1e-99999999999").doubleValue());
        equal("zero with an enormous exponent", 0.0, SDDSLongDouble.parse("0e99999999999").doubleValue());
    }

    /** Files Java makes through its own enum API, checked by the C programs. */
    static void cEnumInterchange() throws Exception {
        SDDSFile f = new SDDSFile(path("japi.sdds"));
        f.setDescription("quote \" bang ! back \\ tab\t", "\u96ea");
        f.defineEnumParameter("p", "sym\"", "u!", "d\\", null, "0:OFF,10:ON,-1:FAULT", null);
        f.defineEnumParameter("fx", null, null, null, null, "0:OFF,10:ON,-1:FAULT", "-1");
        f.defineEnumArray("a", null, null, null, null, "g", "0:OFF,10:a\\,b\\:c\\\\d\\\"e\\n\\r\\t\u96ea ,-1:FAULT", 0, 2);
        f.defineEnumColumn("c", "0:OFF,10:a\\,b\\:c\\\\d\\\"e\\n\\r\\t\u96ea ,-1:FAULT");
        f.setParameter(0, 10, 1);
        f.setArrayDim(1, 0, new int[] { 1, 2 });
        f.setArray(0, new int[] { 10, -1 }, 1);
        f.setColumn(0, new int[] { 0, 10, -1 }, 1, 1);
        for (boolean binary : new boolean[] { false, true }) {
            f.setMode(binary);
            check("write enum file", f.writeFile());
            equal("C checks the enum file", "ok", run("sddscheck", path("japi.sdds")).output.trim());
            equal("C sees the codes", "0\n10\n-1\n", run("sdds2stream", path("japi.sdds"), "-column=c", "-enumCodes").output);
            equal("C sees the fixed value", "FAULT\n", run("sdds2stream", path("japi.sdds"), "-parameter=fx").output);
            String q = run("sddsquery", path("japi.sdds")).output;
            check("C sees the mapping: " + q, q.contains("enum"));
            SDDSFile b = read(path("japi.sdds"));
            equal("description", f.descriptionText, b.descriptionText);
            equal("contents", f.descriptionContents, b.descriptionContents);
            equal("symbol", "sym\"", b.getParameterSymbol(0));
            equal("units", "u!", b.getParameterUnits(0));
            equal("description of parameter", "d\\", b.getParameterDescription(0));
            equal("group", "g", b.getArrayGroupName(0));
        }
        // C can operate on the enum data that Java wrote.
        Result r = run("sddsprocess", path("japi.sdds"), path("japi-on.sdds"), "-match=column,c=OFF");
        check("sddsprocess: " + r.output, r.code == 0);
        SDDSFile on = read(path("japi-on.sdds"));
        equal("C filtered by label", new int[] { 0 }, on.getColumnEnumCodes(0, 1));
        r = run("sddscast", path("japi.sdds"), path("japi-codes.sdds"), "-cast=column,c,enum,long");
        check("sddscast: " + r.output, r.code == 0);
        equal("C cast to codes", SDDSUtil.SDDS_LONG, read(path("japi-codes.sdds")).getColumnType(0));

        // Legacy versions read in C the same way when Java writes them.
        SDDSFile plain = new SDDSFile(path("legacy.sdds"));
        plain.setDescription("a \"b\" c!", null);
        plain.defineColumn("x", "sym bol", "m/s", "de\"sc", "%21.15e", SDDSUtil.SDDS_DOUBLE, 0);
        plain.defineParameter("t", null, null, null, null, SDDSUtil.SDDS_STRING, "fixed! value");
        plain.setColumn(0, new double[] { 1.5, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE }, 1, 1);
        for (boolean binary : new boolean[] { false, true }) {
            plain.setMode(binary);
            check("write legacy", plain.writeFile());
            equal("legacy version", 1, plain.layoutVersion);
            equal("C checks legacy", "ok", run("sddscheck", path("legacy.sdds")).output.trim());
            equal("C sees legacy data", 4, run("sdds2stream", path("legacy.sdds"), "-column=x").output.trim().split("\\s+").length);
            equal("C sees a fixed string", "fixed! value\n", run("sdds2stream", path("legacy.sdds"), "-parameter=t", "-noquotes").output);
        }
    }
}
