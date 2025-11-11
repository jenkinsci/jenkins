#!/usr/bin/bash
set -o errexit
set -o nounset
set -o pipefail
set -o xtrace
cd "$(dirname "$0")"

# https://github.com/jenkinsci/acceptance-test-harness/releases
export ATH_VERSION=6446.v64eb_f0dfb_26d

if [[ $# -eq 0 ]]; then
	export JDK=21
	export BROWSER=firefox
else
	export JDK=$1
	export BROWSER=$2
fi

MVN='mvn -B -ntp -Pquick-build -am -pl war package'
if [[ -n ${MAVEN_SETTINGS-} ]]; then
	MVN="${MVN} -s ${MAVEN_SETTINGS}"
fi

[[ -f war/target/jenkins.war ]] || $MVN

mkdir -p target/ath-reports
chmod a+rwx target/ath-reports

curl \
	--fail \
	--silent \
	--show-error \
	--output /tmp/ath.yml \
	--location "https://raw.githubusercontent.com/jenkinsci/acceptance-test-harness/refs/tags/${ATH_VERSION}/docker-compose.yml"

sed -i -e "s/jenkins\/ath:latest/jenkins\/ath:${ATH_VERSION}/g" /tmp/ath.yml

# obtain the groupId to grant to access the docker socket to run tests needing docker
if [[ -z ${DOCKER_GID:-} ]]; then
	DOCKER_GID=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ubuntu:noble stat -c %g /var/run/docker.sock) || exit 1
	export DOCKER_GID
fi

trap 'docker-compose --file /tmp/ath.yml kill && docker-compose --file /tmp/ath.yml down' EXIT

exec docker-compose \
	--file /tmp/ath.yml \
	run \
	--env JDK \
	--env ATH_VERSION \
	--env BROWSER \
	--name mvn \
	--no-TTY \
	--rm \
	--volume "$(pwd)"/war/target/jenkins.war:/jenkins.war:ro \
	--volume "$(pwd)"/target/ath-reports:/reports:rw \
	mvn \
	bash <<-'INSIDE'
		set -o errexit
		set -o nounset
		set -o pipefail
		set -o xtrace
		cd
		set-java.sh "${JDK}"
		env | sort
		git clone --branch "${ATH_VERSION}" --depth 1 https://github.com/jenkinsci/acceptance-test-harness
		cd acceptance-test-harness
		run.sh "remote-webdriver-${BROWSER}" /jenkins.war \
			-Dmaven.test.failure.ignore \
			-DforkCount=1 \
			-Dgroups=org.jenkinsci.test.acceptance.junit.SmokeTest
		cp --verbose target/surefire-reports/TEST-*.xml /reports
	INSIDE
