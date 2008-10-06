#!/bin/sh -ex
# build flashing balls

t=/tmp/makeBalls$$

for sz in 16x16 24x24 32x32 48x48
do
  for color in grey blue yellow red green
  do
    cp $sz/$color.gif ../resources/images/$sz/$color.gif
    convert $sz/$color.gif -fill white -colorize 20% $t.80.gif
    convert $sz/$color.gif -fill white -colorize 40% $t.60.gif
    convert $sz/$color.gif -fill white -colorize 60% $t.40.gif
    convert $sz/$color.gif -fill white -colorize 80% $t.20.gif
    convert -delay 10 $sz/$color.gif $t.80.gif $t.60.gif $t.40.gif $t.20.gif $sz/nothing.gif $t.20.gif $t.40.gif $t.60.gif $t.80.gif -loop 0 ../resources/images/$sz/${color}_anime.gif
  done
done

rm $t.*.gif
