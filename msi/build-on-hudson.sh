#!/bin/bash -ex
if [ ! -e "$1" ]; then
  echo "Usage: build-on-hudson path/to/war"
  exit 1
fi
tar cvzf bundle.tgz FindJava.java build.sh hudson.wxs
java -jar hudson-cli.jar dist-fork -z bundle.tgz -f hudson.war="$1" -l windows -Z result.tgz bash build.sh hudson.war
