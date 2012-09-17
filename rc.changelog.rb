#!/usr/bin/ruby
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


# Moves the changelog from the trunk section to the release section
#   Usage: rc.changelog.rb < changelog.html > output.html

changelog = []
inside = false;

ARGF.each do |line|
  if /=TRUNK-BEGIN=/ =~ line
    inside = true;
    puts line
    # new template
    puts "<ul class=image>"
    puts "  <li class=>"
    puts "</ul>"
    next
  end
  if /=TRUNK-END=/ =~ line
    inside = false;
    puts line
    next
  end
  if inside
    changelog << line
    next
  end
  if /=RC-CHANGES=/ =~ line
    changelog.each { |line| puts line }
    next
  end
  puts line
end
