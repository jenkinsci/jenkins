// Read generated dependency list from maven-dependency-plugin output (not packaged in JAR)
// TODO Maybe extract strictBundledArtifacts from maven-hpi-plugin to re-use here?
def depsFile = new File(project.build.directory, 'jenkins-core-libs-dependencies.txt')
if (!depsFile.exists()) {
  throw new Exception("Dependency list file not found: " + depsFile)
}

def actualDependencies = [] as Set
depsFile.eachLine { line ->
  line = line.trim()
  if (!line.isEmpty() && !line.startsWith('#') && !line.startsWith('The following')) {
    // Parse format: groupId:artifactId:type:version:scope
    def parts = line.split(':')
    if (parts.length >= 2) {
      actualDependencies.add(parts[1])
    }
  }
}

// Read properties expected to be included (this file is packaged in JAR and is the source of truth at runtime)
def propsFile = new File(project.build.outputDirectory, 'META-INF/bundled-libraries.properties')
if (!propsFile.exists()) {
  throw new Exception("Libraries list file not found: " + propsFile)
}

def props = new Properties()
propsFile.withInputStream { props.load(it) }
def declaredArtifacts = props.keySet()

// Validate: every actual dependency must have a probe class
def missing = []
actualDependencies.each { artifactId ->
  if (!declaredArtifacts.contains(artifactId)) {
    missing.add(artifactId)
  }
}

if (!missing.isEmpty()) {
  throw new Exception("Missing entry for artifacts: " + missing.join(', ') +
    "\nPlease add entries to core-libs/src/main/resources/META-INF/bundled-libraries.properties")
}

// Validate: no extra probe classes that don't match actual dependencies
def extra = []
declaredArtifacts.each { key ->
  if (!actualDependencies.contains(key)) {
    extra.add(key)
  }
}

if (!extra.isEmpty()) {
  throw new Exception("Extra library not matching actual dependencies: " + extra.join(', ') +
    "\nPlease remove these entries from core-libs/src/main/resources/META-INF/bundled-libraries.properties")
}
