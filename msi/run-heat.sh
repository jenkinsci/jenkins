#!/bin/bash -ex
heat dir data -o data.wxs -sfrag -gg -cg DataComponents -dr HudsonDir -var var.DataDir
candle -dVERSION=1.999 -dDataDir=data hudson.wxs data.wxs
light -o hudson.msi hudson.wixobj data.wixobj
