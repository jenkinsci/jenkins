#!/bin/bash -ex
createrepo .
rsync -avz *.key readme.html hudson.repo RPMS repodata hudson-ci.org:~/www/hudson-ci.org/opensuse
