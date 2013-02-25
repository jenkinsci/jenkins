#!/bin/bash -ex
if [ "$2" == "" ]; then
  echo "Usage: build-on-jenkins path/to/war path/to/output/msi"
  exit 1
fi
tar cvzf bundle.tgz FindJava.java build.sh jenkins.wxs
v=$(unzip -p "$1" META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr -d '\r')
java -jar jenkins-cli.jar dist-fork -z bundle.tgz -f jenkins.war="$1" -l windows -F "jenkins-$v.msi=$2" bash -ex build.sh jenkins.war

