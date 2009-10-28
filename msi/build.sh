#!/bin/bash -ex
rm -rf tmp || true
mkdir tmp || true
unzip -p hudson.war 'WEB-INF/lib/hudson-core-*.jar' > tmp/core.jar
unzip -p tmp/core.jar windows-service/hudson.exe > tmp/hudson.exe
unzip -p tmp/core.jar windows-service/hudson.xml > tmp/hudson.xm_
# replace executable name to the bundled JRE
sed -e 's|executable.*|executable>%BASE%\\jre\\bin\\java</executable>|' < tmp/hudson.xm_ > tmp/hudson.xml

# capture JRE
JREDIR='c:\Program Files\Java\jre6'
heat dir "$JREDIR" -o jre.wxs -sfrag -sreg -nologo -srd -gg -cg JreComponents -dr JreDir -var var.JreDir

for v in 1.100 1.101
do
  candle -dVERSION=$v -dJreDir="$JREDIR" -nologo -ext WixUIExtension -ext WixUtilExtension hudson.wxs jre.wxs
  light -o hudson-$v.msi -nologo -ext WixUIExtension -ext WixUtilExtension hudson.wixobj jre.wixobj
done
