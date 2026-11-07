/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import SDDS.java.SDDS.*;

/**
 * An SDDS file open in the editor: its definitions and the text of every value on
 * every page. Saves are staged beside the destination and checked before they replace
 * it, so a failed save leaves the existing file as it was.
 */
final class Document {
    /** The values of one array on one page. */
    static final class ArrayCells {
        String[] values;
        int[] dims;

        ArrayCells(String[] values, int[] dims) {
            this.values = values;
            this.dims = dims;
        }

        ArrayCells copy() {
            return new ArrayCells(values.clone(), dims.clone());
        }
    }

    /** The values of one page. */
    static final class Page {
        String[] parameters = new String[0];
        String[][] columns = new String[0][];
        ArrayCells[] arrays = new ArrayCells[0];
        int rows;

        Page copy() {
            Page p = new Page();
            p.parameters = parameters.clone();
            p.columns = new String[columns.length][];
            for (int i = 0; i < columns.length; i++)
                p.columns[i] = columns[i].clone();
            p.arrays = new ArrayCells[arrays.length];
            for (int i = 0; i < arrays.length; i++)
                p.arrays[i] = arrays[i].copy();
            p.rows = rows;
            return p;
        }
    }

    /** A save or read that could not be done. */
    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        Failure(String message) {
            super(message);
        }
    }

    /** The program that writes HDF5. Tests name a stand-in for it. */
    static String hdfProgram = "sdds2hdf";

    final List<Definition> parameters = new ArrayList<Definition>();
    final List<Definition> columns = new ArrayList<Definition>();
    final List<Definition> arrays = new ArrayList<Definition>();
    final List<Page> pages = new ArrayList<Page>();
    String description;
    String contents;
    String path;
    boolean ascii = false;
    boolean bigEndian = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.BIG_ENDIAN;
    boolean columnMajor = false;
    boolean noRowCounts = false;
    int version = 1;

    /** Everything that a structural change can alter, for undoing it. */
    Document snapshot() {
        Document d = new Document();
        for (Definition x : parameters)
            d.parameters.add(x.copy());
        for (Definition x : columns)
            d.columns.add(x.copy());
        for (Definition x : arrays)
            d.arrays.add(x.copy());
        for (Page p : pages)
            d.pages.add(p.copy());
        d.description = description;
        d.contents = contents;
        return d;
    }

    /** About how many bytes a snapshot of this document keeps alive. The text itself is shared. */
    long referenceBytes() {
        long cells = 0;
        for (Page p : pages) {
            cells += p.parameters.length + (long) p.columns.length * p.rows;
            for (ArrayCells a : p.arrays)
                cells += a.values.length;
        }
        return cells * 4 + pages.size() * 64L;
    }

    /** Put back a snapshot, leaving the file settings alone. */
    void restore(Document d) {
        Document copy = d.snapshot();
        parameters.clear();
        parameters.addAll(copy.parameters);
        columns.clear();
        columns.addAll(copy.columns);
        arrays.clear();
        arrays.addAll(copy.arrays);
        pages.clear();
        pages.addAll(copy.pages);
        description = copy.description;
        contents = copy.contents;
    }

    /* ---- reading ---- */

    /**
     * Read a file.
     *
     * @param path file name
     * @return the document
     * @throws Failure if it cannot be read
     */
    static Document read(String path) throws Failure {
        SDDSFile file = new SDDSFile(path);
        boolean ok;
        try {
            ok = file.readFile();
        } catch (OutOfMemoryError e) {
            throw new Failure("Not enough memory to read " + path);
        }
        if (!ok)
            throw new Failure(file.getErrors().trim());
        /* With nothing defined, no page is stored either, and such a file is one empty page. */
        boolean nothingDefined = isEmpty(file.getParameterNames()) && isEmpty(file.getColumnNames())
                && isEmpty(file.getArrayNames());
        if (file.pageCount() == 0 && !nothingDefined)
            throw new Failure("File contains no pages");
        Document doc = new Document();
        doc.path = path;
        doc.ascii = file.asciiFile;
        doc.bigEndian = file.bigEndian;
        doc.columnMajor = file.columnMajorOrder;
        doc.noRowCounts = file.noRowCount;
        doc.version = file.layoutVersion;
        doc.description = file.descriptionText;
        doc.contents = file.descriptionContents;
        String[] names = file.getParameterNames();
        for (int i = 0; names != null && i < names.length; i++) {
            Definition d = new Definition(names[i], file.getParameterType(i));
            d.symbol = file.getParameterSymbol(i);
            d.units = file.getParameterUnits(i);
            d.description = file.getParameterDescription(i);
            d.format = file.getParameterFormatString(i);
            d.enumDefinition = file.getParameterEnumDefinition(i);
            if (file.getParameterFixedValue(i) != null)
                d.fixedValue = Cells.fromValue(file.getParameterValue(i, 1, false), d.type);
            doc.parameters.add(d);
        }
        names = file.getColumnNames();
        for (int i = 0; names != null && i < names.length; i++) {
            Definition d = new Definition(names[i], file.getColumnType(i));
            d.symbol = file.getColumnSymbol(i);
            d.units = file.getColumnUnits(i);
            d.description = file.getColumnDescription(i);
            d.format = file.getColumnFormatString(i);
            d.fieldLength = file.getColumnFieldLength(i);
            d.enumDefinition = file.getColumnEnumDefinition(i);
            doc.columns.add(d);
        }
        names = file.getArrayNames();
        for (int i = 0; names != null && i < names.length; i++) {
            Definition d = new Definition(names[i], file.getArrayType(i));
            d.symbol = file.getArraySymbol(i);
            d.units = file.getArrayUnits(i);
            d.description = file.getArrayDescription(i);
            d.format = file.getArrayFormatString(i);
            d.groupName = file.getArrayGroupName(i);
            d.fieldLength = file.getArrayFieldLength(i);
            d.dimensions = file.getArrayDimensions(i);
            d.enumDefinition = file.getArrayEnumDefinition(i);
            doc.arrays.add(d);
        }
        int pageCount = file.pageCount();
        for (int number = 1; number <= pageCount; number++) {
            Page page = new Page();
            page.parameters = new String[doc.parameters.size()];
            for (int i = 0; i < page.parameters.length; i++) {
                Definition d = doc.parameters.get(i);
                page.parameters[i] = d.fixedValue != null ? d.fixedValue
                        : Cells.fromValue(file.getParameterValue(i, number, false), d.type);
            }
            page.rows = Math.max(file.getRowCount(number), 0);
            page.columns = new String[doc.columns.size()][];
            for (int i = 0; i < page.columns.length; i++) {
                Object[] values = file.getColumnValues(i, number, false);
                String[] cells = new String[page.rows];
                for (int r = 0; r < cells.length; r++)
                    cells[r] = values != null && r < values.length
                            ? Cells.fromValue(values[r], doc.columns.get(i).type)
                            : "";
                page.columns[i] = cells;
            }
            page.arrays = new ArrayCells[doc.arrays.size()];
            for (int i = 0; i < page.arrays.length; i++) {
                Definition d = doc.arrays.get(i);
                Object[] values = file.getArrayValues(i, number, false);
                int[] dim = file.getArrayDim(number, i);
                String[] cells = new String[values == null ? 0 : values.length];
                for (int k = 0; k < cells.length; k++)
                    cells[k] = Cells.fromValue(values[k], d.type);
                page.arrays[i] = new ArrayCells(cells, dim == null ? new int[d.dimensions] : dim.clone());
            }
            doc.pages.add(page);
        }
        if (doc.pages.isEmpty())
            doc.pages.add(new Page());
        return doc;
    }

    private static boolean isEmpty(String[] names) {
        return names == null || names.length == 0;
    }

    /* ---- writing ---- */

    /** Check every value on a page, naming the first that cannot be written. */
    String pageProblem(int index) {
        Page page = pages.get(index);
        String where = "Page " + (index + 1) + ": ";
        for (int i = 0; i < parameters.size(); i++) {
            Definition d = parameters.get(i);
            String value = d.fixedValue != null ? d.fixedValue : page.parameters[i];
            if (!Cells.valid(value, d)) {
                if (d.isEnum() && value.isEmpty())
                    return where + "parameter '" + d.name + "' has no enum state; choose one of its declared labels";
                return where + "parameter '" + d.name + "' has invalid value '" + brief(value) + "' for type "
                        + d.typeName();
            }
        }
        for (int c = 0; c < columns.size(); c++) {
            Definition d = columns.get(c);
            for (int r = 0; r < page.rows; r++) {
                if (!Cells.valid(page.columns[c][r], d)) {
                    if (d.isEnum() && page.columns[c][r].isEmpty())
                        return where + "column '" + d.name + "' row " + (r + 1)
                                + " has no enum state; choose one of its declared labels";
                    return where + "column '" + d.name + "' row " + (r + 1) + " has invalid value '"
                            + brief(page.columns[c][r]) + "' for type " + d.typeName();
                }
            }
        }
        for (int a = 0; a < arrays.size(); a++) {
            Definition d = arrays.get(a);
            ArrayCells cells = page.arrays[a];
            long size = 1;
            for (int dim : cells.dims)
                size *= dim;
            if (size != cells.values.length)
                return where + "array '" + d.name + "' does not fit its dimensions";
            for (int k = 0; k < cells.values.length; k++) {
                if (!Cells.valid(cells.values[k], d)) {
                    if (d.isEnum() && cells.values[k].isEmpty())
                        return where + "array '" + d.name + "' element " + (k + 1)
                                + " has no enum state; choose one of its declared labels";
                    return where + "array '" + d.name + "' element " + (k + 1) + " has invalid value '"
                            + brief(cells.values[k]) + "' for type " + d.typeName();
                }
            }
        }
        return null;
    }

    private static String brief(String text) {
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }

    private static String blankToZero(String text) {
        return text.trim().isEmpty() ? "0" : text.trim();
    }

    /** Move a document's values into a file object, ready to write. */
    private SDDSFile toFile(String name, boolean forceAscii) throws Failure {
        SDDSFile out = new SDDSFile(name);
        out.setMode(!(ascii || forceAscii));
        out.setEndian(!bigEndian);
        out.columnMajorOrder = columnMajor && !forceAscii;
        out.setDescription(description, contents);
        for (Definition d : parameters) {
            String fixed = d.fixedValue == null ? null
                    : d.isEnum() ? String.valueOf(d.enumDefinition.labelToCode(d.fixedValue))
                            : SDDSUtil.fixedValueText(d.fixedValue, d.type);
            int index = d.isEnum()
                    ? out.defineEnumParameter(d.name, d.symbol, d.units, d.description, null,
                            d.enumDefinition.getValues(), fixed)
                    : out.defineParameter(d.name, d.symbol, d.units, d.description, d.format, d.type, fixed);
            if (index < 0)
                throw new Failure(out.getErrors().trim());
        }
        for (Definition d : arrays) {
            int index = d.isEnum()
                    ? out.defineEnumArray(d.name, d.symbol, d.units, d.description, null, d.groupName,
                            d.enumDefinition.getValues(), 0, d.dimensions)
                    : out.defineArray(d.name, d.symbol, d.units, d.description, d.format, d.groupName, d.type,
                            Math.max(d.fieldLength, 0), d.dimensions);
            if (index < 0)
                throw new Failure(out.getErrors().trim());
        }
        for (Definition d : columns) {
            int index = d.isEnum()
                    ? out.defineEnumColumn(d.name, d.symbol, d.units, d.description, null, d.enumDefinition.getValues(), 0)
                    : out.defineColumn(d.name, d.symbol, d.units, d.description, d.format, d.type, d.fieldLength);
            if (index < 0)
                throw new Failure(out.getErrors().trim());
        }
        for (int number = 0; number < pages.size(); number++) {
            String problem = pageProblem(number);
            if (problem != null)
                throw new Failure(problem);
            Page page = pages.get(number);
            int p = number + 1;
            for (int i = 0; i < parameters.size(); i++) {
                Definition d = parameters.get(i);
                if (d.fixedValue != null) {
                    out.setParameter(i, 0, p);
                    out.getErrors();
                } else if (!setParameter(out, i, d, page.parameters[i], p)) {
                    throw new Failure(out.getErrors().trim());
                }
            }
            for (int i = 0; i < arrays.size(); i++) {
                ArrayCells cells = page.arrays[i];
                out.setArrayDim(p, i, cells.dims.clone());
                if (!setArray(out, i, arrays.get(i), cells.values, p))
                    throw new Failure(out.getErrors().trim());
            }
            for (int i = 0; i < columns.size(); i++) {
                if (!setColumn(out, i, columns.get(i), page.columns[i], p))
                    throw new Failure(out.getErrors().trim());
            }
            if (columns.isEmpty() && arrays.isEmpty() && parameters.isEmpty())
                out.makeBlankPages(p);
        }
        return out;
    }

    private static boolean setParameter(SDDSFile out, int i, Definition d, String text, int page) {
        if (d.isEnum())
            return out.setParameter(i, d.enumDefinition.labelToCode(text).intValue(), page);
        if (d.type == SDDSUtil.SDDS_CHARACTER)
            return out.setParameter(i, text.isEmpty() ? (char) 0 : text.charAt(0), page);
        if (d.type == SDDSUtil.SDDS_STRING)
            return out.setParameter(i, text, page);
        return out.setParameter(i, blankToZero(text), page);
    }

    private static int[] codes(Definition d, String[] cells) {
        int[] codes = new int[cells.length];
        for (int k = 0; k < cells.length; k++)
            codes[k] = d.enumDefinition.labelToCode(cells[k]).intValue();
        return codes;
    }

    private static char[] characters(String[] cells) {
        char[] chars = new char[cells.length];
        for (int k = 0; k < cells.length; k++)
            chars[k] = cells[k].isEmpty() ? (char) 0 : cells[k].charAt(0);
        return chars;
    }

    private static String[] numbers(String[] cells) {
        String[] numbers = new String[cells.length];
        for (int k = 0; k < cells.length; k++)
            numbers[k] = blankToZero(cells[k]);
        return numbers;
    }

    private static boolean setArray(SDDSFile out, int i, Definition d, String[] cells, int page) {
        if (d.isEnum())
            return out.setArray(i, codes(d, cells), page);
        if (d.type == SDDSUtil.SDDS_CHARACTER)
            return out.setArray(i, characters(cells), page);
        if (d.type == SDDSUtil.SDDS_STRING)
            return out.setArray(i, cells, page);
        return out.setArray(i, numbers(cells), page);
    }

    private static boolean setColumn(SDDSFile out, int i, Definition d, String[] cells, int page) {
        if (d.isEnum())
            return out.setColumn(i, codes(d, cells), page, 1);
        if (d.type == SDDSUtil.SDDS_CHARACTER)
            return out.setColumn(i, characters(cells), page, 1);
        if (d.type == SDDSUtil.SDDS_STRING)
            return out.setColumn(i, cells, page, 1);
        return out.setColumn(i, numbers(cells), page, 1);
    }

    /** Write the document to a file directly, with no staging. */
    void writeTo(String name, boolean forceAscii) throws Failure {
        SDDSFile out = toFile(name, forceAscii);
        if (!out.writeFile())
            throw new Failure(out.getErrors().trim());
        if (out.noRowCount)
            out.closeOutput();
        version = out.layoutVersion;
    }

    /**
     * Save to a path. A symbolic link to a versioned file such as <code>data.001</code>
     * gets a new version with the link moved to it. Anything else is replaced whole.
     *
     * @return the file that was written
     * @throws Failure if it cannot be saved, leaving any existing file as it was
     */
    String save(String destination) throws Failure {
        Path link = Paths.get(destination).toAbsolutePath();
        Path target = link;
        boolean updateLink = false;
        if (Files.isSymbolicLink(link)) {
            try {
                target = link.getParent().resolve(Files.readSymbolicLink(link)).normalize();
            } catch (IOException e) {
                throw new Failure("Unable to follow the link " + destination);
            }
            Matcher versioned = Pattern.compile("(.*?)([.-])(\\d+)$").matcher(target.toString());
            if (versioned.matches()) {
                java.math.BigInteger number = new java.math.BigInteger(versioned.group(3));
                int width = versioned.group(3).length();
                do {
                    number = number.add(java.math.BigInteger.ONE);
                    StringBuilder digits = new StringBuilder(number.toString());
                    while (digits.length() < width)
                        digits.insert(0, '0');
                    target = Paths.get(versioned.group(1) + versioned.group(2) + digits);
                } while (Files.exists(target, LinkOption.NOFOLLOW_LINKS));
                updateLink = true;
            } else {
                target = throughLinks(target);
            }
        }
        checkReplaceable(target);
        if (isLocked(target))
            throw new Failure("Output file is locked");
        Path staging;
        try {
            staging = Files.createTempDirectory(target.getParent(), ".sddsedit-");
        } catch (IOException e) {
            throw new Failure("Failed to create staging directory beside " + target);
        }
        try {
            /* Keep the name, whose suffix selects any compression. */
            Path staged = staging.resolve(target.getFileName().toString());
            writeTo(staged.toString(), false);
            /* The file must be readable by this library before it replaces anything. */
            SDDSFile check = new SDDSFile(staged.toString());
            if (!check.readFile())
                throw new Failure("The saved file could not be read back: " + check.getErrors().trim());
            if (updateLink) {
                Files.copy(staged, target);
                syncToDisk(target);
                Path newLink = staging.resolve("link");
                Files.createSymbolicLink(newLink, target);
                Files.move(newLink, link, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                syncFolder(link.getParent());
            } else {
                copyPermissions(target, staged);
                replace(staged, target);
            }
            return target.toString();
        } catch (IOException e) {
            throw new Failure("Failed to replace " + target + ": " + e.getMessage());
        } finally {
            deleteTree(staging);
        }
    }

    /** Renaming over a file needs no write permission on it, so a read-only file is checked here. */
    private static void checkReplaceable(Path target) throws Failure {
        if (Files.exists(target) && !Files.isWritable(target))
            throw new Failure("The existing file is not writable: " + target);
    }

    /** Swap in a staged file, with its contents safely on disk first, so a crash cannot leave it empty. */
    private static void replace(Path staged, Path target) throws IOException {
        syncToDisk(staged);
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
        syncFolder(target.getParent());
    }

    private static void syncToDisk(Path file) throws IOException {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /* Makes a rename durable. Not every system can open a folder, and the rename has still happened. */
    private static void syncFolder(Path folder) {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            /* Nothing more can be done. */
        }
    }

    /** The file a path finally names, following any chain of links. */
    private static Path throughLinks(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : path;
        } catch (IOException e) {
            return path;
        }
    }

    private static void copyPermissions(Path from, Path to) throws IOException {
        if (!Files.exists(from))
            return;
        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        } catch (UnsupportedOperationException e) {
            /* Not every file system has these. */
        }
    }

    /** True if an SDDS program is writing the file. They hold a lock on it while they do. */
    private static boolean isLocked(Path path) {
        if (!Files.exists(path))
            return false;
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock lock = channel.tryLock();
            if (lock == null)
                return true;
            lock.release();
            return false;
        } catch (java.nio.channels.OverlappingFileLockException e) {
            return true;
        } catch (IOException e) {
            /* A file that cannot be opened for writing cannot be checked this way. */
            return false;
        }
    }

    private static void deleteTree(Path directory) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries)
                Files.deleteIfExists(entry);
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            /* Only a leftover empty staging directory. */
        }
    }

    /* ---- export ---- */

    private static String csvText(String text) {
        String t = text.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r"))
            t = '"' + t + '"';
        return t;
    }

    private static void csvRow(Writer out, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0)
                out.write(',');
            out.write(csvText(cells.get(i)));
        }
        out.write('\n');
    }

    private static String csvNumber(String text, Definition d) {
        return d.isNumeric() && text.trim().isEmpty() ? "0" : text;
    }

    /** True if a primitive-only format would lose a dictionary. */
    boolean hasEnums() {
        for (List<Definition> list : Arrays.asList(parameters, columns, arrays))
            for (Definition d : list)
                if (d.isEnum())
                    return true;
        return false;
    }

    /** Export as comma separated values, replacing the destination only when finished. */
    void writeCSV(String destination) throws Failure {
        if (hasEnums())
            throw new Failure("Convert enum elements to integer codes or label strings before exporting to this format.");
        for (int i = 0; i < pages.size(); i++) {
            String problem = pageProblem(i);
            if (problem != null)
                throw new Failure(problem);
        }
        Path target = throughLinks(Paths.get(destination).toAbsolutePath());
        checkReplaceable(target);
        Path staging = null;
        try {
            /* A new file in a new folder gets the permissions that the user's settings give. */
            staging = Files.createTempDirectory(target.getParent(), ".sddsedit-");
            Path staged = staging.resolve(target.getFileName().toString());
            try (Writer out = Files.newBufferedWriter(staged, StandardCharsets.UTF_8)) {
                for (int number = 0; number < pages.size(); number++) {
                    Page page = pages.get(number);
                    if (number > 0)
                        out.write('\n');
                    if (!parameters.isEmpty()) {
                        out.write("Parameters\n");
                        for (int i = 0; i < parameters.size(); i++)
                            csvRow(out, Arrays.asList(parameters.get(i).name,
                                    csvNumber(page.parameters[i], parameters.get(i))));
                        out.write('\n');
                    }
                    if (!columns.isEmpty()) {
                        out.write("Columns\n");
                        List<String> names = new ArrayList<String>();
                        for (Definition d : columns)
                            names.add(d.name);
                        csvRow(out, names);
                        for (int r = 0; r < page.rows; r++) {
                            List<String> row = new ArrayList<String>();
                            for (int c = 0; c < columns.size(); c++)
                                row.add(csvNumber(page.columns[c][r], columns.get(c)));
                            csvRow(out, row);
                        }
                        out.write('\n');
                    }
                    if (!arrays.isEmpty()) {
                        out.write("Arrays\n");
                        List<String> names = new ArrayList<String>();
                        int longest = 0;
                        for (int a = 0; a < arrays.size(); a++) {
                            names.add(arrays.get(a).name);
                            longest = Math.max(longest, page.arrays[a].values.length);
                        }
                        csvRow(out, names);
                        for (int r = 0; r < longest; r++) {
                            List<String> row = new ArrayList<String>();
                            for (int a = 0; a < arrays.size(); a++)
                                row.add(r < page.arrays[a].values.length
                                        ? csvNumber(page.arrays[a].values[r], arrays.get(a))
                                        : "");
                            csvRow(out, row);
                        }
                        out.write('\n');
                    }
                }
            }
            copyPermissions(target, staged);
            replace(staged, target);
        } catch (IOException e) {
            throw new Failure("Failed while writing CSV output: " + e.getMessage());
        } finally {
            if (staging != null)
                deleteTree(staging);
        }
    }

    /**
     * Export as HDF5 with the toolkit's <code>sdds2hdf</code>. Its output is staged beside
     * the destination and replaces it only if the program reports no problem, because the
     * program truncates its output first, and can exit 0 after failing.
     *
     * @param destination file to write
     * @param running     set to the program's process, so that the caller can stop it
     * @throws Failure with the message to show, leaving any existing file as it was
     */
    void exportHDF(String destination, java.util.concurrent.atomic.AtomicReference<Process> running) throws Failure {
        if (hasEnums())
            throw new Failure("Convert enum elements to integer codes or label strings before exporting to this format.");
        Path target = throughLinks(Paths.get(destination).toAbsolutePath());
        checkReplaceable(target);
        Path staging;
        try {
            staging = Files.createTempDirectory(target.getParent(), ".sddsedit-");
        } catch (IOException e) {
            throw new Failure("Failed to create staging directory beside " + target);
        }
        try {
            Path input = staging.resolve("export.sdds");
            Path staged = staging.resolve(target.getFileName().toString() + ".h5");
            Path log = staging.resolve("sdds2hdf.log");
            writeTo(input.toString(), false);
            Process process;
            try {
                process = new ProcessBuilder(hdfProgram, input.toString(), staged.toString()).redirectErrorStream(true)
                        .redirectOutput(log.toFile()).start();
            } catch (IOException e) {
                throw new Failure("HDF export runs the sdds2hdf program, which could not be started. Is it on the PATH?");
            }
            running.set(process);
            int status = process.waitFor();
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            if (status != 0)
                throw new Failure("sdds2hdf failed with exit status " + status + firstLines(output));
            if (output.contains("HDF5-DIAG") || !Files.exists(staged) || Files.size(staged) == 0)
                throw new Failure("sdds2hdf could not write the file" + firstLines(output));
            copyPermissions(target, staged);
            replace(staged, target);
        } catch (IOException e) {
            throw new Failure("Failed to replace " + target + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure("HDF export was interrupted");
        } finally {
            Process process = running.get();
            if (process != null)
                process.destroy();
            deleteTree(staging);
        }
    }

    private static String firstLines(String output) {
        String[] lines = output.trim().split("\n");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.length && i < 4; i++)
            if (!lines[i].trim().isEmpty())
                text.append('\n').append(lines[i].trim());
        return text.toString();
    }

    /* ---- shapes ---- */

    /**
     * True if the library could not read a shape back. It multiplies the sizes in order and
     * stops if the product gets too large, even when a later zero would leave the array empty.
     */
    static boolean shapeTooLarge(int[] dims) {
        long size = 1;
        for (int d : dims) {
            size *= Math.max(d, 0);
            if (size > Integer.MAX_VALUE - 8)
                return true;
        }
        return false;
    }

    /** The product of dimensions, or -1 if negative or too large. */
    static int product(int[] dims) {
        if (dims.length == 0)
            return 0;
        boolean empty = false;
        for (int d : dims) {
            if (d < 0)
                return -1;
            empty = empty || d == 0;
        }
        if (empty)
            return 0;
        long size = 1;
        for (int d : dims) {
            size *= d;
            if (size > Integer.MAX_VALUE - 8)
                return -1;
        }
        return (int) size;
    }

    /**
     * Copy array values into a new shape so that each surviving element keeps its
     * coordinates. SDDS stores the last dimension fastest. New cells hold the fill text.
     */
    static String[] reshape(String[] values, int[] oldDims, int[] newDims, String fill) {
        int size = Math.max(product(newDims), 0);
        String[] result = new String[size];
        Arrays.fill(result, fill);
        if (oldDims.length == 0 || newDims.length == 0 || product(oldDims) != values.length) {
            System.arraycopy(values, 0, result, 0, Math.min(size, values.length));
            return result;
        }
        int count = oldDims.length;
        int axes = Math.max(count, newDims.length);
        int[] coord = new int[count];
        for (int flat = 0; flat < values.length; flat++) {
            int rest = flat;
            for (int d = count - 1; d >= 0; d--) {
                coord[d] = rest % oldDims[d];
                rest /= oldDims[d];
            }
            long target = 0;
            boolean inside = true;
            for (int d = 0; d < axes && inside; d++) {
                int index = d < count ? coord[d] : 0;
                if (d >= newDims.length) {
                    inside = index == 0;
                    continue;
                }
                inside = index < newDims[d];
                target = target * newDims[d] + index;
            }
            if (inside && target < size)
                result[(int) target] = values[flat];
        }
        return result;
    }

    /** Text for an array shape such as <code>4\u00d72</code>. */
    static String shapeText(int[] dims) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < dims.length; i++) {
            if (i > 0)
                out.append('\u00d7');
            out.append(dims[i]);
        }
        return out.toString();
    }

    /** The largest number of rows or elements on a page of this document. */
    int longestArray(int page) {
        int longest = 0;
        for (ArrayCells cells : pages.get(page).arrays)
            longest = Math.max(longest, cells.values.length);
        return longest;
    }

    /** Check a new definition name against a list, ignoring one position. */
    static String nameProblem(String name, String kind, List<Definition> list, int ignore) {
        if (name == null || !SDDSUtil.isValidName(name))
            return "Invalid " + kind + " name: " + name;
        for (int i = 0; i < list.size(); i++)
            if (i != ignore && list.get(i).name.equals(name))
                return "Duplicate " + kind + " name: " + name;
        return null;
    }
}
