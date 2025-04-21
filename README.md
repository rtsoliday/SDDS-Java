# SDDS-Java

A pure‑Java implementation of the **Self‑Describing Data Sets (SDDS)** file protocol, developed at the Advanced Photon Source (APS). The library lets any Java application read, manipulate, and write SDDS files – the same binary format used by the C, Fortran, Tcl/Tk, Python and MATLAB toolkits distributed with the APS SDDS suite. A small Swing application, **SDDSedit**, is included for quick inspection and editing of SDDS files.

---

## Features

* **Pure Java** – no native libraries or JNI required.
* Supports all SDDS data types: parameters, columns and arrays.
* Handles unlimited numbers of pages (tables) per file.
* Simple, chainable API for defining and populating data structures.
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
import SDDS.SDDSFile;

public class ExampleWrite {
    public static void main(String[] args) throws Exception {
        SDDSFile file = new SDDSFile("beam.sdds", SDDSFile.WRITE);

        file.defineParameter("charge", "bunch charge", "pC", SDDSFile.DOUBLE);
        file.defineColumn("x", "horizontal position", "m", SDDSFile.DOUBLE, 0);
        file.defineColumn("y", "vertical position", "m", SDDSFile.DOUBLE, 0);

        file.startTable(1000); // allocate rows
        for (int i = 0; i < 1000; i++) {
            file.setColumnValue("x", i, Math.sin(i * 0.01));
            file.setColumnValue("y", i, Math.cos(i * 0.01));
        }
        file.setParameterValue("charge", 5.6);

        file.writeTable();
        file.close();
    }
}
```

Running:

```bash
$ javac -classpath javalib/SDDS.jar ExampleWrite.java
$ java  -classpath .:javalib/SDDS.jar ExampleWrite
```

---

## SDDSedit – graphical editor

```bash
$ java -jar javalib/SDDSedit.jar
```

`SDDSedit` lets you inspect headers, browse pages, add/delete rows, and edit parameter or column values in place. Use **File → Save As** to write a new SDDS file once your edits are complete.

---

## Project structure

```
SDDS-Java/
├── SDDS/          # core library source
├── SDDSedit/      # Swing GUI
├── rpm/           # helper scripts/specs for building RPMs
├── javalib/       # build output (created by `make`)
└── Makefile       # top‑level dispatcher
```

---

## Acknowledgments
This project is developed and maintained by **[Accelerator Operations & Physics](https://www.aps.anl.gov/Accelerator-Operations-Physics)** at the **Advanced Photon Source** at **Argonne National Laboratory**.

For more details, visit the official **[SDDS documentation](https://www.aps.anl.gov/Accelerator-Operations-Physics/Documentation)**.
