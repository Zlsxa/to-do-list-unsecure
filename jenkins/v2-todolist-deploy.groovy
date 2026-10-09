pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '500', daysToKeepStr: '365'))
        timestamps()
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche Git à déployer')
        choice(name: 'ENV', choices: ['app-dev', 'app-prod1'], description: 'app-dev = recette, app-prod1 = production (main uniquement)')
        string(name: 'TRIGGERED_BY', defaultValue: '', description: 'Rempli automatiquement par todolist-ci')
    }
    environment {
        REPO_URL      = 'https://github.com/Zlsxa/to-do-list-unsecure.git'
        DEPLOY_BRANCH = "${params.BRANCH ?: 'main'}"
        DEPLOY_ENV    = "${params.ENV ?: 'app-dev'}"
    }
    stages {
        stage('Règle de déploiement') {
            steps {
                script {
                    if (env.DEPLOY_ENV == 'app-prod1' && env.DEPLOY_BRANCH != 'main') {
                        error("REFUS : la production n'accepte que la branche main (branche demandée : ${env.DEPLOY_BRANCH})")
                    }
                }
            }
        }
        stage('Sources') {
            steps {
                git url: env.REPO_URL, branch: env.DEPLOY_BRANCH
                script {
                    def users = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    env.DEPLOYER = users ? users[0].userId : (params.TRIGGERED_BY ?: 'inconnu')
                    env.COMMIT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.DEPLOY_ENV} ${env.DEPLOY_BRANCH}@${env.COMMIT} par ${env.DEPLOYER}"
                    currentBuild.description = "Déploiement de ${env.DEPLOY_BRANCH}@${env.COMMIT} sur ${env.DEPLOY_ENV} par ${env.DEPLOYER}"
                }
            }
        }
        stage('Déploiement Ansible') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'deploy-ssh-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
                    dir('deploy') {
                        sh '''
                            BUILD_USER="$DEPLOYER" ansible-playbook deploy_app.yml --limit "$DEPLOY_ENV" --tags app,check \
                              -e app_branch="$DEPLOY_BRANCH" -e app_repo="$REPO_URL" \
                              -u "$SSH_USER" --private-key "$SSH_KEY"
                        '''
                    }
                }
            }
        }
    }
}
