# Conformance tests for the SDDS Java library and SDDSedit.
JAVAC=javac
JAVA=java
BUILD=build
CLASSES=$(BUILD)/classes
LIBRARY=../javalib/SDDSedit.jar

.PHONY: test test-gui classes clean

classes:
	@mkdir -p $(CLASSES)
	$(JAVAC) -classpath $(LIBRARY) -d $(CLASSES) SDDS6ConformanceTest.java SDDSedit/SDDSeditGuiTest.java

test: classes
	$(JAVA) -Dsdds.tests=. -classpath $(CLASSES):$(LIBRARY) SDDS6ConformanceTest

test-gui: classes
	xvfb-run -a -s "-screen 0 1280x800x24" $(JAVA) -Dsdds.tests=. -classpath $(CLASSES):$(LIBRARY) SDDS.java.SDDSedit.SDDSeditGuiTest

clean:
	@rm -rf $(BUILD)
