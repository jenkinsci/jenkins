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


# build flashing balls

for sz in 16x16 24x24 32x32 48x48
do
  for color in grey blue yellow red green
  do
    cp $sz/$color.gif ../src/main/webapp/images/$sz/$color.gif
    cp $sz/$color.png ../src/main/webapp/images/$sz/$color.png
    ./makeFlash.sh $sz/$color.gif ../src/main/webapp/images/$sz/${color}_anime.gif
  done

  cp ../src/main/webapp/images/$sz/grey.png ../src/main/webapp/images/$sz/aborted.png 
  cp ../src/main/webapp/images/$sz/grey.png ../src/main/webapp/images/$sz/disabled.png
  cp ../src/main/webapp/images/$sz/grey.png ../src/main/webapp/images/$sz/nobuilt.png
  cp ../src/main/webapp/images/$sz/grey.gif ../src/main/webapp/images/$sz/aborted.gif
  cp ../src/main/webapp/images/$sz/grey.gif ../src/main/webapp/images/$sz/disabled.gif
  cp ../src/main/webapp/images/$sz/grey.gif ../src/main/webapp/images/$sz/nobuilt.gif
  cp ../src/main/webapp/images/$sz/grey_anime.gif ../src/main/webapp/images/$sz/aborted_anime.gif
  cp ../src/main/webapp/images/$sz/grey_anime.gif ../src/main/webapp/images/$sz/disabled_anime.gif
  cp ../src/main/webapp/images/$sz/grey_anime.gif ../src/main/webapp/images/$sz/nobuilt_anime.gif
done
