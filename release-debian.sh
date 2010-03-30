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


# build a debian package from a release build
ver=$1

cp target/checkout/war/target/hudson.war hudson.war
(cat << EOF
hudson ($ver) unstable; urgency=low

  * See http://hudson.dev.java.net/changelog.html for more details.

 -- Kohsuke Kawaguchi <kk@kohsuke.org>  $(date -R)

EOF
cat debian/changelog ) > debian/changelog.tmp
mv debian/changelog.tmp debian/changelog

# build the debian package
sudo apt-get install -y devscripts || true
debuild -us -uc -B
scp ../hudson_${ver}_all.deb hudson-ci.org:~/public_html_hudson/debian/binary

# build package index
# see http://wiki.debian.org/SecureApt for more details
pushd ..
mkdir binary > /dev/null 2>&1 || true
mv hudson_${ver}_all.deb binary
sudo apt-get install apt-utils
apt-ftparchive packages binary | tee binary/Packages | gzip -9c > binary/Packages.gz
apt-ftparchive contents binary | gzip -9c > binary/Contents.gz
apt-ftparchive -c main/debian/release.conf release  binary > binary/Release
# sign the release file
rm binary/Release.gpg || true
gpg --no-use-agent --passphrase-file ~/.gpg.passphrase -abs -o binary/Release.gpg binary/Release
scp binary/Packages.gz binary/Release binary/Release.gpg binary/Contents.gz hudson-ci.org:~/public_html_hudson/debian/binary
popd
