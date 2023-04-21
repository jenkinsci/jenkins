#!/usr/bin/env groovy

/*
 * This Jenkinsfile is intended to run on https://ci.jenkins.io and may fail anywhere else.
 * It makes assumptions about plugins being installed, labels mapping to nodes that can build what is needed, etc.
 */

def failFast = false

properties([
  buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '3')),
  disableConcurrentBuilds(abortPrevious: true)
])

def buildTypes = ['Linux', 'Windows']
def jdks = [11, 17, 19]

def builds = [:]
for (i = 0; i < buildTypes.size(); i++) {
  for (j = 0; j < jdks.size(); j++) {
    def buildType = buildTypes[i]
    def jdk = jdks[j]
    if (buildType == 'Windows' && jdk != 17) {
      continue // unnecessary use of hardware
    }
    builds["${buildType}-jdk${jdk}"] = {
      // see https://github.com/jenkins-infra/documentation/blob/master/ci.adoc#node-labels for information on what node types are available
      def agentContainerLabel = 'maven-' + jdk
      if (buildType == 'Windows') {
        agentContainerLabel += '-windows'
      }
      retry(conditions: [kubernetesAgent(handleNonKubernetes: true), nonresumable()], count: 2) {
        node(agentContainerLabel) {
          // First stage is actually checking out the source. Since we're using Multibranch
          // currently, we can use "checkout scm".
          stage('Checkout') {
            infra.checkoutSCM()
          }

          def changelistF = "${pwd tmp: true}/changelist"
          def m2repo = "${pwd tmp: true}/m2repo"

          // Now run the actual build.
          stage("${buildType} Build / Test") {
            timeout(time: 6, unit: 'HOURS') {
              realtimeJUnit(healthScaleFactor: 20.0, testResults: '*/target/surefire-reports/*.xml') {
                def mavenOptions = [
                  '-Pdebug',
                  '-Penable-jacoco',
                  '--update-snapshots',
                  "-Dmaven.repo.local=$m2repo",
                  '-Dmaven.test.failure.ignore',
                  '-DforkCount=2',
                  '-Dspotbugs.failOnError=false',
                  '-Dcheckstyle.failOnViolation=false',
                  '-Dset.changelist',
                  'help:evaluate',
                  '-Dexpression=changelist',
                  "-Doutput=$changelistF",
                  'clean',
                  'install',
                ]
                infra.runMaven(mavenOptions, jdk)
                if (isUnix()) {
                  sh 'git add . && git diff --exit-code HEAD'
                }
              }
            }
          }

          // Once we've built, archive the artifacts and the test results.
          stage("${buildType} Publishing") {
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/surefire-reports/*.dumpstream'
            if (!fileExists('core/target/surefire-reports/TEST-jenkins.Junit4TestsRanTest.xml')) {
              error 'JUnit 4 tests are no longer being run for the core package'
            }
            if (!fileExists('test/target/surefire-reports/TEST-jenkins.Junit4TestsRanTest.xml')) {
              error 'JUnit 4 tests are no longer being run for the test package'
            }
            // cli and war have been migrated to JUnit 5
            if (failFast && currentBuild.result == 'UNSTABLE') {
              error 'There were test failures; halting early'
            }
            if (buildType == 'Linux' && jdk == jdks[0]) {
              def folders = env.JOB_NAME.split('/')
              if (folders.length > 1) {
                discoverGitReferenceBuild(scm: folders[1])
              }
              recordCoverage(tools: [[parser: 'JACOCO', pattern: 'coverage/target/site/jacoco-aggregate/jacoco.xml']], sourceCodeRetention: 'MODIFIED')

              echo "Recording static analysis results for '${buildType}'"
              recordIssues(
                  enabledForFailure: true,
                  tools: [java(), javaDoc()],
                  filters: [excludeFile('.*Assert.java')],
                  sourceCodeEncoding: 'UTF-8',
                  skipBlames: true,
                  trendChartType: 'TOOLS_ONLY'
                  )
              recordIssues([tool: spotBugs(pattern: '**/target/spotbugsXml.xml'),
                sourceCodeEncoding: 'UTF-8',
                skipBlames: true,
                trendChartType: 'TOOLS_ONLY',
                qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]])
              recordIssues([tool: checkStyle(pattern: '**/target/checkstyle-result.xml'),
                sourceCodeEncoding: 'UTF-8',
                skipBlames: true,
                trendChartType: 'TOOLS_ONLY',
                qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]]])
              recordIssues([tool: esLint(pattern: '**/target/eslint-warnings.xml'),
                sourceCodeEncoding: 'UTF-8',
                skipBlames: true,
                trendChartType: 'TOOLS_ONLY',
                qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]]])
              recordIssues([tool: styleLint(pattern: '**/target/stylelint-warnings.xml'),
                sourceCodeEncoding: 'UTF-8',
                skipBlames: true,
                trendChartType: 'TOOLS_ONLY',
                qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]]])
              launchable.install()
              withCredentials([string(credentialsId: 'launchable-jenkins-jenkins', variable: 'LAUNCHABLE_TOKEN')]) {
                launchable('verify')
                /*
                 * TODO Create a Launchable build and session earlier, and replace "--no-build" with
                 * "--session" to associate these test results with a particular build. The commits
                 * associated with the Launchable build should be the commits of the transitive
                 * closure of the Jenkins WAR under test in this build.
                 */
                launchable("record tests --no-build --flavor platform=${buildType.toLowerCase()} --flavor jdk=${jdk} maven './**/target/surefire-reports'")
              }
              if (failFast && currentBuild.result == 'UNSTABLE') {
                error 'Static analysis quality gates not passed; halting early'
              }
              /*
               * If the current build was successful, we send the commits to Launchable so that
               * these commits can be consumed by a build in the Launchable BOM workspace in the
               * future.
               *
               * TODO Move this up to before the present parallel block starts (or move the ATH run
               * in this file to after joining on the present parallel block) so that these commits
               * can be consumed by a build in the Launchable ATH workspace in this file in the
               * future.
               */
              if (currentBuild.currentResult == 'SUCCESS') {
                withCredentials([string(credentialsId: 'launchable-jenkins-bom', variable: 'LAUNCHABLE_TOKEN')]) {
                  launchable('verify')
                  launchable('record commit')
                }
              }

              def changelist = readFile(changelistF)
              dir(m2repo) {
                archiveArtifacts(
                    artifacts: "**/*$changelist/*$changelist*",
                    excludes: '**/*.lastUpdated,**/jenkins-coverage*/,**/jenkins-test*/',
                    allowEmptyArchive: true, // in case we forgot to reincrementalify
                    fingerprint: true
                    )
              }
            }
          }
        }
      }
    }
  }
}

builds.ath = {
  retry(conditions: [agent(), nonresumable()], count: 2) {
    node('docker-highmem') {
      // Just to be safe
      deleteDir()
      checkout scm
      def browser = 'firefox'
      infra.withArtifactCachingProxy {
        sh 'bash ath.sh ' + browser
      }
      junit testResults: 'target/ath-reports/TEST-*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]
      launchable.install()
      withCredentials([string(credentialsId: 'launchable-jenkins-acceptance-test-harness', variable: 'LAUNCHABLE_TOKEN')]) {
        launchable('verify')
        /*
         * TODO Create a Launchable build and session earlier, and replace "--no-build" with
         * "--session" to associate these test results with a particular build. The commits
         * associated with the Launchable build should be the commits of the transitive closure of
         * the Jenkins WAR under test in this build as well as the commits of the transitive closure
         * of the ATH JAR.
         */
        launchable("record tests --no-build --flavor platform=linux --flavor jdk=11 --flavor browser=${browser} maven './target/ath-reports'")
      }
    }
  }
}

builds.failFast = failFast
parallel builds
infra.maybePublishIncrementals()
