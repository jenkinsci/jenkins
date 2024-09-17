#!/usr/bin/bash
set -o errexit
set -o nounset
set -o pipefail
set -o xtrace
cd "$(dirname "$0")"

# https://github.com/jenkinsci/acceptance-test-harness/releases
export ATH_VERSION=5997.v2a_1a_696620a_0

if [[ $# -eq 0 ]]; then
	export JDK=17
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

# obtain the groupId to grant to access the docker socket to run tests needing docker
dockergid=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ubuntu:noble stat -c %g /var/run/docker.sock)

exec docker run --rm \
	--env JDK \
	--env ATH_VERSION \
	--env BROWSER \
	--shm-size 2g `# avoid selenium.WebDriverException exceptions like 'Failed to decode response from marionette' and webdriver closed` \
	--group-add ${dockergid} \
	--volume "$(pwd)"/war/target/jenkins.war:/jenkins.war:ro \
	--volume /var/run/docker.sock:/var/run/docker.sock:rw \
	--volume "$(pwd)"/target/ath-reports:/reports:rw \
	--interactive \
	jenkins/ath:"$ATH_VERSION" \
	bash <<-'INSIDE'
		set -o errexit
		set -o nounset
		set -o pipefail
		set -o xtrace
		cd
		set-java.sh "${JDK}"
		# Start the VNC system provided by the image from the default user home directory
		eval "$(vnc.sh)"
		env | sort
		git clone --branch "$ATH_VERSION" --depth 1 https://github.com/jenkinsci/acceptance-test-harness
		cd acceptance-test-harness
		run.sh "$BROWSER" /jenkins.war \
			-Dmaven.test.failure.ignore \
			-DforkCount=1 \
			-Dgroups=org.jenkinsci.test.acceptance.junit.SmokeTest
		cp --verbose target/surefire-reports/TEST-*.xml /reports
	INSIDE
