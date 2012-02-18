#!/bin/bash

# Usage
if [ -z "$1" ]; then
	echo "Usage: build.sh path/to/jenkins.war"
	exit 1
fi

# Set up build tools
PACKAGEMAKER_APP=$(mdfind "kMDItemCFBundleIdentifier == com.apple.PackageMaker")
if [ -z "$PACKAGEMAKER_APP" ]; then
    echo "Error: PackageMaker.app not found" >&2
    exit 1
fi

PACKAGEMAKER="${PACKAGEMAKER_APP}/Contents/MacOS/PackageMaker"

# Get the Jenkins version number
cp "$1" $(dirname $0)/jenkins.war.tmp
if [ -z "$2" ]; then
  version=$(unzip -p $(dirname $0)/jenkins.war.tmp META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr -d '\r' | sed -e "s/-SNAPSHOT//" | tr - . )
else
  version="$2"
fi
echo Version is $version
PKG_NAME="jenkins-${version}.pkg"
PKG_TITLE="Jenkins ${version}"
rm $(dirname $0)/jenkins.war.tmp

# Fiddle with the package document so it points to the jenkins.war file provided
PACKAGEMAKER_DOC="$(dirname $0)/JenkinsInstaller.pmdoc"
mv $PACKAGEMAKER_DOC/01jenkins-contents.xml $PACKAGEMAKER_DOC/01jenkins-contents.xml.orig
sed s,"pt=\".*\" m=","pt=\"${1}\" m=",g $PACKAGEMAKER_DOC/01jenkins-contents.xml.orig > $PACKAGEMAKER_DOC/01jenkins-contents.xml
mv $PACKAGEMAKER_DOC/01jenkins.xml $PACKAGEMAKER_DOC/01jenkins.xml.orig
sed s,"<installFrom mod=\"true\">.*</installFrom>","<installFrom mod=\"true\">${1}</installFrom>",g $PACKAGEMAKER_DOC/01jenkins.xml.orig > $PACKAGEMAKER_DOC/01jenkins.xml

# Build the package
"${PACKAGEMAKER}" \
	--doc "${PACKAGEMAKER_DOC}" \
	--out "${PKG_NAME}" \
	--version "${version}" \
	--title "${PKG_TITLE}"

# Reset the fiddling so git doesn't get confused
mv $PACKAGEMAKER_DOC/01jenkins.xml.orig $PACKAGEMAKER_DOC/01jenkins.xml
mv $PACKAGEMAKER_DOC/01jenkins-contents.xml.orig $PACKAGEMAKER_DOC/01jenkins-contents.xml
