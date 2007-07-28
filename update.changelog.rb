#!/bin/ruby
# Updates changelog.html
#   Usage: update.changelog.rb <nextVer> < changelog.html > output.html
id=ARGV.shift

ARGF.each do |line|
  if /=BEGIN=/ =~ line
    puts line
    puts "<a name=v#{id}><h3>What's new in 1.#{id}</h3></a>"
    puts "<ul class=image>"
    puts "  <li class=>"
    puts "</ul>"
    puts "</div><!--=END=-->"

    next
  end
  if /=END=/ =~ line
    next
  end
  puts line
end