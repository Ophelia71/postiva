pipeline {
    agent any

    environment {
        DOCKER_BACKEND_IMAGE = "devhoa/postiva-backend"
        DOCKER_FRONTEND_IMAGE = "devhoa/postiva-frontend"
        SERVER_USER = "ubuntu"
        SERVER_IP = "13.250.34.100"
        SERVER_PROJECT_PATH = "/home/ubuntu/postiva"
    }

    stages {
        stage('CHECK_ENVIRONMENT') {
            steps {
                sh 'git --version && docker --version'
            }
        }

        stage('CHECKOUT') {
            steps {
                checkout scm
                script {
                    env.POSTIVA_IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(12)}"
                    echo "Release image tag: ${env.POSTIVA_IMAGE_TAG}"
                }
            }
        }

        stage('CI_GATE') {
            steps {
                sh '''
                    set -eu
                    TEST_DB_CONTAINER="postiva-ci-db-${BUILD_NUMBER}"
                    trap 'docker rm -f "$TEST_DB_CONTAINER" >/dev/null 2>&1 || true' EXIT
                    docker run --detach --rm --name "$TEST_DB_CONTAINER" \
                      -e POSTGRES_USER=postgres \
                      -e POSTGRES_DB=postiva_migration_test \
                      -e POSTGRES_HOST_AUTH_METHOD=trust \
                      postgres:16-alpine >/dev/null

                    attempt=0
                    until docker exec "$TEST_DB_CONTAINER" pg_isready -U postgres -d postiva_migration_test >/dev/null 2>&1; do
                      attempt=$((attempt + 1))
                      if [ "$attempt" -ge 30 ]; then
                        echo 'PostgreSQL test database did not become ready' >&2
                        exit 1
                      fi
                      sleep 2
                    done

                    docker run --rm \
                      --network "container:$TEST_DB_CONTAINER" \
                      -v "$WORKSPACE/backend:/workspace" \
                      -w /workspace \
                      -e POSTIVA_TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/postiva_migration_test \
                      -e POSTIVA_TEST_DB_USERNAME=postgres \
                      -e POSTIVA_TEST_DB_PASSWORD= \
                      maven:3.9.9-eclipse-temurin-21 \
                      mvn --batch-mode --no-transfer-progress verify

                    docker run --rm \
                      -v "$WORKSPACE:/workspace:ro" \
                      -w /workspace \
                      maven:3.9.9-eclipse-temurin-21 \
                      bash -n scripts/backup-production.sh scripts/restore-drill.sh

                    docker run --rm \
                      -v "$WORKSPACE/frontend:/workspace" \
                      -w /workspace \
                      node:22-alpine \
                      sh -lc 'npm ci && npm test && npm run lint && npm run build'
                '''
            }
        }

        stage('BUILD_IMAGES') {
            steps {
                sh '''
                    set -eu
                    docker build --pull \
                      -t "$DOCKER_BACKEND_IMAGE:$POSTIVA_IMAGE_TAG" \
                      ./backend
                    docker build --pull \
                      --build-arg VITE_API_BASE_URL=/api \
                      -t "$DOCKER_FRONTEND_IMAGE:$POSTIVA_IMAGE_TAG" \
                      ./frontend
                '''
            }
        }

        stage('PUSH_IMAGES') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USERNAME',
                    passwordVariable: 'DOCKER_PASSWORD'
                )]) {
                    sh '''
                        set -eu
                        trap 'docker logout >/dev/null 2>&1 || true' EXIT
                        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
                        docker push "$DOCKER_BACKEND_IMAGE:$POSTIVA_IMAGE_TAG"
                        docker push "$DOCKER_FRONTEND_IMAGE:$POSTIVA_IMAGE_TAG"
                    '''
                }
            }
        }

        stage('DEPLOY_AND_SMOKE_TEST') {
            steps {
                withCredentials([file(
                    credentialsId: 'postiva-production-ssh-key',
                    variable: 'SSH_KEY'
                )]) {
                    sh '''
                        set -eu
                        ssh -i "$SSH_KEY" \
                          -o StrictHostKeyChecking=yes \
                          "$SERVER_USER@$SERVER_IP" \
                          "bash -s -- '$POSTIVA_IMAGE_TAG' '$SERVER_PROJECT_PATH'" <<'REMOTE'
                    set -Eeuo pipefail
                    NEW_TAG="$1"
                    PROJECT_PATH="$2"
                    if [[ ! "$NEW_TAG" =~ ^[A-Za-z0-9_.-]+$ ]]; then
                      echo "Invalid release tag" >&2
                      exit 2
                    fi
                    cd "$PROJECT_PATH"

                    PREVIOUS_IMAGE="$(docker inspect --format '{{.Config.Image}}' postiva-backend 2>/dev/null || true)"
                    PREVIOUS_TAG="${PREVIOUS_IMAGE##*:}"
                    if [[ "$PREVIOUS_TAG" == "$PREVIOUS_IMAGE" ]]; then PREVIOUS_TAG=""; fi

                    rollback() {
                      if [[ -n "$PREVIOUS_TAG" && "$PREVIOUS_TAG" =~ ^[A-Za-z0-9_.-]+$ ]]; then
                        echo "Smoke check failed; restoring previous release $PREVIOUS_TAG"
                        POSTIVA_IMAGE_TAG="$PREVIOUS_TAG" docker compose up -d --no-deps backend frontend || return 1
                        for rollback_attempt in $(seq 1 18); do
                          rollback_backend="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unhealthy{{end}}' postiva-backend 2>/dev/null || echo missing)"
                          rollback_frontend="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unhealthy{{end}}' postiva-nginx 2>/dev/null || echo missing)"
                          if [[ "$rollback_backend" == "healthy" && "$rollback_frontend" == "healthy" ]]; then
                            echo "Previous release $PREVIOUS_TAG is healthy again"
                            return 0
                          fi
                          sleep 10
                        done
                        echo "Previous release did not become healthy; manual intervention is required" >&2
                        return 1
                      else
                        echo "No previous release was found; automatic rollback is unavailable" >&2
                        return 1
                      fi
                    }

                    POSTIVA_IMAGE_TAG="$NEW_TAG" docker compose pull backend frontend
                    if ! POSTIVA_IMAGE_TAG="$NEW_TAG" docker compose up -d --no-deps backend; then
                      rollback
                      exit 1
                    fi

                    BACKEND_HEALTH="starting"
                    for attempt in $(seq 1 30); do
                      BACKEND_HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unhealthy{{end}}' postiva-backend 2>/dev/null || echo missing)"
                      if [[ "$BACKEND_HEALTH" == "healthy" ]]; then break; fi
                      if [[ "$BACKEND_HEALTH" == "unhealthy" || "$BACKEND_HEALTH" == "missing" ]]; then
                        rollback
                        exit 1
                      fi
                      sleep 10
                    done
                    if [[ "$BACKEND_HEALTH" != "healthy" ]]; then
                      rollback
                      exit 1
                    fi

                    if ! POSTIVA_IMAGE_TAG="$NEW_TAG" docker compose up -d --no-deps frontend; then
                      rollback
                      exit 1
                    fi
                    FRONTEND_HEALTH="starting"
                    for attempt in $(seq 1 18); do
                      FRONTEND_HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unhealthy{{end}}' postiva-nginx 2>/dev/null || echo missing)"
                      if [[ "$FRONTEND_HEALTH" == "healthy" ]]; then break; fi
                      if [[ "$FRONTEND_HEALTH" == "unhealthy" || "$FRONTEND_HEALTH" == "missing" ]]; then
                        rollback
                        exit 1
                      fi
                      sleep 10
                    done
                    if [[ "$FRONTEND_HEALTH" != "healthy" ]]; then
                      rollback
                      exit 1
                    fi

                    POSTIVA_IMAGE_TAG="$NEW_TAG" docker compose ps
                    echo "Deployment $NEW_TAG passed backend and frontend health checks"
REMOTE
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Production deployment completed with immutable tag ${env.POSTIVA_IMAGE_TAG}"
        }
        failure {
            echo 'Pipeline failed. Check CI output and production health; deploy stage attempts rollback after failed smoke checks.'
        }
    }
}
