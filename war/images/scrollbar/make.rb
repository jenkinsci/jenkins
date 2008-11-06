#!/usr/bin/ruby

# width of the band
$u=20
$uu=$u*2

def make(step,color)
  system "convert -size #{$uu}x8 xc:white -fill '#{color}' -stroke '#{color}' -draw \"polyline 0,#{$u-1} #{$u-1},0 #{$uu-1},0 #{$u},#{$u-1}\" -roll +#{step}+0  screen.#{step}.gif" or fail
end

colors = { "red" => "#cc0000", "blue" => "#3465a4" }
colors.keys.each do |name|
  list = []
  color = colors[name];
  
  (0...$uu).each do |i|
    make(i,color)
    list << "screen.#{i}.gif"
  end

  system "convert -delay 10 #{list.join(" ")} -loop 0 progress-unknown-#{name}.gif" or fail
  
  list.each do |file|
    File.delete(file)
  end
end
