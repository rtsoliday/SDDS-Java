#!/bin/sh  
# \
exec tclsh "$0" "$@"

set version 6.0
set name SDDSJava-$version
puts "Building $name RPM"

exec ./rpmdev-setuptree
exec cp -f SDDSJava.spec $env(HOME)/rpmbuild/SPECS/
exec rm -rf $env(HOME)/rpmbuild/BUILD/$name
exec mkdir $env(HOME)/rpmbuild/BUILD/$name
set binFiles "../SDDSedit/jsddsEdit"
foreach f $binFiles {
  exec chmod a+rx $f
  exec chmod a-w $f
  exec cp -f $f $env(HOME)/rpmbuild/BUILD/${name}/
}
set jarFiles "../javalib/SDDS.jar ../javalib/SDDSedit.jar"
foreach f $jarFiles {
  exec chmod a+r $f
  exec chmod a-wx $f
  exec cp -f $f $env(HOME)/rpmbuild/BUILD/${name}/
}
exec cp -f ../LICENSE $env(HOME)/rpmbuild/BUILD/${name}/
cd $env(HOME)/rpmbuild/BUILD
exec tar -cvf ../SOURCES/${name}.tar $name
exec rm -f ../SOURCES/${name}.tar.gz
exec gzip -9 ../SOURCES/${name}.tar
cd ../SPECS
# rpmbuild can write to stderr when it succeeds, so only its exit status counts.
set failed [catch {exec rpmbuild -bb --quiet --clean --target x86_64 \
                 --buildroot $env(HOME)/rpmbuild/BUILDROOT SDDSJava.spec 2>@1} results]
exec rm -f ../SOURCES/${name}.tar.gz
puts $results
if {$failed} {
  puts stderr "rpmbuild failed; no new RPM was made"
  exit 1
}


puts "New RPM file in ~/rpmbuild/RPMS/x86_64"






