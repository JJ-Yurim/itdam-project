def publishBackendEvent(String stageName, String status, String level = 'INFO') {
    if (!fileExists('scripts/publish-deploy-event.sh')) {
        echo "Deployment event skipped before checkout: ${status}"
        return
    }
    def payload = groovy.json.JsonOutput.toJson([
        deploy_id: env.DEPLOY_ID ?: 'unknown',
        commit: env.GIT_COMMIT_SHORT ?: 'unknown',
        pipeline: 'backend',
        pipeline_stage: stageName,
        status: status,
        jenkins_url: env.BUILD_URL ?: 'unknown',
        environment: env.DEPLOY_BRANCH ?: 'unknown',
        level: level
    ])
    def shellPayload = payload.replace("'", "'\"'\"'")
    sh "printf '%s' '${shellPayload}' | ./scripts/publish-deploy-event.sh"
}

pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 20, unit: 'MINUTES')
        timestamps()
    }

    parameters {
        booleanParam(
            name: 'FORCE_DEPLOY',
            defaultValue: false,
            description: 'Run Backend test, build, and deployment even when no Backend path changed.'
        )
    }

    environment {
        BACKEND_IMAGE = 'c206-backend'
        TESTCONTAINERS_HOST_OVERRIDE = '172.26.8.42'
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    def scmVars = checkout scm
                    def gitBranch = scmVars.GIT_BRANCH

                    if (!gitBranch) {
                        error(
                            "브랜치를 확인할 수 없습니다. SCM 결과: ${scmVars}"
                        )
                    }

                    env.DEPLOY_BRANCH = gitBranch
                        .replaceFirst(/^refs\/remotes\/origin\//, '')
                        .replaceFirst(/^origin\//, '')

                    if (!(env.DEPLOY_BRANCH in ['main', 'develop'])) {
                        error(
                            "배포할 수 없는 브랜치: ${env.DEPLOY_BRANCH}"
                        )
                    }

                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short=8 HEAD',
                        returnStdout: true
                    ).trim()

                    env.BACKEND_IMAGE_TAG =
                        "${env.BACKEND_IMAGE}:${env.DEPLOY_BRANCH}-${env.GIT_COMMIT_SHORT}"

                    env.AI_CONTRACT_TEST_IMAGE =
                        "c206-ai-contract-test:${env.DEPLOY_BRANCH}-${env.GIT_COMMIT_SHORT}"

                    env.DEPLOY_ID =
                        "${env.DEPLOY_BRANCH}-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"

                    echo "배포 브랜치: ${env.DEPLOY_BRANCH}"
                    echo "배포 커밋: ${env.GIT_COMMIT_SHORT}"
                    publishBackendEvent('Pipeline', 'PIPELINE_STARTED')
                }
            }
        }

        // 변경 감지 조건은 이 부모 스테이지 한 곳에서만 관리한다.
        // 하위 스테이지는 함께 실행되거나 함께 스킵되어야 하므로 개별 when을 두지 않는다.
        stage('Backend Pipeline') {
            when {
                anyOf {
                    expression { currentBuild.number == 1 }
                    expression { params.FORCE_DEPLOY }
                    changeset 'backend/**'
                    changeset 'ai-service/**'
                    changeset 'infra/**'
                    changeset 'scripts/deploy-backend.sh'
                    changeset 'scripts/test-deploy-backend.sh'
                    changeset 'scripts/collect-deploy-diagnostics.sh'
                    changeset 'scripts/check-diagnostics-secrets.sh'
                    changeset 'scripts/publish-deploy-event.sh'
                    changeset 'Jenkinsfile'
                }
            }

            stages {
                stage('Build AI Contract Test Image') {
                    steps {
                        sh '''
                            docker build \
                              --pull \
                              --progress=plain \
                              -f ai-service/Dockerfile.test \
                              -t "$AI_CONTRACT_TEST_IMAGE" \
                              ai-service
                        '''
                    }
                }

                stage('Backend Test and Package') {
                    steps {
                        dir('backend') {
                            sh './gradlew clean test infraTest bootJar --stacktrace --no-daemon'
                        }
                    }

                    post {
                        always {
                            junit(
                                testResults:
                                    'backend/build/test-results/**/*.xml'
                            )
                            archiveArtifacts(
                                artifacts:
                                    'backend/build/reports/jacoco/test/html/**',
                                allowEmptyArchive: true
                            )
                        }
                        success {
                            script {
                                publishBackendEvent('Backend test', 'TEST_SUCCEEDED')
                            }
                        }
                        failure {
                            script {
                                publishBackendEvent('Backend test', 'TEST_FAILED', 'ERROR')
                            }
                        }
                    }
                }

                stage('Build Backend Image') {
                    steps {
                        sh '''
                            docker build \
                              --pull \
                              --progress=plain \
                              -t "$BACKEND_IMAGE_TAG" \
                              backend
                        '''
                    }
                    post {
                        success {
                            script {
                                publishBackendEvent('Backend image build', 'BUILD_SUCCEEDED')
                            }
                        }
                        failure {
                            script {
                                publishBackendEvent('Backend image build', 'BUILD_FAILED', 'ERROR')
                            }
                        }
                    }
                }

                stage('Deploy Backend') {
                    steps {
                        script {
                            publishBackendEvent('Backend deploy', 'DEPLOY_STARTED')
                        }
                        withCredentials([
                            usernamePassword(
                                credentialsId: 'develop-postgres',
                                usernameVariable: 'POSTGRES_USER',
                                passwordVariable: 'POSTGRES_PASSWORD'
                            ),
                            usernamePassword(
                                credentialsId: 'develop-rabbitmq',
                                usernameVariable: 'RABBITMQ_USER',
                                passwordVariable: 'RABBITMQ_PASSWORD'
                            ),
                            string(
                                credentialsId: 'develop-redis-password',
                                variable: 'REDIS_PASSWORD'
                            ),
                            usernamePassword(
                                credentialsId: 'develop-admin-account',
                                usernameVariable: 'ADMIN_ACCOUNT_EMAIL',
                                passwordVariable: 'ADMIN_ACCOUNT_PASSWORD'
                            ),
                            usernamePassword(
                                credentialsId: 'develop-smtp',
                                usernameVariable: 'SMTP_USERNAME',
                                passwordVariable: 'SMTP_PASSWORD'
                            ),
                            usernamePassword(
                                credentialsId: 'develop-kakao-oauth',
                                usernameVariable: 'KAKAO_CLIENT_ID',
                                passwordVariable: 'KAKAO_CLIENT_SECRET'
                            ),
                            usernamePassword(
                                credentialsId: 'develop-naver-oauth',
                                usernameVariable: 'NAVER_CLIENT_ID',
                                passwordVariable: 'NAVER_CLIENT_SECRET'
                            ),
                            usernamePassword(
                                credentialsId: 'develop-google-oauth',
                                usernameVariable: 'GOOGLE_CLIENT_ID',
                                passwordVariable: 'GOOGLE_CLIENT_SECRET'
                            ),
                            string(
                                credentialsId: 'develop-ai-readonly-db-password',
                                variable: 'AI_READONLY_DB_PASSWORD'
                            )
                        ]) {
                            sh '''
                                set +x
                                umask 077
                                runtime_env="$(mktemp)"
                                diagnostics_dir="$WORKSPACE/deploy-diagnostics/backend"
                                cleanup_deploy() {
                                  result=$?
                                  trap - EXIT
                                  if [ -d "$diagnostics_dir" ] &&
                                     ! ./scripts/check-diagnostics-secrets.sh "$diagnostics_dir"; then
                                    result=1
                                  fi
                                  rm -f "$runtime_env"
                                  exit "$result"
                                }
                                trap cleanup_deploy EXIT

                                {
                                  printf '%s\n' \
                                    'POSTGRES_DB=mvp_backend' \
                                    "POSTGRES_USER=$POSTGRES_USER" \
                                    "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" \
                                    "REDIS_PASSWORD=$REDIS_PASSWORD" \
                                    "RABBITMQ_USER=$RABBITMQ_USER" \
                                    "RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD" \
                                    'RABBITMQ_VHOST=/' \
                                    "ADMIN_ACCOUNT_EMAIL=$ADMIN_ACCOUNT_EMAIL" \
                                    "ADMIN_ACCOUNT_PASSWORD=$ADMIN_ACCOUNT_PASSWORD" \
                                    "SMTP_USERNAME=$SMTP_USERNAME" \
                                    "SMTP_PASSWORD=$SMTP_PASSWORD" \
                                    "KAKAO_CLIENT_ID=$KAKAO_CLIENT_ID" \
                                    "KAKAO_CLIENT_SECRET=$KAKAO_CLIENT_SECRET" \
                                    "NAVER_CLIENT_ID=$NAVER_CLIENT_ID" \
                                    "NAVER_CLIENT_SECRET=$NAVER_CLIENT_SECRET" \
                                    "GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID" \
                                    "GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET" \
                                    'OAUTH2_REDIRECT_BASE=https://app.example.com/api' \
                                    "AI_READONLY_DB_PASSWORD=$AI_READONLY_DB_PASSWORD" \
                                    'AI_EVALUATION_CLIENT=http' \
                                    'AI_EVALUATION_BASE_URL=http://ai-service:8001' \
                                    'AI_EVALUATION_CONTRACT_VERSION=sentence-evaluation-v1'
                                } >"$runtime_env"

                                ./scripts/deploy-backend.sh \
                                  "$DEPLOY_BRANCH" \
                                  "$BACKEND_IMAGE_TAG" \
                                  "$GIT_COMMIT_SHORT" \
                                  "$runtime_env" \
                                  "$diagnostics_dir"
                            '''
                        }
                    }
                    post {
                        success {
                            script {
                                publishBackendEvent('Backend deploy', 'DEPLOY_SUCCEEDED')
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts(
                artifacts: 'deploy-diagnostics/backend/**',
                allowEmptyArchive: true
            )
        }

        success {
            echo "Backend pipeline completed: ${env.BACKEND_IMAGE_TAG}"
            script {
                publishBackendEvent('Pipeline', 'PIPELINE_SUCCEEDED')
            }
        }

        failure {
            echo '백엔드 빌드 또는 배포에 실패했습니다.'
            script {
                publishBackendEvent('Pipeline', 'PIPELINE_FAILED', 'ERROR')
            }
        }

        cleanup {
            deleteDir()
        }
    }
}
