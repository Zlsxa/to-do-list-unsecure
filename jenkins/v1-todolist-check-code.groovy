pipeline {
    agent any
    parameters {
        string(name: 'BRANCH', defaultValue: 'main', description: 'Branche Git à tester')
    }
    environment {
        REPO_URL = 'https://github.com/Zlsxa/to-do-list-unsecure.git'
    }
    stages {
        stage('Sources') {
            steps {
                git url: env.REPO_URL, branch: params.BRANCH
            }
        }
        stage('Dépendances') {
            steps {
                sh '''
                    python3 -m venv .venv
                    python3 ci/pipfile2req.py Pipfile requirements-ci.txt
                    .venv/bin/pip install -q -r requirements-ci.txt pip-audit
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
                    .venv/bin/pip-audit -r requirements-ci.txt -f json -o pip-audit.json || true
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
