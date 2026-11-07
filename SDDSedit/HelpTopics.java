/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

/** The help text, one HTML body per topic. Generated from the Qt editor's help and edited for this editor. */
final class HelpTopics {
    private HelpTopics() {
    }

    private static final String START =
            "<p>The SDDS Editor views and edits Self Describing Data Set files: the parameters, column "
            + "data and arrays on every page. A file named on the command line opens at startup.</p><ul><"
            + "li><b>New</b> with <b>File &#8250; New...</b> <span class=\"key\">&nbsp;Ctrl+N&nbsp;</span> "
            + "or the <b>New</b> toolbar button. Add parameters, columns and arrays and choose their name"
            + "s, types and units. Give parameters an initial value, columns a row count and arrays a sha"
            + "pe such as 5 or 3x4, then click <b>Create</b>. Problems such as duplicate names appear bes"
            + "ide the buttons as you type. Remove every definition to create an empty file and insert de"
            + "finitions later.</li><li><b>Open</b> a file with <b>File &#8250; Open</b> <span class=\"key"
            + "\">&nbsp;Ctrl+O&nbsp;</span> or the <b>Open</b> toolbar button.</li><li>Data appears in thr"
            + "ee panels: <b>Parameters</b> (one value each per page), <b>Columns</b> (rows of tabular da"
            + "ta) and <b>Arrays</b> (multidimensional data). Click a panel title, or use the <b>View</b>"
            + " menu, to collapse or expand a panel. Drag the gap between panels to resize them.</li><li>"
            + "The <b>outline</b> on the left summarizes the file and lists every parameter, column and a"
            + "rray with its type. Type a name in its filter box, and click an entry to show it. <b>View "
            + "&#8250; Outline</b> <span class=\"key\">&nbsp;Ctrl+Shift+O&nbsp;</span> hides or shows it.</"
            + "li><li>When a file has several pages, change pages with the toolbar arrows or the page lis"
            + "t.</li><li>Click a cell and type to change a value.</li><li><b>Save</b> with <b>File &#825"
            + "0; Save</b> <span class=\"key\">&nbsp;Ctrl+S&nbsp;</span> or <b>File &#8250; Save as...</b>."
            + " The <b>ASCII</b> / <b>Binary</b> switch at the right end of the toolbar selects the forma"
            + "t written.</li></ul><p>The status bar shows <b>&#9679; Modified</b> until changes are save"
            + "d, and the editor asks before discarding unsaved changes.</p><p class=\"note\">New files sta"
            + "rt with one page. Save asks for a filename; the previously open file is kept until you con"
            + "firm any unsaved changes.</p>";

    private static final String EDIT =
            "<h3>Cells</h3><ul><li>Click a cell to edit it. Press Enter to keep the change or Esc to ca"
            + "ncel it. Values are checked against the SDDS type, and invalid values are rejected.</li><l"
            + "i><b>Copy</b> <span class=\"key\">&nbsp;Ctrl+C&nbsp;</span> and <b>Paste</b> <span class=\"ke"
            + "y\">&nbsp;Ctrl+V&nbsp;</span> use tab-separated text, so data can move to and from spreadsh"
            + "eets. A paste fills cells from the current cell onward.</li><li><b>Delete</b> Del clears t"
            + "he selected cells.</li><li><b>Undo</b> <span class=\"key\">&nbsp;Ctrl+Z&nbsp;</span> and <b>"
            + "Redo</b> <span class=\"key\">&nbsp;Ctrl+Shift+Z&nbsp;</span> cover cell edits and structural"
            + " changes such as inserts, deletions, sorts and type changes. The status bar shows the last"
            + " undoable action. Changing pages clears the Undo history. The history keeps as much as fits"
            + " in about a quarter of the memory available to Java, so very large files may drop their"
            + " oldest steps.</li></ul><h3>Parameters</h3><p>P"
            + "arameters are listed as Name, Type, Units, Value and Description, in side-by-side groups w"
            + "hen the panel is wide enough; the list runs down each group before the next. Only the valu"
            + "e is edited in the table: double-click the type to change it, or the name, units or descri"
            + "ption to open the parameter's attributes. Copy, paste and the formula tools treat paramete"
            + "rs as one list in order. Right-click a parameter and choose <b>Move Up</b> or <b>Move Down"
            + "</b> to reorder parameters.</p><h3>Columns and arrays</h3><p>Each header shows the name wi"
            + "th a colored type badge (blue for integers, green for floating point, amber for text), the"
            + " units and, for arrays, the shape. Hatched cells lie past the end of an array shorter than"
            + " the longest one. Double-click a header to change the type, drag it to reorder, or right-c"
            + "lick it for more actions (see <a href=\"#menus\">Right-click menus</a>).</p><p>Click <b>Clea"
            + "r selection</b> in the Columns panel to deselect its cells and search all columns again. P"
            + "ress Esc in a table to clear its selection; if a cell is being edited, the first Esc cance"
            + "ls the edit and a second Esc clears the selection.</p><p class=\"note\">Floating-point value"
            + "s are shown with the fewest digits that reproduce the stored number exactly.</p>";

    private static final String STRUCTURE =
            "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\"><tr><th>To change</th><th>Use</th></tr"
            + "><tr><td>Parameters, columns and arrays</td><td><b>Edit &#8250; Parameter</b>, <b>Column</"
            + "b> or <b>Array</b>, then <b>Insert</b>, <b>Delete</b> or <b>Attributes</b>. Each panel hea"
            + "der also has <b>Insert</b> and <b>Attributes</b> buttons.</td></tr><tr><td>Column rows</td"
            + "><td><b>Edit &#8250; Column Rows &#8250; Insert</b> or <b>Delete</b>, or right-click a row"
            + " number.</td></tr><tr><td>Pages</td><td><b>Edit &#8250; Page &#8250; Insert</b>, <b>Insert"
            + " and clone current page</b> or <b>Delete</b>.</td></tr><tr><td>Array shape</td><td>Right-c"
            + "lick an array and choose <b>Resize</b>, or change the number of dimensions in its <b>Attri"
            + "butes</b>. Elements keep their indices.</td></tr></table><p><b>Attributes</b> edits the de"
            + "finition of the item under the current cell, such as its name, units, description and type"
            + ". <b>Delete</b> removes every selected parameter, column or array.</p>";

    private static final String MENUS =
            "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\"><tr><th>Right-click</th><th>Actions</t"
            + "h></tr><tr><td>Parameter</td><td>Attributes, Change Type, Move Up, Move Down, Delete</td><"
            + "/tr><tr><td>Column header or cell</td><td>Attributes, Time display (numeric Time columns),"
            + " Plot from file, Sort ascending, Sort descending, Search/Replace, Filter/View, Clear Filte"
            + "r/View, formula tools, Delete</td></tr><tr><td>Column row number</td><td>Insert, Delete, F"
            + "ilter/View, Clear Filter/View, formula tools</td></tr><tr><td>Array header or cell</td><td"
            + ">Attributes, Open Array Viewer, Plot, Search, Resize, formula tools, Delete</td></tr></tab"
            + "le><p>Sorting reorders whole rows and keeps rows with equal values in their original order"
            + ". The formula tools are Fill Series, Apply Numerical Expression and Apply Text Formula (se"
            + "e <a href=\"#formula\">Formulas and fill</a>).</p><p><b>Time display</b> is available for nu"
            + "meric columns whose names contain Time (ignoring case) and whose units are blank or second"
            + "s, so TimeOfDay in hours is excluded. Choose <b>Epoch (raw)</b>, <b>Date/time &#8212; Loca"
            + "l</b> or <b>Date/time &#8212; UTC</b>. Dates interpret values as Unix epoch seconds and sh"
            + "ow milliseconds and the time zone; durations such as elapsed seconds would display as date"
            + "s near 1970. Unsupported values remain raw. Editing, copying, searching, sorting, filterin"
            + "g and saving continue using the original numeric values. Each column keeps its display set"
            + "ting across pages, reordering and renaming until another document is opened or created; de"
            + "leting the column or changing it to another type or unit forgets the setting, and Undo res"
            + "tores it. Changing the display does not mark the file modified.</p>";

    private static final String FIND =
            "<h3>Column search box</h3><p>Type in the search box in the Columns panel header and press "
            + "Enter to select the next match; press Enter again to continue. The search covers the selec"
            + "ted columns, or all columns when none are selected. It ignores case, skips filtered rows a"
            + "nd wraps to the beginning.</p><h3>Search and replace</h3><p>Right-click a column and choos"
            + "e <b>Search/Replace</b>, or an array and choose <b>Search</b>. <b>Next</b> and <b>Previous"
            + "</b> step through the matches; <b>Replace</b> and <b>Replace All</b> change them.</p><h3>R"
            + "ow filter</h3><p><b>Filter/View...</b> <span class=\"key\">&nbsp;Ctrl+Shift+R&nbsp;</span> s"
            + "hows only the column rows that match an expression; no data is deleted. Open it from <b>Ed"
            + "it &#8250; Column Rows</b>, the <b>Filter rows</b> toolbar button or a right-click menu. A"
            + " chip beside the Columns title shows the filter and the number of visible rows: click it t"
            + "o edit the filter, or &#215; to clear it. Copy, paste, delete, search/replace and the form"
            + "ula tools change only visible rows.</p><table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\""
            + "><tr><th>Element</th><th>Syntax</th></tr><tr><td>Column value</td><td><code>X</code>, or <"
            + "code>[Beam Current]</code> for a name that is not a plain identifier. A bracketed name is "
            + "always a column, even <code>[row]</code>.</td></tr><tr><td>Row index</td><td><code>row</co"
            + "de> or <code>i</code>, starting at 0</td></tr><tr><td>Literals</td><td>numbers, <code>\"tex"
            + "t\"</code> or <code>'text'</code>, <code>true</code>, <code>false</code></td></tr><tr><td>C"
            + "omparison</td><td><code>==</code> <code>!=</code> <code>&lt;</code> <code>&lt;=</code> <co"
            + "de>&gt;</code> <code>&gt;=</code></td></tr><tr><td>Logic</td><td><code>&amp;&amp;</code> <"
            + "code>||</code> <code>!</code> and parentheses</td></tr></table><p>Numbers compare numerica"
            + "lly and other values as case-sensitive text. A value on its own is true unless it is zero,"
            + " empty, <code>false</code>, <code>no</code> or <code>off</code>. Column names match case-s"
            + "ensitively first, then ignoring case.</p><p>Examples: <code>X &gt; 0 &amp;&amp; Status == "
            + "\"OK\"</code>, <code>[Beam Current] &gt;= 100</code>, <code>row &lt; 10 || !Flag</code></p>";

    private static final String FORMULA =
            "<p>Select the cells to change in any table, then choose a tool from <b>Edit &#8250; Formul"
            + "a / Fill</b> or a right-click menu.</p><table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\""
            + "><tr><th>Tool</th><th>Each selected cell becomes</th></tr><tr><td><b>Fill Series...</b> <s"
            + "pan class=\"key\">&nbsp;Ctrl+Shift+F&nbsp;</span></td><td><i>start</i> + <i>step</i> &#215; "
            + "<code>i</code></td></tr><tr><td><b>Apply Numerical Expression...</b> <span class=\"key\">&nb"
            + "sp;Ctrl+Shift+E&nbsp;</span></td><td>the value of an expression, such as <code>x * 2</code"
            + "></td></tr><tr><td><b>Apply Text Formula...</b> <span class=\"key\">&nbsp;Ctrl+Shift+M&nbsp;"
            + "</span></td><td>a text template with its tokens replaced, such as <code>run_${i}</code></t"
            + "d></tr></table><h3>Variables</h3><table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\"><tr><"
            + "th>Expression</th><th>Template</th><th>Meaning</th></tr><tr><td><code>x</code></td><td><co"
            + "de>${x}</code></td><td>current cell value</td></tr><tr><td><code>a</code></td><td><code>${"
            + "a}</code></td><td>anchor value (first selected cell)</td></tr><tr><td><code>i</code></td><"
            + "td><code>${i}</code></td><td>position in the selection, from 0</td></tr><tr><td><code>row<"
            + "/code></td><td><code>${row}</code></td><td>row index, from 0</td></tr><tr><td><code>col</c"
            + "ode></td><td><code>${col}</code></td><td>column index, from 0</td></tr><tr><td><code>dr</c"
            + "ode></td><td><code>${dr}</code></td><td>rows below the anchor</td></tr><tr><td><code>dc</c"
            + "ode></td><td><code>${dc}</code></td><td>columns right of the anchor</td></tr></table><p>Ce"
            + "lls are processed top to bottom, then left to right; the first cell in that order is the a"
            + "nchor.</p><h3>Expressions</h3><ul><li>Operators <code>+</code> <code>-</code> <code>*</cod"
            + "e> <code>/</code> and <code>^</code> (power; <code>-2^2</code> is -4), with parentheses.</"
            + "li><li>Functions <code>abs</code> <code>sqrt</code> <code>sin</code> <code>cos</code> <cod"
            + "e>tan</code> <code>log</code> <code>exp</code> <code>floor</code> <code>ceil</code>. <code"
            + ">log</code> is the natural logarithm, and angles are in radians.</li><li>Constants <code>p"
            + "i</code> and <code>e</code>.</li></ul><h3>Examples</h3><table width=\"100%\" cellspacing=\"0\""
            + " cellpadding=\"5\"><tr><th>Formula</th><th>Effect</th></tr><tr><td><code>x + 10</code></td><"
            + "td>add 10 to each value</td></tr><tr><td><code>a + i*0.5</code></td><td>series from the an"
            + "chor value in steps of 0.5</td></tr><tr><td><code>x * (1 + dr*0.01)</code></td><td>scale b"
            + "y 1&#37; more on each row below the anchor</td></tr><tr><td><code>sin(x) * exp(-i/10)</cod"
            + "e></td><td>damped sine of each value</td></tr><tr><td><code>${x} mm</code></td><td>text fo"
            + "rmula: append \" mm\"</td></tr><tr><td><code>S${row}</code></td><td>text formula: S0, S1, S2"
            + ", ...</td></tr></table><p class=\"note\">Integer columns are computed exactly with 64-bit ar"
            + "ithmetic when an expression uses only integers, + - * exact division, abs, floor and ceil."
            + " A result that would be rounded or does not fit the type is rejected.</p>";

    private static final String ARRAYS =
            "<p>Open a viewer for the current array with the <b>Array viewer</b> toolbar button, <b>Edi"
            + "t &#8250; Array &#8250; Open Array Viewer...</b>, or by right-clicking an array. Each view"
            + "er is a separate window showing that array on the current page.</p><ul><li>Choose the dime"
            + "nsions that form the rows and columns of the grid; choosing an axis already in use swaps t"
            + "he two. Spin boxes and sliders select the slice of the other dimensions. All indices start"
            + " at 0.</li><li>Edits appear in the main table at once and share its Undo/Redo history. <sp"
            + "an class=\"key\">&nbsp;Ctrl+S&nbsp;</span> saves the document.</li><li>Copy and paste work o"
            + "n rectangular selections; <b>Copy slice</b> copies the displayed plane. A paste must fit i"
            + "nside the plane.</li><li>The viewer follows page changes, and slice indices adjust when th"
            + "e array shape changes.</li><li><b>Heatmap</b> colors numeric cells, scaled to the <b>Curre"
            + "nt slice</b> or to a <b>Fixed range</b> (enter Min and Max, then Apply). Gray marks missin"
            + "g, NaN and infinite values. The heatmap changes only the display.</li></ul>";

    private static final String EXPORT =
            "<h3>Plotting</h3><p>Select a cell in a column or numeric array and click <b>Plot</b> on th"
            + "e toolbar. You can also right-click a column and choose <b>Plot from file</b>, or an array"
            + " and choose <b>Plot</b>. Plot uses the last active column or array table. <code>sddsplot</"
            + "code> must be on the PATH. For columns, a <code>Time</code> column, if present, is used fo"
            + "r the horizontal axis. Arrays are plotted against the zero-based element index; multidimen"
            + "sional arrays use their stored order, with the last dimension varying fastest. Each page i"
            + "s plotted separately. Unsaved edits are included. A plot reads a temporary copy of the doc"
            + "ument, which is removed when the editor closes, so a plot left open after that can no lon"
            + "ger zoom.</p><h3>Export</h3><ul><li><b>File &#8250"
            + "; Export CSV</b> <span class=\"key\">&nbsp;Ctrl+Shift+C&nbsp;</span> writes the data as comm"
            + "a-separated values.</li><li><b>File &#8250; Export HDF</b> <span class=\"key\">&nbsp;Ctrl+Sh"
            + "ift+H&nbsp;</span> writes an HDF5 file by running the external <code>sdds2hdf</code> tool,"
            + " which must be on the PATH.</li></ul><"
            + "p class=\"note\">Saves and exports are written to a temporary file first, so a failure leave"
            + "s the existing file unchanged. The new file then replaces the old one: it keeps the old fi"
            + "le's permissions and, where the system allows, its group, but you become its owner, and an"
            + "y other hard links to the old file keep the old contents. Exporting does not mark the docu"
            + "ment as saved.</p>";

    private static final String STATUS =
            "<ul><li>The status bar shows whether the document is saved, the file (hover for the full p"
            + "ath), the page, the current cell, the row count (visible rows while a filter is active) an"
            + "d the last undoable action.</li><li>Messages appear briefly in the status bar and are kept"
            + " in the message log. Open the log with the <b>Messages</b> button or <b>View &#8250; Messa"
            + "ges</b> <span class=\"key\">&nbsp;Ctrl+Shift+L&nbsp;</span>; the button counts unread messag"
            + "es.</li><li>The <b>View</b> menu also shows or hides the toolbar, the status bar, the outl"
            + "ine and each panel.</li><li>The editor uses a dark appearance when the desktop reports a da"
            + "rk GNOME theme at startup. Set <code>SDDSEDIT_THEME</code> to <code>light</code> or <code>d"
            + "ark</code> to choose.</li><li><b>File &#8250; Restart</b> <span class=\"key\">&nbsp;Ctrl+R&nbsp;</span> restar"
            + "ts the editor after offering to save changes.</li></ul>";

    private static final String KEYS =
            "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\"><tr><th>Action</th><th>Shortcut</th></"
            + "tr><tr><td>New</td><td><span class=\"key\">&nbsp;Ctrl+N&nbsp;</span></td></tr><tr><td>Open</"
            + "td><td><span class=\"key\">&nbsp;Ctrl+O&nbsp;</span></td></tr><tr><td>Save</td><td><span cla"
            + "ss=\"key\">&nbsp;Ctrl+S&nbsp;</span></td></tr><tr><td>Save as</td><td><span class=\"key\">&nbs"
            + "p;Ctrl+Shift+S&nbsp;</span></td></tr><tr><td>Export CSV</td><td><span class=\"key\">&nbsp;Ct"
            + "rl+Shift+C&nbsp;</span></td></tr><tr><td>Export HDF</td><td><span class=\"key\">&nbsp;Ctrl+S"
            + "hift+H&nbsp;</span></td></tr><tr><td>Restart</td><td><span class=\"key\">&nbsp;Ctrl+R&nbsp;</span></td></tr><tr><td>Quit</td><td><span class=\"key\">&nbsp;Ctrl+Q&nbsp;</sp"
            + "an></td></tr><tr><td>Undo</td><td><span class=\"key\">&nbsp;Ctrl+Z&nbsp;</span></td></tr><tr"
            + "><td>Redo</td><td><span class=\"key\">&nbsp;Ctrl+Shift+Z&nbsp;</span></td></tr><tr><td>Copy<"
            + "/td><td><span class=\"key\">&nbsp;Ctrl+C&nbsp;</span></td></tr><tr><td>Paste</td><td><span c"
            + "lass=\"key\">&nbsp;Ctrl+V&nbsp;</span></td></tr><tr><td>Clear selected cells</td><td><span c"
            + "lass=\"key\">&nbsp;Delete&nbsp;</span></td></tr><tr><td>Filter/View rows</td><td><span class"
            + "=\"key\">&nbsp;Ctrl+Shift+R&nbsp;</span></td></tr><tr><td>Fill Series</td><td><span class=\"k"
            + "ey\">&nbsp;Ctrl+Shift+F&nbsp;</span></td></tr><tr><td>Apply Numerical Expression</td><td><s"
            + "pan class=\"key\">&nbsp;Ctrl+Shift+E&nbsp;</span></td></tr><tr><td>Apply Text Formula</td><t"
            + "d><span class=\"key\">&nbsp;Ctrl+Shift+M&nbsp;</span></td></tr><tr><td>Show or hide messages"
            + "</td><td><span class=\"key\">&nbsp;Ctrl+Shift+L&nbsp;</span></td></tr><tr><td>Show or hide t"
            + "he outline</td><td><span class=\"key\">&nbsp;Ctrl+Shift+O&nbsp;</span></td></tr><tr><td>Help"
            + "</td><td><span class=\"key\">&nbsp;F1&nbsp;</span></td></tr><tr><td>Find in this help</td><t"
            + "d><span class=\"key\">&nbsp;Ctrl+F&nbsp;</span></td></tr><tr><td>Next / previous match in th"
            + "is help</td><td><span class=\"key\">&nbsp;F3&nbsp;</span> / <span class=\"key\">&nbsp;Shift+F3"
            + "&nbsp;</span></td></tr></table>";

    /** Anchor, title and HTML body of each topic, in display order. */
    static final String[][] TOPICS = {
        { "start", "Getting started", START },
        { "edit", "Editing data", EDIT },
        { "structure", "Adding and removing", STRUCTURE },
        { "menus", "Right-click menus", MENUS },
        { "find", "Search and filter", FIND },
        { "formula", "Formulas and fill", FORMULA },
        { "arrays", "Array viewer", ARRAYS },
        { "export", "Plotting and export", EXPORT },
        { "status", "Status bar and messages", STATUS },
        { "keys", "Keyboard shortcuts", KEYS },
    };
}
