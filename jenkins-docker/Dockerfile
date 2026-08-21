# Custom Jenkins image: everything needed to build & deploy the Spring Boot app
# is baked in here. No local machine installs, no PATH hacking required.
FROM jenkins/jenkins:lts-jdk17

USER root

# Install Docker CLI + Compose plugin (talks to the HOST's Docker daemon via
# the mounted socket - this is "Docker outside of Docker", the standard,
# lightweight way to let a Jenkins container run docker/docker-compose commands
# without running a nested Docker daemon).
RUN apt-get update && \
    apt-get install -y ca-certificates curl gnupg lsb-release && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
      | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli docker-compose-plugin maven && \
    rm -rf /var/lib/apt/lists/*

# Run as root so the container can read the mounted docker.sock without
# fiddling with matching group IDs between host and container.
# (Fine for local dev; for production Jenkins you'd lock this down.)
USER root

# Pre-install the plugins we need so the setup wizard doesn't need manual clicking
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
