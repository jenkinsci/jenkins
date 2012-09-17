#!/bin/bash -ex
tar cvzf send.tgz FindJava.java build.sh jenkins.wxs
java -jar jenkins-cli.jar dist-fork -z send.tgz -l windows -f jenkins.war="$1" -Z result.tgz bash build.sh jenkins.war
