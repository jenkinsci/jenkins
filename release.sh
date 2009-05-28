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
# Kohsuke's automated release script. Sorry for my checking this in,
# but Maven doesn't let me run release goals unless I have this in CVS.

# make sure we have up to date workspace
svn update
# if left-over hudson.war for Debian build from the last time, delete it.
rm hudson.war || true

tag=hudson-$(show-pom-version pom.xml | sed -e "s/-SNAPSHOT//g" -e "s/\\./_/g")
export MAVEN_OPTS="-Xmx512m -XX:MaxPermSize=256m"
mvn -B -Dtag=$tag -DskipTests release:prepare || mvn -B -Dtag=$tag -DskipTests install release:prepare || true
#svn up -r head
#mvn -B -Dtag=$tag -Dresume release:prepare
mvn release:perform

id=$(show-pom-version target/checkout/pom.xml)
case $id in
*-SNAPSHOT)
	echo Trying to release a SNAPSHOT
	exit 1
	;;
esac
javanettasks uploadFile hudson /releases/$id                "`date +"%Y/%m/%d"` release" Stable target/checkout/war/target/hudson.war | tee target/war-upload.log
warUrl=$(cat target/war-upload.log | grep "^Posted" | sed -e "s/Posted //g")
javanettasks uploadFile hudson /releases/source-bundles/$id "`date +"%Y/%m/%d"` release" Stable target/checkout/target/hudson-$id-src.zip
javanettasks announce hudson "Hudson $id released" "$warUrl" << EOF
See <a href="https://hudson.dev.java.net/changelog.html">the changelog</a> for details.
Download is available from <a href="$warUrl">here</a>.
EOF

# this is for the JNLP start
cp target/checkout/war/target/hudson.war target/checkout/war/target/hudson.jar
javanettasks uploadFile hudson /releases/jnlp/hudson.jar "version $id" Stable target/checkout/war/target/hudson.jar | tee target/upload.log

# replace the jar file link accordingly
WWW=../../../www
pushd $WWW
svn revert -R .
svn update
popd
jarUrl=$(cat target/upload.log | grep "^Posted" | sed -e "s/Posted //g")
perl -p -i.bak -e "s|https://.+hudson\.jar|$jarUrl|" $WWW/hudson.jnlp
cp $WWW/hudson.jnlp $WWW/$id.jnlp

# push the permalink
echo "Redirect 302 /latest/hudson.war $warUrl" > /tmp/latest.htaccess.war
scp /tmp/latest.htaccess.war hudson.gotdns.com:/home/kohsuke/public_html_hudson/latest/.htaccess.war
ssh hudson.gotdns.com "cd /home/kohsuke/public_html_hudson/latest; cat .htaccess.* > .htaccess"

# update changelog.html
ruby update.changelog.rb $id < $WWW/changelog.html > $WWW/changelog.new
mv $WWW/changelog.new $WWW/changelog.html

# push changes to the maven repository
ruby push-m2-repo.rb $id

chmod u+x publish-javadoc.sh
./publish-javadoc.sh

# create and publish debian package
chmod u+x release-debian.sh
./release-debian.sh $id
svn commit -m "updated changelog as a part of the release" debian/changelog

# publish IPS. The server needs to be restarted for it to see the new package.
cat war/target/hudson-war-$id.ipstgz | ssh wsinterop.sun.com "cd ips/repository; gtar xvzf -"
ssh wsinterop.sun.com "cd ips; ./start.sh"

cd $WWW
svn commit -m "Hudson $id released" changelog.html hudson.jnlp
