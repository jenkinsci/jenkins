#!/bin/bash
echo
echo
echo "Jenkins uninstallation script"
echo
echo "The following commands are executed using sudo, so you need to be logged"
echo "in as an administrator. Please provide your password when prompted."
echo
set -x
sudo launchctl unload /Library/LaunchDaemons/org.jenkins-ci.plist
sudo rm /Library/LaunchDaemons/org.jenkins-ci.plist
sudo rm -rf /Applications/Jenkins "/Library/Application Support/Jenkins" /Library/Documentation/Jenkins
sudo rm -rf /Users/Shared/Jenkins
sudo dscl . -delete /Users/jenkins
sudo dscl . -delete /Groups/jenkins
pkgutil --pkgs | grep 'org\.jenkins-ci\.' | xargs -n 1 sudo pkgutil --forget
set +x
echo
echo "Jenkins has been uninstalled."
