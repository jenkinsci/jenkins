#!/bin/sh -ex
# build flashing balls

for sz in 16x16 24x24 32x32 48x48
do
  for color in grey blue yellow red green
  do
    cp $sz/$color.gif ../resources/images/$sz/$color.gif
    ./makeFlash.sh $sz/$color.gif ../resources/images/$sz/${color}_anime.gif
  done
done
