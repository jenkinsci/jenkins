#!/usr/bin/env groovy

/*
 * This Jenkinsfile is intended to run on https://ci.jenkins-ci.org and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 *
 * The required labels are "java" and "docker" - "java" would be any node that can run Java builds. It doesn't need
 * to have Java installed, but some setups may have nodes that shouldn't have heavier builds running on them, so we
 * make this explicit. "docker" would be any node with docker installed.
 */

// TEST FLAG - to make it easier to turn on/off unit tests for speeding up access to later stuff.
def runTests = true

// Only keep the 10 most recent builds.
properties([[$class: 'jenkins.model.BuildDiscarderProperty', strategy: [$class: 'LogRotator',
                                                                        numToKeepStr: '50',
                                                                        artifactNumToKeepStr: '20']]])

String packagingBranch = (binding.hasVariable('packagingBranch')) ? packagingBranch : 'jenkins-2.0'

timestampedNode('java') {

    // First stage is actually checking out the source. Since we're using Multibranch
    // currently, we can use "checkout scm".
    stage('Checkout') {
        checkout scm
    }

    // Now run the actual build.
    stage("Build / Test") {
        timeout(time: 180, unit: 'MINUTES') {
            // See below for what this method does - we're passing an arbitrary environment
            // variable to it so that JAVA_OPTS and MAVEN_OPTS are set correctly.
            withMavenEnv(["JAVA_OPTS=-Xmx1536m -Xms512m -XX:MaxPermSize=1024m",
                        "MAVEN_OPTS=-Xmx1536m -Xms512m -XX:MaxPermSize=1024m"]) {
                // Actually run Maven!
                // The -Dmaven.repo.local=${pwd()}/.repository means that Maven will create a
                // .repository directory at the root of the build (which it gets from the
                // pwd() Workflow call) and use that for the local Maven repository.
                sh "mvn -Pdebug -U clean install ${runTests ? '-Dmaven.test.failure.ignore=true' : '-DskipTests'} -V -B -Dmaven.repo.local=${pwd()}/.repository"
            }
        }
    }


    // Once we've built, archive the artifacts and the test results.
    stage('Archive Artifacts / Test Results') {
        archiveArtifacts artifacts: '**/target/*.jar, **/target/*.war, **/target/*.hpi',
                    fingerprint: true
        if (runTests) {
            junit healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'
        }
    }
}

def debFileName
def rpmFileName
def suseFileName

// Run the packaging build on a node with the "docker" label.
timestampedNode('docker') {
    // First stage here is getting prepped for packaging.
    stage('Packaging - Docker Prep') {
        // Docker environment to build packagings
        dir('packaging-docker') {
            git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'
            sh 'docker build -t jenkins-packaging-builder:0.1 docker'
        }
    }

    stage('Packaging') {
        // Working packaging code, separate branch with fixes
        dir('packaging') {
            deleteDir()

            docker.image("jenkins-packaging-builder:0.1").inside("-u root") {
                git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'

                try {
                    // Saw issues with unstashing inside a container, and not sure copy artifact plugin would work here.
                    // So, simple wget.
                    sh "wget -q ${currentBuild.absoluteUrl}/artifact/war/target/jenkins.war"
                    sh "make clean deb rpm suse BRAND=./branding/jenkins.mk BUILDENV=./env/test.mk CREDENTIAL=./credentials/test.mk WAR=jenkins.war"
                } catch (Exception e) {
                    error "Packaging failed: ${e}"
                } finally {
                    // Needed to make sure the output of the build can be deleted by later runs.
                    // Hackish, yes, but rpm builds as a numeric UID only user fail, so...
                    sh "chmod -R a+w target || true"
                    sh "chmod a+w jenkins.war || true"
                }
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

                step([$class: 'ArtifactArchiver', artifacts: 'target/**/*', fingerprint: true])

                // Fail the build if we didn't find at least one of the packages, meaning they weren't built but
                // somehow make didn't error out.
                if (debFileName == null || rpmFileName == null || suseFileName  == null) {
                    error "At least one of Debian, RPM or SuSE packages are missing, so failing the build."
                }
            }
        }
    }
}


stage('Packaging - Testing') {
    if (runTests) {
        if (!env.CHANGE_ID) {
            // NOTE: As of now, a lot of package tests will fail. See https://issues.jenkins-ci.org/issues/?filter=15257 for
            // possible open JIRAs.

            // Basic parameters
            String artifactName = (binding.hasVariable('artifactName')) ? artifactName : 'jenkins'
            String jenkinsPort = (binding.hasVariable('jenkinsPort')) ? jenkinsPort : '8080'

            // Set up
            String debfile = "artifact://${env.JOB_NAME}/${env.BUILD_NUMBER}#target/debian/${debFileName}"
            String rpmfile = "artifact://${env.JOB_NAME}/${env.BUILD_NUMBER}#target/rpm/${rpmFileName}"
            String susefile = "artifact://${env.JOB_NAME}/${env.BUILD_NUMBER}#target/suse/${suseFileName}"

            timestampedNode("docker") {
                stage "Load Lib"
                dir('workflowlib') {
                    deleteDir()
                    git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'
                    flow = load 'workflow/installertest.groovy'
                }
            }
            // Run the real tests within docker node label
            flow.fetchAndRunJenkinsInstallerTest("docker", rpmfile, susefile, debfile,
                packagingBranch, artifactName, jenkinsPort)
        }
        else {
            echo "Not running package testing against pull requests"
        }
    }
    else {
        echo "Skipping package tests"
    }
}


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

// Runs the given body within a Timestamper wrapper on the given label.
def timestampedNode(String label, Closure body) {
    node(label) {
        timestamps {
            body.call()
        }
    }
}
