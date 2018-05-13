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
def jdk = "8"

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
                        infra.runMaven(["-Pdebug", "-U", "-Dset.changelist", "help:evaluate",
                                        "-Dexpression=changelist", "-Doutput=$changelistF",
                                        runTests ? '-Dmaven.test.failure.ignore' : '-DskipTests',
                                        "-V", "-B", "-Dmaven.repo.local=$m2repo",
                                        "clean", "install"],
                            jdk, ["JAVA_OPTS=-Xmx1536m -Xms512m", "MAVEN_OPTS=-Xmx1536m -Xms512m"])

                        if (isUnix()) {
                            sh 'test `git status --short | tee /dev/stderr | wc --bytes` -eq 0'
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

builds.failFast = failFast
parallel builds

// Integration tests, see essentials.yml
essentialsTest()

// Publish to incrementals if everything is fine
infra.maybePublishIncrementals()

