#!/bin/bash -ex
# import JRCS jars to the .m2 repo

v=0.4.2

for t in diff rcs
do
  echo $t
  mvn deploy:deploy-file \
    -DgroupId=org.jvnet.hudson \
    -DartifactId=org.suigeneris.jrcs.$t \
    -Dversion=$v \
    -Dpackaging=jar \
    -Dfile=org.suigeneris.jrcs.$t-$v.jar \
    -DrepositoryId=java.net-m2-repository \
    -Durl=file://$JAVANET_M2_REPO
  mvn deploy:deploy-file \
    -DgroupId=org.jvnet.hudson \
    -DartifactId=org.suigeneris.jrcs.$t \
    -Dversion=$v \
    -Dpackaging=jar \
    -Dfile=src.zip \
    -DrepositoryId=java.net-m2-repository \
    -Durl=file://$JAVANET_M2_REPO \
    -Dclassifier=sources
done
