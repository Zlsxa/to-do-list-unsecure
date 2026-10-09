pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '90'))
        timestamps()
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche Git à tester')
    }
    environment {
        REPO_URL     = 'https://github.com/Zlsxa/to-do-list-unsecure.git'
        CHECK_BRANCH = "${params.BRANCH ?: 'main'}"
    }
    stages {
        stage('Sources') {
            steps {
                git url: env.REPO_URL, branch: env.CHECK_BRANCH
                script {
                    def commit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.CHECK_BRANCH}@${commit}"
                    currentBuild.description = currentBuild.getBuildCauses()[0].shortDescription
                }
            }
        }
        stage('Dépendances') {
            steps {
                sh '''
                    python3 -m venv .venv
                    python3 ci/pipfile2req.py Pipfile requirements-ci.txt
                    .venv/bin/pip install -q -r requirements-ci.txt
                    python3 -m venv .venv-audit
                    .venv-audit/bin/pip install -q pip-audit
                '''
            }
        }
        stage('Tests unitaires') {
            steps {
                sh '.venv/bin/python manage.py test --noinput -v 2'
            }
        }
        stage('Sécurité des dépendances (pip-audit)') {
            steps {
                sh '''
                    .venv-audit/bin/pip-audit -r requirements-ci.txt -f json -o pip-audit.json || true
                    python3 ci/pip_audit_gate.py pip-audit.json || true
                '''
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'pip-audit.json', allowEmptyArchive: true
        }
    }
}
