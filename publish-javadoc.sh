#!/bin/bash -xe
#
# publish Hudson javadoc and deploy that into the java.net CVS repository
# 

# generate javadoc
cd core
mvn -o javadoc:javadoc
cd ..

cd ../../www/javadoc
cvs update -Pd

cp -R ../../hudson/main/core/target/site/apidocs/* .

# ignore everything under CVS, then
# ignore all files that are already in CVS, then
# add the rest of the files
find . -name CVS -prune -o -exec bash in-cvs.sh {} \; -o \( -print -a -exec cvs add {} \+ \)

# sometimes the first commit fails
cvs commit -m "commit 1 " || cvs commit -m "commit 2"
