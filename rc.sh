#!/bin/bash -ex
# The MIT License
# 
# Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.


#
# Kohsuke's automated script to create the RC branch.
#

# there shouldn't be any unmerged changes in the RC branch
svnmerge merge -S rc .
pending=$(svn status | grep -v '^?' | wc -l)
if [ $pending != 0 ]; then
  echo "unmerged or uncommitted changes"
  exit 1
fi

# create the release branch
repo=https://www.dev.java.net/svn/hudson
RC=$repo/branches/rc
svn rm -m "deleting the old RC branch" $RC
svn cp -m "creating a new RC branch" $repo/trunk/hudson/main $RC

# update changelog.html
WWW=../../www
svn up $WWW/changelog.html
ruby rc.changelog.rb < $WWW/changelog.html > $WWW/changelog.new
mv $WWW/changelog.new $WWW/changelog.html

cd $WWW
svn commit -m "RC branch is created" changelog.html
