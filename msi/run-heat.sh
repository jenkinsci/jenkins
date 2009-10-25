#!/bin/bash -ex
# capture JRE
JREDIR='c:\Program Files\Java\jre6'
heat dir "$JREDIR" -o jre.wxs -sfrag -sreg -nologo -srd -gg -cg JreComponents -dr JreDir -var var.JreDir

for v in 1.100 1.101
do
  candle -dVERSION=$v -dJreDir="$JREDIR" -nologo -ext WixUIExtension -ext WixUtilExtension hudson.wxs jre.wxs
  light -o hudson-$v.msi -nologo -ext WixUIExtension -ext WixUtilExtension hudson.wixobj jre.wixobj
done
