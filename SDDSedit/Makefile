JAVAC=javac
JAR=jar
LIBDIR=../javalib
CLSDIR=$(LIBDIR)/SDDS
SRCS=$(wildcard *.java)
TARGET=$(LIBDIR)/SDDSedit.jar

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRCS)
	@mkdir -p $(CLSDIR)
	$(JAVAC) -classpath $(CLSDIR) -d $(CLSDIR) $(SRCS)
	$(JAR) cf $(TARGET) -C $(CLSDIR) .

clean:
	@rm -rf $(CLSDIR) $(TARGET)
