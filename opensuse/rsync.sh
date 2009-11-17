#!/bin/bash -ex
createrepo .
GPG_AGENT_INFO= ./repomd-sign $(cat ~/.gpg.passphrase) repodata/repomd.xml
cp hudson-ci.org.key repodata/repomd.xml.key
rsync -avz *.key readme.html hudson.repo RPMS repodata hudson-ci.org:~/www/hudson-ci.org/opensuse
