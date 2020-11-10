#!/usr/bin/env groovy

/*
 * This Jenkinsfile is intended to run on https://ci.jenkins.io and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 */

def buildNumber = BUILD_NUMBER as int; if (buildNumber > 1) milestone(buildNumber - 1); milestone(buildNumber) // JENKINS-43353 / JENKINS-58625

// TEST FLAG - to make it easier to turn on/off unit tests for speeding up access to later stuff.
def runTests = true
def failFast = false

properties([buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '3')), durabilityHint('PERFORMANCE_OPTIMIZED')])

// TODO: Restore 'Windows' once https://groups.google.com/forum/#!topic/jenkinsci-dev/v9d-XosOp2s is resolved
def buildTypes = ['Linux']
def jdks = [8, 11]

def builds = [:]
for(i = 0; i < buildTypes.size(); i++) {
for(j = 0; j < jdks.size(); j++) {
    def buildType = buildTypes[i]
    def jdk = jdks[j]
    builds["${buildType}-jdk${jdk}"] = {
        // see https://github.com/jenkins-infra/documentation/blob/master/ci.adoc#node-labels for information on what node types are available
        node(buildType == 'Linux' ? (jdk == 8 ? 'maven' : 'maven-11') : buildType.toLowerCase()) {
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
                                    "MAVEN_OPTS=-Xmx1536m -Xms512m"], buildType, jdk) {
                            // Actually run Maven!
                            // -Dmaven.repo.local=… tells Maven to create a subdir in the temporary directory for the local Maven repository
                            def mvnCmd = "mvn -Pdebug -Pjapicmp -U -Dset.changelist help:evaluate -Dexpression=changelist -Doutput=$changelistF clean install ${runTests ? '-Dmaven.test.failure.ignore' : '-DskipTests'} -V -B -ntp -Dmaven.repo.local=$m2repo -e"

                            if(isUnix()) {
                                sh mvnCmd
                                sh 'git add . && git diff --exit-code HEAD'
                            } else {
                                bat mvnCmd
                            }
                        }
                    }
                }

                // Once we've built, archive the artifacts and the test results.
                stage("${buildType} Publishing") {
                    if (runTests) {
                        junit healthScaleFactor: 20.0, testResults: '*/target/surefire-reports/*.xml,war/junit.xml'
                        archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/surefire-reports/*.dumpstream'
                    }
                    if (buildType == 'Linux' && jdk == jdks[0]) {
                        def changelist = readFile(changelistF)
                        dir(m2repo) {
                            archiveArtifacts artifacts: "**/*$changelist/*$changelist*",
                                             excludes: '**/*.lastUpdated,**/jenkins-test*/',
                                             allowEmptyArchive: true, // in case we forgot to reincrementalify
                                             fingerprint: true
                        }
                        publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, includes: 'japicmp.html', keepAll: false, reportDir: 'core/target/japicmp', reportFiles: 'japicmp.html', reportName: 'API compatibility', reportTitles: 'japicmp report'])
                    }
                }
        }
    }
}}

// TODO: Restore ATH once https://groups.google.com/forum/#!topic/jenkinsci-dev/v9d-XosOp2s is resolved
// TODO: ATH flow now supports Java 8 only, it needs to be reworked (INFRA-1690)
builds.ath = {
    node("docker&&highmem") {
        // Just to be safe
        deleteDir()
        def fileUri
        def metadataPath
        dir("sources") {
            checkout scm
            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m",
                          "MAVEN_OPTS=-Xmx1536m -Xms512m"], 8) {
                sh "mvn --batch-mode --show-version -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -DskipTests -am -pl war package -Dmaven.repo.local=${pwd tmp: true}/m2repo"
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
void withMavenEnv(List envVars = [], def buildType, def javaVersion, def body) {
    if (buildType == 'Linux') {
        // I.e., a Maven container using ACI. No need to install tools.
        return withEnv(envVars) {
            body.call()
        }
    }
    
    // The names here are currently hardcoded for my test environment. This needs
    // to be made more flexible.
    // Using the "tool" Workflow call automatically installs those tools on the
    // node.
    String mvntool = tool name: "mvn", type: 'hudson.tasks.Maven$MavenInstallation'
    String jdktool = tool name: "jdk${javaVersion}", type: 'hudson.model.JDK'

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
