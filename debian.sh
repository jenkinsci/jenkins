#!/bin/sh
# build a debian package from a Maven build
cp war/target/hudson.war hudson.war

exec debuild -us -uc -B
