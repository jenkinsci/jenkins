// displays one line HTML summary of the build, which includes the difference
// from the previous build
//
// Usage: <buildStatusSummary build="${...}" />

jelly {
  def s = build.getBuildStatusSummary();
  if(s.isWorse) {
    output.write("<font color=red>${s.message}</font>");
  } else {
    output.write(s.message);
  }
}
