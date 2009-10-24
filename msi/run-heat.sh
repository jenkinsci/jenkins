#!/bin/bash -ex
heat dir data -o data.wxs -sfrag -gg -cg DataComponents -dr HudsonDir -var var.DataDir
candle -dVERSION=1.100 -dDataDir=data -ext WixUIExtension -ext WixUtilExtension hudson.wxs data.wxs
light -o hudson-1.msi -ext WixUIExtension -ext WixUtilExtension hudson.wixobj data.wixobj

#candle -dVERSION=1.101 -dDataDir=data -ext WixUIExtension -ext WixUtilExtension hudson.wxs data.wxs
#light -o hudson-2.msi -ext WixUIExtension -ext WixUtilExtension hudson.wixobj data.wixobj
