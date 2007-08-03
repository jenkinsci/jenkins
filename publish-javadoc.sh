#!/bin/bash -xe
#
# publish Hudson javadoc and deploy that into the java.net CVS repository
# 

pushd ../../../www/javadoc
cvs update -Pd
popd

cp -R target/checkout/core/target/site/apidocs/* ../../../www/javadoc

cd ../../../www/javadoc

# ignore everything under CVS, then
# ignore all files that are already in CVS, then
# add the rest of the files
#find . -name CVS -prune -o -exec bash in-cvs.sh {} \; -o \( -print -a -exec cvs add {} \+ \)
rcvsadd . "commiting javadoc"

# sometimes the first commit fails
#cvs commit -m "commit 1 " || cvs commit -m "commit 2"
