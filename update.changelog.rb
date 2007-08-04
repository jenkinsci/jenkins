#!/bin/ruby
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
