# Top-level Makefile to build SDDS and SDDSedit
.PHONY: all clean

SUBDIRS = SDDS SDDSedit

all:
	@for dir in $(SUBDIRS); do \
		$(MAKE) -C $$dir; \
	done

clean:
	@for dir in $(SUBDIRS); do \
		$(MAKE) -C $$dir clean; \
	done
