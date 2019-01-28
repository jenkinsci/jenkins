#!/bin/sh
echo 'public-1' > public1.key
ln -s ../../secrets to_secrets1 
ln -s ../../secrets/goal.txt to_secrets_goal1 

mkdir intermediateFolder
cd intermediateFolder
echo 'public-2' > public2.key
ln -s ../../../secrets to_secrets2 
ln -s ../../../secrets/goal.txt to_secrets_goal2 

mkdir otherFolder
cd otherFolder
ln -s ../../../../secrets to_secrets3
