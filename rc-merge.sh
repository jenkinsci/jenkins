#!/bin/bash -ex
# merge back the RC branch.
svn up
svnmerge merge -S /branches/rc .
svn commit -F svnmerge-commit-message.txt
