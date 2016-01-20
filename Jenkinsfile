#!groovy
/*
 * This Jenkinsfile is intended to run on https://ci.jenkins-ci.org and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 */

// TEST FLAG - to make it easier to turn on/off unit tests for speeding up access to later stuff.
def runTests = true

// Only keep the 10 most recent builds.
properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator',
                                                          numToKeepStr: '10']]])

// TODO: Once https://github.com/jenkinsci/packaging/pull/34 is merged, switch to master branch.
String packagingBranch = (binding.hasVariable('packagingBranch')) ? packagingBranch : 'oss-dockerized-tests'

node('java') {

    // Add timestamps to logging output.
    wrap([$class: 'TimestamperBuildWrapper']) {

        // First stage is actually checking out the source. Since we're using Multibranch
        // currently, we can use "checkout scm".
        stage "Checkout source"

        checkout scm

        // Now run the actual build.
        stage "Build and test"

        // We're wrapping this in a timeout - if it takes more than 180 minutes, kill it.
        timeout(time: 180, unit: 'MINUTES') {
            // See below for what this method does - we're passing an arbitrary environment
            // variable to it so that JAVA_OPTS and MAVEN_OPTS are set correctly.
            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m -XX:MaxPermSize=1024m",
                          "MAVEN_OPTS=-Xmx1536m -Xms512m -XX:MaxPermSize=1024m"]) {
                // Actually run Maven!
                // The -Dmaven.repo.local=${pwd()}/.repository means that Maven will create a
                // .repository directory at the root of the build (which it gets from the
                // pwd() Workflow call) and use that for the local Maven repository.
                sh "mvn -Pdebug -U clean install ${runTests ? '-Dmaven.test.failure.ignore=true -Dconcurrency=1' : '-DskipTests'} -V -B -Dmaven.repo.local=${pwd()}/.repository"
            }
        }

        // Once we've built, archive the artifacts and the test results.
        stage "Archive artifacts and test results"

        archive includes: "**/target/*.jar, **/target/*.war, **/target/*.hpi"
        if (runTests) {
            step([$class: 'JUnitResultArchiver', healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'])
        }

        // And stash the jenkins.war for the next step
        stash name: "jenkins.war", includes: "war/target/jenkins.war"
    }
}

def debFileName
def rpmFileName
def suseFileName

// Run the packaging build on a node with the "pkg" label.
node('docker') {
    // Add timestamps to logging output.
    wrap([$class: 'TimestamperBuildWrapper']) {

        // First stage here is getting prepped for packaging.
        stage "packaging - docker prep"

        // Docker environment to build packagings
        dir('packaging-docker') {
            git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'
            sh 'docker build -t jenkins-packaging-builder:0.1 docker'
        }

        stage "packaging - actually packaging"
        // Working packaging code, separate branch with fixes
        dir('packaging') {
            git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'
            // Grab the war file from the stash - it goes to war/target/jenkins.war
            unstash "jenkins.war"
            sh "cp war/target/jenkins.war ."

            sh 'docker run --rm -v "`pwd`":/tmp/packaging -w /tmp/packaging jenkins-packaging-builder:0.1 make clean deb rpm suse BRAND=./branding/jenkins.mk BUILDENV=./env/test.mk CREDENTIAL=./credentials/test.mk WAR=jenkins.war'

            dir("target/debian") {
                def debFilesFound = findFiles(glob: "*.deb")
                if (debFilesFound.size() > 0) {
                    debFileName = debFilesFound[0]?.name
                }
            }

            dir("target/rpm") {
                def rpmFilesFound = findFiles(glob: "*.rpm")
                if (rpmFilesFound.size() > 0) {
                    rpmFileName = rpmFilesFound[0]?.name
                }
            }

            dir("target/suse") {
                def suseFilesFound = findFiles(glob: "*.rpm")
                if (suseFilesFound.size() > 0) {
                    suseFileName = suseFilesFound[0]?.name
                }
            }
            archive includes: "target/**/*"
        }

    }
}

stage "Package testing"

if (runTests) {
    // NOTE: As of now, a lot of package tests will fail. See https://issues.jenkins-ci.org/issues/?filter=15257 for
    // possible open JIRAs.

    // Basic parameters
    String artifactName = (binding.hasVariable('artifactName')) ? artifactName : 'jenkins'
    String jenkinsPort = (binding.hasVariable('jenkinsPort')) ? jenkinsPort : '8080'

    // Set up
    String debfile = "artifact://${env.JOB_NAME}/${env.BUILD_NUMBER}#target/debian/${debFileName}"
    String rpmfile = "artifact://${env.JOB_NAME}/${env.BUILD_NUMBER}#target/rpm/${rpmFileName}"
    String susefile = "artifact://${env.JOB_NAME}/${env.BUILD_NUMBER}#target/suse/${suseFileName}"

    node("docker") {
        stage "Load Lib"
        sh 'rm -rf workflowlib'
        dir ('workflowlib') {
            git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'
            flow = load 'workflow/installertest.groovy'
        }
    }
    // Run the real tests within docker node label
    flow.fetchAndRunJenkinsInstallerTest("docker", rpmfile, susefile, debfile,
            packagingBranch, artifactName, jenkinsPort)
} else {
    echo "Skipping package tests"
}

// This method sets up the Maven and JDK tools, puts them in the environment along
// with whatever other arbitrary environment variables we passed in, and runs the
// body we passed in within that environment.
void withMavenEnv(List envVars = [], def body) {
    // The names here are currently hardcoded for my test environment. This needs
    // to be made more flexible.
    // Using the "tool" Workflow call automatically installs those tools on the
    // node.
    String mvntool = tool name: "mvn3.3.3", type: 'hudson.tasks.Maven$MavenInstallation'
    String jdktool = tool name: "jdk7_80", type: 'hudson.model.JDK'

    // Set JAVA_HOME, MAVEN_HOME and special PATH variables for the tools we're
    // using.
    List mvnEnv = ["PATH+MVN=${mvntool}/bin", "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}", "MAVEN_HOME=${mvntool}"]

    // Add any additional environment variables.
    mvnEnv.addAll(envVars)

    // Invoke the body closure we're passed within the environment we've created.
    withEnv(mvnEnv) {
        body.call()
    }
}
