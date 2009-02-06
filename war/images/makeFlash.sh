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


# build a flashing animation image from a still picture
# Usage <src gif> <dst gif>
t=/tmp/flash$$

src=$1
dst=$2
convert $src -fill white -colorize 20% $t.80.gif
convert $src -fill white -colorize 40% $t.60.gif
convert $src -fill white -colorize 60% $t.40.gif
convert $src -fill white -colorize 80% $t.20.gif
convert $src -fill white -colorize 100% $t.0.gif
convert -delay 10 $src $t.80.gif $t.60.gif $t.40.gif $t.20.gif $t.0.gif $t.20.gif $t.40.gif $t.60.gif $t.80.gif -loop 0 $dst

rm $t.*.gif