#!/usr/bin/ruby
# update the version of Hudson that this POM depends on from $1
require "rexml/document"
require "rexml/xpath"

# version to update to
v=ARGV.shift

pom = REXML::Document.new $stdin
pom.elements.each("//dependency[groupId='org.jvnet.hudson.main']") { |dep|
  dep.elements["version"].text = v
}
pom.write $stdout


