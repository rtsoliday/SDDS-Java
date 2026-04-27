# Agent Notes for SDDS-Java

This file contains a short tactical summary based on repository evidence. `../llm-wiki/scripts/refresh_wiki.py` rewrites only the machine-managed block.

<!-- BEGIN MACHINE:summary -->
## Quick start
- Repository-local guidance is sufficient: start with `AGENTS.md`, `README.md`, `docs/`, build/test/config files, and the source tree.
- A pure‑Java implementation of the **Self‑Describing Data Sets (SDDS)** file protocol, developed at the Advanced Photon Source (APS). The library lets any Java application read, manipulate, and write SDDS files – the same binary format used by the C, Fortran, Tcl/Tk, Python and MATLAB toolkits distributed with the APS SDDS suite. A small Swing application, **SDDSedit**, is included for quick inspection and editing of SDDS files.
- Primary work areas: `javalib`, `rpm`, `SDDS`, `SDDSedit`.

## Read first
- `README.md`: Primary project overview and workflow notes
- `SDDS/Makefile`: Build system entry point or dependency manifest
- `SDDSedit/Makefile`: Build system entry point or dependency manifest
- `LICENSE`: Repository configuration that affects local work

## Build and test
- Documented setup/build commands: `make`.
- Detected build systems: GNU Make.
- Unknown: no test workflow evidence was found in the inspected files.
- Likely run commands or operator entry points: `java -classpath .:javalib/SDDS.jar ExampleWrite`, `java -jar javalib/SDDSedit.jar`.

## Operational warnings
- Local checkout layout appears significant; avoid casual changes to sibling-repo assumptions or relative paths.

## Compatibility constraints
- Build and runtime behavior likely depends on neighboring core toolkit checkouts.

## Related knowledge
- Repository-local documentation should be treated as authoritative.
- If a shared `llm-wiki/` directory is present in this workspace or parent folder, consult [the matching repo page](../llm-wiki/repos/SDDS-Java.md) for additional architectural context.
- If no shared wiki is present, continue using repository-local evidence only.
- If available, [the SDDS concept page](../llm-wiki/concepts/sdds.md) adds broader cross-repo context.
- If present in this workspace, [the cross-repo map](../llm-wiki/insights/cross-repo-map.md) helps explain related repositories.
<!-- END MACHINE:summary -->

## Human notes
Add durable repo-specific instructions here.
