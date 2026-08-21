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

        stage('Deploy to Kubernetes') {
            steps {
                sh """
                    # Apply everything except the app Deployment first (namespace,
                    # secret, PVC, postgres, service) - these rarely change.
                    kubectl apply -f k8s/00-namespace.yaml
                    kubectl apply -f k8s/01-postgres-secret.yaml
                    kubectl apply -f k8s/02-postgres-pvc.yaml
                    kubectl apply -f k8s/03-postgres-deployment.yaml
                    kubectl apply -f k8s/04-postgres-service.yaml
                    kubectl apply -f k8s/05-app-deployment.yaml
                    kubectl apply -f k8s/06-app-service.yaml

                    # Point the Deployment at the image we just built & pushed
                    # this build, then wait for the rollout to actually finish
                    # (old pods drained, new pods Ready) before smoke testing.
                    kubectl -n demo-app set image deployment/demo-app demo-app=${IMAGE_NAME}:${IMAGE_TAG}
                    kubectl -n demo-app rollout status deployment/demo-app --timeout=120s
                    kubectl -n demo-app rollout status deployment/postgres --timeout=120s
                """
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    sleep 10
                    # Docker Desktop's Kubernetes maps a LoadBalancer Service's
                    # external IP straight to localhost - but from INSIDE the
                    # Jenkins container, "localhost" means the Jenkins container
                    # itself, so we reach the host (and therefore the k8s
                    # LoadBalancer) via host.docker.internal instead.
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
