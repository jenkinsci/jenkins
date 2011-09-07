#!/bin/bash

# Check Usage
if [ -z "$1" ]; then
	echo "Usage: build.sh path/to/jenkins.war"
	exit 1
fi

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

# Stage jenkins.war
cp $1 $(dirname $0)/packages/jenkins/dest_root/Applications/Jenkins/jenkins.war
# Change the Installer title to match the version
sed s,"<title>Jenkins CI Server</title>","<title>${PKG_TITLE}</title>", distribution.xml >distribution-title.xml

# Run pkgbuild for each sub-package
# To verify each sub-package, run "lsbom `pkgutil --bom child.pkg`".
# This will print out the destination for the files in each package along with
# the permissions for each file
PACKAGE_PATHS=""
for CHILD in `ls packages`; do
	pushd packages/$CHILD > /dev/null
	COMMANDSTRING=""
	# If there are package scripts
	if [ -d ./scripts ]; then
		COMMANDSTRING="${COMMANDSTRING} --scripts scripts"
	fi
	# If we have a specific version for this package
	if [ -f ./version ]; then
		PKG_VERSION=`cat ./version`
		COMMANDSTRING="${COMMANDSTRING} --version ${PKG_VERSION}"
	fi
	# Build the package
	pkgbuild \
		--root dest_root \
		--identifier `cat ./identifier` \
		--ownership recommended \
		$CHILD.pkg
	PACKAGE_PATHS="--package-path ${PWD} ${PACKAGE_PATHS}"
	popd > /dev/null
done

# Now build the pretty meta-package
productbuild \
	--distribution distribution-title.xml \
	$PACKAGE_PATHS \
	--resources resources \
	--version $version \
	$PKG_NAME

# Clean up
rm distribution-title.xml