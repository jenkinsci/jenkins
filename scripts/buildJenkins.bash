#!/bin/bash

set -e    # Abort script at first error
set -u    # Attempt to use undefined variable outputs error message, and forces an exit

# https://maven.apache.org/maven-release/maven-release-plugin/perform-mojo.html
# mvn -Prelease help:active-profiles

# Temporary
WORKSPACE="/tmp"

: "${BRANCH_NAME:=experimental}"
: "${GIT_REPOSITORY:=scm:git:git://github.com/jenkinsci/jenkins.git}"
: "${GIT_EMAIL:=jenkins-bot@example.com}"
: "${GIT_NAME:=jenkins-bot}"
: "${GPG_KEYNAME:=test-jenkins-release}"
: "${SIGN_ALIAS:=jenkins}"
: "${SIGN_KEYSTORE:=${WORKSPACE}/jenkins.pfx}"
: "${SIGN_CERTIFICATE:=jenkins.pem}"
: "${MAVEN_PROFILE:=release}"
: "${MAVEN_REPOSITORY_USERNAME:=jenkins-bot}"
: "${MAVEN_REPOSITORY_URL:=http://nexus/repository}"

export GIT_REPOSITORY
export GIT_EMAIL
export GIT_NAME
export GPG_KEYNAME
export GPG_PASSPHRASE
export MAVEN_PROFILE
export MAVEN_REPOSITORY_PASSWORD
export MAVEN_REPOSITORY_URL
export MAVEN_REPOSITORY_USERNAME
export SIGN_ALIAS
export SIGN_KEYSTORE
export SIGN_STOREPASS
export SIGN_CERTIFICATE

function requireRepositoryPassword(){
  : "${MAVEN_REPOSITORY_PASSWORD:?Repository Password Missing}"
}

function requireGPGPassphrase(){
  : "${GPG_PASSPHRASE:?GPG Passphrase Required}" # Password must be the same for gpg agent and gpg key
}

function requireKeystorePass(){
  : "${SIGN_STOREPASS:?pass}"
}

function clean(){
    mvn -P"${MAVEN_PROFILE}" -s settings-release.xml -B  release:clean
}

function configureGit(){
  git checkout "${BRANCH_NAME}"
  git config --local user.email "${GIT_EMAIL}"
  git config --local user.name "${GIT_NAME}"
}

function configureGPG(){ 
  requireGPGPassphrase
  if ! gpg --fingerprint "${GPG_KEYNAME}"; then
    if [ ! -f "${GPG_FILE}" ]; then
      exit "${GPG_KEYNAME} or ${GPG_FILE} cannot be found"
    else
      ## --pinenty-mode is needed to avoid gpg prompt during maven release
      gpg --import --batch "${GPG_FILE}"
    fi
  fi
}


function configureKeystore(){
  requireKeystorePass
  if [ ! -f ${SIGN_CERTIFICATE} ]; then
      exit "${SIGN_CERTIFICATE} not found"
  else
    openssl pkcs12 -export \
      -out $SIGN_KEYSTORE \
      -in ${SIGN_CERTIFICATE} \
      -password pass:$SIGN_STOREPASS \
      -name $SIGN_ALIAS
  fi
}

function generateSettingsXml(){
requireRepositoryPassword
cat <<EOT> settings-release.xml
<settings>
  <mirrors>
    <mirror>
      <id>mirror-jenkins-public</id>
      <url>http://nexus/repository/jenkins-public/</url>
      <mirrorOf>repo.jenkins-ci.org</mirrorOf>
    </mirror>
  </mirrors>
  <servers>
    <server>
      <id>releases-snapshots</id>
      <username>$MAVEN_REPOSITORY_USERNAME</username>
      <password>$MAVEN_REPOSITORY_PASSWORD</password>
    </server>
    <server>
      <id>releases</id>
      <username>$MAVEN_REPOSITORY_USERNAME</username>
      <password>$MAVEN_REPOSITORY_PASSWORD</password>
    </server>
    <server>
      <id>mirror-jenkins-public</id>
      <username>$MAVEN_REPOSITORY_USERNAME</username>
      <password>$MAVEN_REPOSITORY_PASSWORD</password>
    </server>
  </servers>

</settings>
EOT
}


function prepareRelease(){
  requireGPGPassphrase
  requireKeystorePass
  printf "\\n Prepare Jenkins Release\\n\\n"
  mvn -P"${MAVEN_PROFILE}" -s settings-release.xml -B release:prepare
}

function performRelease(){
  requireGPGPassphrase
  requireKeystorePass
  printf "\\n Perform Jenkins Release\\n\\n"
  mvn -P"${MAVEN_PROFILE}" -s settings-release.xml -B release:perform
}

function validateKeystore(){
  requireKeystorePass
  keytool -keystore "${SIGN_KEYSTORE}" -storepass "${SIGN_STOREPASS}" -list -alias "${SIGN_ALIAS}"
}
function validateGPG(){
  true
}

function main(){
  if [ $# -eq 0 ] ;then
    configureGPG
    configureKeystore
    configureGit
    validateKeystore
    validateGPG
    generateSettingsXml
    prepareRelease
    performRelease
  else
    while [ $# -gt 0 ];
    do
      case "$1" in
            --cleanRelease) echo "Clean Release" && generateSettingsXml && clean;;
            --configureGPG) echo "ConfigureGPG" && configureGPG ;;
            --configureKeystore) echo "Configure Keystore" && configureKeystore ;;
            --configureGit) echo "Configure Git" && configureGit ;;
            --validateKeystore) echo "Validate Keystore"  && validateKeystore ;;
            --validateGPG) echo "Validate GPG" && validateGPG ;;
            --prepareRelease) echo "Prepare Release" && generateSettingsXml && prepareRelease ;;
            --performRelease) echo "Perform Release" && performRelease ;;
            -h) echo "help" ;;
            -*) echo "help" ;;
        esac
        shift
    done
  fi
}

main "$@"
