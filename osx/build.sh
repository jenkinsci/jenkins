#!/bin/bash -e

# Usage
if [ -z "$1" ]; then
	echo "Usage: build.sh path/to/jenkins.war"
	exit 1
fi

# Set up build tools
makeself=$(dirname $0)/makeself/makeself.sh

# Get the Jenkins version number
cp "$1" $(dirname $0)/jenkins.war.tmp
if [ -z "$2" ]; then
  version=$(unzip -p $(dirname $0)/jenkins.war.tmp META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr -d '\r' | sed -e "s/-SNAPSHOT//" | tr - . )
else
  version="$2"
fi
echo Version is $version
PKG_NAME="jenkins-${version}.command"
PKG_TITLE="Jenkins ${version}"
rm $(dirname $0)/jenkins.war.tmp

pkgroot=$(mktemp -d -t jenkins) || exit 1

cp "$1" jenkins-runner.sh command-line-preferences.html org.jenkins-ci.plist \
"$pkgroot"

sed -e "s/@VERSION@/$version/" setup-jenkins.sh > "$pkgroot"/setup-jenkins
chmod 755 "$pkgroot"/setup-jenkins

# makeself  archivedir archivename title        startupscript
"$makeself" "$pkgroot" "$PKG_NAME" "$PKG_TITLE" ./setup-jenkins

rm -rf "$pkgroot"
