#!/bin/sh
version=$1
if [ ! \( -e winstone-$version.jar -a -e winstone-$version-sources.jar \) ]; then
  echo files not found
  exit 1
fi

cat winstone.pom | sed -e "s/VERSION/$version/" > tmp.pom
mvn -f tmp.pom deploy:deploy-file -Durl=java-net:/maven2-repository/trunk/www/repository/ -DpomFile=tmp.pom -Dfile=winstone-$version.jar
mvn -f tmp.pom deploy:deploy-file -Durl=java-net:/maven2-repository/trunk/www/repository/ -DpomFile=tmp.pom -Dfile=winstone-$version-sources.jar -Dclassifier=sources
rm tmp.pom

