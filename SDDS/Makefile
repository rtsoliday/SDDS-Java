JAVAC=javac
JAVACFLAGS=--release 11
JAR=jar
LIBDIR=../javalib
CLSDIR=$(LIBDIR)/SDDS
SRCS=$(wildcard *.java)
TARGET=$(LIBDIR)/SDDS.jar

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRCS) manifest ../LICENSE
	@mkdir -p $(CLSDIR)
	$(JAVAC) $(JAVACFLAGS) -d $(CLSDIR) $(SRCS)
	$(JAR) cmf manifest $(TARGET) -C $(CLSDIR) SDDS/java/SDDS -C .. LICENSE

clean:
	@rm -rf $(CLSDIR) $(TARGET)
