# Top-level Makefile to build SDDS and SDDSedit
.PHONY: all clean test test-gui

SUBDIRS = SDDS SDDSedit

all:
	@for dir in $(SUBDIRS); do \
		$(MAKE) -C $$dir; \
	done

# Library conformance tests, which also exchange files with the C toolkit when it is
# found in ../SDDS or SDDS_BIN.
test: all
	$(MAKE) -C tests test

# SDDSedit tests, which need a window and so run in a private virtual display.
test-gui: all
	$(MAKE) -C tests test-gui

clean:
	@for dir in $(SUBDIRS); do \
		$(MAKE) -C $$dir clean; \
	done
	$(MAKE) -C tests clean
