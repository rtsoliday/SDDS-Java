JAVAC=javac
JAR=jar
SRCS=$(wildcard *.java)
TARGET=SDDS.jar

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRCS)
	$(JAVAC) -d . $(SRCS)
	$(JAR) cf $(TARGET) $(shell find . -type f -name "*.class")

clean:
	$(RM) $(TARGET) $(shell find . -type f -name "*.class")
