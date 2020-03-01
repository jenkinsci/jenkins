#!/bin/sh
ln -s ./asset to_internal1
ln -s ./asset/goal.txt to_internal_goal1 

mkdir intermediateFolder
cd intermediateFolder
ln -s ../asset to_internal2
ln -s ../asset/goal.txt to_internal_goal2
