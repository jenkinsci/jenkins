#!/bin/sh -e
# The MIT License
# 
# Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.


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