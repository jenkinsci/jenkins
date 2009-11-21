#!/bin/bash -ex
createrepo .
gpg -a --detach-sign --yes --no-use-agent --passphrase-file ~/.gpg.passphrase repodata/repomd.xml
cp hudson-ci.org.key repodata/repomd.xml.key
rsync -avz *.key readme.html RPMS repodata hudson-ci.org:~/www/hudson-ci.org/opensuse
