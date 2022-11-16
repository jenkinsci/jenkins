#!/usr/bin/env bash
set -euxo pipefail
cd $(dirname $0)

# https://github.com/jenkinsci/acceptance-test-harness/releases
ATH_VERSION=5458.v911b_2f0818ee

# TODO use Artifactory proxy?

[ -f war/target/jenkins.war ] || mvn -B -ntp -Pquick-build -am -pl war package

mkdir -p target/ath-reports
chmod a+rwx target/ath-reports

docker run --rm \
  -e ATH_VERSION=$ATH_VERSION \
  --shm-size 2g `# avoid selenium.WebDriverException exceptions like 'Failed to decode response from marionette' and webdriver closed` \
  -v $(pwd)/war/target/jenkins.war:/jenkins.war \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $(pwd)/target/ath-reports:/reports \
  jenkins/ath:$ATH_VERSION \
  bash -c 'cd && eval $(vnc.sh) && env | sort && git clone -b $ATH_VERSION --depth 1 https://github.com/jenkinsci/acceptance-test-harness && cd acceptance-test-harness && run.sh firefox /jenkins.war -Dmaven.test.failure.ignore -DforkCount=1 -Dgroups=org.jenkinsci.test.acceptance.junit.SmokeTest && cp -v target/surefire-reports/TEST-*.xml /reports'
