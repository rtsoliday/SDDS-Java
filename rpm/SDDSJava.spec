Summary:	SDDS Java Interface
Name:		SDDSJava
License:	EPICS Open license http://www.aps.anl.gov/epics/license/open.php
Group:		Development/Languages
URL:		http://www.aps.anl.gov/asd/oag/oaghome.shtml
Packager:	Robert Soliday <soliday@aps.anl.gov>
Autoreq:	0
Version:	5.3
Release:	1
Source:		SDDSJava-5.3.tar.gz


%define debug_package %{nil}
%undefine __check_files
%description
Java JAR files used to read and write SDDS data files.

%prep
%setup

%build
%install
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/sdds
install -m 644 SDDS.jar %{buildroot}/usr/share/sdds/SDDS.jar
install -m 644 SDDSedit.jar %{buildroot}/usr/share/sdds/SDDSedit.jar
install -m 755 jsddsEdit %{buildroot}%{_bindir}/jsddsEdit

%files

%{_bindir}/jsddsEdit
/usr/share/sdds/SDDS.jar
/usr/share/sdds/SDDSedit.jar
