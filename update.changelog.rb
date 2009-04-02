#!/bin/ruby
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


# Updates changelog.html
#   Usage: update.changelog.rb <nextVer> < changelog.html > output.html

# version number manipulation class
class VersionNumber
  def initialize(str)
    @tokens = str.split(/\./)
  end
  def inc
    @tokens[-1] = (@tokens[-1].to_i()+1).to_s()
  end
  def dec
    @tokens[-1] = (@tokens[-1].to_i()-1).to_s()
  end
  def to_s
    @tokens.join(".")
  end
end

id=VersionNumber.new(ARGV.shift)
id.inc()


ARGF.each do |line|
  if /=BEGIN=/ =~ line
    puts line
    puts "<a name=v#{id}><h3>What's new in #{id}</h3></a>"
    puts "<!--=RC-CHANGES=-->"
    puts "</div><!--=END=-->"

    next
  end
  if /=END=/ =~ line
    next
  end
  puts line
end
