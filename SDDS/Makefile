JAVAC=javac
JAR=jar
LIBDIR=../javalib
CLSDIR=$(LIBDIR)/SDDS
SRCS=$(wildcard *.java)
TARGET=$(LIBDIR)/SDDS.jar

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRCS)
	@mkdir -p $(CLSDIR)
	$(JAVAC) -d $(CLSDIR) $(SRCS)
	$(JAR) cf $(TARGET) -C $(CLSDIR) .

clean:
	@rm -rf $(CLSDIR) $(TARGET)
