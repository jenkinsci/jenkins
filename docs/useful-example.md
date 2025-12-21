# Useful Usage Examples

This document provides practical, real-world usage examples for commonly used Jenkins plugins.
The goal is to help new users understand how these plugins are typically used in day-to-day Jenkins setups.

---

## 1. Git Plugin

### When to use
The Git plugin is used when your source code is stored in a Git repository such as GitHub, GitLab, or Bitbucket.

### Example: Checkout source code in a Pipeline

```groovy
pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                git(
                    url: 'https://github.com/example/project.git',
                    branch: 'main'
                )
            }
        }
    }
}


### Example: Basic CI Pipeline

pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                echo 'Building the application'
            }
        }

        stage('Test') {
            steps {
                echo 'Running tests'
            }
        }
    }
}

### Example: Using credentials securely in a pipeline

pipeline {
    agent any

    stages {
        stage('Deploy') {
            steps {
                withCredentials([
                    string(credentialsId: 'api-token', variable: 'API_TOKEN')
                ]) {
                    sh 'echo Deploying with token $API_TOKEN'
                }
            }
        }
    }
}

### Example: Send email on build failure

pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                error 'Simulated failure'
            }
        }
    }

    post {
        failure {
            emailext(
                subject: 'Build Failed: ${env.JOB_NAME}',
                body: 'The build has failed. Please check the Jenkins console output.',
                to: 'team@example.com'
            )
        }
    }
}


