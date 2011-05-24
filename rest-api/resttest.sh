#!/bin/bash

set -e

CUR_DIR=$(dirname $(readlink -f $0))

pushd ${CUR_DIR}/..
mvn install -DskipTests=true -Dlicense.disableCheck
popd

pushd ${CUR_DIR}/../war
mvn install -DskipTests=true -Dlicense.disableCheck

export HUDSON_HOME="/tmp/hudson"
export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n"
mvn hudson-dev:run -e

popd
