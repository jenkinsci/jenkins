#!/bin/sh -e
for src in *.svg
do
  echo processing $src
  e=$(echo $src | sed -e s/.svg/.gif/ )
  for sz in 16 24 32 48
  do
    dst=${sz}x${sz}/$e
    if [ ! -e $dst -o $src -nt $dst ];
    then
      mkdir ${sz}x${sz} > /dev/null 2>&1 || true
      svg2png -w $sz -h $sz < $src > t.png
      #convert t.png \( +clone -fill white -draw 'color 0,0 reset' \) \
      #   -compose Dst_Over $dst
      composite -compose Dst_Over -tile xc:white t.png $dst
      rm t.png
    fi
  done
done
