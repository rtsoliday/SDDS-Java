Summary:	SDDS Java Interface
Name:		SDDSJava
License:	EPICS Open license http://www.aps.anl.gov/epics/license/open.php
Group:		Development/Languages
URL:		http://www.aps.anl.gov/asd/oag/oaghome.shtml
Packager:	Robert Soliday <soliday@aps.anl.gov>
Autoreq:	0
Version:	6.0
Release:	1
Source:		SDDSJava-6.0.tar.gz


%define debug_package %{nil}
%undefine __check_files
%description
Java JAR files used to read and write SDDS data files, and the jsddsEdit editor.
Needs Java 11 or newer on the PATH.

%prep
%setup

%build
%install
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/sdds
install -m 644 SDDS.jar %{buildroot}/usr/share/sdds/SDDS.jar
install -m 644 SDDSedit.jar %{buildroot}/usr/share/sdds/SDDSedit.jar
install -m 644 LICENSE %{buildroot}/usr/share/sdds/LICENSE
install -m 755 jsddsEdit %{buildroot}%{_bindir}/jsddsEdit

%files

%{_bindir}/jsddsEdit
/usr/share/sdds/SDDS.jar
/usr/share/sdds/SDDSedit.jar
/usr/share/sdds/LICENSE
