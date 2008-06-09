#!/bin/bash -ex
#
# Kohsuke's automated release script. Sorry for my checking this in,
# but Maven doesn't let me run release goals unless I have this in CVS.

# make sure we have up to date workspace
svn update
# if left-over hudson.war for Debian build from the last time, delete it.
rm hudson.war || true

tag=hudson-$(show-pom-version pom.xml | sed -e "s/-SNAPSHOT//g" -e "s/\\./_/g")
mvn -B -Dtag=$tag release:prepare || mvn -B -Dtag=$tag install release:prepare
mvn release:perform

id=$(show-pom-version target/checkout/pom.xml)
#./publish-javadoc.sh
javanettasks uploadFile hudson /releases/$id                "`date +"%Y/%m/%d"` release" Stable target/checkout/war/target/hudson.war | tee target/war-upload.log
warUrl=$(cat target/war-upload.log | grep "^Posted" | sed -e "s/Posted //g")
javanettasks uploadFile hudson /releases/source-bundles/$id "`date +"%Y/%m/%d"` release" Stable target/checkout/target/hudson-$id-src.zip
javanettasks announce hudson "Hudson $id released" "$warUrl" << EOF
See <a href="https://hudson.dev.java.net/changelog.html">the changelog</a> for details.
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
echo "Redirect 302 /latest/hudson.war $warUrl" > /tmp/latest.htaccess
scp /tmp/latest.htaccess hudson.gotdns.com:/home/kohsuke/public_html_hudson/latest/.htaccess

# update changelog.html
ruby update.changelog.rb $id < $WWW/changelog.html > $WWW/changelog.new
mv $WWW/changelog.new $WWW/changelog.html

# push changes to the maven repository
ruby push-m2-repo.rb $id

chmod u+x publish-javadoc.sh
./publish-javadoc.sh

chmod u+x release-debian.sh
./release-debian.sh $id
svn commit -m "updated changelog as a part of the release" debian/changelog

cd $WWW
svn commit -m "Hudson $id released" changelog.html hudson.jnlp
