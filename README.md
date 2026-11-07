# SDDS-Java

A pure‑Java implementation of the **Self‑Describing Data Sets (SDDS)** file protocol, developed at the Advanced Photon Source (APS). The library lets any Java application read, manipulate, and write SDDS files – the same binary format used by the C, Fortran, Tcl/Tk, Python and MATLAB toolkits distributed with the APS SDDS suite. A small Swing application, **SDDSedit**, is included for quick inspection and editing of SDDS files.

---

## Features

* **Pure Java** – no native libraries or JNI required.
* Reads and writes SDDS protocols 1 to 6, including native SDDS6 categorical enums.
* Supports all SDDS data types in parameters, columns and arrays, with `longdouble` and `ulong64` values kept exactly.
* Both binary byte orders and both binary major orders, plus ASCII.
* Plain, gzip (`.gz`) and XZ/LZMA (`.xz`, `.lzma`) files, chosen by file name as in the C library.
* Handles unlimited numbers of pages (tables) per file.
* Command‑line & GUI utilities:
  * `SDDS.SDDSUtil` – quick conversions and file inspection.
  * `SDDSedit.jar` – Swing editor for visualising and editing SDDS files.

---

## Quick start

### Prerequisites

* **JDK 11** or newer (tested up to OpenJDK 21).
* GNU `make` (or run the `javac`/`jar` commands manually).

### Building everything

```bash
# Clone the repo
$ git clone https://github.com/rtsoliday/SDDS-Java.git
$ cd SDDS-Java

# Compile the core library and SDDSedit
$ make
```

Artifacts are written to the **`javalib/`** directory:

| Jar | Description |
| --- | --- |
| `SDDS.jar` | Core SDDS library – add to your project’s class‑path. |
| `SDDSedit.jar` | Stand‑alone GUI editor (depends on `SDDS.jar`). |

To rebuild from scratch run `make clean && make`.

---

## Using the library

```java
import SDDS.java.SDDS.*;

public class ExampleWrite {
    public static void main(String[] args) {
        SDDSFile file = new SDDSFile("beam.sdds", true); // true for binary

        file.defineParameter("charge", "Q", "pC", "bunch charge", null, SDDSUtil.SDDS_DOUBLE, null);
        file.defineColumn("x", "x", "m", "horizontal position", null, SDDSUtil.SDDS_DOUBLE, 0);
        file.defineEnumColumn("state", "0:OFF,10:ON,-1:FAULT");

        double[] x = new double[1000];
        int[] state = new int[1000];
        for (int i = 0; i < 1000; i++) {
            x[i] = Math.sin(i * 0.01);
            state[i] = i % 2 == 0 ? 10 : 0;
        }
        file.setParameter("charge", 5.6, 1);
        file.setColumn("x", x, 1);
        file.setColumn("state", state, 1);

        if (!file.writeFile())
            file.printErrors();
    }
}
```

Running:

```bash
$ javac -classpath javalib/SDDS.jar ExampleWrite.java
$ java  -classpath .:javalib/SDDS.jar ExampleWrite
```

---

## SDDS6 enums

SDDS6 adds the `enum` type (type ID 12, `SDDSUtil.SDDS_ENUM`) to parameters, arrays and columns. A value is a signed 32-bit code with a label from a required dictionary such as `0:OFF,10:ON,-1:FAULT`. Files carry codes. This library follows the contract in the SDDS toolkit's [`SDDSlib/doc/enums.md`](https://github.com/rtsoliday/SDDS/blob/master/SDDSlib/doc/enums.md), and writes SDDS6 only when a layout has an enum, so other files stay readable by older programs.

| Task | Methods |
| --- | --- |
| Define | `defineEnumColumn`, `defineEnumParameter`, `defineEnumArray` |
| Query a mapping | `getColumnEnumValues`, `getParameterEnumValues`, `getArrayEnumValues`, and the matching `get...EnumDefinition` |
| Set checked codes | The integer `setColumn`, `setParameter` and `setArray` overloads, and strings holding complete decimal codes |
| Set by label | `setColumnLabels`, `setParameterLabel`, `setArrayLabels` |
| Get codes and labels | `getColumnEnumCodes`, `getColumnEnumLabels`, and the parameter and array equivalents |
| Replace a mapping or change type | `convertColumn`, `convertParameter`, `convertArray`, which remap by exact label or convert to and from `long` and `string` |
| Guard an exporter | `checkEnumExport` |

`SDDSEnumDefinition` is the dictionary itself: lookups in both directions, `remap`, order-independent `equals`, and `setReaderLimits` for the per-thread header limits (16 MiB per layout command and 1 MiB per mapping by default).

Enums are categorical, so numeric setters and casts reject them, and the generic getters return `SDDSEnumValue` objects rather than numbers. An unset value holds code 0, which is valid only if the dictionary maps it; nothing else is substituted, and `writeFile` refuses to write an unmapped value and names it, leaving any existing file alone.

```java
SDDSFile file = new SDDSFile("states.sdds");
file.readFile();
int index = file.getColumnIndex("state");
String mapping = file.getColumnEnumValues(index);   // "0:OFF,10:ON,-1:FAULT"
int[] codes = file.getColumnEnumCodes(index, 1);    // {10, 0, -1}
String[] labels = file.getColumnEnumLabels(index, 1); // {"ON", "OFF", "FAULT"}
file.setColumn(index, new int[] { 42 }, 1, 1);      // false: 42 is not mapped
file.convertColumn(index, SDDSUtil.SDDS_ENUM, "7:OFF,8:ON,9:FAULT"); // codes follow the labels
```

Limits of a pure Java implementation:

* A page is limited to the size of a Java array, about 2<sup>31</sup> rows. Larger pages in a file are reported as errors.
* XZ input supports the LZMA2 filter, which is what the `xz` program and the SDDS toolkit use unless told otherwise. BCJ and delta filters are reported as errors.
* XZ output is valid but its ratio is below that of liblzma, because the encoder is deliberately simple.
* `&associate` commands are ignored, as they are in the toolkit's default build.
* Files compressed with the old `compress` and `freeze` programs (`.Z`, `.F`) are not unpacked.

---

## SDDSedit – graphical editor

```bash
$ java -jar javalib/SDDSedit.jar data.sdds
```

`SDDSedit` is modeled on the Qt `sddseditor` in the SDDS toolkit: the same layout, menus, shortcuts and behavior, in pure Java. **Info → Help** (F1) has the full guide, and it is searchable.

* **Panels.** Parameters, columns and arrays each have a panel that can be collapsed and resized. Parameters wrap into side-by-side groups, and column headers show each type and its units. The outline on the left summarizes the file and jumps to any definition.
* **Editing.** A click edits a cell, and typing replaces its text. Ctrl-click, Shift-click and dragging select cells, disconnected ones too, and Esc clears the selection. Copy and paste use tab-separated text. Undo and redo cover cell edits and structural changes, but history does not cross a page change. Values are checked against their type.
* **Rows.** Sort, insert and delete rows, or use **Filter rows** to view only rows that pass an expression such as `x>0 && Status=="OK"`. This never changes the file. A numeric column with Time in its name can display as a local or UTC date.
* **Formulas.** Fill Series, Apply Numerical Expression and Apply Text Formula work on the selected cells. Integers of up to 64 bits are computed exactly, and long double cells keep their digits through `+ - * /`.
* **Arrays.** **Array viewer** opens a window with any two dimensions as the grid, sliders for the others, and an optional heatmap. Its edits share the main window's undo history.
* **Definitions.** The insert and attributes dialogs change definitions, and dragging a column or array header reorders it. **File → New** builds a file from nothing.
* **Saving.** A save is written beside the destination, checked, and only then replaces it, so a failed save leaves the old file as it was. Saving through a symbolic link to a versioned file such as `data.009` writes `data.010` and moves the link. The save format switch in the toolbar chooses ASCII or binary.
* **Other programs.** **Plot** runs `sddsplot` on a snapshot. **Export CSV** is built in. **Export HDF** runs `sdds2hdf`, because writing HDF natively needs a library that pure Java does not have. Both programs must be on the PATH.

Enum values appear as `label (code)` and are edited by choosing from the states in the dictionary. New rows, pages and array elements start at the state of code 0 if the dictionary maps it. Otherwise they stay blank, and saving names the cell that needs a state. To replace a dictionary, edit **enum values** in an element's attributes: values are converted by label, and the change is refused if a label in use would be lost.

The window follows a dark desktop theme where the toolkit reports one. Set `SDDSEDIT_THEME` or the `sddsedit.theme` property to `light` or `dark` to choose.

---

## Tests

```bash
$ make test      # library conformance tests
$ make test-gui  # SDDSedit tests, which need xvfb-run
```

`tests/fixtures/enums` is a copy of the fixtures shared by the C and C++ readers. When the SDDS toolkit is at `../SDDS/bin/Linux-x86_64` or in the directory named by `SDDS_BIN`, the tests also exchange files with the C programs in every encoding, in both directions. Otherwise those tests are skipped.

---

## Project structure

```
SDDS-Java/
├── SDDS/          # core library source
├── SDDSedit/      # Swing GUI
├── tests/         # conformance tests
├── rpm/           # helper scripts/specs for building RPMs
├── javalib/       # build output (created by `make`)
└── Makefile       # top‑level dispatcher
```

---

## Acknowledgments
This project is developed and maintained by **[Accelerator Operations & Physics](https://www.aps.anl.gov/Accelerator-Operations-Physics)** at the **Advanced Photon Source** at **Argonne National Laboratory**.

For more details, visit the official **[SDDS documentation](https://www.aps.anl.gov/Accelerator-Operations-Physics/Documentation)**.
