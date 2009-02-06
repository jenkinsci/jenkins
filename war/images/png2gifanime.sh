#!/bin/bash -e
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