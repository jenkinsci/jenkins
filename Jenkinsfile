// -*- Groovy -*-
pipeline {
  agent {
    kubernetes {
      label 'release-linux'
      yamlFile 'PodTemplates.d/release-linux.yaml'
    }
  }


  options {
    disableConcurrentBuilds()
  }

  stages {
    stage('Prepare Release') {
      steps {
        // Maven Release requires gpg key with password password and a certificate key with password
        sh '''
          mvn -B --no-transfer-progress -Darguments=-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn release:prepare
        '''
      }
    }
  }
}
