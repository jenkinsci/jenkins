#!/bin/bash -e
# take multiple PNG files in the command line, and convert them to animation gif in the white background
# then send it to stdout
i=0
tmpbase=/tmp/png2gifanime$$

for f in "$@"
do
  convert $f \( +clone -fill white -draw 'color 0,0 reset' \) \
         -compose Dst_Over $tmpbase$i.gif
  fileList[$i]=$tmpbase$i.gif
  i=$((i+1))
done

convert -delay 10 ${fileList[@]} -loop 0 "${tmpbase}final.gif"
cat ${tmpbase}final.gif
rm ${fileList[@]} ${tmpbase}final.gif
