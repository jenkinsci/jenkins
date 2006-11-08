#!/bin/bash -x
#
# Kohsuke's automation script to publish javadoc to java.net CVS repository.
# Checked in only because Maven doesn't let me release it unless I have it in CVS.
# 

cd ../www/javadoc
cvs update -Pd

cp -R ../../hudson/build/javadoc/* .

# ignore everything under CVS, then
# ignore all files that are already in CVS, then
# add the rest of the files
find . -name CVS -prune -o -exec bash in-cvs.sh {} \; -o \( -print -a -exec cvs add {} \+ \)

# sometimes the first commit fails
cvs commit -m "commit 1 " || cvs commit -m "commit 2"
