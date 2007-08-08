#!/bin/ruby
m2repo="c:/kohsuke/Sun/java.net/m2-repo"
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

def updatePom(src)
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
  # update master POM
  updatePom("pom.xml")
  # update parent reference in module POM
  Dir.glob("*") do |name|
    next unless File.directory?(name)
    print "#{name}\n"
    next unless File.exists(name+"/pom.xml")
    updatePom(name+"/pom.xml")
  done
  system "cvs commit -m 'bumping up POM version'" or fail
  system "mvn -N deploy" or fail
end



print "Pushing to maven repository\n"
Dir.chdir(m2repo) do
  Dir.chdir("org/jvnet/hudson/main") do
	  Dir.glob("*") do |name|
	    next unless File.directory?(name)
      print "#{name}\n"
      system "svn add name/#{ver}" or fail
	  end
	  system "svn commit -m 'Hudson #{ver}'" || fail
  end
  Dir.chdir("org/jvnet/hudson/plugins/plugin") do
    system "svn add #{ver}" or fail
	  system "svn commit -m 'Hudson #{ver}'" || fail
  end
end
