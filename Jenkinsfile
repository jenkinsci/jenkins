#!/usr/bin/env groovy

/*
 * This Jenkinsfile is intended to run on https://ci.jenkins.io and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 */

def buildNumber = BUILD_NUMBER as int; if (buildNumber > 1) milestone(buildNumber - 1); milestone(buildNumber) // JENKINS-43353 / JENKINS-58625

def failFast = false
// Same memory sizing for both builds and ATH
def javaOpts = [
  'JAVA_OPTS=-Xmx1536m -Xms512m',
  'MAVEN_OPTS=-Xmx1536m -Xms512m',
]


 node("maven-11-windows") {
 stage('hello') {
    bat '''
      pwsh.exe -Command Get-CimInstance -ClassName CIM_DiskDrive
      pwsh.exe -Command Get-CimInstance -ClassName CIM_Processor
    '''
  }
}
