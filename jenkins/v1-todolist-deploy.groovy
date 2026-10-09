pipeline {
    agent any
    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche Git à déployer')
        choice(name: 'ENV', choices: ['app-dev', 'app-prod1'], description: 'app-dev = recette, app-prod1 = production (main uniquement)')
    }
    environment {
        REPO_URL = 'https://github.com/Zlsxa/to-do-list-unsecure.git'
    }
    stages {
        stage('Règle de déploiement') {
            steps {
                script {
                    if (params.ENV == 'app-prod1' && params.BRANCH != 'main') {
                        error("REFUS : la production n'accepte que la branche main (branche demandée : ${params.BRANCH})")
                    }
                }
            }
        }
        stage('Sources') {
            steps {
                git url: env.REPO_URL, branch: params.BRANCH
            }
        }
        stage('Déploiement Ansible') {
            steps {
                dir('deploy') {
                    sh '''
                        ansible-playbook deploy_app.yml --limit "$ENV" --tags app,check \
                          -e app_branch="$BRANCH" -e app_repo="$REPO_URL" \
                          --private-key /var/lib/jenkins/.ssh/id-rda-infra.key
                    '''
                }
            }
        }
    }
}
