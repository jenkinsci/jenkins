#!/bin/sh
makepkgs -d repo proto.py
tarpkgs repo hudson | ssh hudson.gotdns.com "cd ips/repository; gunzip | tar xvf -"
ssh hudson.gotdns.com "cd ips; ./start.sh"

