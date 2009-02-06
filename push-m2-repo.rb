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


ver=ARGV.shift


require 'ftools'

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


print "Releasing master POM for plugins"
prev=VersionNumber.new(ver)
prev.dec()

def updatePom(src,prev,ver)
  open(src) do |i|
    open(src+".tmp","w") do |o|
      i.each do |line|
        line = line.gsub("<version>#{prev}</version>","<version>#{ver}</version>")
        o.puts line
      end
    end
  end
  File.move(src+".tmp",src)
end

Dir.chdir("../plugins") do
  system "svn update"
  # update master POM
  updatePom("pom.xml",prev,ver)
  # update parent reference in module POM
  Dir.glob("*") do |name|
    next unless File.directory?(name)
    print "#{name}\n"
    next unless File.exists?(name+"/pom.xml")
    updatePom(name+"/pom.xml",prev,ver)
  end
  system "svn commit -m 'bumping up POM version'" or fail
  system "mvn -N deploy" or fail
end