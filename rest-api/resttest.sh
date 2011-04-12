#!/bin/bash

CUR_DIR=$(dirname $(readlink -f $0))

pushd ${CUR_DIR}
mvn install
popd

pushd ${CUR_DIR}/../war
mvn clean install

export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n"
mvn hudson-dev:run -e
popd
