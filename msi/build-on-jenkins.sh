#!/bin/bash -ex
if [ ! -e "$1" ]; then
  echo "Usage: build-on-jenkins path/to/war"
  exit 1
fi
tar cvzf bundle.tgz FindJava.java build.sh jenkins.wxs
java -jar jenkins-cli.jar dist-fork -z bundle.tgz -f jenkins.war="$1" -l windows -Z result.tgz bash -ex build.sh jenkins.war

# hack until we fix distfork to avoid pointless intermediate directory
rm -rf distfork*
tar xvzf result.tgz
mv distfork*/jenkins-*.msi .
