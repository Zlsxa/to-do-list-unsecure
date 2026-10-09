gateFailures = []

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
                    env.COMMIT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.CHECK_BRANCH}@${env.COMMIT}"
                }
            }
        }
        stage('Dépendances') {
            steps {
                sh '''
                    python3 -m venv .venv
                    python3 ci/pipfile2req.py Pipfile requirements-ci.txt
                    .venv/bin/pip install -q -r requirements-ci.txt coverage
                    python3 -m venv .venv-audit
                    .venv-audit/bin/pip install -q pip-audit
                '''
            }
        }
        stage('Tests unitaires + couverture') {
            steps {
                sh '''
                    .venv/bin/coverage run --source=tasks,todo manage.py test --noinput -v 2
                    .venv/bin/coverage xml -o coverage.xml
                    .venv/bin/coverage report
                '''
            }
        }
        stage('Gate SCA : pip-audit (CVE critiques)') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    script {
                        sh '.venv-audit/bin/pip-audit -r requirements-ci.txt -f json -o pip-audit.json || true'
                        def rc = sh(returnStatus: true, script: 'python3 ci/pip_audit_gate.py pip-audit.json')
                        if (rc != 0) {
                            gateFailures << 'pip-audit (dépendance avec CVE critique)'
                            error('ÉCHEC GATE SCA : une dépendance a une CVE de sévérité CRITICAL (voir le tableau ci-dessus)')
                        }
                    }
                }
            }
        }
        stage('Gate qualité : SonarQube') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    script {
                        def scannerHome = tool 'sonar-scanner'
                        withSonarQubeEnv('sonarqube') {
                            def rc = sh(returnStatus: true, script: """
                                ${scannerHome}/bin/sonar-scanner \
                                  -Dsonar.projectKey=todolist \
                                  -Dsonar.projectName=todolist \
                                  -Dsonar.projectVersion=${env.COMMIT} \
                                  -Dsonar.sources=tasks,todo \
                                  -Dsonar.tests=tasks \
                                  -Dsonar.test.inclusions=tasks/tests.py \
                                  -Dsonar.exclusions=**/migrations/**,tasks/tests.py \
                                  -Dsonar.python.version=3.13 \
                                  -Dsonar.python.coverage.reportPaths=coverage.xml \
                                  -Dsonar.qualitygate.wait=true \
                                  -Dsonar.qualitygate.timeout=300
                            """)
                            if (rc != 0) {
                                gateFailures << 'quality gate SonarQube'
                                error("ÉCHEC QUALITY GATE SonarQube : le code ne respecte pas le gate 'todolist-gate' (détail : ${env.SONAR_HOST_URL}/dashboard?id=todolist)")
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'pip-audit.json,coverage.xml', allowEmptyArchive: true
        }
        success {
            script { currentBuild.description = 'Tests, pip-audit et SonarQube OK : code autorisé au déploiement' }
        }
        failure {
            script {
                def cause = gateFailures ? gateFailures.join(' + ') : 'tests unitaires ou étape technique'
                currentBuild.description = "BLOQUÉ par : ${cause}"
                echo "================================================================"
                echo " CODE REFUSÉ - bloqué par : ${cause}"
                echo "================================================================"
            }
        }
    }
}
