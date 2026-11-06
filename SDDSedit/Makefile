JAVAC=javac
JAR=jar
LIBDIR=../javalib
CLSDIR=$(LIBDIR)/SDDS
SRCS=$(wildcard *.java)
TARGET=$(LIBDIR)/SDDSedit.jar

.PHONY: all clean

all: $(TARGET)

# The jar holds the library classes too, so it depends on the library sources.
$(TARGET): $(SRCS) $(wildcard ../SDDS/*.java) $(LIBDIR)/SDDS.jar
	@mkdir -p $(CLSDIR)
	$(JAVAC) -classpath $(CLSDIR) -d $(CLSDIR) $(SRCS)
	$(JAR) cmf manifest $(TARGET) -C $(CLSDIR) SDDS/java/SDDSedit -C $(CLSDIR) SDDS/java/SDDS

clean:
	@rm -rf $(CLSDIR) $(TARGET)
