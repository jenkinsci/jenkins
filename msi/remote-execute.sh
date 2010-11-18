#!/bin/bash -ex
tar cvzf send.tgz FindJava.java build.sh hudson.wxs
java -jar hudson-cli.jar dist-fork -z send.tgz -l windows -f hudson.war="$1" -Z result.tgz bash build.sh hudson.war
