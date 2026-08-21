# Spring Boot + PostgreSQL + Docker + Jenkins (fully Dockerized, zero local installs)

Everything runs in containers — the app, Postgres, **and Jenkins itself**.
You do not need Maven, a JDK, or Docker CLI configured on your host's shell/PATH.
The only host requirement is **Docker Desktop** running.

---

## 1. Project structure

```
springboot-postgres-app/
├── src/...                       # Spring Boot app source
├── pom.xml
├── Dockerfile                    # builds the app image
├── docker-compose.yml            # app + postgres (deployed BY Jenkins)
├── Jenkinsfile                   # CI/CD pipeline definition
├── jenkins-docker/
│   ├── Dockerfile                # custom Jenkins image: JDK17 + Maven + Docker CLI baked in
│   ├── plugins.txt                # plugins auto-installed into that image
│   └── docker-compose.yml        # runs the Jenkins container itself
└── README.md
```

---

## 2. Start Jenkins (one time, fully containerized)

```bash
cd jenkins-docker
docker compose up -d --build
```

This builds a **custom Jenkins image** (JDK 17 + Maven + Docker CLI + required
plugins already installed — see `jenkins-docker/Dockerfile` and `plugins.txt`)
and starts it, with the **host's Docker socket mounted in** so Jenkins can run
`docker build` / `docker compose` commands that create containers on your
host — without Docker being installed in your shell at all.

Wait ~30 seconds, then get the initial admin password:

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Open **http://localhost:8080**, paste that password in.
Since all required plugins are pre-baked into the image, you can skip the
"Install suggested plugins" step (or just let it run — it'll be fast since
most are already present) and go straight to **Create First Admin User**.

---

## 3. Add credentials in Jenkins

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

**A. Docker Hub** (for pushing images)
- Kind: `Username with password`
- Username: your Docker Hub username
- Password: a Docker Hub **access token** (Docker Hub → Account Settings →
  Security → New Access Token — safer than your real password)
- ID: `dockerhub-credentials` ← must match the `Jenkinsfile` exactly

**B. GitHub** (for pulling the repo)
- Kind: `Username with password`
- Username: your GitHub username
- Password: a GitHub **Personal Access Token** (GitHub → Settings →
  Developer settings → Personal access tokens → Tokens (classic) → scope: `repo`)
- ID: `github-credentials`

---

## 4. Update the Jenkinsfile with your own Docker Hub username

Open `Jenkinsfile`, change:
```groovy
IMAGE_NAME = "gannepakajeevankumar/demo-app"
```
to your own Docker Hub username, then commit & push to GitHub.

---

## 5. Create the Jenkins Pipeline job

1. Jenkins → **New Item** → name it `demo-app-pipeline` → type **Pipeline** → OK
2. **Build Triggers** → check ✅ **"GitHub hook trigger for GITScm polling"**
3. **Pipeline** section:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: `https://github.com/<your-username>/<your-repo>.git`
   - Credentials: select `github-credentials`
   - Branch Specifier: `*/main`
   - Script Path: `Jenkinsfile`
4. Save

---

## 6. Set up the GitHub webhook

GitHub needs to reach your Jenkins over the internet. Since Jenkins is on
`localhost:8080`, use a tunnel:

```bash
brew install ngrok
ngrok http 8080
```

Copy the printed `https://xxxx.ngrok-free.app` URL.

On GitHub → your repo → **Settings → Webhooks → Add webhook**:
- Payload URL: `https://xxxx.ngrok-free.app/github-webhook/`
- Content type: `application/json`
- Event: **Just the push event**
- Save

(Note: this URL changes each time you restart ngrok unless you have a paid
static domain — update the webhook if you restart it.)

---

## 7. Push a commit and watch it build automatically

```bash
git add .
git commit -m "trigger CI"
git push
```

Jenkins should start the build within seconds. The pipeline:
1. **Checkout** — pulls your latest commit
2. **Build (Maven)** — `mvn compile` (using the JDK17+Maven baked into the Jenkins image)
3. **Unit Tests** — `mvn test`, publishes JUnit results
4. **Package** — builds the jar
5. **Build Docker Image** — `docker build` via the mounted host socket
6. **Push Docker Image** — pushes to Docker Hub
7. **Deploy** — `docker compose up -d --build` starts the app + Postgres containers on your host
8. **Smoke Test** — hits `http://host.docker.internal:9090/actuator/health`

---

## 8. Access the running app

The app itself is exposed on **port 9090** (not 8080 — that port belongs to
Jenkins now):

```bash
curl http://localhost:9090/actuator/health
curl http://localhost:9090/api/employees
```

---

## Why `host.docker.internal` instead of `localhost` in the Jenkinsfile?

Jenkins runs **inside its own container**. When the pipeline runs
`docker compose up -d`, that creates the app + Postgres containers as
**siblings** on your host's Docker daemon — not nested inside the Jenkins
container. So from inside the Jenkins container, `localhost` refers to the
Jenkins container itself, not your Mac. `host.docker.internal` is Docker
Desktop's built-in DNS name that always resolves to the host machine from
inside any container, which is why the Smoke Test stage uses it.

## Notes
- This setup uses **Docker-outside-of-Docker** (mounting `/var/run/docker.sock`
  into the Jenkins container) rather than Docker-in-Docker — it's simpler and
  is why `docker build`/`docker compose` commands inside the pipeline "just work"
  without a nested Docker daemon.
- The Jenkins container runs as `root` for simplicity in this local/dev setup
  (avoids Docker socket permission mismatches). For a real production Jenkins,
  you'd lock this down with matching group IDs instead.
- Postgres and the app are **not** part of the `jenkins-docker/docker-compose.yml`
  — they're started separately by the pipeline's Deploy stage, using the
  top-level `docker-compose.yml`.
