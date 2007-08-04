#!/bin/ruby
m2repo="c:/kohsuke/Sun/java.net/m2-repo"
ver=ARGV.shift

print "Pushing to maven repository\n"
Dir.chdir(m2repo) {
  Dir.chdir("org/jvnet/hudson/main") {
	  Dir.glob("*") {|name|
	    next if File.directory?(name)
      print "#{name}\n"
      system "svn add #{ver}" or fail
	  }
	  system "svn commit -m 'Hudson #{ver}'" || fail
  }
}
