#!/bin/sh  
# \
exec tclsh "$0" "$@"

set version 5.3
set name SDDSJava-$version
puts "Building $name RPM"

exec ./rpmdev-setuptree
exec cp -f SDDSJava.spec $env(HOME)/rpmbuild/SPECS/
exec rm -rf $env(HOME)/rpmbuild/BUILD/$name
exec mkdir $env(HOME)/rpmbuild/BUILD/$name
set binFiles "../SDDSedit/jsddsEdit ../SDDS3Dplot/jsdds3dplot"
foreach f $binFiles {
  exec chmod a+rx $f
  exec chmod a-w $f
  exec cp -f $f $env(HOME)/rpmbuild/BUILD/${name}/
}
set jarFiles "../../../../javalib/SDDS.jar ../../../../javalib/SDDSedit.jar ../../../../javalib/SDDS3Dplot.jar visad.jar j3d-1_5_2-linux-amd64/lib/ext/j3dcore.jar j3d-1_5_2-linux-amd64/lib/ext/j3dutils.jar j3d-1_5_2-linux-amd64/lib/ext/vecmath.jar j3d-1_5_2-linux-amd64/lib/amd64/libj3dcore-ogl.so"
foreach f $jarFiles {
  exec chmod a+r $f
  exec chmod a-wx $f
  exec cp -f $f $env(HOME)/rpmbuild/BUILD/${name}/
}
cd $env(HOME)/rpmbuild/BUILD
exec tar -cvf ../SOURCES/${name}.tar $name
exec rm -f ../SOURCES/${name}.tar.gz
exec gzip -9 ../SOURCES/${name}.tar
cd ../SPECS
if {[catch {exec rpmbuild -bb --quiet --clean --target x86_64 \
                 --buildroot $env(HOME)/rpmbuild/BUILDROOT SDDSJava.spec} results]} {
}
exec rm -f ../SOURCES/${name}.tar.gz
puts $results


puts "New RPM file in ~/rpmbuild/RPMS/x86_64"






