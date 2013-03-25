#!/bin/bash -ex
export PATH=~/tools/native/wix:$PATH

war="$1"
if [ ! -e "$war" ]; then
  echo "build.sh path/to/jenkins.war"
  exit 1
fi

rm -rf tmp || true
mkdir tmp || true
unzip -p "$war" 'WEB-INF/lib/jenkins-core-*.jar' > tmp/core.jar
unzip -p tmp/core.jar windows-service/jenkins.exe        > tmp/jenkins.exe
unzip -p tmp/core.jar windows-service/jenkins.exe.config > tmp/jenkins.exe.config
unzip -p tmp/core.jar windows-service/jenkins.xml        > tmp/jenkins.xm_
# replace executable name to the bundled JRE
sed -e 's|executable.*|executable>%BASE%\\jre\\bin\\java</executable>|' < tmp/jenkins.xm_ > tmp/jenkins.xml

# capture JRE
javac FindJava.java
JREDIR=$(java -cp . FindJava)
echo "JRE=$JREDIR"
heat dir "$JREDIR" -o jre.wxs -sfrag -sreg -nologo -srd -gg -cg JreComponents -dr JreDir -var var.JreDir

# version
v=$(unzip -p "$war" META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr -d '\r')
echo version=$v

candle -dVERSION=$v -dJreDir="$JREDIR" -dWAR="$war" -nologo -ext WixUIExtension -ext WixUtilExtension jenkins.wxs jre.wxs
# '-sval' skips validation. without this, light somehow doesn't work on automated build environment
light -o jenkins-$v.msi -sval -nologo -dcl:high -ext WixUIExtension -ext WixUtilExtension jenkins.wixobj jre.wixobj

# avoid bringing back files that we don't care
rm -rf tmp *.class *.wixpdb *.wixobj
