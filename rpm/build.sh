#!/bin/bash -e
if [ -z "$1" ]; then
  echo "Usage: build.sh path/to/jenkins.war"
  exit 1
fi

which rpm > /dev/null 2>&1 
if [ $? != 0 ]; then
  sudo apt-get install -y rpm expect || true
fi


# figure out the version to package
cp "$1" $(dirname $0)/SOURCES/jenkins.war
pushd $(dirname $0)
if [ -z "$2" ]; then
  version=$(unzip -p SOURCES/jenkins.war META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr - .)
else
  version="$2"
fi
echo Version is $version

# prepare fresh directories
rm -rf BUILD RPMS SRPMS tmp || true
mkdir -p BUILD RPMS SRPMS

cat SOURCES/jenkins.repo.in | sed -e "s#@URL@#${RPM_URL}/#g" > SOURCES/jenkins.repo

# real action happens here
rpmbuild -ba --define="_topdir $PWD" --define="_tmppath $PWD/tmp" --define="ver $version" SPECS/jenkins.spec
