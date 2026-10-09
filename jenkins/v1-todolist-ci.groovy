pipeline {
    agent any
    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche Git à tester puis déployer')
        choice(name: 'ENV', choices: ['app-dev', 'app-prod1'], description: 'app-dev = recette, app-prod1 = production (main uniquement)')
    }
    stages {
        stage('Check code') {
            steps {
                build job: 'todolist-check-code', parameters: [
                    string(name: 'BRANCH', value: params.BRANCH)
                ]
            }
        }
        stage('Deploy') {
            steps {
                build job: 'todolist-deploy', parameters: [
                    string(name: 'BRANCH', value: params.BRANCH),
                    string(name: 'ENV', value: params.ENV)
                ]
            }
        }
    }
}
