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
import java.util.zip.*;
import java.net.*;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

/**
 * The SDDS File object is used to read and write Self Describing
 * Data Sets. It creates and manages SDDS Parameter objects and
 * SDDS Column objects. A brief introduction to the SDDS concept can be found
 * <a href=http://www.aps.anl.gov/asd/oag/SDDSIntroTalk/slides.html>here</a>.
 *
 * @author Robert Soliday
 *         <a href=mailto:soliday@aps.anl.gov>soliday@aps.anl.gov</a>
 * @version 1.0 (November 1, 1999)
 * @since JDK 1.2
 *
 * @version 1.1 (November, 2001)
 * @since JDK 1.3
 */
public class SDDSFile {
    FileOutputStream fos;
    FileInputStream fis;
    InputStream is;
    BufferedOutputStream bos;
    BufferedInputStream bis;
    GZIPInputStream gzis;
    GZIPOutputStream gzos;
    DataOutputStream dos;
    DataInputStream dis;
    LEDataOutputStream ledos;
    LEDataInputStream ledis;
    public String fileName;
    public URL URLName;
    public String descriptionText = null;
    public String descriptionContents = null;
    int parameterCount = 0;
    SDDSParameter[] parameterObject;
    int columnCount = 0;
    SDDSColumn[] columnObject;
    int arrayCount = 0;
    SDDSArray[] arrayObject;
    int n_errors;
    String[] error_description;
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
    public boolean asciiFile;
    public boolean noRowCount;
    public boolean bigEndian;
    public boolean columnMajorOrder;
    public int layoutVersion;
    public boolean isURL = false, isIS = false;
    int linesPerRow = 1;
    int additionalHeaderLines = 0;
    boolean fixedRowCount = false;
    private int minimumVersion = 1;
    private boolean layoutWritten = false;
    private boolean definingEnum = false;
    private boolean dataCommandSeen = false;
    private boolean bigEndianComment = false;
    private boolean littleEndianComment = false;
    private boolean fixedRowCountComment = false;
    private boolean pagesEnded = false;
    private boolean endianSet = false;
    private final static int INCLUDE_DEPTH_LIMIT = 64;
    private final static int ARRAY_SIZE_LIMIT = Integer.MAX_VALUE - 8;

    /**
     * Create a new SDDS file that can be used to read and write SDDS files.
     */
    public SDDSFile() {
        n_errors = 0;
        asciiFile = true;
        noRowCount = false;
        columnMajorOrder = false;
        bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    /**
     * Create a new SDDS file that can be used to read and write SDDS files.
     * 
     * @param filename file name to read or write data
     */
    public SDDSFile(String filename) {
        fileName = filename;
        n_errors = 0;
        asciiFile = true;
        noRowCount = false;
        columnMajorOrder = false;
        bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    /**
     * Use a supplied InputStream object to read an SDDS file
     * 
     * @param is0 InputStream object to read from
     */
    public SDDSFile(InputStream is0) {
        fileName = null;
        is = is0;
        isIS = true;
        n_errors = 0;
        asciiFile = true;
        noRowCount = false;
        columnMajorOrder = false;
        bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    /**
     * Create a new SDDS file that can be used to read and write SDDS files.
     * 
     * @param filename file name to read or write data
     */
    public SDDSFile(URL filename) {
        fileName = filename.getFile();
        URLName = filename;
        n_errors = 0;
        asciiFile = true;
        noRowCount = false;
        columnMajorOrder = false;
        isURL = true;
        bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    /**
     * Create a new SDDS file that can be used to read and write SDDS files.
     * 
     * @param binary <code>true</code> if binary output, <code>false</code> if ascii
     *               output
     */
    public SDDSFile(boolean binary) {
        n_errors = 0;
        asciiFile = !binary;
        noRowCount = false;
        columnMajorOrder = false;
        bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    /**
     * Create a new SDDS file that can be used to read and write SDDS files.
     * 
     * @param filename file name to read or write data
     * @param binary   <code>true</code> if binary output, <code>false</code> if
     *                 ascii output
     */
    public SDDSFile(String filename, boolean binary) {
        fileName = filename;
        n_errors = 0;
        asciiFile = !binary;
        noRowCount = false;
        columnMajorOrder = false;
        bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    /**
     * Set the file name for input and output.
     * 
     * @param filename file name to read or write data
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean setFileName(String filename) {
        fileName = filename;
        return true;
    }

    /**
     * Set binary mode for accessing files.
     * 
     * @param binary <code>true</code> if binary output, <code>false</code> if ascii
     *               output
     */
    public void setMode(boolean binary) {
        asciiFile = !binary;
    }

    /**
     * Set endian mode for accessing files.
     * 
     * @param little_endian <code>true</code> if little endian input and output,
     *                      <code>false</code> if big endian input and output. Not
     *                      used with ascii files.
     */
    public void setEndian(boolean little_endian) {
        bigEndian = !little_endian;
        endianSet = true;
    }

    /**
     * Set for fixed number of rows in column data or dynamic. This method will have
     * no effect
     * if called after header is written.
     *
     * @param norowcount <code>true</code> dynamic column data
     *                   <code>false</code> fixed number of rows in column data
     *
     * @return <code>true</code> if successful <br>
     *         <code>false</code> if an error occurs
     */
    public boolean setNoRowCount(boolean norowcount) {
        noRowCount = norowcount;
        return true;
    }

    /**
     * Switch writing of column data from one file to a new file without having to
     * redefine parameters,
     * arrays and columns. This is useful for when noRowCount is set and the number
     * of rows has grown
     * rather large and continuing the data in a new file would seem appropriate.
     * The parameter and array data
     * is duplicated in the new file and the column data headers are duplicated in
     * the new file
     * without carrying the actual column data from the old file over. Fresh row
     * data can be added one
     * row at a time to the new file.
     *
     * @param fileName <code>fileName</code> The new file to be written into
     *
     * @return <code>true</code> If success<br>
     *         <code>false</code> If an error occurs
     */
    public boolean switchToNewFile(String fileName) {
        /*
         * delete the row data but keep the row definitions and all arrays and
         * parameters
         */
        noRowCount = true;
        int rowCount;
        for (int page = 1; page <= pageCount(); page++) {
            rowCount = getRowCount(page);
            for (int row = rowCount; row >= 1; row--) {
                deleteRow(page, row);
            }
        }
        /* set new file name */
        setFileName(fileName);
        return writeFile();
    }

    /**
     * Duplicate an SDDSFile object copying over appropriate header information and
     * data.
     *
     * @param file       SDDSFile object to be copied<br>
     * @param parameters <code>true</code> copy parameter information<br>
     *                   <code>false</code> ignore parameter data
     * @param arrays     <code>true</code> copy array information<br>
     *                   <code>false</code> ignore array data
     * @param columns    <code>true</code> copy column information<br>
     *                   <code>false</code> ignore column data
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if error occurs
     */
    public boolean duplicateCopy(SDDSFile file, boolean parameters, boolean arrays, boolean columns) {
        int index;
        for (int i = 0; parameters && i < file.parameterCount; i++) {
            SDDSParameter source = file.parameterObject[i];
            if (source.getType() == SDDS_ENUM)
                index = defineEnumParameter(source.getName(), source.getSymbol(), source.getUnits(),
                        source.getDescription(), null, source.getEnumValues(), source.getFixed_Value());
            else
                index = defineParameter(source.getName(), source.getSymbol(), source.getUnits(),
                        source.getDescription(), source.getFormat_String(), source.getType(), source.getFixed_Value());
            if (index == -1)
                return false;
            parameterObject[index].copyValues(source);
        }
        for (int i = 0; arrays && i < file.arrayCount; i++) {
            SDDSArray source = file.arrayObject[i];
            if (source.getType() == SDDS_ENUM)
                index = defineEnumArray(source.getName(), source.getSymbol(), source.getUnits(),
                        source.getDescription(), null, source.getGroup_Name(), source.getEnumValues(), 0,
                        source.getDimensions());
            else
                index = defineArray(source.getName(), source.getSymbol(), source.getUnits(), source.getDescription(),
                        source.getFormat_String(), source.getGroup_Name(), source.getType(), 0,
                        source.getDimensions());
            if (index == -1)
                return false;
            arrayObject[index].copyValues(source);
        }
        for (int i = 0; columns && i < file.columnCount; i++) {
            SDDSColumn source = file.columnObject[i];
            if (source.getType() == SDDS_ENUM)
                index = defineEnumColumn(source.getName(), source.getSymbol(), source.getUnits(),
                        source.getDescription(), null, source.getEnumValues(), 0);
            else
                index = defineColumn(source.getName(), source.getSymbol(), source.getUnits(),
                        source.getDescription(), source.getFormat_String(), source.getType(),
                        source.getField_Length());
            if (index == -1)
                return false;
            columnObject[index].copyValues(source);
        }
        return true;
    }

    /**
     * Open a file for writting. A file name ending in <code>.gz</code> is written with
     * gzip compression, and one ending in <code>.xz</code> or <code>.lzma</code> with
     * XZ compression, as the C library does.
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if error occurs
     */
    public boolean openOutputFile() {
        if (fileName == null) {
            bos = new BufferedOutputStream(System.out, 4096 /* buffsize */);
            dos = new DataOutputStream(bos);
            ledos = new LEDataOutputStream(bos);

        } else {
            try {
                fos = new FileOutputStream(fileName, false /* append */);
            } catch (IOException e) {
                setError("problem opening output file (openOutputFile)");
                return false;
            }
            if (fileName.endsWith(".gz")) {
                try {
                    gzos = new GZIPOutputStream(fos, 4096 /* buffsize */);
                } catch (Exception e) {
                    setError("unable to open file in gzip format (openOutputFile)");
                    return false;
                }
                bos = new BufferedOutputStream(gzos, 4096 /* buffsize */);
            } else if (fileName.endsWith(".xz") || fileName.endsWith(".lzma")) {
                try {
                    bos = new BufferedOutputStream(SDDSXZ.openOutput(fos), 65536 /* buffsize */);
                } catch (Exception e) {
                    setError("unable to open file in xz format (openOutputFile)");
                    return false;
                }
            } else {
                bos = new BufferedOutputStream(fos, 4096 /* buffsize */);
            }
            dos = new DataOutputStream(bos);
            ledos = new LEDataOutputStream(bos);
        }
        return true;
    }

    /**
     * Write out the SDDS file. Every page is checked before the output file is opened,
     * so a value that cannot be written leaves an existing file alone. A file with no
     * row counts stays open for {@link #writeRow} if it is plain ascii. Rows cannot be
     * added to binary or compressed files, so those are always closed here.
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean writeFile() {
        if (!validateEnumLayout())
            return false;
        int pages = pageCount();
        for (int page = 1; page <= pages; page++) {
            if (!preparePage(page))
                return false;
        }
        if (!openOutputFile()) {
            return false;
        }
        if (!writeLayout()) {
            closeOutput();
            return false;
        }
        if (!writePages()) {
            closeOutput();
            return false;
        }
        boolean compressed = (fileName != null)
                && (fileName.endsWith(".gz") || fileName.endsWith(".xz") || fileName.endsWith(".lzma"));
        if (!noRowCount || !asciiFile || compressed) {
            if (!closeOutput()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Read the SDDS file
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean readFile() {
        if (!openInputFile()) {
            return false;
        }
        if (!parseHeaders()) {
            closeInput();
            return false;
        }
        if (asciiFile) {
            if (!readAsciiData()) {
                closeInput();
                return false;
            }
        } else {
            if (!readBinaryData()) {
                closeInput();
                return false;
            }
        }
        if (!closeInput())
            return false;
        return true;
    }

    private static String openProblem(String name) {
        java.io.File file = new java.io.File(name);
        if (!file.exists())
            return "does not exist";
        return file.isDirectory() ? "is a directory" : "cannot be read";
    }

    /**
     * Open SDDS input file. A file name ending in <code>.gz</code> is read with gzip
     * decompression. One ending in <code>.xz</code> or <code>.lzma</code> is read as
     * either an XZ or a legacy LZMA stream, whichever it holds.
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean openInputFile() {
        InputStream source;
        if (is == null && fileName == null) {
            source = System.in;
        } else {
            if (is == null) {
                try {
                    if (isURL) {
                        is = URLName.openStream();
                    } else {
                        fis = new FileInputStream(fileName);
                    }
                } catch (Exception e) {
                    setError((isURL ? "URL does not exist" : "input file " + openProblem(fileName)) + " (openInputFile)");
                    return false;
                }
            }
            source = (isURL || isIS) ? is : fis;
            if (fileName != null && fileName.endsWith(".gz")) {
                try {
                    /* As with gzopen, a file with this name need not actually be compressed. */
                    BufferedInputStream peek = new BufferedInputStream(source, 65536 /* buffsize */);
                    peek.mark(2);
                    boolean gzipped = (peek.read() == 0x1f) && (peek.read() == 0x8b);
                    peek.reset();
                    source = peek;
                    if (gzipped) {
                        gzis = new GZIPInputStream(peek, 4096 /* buffsize */);
                        source = gzis;
                    }
                } catch (Exception e) {
                    setError("input file has an invalid gzip format (openInputFile)");
                    return false;
                }
            } else if (fileName != null && (fileName.endsWith(".xz") || fileName.endsWith(".lzma"))) {
                try {
                    source = SDDSXZ.openInput(source);
                } catch (Exception e) {
                    setError("input file has an invalid xz format (openInputFile)");
                    return false;
                }
            }
        }
        bis = new BufferedInputStream(source, 65536 /* buffsize */);
        dis = new DataInputStream(bis);
        ledis = new LEDataInputStream(bis);
        return true;
    }

    /**
     * Parse SDDS input file header lines. SDDS6 files use the bounded SDDS6 grammar;
     * SDDS1 to SDDS5 files use the legacy grammar and cannot declare enums.
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean parseHeaders() {
        arrayCount = parameterCount = columnCount = 0;
        descriptionText = descriptionContents = null;
        noRowCount = false;
        columnMajorOrder = false;
        linesPerRow = 1;
        additionalHeaderLines = 0;
        fixedRowCount = false;
        dataCommandSeen = false;
        bigEndianComment = littleEndianComment = fixedRowCountComment = false;
        try {
            String first = SDDSText.readRawLine(dis);
            if (first == null) {
                setError("unable to read header lines (parseHeaders)");
                return false;
            }
            if (!first.startsWith("SDDS")) {
                setError("Missing SDDS version number (parseHeaders)");
                return false;
            }
            try {
                layoutVersion = (int) SDDSText.scanLong(first.substring(4));
            } catch (NumberFormatException e) {
                setError("Missing SDDS version number (parseHeaders)");
                return false;
            }
            if ((layoutVersion < 1) || (layoutVersion > SDDSUtil.SDDS_VERSION)) {
                setError("SDDS version of file is unrecognized by this version of SDDS/Java (parseHeaders)");
                return false;
            }
            if (!readLayout(dis, 0)) {
                setError("problem reading header lines (parseHeaders)");
                return false;
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            setError(e.getMessage() + " (parseHeaders)");
            return false;
        } catch (OutOfMemoryError e) {
            setError("not enough memory to read header lines (parseHeaders)");
            return false;
        }
    }

    /** Process layout commands from a stream, descending into included files. */
    private boolean readLayout(InputStream in, int depth) throws IOException {
        String command;
        while ((command = layoutVersion == 6 ? readCommand6(in) : readCommandLegacy(in)) != null) {
            int space = -1;
            for (int i = 0; (i < command.length()) && (space < 0); i++) {
                if ((command.charAt(i) == ' ') || (command.charAt(i) == '\t'))
                    space = i;
            }
            if (space < 0)
                throw new IOException("no groupname in namelist");
            String group = command.substring(1, space);
            String text = command.substring(space + 1);
            if (group.equals("description")) {
                Map<String, String> f = parseCommand(text, SDDSNamelist.DESCRIPTION_FIELDS);
                descriptionText = SDDSText.fromRaw(f.get("text"));
                descriptionContents = SDDSText.fromRaw(f.get("contents"));
            } else if (group.equals("column")) {
                processColumn(parseCommand(text, SDDSNamelist.COLUMN_FIELDS));
            } else if (group.equals("parameter")) {
                processParameter(parseCommand(text, SDDSNamelist.PARAMETER_FIELDS));
            } else if (group.equals("array")) {
                processArray(parseCommand(text, SDDSNamelist.ARRAY_FIELDS));
            } else if (group.equals("associate")) {
                /* The toolkit is built without associate support and ignores these too. */
            } else if (group.equals("data")) {
                processDataMode(parseCommand(text, SDDSNamelist.DATA_FIELDS));
                return true;
            } else if (group.equals("include")) {
                String filename = SDDSText.fromRaw(parseCommand(text, SDDSNamelist.INCLUDE_FIELDS).get("filename"));
                if (filename == null)
                    throw new IOException("Problem parsing include namelist");
                if (depth >= INCLUDE_DEPTH_LIMIT)
                    throw new IOException("Too many nested include commands");
                try (InputStream included = new BufferedInputStream(new FileInputStream(filename))) {
                    if (!readLayout(included, depth + 1))
                        return false;
                }
                if (dataCommandSeen)
                    return true;
            } else {
                throw new IOException("Unknown layout entry " + group + " given");
            }
        }
        /* Only a clean end of file may finish an included file. */
        return depth != 0;
    }

    private Map<String, String> parseCommand(String text, java.util.List<String> fields) throws IOException {
        return layoutVersion == 6 ? SDDSNamelist.parse6(text, fields) : SDDSNamelist.parseLegacy(text, fields);
    }

    /** Read the next line that is not a comment, with any inline comment removed. */
    private String nextLine(InputStream in) throws IOException {
        String line;
        while ((line = SDDSText.readRawLine(in)) != null) {
            if (line.charAt(0) != '!')
                return SDDSText.cutOutComments(line);
            if ((line.length() > 1) && (line.charAt(1) == '#'))
                parseSpecialComment(line.substring(2));
        }
        return null;
    }

    /** Note the settings given by a comment such as <code>!# big-endian</code>. */
    private void parseSpecialComment(String text) {
        int[] cursor = { 0 };
        String token;
        while (((token = SDDSText.nextToken(text, cursor)) != null) && !token.isEmpty()) {
            if (token.equals("big-endian"))
                bigEndianComment = true;
            else if (token.equals("little-endian"))
                littleEndianComment = true;
            else if (token.equals("fixed-rowcount"))
                fixedRowCountComment = true;
        }
    }

    private static String lineToSpace(String line) {
        int n = line.length();
        if ((n > 0) && (line.charAt(n - 1) == '\n')) {
            n--;
            if ((n > 0) && (line.charAt(n - 1) == '\r'))
                n--;
            StringBuilder out = new StringBuilder(line.substring(0, n));
            while (out.length() < line.length())
                out.append(' ');
            return out.toString();
        }
        return line;
    }

    /**
     * Read one command with the legacy grammar (SDDS_GetNamelist).
     *
     * @return the raw command starting at its ampersand, or <code>null</code> if there
     *         are no more commands
     */
    private String readCommandLegacy(InputStream in) throws IOException {
        String line;
        while ((line = nextLine(in)) != null) {
            int amp = line.indexOf('&');
            if ((amp >= 0) && !SDDSText.isQuoted(line, amp))
                break;
        }
        if (line == null)
            return null;
        StringBuilder buffer = new StringBuilder(lineToSpace(line));
        int start = -1;
        for (int p = 0; (p < buffer.length()) && (start < 0);) {
            char c = buffer.charAt(p);
            if (c == '"') {
                p++;
                while ((p < buffer.length()) && (buffer.charAt(p) != '"'))
                    p++;
                if (p < buffer.length())
                    p++;
            } else if (c == '&') {
                if (buffer.indexOf("&end", p) == p)
                    return null;
                start = p;
            } else {
                p++;
            }
        }
        if (start < 0)
            return null;
        for (int q = buffer.length() - 4; q >= 0; q--) {
            if ((buffer.charAt(q) == '&') && ((q == 0) || (buffer.charAt(q - 1) != '\\'))
                    && (buffer.indexOf("&end", q) == q) && !SDDSText.isQuoted(buffer, q))
                return buffer.substring(start, q);
        }
        for (int lines = 0; (line = nextLine(in)) != null; lines++) {
            String part = lineToSpace(line);
            int offset = buffer.length();
            int end = part.indexOf("&end");
            buffer.append(part);
            if ((end >= 0) && !SDDSText.isQuoted(buffer, offset + end))
                return buffer.substring(start);
            /* A file with binary data dumped into its header must not loop forever. */
            if (lines > 10000)
                return null;
        }
        return null;
    }

    /**
     * Read one command with the bounded SDDS6 grammar (SDDS6_GetNamelist), preserving
     * quoted delimiters and escape parity.
     *
     * @return the raw command without its <code>&amp;end</code>, or <code>null</code>
     *         at a clean end of file
     */
    private String readCommand6(InputStream in) throws IOException {
        StringBuilder s = new StringBuilder(256);
        StringBuilder commentText = new StringBuilder();
        long limit = SDDSEnumDefinition.getLayoutLimit();
        boolean quoted = false, escaped = false, started = false, comment = false;
        int c;
        while ((c = in.read()) != -1) {
            if (c == 0)
                throw new IOException("NUL in SDDS6 header");
            if (comment) {
                if (c == '\n') {
                    if ((commentText.length() > 0) && (commentText.charAt(0) == '#'))
                        parseSpecialComment(commentText.substring(1));
                    commentText.setLength(0);
                    comment = false;
                    /* Inside a command the newline ending a comment still separates tokens. */
                    if (!started)
                        continue;
                } else {
                    if (commentText.length() < 255)
                        commentText.append((char) c);
                    continue;
                }
            }
            if (!quoted && (c == '!')) {
                comment = true;
                continue;
            }
            if (!started) {
                if (c != '&')
                    continue;
                started = true;
            }
            if (s.length() + 1 >= limit)
                throw new IOException("SDDS6 layout command exceeds configured byte limit");
            s.append(((c == '\n') || (c == '\r')) && !quoted ? ' ' : (char) c);
            if ((c == '"') && !escaped)
                quoted = !quoted;
            int used = s.length();
            if (!quoted && (used >= 4) && (s.charAt(used - 4) == '&') && (s.charAt(used - 3) == 'e')
                    && (s.charAt(used - 2) == 'n') && (s.charAt(used - 1) == 'd')) {
                /* Consume the rest of the header line without consuming binary page bytes. */
                boolean trailingComment = false;
                while (((c = in.read()) != -1) && (c != '\n')) {
                    if (c == 0)
                        throw new IOException("NUL after SDDS6 namelist");
                    if (c == '!')
                        trailingComment = true;
                    if (trailingComment)
                        continue;
                    if ((c != ' ') && (c != '\t') && (c != '\r') && (c != ','))
                        throw new IOException("Unexpected text after SDDS6 &end");
                }
                s.setLength(used - 4);
                return s.toString();
            }
            escaped = (c == '\\') && !escaped;
        }
        if (started)
            throw new IOException("Unterminated SDDS6 namelist");
        return null;
    }

    private int typeField(Map<String, String> f) throws IOException {
        String type = f.get("type");
        if (type == null)
            return 0;
        int id = SDDSUtil.identifyTypeExactly(type);
        if (id == 0)
            throw new IOException("Unrecognized value given for type: " + type);
        return id;
    }

    /** Apply the rule that only enums, and only in SDDS6, may have enum_values. */
    private void checkEnumFields(int type, String enumValues) throws IOException {
        if (((type == SDDS_ENUM) || (enumValues != null)) && (layoutVersion < 6))
            throw new IOException("Enum definitions require SDDS6");
        if ((type != SDDS_ENUM) && (enumValues != null))
            throw new IOException("enum_values is only valid for type=enum");
    }

    private void processColumn(Map<String, String> f) throws IOException {
        int type = typeField(f);
        String enumValues = SDDSText.fromRaw(f.get("enum_values"));
        checkEnumFields(type, enumValues);
        int length = SDDSNamelist.integerField(f, "field_length", 0);
        String name = SDDSText.fromRaw(f.get("name")), symbol = SDDSText.fromRaw(f.get("symbol"));
        String units = SDDSText.fromRaw(f.get("units")), description = SDDSText.fromRaw(f.get("description"));
        String format = SDDSText.fromRaw(f.get("format_string"));
        int index = type == SDDS_ENUM ? defineEnumColumn(name, symbol, units, description, format, enumValues, length)
                : defineColumn(name, symbol, units, description, format, type, length);
        if (index == -1)
            throw new IOException("invalid column header");
    }

    private void processParameter(Map<String, String> f) throws IOException {
        int type = typeField(f);
        String enumValues = SDDSText.fromRaw(f.get("enum_values"));
        checkEnumFields(type, enumValues);
        String name = SDDSText.fromRaw(f.get("name")), symbol = SDDSText.fromRaw(f.get("symbol"));
        String units = SDDSText.fromRaw(f.get("units")), description = SDDSText.fromRaw(f.get("description"));
        String format = SDDSText.fromRaw(f.get("format_string")), fixed = SDDSText.fromRaw(f.get("fixed_value"));
        int index = type == SDDS_ENUM
                ? defineEnumParameter(name, symbol, units, description, format, enumValues, fixed)
                : defineParameter(name, symbol, units, description, format, type, fixed);
        if (index == -1)
            throw new IOException("invalid parameter header");
    }

    private void processArray(Map<String, String> f) throws IOException {
        int type = typeField(f);
        String enumValues = SDDSText.fromRaw(f.get("enum_values"));
        checkEnumFields(type, enumValues);
        int length = SDDSNamelist.integerField(f, "field_length", 0);
        int dimensions = SDDSNamelist.integerField(f, "dimensions", 1);
        String name = SDDSText.fromRaw(f.get("name")), symbol = SDDSText.fromRaw(f.get("symbol"));
        String units = SDDSText.fromRaw(f.get("units")), description = SDDSText.fromRaw(f.get("description"));
        String format = SDDSText.fromRaw(f.get("format_string")), group = SDDSText.fromRaw(f.get("group_name"));
        int index = type == SDDS_ENUM
                ? defineEnumArray(name, symbol, units, description, format, group, enumValues, length, dimensions)
                : defineArray(name, symbol, units, description, format, group, type, length, dimensions);
        if (index == -1)
            throw new IOException("invalid array header");
    }

    private void processDataMode(Map<String, String> f) throws IOException {
        String mode = f.get("mode");
        if (mode == null)
            throw new IOException("Problem with data namelist: mode not given.");
        if (!mode.equals("ascii") && !mode.equals("binary"))
            throw new IOException("Unrecognized value given for mode: " + mode);
        asciiFile = mode.equals("ascii");
        linesPerRow = SDDSNamelist.integerField(f, "lines_per_row", 1);
        if (asciiFile && (linesPerRow < 0))
            throw new IOException("Unable to process data mode--lines_per_row is invalid");
        noRowCount = SDDSNamelist.integerField(f, "no_row_counts", 0) != 0;
        additionalHeaderLines = SDDSNamelist.integerField(f, "additional_header_lines", 0);
        columnMajorOrder = (short) SDDSNamelist.integerField(f, "column_major_order", 0) != 0;
        fixedRowCount = fixedRowCountComment || (SDDSNamelist.integerField(f, "fixed_row_count", 0) != 0);
        String endian = f.get("endian");
        if ((endian != null) && !endian.equals("big") && !endian.equals("little"))
            throw new IOException("Unrecognized value given for endian: " + endian);
        if (bigEndianComment && littleEndianComment)
            throw new IOException("Unable to read data as it says it is both big and little endian");
        boolean declaredBig = bigEndianComment || "big".equals(endian);
        boolean declaredLittle = littleEndianComment || "little".equals(endian);
        boolean nativeBig = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
        /* Data with no declared order is native, and of two conflicting declarations the non-native one wins. */
        if (declaredBig && declaredLittle)
            bigEndian = !nativeBig;
        else if (declaredBig || declaredLittle)
            bigEndian = declaredBig;
        else
            bigEndian = nativeBig;
        endianSet = false;
        dataCommandSeen = true;
    }

    /** Holds the values of one page until the whole page has been read. */
    private final class PageData {
        final Object[] parameters = new Object[parameterCount];
        final int[][] dimensions = new int[arrayCount][];
        final Object[] arrays = new Object[arrayCount];
        final Object[] columns = new Object[columnCount];
        int rows = 0;

        void commit(int page) {
            for (int i = 0; i < parameterCount; i++) {
                parameterObject[i].makeBlankPages(page);
                if (parameters[i] != null)
                    parameterObject[i].store(parameters[i], page);
            }
            for (int i = 0; i < arrayCount; i++) {
                arrayObject[i].makeBlankPages(page);
                arrayObject[i].setDim(page, dimensions[i]);
                arrayObject[i].store(arrays[i], Array.getLength(arrays[i]), page);
            }
            for (int i = 0; i < columnCount; i++) {
                columnObject[i].makeBlankPages(page);
                if (rows > 0)
                    columnObject[i].store(columns[i], rows, page, 1);
            }
        }
    }

    /** Multiply array dimensions, refusing a size Java cannot hold. */
    private int arrayElements(int[] dim) throws IOException {
        long elements = 1;
        for (int i = 0; i < dim.length; i++) {
            if (dim[i] < 0)
                throw new IOException("Unable to read array--dimensions missing or negative");
            elements *= dim[i];
            if (elements > ARRAY_SIZE_LIMIT)
                throw new IOException("Array size exceeds limit in Java SDDS");
        }
        return (int) elements;
    }

    /** Read as many bytes as are left, up to the size of the buffer. */
    private int readAvailable(byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) {
            int count = dis.read(b, n, b.length - n);
            if (count < 0)
                break;
            n += count;
        }
        return n;
    }

    /**
     * Read the row count of a binary page.
     *
     * @return the count, or <code>-1</code> at the end of the data
     */
    private long readBinaryRowCount() throws IOException {
        byte[] b = new byte[4];
        if (readAvailable(b) != 4)
            return -1;
        int rows32 = 0;
        for (int i = 0; i < 4; i++)
            rows32 = (rows32 << 8) | (b[bigEndian ? i : 3 - i] & 0xff);
        if (rows32 != Integer.MIN_VALUE) {
            if (rows32 < 0)
                throw new IOException("Unable to read page--invalid number of rows");
            return rows32;
        }
        /* The marker for a row count too large for 32 bits. */
        b = new byte[8];
        if (readAvailable(b) != 8)
            return -1;
        long rows = 0;
        for (int i = 0; i < 8; i++)
            rows = (rows << 8) | (b[bigEndian ? i : 7 - i] & 0xffL);
        if (rows < 0)
            throw new IOException("Unable to read page--invalid number of rows");
        return rows;
    }

    /**
     * Read SDDS input file binary data
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean readBinaryData() {
        DataInput in = bigEndian ? (DataInput) dis : (DataInput) ledis;
        pagesEnded = false;
        try {
            for (int page = 1; !pagesEnded; page++) {
                long rows = readBinaryRowCount();
                if (rows < 0)
                    return true;
                if (rows > ARRAY_SIZE_LIMIT) {
                    setError("Row count exceeds limit in Java SDDS (readBinaryData)");
                    return false;
                }
                PageData data = new PageData();
                data.rows = (int) rows;
                for (int i = 0; i < parameterCount; i++) {
                    if (parameterObject[i].fixed) {
                        checkFixedValue(i);
                        continue;
                    }
                    int type = parameterObject[i].getType();
                    data.parameters[i] = SDDSValues.newArray(type, 1);
                    SDDSValues.readBinary(in, type, data.parameters[i], 0, bigEndian,
                            parameterObject[i].getEnumDefinition());
                }
                for (int i = 0; i < arrayCount; i++) {
                    int type = arrayObject[i].getType();
                    int[] dim = new int[arrayObject[i].getDimensions()];
                    for (int j = 0; j < dim.length; j++)
                        dim[j] = in.readInt();
                    int elements = arrayElements(dim);
                    data.dimensions[i] = dim;
                    data.arrays[i] = SDDSValues.newArray(type, elements);
                    for (int j = 0; j < elements; j++)
                        SDDSValues.readBinary(in, type, data.arrays[i], j, bigEndian,
                                arrayObject[i].getEnumDefinition());
                }
                readBinaryColumns(in, data);
                data.commit(page);
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            setError("error reading binary data: " + (e instanceof java.io.EOFException ? "the file ends too soon" : e.getMessage())
                    + " (readBinaryData)");
        } catch (OutOfMemoryError e) {
            setError("not enough memory to read binary data (readBinaryData)");
        }
        return false;
    }

    private void readBinaryColumns(DataInput in, PageData data) throws IOException {
        if (columnCount == 0) {
            data.rows = 0;
            return;
        }
        for (int i = 0; i < columnCount; i++)
            data.columns[i] = SDDSValues.newArray(columnObject[i].getType(), data.rows);
        if (columnMajorOrder) {
            for (int i = 0; i < columnCount; i++) {
                int type = columnObject[i].getType();
                SDDSEnumDefinition definition = columnObject[i].getEnumDefinition();
                for (int row = 0; row < data.rows; row++)
                    SDDSValues.readBinary(in, type, data.columns[i], row, bigEndian, definition);
            }
            return;
        }
        int[] types = new int[columnCount];
        SDDSEnumDefinition[] definitions = new SDDSEnumDefinition[columnCount];
        for (int i = 0; i < columnCount; i++) {
            types[i] = columnObject[i].getType();
            definitions[i] = columnObject[i].getEnumDefinition();
        }
        for (int row = 0; row < data.rows; row++) {
            try {
                for (int i = 0; i < columnCount; i++)
                    SDDSValues.readBinary(in, types[i], data.columns[i], row, bigEndian, definitions[i]);
            } catch (EOFException e) {
                /* A file being written with a fixed row count may not have reached its count yet. */
                if (!fixedRowCount)
                    throw e;
                data.rows = row;
                pagesEnded = true;
                return;
            }
        }
    }

    /**
     * Read SDDS input file ascii data
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean readAsciiData() {
        boolean dataExpected = (columnCount > 0) || (arrayCount > 0);
        for (int i = 0; (i < parameterCount) && !dataExpected; i++)
            dataExpected = !parameterObject[i].fixed;
        pagesEnded = false;
        try {
            for (int i = 0; i < additionalHeaderLines; i++) {
                if (nextLine(dis) == null)
                    return true;
            }
            for (int page = 1; !pagesEnded; page++) {
                /* With only fixed values there is no page data, and just one page. */
                if (!dataExpected && (page != 1))
                    return true;
                PageData data = new PageData();
                if (!readAsciiParameters(data) || !readAsciiArrays(data) || !readAsciiColumns(data, page))
                    return true;
                data.commit(page);
            }
            return true;
        } catch (IOException | IllegalArgumentException e) {
            setError(e.getMessage() + " (readAsciiData)");
        } catch (OutOfMemoryError e) {
            setError("not enough memory to read ascii data (readAsciiData)");
        }
        return false;
    }

    /** A fixed value that cannot be read makes every page that uses it unreadable. */
    private void checkFixedValue(int index) throws IOException {
        if (!parameterObject[index].isValidFixedValue())
            throw new IOException("Unable to read page--invalid fixed value for parameter "
                    + parameterObject[index].getName());
    }

    /** @return <code>false</code> if the data ended before the first value */
    private boolean readAsciiParameters(PageData data) throws IOException {
        boolean firstRead = true;
        for (int i = 0; i < parameterCount; i++) {
            if (parameterObject[i].fixed) {
                checkFixedValue(i);
                continue;
            }
            String line = nextLine(dis);
            if (line == null) {
                if (firstRead)
                    return false;
                throw new IOException("Unable to read parameters--data ends prematurely");
            }
            firstRead = false;
            int n = line.length();
            if (line.charAt(n - 1) == '\n')
                line = line.substring(0, n - 1);
            int type = parameterObject[i].getType();
            data.parameters[i] = SDDSValues.newArray(type, 1);
            try {
                if (type == SDDS_STRING) {
                    /* A string parameter is the whole line unless it starts with a quote. */
                    if (line.endsWith("\r"))
                        line = line.substring(0, line.length() - 1);
                    String text = line.startsWith("\"") ? SDDSText.nextToken(line, new int[] { 0 }) : line;
                    SDDSValues.scan(type, text == null ? "" : text, data.parameters[i], 0, null);
                } else {
                    String token = SDDSText.nextToken(line, new int[] { 0 });
                    if (token == null)
                        throw new NumberFormatException("no value");
                    SDDSValues.scan(type, token, data.parameters[i], 0, parameterObject[i].getEnumDefinition());
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Unable to read page--parameter scanning error for "
                        + parameterObject[i].getName() + ": " + e.getMessage());
            }
        }
        return true;
    }

    /** Scan one value of a column or an array from a line of text. */
    private void scanAsciiValue(int type, int fieldLength, String line, int[] cursor, Object data, int index,
            SDDSEnumDefinition definition) {
        String token;
        if (fieldLength != 0) {
            int size = Math.abs(fieldLength);
            int end = Math.min(cursor[0] + size, line.length());
            token = line.substring(cursor[0], end);
            cursor[0] = end;
            if ((type == SDDS_STRING) && (fieldLength < 0))
                token = SDDSText.removePadding(token);
        } else {
            token = SDDSText.nextToken(line, cursor);
            if (token == null)
                throw new NumberFormatException("tokenizing error");
        }
        SDDSValues.scan(type, token, data, index, definition);
    }

    /** @return <code>false</code> if the data ended before the first array */
    private boolean readAsciiArrays(PageData data) throws IOException {
        for (int i = 0; i < arrayCount; i++) {
            String line = nextLine(dis);
            if ((line == null) || SDDSText.isBlank(line, 0)) {
                if (i == 0)
                    return false;
                throw new IOException("Unable to read array--dimensions missing");
            }
            int[] dim = new int[arrayObject[i].getDimensions()];
            int[] cursor = { 0 };
            for (int j = 0; j < dim.length; j++) {
                String token = SDDSText.nextToken(line, cursor);
                try {
                    if ((token == null) || token.isEmpty())
                        throw new NumberFormatException("no value");
                    dim[j] = (int) SDDSText.scanLong(token);
                } catch (NumberFormatException e) {
                    throw new IOException("Unable to read array--dimensions missing or negative");
                }
            }
            int elements = arrayElements(dim);
            int type = arrayObject[i].getType();
            int fieldLength = arrayObject[i].getField_Length();
            SDDSEnumDefinition definition = arrayObject[i].getEnumDefinition();
            Object values = SDDSValues.newArray(type, elements);
            data.dimensions[i] = dim;
            data.arrays[i] = values;
            int j = 0;
            while (j < elements) {
                line = nextLine(dis);
                if ((line == null) || SDDSText.isBlank(line, 0))
                    throw new IOException("Unable to read array--data ends prematurely");
                cursor[0] = 0;
                try {
                    do {
                        scanAsciiValue(type, fieldLength, line, cursor, values, j, definition);
                    } while ((++j < elements) && !SDDSText.isBlank(line, cursor[0]));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Unable to read array " + arrayObject[i].getName()
                            + "--error scanning data element: " + e.getMessage());
                }
            }
        }
        return true;
    }

    /** @return <code>false</code> if the data ended and this page does not exist */
    private boolean readAsciiColumns(PageData data, int page) throws IOException {
        if (columnCount == 0)
            return true;
        long rowLimit = -1;
        if (!noRowCount) {
            String s;
            do {
                s = SDDSText.readRawLine(dis);
                if (s == null)
                    return false;
            } while (s.charAt(0) == '!');
            try {
                rowLimit = SDDSText.scanLong(s);
            } catch (NumberFormatException e) {
                rowLimit = -1;
            }
            if (rowLimit < 0)
                throw new IOException("Unable to read page--file has no (valid) number-of-rows entry");
            if (rowLimit > ARRAY_SIZE_LIMIT)
                throw new IOException("Row count exceeds limit in Java SDDS");
            if (rowLimit == 0)
                return true;
        }
        int capacity = rowLimit < 0 ? 64 : (int) Math.min(rowLimit, 65536);
        int[] types = new int[columnCount];
        int[] fieldLengths = new int[columnCount];
        SDDSEnumDefinition[] definitions = new SDDSEnumDefinition[columnCount];
        for (int i = 0; i < columnCount; i++) {
            types[i] = columnObject[i].getType();
            fieldLengths[i] = columnObject[i].getField_Length();
            definitions[i] = columnObject[i].getEnumDefinition();
            data.columns[i] = SDDSValues.newArray(types[i], capacity);
        }
        String line = "";
        int[] cursor = { 0 };
        int row = 0;
        boolean endOfData = false, dataRead = false;
        do {
            if (row >= capacity) {
                capacity = (int) Math.min(2L * capacity, ARRAY_SIZE_LIMIT);
                for (int i = 0; i < columnCount; i++)
                    data.columns[i] = SDDSValues.copyOf(data.columns[i], capacity);
            }
            int lineCount = 0;
            dataRead = false;
            for (int i = 0; i < columnCount; i++) {
                if (SDDSText.isBlank(line, cursor[0])) {
                    line = nextLine(dis);
                    dataRead = line != null;
                    cursor[0] = 0;
                    if ((line == null) || SDDSText.isBlank(line, 0)) {
                        if (noRowCount) {
                            endOfData = true;
                            break;
                        }
                        /* A file being written with a fixed row count may be incomplete. */
                        if (fixedRowCount) {
                            data.rows = row;
                            pagesEnded = true;
                            return true;
                        }
                        throw new IOException("Unable to read page--data ends prematurely");
                    }
                    lineCount++;
                }
                try {
                    scanAsciiValue(types[i], fieldLengths[i], line, cursor, data.columns[i], row, definitions[i]);
                } catch (IllegalArgumentException e) {
                    if ((types[i] != SDDS_ENUM) && fixedRowCount) {
                        data.rows = row;
                        pagesEnded = true;
                        return true;
                    }
                    throw new IOException("Unable to read page--scanning error in column "
                            + columnObject[i].getName() + " at row " + (row + 1) + ": " + e.getMessage());
                }
            }
            if (endOfData)
                break;
            if ((linesPerRow != 0) && (lineCount != linesPerRow)) {
                if (fixedRowCount) {
                    data.rows = row;
                    pagesEnded = true;
                    return true;
                }
                throw new IOException("Unable to read page--line layout error at line " + (row + 1) + " of page "
                        + page);
            }
            if (linesPerRow != 0) {
                line = "";
                cursor[0] = 0;
            }
            row++;
        } while (noRowCount || (row < rowLimit));
        /* End of file does not make an empty page after the first when there are no row counts. */
        if (endOfData && (page != 1) && (row == 0) && !dataRead)
            return false;
        data.rows = row;
        return true;
    }

    /**
     * Create blank pages up to the last page specified
     * 
     * @param lastpage last page to create blank page
     */
    public void makeBlankPages(int lastpage) {
        int i;
        for (i = 0; i < columnCount; i++) {
            columnObject[i].makeBlankPages(lastpage);
        }
    }

    /**
     * Insert blank page at index specifed
     * 
     * @param page index of page to create;
     */
    public boolean insertPage(int page) {
        int i;
        for (i = 0; i < parameterCount; i++) {
            if (!parameterObject[i].insertPage(page)) {
                setError("problem encountered while inserting page (insertPage)");
                return false;
            }
        }
        for (i = 0; i < arrayCount; i++) {
            if (!arrayObject[i].insertPage(page)) {
                setError("problem encountered while inserting page (insertPage)");
                return false;
            }
        }
        for (i = 0; i < columnCount; i++) {
            if (!columnObject[i].insertPage(page)) {
                setError("problem encountered while inserting page (insertPage)");
                return false;
            }
        }
        return true;
    }

    /**
     * Delete page at index specifed
     * 
     * @param page index of page to delete;
     */
    public boolean deletePage(int page) {
        int i;
        for (i = 0; i < parameterCount; i++) {
            if (!parameterObject[i].deletePage(page)) {
                setError("problem encountered while deleteing page (deletePage)");
                return false;
            }
        }
        for (i = 0; i < arrayCount; i++) {
            if (!arrayObject[i].deletePage(page)) {
                setError("problem encountered while deleting page (deletePage)");
                return false;
            }
        }
        for (i = 0; i < columnCount; i++) {
            if (!columnObject[i].deletePage(page)) {
                setError("problem encountered while deleteing page (deletePage)");
                return false;
            }
        }
        return true;
    }

    /**
     * Close the SDDS output file
     * 
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if error occurs
     */
    public boolean closeOutput() {
        layoutWritten = false;
        try {
            dos.close();
        } catch (Exception e) {
            setError("problem closing output file (closeOutput)");
            return false;
        }

        return true;
    }

    /**
     * Close the SDDS input file
     * 
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if error occurs
     */
    public boolean closeInput() {
        try {
            dis.close();
            if (isURL)
                is = null;
        } catch (IOException e) {
            setError("problem closing input file (closeInput)");
            return false;
        }
        return true;
    }

    /**
     * Set the description text and contents.
     * 
     * @param text     may be <code>null</code>
     * @param contents may be <code>null</code>
     */
    public void setDescription(String text, String contents) {
        descriptionText = text;
        descriptionContents = contents;
        return;
    }

    /**
     * Delete parameter
     * 
     * @param name name of the parameter
     */
    public void deleteParameter(String name) {
        int i, j;
        i = getParameterIndex(name);
        if (i == -1)
            return;
        if (parameterCount == 1) {
            parameterObject[0] = null;
        } else {
            for (j = i; j < parameterCount - 1; j++) {
                parameterObject[j] = parameterObject[j + 1];
            }
            parameterObject[parameterCount - 1] = null;
        }
        parameterCount--;
    }

    /**
     * Delete array
     *
     * @param name name of the array
     */
    public void deleteArray(String name) {
        int i, j;
        i = getArrayIndex(name);
        if (i == -1)
            return;
        if (arrayCount == 1) {
            arrayObject[0] = null;
        } else {
            for (j = i; j < arrayCount - 1; j++) {
                arrayObject[j] = arrayObject[j + 1];
            }
            arrayObject[arrayCount - 1] = null;
        }
        arrayCount--;
    }

    /**
     * Delete column
     * 
     * @param name name of the column
     */
    public void deleteColumn(String name) {
        int i, j;
        i = getColumnIndex(name);
        if (i == -1)
            return;
        if (columnCount == 1) {
            columnObject[0] = null;
        } else {
            for (j = i; j < columnCount - 1; j++) {
                columnObject[j] = columnObject[j + 1];
            }
            columnObject[columnCount - 1] = null;
        }
        columnCount--;
    }

    /**
     * Rename parameter
     * 
     * @param oldname old name of the parameter
     * @param newname new name of the parameter
     */
    public void renameParameter(String oldname, String newname) {
        int i;
        i = getParameterIndex(oldname);
        if (i == -1)
            return;
        if (!newNameAllowed(newname, indexInUse(parameterObject, parameterCount, newname), i, "renameParameter"))
            return;
        parameterObject[i].setName(newname);
    }

    /**
     * Rename array
     *
     * @param oldname old name of the array
     * @param newname new name of the array
     */
    public void renameArray(String oldname, String newname) {
        int i;
        i = getArrayIndex(oldname);
        if (i == -1)
            return;
        if (!newNameAllowed(newname, indexInUse(arrayObject, arrayCount, newname), i, "renameArray"))
            return;
        arrayObject[i].setName(newname);
    }

    /**
     * Rename column
     * 
     * @param oldname old name of the column
     * @param newname new name of the column
     */
    public void renameColumn(String oldname, String newname) {
        int i;
        i = getColumnIndex(oldname);
        if (i == -1)
            return;
        if (!newNameAllowed(newname, indexInUse(columnObject, columnCount, newname), i, "renameColumn"))
            return;
        columnObject[i].setName(newname);
    }

    /** Returns the index of an element by name, without recording an error if there is none. */
    private static int indexInUse(Object[] elements, int count, String name) {
        for (int i = 0; i < count; i++) {
            String existing = elements[i] instanceof SDDSParameter ? ((SDDSParameter) elements[i]).getName()
                    : elements[i] instanceof SDDSArray ? ((SDDSArray) elements[i]).getName()
                            : ((SDDSColumn) elements[i]).getName();
            if (name.equals(existing))
                return i;
        }
        return -1;
    }

    /** Check a new name, which the file could not be read back with if invalid or in use. */
    private boolean newNameAllowed(String name, int existing, int index, String caller) {
        if (!SDDSUtil.isValidName(name)) {
            setError("The name \"" + name + "\" is invalid (" + caller + ")");
            return false;
        }
        if ((existing != -1) && (existing != index)) {
            setError("The name \"" + name + "\" is already in use (" + caller + ")");
            return false;
        }
        return true;
    }

    /** Check a format for its type, which the file could not be read back with if invalid. */
    private boolean formatAllowed(String format, int type, String caller) {
        if ((SDDSNamelist.blankToNull(format) != null) && !SDDSUtil.verifyPrintfFormat(format, type)) {
            setError("Invalid format string (" + caller + ")");
            return false;
        }
        return true;
    }

    /**
     * Set column symbol
     * 
     * @param index  index of the column
     * @param symbol symbol for the column
     */
    public void setColumnSymbol(int index, String symbol) {
        if ((index < 0) || (index >= columnCount))
            return;
        columnObject[index].setSymbol(symbol);
    }

    /**
     * Set array symbol
     * 
     * @param index  index of the array
     * @param symbol symbol for the array
     */
    public void setArraySymbol(int index, String symbol) {
        if ((index < 0) || (index >= arrayCount))
            return;
        arrayObject[index].setSymbol(symbol);
    }

    /**
     * Set parameter symbol
     * 
     * @param index  index of the parameter
     * @param symbol symbol for the parameter
     */
    public void setParameterSymbol(int index, String symbol) {
        if ((index < 0) || (index >= parameterCount))
            return;
        parameterObject[index].setSymbol(symbol);
    }

    /**
     * Set column units
     * 
     * @param index index of the column
     * @param units units for the column
     */
    public void setColumnUnits(int index, String units) {
        if ((index < 0) || (index >= columnCount))
            return;
        columnObject[index].setUnits(units);
    }

    /**
     * Set array units
     * 
     * @param index index of the array
     * @param units units for the array
     */
    public void setArrayUnits(int index, String units) {
        if ((index < 0) || (index >= arrayCount))
            return;
        arrayObject[index].setUnits(units);
    }

    /**
     * Set parameter units
     * 
     * @param index index of the parameter
     * @param units units for the parameter
     */
    public void setParameterUnits(int index, String units) {
        if ((index < 0) || (index >= parameterCount))
            return;
        parameterObject[index].setUnits(units);
    }

    /**
     * Set column description
     * 
     * @param index       index of the column
     * @param description description for the column
     */
    public void setColumnDescription(int index, String description) {
        if ((index < 0) || (index >= columnCount))
            return;
        columnObject[index].setDescription(description);
    }

    /**
     * Set array description
     * 
     * @param index       index of the array
     * @param description description for the array
     */
    public void setArrayDescription(int index, String description) {
        if ((index < 0) || (index >= arrayCount))
            return;
        arrayObject[index].setDescription(description);
    }

    /**
     * Set parameter description
     * 
     * @param index       index of the parameter
     * @param description description for the parameter
     */
    public void setParameterDescription(int index, String description) {
        if ((index < 0) || (index >= parameterCount))
            return;
        parameterObject[index].setDescription(description);
    }

    /**
     * Set column format string
     * 
     * @param index         index of the column
     * @param format_string format string for the column
     */
    public void setColumnFormatString(int index, String format_string) {
        if ((index < 0) || (index >= columnCount))
            return;
        if (!formatAllowed(format_string, columnObject[index].getType(), "setColumnFormatString"))
            return;
        try {
            columnObject[index].setFormat_String(format_string);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumnInformation)");
        }
    }

    /**
     * Set array format string
     * 
     * @param index         index of the array
     * @param format_string format string for the array
     */
    public void setArrayFormatString(int index, String format_string) {
        if ((index < 0) || (index >= arrayCount))
            return;
        if (!formatAllowed(format_string, arrayObject[index].getType(), "setArrayFormatString"))
            return;
        try {
            arrayObject[index].setFormat_String(format_string);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArrayInformation)");
        }
    }

    /**
     * Set parameter format string
     * 
     * @param index         index of the parameter
     * @param format_string format string for the parameter
     */
    public void setParameterFormatString(int index, String format_string) {
        if ((index < 0) || (index >= parameterCount))
            return;
        if (!formatAllowed(format_string, parameterObject[index].getType(), "setParameterFormatString"))
            return;
        try {
            parameterObject[index].setFormat_String(format_string);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameterInformation)");
        }
    }

    /**
     * Set array group name
     * 
     * @param index      index of the array
     * @param group_name group name for the array
     */
    public void setArrayGroupName(int index, String group_name) {
        if ((index < 0) || (index >= arrayCount))
            return;
        arrayObject[index].setGroup_Name(group_name);
    }

    /**
     * Set column field length
     * 
     * @param index        index of the column
     * @param field_length field length for the column
     */
    public void setColumnFieldLength(int index, int field_length) {
        if ((index < 0) || (index >= columnCount))
            return;
        try {
            columnObject[index].setField_Length(field_length);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumnInformation)");
        }
    }

    /**
     * Set array field length
     * 
     * @param index        index of the array
     * @param field_length field length for the array
     */
    public void setArrayFieldLength(int index, int field_length) {
        if ((index < 0) || (index >= arrayCount))
            return;
        try {
            arrayObject[index].setField_Length(field_length);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArrayInformation)");
        }
    }

    /**
     * Set parameter fixed value
     * 
     * @param index       index of the parameter
     * @param fixed_value fixed_value for the parameter
     */
    public void setParameterFixedValue(int index, String fixed_value) {
        if ((index < 0) || (index >= parameterCount))
            return;
        try {
            parameterObject[index].setFixed_Value(fixed_value);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameterInformation)");
        }
    }

    /**
     * Get parameter names
     * 
     * @return string array of parameter names
     */
    public String[] getParameterNames() {
        int i;
        String[] names;
        names = new String[parameterCount];
        for (i = 0; i < parameterCount; i++) {
            names[i] = getParameterName(i);
        }
        return names;
    }

    /**
     * Get parameter name
     * 
     * @param index index of the parameter
     * @return parameter name
     */
    public String getParameterName(int index) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getName();
    }

    /**
     * Get array names
     *
     * @return string of array names
     */
    public String[] getArrayNames() {
        int i;
        String[] names;
        names = new String[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            names[i] = getArrayName(i);
        }
        return names;
    }

    /**
     * Get array name
     *
     * @param index index of the array
     * @return array name
     */
    public String getArrayName(int index) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getName();
    }

    /**
     * Get column names
     * 
     * @return string array of column names
     */
    public String[] getColumnNames() {
        int i;
        String[] names;
        names = new String[columnCount];
        for (i = 0; i < columnCount; i++) {
            names[i] = getColumnName(i);
        }
        return names;
    }

    /**
     * Get column name
     * 
     * @param index index of the column
     * @return column name
     */
    public String getColumnName(int index) {
        if ((index < 0) || (index >= columnCount))
            return null;
        return columnObject[index].getName();
    }

    /**
     * Get all parameter units
     * 
     * @return string array of parameter units
     */
    public String[] getAllParameterUnits() {
        int i;
        String[] units;
        units = new String[parameterCount];
        for (i = 0; i < parameterCount; i++) {
            units[i] = getParameterUnits(i);
        }
        return units;
    }

    /**
     * Get parameter units
     * 
     * @param index index of the parameter
     * @return parameter units
     */
    public String getParameterUnits(int index) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getUnits();
    }

    /**
     * Get all array units
     *
     * @return string array of array units
     */
    public String[] getAllArrayUnits() {
        int i;
        String[] units;
        units = new String[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            units[i] = getArrayUnits(i);
        }
        return units;
    }

    /**
     * Get array units
     *
     * @param index index of the array
     * @return array units
     */
    public String getArrayUnits(int index) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getUnits();
    }

    /**
     * Get all column units
     * 
     * @return string array of column units
     */
    public String[] getAllColumnUnits() {
        int i;
        String[] units;
        units = new String[columnCount];
        for (i = 0; i < columnCount; i++) {
            units[i] = getColumnUnits(i);
        }
        return units;
    }

    /**
     * Get column units
     * 
     * @param index index of the column
     * @return column units
     */
    public String getColumnUnits(int index) {
        if ((index < 0) || (index >= columnCount))
            return null;
        return columnObject[index].getUnits();
    }

    /**
     * Get parameter symbols
     * 
     * @return string array of parameter symbols
     */
    public String[] getParameterSymbols() {
        int i;
        String[] symbols;
        symbols = new String[parameterCount];
        for (i = 0; i < parameterCount; i++) {
            symbols[i] = getParameterSymbol(i);
        }
        return symbols;
    }

    /**
     * Get parameter symbol
     * 
     * @param index index of the parameter
     * @return parameter symbol
     */
    public String getParameterSymbol(int index) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getSymbol();
    }

    /**
     * Get array symbols
     * 
     * @return string array of array symbols
     */
    public String[] getArraySymbols() {
        int i;
        String[] symbols;
        symbols = new String[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            symbols[i] = getArraySymbol(i);
        }
        return symbols;
    }

    /**
     * Get array symbol
     * 
     * @param index index of the array
     * @return array symbol
     */
    public String getArraySymbol(int index) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getSymbol();
    }

    /**
     * Get column symbols
     * 
     * @return string array of column symbols
     */
    public String[] getColumnSymbols() {
        int i;
        String[] symbols;
        symbols = new String[columnCount];
        for (i = 0; i < columnCount; i++) {
            symbols[i] = getColumnSymbol(i);
        }
        return symbols;
    }

    /**
     * Get column symbol
     * 
     * @param index index of the column
     * @return column symbol
     */
    public String getColumnSymbol(int index) {
        if ((index < 0) || (index >= columnCount))
            return null;
        return columnObject[index].getSymbol();
    }

    /**
     * Get parameter format strings
     * 
     * @return string array of parameter format strings
     */
    public String[] getParameterFormatStrings() {
        int i;
        String[] format_strings;
        format_strings = new String[parameterCount];
        for (i = 0; i < parameterCount; i++) {
            format_strings[i] = getParameterFormatString(i);
        }
        return format_strings;
    }

    /**
     * Get parameter format string
     * 
     * @param index index of the parameter
     * @return parameter format string
     */
    public String getParameterFormatString(int index) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getFormat_String();
    }

    /**
     * Get array format strings
     * 
     * @return string array of array format strings
     */
    public String[] getArrayFormatStrings() {
        int i;
        String[] format_strings;
        format_strings = new String[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            format_strings[i] = getArrayFormatString(i);
        }
        return format_strings;
    }

    /**
     * Get array format string
     * 
     * @param index index of the array
     * @return array format string
     */
    public String getArrayFormatString(int index) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getFormat_String();
    }

    /**
     * Get column format strings
     * 
     * @return string array of column format strings
     */
    public String[] getColumnFormatStrings() {
        int i;
        String[] format_strings;
        format_strings = new String[columnCount];
        for (i = 0; i < columnCount; i++) {
            format_strings[i] = getColumnFormatString(i);
        }
        return format_strings;
    }

    /**
     * Get column format string
     * 
     * @param index index of the column
     * @return column format string
     */
    public String getColumnFormatString(int index) {
        if ((index < 0) || (index >= columnCount))
            return null;
        return columnObject[index].getFormat_String();
    }

    /**
     * Get array group names
     * 
     * @return string array of array group names
     */
    public String[] getArrayGroupNames() {
        int i;
        String[] group_names;
        group_names = new String[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            group_names[i] = getArrayGroupName(i);
        }
        return group_names;
    }

    /**
     * Get array group name
     * 
     * @param index index of the array
     * @return array group name
     */
    public String getArrayGroupName(int index) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getGroup_Name();
    }

    /**
     * Get parameter types
     * 
     * @return int array of parameter types
     */
    public int[] getParameterTypes() {
        int i;
        int[] types;
        types = new int[parameterCount];
        for (i = 0; i < parameterCount; i++) {
            types[i] = getParameterType(i);
        }
        return types;
    }

    /**
     * Get parameter type
     * 
     * @param index index of the parameter
     * @return parameter type
     */
    public int getParameterType(int index) {
        if ((index < 0) || (index >= parameterCount))
            return 0;
        return parameterObject[index].getType();
    }

    /**
     * Get array types
     * 
     * @return int array of array types
     */
    public int[] getArrayTypes() {
        int i;
        int[] types;
        types = new int[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            types[i] = getArrayType(i);
        }
        return types;
    }

    /**
     * Get array type
     * 
     * @param index index of the array
     * @return array type
     */
    public int getArrayType(int index) {
        if ((index < 0) || (index >= arrayCount))
            return 0;
        return arrayObject[index].getType();
    }

    /**
     * Get column types
     * 
     * @return int array of column types
     */
    public int[] getColumnTypes() {
        int i;
        int[] types;
        types = new int[columnCount];
        for (i = 0; i < columnCount; i++) {
            types[i] = getColumnType(i);
        }
        return types;
    }

    /**
     * Get column type
     * 
     * @param index index of the column
     * @return column type
     */
    public int getColumnType(int index) {
        if ((index < 0) || (index >= columnCount))
            return 0;
        return columnObject[index].getType();
    }

    /**
     * Get field lengths
     * 
     * @return int array of column field lengths
     */
    public int[] getColumnFieldLengths() {
        int i;
        int[] lengths;
        lengths = new int[columnCount];
        for (i = 0; i < columnCount; i++) {
            lengths[i] = getColumnFieldLength(i);
        }
        return lengths;
    }

    /**
     * Get field lengths
     * 
     * @return int array of array field lengths
     */
    public int[] getArrayFieldLengths() {
        int i;
        int[] lengths;
        lengths = new int[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            lengths[i] = getArrayFieldLength(i);
        }
        return lengths;
    }

    /**
     * Get column field length
     * 
     * @param index index of the column
     * @return column field length
     */
    public int getColumnFieldLength(int index) {
        if ((index < 0) || (index >= columnCount))
            return 0;
        return columnObject[index].getField_Length();
    }

    /**
     * Get array field length
     * 
     * @param index index of the array
     * @return array field length
     */
    public int getArrayFieldLength(int index) {
        if ((index < 0) || (index >= arrayCount))
            return 0;
        return arrayObject[index].getField_Length();
    }

    /**
     * Get array dimensions
     * 
     * @param index index of the array
     * @return array dimensions
     */
    public int getArrayDimensions(int index) {
        if ((index < 0) || (index >= arrayCount))
            return 0;
        return arrayObject[index].getDimensions();
    }

    /**
     * Returns the number of elements in each dimension of the array
     * 
     * @param page  index of the page
     * @param index index of the array
     * @return array of sdds array dimensions
     */
    public int[] getArrayDim(int page, int index) {
        if ((index < 0) || (index >= arrayCount)) {
            return null;
        }
        return arrayObject[index].getDim(page);
    }

    /**
     * Sets the number of elements in each dimension of the array
     * 
     * @param page  index of the page
     * @param index index of the array
     * @param dim   array of sdds array dimensions
     */
    public void setArrayDim(int page, int index, int[] dim) {
        arrayObject[index].setDim(page, dim);
    }

    public void makeBlankArrayPage(int page, int index) {
        arrayObject[index].makeBlankPages(page);
    }

    /**
     * Get parameter descriptions
     * 
     * @return string array of parameter descriptions
     */
    public String[] getParameterDescriptions() {
        int i;
        String[] descriptions;
        descriptions = new String[parameterCount];
        for (i = 0; i < parameterCount; i++) {
            descriptions[i] = getParameterDescription(i);
        }
        return descriptions;
    }

    /**
     * Get parameter description
     * 
     * @param index index of the parameter
     * @return parameter description
     */
    public String getParameterDescription(int index) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getDescription();
    }

    /**
     * Get array descriptions
     * 
     * @return string array of array descriptions
     */
    public String[] getArrayDescriptions() {
        int i;
        String[] descriptions;
        descriptions = new String[arrayCount];
        for (i = 0; i < arrayCount; i++) {
            descriptions[i] = getArrayDescription(i);
        }
        return descriptions;
    }

    /**
     * Get array description
     * 
     * @param index index of the array
     * @return array description
     */
    public String getArrayDescription(int index) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getDescription();
    }

    /**
     * Get column descriptions
     * 
     * @return string array of column descriptions
     */
    public String[] getColumnDescriptions() {
        int i;
        String[] descriptions;
        descriptions = new String[columnCount];
        for (i = 0; i < columnCount; i++) {
            descriptions[i] = getColumnDescription(i);
        }
        return descriptions;
    }

    /**
     * Get column description
     * 
     * @param index index of the column
     * @return column description
     */
    public String getColumnDescription(int index) {
        if ((index < 0) || (index >= columnCount))
            return null;
        return columnObject[index].getDescription();
    }

    /**
     * Get parameter fixed value
     * 
     * @param index index of the parameter
     * @return parameter's fixed value
     */
    public String getParameterFixedValue(int index) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getFixed_Value();
    }

    /**
     * Get the values of the column for a chosen page
     *
     * @param index         index of the column
     * @param string_quotes <code>true</code> if quotes are to be added around
     *                      strings<br>
     *                      <code>false</code> if raw strings are to be outputed.
     * @param page          the page of the column, must be greater then
     *                      <code>0</code>
     * @return Object array of data
     */
    public Object[] getColumnValues(int index, int page, boolean string_quotes) {
        if ((index < 0) || (index >= columnCount))
            return null;
        return columnObject[index].getValues(page, string_quotes);
    }

    /**
     * Get the values of the array for a chosen page
     *
     * @param index         index of the array
     * @param string_quotes <code>true</code> if quotes are to be added around
     *                      strings<br>
     *                      <code>false</code> if raw strings are to be outputed.
     * @param page          the page of the array, must be greater then
     *                      <code>0</code>
     * @return Object array of data
     */
    public Object[] getArrayValues(int index, int page, boolean string_quotes) {
        if ((index < 0) || (index >= arrayCount))
            return null;
        return arrayObject[index].getValues(page, string_quotes);
    }

    /**
     * Get the value of the parameter for a chosen page
     *
     * @param index         index of the parameter
     * @param string_quotes <code>true</code> if quotes are to be added around
     *                      strings<br>
     *                      <code>false</code> if raw strings are to be outputed.
     * @param page          the page of the parameter, must be greater then
     *                      <code>0</code>
     * @return Object containing parameter data
     */
    public Object getParameterValue(int index, int page, boolean string_quotes) {
        if ((index < 0) || (index >= parameterCount))
            return null;
        return parameterObject[index].getValue(page, string_quotes);
    }

    /**
     * Creates a new parameter with the specified settings.
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
     *                      </ul>
     * @param fixed_value   may be <code>null</code>
     * @return index of the parameter<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineParameter(String name, String symbol, String units, String description, String format_string,
            int type, String fixed_value) {
        int i, j;
        i = parameterCount + 1;
        if (name == null) {
            setError("NULL name not allowed for parameter definition (defineParameter)");
            return -1;
        }
        if (!SDDSUtil.isValidName(name)) {
            setError("The following parameter name is invalid: >" + name + "< (defineParameter)");
            return -1;
        }
        if ((type == SDDS_ENUM) && !definingEnum) {
            setError("Use defineEnumColumn/Parameter/Array to supply an enum dictionary (defineParameter)");
            return -1;
        }
        for (j = 0; j < parameterCount; j++) {
            if (name.compareTo(parameterObject[j].getName()) == 0) {
                setError("Parameter " + name + " already exists (defineParameter)");
                return -1;
            }
        }
        if (!SDDSUtil.isValidType(type)) {
            setError("Unknown data type (defineParameter)");
            return -1;
        }
        format_string = SDDSNamelist.blankToNull(format_string);
        if (format_string != null) {
            if (!SDDSUtil.verifyPrintfFormat(format_string, type)) {
                setError("Invalid format string (defineParameter)");
                return -1;
            }
        }
        if (parameterObject == null) {
            parameterObject = new SDDSParameter[1];
        } else {
            try {
                parameterObject = (SDDSParameter[]) SDDSUtil.resize(parameterObject, i);
            } catch (Exception e) {
                setError("Unable to resize because variable is not an array (defineParameter)");
                return -1;
            }
        }
        parameterObject[parameterCount] = new SDDSParameter();
        parameterObject[parameterCount].setupParameter(name, symbol, units, description, format_string, type,
                fixed_value);
        parameterCount++;
        return parameterCount - 1;
    }

    /**
     * Creates a new parameter with the specified settings.
     * 
     * @param name          should be a valid name as defined by
     *                      SDDSUtil.isValidName
     * @param symbol        may be <code>null</code>
     * @param units         may be <code>null</code>
     * @param description   may be <code>null</code>
     * @param format_string may be <code>null</code>
     * @param type          valid values are:
     *                      <ul>
     *                      <li><code>"longdouble"</code></li>
     *                      <li><code>"double"</code></li>
     *                      <li><code>"float"</code></li>
     *                      <li><code>"long64"</code></li>
     *                      <li><code>"ulong64"</code></li>
     *                      <li><code>"long"</code></li>
     *                      <li><code>"ulong"</code></li>
     *                      <li><code>"short"</code></li>
     *                      <li><code>"ushort"</code></li>
     *                      <li><code>"string"</code></li>
     *                      <li><code>"character"</code></li>
     *                      </ul>
     * @param fixed_value   may be <code>null</code>
     * @return index of the parameter<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineParameter(String name, String symbol, String units,
            String description, String format_string,
            String type, String fixed_value) {
        return defineParameter(name, symbol, units, description, format_string,
                SDDSUtil.identifyType(type), fixed_value);
    }

    /**
     * Creates a new parameter with the specified settings.
     * 
     * @param name should be a valid name as defined by
     *             SDDSUtil.isValidName
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
     * @return index of the parameter<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineParameter(String name, int type) {
        return defineParameter(name, null, null, null, null, type, null);
    }

    /**
     * Creates a new parameter with the specified settings.
     * 
     * @param name should be a valid name as defined by
     *             SDDSUtil.isValidName
     * @param type valid values are:
     *             <ul>
     *             <li><code>"longdouble"</code></li>
     *             <li><code>"double"</code></li>
     *             <li><code>"float"</code></li>
     *             <li><code>"long64"</code></li>
     *             <li><code>"ulong64"</code></li>
     *             <li><code>"long"</code></li>
     *             <li><code>"ulong"</code></li>
     *             <li><code>"short"</code></li>
     *             <li><code>"ushort"</code></li>
     *             <li><code>"string"</code></li>
     *             <li><code>"character"</code></li>
     *             </ul>
     * @return index of the parameter<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineParameter(String name, String type) {
        return defineParameter(name, null, null, null, null, SDDSUtil.identifyType(type), null);
    }

    /**
     * Creates new parameters with the specified settings.
     * 
     * @param names          should be an array with valid names as defined by
     *                       SDDSUtil.isValidName
     * @param symbols        elements may be <code>null</code>
     * @param units          elements may be <code>null</code>
     * @param descriptions   elements may be <code>null</code>
     * @param format_strings elements may be <code>null</code>
     * @param types          valid values or the elements are:
     *                       <ul>
     *                       <li><code>1</code> for longdouble</li>
     *                       <li><code>2</code> for double</li>
     *                       <li><code>3</code> for float</li>
     *                       <li><code>4</code> for long64</li>
     *                       <li><code>5</code> for ulong64</li>
     *                       <li><code>6</code> for long</li>
     *                       <li><code>7</code> for ulong</li>
     *                       <li><code>8</code> for short</li>
     *                       <li><code>9</code> for ushort</li>
     *                       <li><code>10</code> for string</li>
     *                       <li><code>11</code> for character</li>
     *                       </ul>
     * @param fixed_values   elements may be <code>null</code>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineParameters(String[] names, String[] symbols, String[] units,
            String[] descriptions, String[] format_strings,
            int[] types, String[] fixed_values) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(symbols)) || (n != Array.getLength(units)) ||
                (n != Array.getLength(descriptions)) || (n != Array.getLength(format_strings)) ||
                (n != Array.getLength(types)) || (n != Array.getLength(fixed_values))) {
            setError("Unequal number of array elements (defineParameters)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineParameter(names[i], symbols[i], units[i], descriptions[i],
                    format_strings[i], types[i],
                    fixed_values[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new parameters with the specified settings.
     * 
     * @param names          should be an array with valid names as defined by
     *                       SDDSUtil.isValidName
     * @param symbols        elements may be <code>null</code>
     * @param units          elements may be <code>null</code>
     * @param descriptions   elements may be <code>null</code>
     * @param format_strings elements may be <code>null</code>
     * @param types          valid values or the elements are:
     *                       <ul>
     *                       <li><code>"longdouble"</code></li>
     *                       <li><code>"double"</code></li>
     *                       <li><code>"float"</code></li>
     *                       <li><code>"long64"</code></li>
     *                       <li><code>"ulong64"</code></li>
     *                       <li><code>"long"</code></li>
     *                       <li><code>"ulong"</code></li>
     *                       <li><code>"short"</code></li>
     *                       <li><code>"ushort"</code></li>
     *                       <li><code>"string"</code></li>
     *                       <li><code>"character"</code></li>
     *                       </ul>
     * @param fixed_values   elements may be <code>null</code>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineParameters(String[] names, String[] symbols, String[] units,
            String[] descriptions, String[] format_strings,
            String[] types, String[] fixed_values) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(symbols)) || (n != Array.getLength(units)) ||
                (n != Array.getLength(descriptions)) || (n != Array.getLength(format_strings)) ||
                (n != Array.getLength(types)) || (n != Array.getLength(fixed_values))) {
            setError("Unequal number of array elements (defineParameters)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineParameter(names[i], symbols[i], units[i], descriptions[i],
                    format_strings[i], SDDSUtil.identifyType(types[i]),
                    fixed_values[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new parameters with the specified settings.
     * 
     * @param names should be an array with valid names as defined by
     *              SDDSUtil.isValidName
     * @param types valid values or the elements are:
     *              <ul>
     *              <li><code>1</code> for longdouble</li>
     *              <li><code>2</code> for double</li>
     *              <li><code>3</code> for float</li>
     *              <li><code>4</code> for long64</li>
     *              <li><code>5</code> for ulong64</li>
     *              <li><code>6</code> for long</li>
     *              <li><code>7</code> for ulong</li>
     *              <li><code>8</code> for short</li>
     *              <li><code>9</code> for ushort</li>
     *              <li><code>10</code> for string</li>
     *              <li><code>11</code> for character</li>
     *              </ul>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineParameters(String[] names, int[] types) {
        int n, i;
        n = Array.getLength(names);
        if (n != Array.getLength(types)) {
            setError("Unequal number of array elements (defineParameters)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineParameter(names[i], null, null, null, null, types[i], null) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new parameters with the specified settings.
     * 
     * @param names should be an array with valid names as defined by
     *              SDDSUtil.isValidName
     * @param types valid values or the elements are:
     *              <ul>
     *              <li><code>"longdouble"</code></li>
     *              <li><code>"double"</code></li>
     *              <li><code>"float"</code></li>
     *              <li><code>"long64"</code></li>
     *              <li><code>"ulong64"</code></li>
     *              <li><code>"long"</code></li>
     *              <li><code>"ulong"</code></li>
     *              <li><code>"short"</code></li>
     *              <li><code>"ushort"</code></li>
     *              <li><code>"string"</code></li>
     *              <li><code>"character"</code></li>
     *              </ul>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineParameters(String[] names, String[] types) {
        int n, i;
        n = Array.getLength(names);
        if (n != Array.getLength(types)) {
            setError("Unequal number of array elements (defineParameters)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineParameter(names[i], null, null, null, null, SDDSUtil.identifyType(types[i]), null) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new array with the specified settings.
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
     *                      </ul>
     * @param field_length  has no effect currently
     * @param dimensions    integer
     * @return <code>-1</code> if error occurs <br>
     *         index of the array
     */
    public int defineArray(String name, String symbol, String units, String description,
            String format_string, String group_name, int type, int field_length, int dimensions) {
        int i, j;
        i = arrayCount + 1;
        if (name == null) {
            setError("NULL name not allowed for array definition (defineArray)");
            return -1;
        }
        if (!SDDSUtil.isValidName(name)) {
            setError("The following array name is invalid: >" + name + "< (defineArray)");
            return -1;
        }
        if ((type == SDDS_ENUM) && !definingEnum) {
            setError("Use defineEnumColumn/Parameter/Array to supply an enum dictionary (defineArray)");
            return -1;
        }
        for (j = 0; j < arrayCount; j++) {
            if (name.compareTo(arrayObject[j].getName()) == 0) {
                setError("Array " + name + " already exists (defineArray)");
                return -1;
            }
        }
        if (!SDDSUtil.isValidType(type)) {
            setError("Unknown data type (defineArray)");
            return -1;
        }
        format_string = SDDSNamelist.blankToNull(format_string);
        if (format_string != null) {
            if (!SDDSUtil.verifyPrintfFormat(format_string, type)) {
                setError("Invalid format string (defineArray)");
                return -1;
            }
        }
        if ((field_length < 0) && (type != SDDS_STRING)) {
            setError("Invalid field length (defineArray)");
            return -1;
        }
        if (dimensions <= 0) {
            setError("Invalid dimensions value (defineArray)");
            return -1;
        }
        if (arrayObject == null) {
            arrayObject = new SDDSArray[1];
        } else {
            try {
                arrayObject = (SDDSArray[]) SDDSUtil.resize(arrayObject, i);
            } catch (Exception e) {
                setError("Unable to resize because variable is not an array (defineArray)");
                return -1;
            }
        }
        arrayObject[arrayCount] = new SDDSArray();
        arrayObject[arrayCount].setupArray(name, symbol, units, description, format_string, group_name, type,
                field_length, dimensions);
        arrayCount++;
        return arrayCount - 1;
    }

    /**
     * Creates a new array with the specified settings.
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
     *                      <li><code>"longdouble"</code></li>
     *                      <li><code>"double"</code></li>
     *                      <li><code>"float"</code></li>
     *                      <li><code>"long64"</code></li>
     *                      <li><code>"ulong64"</code></li>
     *                      <li><code>"long"</code></li>
     *                      <li><code>"ulong"</code></li>
     *                      <li><code>"short"</code></li>
     *                      <li><code>"ushort"</code></li>
     *                      <li><code>"string"</code></li>
     *                      <li><code>"character"</code></li>
     *                      </ul>
     * @param field_length  has no effect currently
     * @param dimensions    integer
     * @return index of the array<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineArray(String name, String symbol, String units, String description,
            String format_string, String group_name, String type, int field_length, int dimensions) {
        return defineArray(name, symbol, units, description, format_string,
                group_name, SDDSUtil.identifyType(type), field_length, dimensions);
    }

    /**
     * Creates a new array with the specified settings.
     * 
     * @param name       should be a valid name as defined by
     *                   SDDSUtil.isValidName
     * @param type       valid values are:
     *                   <ul>
     *                   <li><code>1</code> for longdouble</li>
     *                   <li><code>2</code> for double</li>
     *                   <li><code>3</code> for float</li>
     *                   <li><code>4</code> for long64</li>
     *                   <li><code>5</code> for ulong64</li>
     *                   <li><code>6</code> for long</li>
     *                   <li><code>7</code> for ulong</li>
     *                   <li><code>8</code> for short</li>
     *                   <li><code>9</code> for ushort</li>
     *                   <li><code>10</code> for string</li>
     *                   <li><code>11</code> for character</li>
     *                   </ul>
     * @param dimensions integer
     * @return index of the array<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineArray(String name, int type, int dimensions) {
        return defineArray(name, null, null, null, null, null, type, 0, dimensions);
    }

    /**
     * Creates a new array with the specified settings.
     * 
     * @param name       should be a valid name as defined by
     *                   SDDSUtil.isValidName
     * @param type       valid values are:
     *                   <ul>
     *                   <li><code>"longdouble"</code></li>
     *                   <li><code>"double"</code></li>
     *                   <li><code>"float"</code></li>
     *                   <li><code>"long64"</code></li>
     *                   <li><code>"ulong64"</code></li>
     *                   <li><code>"long"</code></li>
     *                   <li><code>"ulong"</code></li>
     *                   <li><code>"short"</code></li>
     *                   <li><code>"ushort"</code></li>
     *                   <li><code>"string"</code></li>
     *                   <li><code>"character"</code></li>
     *                   </ul>
     * @param dimensions integer
     * @return index of the array<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineArray(String name, String type, int dimensions) {
        return defineArray(name, null, null, null, null, null, SDDSUtil.identifyType(type), 0, dimensions);
    }

    /**
     * Creates new arrays with the specified settings.
     * 
     * @param names          should be an array with valid names as defined by
     *                       SDDSUtil.isValidName
     * @param symbols        elements may be <code>null</code>
     * @param units          elements may be <code>null</code>
     * @param descriptions   elements may be <code>null</code>
     * @param format_strings elements may be <code>null</code>
     * @param group_names    elements may be <code>null</code>
     * @param types          valid values or the elements are:
     *                       <ul>
     *                       <li><code>1</code> for longdouble</li>
     *                       <li><code>2</code> for double</li>
     *                       <li><code>3</code> for float</li>
     *                       <li><code>4</code> for long64</li>
     *                       <li><code>5</code> for ulong64</li>
     *                       <li><code>6</code> for long</li>
     *                       <li><code>7</code> for ulong</li>
     *                       <li><code>8</code> for short</li>
     *                       <li><code>9</code> for ushort</li>
     *                       <li><code>10</code> for string</li>
     *                       <li><code>11</code> for character</li>
     *                       </ul>
     * @param field_lengths  elements have no effect currently
     * @param dimensions     integer elements
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineArrays(String[] names, String[] symbols, String[] units,
            String[] descriptions, String[] format_strings,
            String[] group_names, int[] types, int[] field_lengths, int[] dimensions) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(symbols)) || (n != Array.getLength(units)) ||
                (n != Array.getLength(descriptions)) || (n != Array.getLength(format_strings)) ||
                (n != Array.getLength(group_names)) || (n != Array.getLength(field_lengths)) ||
                (n != Array.getLength(types)) || (n != Array.getLength(dimensions))) {
            setError("Unequal number of array elements (defineArrays)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineArray(names[i], symbols[i], units[i], descriptions[i],
                    format_strings[i], group_names[i], types[i], field_lengths[i], dimensions[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new arrays with the specified settings.
     * 
     * @param names          should be an array with valid names as defined by
     *                       SDDSUtil.isValidName
     * @param symbols        elements may be <code>null</code>
     * @param units          elements may be <code>null</code>
     * @param descriptions   elements may be <code>null</code>
     * @param format_strings elements may be <code>null</code>
     * @param group_names    elements may be <code>null</code>
     * @param types          valid values or the elements are:
     *                       <ul>
     *                       <li><code>"longdouble"</code></li>
     *                       <li><code>"double"</code></li>
     *                       <li><code>"float"</code></li>
     *                       <li><code>"long64"</code></li>
     *                       <li><code>"ulong64"</code></li>
     *                       <li><code>"long"</code></li>
     *                       <li><code>"ulong"</code></li>
     *                       <li><code>"short"</code></li>
     *                       <li><code>"ushort"</code></li>
     *                       <li><code>"string"</code></li>
     *                       <li><code>"character"</code></li>
     *                       </ul>
     * @param field_lengths  elements have no effect currently
     * @param dimensions     integer elements
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineArrays(String[] names, String[] symbols, String[] units,
            String[] descriptions, String[] format_strings,
            String[] group_names, String[] types, int[] field_lengths, int dimensions[]) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(symbols)) || (n != Array.getLength(units)) ||
                (n != Array.getLength(descriptions)) || (n != Array.getLength(format_strings)) ||
                (n != Array.getLength(group_names)) || (n != Array.getLength(field_lengths)) ||
                (n != Array.getLength(types)) || (n != Array.getLength(dimensions))) {
            setError("Unequal number of array elements (defineArrays)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineArray(names[i], symbols[i], units[i], descriptions[i],
                    format_strings[i], group_names[i],
                    SDDSUtil.identifyType(types[i]), field_lengths[i], dimensions[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new arrays with the specified settings.
     * 
     * @param names      should be an array with valid names as defined by
     *                   SDDSUtil.isValidName
     * @param types      valid values or the elements are:
     *                   <ul>
     *                   <li><code>1</code> for longdouble</li>
     *                   <li><code>2</code> for double</li>
     *                   <li><code>3</code> for float</li>
     *                   <li><code>4</code> for long64</li>
     *                   <li><code>5</code> for ulong64</li>
     *                   <li><code>6</code> for long</li>
     *                   <li><code>7</code> for ulong</li>
     *                   <li><code>8</code> for short</li>
     *                   <li><code>9</code> for ushort</li>
     *                   <li><code>10</code> for string</li>
     *                   <li><code>11</code> for character</li>
     *                   </ul>
     * @param dimensions integer elements
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineArrays(String[] names, int[] types, int dimensions[]) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(types)) || (n != Array.getLength(dimensions))) {
            setError("Unequal number of array elements (defineArrays)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineArray(names[i], null, null, null, null, null, types[i], 0, dimensions[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new arrays with the specified settings.
     * 
     * @param names      should be an array with valid names as defined by
     *                   SDDSUtil.isValidName
     * @param types      valid values or the elements are:
     *                   <ul>
     *                   <li><code>"longdouble"</code></li>
     *                   <li><code>"double"</code></li>
     *                   <li><code>"float"</code></li>
     *                   <li><code>"long64"</code></li>
     *                   <li><code>"ulong64"</code></li>
     *                   <li><code>"long"</code></li>
     *                   <li><code>"ulong"</code></li>
     *                   <li><code>"short"</code></li>
     *                   <li><code>"ushort"</code></li>
     *                   <li><code>"string"</code></li>
     *                   <li><code>"character"</code></li>
     *                   </ul>
     * @param dimensions integer elements
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineArrays(String[] names, String[] types, int[] dimensions) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(types)) || (n != Array.getLength(dimensions))) {
            setError("Unequal number of array elements (defineArrays)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineArray(names[i], null, null, null, null, null, SDDSUtil.identifyType(types[i]), 0,
                    dimensions[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new column with the specified settings.
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
     *                      </ul>
     * @param field_length  has no effect currently
     * @return index of the column<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineColumn(String name, String symbol, String units, String description,
            String format_string, int type, int field_length) {
        int i, j;
        i = columnCount + 1;
        if (name == null) {
            setError("NULL name not allowed for column definition (defineColumn)");
            return -1;
        }
        if (!SDDSUtil.isValidName(name)) {
            setError("The following column name is invalid: >" + name + "< (defineColumn)");
            return -1;
        }
        if ((type == SDDS_ENUM) && !definingEnum) {
            setError("Use defineEnumColumn/Parameter/Array to supply an enum dictionary (defineColumn)");
            return -1;
        }
        for (j = 0; j < columnCount; j++) {
            if (name.compareTo(columnObject[j].getName()) == 0) {
                setError("Column " + name + " already exists (defineColumn)");
                return -1;
            }
        }
        if (!SDDSUtil.isValidType(type)) {
            setError("Unknown data type (defineColumn)");
            return -1;
        }
        format_string = SDDSNamelist.blankToNull(format_string);
        if (format_string != null) {
            if (!SDDSUtil.verifyPrintfFormat(format_string, type)) {
                setError("Invalid format string (defineColumn)");
                return -1;
            }
        }
        if ((field_length < 0) && (type != SDDS_STRING)) {
            setError("Invalid field length (defineColumn)");
            return -1;
        }
        if (columnObject == null) {
            columnObject = new SDDSColumn[1];
        } else {
            try {
                columnObject = (SDDSColumn[]) SDDSUtil.resize(columnObject, i);
            } catch (Exception e) {
                setError("Unable to resize because variable is not an array (defineColumn)");
                return -1;
            }
        }
        columnObject[columnCount] = new SDDSColumn();
        columnObject[columnCount].setupColumn(name, symbol, units, description, format_string, type, field_length);
        columnCount++;
        return columnCount - 1;
    }

    /**
     * Creates a new column with the specified settings.
     * 
     * @param name          should be a valid name as defined by
     *                      SDDSUtil.isValidName
     * @param symbol        may be <code>null</code>
     * @param units         may be <code>null</code>
     * @param description   may be <code>null</code>
     * @param format_string may be <code>null</code>
     * @param type          valid values are:
     *                      <ul>
     *                      <li><code>"longdouble"</code></li>
     *                      <li><code>"double"</code></li>
     *                      <li><code>"float"</code></li>
     *                      <li><code>"long64"</code></li>
     *                      <li><code>"ulong64"</code></li>
     *                      <li><code>"long"</code></li>
     *                      <li><code>"ulong"</code></li>
     *                      <li><code>"short"</code></li>
     *                      <li><code>"ushort"</code></li>
     *                      <li><code>"string"</code></li>
     *                      <li><code>"character"</code></li>
     *                      </ul>
     * @param field_length  has no effect currently
     * @return index of the column<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineColumn(String name, String symbol, String units, String description, String format_string,
            String type, int field_length) {
        return defineColumn(name, symbol, units, description, format_string, SDDSUtil.identifyType(type), field_length);
    }

    /**
     * Creates a new column with the specified settings.
     * 
     * @param name should be a valid name as defined by
     *             SDDSUtil.isValidName
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
     * @return index of the column<br>
     *         <code>-1</code> if an error occurs
     */

    //// FIX
    public int defineColumn(String name, int type) {
        return defineColumn(name, null, null, null, null, type, 0);
    }

    /**
     * Creates a new column with the specified settings.
     * 
     * @param name should be a valid name as defined by
     *             SDDSUtil.isValidName
     * @param type valid values are:
     *             <ul>
     *             <li><code>"longdouble"</code></li>
     *             <li><code>"double"</code></li>
     *             <li><code>"float"</code></li>
     *             <li><code>"long64"</code></li>
     *             <li><code>"ulong64"</code></li>
     *             <li><code>"long"</code></li>
     *             <li><code>"ulong"</code></li>
     *             <li><code>"short"</code></li>
     *             <li><code>"ushort"</code></li>
     *             <li><code>"string"</code></li>
     *             <li><code>"character"</code></li>
     *             </ul>
     * @return index of the column<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineColumn(String name, String type) {
        return defineColumn(name, null, null, null, null, SDDSUtil.identifyType(type), 0);
    }

    /**
     * Creates new columns with the specified settings.
     * 
     * @param names          should be an array with valid names as defined by
     *                       SDDSUtil.isValidName
     * @param symbols        elements may be <code>null</code>
     * @param units          elements may be <code>null</code>
     * @param descriptions   elements may be <code>null</code>
     * @param format_strings elements may be <code>null</code>
     * @param types          valid values or the elements are:
     *                       <ul>
     *                       <li><code>1</code> for longdouble</li>
     *                       <li><code>2</code> for double</li>
     *                       <li><code>3</code> for float</li>
     *                       <li><code>4</code> for long64</li>
     *                       <li><code>5</code> for ulong64</li>
     *                       <li><code>6</code> for long</li>
     *                       <li><code>7</code> for ulong</li>
     *                       <li><code>8</code> for short</li>
     *                       <li><code>9</code> for ushort</li>
     *                       <li><code>10</code> for string</li>
     *                       <li><code>11</code> for character</li>
     *                       </ul>
     * @param field_lengths  has no effect currently
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineColumns(String[] names, String[] symbols, String[] units,
            String[] descriptions, String[] format_strings,
            int[] types, int[] field_lengths) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(symbols)) || (n != Array.getLength(units)) ||
                (n != Array.getLength(descriptions)) || (n != Array.getLength(format_strings)) ||
                (n != Array.getLength(types)) || (n != Array.getLength(field_lengths))) {
            setError("Unequal number of array elements (defineColumns)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineColumn(names[i], symbols[i], units[i], descriptions[i],
                    format_strings[i], types[i],
                    field_lengths[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new columns with the specified settings.
     * 
     * @param names          should be an array with valid names as defined by
     *                       SDDSUtil.isValidName
     * @param symbols        elements may be <code>null</code>
     * @param units          elements may be <code>null</code>
     * @param descriptions   elements may be <code>null</code>
     * @param format_strings elements may be <code>null</code>
     * @param types          valid values or the elements are:
     *                       <ul>
     *                       <li><code>"longdouble"</code></li>
     *                       <li><code>"double"</code></li>
     *                       <li><code>"float"</code></li>
     *                       <li><code>"long64"</code></li>
     *                       <li><code>"ulong64"</code></li>
     *                       <li><code>"long"</code></li>
     *                       <li><code>"ulong"</code></li>
     *                       <li><code>"short"</code></li>
     *                       <li><code>"ushort"</code></li>
     *                       <li><code>"string"</code></li>
     *                       <li><code>"character"</code></li>
     *                       </ul>
     * @param field_lengths  has no effect currently
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineColumns(String[] names, String[] symbols, String[] units,
            String[] descriptions, String[] format_strings,
            String[] types, int[] field_lengths) {
        int n, i;
        n = Array.getLength(names);
        if ((n != Array.getLength(symbols)) || (n != Array.getLength(units)) ||
                (n != Array.getLength(descriptions)) || (n != Array.getLength(format_strings)) ||
                (n != Array.getLength(types)) || (n != Array.getLength(field_lengths))) {
            setError("Unequal number of array elements (defineColumns)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineColumn(names[i], symbols[i], units[i], descriptions[i],
                    format_strings[i], SDDSUtil.identifyType(types[i]),
                    field_lengths[i]) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new columns with the specified settings.
     * 
     * @param names should be an array with valid names as defined by
     *              SDDSUtil.isValidName
     * @param types valid values or the elements are:
     *              <ul>
     *              <li><code>1</code> for longdouble</li>
     *              <li><code>2</code> for double</li>
     *              <li><code>3</code> for float</li>
     *              <li><code>4</code> for long64</li>
     *              <li><code>5</code> for ulong64</li>
     *              <li><code>6</code> for long</li>
     *              <li><code>7</code> for ulong</li>
     *              <li><code>8</code> for short</li>
     *              <li><code>9</code> for ushort</li>
     *              <li><code>10</code> for string</li>
     *              <li><code>11</code> for character</li>
     *              </ul>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineColumns(String[] names, int[] types) {
        int n, i;
        n = Array.getLength(names);
        if (n != Array.getLength(types)) {
            setError("Unequal number of array elements (defineColumns)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineColumn(names[i], null, null, null, null, types[i], 0) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates new columns with the specified settings.
     * 
     * @param names should be an array with valid names as defined by
     *              SDDSUtil.isValidName
     * @param types valid values or the elements are:
     *              <ul>
     *              <li><code>"longdouble"</code></li>
     *              <li><code>"double"</code></li>
     *              <li><code>"float"</code></li>
     *              <li><code>"long64"</code></li>
     *              <li><code>"ulong64"</code></li>
     *              <li><code>"long"</code></li>
     *              <li><code>"ulong"</code></li>
     *              <li><code>"short"</code></li>
     *              <li><code>"ushort"</code></li>
     *              <li><code>"string"</code></li>
     *              <li><code>"character"</code></li>
     *              </ul>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean defineColumns(String[] names, String[] types) {
        int n, i;
        n = Array.getLength(names);
        if (n != Array.getLength(types)) {
            setError("Unequal number of array elements (defineColumns)");
            return false;
        }
        for (i = 0; i < n; i++) {
            if (defineColumn(names[i], null, null, null, null, SDDSUtil.identifyType(types[i]), 0) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new enum column. Its values are signed 32-bit codes with labels from a
     * required dictionary.
     *
     * @param name          should be a valid name as defined by
     *                      SDDSUtil.isValidName
     * @param symbol        may be <code>null</code>
     * @param units         may be <code>null</code>
     * @param description   may be <code>null</code>
     * @param format_string must be <code>null</code> or blank
     * @param enum_values   logical mapping, such as
     *                      <code>0:OFF,10:ON,-1:FAULT</code>
     * @param field_length  must be <code>0</code>
     * @return index of the column<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineEnumColumn(String name, String symbol, String units, String description, String format_string,
            String enum_values, int field_length) {
        if (layoutWritten || (field_length != 0) || (SDDSNamelist.blankToNull(format_string) != null)) {
            setError("Enum definitions require a fresh layout, zero field_length and no format_string (defineEnumColumn)");
            return -1;
        }
        SDDSEnumDefinition definition = parseMapping(enum_values, "defineEnumColumn");
        if (definition == null)
            return -1;
        definingEnum = true;
        int index = defineColumn(name, symbol, units, description, null, SDDS_ENUM, 0);
        definingEnum = false;
        if (index != -1)
            columnObject[index].setEnumDefinition(definition);
        return index;
    }

    /**
     * Creates a new enum column.
     *
     * @param name        should be a valid name as defined by SDDSUtil.isValidName
     * @param enum_values logical mapping, such as <code>0:OFF,10:ON,-1:FAULT</code>
     * @return index of the column<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineEnumColumn(String name, String enum_values) {
        return defineEnumColumn(name, null, null, null, null, enum_values, 0);
    }

    /**
     * Creates a new enum parameter.
     *
     * @param name          should be a valid name as defined by
     *                      SDDSUtil.isValidName
     * @param symbol        may be <code>null</code>
     * @param units         may be <code>null</code>
     * @param description   may be <code>null</code>
     * @param format_string must be <code>null</code> or blank
     * @param enum_values   logical mapping, such as
     *                      <code>0:OFF,10:ON,-1:FAULT</code>
     * @param fixed_value   <code>null</code>, or a complete decimal code from the
     *                      mapping
     * @return index of the parameter<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineEnumParameter(String name, String symbol, String units, String description, String format_string,
            String enum_values, String fixed_value) {
        if (layoutWritten || (SDDSNamelist.blankToNull(format_string) != null)) {
            setError("Enum parameter definitions require a fresh layout and no format_string (defineEnumParameter)");
            return -1;
        }
        SDDSEnumDefinition definition = parseMapping(enum_values, "defineEnumParameter");
        if (definition == null)
            return -1;
        if (fixed_value != null) {
            Integer code = SDDSEnumDefinition.parseCode(fixed_value);
            if ((code == null) || !definition.hasCode(code.intValue())) {
                setError("Invalid fixed enum parameter code (defineEnumParameter)");
                return -1;
            }
        }
        definingEnum = true;
        int index = defineParameter(name, symbol, units, description, null, SDDS_ENUM, fixed_value);
        definingEnum = false;
        if (index != -1)
            parameterObject[index].setEnumDefinition(definition);
        return index;
    }

    /**
     * Creates a new enum parameter.
     *
     * @param name        should be a valid name as defined by SDDSUtil.isValidName
     * @param enum_values logical mapping, such as <code>0:OFF,10:ON,-1:FAULT</code>
     * @return index of the parameter<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineEnumParameter(String name, String enum_values) {
        return defineEnumParameter(name, null, null, null, null, enum_values, null);
    }

    /**
     * Creates a new enum array.
     *
     * @param name          should be a valid name as defined by
     *                      SDDSUtil.isValidName
     * @param symbol        may be <code>null</code>
     * @param units         may be <code>null</code>
     * @param description   may be <code>null</code>
     * @param format_string must be <code>null</code> or blank
     * @param group_name    may be <code>null</code>
     * @param enum_values   logical mapping, such as
     *                      <code>0:OFF,10:ON,-1:FAULT</code>
     * @param field_length  must be <code>0</code>
     * @param dimensions    number of dimensions
     * @return index of the array<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineEnumArray(String name, String symbol, String units, String description, String format_string,
            String group_name, String enum_values, int field_length, int dimensions) {
        if (layoutWritten || (field_length != 0) || (SDDSNamelist.blankToNull(format_string) != null)) {
            setError("Enum array definitions require a fresh layout, zero field_length and no format_string (defineEnumArray)");
            return -1;
        }
        SDDSEnumDefinition definition = parseMapping(enum_values, "defineEnumArray");
        if (definition == null)
            return -1;
        definingEnum = true;
        int index = defineArray(name, symbol, units, description, null, group_name, SDDS_ENUM, 0, dimensions);
        definingEnum = false;
        if (index != -1)
            arrayObject[index].setEnumDefinition(definition);
        return index;
    }

    /**
     * Creates a new enum array.
     *
     * @param name        should be a valid name as defined by SDDSUtil.isValidName
     * @param enum_values logical mapping, such as <code>0:OFF,10:ON,-1:FAULT</code>
     * @param dimensions  number of dimensions
     * @return index of the array<br>
     *         <code>-1</code> if an error occurs
     */
    public int defineEnumArray(String name, String enum_values, int dimensions) {
        return defineEnumArray(name, null, null, null, null, null, enum_values, 0, dimensions);
    }

    private SDDSEnumDefinition parseMapping(String enum_values, String caller) {
        try {
            return new SDDSEnumDefinition(enum_values);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (" + caller + ")");
            return null;
        }
    }

    /** Returns the canonical mapping of a column, or <code>null</code> if it is not an enum. */
    public String getColumnEnumValues(int index) {
        SDDSEnumDefinition definition = getColumnEnumDefinition(index);
        return definition == null ? null : definition.getValues();
    }

    /** Returns the canonical mapping of a parameter, or <code>null</code> if it is not an enum. */
    public String getParameterEnumValues(int index) {
        SDDSEnumDefinition definition = getParameterEnumDefinition(index);
        return definition == null ? null : definition.getValues();
    }

    /** Returns the canonical mapping of an array, or <code>null</code> if it is not an enum. */
    public String getArrayEnumValues(int index) {
        SDDSEnumDefinition definition = getArrayEnumDefinition(index);
        return definition == null ? null : definition.getValues();
    }

    /** Returns the dictionary of a column, or <code>null</code> if it is not an enum. */
    public SDDSEnumDefinition getColumnEnumDefinition(int index) {
        return ((index < 0) || (index >= columnCount)) ? null : columnObject[index].getEnumDefinition();
    }

    /** Returns the dictionary of a parameter, or <code>null</code> if it is not an enum. */
    public SDDSEnumDefinition getParameterEnumDefinition(int index) {
        return ((index < 0) || (index >= parameterCount)) ? null : parameterObject[index].getEnumDefinition();
    }

    /** Returns the dictionary of an array, or <code>null</code> if it is not an enum. */
    public SDDSEnumDefinition getArrayEnumDefinition(int index) {
        return ((index < 0) || (index >= arrayCount)) ? null : arrayObject[index].getEnumDefinition();
    }

    /**
     * Returns the codes of an enum column on a page.
     *
     * @param index index of the column
     * @param page  page of the data
     * @return the codes, or <code>null</code> if it is not an enum or the page has no
     *         data
     */
    public int[] getColumnEnumCodes(int index, int page) {
        return ((index < 0) || (index >= columnCount)) ? null : columnObject[index].getEnumCodes(page);
    }

    /**
     * Returns the labels of an enum column on a page.
     *
     * @param index index of the column
     * @param page  page of the data
     * @return the labels, or <code>null</code> if it is not an enum or the page has no
     *         data
     */
    public String[] getColumnEnumLabels(int index, int page) {
        return ((index < 0) || (index >= columnCount)) ? null : columnObject[index].getEnumLabels(page);
    }

    /**
     * Returns the codes of an enum array on a page.
     *
     * @param index index of the array
     * @param page  page of the data
     * @return the codes, or <code>null</code> if it is not an enum or the page has no
     *         data
     */
    public int[] getArrayEnumCodes(int index, int page) {
        return ((index < 0) || (index >= arrayCount)) ? null : arrayObject[index].getEnumCodes(page);
    }

    /**
     * Returns the labels of an enum array on a page.
     *
     * @param index index of the array
     * @param page  page of the data
     * @return the labels, or <code>null</code> if it is not an enum or the page has no
     *         data
     */
    public String[] getArrayEnumLabels(int index, int page) {
        return ((index < 0) || (index >= arrayCount)) ? null : arrayObject[index].getEnumLabels(page);
    }

    /**
     * Returns the code of an enum parameter on a page.
     *
     * @param index index of the parameter
     * @param page  page of the data
     * @return the code, or <code>null</code> if it is not an enum or has no value
     */
    public Integer getParameterEnumCode(int index, int page) {
        return ((index < 0) || (index >= parameterCount)) ? null : parameterObject[index].getEnumCode(page);
    }

    /**
     * Returns the label of an enum parameter on a page.
     *
     * @param index index of the parameter
     * @param page  page of the data
     * @return the label, or <code>null</code> if it is not an enum or has no mapped
     *         value
     */
    public String getParameterEnumLabel(int index, int page) {
        return ((index < 0) || (index >= parameterCount)) ? null : parameterObject[index].getEnumLabel(page);
    }

    /**
     * Set enum column values by label.
     *
     * @param index    index of the column
     * @param labels   labels from the dictionary
     * @param page     page to set the values
     * @param startRow row to start setting values
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if a label is not in the dictionary
     */
    public boolean setColumnLabels(int index, String[] labels, int page, int startRow) {
        if ((index < 0) || (index >= columnCount)) {
            setError("column index " + index + " does not exist (setColumnLabels)");
            return false;
        }
        try {
            columnObject[index].setLabels(labels, page, startRow);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumnLabels)");
            return false;
        }
        return true;
    }

    /**
     * Set enum array values by label.
     *
     * @param index  index of the array
     * @param labels labels from the dictionary
     * @param page   page to set the values
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if a label is not in the dictionary
     */
    public boolean setArrayLabels(int index, String[] labels, int page) {
        if ((index < 0) || (index >= arrayCount)) {
            setError("array index " + index + " does not exist (setArrayLabels)");
            return false;
        }
        try {
            arrayObject[index].setLabels(labels, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArrayLabels)");
            return false;
        }
        return true;
    }

    /**
     * Set an enum parameter value by label.
     *
     * @param index index of the parameter
     * @param label label from the dictionary
     * @param page  page to set the value
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if the label is not in the dictionary
     */
    public boolean setParameterLabel(int index, String label, int page) {
        if ((index < 0) || (index >= parameterCount)) {
            setError("parameter index " + index + " does not exist (setParameterLabel)");
            return false;
        }
        try {
            parameterObject[index].setLabel(label, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameterLabel)");
            return false;
        }
        return true;
    }

    /**
     * Set enum column values where some may not have been chosen yet, as an interactive
     * program needs to. A chosen code is checked. A <code>null</code> is stored as code
     * 0, so the file cannot be written until it has a mapped code.
     *
     * @param index    index of the column
     * @param codes    codes, with <code>null</code> for a value not chosen yet
     * @param page     page to set the values
     * @param startRow row to start setting values
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if a code is not mapped
     */
    public boolean setColumnEnumCells(int index, Integer[] codes, int page, int startRow) {
        if ((index < 0) || (index >= columnCount)) {
            setError("column index " + index + " does not exist (setColumnEnumCells)");
            return false;
        }
        try {
            columnObject[index].setEnumCells(codes, page, startRow);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumnEnumCells)");
            return false;
        }
        return true;
    }

    /**
     * Set enum array values where some may not have been chosen yet.
     *
     * @param index index of the array
     * @param codes codes, with <code>null</code> for a value not chosen yet
     * @param page  page to set the values
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if a code is not mapped
     * @see #setColumnEnumCells
     */
    public boolean setArrayEnumCells(int index, Integer[] codes, int page) {
        if ((index < 0) || (index >= arrayCount)) {
            setError("array index " + index + " does not exist (setArrayEnumCells)");
            return false;
        }
        try {
            arrayObject[index].setEnumCells(codes, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArrayEnumCells)");
            return false;
        }
        return true;
    }

    /**
     * Set an enum parameter value that may not have been chosen yet.
     *
     * @param index index of the parameter
     * @param code  the code, or <code>null</code> for a value not chosen yet
     * @param page  page to set the value
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if the code is not mapped
     * @see #setColumnEnumCells
     */
    public boolean setParameterEnumCell(int index, Integer code, int page) {
        if ((index < 0) || (index >= parameterCount)) {
            setError("parameter index " + index + " does not exist (setParameterEnumCell)");
            return false;
        }
        try {
            parameterObject[index].setEnumCell(code, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameterEnumCell)");
            return false;
        }
        return true;
    }

    /**
     * Extend a column page to a number of rows holding zero values, with no dictionary
     * check. This is how a program represents enum values that have not been chosen
     * yet. The file cannot be written until they hold mapped codes.
     *
     * @param index index of the column
     * @param page  page of the data
     * @param rows  number of rows the page should have
     */
    public void setColumnBlankRows(int index, int page, int rows) {
        if ((index >= 0) && (index < columnCount))
            columnObject[index].setBlankRows(page, rows);
    }

    /**
     * Explicitly convert a column between enum, long and string. Enum to enum remaps by
     * exact label, enum to long gives codes, enum to string gives labels, long to enum
     * checks codes and string to enum looks up labels. Every value is checked before
     * any is changed. This is the way to replace a dictionary. It is not available
     * while a layout that has been written is still open.
     *
     * @param index       index of the column
     * @param targetType  <code>SDDSUtil.SDDS_ENUM</code>,
     *                    <code>SDDSUtil.SDDS_LONG</code> or
     *                    <code>SDDSUtil.SDDS_STRING</code>
     * @param enum_values logical mapping for an enum target, otherwise
     *                    <code>null</code>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean convertColumn(int index, int targetType, String enum_values) {
        if ((index < 0) || (index >= columnCount) || !conversionAllowed("convertColumn"))
            return false;
        try {
            columnObject[index].convert(targetType,
                    targetType == SDDS_ENUM ? new SDDSEnumDefinition(enum_values) : null);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (convertColumn)");
            return false;
        }
        return true;
    }

    /**
     * Explicitly convert a parameter between enum, long and string.
     *
     * @param index       index of the parameter
     * @param targetType  <code>SDDSUtil.SDDS_ENUM</code>,
     *                    <code>SDDSUtil.SDDS_LONG</code> or
     *                    <code>SDDSUtil.SDDS_STRING</code>
     * @param enum_values logical mapping for an enum target, otherwise
     *                    <code>null</code>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     * @see #convertColumn
     */
    public boolean convertParameter(int index, int targetType, String enum_values) {
        if ((index < 0) || (index >= parameterCount) || !conversionAllowed("convertParameter"))
            return false;
        try {
            parameterObject[index].convert(targetType,
                    targetType == SDDS_ENUM ? new SDDSEnumDefinition(enum_values) : null);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (convertParameter)");
            return false;
        }
        return true;
    }

    /**
     * Explicitly convert an array between enum, long and string.
     *
     * @param index       index of the array
     * @param targetType  <code>SDDSUtil.SDDS_ENUM</code>,
     *                    <code>SDDSUtil.SDDS_LONG</code> or
     *                    <code>SDDSUtil.SDDS_STRING</code>
     * @param enum_values logical mapping for an enum target, otherwise
     *                    <code>null</code>
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     * @see #convertColumn
     */
    public boolean convertArray(int index, int targetType, String enum_values) {
        if ((index < 0) || (index >= arrayCount) || !conversionAllowed("convertArray"))
            return false;
        try {
            arrayObject[index].convert(targetType,
                    targetType == SDDS_ENUM ? new SDDSEnumDefinition(enum_values) : null);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (convertArray)");
            return false;
        }
        return true;
    }

    private boolean conversionAllowed(String caller) {
        if (layoutWritten)
            setError("Enum conversion requires a layout that has not been written (" + caller + ")");
        return !layoutWritten;
    }

    /**
     * Check for dictionaries that would be lost by exporting to a format with no native
     * enum representation. An exporter should call this first and refuse to continue
     * if it fails.
     *
     * @return <code>true</code> if the layout has no enums<br>
     *         <code>false</code> if it does
     */
    public boolean checkEnumExport() {
        for (int i = 0; i < columnCount; i++)
            if (columnObject[i].getType() == SDDS_ENUM)
                return enumExportError();
        for (int i = 0; i < parameterCount; i++)
            if (parameterObject[i].getType() == SDDS_ENUM)
                return enumExportError();
        for (int i = 0; i < arrayCount; i++)
            if (arrayObject[i].getType() == SDDS_ENUM)
                return enumExportError();
        return true;
    }

    private boolean enumExportError() {
        setError("This export format cannot preserve enums; convert explicitly to long codes or string labels first (checkEnumExport)");
        return false;
    }

    /** Check that every enum in the layout has a valid, unchanged definition. */
    private boolean validateEnumLayout() {
        for (int i = 0; i < columnCount; i++) {
            if ((columnObject[i].getType() == SDDS_ENUM) && (columnObject[i].getEnumDefinition() == null)) {
                setError("Incomplete or invalid enum definition for column " + columnObject[i].getName());
                return false;
            }
        }
        for (int i = 0; i < parameterCount; i++) {
            if ((parameterObject[i].getType() == SDDS_ENUM) && (parameterObject[i].getEnumDefinition() == null)) {
                setError("Incomplete or invalid enum definition for parameter " + parameterObject[i].getName());
                return false;
            }
        }
        for (int i = 0; i < arrayCount; i++) {
            if ((arrayObject[i].getType() == SDDS_ENUM) && (arrayObject[i].getEnumDefinition() == null)) {
                setError("Incomplete or invalid enum definition for array " + arrayObject[i].getName());
                return false;
            }
        }
        return true;
    }

    /**
     * Check that a whole page can be written before any of it is. An enum value that was
     * never set holds code 0, which is written if the dictionary maps it and otherwise
     * stops the write, since nothing else may be substituted.
     */
    private boolean preparePage(int page) {
        int endRow = 0;
        for (int i = 0; i < columnCount; i++)
            endRow = Math.max(endRow, columnObject[i].getRowCount(page));
        for (int i = 0; i < parameterCount; i++) {
            SDDSParameter parameter = parameterObject[i];
            String name = parameter.getName();
            if (!parameter.isValidFixedValue()) {
                setError("Invalid fixed value for parameter " + name + " (writePage)");
                return false;
            }
            if (!parameter.fixed && (page > parameter.getPageCount())) {
                if (parameter.getType() != SDDS_ENUM) {
                    setError("invalid page number (" + page + ") for parameter " + name + " (writePage)");
                    return false;
                }
                SDDSEnumDefinition definition = parameter.getEnumDefinition();
                if ((definition != null) && definition.hasCode(0))
                    parameter.makeBlankPages(page);
            }
            if (!parameter.isValidEnumPage(page) || ((parameter.getType() == SDDS_ENUM) && !parameter.fixed
                    && (page > parameter.getPageCount()))) {
                setError("Invalid enum parameter " + name + ": page " + page + " needs a state");
                return false;
            }
        }
        for (int i = 0; i < arrayCount; i++) {
            SDDSArray array = arrayObject[i];
            if ((page > array.getPageCount()) && (array.getType() == SDDS_ENUM))
                array.makeBlankPages(page);
            if (!array.dimensionsMatch(page)) {
                setError("unable to write array data on page " + page + " for array " + array.getName()
                        + ": its dimensions do not match its values (writePage)");
                return false;
            }
            int element = array.firstInvalidElement(page);
            if (element > 0) {
                setError("Invalid enum array " + array.getName() + ": page " + page + " element " + element
                        + " needs a state");
                return false;
            }
        }
        for (int i = 0; i < columnCount; i++) {
            SDDSColumn column = columnObject[i];
            int rows = Math.max(column.getRowCount(page), 0);
            if (rows < endRow) {
                if (column.getType() != SDDS_ENUM) {
                    setError("invalid column elemant at page (" + page + ") row (" + (rows + 1) + ") for column "
                            + column.getName() + " (writePage)");
                    return false;
                }
                column.setBlankRows(page, endRow);
            }
            int row = column.firstInvalidRow(page);
            if (row > 0) {
                setError("Invalid enum column " + column.getName() + ": page " + page + " row " + row
                        + " needs a state");
                return false;
            }
        }
        return true;
    }

    /**
     * Ask for at least this protocol version when writing. Without this the writer uses
     * the lowest version that can hold the layout, so that the file stays readable by
     * older programs, and SDDS6 whenever the layout has an enum.
     *
     * @param version protocol version from <code>1</code> to
     *                <code>SDDSUtil.SDDS_VERSION</code>
     * @return <code>true</code> if the version is valid
     */
    public boolean setMinimumVersion(int version) {
        if ((version < 1) || (version > SDDSUtil.SDDS_VERSION)) {
            setError("Invalid SDDS protocol version (setMinimumVersion)");
            return false;
        }
        minimumVersion = version;
        return true;
    }

    /** Returns the protocol version the current layout would be written with. */
    public int selectVersion() {
        boolean unsigned = false, longDouble = false, wide = false, categorical = false;
        for (int pass = 0; pass < 3; pass++) {
            int count = pass == 0 ? parameterCount : pass == 1 ? arrayCount : columnCount;
            for (int i = 0; i < count; i++) {
                int type = pass == 0 ? parameterObject[i].getType()
                        : pass == 1 ? arrayObject[i].getType() : columnObject[i].getType();
                unsigned |= (type == SDDS_ULONG) || (type == SDDS_USHORT);
                longDouble |= type == SDDS_LONGDOUBLE;
                wide |= (type == SDDS_ULONG64) || (type == SDDS_LONG64);
                categorical |= type == SDDS_ENUM;
            }
        }
        int version = 1;
        if (unsigned)
            version = 2;
        if (columnMajorOrder && !asciiFile)
            version = 3;
        if (longDouble)
            version = 4;
        if (wide)
            version = 5;
        if (categorical)
            version = 6;
        return Math.max(version, minimumVersion);
    }

    /**
     * Return the index of the specified parameter.
     * 
     * @param name name of an existing parameter
     * @return index of parameter<br>
     *         <code>-1</code> if the parameter does not exist
     */
    public int getParameterIndex(String name) {
        int i;
        for (i = 0; i < parameterCount; i++)
            if (name.compareTo(parameterObject[i].getName()) == 0)
                return i;
        setError("parameter " + name + " does not exist (getParameterIndex)");
        return -1;
    }

    /**
     * Return the indexes of the specified parameter.
     * 
     * @param names names of an existing parameters
     * @return indexes of parameters<br>
     *         elements may contain <code>-1</code> if the parameter does not exist
     */
    public int[] getParameterIndexes(String[] names) {
        int[] n;
        int i, j, k;
        j = Array.getLength(names);
        n = new int[j];
        for (k = 0; k < j; k++) {
            n[k] = -1;
            for (i = 0; i < parameterCount; i++)
                if (names[k].compareTo(parameterObject[i].getName()) == 0)
                    n[k] = i;
        }
        return n;
    }

    /**
     * Return the index of the specified array.
     * 
     * @param name name of an existing array
     * @return index of array<br>
     *         <code>-1</code> if the array does not exist
     */
    public int getArrayIndex(String name) {
        int i;
        for (i = 0; i < arrayCount; i++)
            if (name.compareTo(arrayObject[i].getName()) == 0)
                return i;
        setError("array " + name + " does not exist (getArrayIndex)");
        return -1;
    }

    /**
     * Return the indexes of the specified arrays.
     * 
     * @param names names of an existing arrays
     * @return indexes of arrays<br>
     *         elements may contain <code>-1</code> if the array does not exist
     */
    public int[] getArrayIndexes(String[] names) {
        int[] n;
        int i, j, k;
        j = Array.getLength(names);
        n = new int[j];
        for (k = 0; k < j; k++) {
            n[k] = -1;
            for (i = 0; i < arrayCount; i++)
                if (names[k].compareTo(arrayObject[i].getName()) == 0)
                    n[k] = i;
        }
        return n;
    }

    /**
     * Return the index of the specified column.
     * 
     * @param name name of an existing column
     * @return index of column<br>
     *         <code>-1</code> if the column does not exist
     */
    public int getColumnIndex(String name) {
        int i;
        for (i = 0; i < columnCount; i++)
            if (name.compareTo(columnObject[i].getName()) == 0)
                return i;
        setError("column " + name + " does not exist (getColumnIndex)");
        return -1;
    }

    /**
     * Return the indexes of the specified columns.
     * 
     * @param names names of an existing columns
     * @return indexes of columns<br>
     *         elements may contain <code>-1</code> if the column does not exist
     */
    public int[] getColumnIndexes(String[] names) {
        int[] n;
        int i, j, k;
        j = Array.getLength(names);
        n = new int[j];
        for (k = 0; k < j; k++) {
            n[k] = -1;
            for (i = 0; i < columnCount; i++)
                if (names[k].compareTo(columnObject[i].getName()) == 0)
                    n[k] = i;
        }
        return n;
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, short[] values, int startPage) {
        return setParameter(getParameterIndex(name), SDDSUtil.castArrayAsLong(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, short[] values, int startPage) {
        return setParameter(parameterIndex, SDDSUtil.castArrayAsLong(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, int[] values, int startPage) {
        return setParameter(getParameterIndex(name), SDDSUtil.castArrayAsLong(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, int[] values, int startPage) {
        return setParameter(parameterIndex, SDDSUtil.castArrayAsLong(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, long[] values, int startPage) {
        return setParameter(getParameterIndex(name), values, startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, long[] values, int startPage) {
        int i;
        if ((parameterIndex < 0) || (parameterIndex >= parameterCount)) {
            setError("parameter index " + parameterIndex + " does not exist (setParameter)");
            return false;
        }
        try {
            for (i = 0; i < Array.getLength(values); i++)
                parameterObject[parameterIndex].setValue(values[i], i + startPage);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameter)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, float[] values, int startPage) {
        return setParameter(getParameterIndex(name), SDDSUtil.castArrayAsDouble(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, float[] values, int startPage) {
        return setParameter(parameterIndex, SDDSUtil.castArrayAsDouble(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, double[] values, int startPage) {
        return setParameter(getParameterIndex(name), values, startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, double[] values, int startPage) {
        int i;
        if ((parameterIndex < 0) || (parameterIndex >= parameterCount)) {
            setError("parameter index " + parameterIndex + " does not exist (setParameter)");
            return false;
        }
        try {
            for (i = 0; i < Array.getLength(values); i++)
                parameterObject[parameterIndex].setValue(values[i], i + startPage);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameter)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, String[] values, int startPage) {
        return setParameter(getParameterIndex(name), values, startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, String[] values, int startPage) {
        int i;
        if ((parameterIndex < 0) || (parameterIndex >= parameterCount)) {
            setError("parameter index " + parameterIndex + " does not exist (setParameter)");
            return false;
        }
        try {
            for (i = 0; i < Array.getLength(values); i++)
                parameterObject[parameterIndex].setValue(values[i], i + startPage);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setParameter)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param name      name of an existing parameter
     * @param values    array of values
     * @param startPage page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, char[] values, int startPage) {
        return setParameter(getParameterIndex(name), SDDSUtil.castArrayAsString(values), startPage);
    }

    /**
     * Set the values of the parameter starting from the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param values         array of values
     * @param startPage      page to start setting values
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, char[] values, int startPage) {
        return setParameter(parameterIndex, SDDSUtil.castArrayAsString(values), startPage);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, short value, int page) {
        return setParameter(getParameterIndex(name), (long) value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, short value, int page) {
        return setParameter(parameterIndex, (long) value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, int value, int page) {
        return setParameter(getParameterIndex(name), (long) value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, int value, int page) {
        return setParameter(parameterIndex, (long) value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, long value, int page) {
        return setParameter(getParameterIndex(name), value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, long value, int page) {
        long[] v = new long[1];
        v[0] = value;
        return setParameter(parameterIndex, v, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, float value, int page) {
        return setParameter(getParameterIndex(name), (double) value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, float value, int page) {
        return setParameter(parameterIndex, (double) value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, double value, int page) {
        return setParameter(getParameterIndex(name), value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, double value, int page) {
        double[] v = new double[1];
        v[0] = value;
        return setParameter(parameterIndex, v, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, String value, int page) {
        return setParameter(getParameterIndex(name), value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, String value, int page) {
        String[] v = new String[1];
        v[0] = value;
        return setParameter(parameterIndex, v, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param name  name of an existing parameter
     * @param value value to be set
     * @param page  page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(String name, char value, int page) {
        return setParameter(getParameterIndex(name), value, page);
    }

    /**
     * Set the value of the parameter at the specified page.
     * 
     * @param parameterIndex index of an existing parameter
     * @param value          value to be set
     * @param page           page to set value
     * @return <code>true</code> if successful
     *         <code>false</code> if index does not exist
     */
    public boolean setParameter(int parameterIndex, char value, int page) {
        char[] v = new char[1];
        v[0] = value;
        return setParameter(parameterIndex, v, page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, short[] values, int page) {
        return setParameters(getParameterIndexes(names), SDDSUtil.castArrayAsLong(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, short[] values, int page) {
        return setParameters(parameterIndexes, SDDSUtil.castArrayAsLong(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, int[] values, int page) {
        return setParameters(getParameterIndexes(names), SDDSUtil.castArrayAsLong(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, int[] values, int page) {
        return setParameters(parameterIndexes, SDDSUtil.castArrayAsLong(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, long[] values, int page) {
        return setParameters(getParameterIndexes(names), values, page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, long[] values, int page) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], values[i], page))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, float[] values, int page) {
        return setParameters(getParameterIndexes(names), SDDSUtil.castArrayAsDouble(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, float[] values, int page) {
        return setParameters(parameterIndexes, SDDSUtil.castArrayAsDouble(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, double[] values, int page) {
        return setParameters(getParameterIndexes(names), values, page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, double[] values, int page) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], values[i], page))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, String[] values, int page) {
        return setParameters(getParameterIndexes(names), values, page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, String[] values, int page) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], values[i], page))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param names  names of an existing parameters
     * @param values array of values to be set
     * @param page   page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, char[] values, int page) {
        return setParameters(getParameterIndexes(names), SDDSUtil.castArrayAsString(values), page);
    }

    /**
     * Set the values of the parameters at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           array of values to be set
     * @param page             page to set values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, char[] values, int page) {
        return setParameters(parameterIndexes, SDDSUtil.castArrayAsString(values), page);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, short[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, short[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], SDDSUtil.castArrayAsLong(values[i]), startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, int[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, int[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], SDDSUtil.castArrayAsLong(values[i]), startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, long[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, long[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], values[i], startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, float[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, float[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], SDDSUtil.castArrayAsDouble(values[i]), startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, double[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, double[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], values[i], startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, String[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, String[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], values[i], startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param names     names of an existing parameters
     * @param values    two dimensional array of values to be set
     * @param startPage page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(String[] names, char[][] values, int startPage) {
        return setParameters(getParameterIndexes(names), values, startPage);
    }

    /**
     * Set the values of the parameters starting at the specified page.
     * 
     * @param parameterIndexes indexes of an existing parameters
     * @param values           two dimensional array of values to be set
     * @param startPage        page to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setParameters(int[] parameterIndexes, char[][] values, int startPage) {
        int n, i;
        n = Array.getLength(parameterIndexes);
        if (n != Array.getLength(values)) {
            setError("Unequal number of array elements (setParameters)");
            return false;
        }
        for (i = 0; i < n; i++)
            if (!setParameter(parameterIndexes[i], SDDSUtil.castArrayAsString(values[i]), startPage))
                return false;
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, short[] values, int page) {
        return setArray(getArrayIndex(name), SDDSUtil.castArrayAsLong(values), page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, short[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsLong(values), page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, int[] values, int page) {
        return setArray(getArrayIndex(name), SDDSUtil.castArrayAsLong(values), page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, int[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsLong(values), page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, long[] values, int page) {
        return setArray(getArrayIndex(name), values, page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, long[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(values, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, float[] values, int page) {
        return setArray(getArrayIndex(name), SDDSUtil.castArrayAsDouble(values), page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, float[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsDouble(values), page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, double[] values, int page) {
        return setArray(getArrayIndex(name), values, page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, double[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(values, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, String[] values, int page) {
        return setArray(getArrayIndex(name), values, page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, String[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(values, page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, char[] values, int page) {
        return setArray(getArrayIndex(name), values, page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, char[] values, int page) {
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        try {
            arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsString(values), page);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setArray)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param name   name of an existing array
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(String name, Object[] values, int page) {
        return setArray(getArrayIndex(name), values, page);
    }

    /**
     * Set the values of the array at the specified page.
     * 
     * @param arrayIndex index of an existing array
     * @param values     array of values to be set
     * @param page       page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setArray(int arrayIndex, Object[] values, int page) {
        int type;
        if ((arrayIndex < 0) || (arrayIndex >= arrayCount)) {
            setError("array index " + arrayIndex + " does not exist (setArray)");
            return false;
        }
        type = getArrayType(arrayIndex);
        switch (type) {
            case SDDS_SHORT:
            case SDDS_USHORT:
            case SDDS_LONG:
            case SDDS_ULONG:
            case SDDS_LONG64:
            case SDDS_ULONG64:
                arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsLong(values, type), page);
                return true;
            case SDDS_FLOAT:
            case SDDS_DOUBLE:
                arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsDouble(values, type), page);
                return true;
            case SDDS_LONGDOUBLE:
                arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsLongDouble(values), page);
                return true;
            case SDDS_ENUM:
                try {
                    arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsEnumCodes(values), page);
                } catch (IllegalArgumentException e) {
                    setError(e.getMessage() + " (setArray)");
                    return false;
                }
                return true;
            case SDDS_STRING:
            case SDDS_CHARACTER:
                arrayObject[arrayIndex].setValues(SDDSUtil.castArrayAsString(values, type), page);
                return true;
        }
        return false;
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, short[] values, int page) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsLong(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, short[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsLong(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, short[] values, int page) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsLong(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, short[] values, int page, int startRow) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsLong(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, int[] values, int page) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsLong(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, int[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsLong(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, int[] values, int page) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsLong(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, int[] values, int page, int startRow) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsLong(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, long[] values, int page) {
        return setColumn(getColumnIndex(name), values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, long[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), values, page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, long[] values, int page) {
        return setColumn(columnIndex, values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, long[] values, int page, int startRow) {
        if ((columnIndex < 0) || (columnIndex >= columnCount)) {
            setError("column index " + columnIndex + " does not exist (setColumn)");
            return false;
        }
        try {
            columnObject[columnIndex].setValues(values, page, startRow);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumn)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, float[] values, int page) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsDouble(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, float[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsDouble(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, float[] values, int page) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsDouble(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, float[] values, int page, int startRow) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsDouble(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, double[] values, int page) {
        return setColumn(getColumnIndex(name), values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, double[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), values, page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, double[] values, int page) {
        return setColumn(columnIndex, values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, double[] values, int page, int startRow) {
        if ((columnIndex < 0) || (columnIndex >= columnCount)) {
            setError("column index " + columnIndex + " does not exist (setColumn)");
            return false;
        }
        try {
            columnObject[columnIndex].setValues(values, page, startRow);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumn)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, String[] values, int page) {
        return setColumn(getColumnIndex(name), values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, String[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), values, page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, String[] values, int page) {
        return setColumn(columnIndex, values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, String[] values, int page, int startRow) {
        if ((columnIndex < 0) || (columnIndex >= columnCount)) {
            setError("column index " + columnIndex + " does not exist (setColumn)");
            return false;
        }
        try {
            columnObject[columnIndex].setValues(values, page, startRow);
        } catch (IllegalArgumentException e) {
            setError(e.getMessage() + " (setColumn)");
            return false;
        }
        return true;
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, char[] values, int page) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsString(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, char[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), SDDSUtil.castArrayAsString(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, char[] values, int page) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsString(values), page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, char[] values, int page, int startRow) {
        return setColumn(columnIndex, SDDSUtil.castArrayAsString(values), page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param name   name of an existing column
     * @param values array of values to be set
     * @param page   page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, Object[] values, int page) {
        return setColumn(getColumnIndex(name), values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param name     name of an existing column
     * @param values   array of values to be set
     * @param page     page to set the values
     * @param startRow row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(String name, Object[] values, int page, int startRow) {
        return setColumn(getColumnIndex(name), values, page, startRow);
    }

    /**
     * Set the values of the column at the specified page.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, Object[] values, int page) {
        return setColumn(columnIndex, values, page, 1);
    }

    /**
     * Set the values of the column at the specified page starting at the specified
     * row.
     * 
     * @param columnIndex index of an existing column
     * @param values      array of values to be set
     * @param page        page to set the values
     * @param startRow    row to start setting the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumn(int columnIndex, Object[] values, int page, int startRow) {
        int type;
        type = getColumnType(columnIndex);
        switch (type) {
            case SDDS_SHORT:
            case SDDS_USHORT:
            case SDDS_LONG:
            case SDDS_ULONG:
            case SDDS_LONG64:
            case SDDS_ULONG64:
                return setColumn(columnIndex, SDDSUtil.castArrayAsLong(values, type), page, startRow);
            case SDDS_FLOAT:
            case SDDS_DOUBLE:
                return setColumn(columnIndex, SDDSUtil.castArrayAsDouble(values, type), page, startRow);
            case SDDS_LONGDOUBLE:
                columnObject[columnIndex].setValues(SDDSUtil.castArrayAsLongDouble(values), page, startRow);
                return true;
            case SDDS_ENUM:
                try {
                    return setColumn(columnIndex, SDDSUtil.castArrayAsEnumCodes(values), page, startRow);
                } catch (IllegalArgumentException e) {
                    setError(e.getMessage() + " (setColumn)");
                    return false;
                }
            case SDDS_STRING:
            case SDDS_CHARACTER:
                return setColumn(columnIndex, SDDSUtil.castArrayAsString(values, type), page, startRow);
        }
        return false;
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, short value, int page, int row) {
        long[] v = new long[1];
        v[0] = (long) value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, short value, int page, int row) {
        long[] v = new long[1];
        v[0] = (long) value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, int value, int page, int row) {
        long[] v = new long[1];
        v[0] = (long) value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, int value, int page, int row) {
        long[] v = new long[1];
        v[0] = (long) value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, long value, int page, int row) {
        long[] v = new long[1];
        v[0] = value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, long value, int page, int row) {
        long[] v = new long[1];
        v[0] = value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, float value, int page, int row) {
        double[] v = new double[1];
        v[0] = (double) value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, float value, int page, int row) {
        double[] v = new double[1];
        v[0] = (double) value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, double value, int page, int row) {
        double[] v = new double[1];
        v[0] = value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, double value, int page, int row) {
        double[] v = new double[1];
        v[0] = value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, String value, int page, int row) {
        String[] v = new String[1];
        v[0] = value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, String value, int page, int row) {
        String[] v = new String[1];
        v[0] = value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param name  name of an existing column
     * @param value value to be set
     * @param page  page to set the values
     * @param row   row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(String name, char value, int page, int row) {
        char[] v = new char[1];
        v[0] = value;
        return setColumn(getColumnIndex(name), v, page, row);
    }

    /**
     * Set the value of the column element at the specified page and row.
     * 
     * @param columnIndex index of an existing column
     * @param value       value to be set
     * @param page        page to set the values
     * @param row         row to set the values
     * @return <code>true</code> if successful
     *         <code>false</code> if an error occurs
     */
    public boolean setColumnElement(int columnIndex, char value, int page, int row) {
        char[] v = new char[1];
        v[0] = value;
        return setColumn(columnIndex, v, page, row);
    }

    /**
     * Write the layout to a file. The protocol version is the lowest that can hold the
     * layout (see {@link #selectVersion}), which is SDDS6 whenever there is an enum.
     *
     * @return <code>true</code> if the layout was successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeLayout() {
        if (!validateEnumLayout())
            return false;
        layoutVersion = selectVersion();
        /* The setting the C writers honor, unless this program chose a byte order itself. */
        String outputEndian = System.getenv("SDDS_OUTPUT_ENDIANESS");
        if ((outputEndian != null) && !endianSet) {
            if (outputEndian.startsWith("big"))
                bigEndian = true;
            else if (outputEndian.startsWith("little"))
                bigEndian = false;
        }
        try {
            dos.writeBytes("SDDS" + layoutVersion + "\n");
            if ((layoutVersion < 3) && !asciiFile)
                dos.writeBytes(bigEndian ? "!# big-endian\n" : "!# little-endian\n");
            if (!writeDescription())
                return false;
            if (!writeParameterHeaders())
                return false;
            if (!writeArrayHeaders())
                return false;
            if (!writeColumnHeaders())
                return false;
            String mode = asciiFile ? "ascii" : "binary";
            String endian = bigEndian ? "big" : "little";
            boolean columnMajor = columnMajorOrder && !asciiFile;
            boolean rowCounts = noRowCount && asciiFile;
            if (layoutVersion == 6) {
                dos.writeBytes("&data mode=" + mode + ", lines_per_row=1, no_row_counts=" + (rowCounts ? 1 : 0)
                        + ", fixed_row_count=0, additional_header_lines=0, column_major_order="
                        + (columnMajor ? 1 : 0) + ", endian=" + endian + ",  &end\n");
            } else {
                String line = "&data mode=" + mode + ", ";
                if (rowCounts)
                    line = line + "no_row_counts=1, ";
                if ((layoutVersion >= 3) && !asciiFile) {
                    line = line + "endian=" + endian + ", ";
                    if (columnMajor)
                        line = line + "column_major_order=1, ";
                }
                dos.writeBytes(line + "&end\n");
            }
            dos.flush();
        } catch (Exception e) {
            setError("unable to write layout (writeLayout)");
            return false;
        }
        layoutWritten = true;
        return true;
    }

    /**
     * Write the description to a file.
     *
     * @return <code>true</code> if the description was successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeDescription() {
        if ((descriptionText == null) && (descriptionContents == null))
            return true;
        try {
            if (selectVersion() == 6) {
                dos.write(SDDSNamelist.finish6("&description " + SDDSNamelist.field6("text", descriptionText)
                        + SDDSNamelist.field6("contents", descriptionContents) + " &end\n"));
            } else {
                dos.writeBytes("&description " + SDDSNamelist.legacyField("text", descriptionText)
                        + SDDSNamelist.legacyField("contents", descriptionContents) + "&end\n");
            }
        } catch (Exception e) {
            setError("unable to write description (writeDescription)");
            return false;
        }
        return true;
    }

    /**
     * Write the parameter headers to a file.
     *
     * @return <code>true</code> if the headers were successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeParameterHeaders() {
        int i;
        for (i = 0; i < parameterCount; i++) {
            if (!writeParameterHeader(i))
                return false;
        }
        return true;
    }

    /**
     * Write the specified parameter header to a file.
     * 
     * @param index index of an existing parameter
     * @return <code>true</code> if the header was successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeParameterHeader(int index) {
        if ((index < 0) || (index >= parameterCount)) {
            setError("invalid parameter index (writeParameterHeader)");
            return false;
        }
        if (parameterObject[index].writeHeader(dos, selectVersion()))
            return true;
        setError("unable to write parameter header (writeParameterHeader)");
        return false;
    }

    /**
     * Write the array headers to a file.
     *
     * @return <code>true</code> if the headers were successfully written<br>
     *         <code>false</code> if an error occurred
     */
    public boolean writeArrayHeaders() {
        int i;
        for (i = 0; i < arrayCount; i++) {
            if (!writeArrayHeader(i))
                return false;
        }
        return true;
    }

    /**
     * Write the specified array header to a file.
     * 
     * @param index index of an existing array
     * @return <code>true</code> if the header was successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeArrayHeader(int index) {
        if ((index < 0) || (index >= arrayCount)) {
            setError("invalid array index (writeArrayHeader)");
            return false;
        }
        if (arrayObject[index].writeHeader(dos, selectVersion()))
            return true;
        setError("unable to write array header (writeColumnHeader)");
        return false;
    }

    /**
     * Write the column headers to a file.
     *
     * @return <code>true</code> if the headers were successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeColumnHeaders() {
        int i;
        for (i = 0; i < columnCount; i++) {
            if (!writeColumnHeader(i))
                return false;
        }
        return true;
    }

    /**
     * Write the specified column header to a file.
     * 
     * @param index index of an existing column
     * @return <code>true</code> if the header was successfully written<br>
     *         <code>false</code> if an error occured
     */
    public boolean writeColumnHeader(int index) {
        if ((index < 0) || (index >= columnCount)) {
            setError("invalid column index (writeColumnHeader)");
            return false;
        }
        if (columnObject[index].writeHeader(dos, selectVersion()))
            return true;
        setError("unable to write column header (writeColumnHeader)");
        return false;
    }

    /**
     * Write all the pages to a file.
     * 
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean writePages() {
        return writePages(1, pageCount());
    }

    /**
     * Get the number of pages in the SDDS object
     *
     * @return number of pages
     */
    public int pageCount() {
        int endPage, i, pages;
        endPage = 0;
        for (i = 0; i < parameterCount; i++) {
            pages = parameterObject[i].getPageCount();
            if (pages > endPage)
                endPage = pages;
        }
        for (i = 0; i < arrayCount; i++) {
            pages = arrayObject[i].getPageCount();
            if (pages > endPage)
                endPage = pages;
        }
        for (i = 0; i < columnCount; i++) {
            pages = columnObject[i].getPageCount();
            if (pages > endPage)
                endPage = pages;
        }
        return endPage;
    }

    /**
     * Write specified the pages to a file.
     * 
     * @param startPage first page to write
     * @param endPage   last page to write
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean writePages(int startPage, int endPage) {
        int i;
        i = startPage;
        while (i <= endPage) {
            if (!writePage(i))
                return false;
            i++;
        }
        return true;
    }

    /**
     * Deletes the value of the column at a chosen page and at a chosen row
     *
     * @param page the page of the column, must be greater then <code>0</code>
     * @param row  the row of the column, must be greater then <code>0</code>
     */
    public void deleteRow(int page, int row) {
        int i;
        for (i = 0; i < columnCount; i++) {
            columnObject[i].deleteRow(page, row);
        }
    }

    /**
     * Get the number of rows on a page.
     * 
     * @param page page of sdds file
     * @return number of rows on the specified page
     */
    public int getRowCount(int page) {
        int i, endRow, rows;
        endRow = 0;
        for (i = 0; i < columnCount; i++) {
            rows = columnObject[i].getRowCount(page);
            if (rows > endRow)
                endRow = rows;
        }
        return endRow;
    }

    /**
     * Write specified the page to a file. The page is checked before any of it is
     * written.
     *
     * @param page page to write
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean writePage(int page) {
        if (!preparePage(page))
            return false;
        int endRow = 0;
        for (int i = 0; i < columnCount; i++)
            endRow = Math.max(endRow, columnObject[i].getRowCount(page));
        boolean written = asciiFile ? writeAsciiPage(page, endRow) : writeBinaryPage(page, endRow);
        try {
            dos.flush();
            ledos.flush();
        } catch (IOException e) {
            setError("unable to flush data for page " + page + " (writePage)");
            return false;
        }
        return written;
    }

    private boolean writeAsciiRows(int page, int endRow) {
        for (int row = 1; row <= endRow; row++) {
            for (int i = 0; i < columnCount; i++) {
                if (!columnObject[i].writeElement(dos, page, row, true)) {
                    setError("invalid column elemant at page (" + page + ") row (" + row + ") for column "
                            + columnObject[i].getName() + " (writePage)");
                    return false;
                }
            }
            try {
                dos.writeBytes("\n");
            } catch (IOException e) {
                setError("unable to write newline character to ascii page " + page + " (writePage)");
                return false;
            }
        }
        return true;
    }

    private boolean writeAsciiPage(int page, int endRow) {
        try {
            dos.writeBytes("!page number " + page + "\n");
        } catch (IOException e) {
            setError("unable to write comment line to ascii page " + page + " (writePage)");
            return false;
        }
        for (int i = 0; i < parameterCount; i++) {
            if (!parameterObject[i].writeValue(dos, page, true)) {
                setError("invalid page number (" + page + ") for parameter " + parameterObject[i].getName()
                        + " (writePage)");
                return false;
            }
        }
        for (int i = 0; i < arrayCount; i++) {
            if (!arrayObject[i].writeValues(dos, page, true)) {
                setError("unable to write array data on page " + page + " for array " + arrayObject[i].getName()
                        + " (writePage)");
                return false;
            }
        }
        if (columnCount == 0)
            return true;
        try {
            /*
             * We are using 32bit integer row count instead of 64bit because Java arrays are
             * limited in size
             */
            if (!noRowCount)
                dos.writeBytes("\t" + endRow + "\n");
        } catch (IOException e) {
            setError("unable to write number of rows to ascii page " + page + " (writePage)");
            return false;
        }
        if (!writeAsciiRows(page, endRow))
            return false;
        if (noRowCount) {
            try {
                dos.writeBytes("\n");
            } catch (IOException e) {
                setError("unable to write newline character to ascii page " + page + " (writePage)");
                return false;
            }
        }
        return true;
    }

    private boolean writeBinaryElement(int column, int page, int row) {
        boolean written = bigEndian ? columnObject[column].writeElement(dos, page, row, false)
                : columnObject[column].writeElement(ledos, page, row);
        if (!written)
            setError("invalid column elemant at page (" + page + ") row (" + row + ") for column "
                    + columnObject[column].getName() + " (writePage)");
        return written;
    }

    private boolean writeBinaryPage(int page, int endRow) {
        try {
            /*
             * We are using 32bit integer row count instead of 64bit because Java arrays are
             * limited in size
             */
            if (bigEndian)
                dos.writeInt(endRow);
            else
                ledos.writeInt(endRow);
        } catch (IOException e) {
            setError("unable to write row count to binary page " + page + " (writePage)");
            return false;
        }
        for (int i = 0; i < parameterCount; i++) {
            if (!(bigEndian ? parameterObject[i].writeValue(dos, page, false)
                    : parameterObject[i].writeValue(ledos, page))) {
                setError("invalid page number (" + page + ") for parameter " + parameterObject[i].getName()
                        + " (writePage)");
                return false;
            }
        }
        for (int i = 0; i < arrayCount; i++) {
            if (!(bigEndian ? arrayObject[i].writeValues(dos, page, false) : arrayObject[i].writeValues(ledos, page))) {
                setError("unable to write binary array data on page " + page + " for array "
                        + arrayObject[i].getName() + " (writePage)");
                return false;
            }
        }
        if (columnMajorOrder) {
            for (int i = 0; i < columnCount; i++) {
                for (int row = 1; row <= endRow; row++) {
                    if (!writeBinaryElement(i, page, row))
                        return false;
                }
            }
        } else {
            for (int row = 1; row <= endRow; row++) {
                for (int i = 0; i < columnCount; i++) {
                    if (!writeBinaryElement(i, page, row))
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * add a single element to a column of data
     *
     * @param column Column number (first column is 1)
     * @param v      value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(int column, String v) {
        if (column < 1 || column > columnCount) {
            setError("Invalid column specified (addColumnElement)");
            return false;
        }
        column--;
        if (columnObject[column].getType() == SDDS_ENUM)
            return addEnumElement(column, SDDSEnumDefinition.parseCode(v));
        String values[];
        int pcount = pageCount();
        int size = columnObject[column].getRowCount(pcount);
        if (size < 0) {
            size = 1;
            values = new String[size];
        } else {
            size++;
            Object o[] = (columnObject[column].getValues(pcount, false));
            values = new String[size];
            for (int i = 0; i < size - 1; i++)
                values[i] = o[i].toString();
        }

        values[size - 1] = v;
        columnObject[column].setValues(values, pcount, 1);
        return true;
    }

    /**
     * add a single element to a column of data
     *
     * @param name Column name
     * @param v    value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(String name, String v) {
        return addColumnElement(getColumnIndex(name) + 1, v);
    }

    /**
     * add a single element to a column of data
     *
     * @param column Column number (first column is 1)
     * @param v      value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(int column, char v) {
        String s = String.valueOf(v);
        return addColumnElement(column, s);
    }

    /**
     * add a single element to a column of data
     *
     * @param name Column name
     * @param v    value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(String name, char v) {
        return addColumnElement(getColumnIndex(name) + 1, v);
    }

    /**
     * add a single element to a column of data
     *
     * @param column Column number (first column is 1)
     * @param v      value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(int column, double v) {
        if (column < 1 || column > columnCount) {
            setError("Invalid column specified (addColumnElement)");
            return false;
        }
        column--;
        if (columnObject[column].getType() == SDDS_ENUM) {
            setError("Enum values are categorical; a numeric value requires an explicit integer code (addColumnElement)");
            return false;
        }
        double values[];
        int pcount = pageCount();
        int size = columnObject[column].getRowCount(pcount);
        if (size < 0) {
            size = 1;
            values = new double[size];
        } else {
            size++;
            Object o[] = (columnObject[column].getValues(pcount, false));
            values = new double[size];
            for (int i = 0; i < size - 1; i++) {
                try {
                    values[i] = ((Double) o[i]).doubleValue();
                } catch (Exception e) {
                    setError("Attempt to set double value (" + v + ") for column " + column
                            + " failed. (addColumnElement)");
                    return false;
                }
            }
        }

        values[size - 1] = v;
        columnObject[column].setValues(values, pcount, 1);
        return true;
    }

    /**
     * add a single element to a column of data
     *
     * @param name Column name
     * @param v    value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(String name, double v) {
        return addColumnElement(getColumnIndex(name) + 1, v);
    }

    /**
     * add a single element to a column of data
     *
     * @param column Column number (first column is 1)
     * @param v      value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(int column, float v) {
        if (column < 1 || column > columnCount) {
            setError("Invalid column specified (addColumnElement)");
            return false;
        }
        column--;
        if (columnObject[column].getType() == SDDS_ENUM) {
            setError("Enum values are categorical; a numeric value requires an explicit integer code (addColumnElement)");
            return false;
        }
        double values[];
        int pcount = pageCount();
        int size = columnObject[column].getRowCount(pcount);
        if (size < 0) {
            size = 1;
            values = new double[size];
        } else {
            size++;
            Object o[] = (columnObject[column].getValues(pcount, false));
            values = new double[size];
            for (int i = 0; i < size - 1; i++) {
                try {
                    values[i] = (double) ((Float) o[i]).floatValue();
                } catch (Exception e) {
                    setError("Attempt to set float value (" + v + ") for column " + column
                            + " failed. (addColumnElement)");
                    return false;
                }
            }
        }

        values[size - 1] = (double) v;
        columnObject[column].setValues(values, pcount, 1);
        return true;
    }

    /**
     * add a single element to a column of data
     *
     * @param name Column name
     * @param v    value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(String name, float v) {
        return addColumnElement(getColumnIndex(name) + 1, v);
    }

    /**
     * add a single element to a column of data
     *
     * @param column Column number (first column is 1)
     * @param v      value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(int column, long v) {
        if (column < 1 || column > columnCount) {
            setError("Invalid column specified (addColumnElement)");
            return false;
        }
        column--;
        if (columnObject[column].getType() == SDDS_ENUM)
            return addEnumElement(column, (v < Integer.MIN_VALUE) || (v > Integer.MAX_VALUE) ? null
                    : Integer.valueOf((int) v));
        long values[];
        int pcount = pageCount();
        int size = columnObject[column].getRowCount(pcount);
        if (size < 0) {
            size = 1;
            values = new long[size];
        } else {
            size++;
            Object o[] = (columnObject[column].getValues(pcount, false));
            values = new long[size];
            for (int i = 0; i < size - 1; i++) {
                try {
                    values[i] = ((Number) o[i]).longValue();
                } catch (Exception e) {
                    setError("Attempt to set long value (" + v + ") for column " + column
                            + " failed. (addColumnElement)");
                    return false;
                }
            }
        }

        values[size - 1] = v;
        columnObject[column].setValues(values, pcount, 1);
        return true;
    }

    /**
     * add a single element to a column of data
     *
     * @param name Column name
     * @param v    value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(String name, long v) {
        return addColumnElement(getColumnIndex(name) + 1, v);
    }

    /**
     * add a single element to a column of data
     *
     * @param column Column number (first column is 1)
     * @param v      value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(int column, short v) {
        if (column < 1 || column > columnCount) {
            setError("Invalid column specified (addColumnElement)");
            return false;
        }
        column--;
        if (columnObject[column].getType() == SDDS_ENUM)
            return addEnumElement(column, Integer.valueOf(v));
        long values[];
        int pcount = pageCount();
        int size = columnObject[column].getRowCount(pcount);
        if (size < 0) {
            size = 1;
            values = new long[size];
        } else {
            size++;
            Object o[] = (columnObject[column].getValues(pcount, false));
            values = new long[size];
            for (int i = 0; i < size - 1; i++) {
                try {
                    values[i] = (long) (((Short) o[i]).shortValue());
                } catch (Exception e) {
                    setError("Attempt to set short value (" + v + ") for column " + column
                            + " failed. (addColumnElement)");
                    return false;
                }
            }
        }

        values[size - 1] = (long) v;
        columnObject[column].setValues(values, pcount, 1);
        return true;
    }

    /**
     * add a single element to a column of data
     *
     * @param name Column name
     * @param v    value to add to the column
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean addColumnElement(String name, short v) {
        return addColumnElement(getColumnIndex(name) + 1, v);
    }

    /** Add one checked code to the last page of an enum column. */
    private boolean addEnumElement(int column, Integer code) {
        SDDSEnumDefinition definition = columnObject[column].getEnumDefinition();
        if ((code == null) || (definition == null) || !definition.hasCode(code.intValue())) {
            setError("Invalid enum code for column " + columnObject[column].getName() + " (addColumnElement)");
            return false;
        }
        int page = pageCount();
        int rows = Math.max(columnObject[column].getRowCount(page), 0);
        columnObject[column].setValues(new long[] { code.intValue() }, page, rows + 1);
        return true;
    }

    /**
     * Write a new row of column data. Will write the latest data added to each
     * column.
     *
     * @return <code>true</code> if successful<br>
     *         <code>false</code> if an error occurs
     */
    public boolean writeRow() {
        int i, j, row;
        int page = pageCount();
        // check that all columns are updated to the same row
        row = columnObject[0].getRowCount(page);
        for (i = 1; i < columnCount; i++) {
            j = columnObject[i].getRowCount(page);
            if (row != j) {
                setError("row counts for columns are not consistent (column " + i + " has " + j + " rows, column 1 has "
                        + row + " rows)");
                return false;
            }
        }
        if (asciiFile) {
            if (columnCount > 0) {
                for (i = 0; i < columnCount; i++) {
                    if (!columnObject[i].writeElement(dos, page, row, true)) {
                        setError("invalid column elemant at page (" + page + ") row (" + row + ") for column "
                                + columnObject[i].getName() + " (writeRow)");
                        try {
                            dos.flush();
                        } catch (IOException e) {
                            return false;
                        }
                        return false;
                    }
                }
                try {
                    dos.writeBytes("\n");
                } catch (IOException e) {
                    setError("unable to write newline character to ascii page " + page + " (writeRow)");
                    return false;
                }
            }
            try {
                dos.flush();
            } catch (IOException e) {
                setError("unable to flush data for ascii page " + page + " (writeRow)");
                return false;
            }
        } else {
            setError("unable to use writePage for binary data " + page + " (writeRow)");
            return false;
        }
        return true;
    }

    /**
     * Append to the error description array.
     * 
     * @param error_text error message
     */
    public void setError(String error_text) {
        if (error_text != null) {
            n_errors++;
            if (error_description == null) {
                error_description = new String[1];
            } else {
                error_description = (String[]) SDDSUtil.resize(error_description, n_errors);
            }
            error_description[n_errors - 1] = error_text;
        }
    }

    /**
     * Return all the error messages.
     */
    public String getErrors() {
        int i, depth;
        String s = new String("");
        if (n_errors == 0)
            return s;
        depth = n_errors;
        if (error_description == null) {
            s = "warning: internal error: error_description variable is unexpectedly null\n";
        } else {
            for (i = depth - 1; i >= 0; i--) {
                if (error_description[i] == null) {
                    s = s + "warning: internal error: error_description[" + i + "] variable is unexpectedly null\n";
                } else {
                    s = s + error_description[i] + "\n";
                }
            }
        }
        n_errors = 0;
        return s;
    }

    /**
     * Print the error messages to standard error.
     */
    public void printErrors() {
        if (n_errors == 0)
            return;
        System.err.print("Error:\n" + getErrors());
    }
}
