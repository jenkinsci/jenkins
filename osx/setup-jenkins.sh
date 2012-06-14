#!/bin/bash

cat <<EOM
*****************************************
Jenkins @VERSION@ setup

Setup requires admin privileges. 
Please enter your password when prompted.
*****************************************
EOM

asdir="/Library/Application Support/Jenkins"
dir=$(dirname "$0")
if [ "$dir" = "$asdir" ]; then
    inplace=true
else
    inplace=false
fi

JENKINS_PLIST="/Library/LaunchDaemons/org.jenkins-ci.plist"

echo "Stopping Jenkins. (It is OK for this to fail.)"
sudo launchctl unload "$JENKINS_PLIST"

if ! $inplace; then
    sudo rm -rf "$asdir"
    sudo mkdir -m 755 -p "$asdir"
    sudo install -o root -g wheel -m 644 \
    "$dir/jenkins.war" \
    "$dir/command-line-preferences.html" \
    "$dir/org.jenkins-ci.plist" \
    "$asdir"
    
    sudo install -o root -g wheel -m 755 \
    "$dir/jenkins-runner.sh" \
    "$dir/setup-jenkins" \
    "$asdir"
fi

JENKINS_HOMEDIR="/Users/Shared/Jenkins"
mkdir -p $JENKINS_HOMEDIR

while true; do
    read -p "Do you want Jenkins to start automatically at boot? [y|n] " answer
    case "$answer" in
    Y*|y*) install_launchd_plist=true ; break ;;
    N*|n*) install_launchd_plist=false; break ;;
    esac
done

read -p "Which account will be used to run Jenkins? " jenkins_user
if [ -n "$jenkins_user" ]; then
    if ! dscl . -list "/Users/$jenkins_user" >/dev/null 2>&1; then
        while true; do
            read -p "$jenkins_user does not exist. Create it? [y|n] " answer
            case "$answer" in
            Y*|y*) create_jenkins_user=true ; break ;;
            N*|n*) create_jenkins_user=false; break ;;
            esac
        done
        if $create_jenkins_user; then
            # Find free uid under 500
            uid=$(dscl . -list /Users uid | sort -nrk 2 | awk '$2 < 500 {print $2 + 1; exit 0}')
            if [ $uid -eq 500 ]; then
                echo 'ERROR: All system uids are in use!'
                exit 1
            fi
            echo "Using uid $uid for $jenkins_user"
            
            gid=$uid
            while dscl -search /Groups gid $gid | grep -q $gid; do
                echo "gid $gid is not free, trying next"
                gid=$(($gid + 1))
            done
            echo "Using gid $gid for $jenkins_user"
            
            sudo dscl . -create /Groups/$jenkins_user PrimaryGroupID $gid
            
            sudo dscl . -create /Users/$jenkins_user UserShell /usr/bin/false
            sudo dscl . -create /Users/$jenkins_user Password '*'
            sudo dscl . -create /Users/$jenkins_user UniqueID $uid
            sudo dscl . -create /Users/$jenkins_user PrimaryGroupID $gid
            sudo dscl . -create /Users/$jenkins_user NFSHomeDirectory "$JENKINS_HOMEDIR"
            
            sudo dscl . -append /Groups/$jenkins_user GroupMembership $jenkins_user
        else
            echo "You need to create user $jenkins_user yourself."
        fi
    fi
    
    if dscl . -list "/Users/$jenkins_user" >/dev/null 2>&1; then
        sudo find "$JENKINS_HOMEDIR" \( -not -user $jenkins_user -or -not -group $jenkins_user \) -print0 | sudo xargs -0 chown $jenkins_user:$jenkins_user
    fi
else
    echo "No value provided. You need to set up user and permissions yourself."    
fi

cat <<EOM

Jenkins has been installed into $asdir.
JENKINS_HOME is $JENKINS_HOMEDIR/Home.
Please read $asdir/command-line-preferences.html
EOM

if $install_launchd_plist; then
    plist=$(mktemp -t jenkins_plist) || exit 1
    sed -e "s/@JENKINS_USER@/$jenkins_user/" "$(dirname "$0")"/org.jenkins-ci.plist > "$plist"  
    sudo install -b -o root -g wheel -m 644 "$plist" "$JENKINS_PLIST"
    sudo launchctl load -w "$JENKINS_PLIST"
    # Wait for port 8080 to start accepting connections.
    # But don't wait forever.
    echo "Waiting for Jenkins to start listening to port 8080"
    timeout=$(($(date +%s) + 60))
    while [ $(date +%s) -lt $timeout ] && ! curl -s http://localhost:8080 >/dev/null; do
        echo -n .
        sleep 1
    done
    echo
    if [ $(date +%s) -ge $timeout ]; then
        echo "Timed out waiting for Jenkins port 8080 to start listening!"
        echo "Either Jenkins did not load or this system is very slow."
    else
        echo "Jenkins is up and running in port 8080."
    fi
else
    test -f "$JENKINS_PLIST" && echo "$JENKINS_PLIST has not been changed."
fi

cat <<EOM

This setup script has been installed into 
$asdir/setup-jenkins
You can run it again if you want to change the answers you gave above.

EOM
