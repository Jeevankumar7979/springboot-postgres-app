# Spring Boot + PostgreSQL + Docker + Jenkins CI/CD

A minimal Employee CRUD REST API demonstrating:
- Spring Boot 3 + Spring Data JPA + **PostgreSQL only**
- Dockerized app (multi-stage Dockerfile) + `docker-compose` (app + Postgres)
- A Jenkins pipeline that **auto-triggers on every GitHub commit** via webhook, builds, tests, dockerizes, and redeploys

---

## 1. Project structure

```
springboot-postgres-app/
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java
│   ├── model/Employee.java
│   ├── repository/EmployeeRepository.java
│   └── controller/EmployeeController.java
├── src/main/resources/application.properties
├── src/test/java/com/example/demo/DemoApplicationTests.java
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── Jenkinsfile
└── .gitignore
```

## 2. Run locally with Docker Compose

```bash
docker compose up -d --build
```

This starts:
- `postgres` container on `5432` (db: `demodb`, user/pass: `postgres`/`postgres`)
- `app` container on `9090`, connected to postgres via env vars

Test it:
```bash
curl http://localhost:9090/actuator/health

curl -X POST http://localhost:9090/api/employees \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","department":"Engineering"}'

curl http://localhost:9090/api/employees
```

## 3. Run locally without Docker (for dev)

Start Postgres yourself, then:
```bash
export DB_HOST=localhost DB_PORT=5432 DB_NAME=demodb DB_USERNAME=postgres DB_PASSWORD=postgres
mvn spring-boot:run
```

---

## 4. Push to GitHub

```bash
git init
git add .
git commit -m "Initial commit: Spring Boot + Postgres + Docker + Jenkins"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

---

## 5. Jenkins setup (auto-trigger on every commit)

### A. Install required Jenkins plugins
- **GitHub** plugin
- **GitHub Integration** plugin (provides the `githubPush()` trigger used in the `Jenkinsfile`)
- **Docker Pipeline** plugin
- **Pipeline** plugin (usually installed by default)

### B. Create the Jenkins job
1. Jenkins → **New Item** → *Pipeline* (or *Multibranch Pipeline* if you want PR/branch builds too).
2. Under **Pipeline**, choose **Pipeline script from SCM** → SCM: **Git**.
3. Repository URL: your GitHub repo URL.
4. Credentials: add a GitHub personal access token or SSH key if the repo is private.
5. Script Path: `Jenkinsfile` (already at repo root).
6. Under **Build Triggers**, check **"GitHub hook trigger for GITScm polling"**.

### C. Add Docker Hub credentials (for image push stage)
Jenkins → **Manage Jenkins → Credentials → System → Global credentials**
- Add **Username with password** credentials
- ID: `dockerhub-credentials` (must match the ID used in `Jenkinsfile`)

Also update `IMAGE_NAME` in `Jenkinsfile` to your own Docker Hub repo, e.g. `yourdockerhubuser/demo-app`.

### D. Configure the GitHub webhook
On GitHub → your repo → **Settings → Webhooks → Add webhook**:
- Payload URL: `http://<your-jenkins-host>:8080/github-webhook/`
  (must be reachable from GitHub — use ngrok or a public IP/domain if Jenkins is local)
- Content type: `application/json`
- Event: **Just the push event**
- Save

Now: **every `git push` to GitHub → GitHub calls the webhook → Jenkins job triggers automatically → pipeline runs.**

### E. What the pipeline does (`Jenkinsfile`)
1. **Checkout** – pulls latest commit from GitHub
2. **Build** – `mvn compile`
3. **Unit Tests** – `mvn test` + publishes JUnit results
4. **Package** – builds the jar
5. **Build Docker Image** – builds image from `Dockerfile`
6. **Push Docker Image** – pushes to Docker Hub (tagged with build number + `latest`)
7. **Deploy** – `docker compose up -d --build` (recreates app+postgres containers with the new image)
8. **Smoke Test** – hits `/actuator/health` to confirm the new deployment is healthy

If any stage fails, the pipeline stops and marks the build red — nothing broken gets deployed.

---

## 6. Notes
- The database is **PostgreSQL only** — no H2 or in-memory DB is used anywhere, including tests (the sample test is a lightweight placeholder so `mvn test` doesn't need a live DB in the build stage; wire up Testcontainers-Postgres if you want full integration tests).
- `spring.jpa.hibernate.ddl-auto=update` auto-creates the `employees` table on first run — fine for demos; use Flyway/Liquibase migrations for production.
- Jenkins agent running the pipeline needs Docker + Maven (or the `maven:3.9-eclipse-temurin-17` image via a Docker agent) available on the host.
