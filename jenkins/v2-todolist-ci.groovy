pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '200', daysToKeepStr: '365'))
        timestamps()
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche Git à tester puis déployer')
        choice(name: 'ENV', choices: ['app-dev', 'app-prod1'], description: 'app-dev = recette, app-prod1 = production (main uniquement)')
    }
    environment {
        CI_BRANCH = "${params.BRANCH ?: 'main'}"
        CI_ENV    = "${params.ENV ?: 'app-dev'}"
    }
    stages {
        stage('Traçabilité') {
            steps {
                script {
                    def users = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    env.CI_USER = users ? users[0].userId : 'automatique'
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.CI_BRANCH} -> ${env.CI_ENV} par ${env.CI_USER}"
                }
            }
        }
        stage('Check code') {
            steps {
                build job: 'todolist-check-code', parameters: [
                    string(name: 'BRANCH', value: env.CI_BRANCH)
                ]
            }
        }
        stage('Deploy') {
            steps {
                build job: 'todolist-deploy', parameters: [
                    string(name: 'BRANCH', value: env.CI_BRANCH),
                    string(name: 'ENV', value: env.CI_ENV),
                    string(name: 'TRIGGERED_BY', value: env.CI_USER)
                ]
            }
        }
    }
}
