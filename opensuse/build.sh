#!/bin/bash -e
if [ -z "$1" ]; then
  echo "Usage: build.sh path/to/hudson.war"
  exit 1
fi

# figure out the version to package
cp "$1" $(dirname $0)/SOURCES/hudson.war
pushd $(dirname $0)
version=$(unzip -p SOURCES/hudson.war META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr - .)
echo Version is $version

# prepare fresh directories
rm -rf BUILD RPMS SRPMS tmp || true
mkdir -p BUILD RPMS SRPMS

# real action happens here
rpmbuild -ba --define="_topdir $PWD" --define="_tmppath $PWD/tmp" --define="ver $version" SPECS/hudson.spec
