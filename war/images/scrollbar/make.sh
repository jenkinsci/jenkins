#!/bin/zsh -ex
KSH_ARRAYS=off

# width of the band
u=20

color="#3465a4"

U=$(($u*2))


make() {
  convert -size ${U}x8 xc:white -fill $color -stroke $color -draw "polyline 0,$(($u-1)) $(($u-1)),0 $(($U-1)),0 $u,$(($u-1))" -roll +$1+0  screen.$1.gif
}

set -A list

for ((  i=0 ; i<$U; i+=1 ))
do
  make $i
  list[$((${#list}+1))]=screen.$i.gif
done

convert -delay 10 ${list[@]} -loop 0 progress-unknown.gif
