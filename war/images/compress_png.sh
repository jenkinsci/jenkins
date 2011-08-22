#!/bin/bash

function compress {
  echo "Crushing $1"
  pngcrush -rem gAMA -rem cHRM -rem iCCP -rem sRGB $1 $1.tmp
  mv $1.tmp $1
  echo "Compressing $1"
  optipng -o9 $1 > /dev/null
}

if [ -z "$*" ]
then
  echo 'Please pass a list of pngs to compress.';
fi

for image in "$@"  # Doesn't work properly if "$*" isn't quoted.
do
  compress $image
done

