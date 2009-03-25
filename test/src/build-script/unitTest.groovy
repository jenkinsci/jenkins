// run unit tests
import org.apache.commons.io.FileUtils

ant.project.setBaseDir(project.basedir)

// start from where the core left off, and build from there
ser=new File(project.basedir,"target/cobertura.ser");
FileUtils.copyFile(maven.resolveArtifact("${project.groupId}:hudson-core:${project.version}:cobertura:ser"),ser)

cob = new Cobertura(project,maven,ant,ser);

cob.instrument(["remoting","hudson-core"].collect{ m -> maven.resolveArtifact("${project.groupId}:${m}:${project.version}") })
cob.runTests()
cob.report()
cob.makeBuildFailIfTestFail();
