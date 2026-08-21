pipeline {
    agent any
    // ^ "any" now safely means: run inside the Jenkins container itself,
    // which already has JDK 17, Maven, and the Docker CLI baked in
    // (see jenkins-docker/Dockerfile). No host machine setup required.

    // Requires the "GitHub Integration" / "GitHub" plugin (already
    // pre-installed via jenkins-docker/plugins.txt).
    // Makes the job start automatically whenever GitHub sends a push webhook
    // to http://<your-jenkins-host>:8080/github-webhook/
    triggers {
        githubPush()
    }

    environment {
        IMAGE_NAME        = "gannepakajeevankumar/demo-app"
        IMAGE_TAG         = "${env.BUILD_NUMBER}"
        DOCKERHUB_CREDS   = credentials('dockerhub-credentials')   // Jenkins credential ID
        COMPOSE_PROJECT   = "demo"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                echo "Checking out source from GitHub..."
                checkout scm
            }
        }

        stage('Build (Maven)') {
            steps {
                sh 'mvn -B clean compile'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    dockerImage = docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-credentials') {
                        dockerImage.push("${IMAGE_TAG}")
                        dockerImage.push("latest")
                    }
                }
            }
        }

        stage('Deploy (docker-compose)') {
            steps {
                sh """
                    docker compose -p ${COMPOSE_PROJECT} down || true
                    docker compose -p ${COMPOSE_PROJECT} up -d --build
                """
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    sleep 15
                    # NOTE: Jenkins is running inside its own container, and the app
                    # container is a SIBLING container on the host (not nested inside
                    # Jenkins), so we must reach it via host.docker.internal, not
                    # localhost - localhost from inside the Jenkins container means
                    # the Jenkins container itself, not the host machine.
                    curl -f http://host.docker.internal:9090/actuator/health || (echo "Health check failed" && exit 1)
                '''
            }
        }
    }

    post {
        success {
            echo "✅ Build #${env.BUILD_NUMBER} deployed successfully."
        }
        failure {
            echo "❌ Build #${env.BUILD_NUMBER} failed. Check logs above."
        }
        always {
            sh 'docker image prune -f || true'
        }
    }
}
