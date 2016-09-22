#!/usr/bin/env groovy

/*
 * This Jenkinsfile is intended to run on https://ci.jenkins-ci.org and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 *
 * The required labels are "java" and "docker" - "java" would be any node that can run Java builds. It doesn't need
 * to have Java installed, but some setups may have nodes that shouldn't have heavier builds running on them, so we
 * make this explicit. "docker" would be any node with docker installed.
 */

/*
 * Plugins required:
 *  - Pipeline
 *  - CloudBees Docker Pipeline
 *  - Timestamper
 */

// TEST FLAG - to make it easier to turn on/off unit tests for speeding up access to later stuff.
def runTests = false
/* Branch from jenkinsci/packaging to use for the packaging stages */
String packagingBranch = 'master'

// Only keep the 10 most recent builds.
properties([[$class: 'jenkins.model.BuildDiscarderProperty', strategy: [$class: 'LogRotator',
                                                                        numToKeepStr: '50',
                                                                        artifactNumToKeepStr: '20']]])

node('java') {
    timestamps {
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
                    sh "mvn -T 1C -Pdebug -U clean install ${runTests ? '-Dmaven.test.failure.ignore=true' : '-DskipTests'} -V -B -Dmaven.repo.local=${pwd()}/.repository"
                }
            }
        }


        // Once we've built, archive the artifacts and the test results.
        stage('Archive Artifacts / Test Results') {
            /* Stash jenkins.war for later packaging preparations */
            stash includes: '**/target/*.war', name: 'warfile'
            // UNCOMMENT: commented out to avoid the transit hit during testing
            //archiveArtifacts artifacts: '**/target/*.jar, **/target/*.war, **/target/*.hpi',
            //            fingerprint: true
            if (runTests) {
                junit healthScaleFactor: 20.0, testResults: '**/target/surefire-reports/*.xml'
            }
        }
    }
}
// TODO  if (!env.CHANGE_ID) { }
node('docker && celery') {   // NO KELP PLZ KTHX
    timestamps {
        def image

        sh 'docker version'
        dir('packaging') {
            git branch: packagingBranch, url: 'https://github.com/jenkinsci/packaging.git'

            stage('Packaging - Preparation') {
                docker.image('ubuntu:14.04').pull()
                docker.image('centos:6').pull()


                docker.image('ubuntu:15.10').pull()
                docker.image('opensuse:13.2').pull()
                docker.image('debian:wheezy').pull()
                docker.image('centos:7').pull()
                
                image = docker.build("jenkinsci/packaging-builder:0.2", 'docker')
                sh 'cd docker && ./build-sudo-images.sh'
            }

            stage('Packaging - Build') {
                // FIXME needs to be sudo-able packaging container
                image.inside('-u root') {
                    withEnv([
                        'BRANCH=./branding/jenkins.mk',
                        'BUILDENV=./env/test.mk',
                        'CREDENTIAL=./credentials/test.mk',
                        'WAR=target/jenkins.war',
                    ]) {
                        sh 'make clean'
                        unstash 'warfile' // Removed by cleanup previously
                        sh 'make deb rpm suse'
                    }
                    stash(includes: 'target/rpm/*.rpm', name: 'rpm')
                    stash(includes: 'target/suse/*.rpm', name: 'suse')
                    stash(includes: 'target/debian/*.deb', name: 'debian')
                    sh 'rm -rf target'
                }
            }
        }

        String packagingTestBranch = 'packaging-stable-tests';

        stage('Run installer tests') {
            sh 'rm -rf packaging-tests'
            dir('packaging-tests') {
                git changelog: false, poll: false, branch: packagingTestBranch, url: 'https://github.com/jenkinsci/packaging.git'
                testFlow = load 'workflow/installertest.groovy'
                unstash('rpm')
                unstash('suse')
                unstash('debian')
                
                // Needed because the installer folders don't align to expected locations
                sh 'mv target installers'
                sh 'mv installers/debian installers/deb'
                
                testFlow.runJenkinsInstallTests('master', 'jenkins', '8080')
            }
            sh 'rm -rf packaging-tests || true'
        }
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
