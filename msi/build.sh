#!/bin/bash -ex
rm -rf tmp || true
mkdir tmp || true
unzip -p hudson.war 'WEB-INF/lib/hudson-core-*.jar' > tmp/core.jar
unzip -p tmp/core.jar windows-service/hudson.exe > tmp/hudson.exe
unzip -p tmp/core.jar windows-service/hudson.xml > tmp/hudson.xml
