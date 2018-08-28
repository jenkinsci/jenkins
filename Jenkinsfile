#!/usr/bin/env groovy

/*
 * This Jenkinsfile is intended to run on https://ci.jenkins.io and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 *
 * The required labels are "java" and "docker" - "java" would be any node that can run Java builds. It doesn't need
 * to have Java installed, but some setups may have nodes that shouldn't have heavier builds running on them, so we
 * make this explicit. "docker" would be any node with docker installed.
 */

// TEST FLAG - to make it easier to turn on/off unit tests for speeding up access to later stuff.
def runTests = true
def failFast = false

properties([buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '20')), durabilityHint('PERFORMANCE_OPTIMIZED')])

// see https://github.com/jenkins-infra/documentation/blob/master/ci.adoc for information on what node types are available
def buildTypes = ['Linux', 'Windows']

def builds = [:]
for(i = 0; i < buildTypes.size(); i++) {
    def buildType = buildTypes[i]
    builds[buildType] = {
        node(buildType.toLowerCase()) {
            timestamps {
                // First stage is actually checking out the source. Since we're using Multibranch
                // currently, we can use "checkout scm".
                stage('Checkout') {
                    checkout scm
                }

                def changelistF = "${pwd tmp: true}/changelist"
                def m2repo = "${pwd tmp: true}/m2repo"

                // Now run the actual build.
                stage("${buildType} Build / Test") {
                    timeout(time: 180, unit: 'MINUTES') {
                        // See below for what this method does - we're passing an arbitrary environment
                        // variable to it so that JAVA_OPTS and MAVEN_OPTS are set correctly.
                        withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m",
                                    "MAVEN_OPTS=-Xmx1536m -Xms512m"]) {
                            // Actually run Maven!
                            // -Dmaven.repo.local=… tells Maven to create a subdir in the temporary directory for the local Maven repository
                            def mvnCmd = "mvn -Pdebug -U -Dset.changelist help:evaluate -Dexpression=changelist -Doutput=$changelistF clean install ${runTests ? '-Dmaven.test.failure.ignore' : '-DskipTests'} -V -B -Dmaven.repo.local=$m2repo -s settings-azure.xml -e"
                            if(isUnix()) {
                                sh mvnCmd
                                sh 'test `git status --short | tee /dev/stderr | wc --bytes` -eq 0'
                            } else {
                                bat mvnCmd
                            }
                        }
                    }
                }

                // Once we've built, archive the artifacts and the test results.
                stage("${buildType} Publishing") {
                    if (runTests) {
                        junit healthScaleFactor: 20.0, testResults: '*/target/surefire-reports/*.xml'
                    }
                    if (buildType == 'Linux') {
                        def changelist = readFile(changelistF)
                        dir(m2repo) {
                            archiveArtifacts artifacts: "**/*$changelist/*$changelist*",
                                             excludes: '**/*.lastUpdated,**/jenkins-test/',
                                             allowEmptyArchive: true, // in case we forgot to reincrementalify
                                             fingerprint: true
                        }
                    }
                }
            }
        }
    }
}

builds.ath = {
    node("docker&&highmem") {
        // Just to be safe
        deleteDir()
        def fileUri
        def metadataPath
        dir("sources") {
            checkout scm
            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m",
                          "MAVEN_OPTS=-Xmx1536m -Xms512m"]) {
                sh "mvn --batch-mode --show-version -DskipTests -am -pl war package -Dmaven.repo.local=${pwd tmp: true}/m2repo -s settings-azure.xml"
            }
            dir("war/target") {
                fileUri = "file://" + pwd() + "/jenkins.war"
            }
            metadataPath = pwd() + "/essentials.yml"
        }
        dir("ath") {
            runATH jenkins: fileUri, metadataFile: metadataPath
        }
    }
}

builds.failFast = failFast
parallel builds
infra.maybePublishIncrementals()

// This method sets up the Maven and JDK tools, puts them in the environment along
// with whatever other arbitrary environment variables we passed in, and runs the
// body we passed in within that environment.
void withMavenEnv(List envVars = [], def body) {
    // The names here are currently hardcoded for my test environment. This needs
    // to be made more flexible.
    // Using the "tool" Workflow call automatically installs those tools on the
    // node.
    String mvntool = tool name: "mvn", type: 'hudson.tasks.Maven$MavenInstallation'
    String jdktool = tool name: "jdk8", type: 'hudson.model.JDK'

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
