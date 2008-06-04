#!/bin/bash -ex
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
debuild -us -uc -B
scp ../hudson_${ver}_all.deb hudson.gotdns.com:~/public_html_hudson/debian/binary

