#!/bin/bash -ex
# merge back the RC branch.
svnmerge init -F https://www.dev.java.net/svn/hudson/branches/rc
svn commit -F svnmerge-commit-message.txt
svnmerge merge -S rc .
svn commit -F svnmerge-commit-message.txt
