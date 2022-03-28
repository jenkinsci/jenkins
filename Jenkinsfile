pipeline {
  agent any
  stages {
    stage('checkout'){
      checkout scm
  }
    stage('build'){
      steps {  
      mvn package
      }
    }
}
}
