#!/bin/bash -ex
if [ ! -e "$1" ]; then
  echo "Usage: build-on-hudson path/to/war"
  exit 1
fi
if [ "$HUDSON_URL" == "" ]; then
  export HUDSON_URL=http://hudson.sfbay/
fi
tar cvzf bundle.tgz FindJava.java build.sh hudson.wxs
java -jar hudson-cli.jar dist-fork -z bundle.tgz -f hudson.war="$1" -l windows -Z result.tgz bash -ex build.sh hudson.war

# hack until we fix distfork to avoid pointless intermediate directory
rm -rf distfork*
tar xvzf result.tgz
mv distfork*/hudson-*.msi .
