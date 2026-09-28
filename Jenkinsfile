pipeline {

    agent any

    environment {

        DOCKER_BACKEND_IMAGE = "devhoa/postiva-backend"
        DOCKER_FRONTEND_IMAGE = "devhoa/postiva-frontend"

        SERVER_USER = "ubuntu"
        SERVER_IP = "13.250.34.100"

        SERVER_PROJECT_PATH = "/home/ubuntu/postiva"

        SSH_KEY = "/var/lib/jenkins/.ssh/aws-project-key.pem"
    }

    stages {

        stage('CHECK_ENVIRONMENT') {

            steps {

                echo 'Checking environment...'

                sh '''
                    git --version
                    docker --version
                '''
            }
        }


        stage('CHECKOUT') {

            steps {

                echo 'Pulling source code...'

                checkout scm
            }
        }


        stage('BUILD_BACKEND_IMAGE') {

            steps {

                echo 'Building backend Docker image...'

                sh '''
                    docker build \
                    -t $DOCKER_BACKEND_IMAGE:$BUILD_NUMBER \
                    -t $DOCKER_BACKEND_IMAGE:latest \
                    ./backend
                '''
            }
        }


        stage('BUILD_FRONTEND_IMAGE') {

            steps {

                echo 'Building frontend Docker image...'

                sh '''
                    docker build \
                    --build-arg VITE_API_BASE_URL=/api \
                    -t $DOCKER_FRONTEND_IMAGE:$BUILD_NUMBER \
                    -t $DOCKER_FRONTEND_IMAGE:latest \
                    ./frontend
                '''
            }
        }


        stage('PUSH_IMAGE') {

            steps {

                echo 'Pushing images to Docker Hub...'

                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {

                    sh '''

                        echo "$DOCKER_PASSWORD" | \
                        docker login \
                        -u "$DOCKER_USERNAME" \
                        --password-stdin


                        docker push \
                        $DOCKER_BACKEND_IMAGE:$BUILD_NUMBER

                        docker push \
                        $DOCKER_BACKEND_IMAGE:latest


                        docker push \
                        $DOCKER_FRONTEND_IMAGE:$BUILD_NUMBER

                        docker push \
                        $DOCKER_FRONTEND_IMAGE:latest


                        docker logout
                    '''
                }
            }
        }


        stage('CONNECT_SERVER') {

            steps {

                echo 'Deploying to production server...'

                sh '''

                    ssh \
                    -i $SSH_KEY \
                    -o StrictHostKeyChecking=yes \
                    $SERVER_USER@$SERVER_IP "

                        set -e

                        cd $SERVER_PROJECT_PATH

                        echo 'Pulling latest images...'

                        docker compose pull backend frontend


                        echo 'Starting services...'

                        docker compose up -d


                        echo 'Removing unused images...'

                        docker image prune -f


                        echo 'Current containers:'

                        docker compose ps

                    "
                '''
            }
        }

    }


    post {

        success {

            echo '=============================='
            echo 'DEPLOY SUCCESS'
            echo '=============================='
        }

        failure {

            echo '=============================='
            echo 'DEPLOY FAILED'
            echo '=============================='
        }

        always {

            sh '''
                docker image prune -f || true
            '''
        }
    }
}