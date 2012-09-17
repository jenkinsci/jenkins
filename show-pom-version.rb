#!/usr/bin/ruby
# parse POM from stdin and prints the version number
require "rexml/document"
require "rexml/xpath"

pom = REXML::Document.new $stdin
# if the POM doesn't define the version by itself, it's inherited from the parent
puts (pom.elements["/project/version"] || pom.elements["/project/parent/version"]).text
