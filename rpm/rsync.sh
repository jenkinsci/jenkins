#!/bin/bash -ex
sudo apt-get install -y createrepo || true
createrepo .
rsync -avz *.key readme.html hudson.repo RPMS repodata hudson-ci.org:~/www/hudson-ci.org/redhat
