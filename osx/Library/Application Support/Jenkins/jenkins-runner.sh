#!/bin/bash
#
# Startup script used by Jenkins launchd job.
# Mac OS X launchd process calls this script to customize
# the java process command line used to run Jenkins.
# 
# Customizable parameters are found in
# /Library/Preferences/org.jenkins-ci.plist
#
# You can manipulate it using the "defaults" utility.
# See "man defaults" for details.

defaults="defaults read /Library/Preferences/org.jenkins-ci"


war=`$defaults war` || war="/Applications/Jenkins/jenkins.war"

javaArgs=""
heapSize=`$defaults heapSize` && javaArgs="$javaArgs -Xmx${heapSize}"

home=`$defaults JENKINS_HOME` && export JENKINS_HOME="$home"

# Prepare and unlock login keychain
keychain="$home/Library/Keychains/login.keychain"
if [ -f "$keychain" ]; then
    unlock=`$defaults unlockPassword`
    if [ "X$unlock" != "X" ]; then
	echo "Unlocking Keychain"
	security list-keychain -s "$keychain"
	security login-keychain -d user -s "$keychain"
	security unlock-keychain -p "$unlock" "$keychain" || echo "Failed to unlock Keychain."
    fi
fi

exit 0

add_to_args() {
    val=`$defaults $1` && args="$args --${1}=${val}"
}

args=""
add_to_args prefix
add_to_args httpPort
add_to_args httpListenAddress
add_to_args httpsPort
add_to_args httpsListenAddress
add_to_args ajp13Port
add_to_args ajp13ListenAddress

echo "JENKINS_HOME=$JENKINS_HOME"
echo "Jenkins command line for execution:"
echo /usr/bin/java $javaArgs -jar "$war" $args
exec /usr/bin/java $javaArgs -jar "$war" $args
