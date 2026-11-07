# SDDS-Java release notes

## 6.0

### Library

* Reads and writes SDDS protocols 1 to 6, including SDDS6 enums in parameters, arrays and columns. Files are written at the lowest protocol version that can hold them, so files without enums stay readable by older programs.
* `longdouble` and `ulong64` values are kept exactly. Adds XZ and LZMA files in pure Java, column-major output, and UTF-8 strings that keep non-UTF-8 bytes.
* `writeFile` checks every page before opening the output, so a failed write leaves an existing file alone.
* String setters now read `nan` and `inf` as C does, in any case. They used to store 0.
* No public method was removed.

### SDDSedit

* Rebuilt to follow the Qt `sddseditor` in layout, menus, shortcuts and behavior. See the README and **Info → Help**.
* Adds row filters, sorting, search and replace, formula tools, a date display for time columns, an array viewer, CSV export, plotting through `sddsplot`, HDF export through `sdds2hdf`, and the New File dialog.
* Saves are staged, checked and then swapped in. A read-only file or one locked by an SDDS program is refused. Saving through a link to a versioned file writes a new version.
* `jsddsEdit` now passes a file name to the editor.

### Requirements and packaging

* Java 11 or newer. The jars now target Java 11 whichever JDK builds them.
* The RPM and both jars now include `LICENSE`.
* `rpm/build.tcl` now exits with an error when `rpmbuild` fails.
* `java -jar SDDS.jar` was never able to run. The library jar no longer names a main class.

### Known limits

* `field_length` is shown and can be edited, but the library always writes 0.
* HDF export depends on `sdds2hdf`, which cannot write some types, such as `longdouble` and `ulong64`. The editor then reports the failure and keeps any existing file.
* In SDDS1 to SDDS5 headers, text containing `"` gains a backslash on every save. The C toolkit does the same, and SDDS6 headers do not.
* The library's string setters store 0 for text that is not a number, and numeric setters wrap values out of range for the type. SDDSedit checks values first.
* Only tested on Linux, with OpenJDK 11 and 17, on a virtual display.
