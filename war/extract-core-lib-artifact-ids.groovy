import java.util.jar.JarFile

// Read probe classes properties from jenkins-core-libs jar to get the list of artifact IDs
def coreLibsArtifact = project.artifacts.find { it.artifactId == 'jenkins-core-libs' }
if (!coreLibsArtifact) {
  throw new Exception("jenkins-core-libs artifact not found in dependencies")
}

def props = new Properties()
def artifactFile = coreLibsArtifact.file

try (def jarFile = new JarFile(artifactFile)) {
  def propsEntry = jarFile.getEntry('META-INF/bundled-libraries.properties')
  if (!propsEntry) {
    throw new Exception("Library properties file not found in jenkins-core-libs JAR")
  }
  jarFile.getInputStream(propsEntry).withStream { props.load(it) }
}

def artifactIds = props.keySet().join(',')
project.properties['core-lib-artifact-ids'] = artifactIds

// Generate regex pattern for packaging excludes (include jenkins-core-libs itself + all artifact IDs)
def allIds = (['jenkins-core-libs'] + props.keySet()).join('|')
project.properties['core-lib-packaging-excludes'] = "%regex[WEB-INF/lib/(" + allIds + ")-.*\\.jar]"
