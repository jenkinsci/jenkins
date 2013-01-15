#!/bin/sh -ex
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


# build a glowing animation from two still pictures
# Usage <src gif 1> <src gif 2> <dst gif>
t=/tmp/flash$$

src1=$1
src2=$2
dst=$3
for p1 in 20 40 60 80
do
  p2=`expr 100 - $p1`
  composite -blend ${p1}%x${p2}% "$src1" "$src2" $t.${p1}.gif
done
convert -delay 15 "$src1" $t.80.gif $t.60.gif $t.40.gif $t.20.gif "$src2" $t.20.gif $t.40.gif $t.60.gif $t.80.gif -loop 0 "$dst"

rm $t.*.gif
