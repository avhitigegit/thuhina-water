# Thuhina Water – Sales, Inventory & Distribution System

One repository for the whole project.

| Folder | What it holds |
|---|---|
| `server/` | Spring Boot API – Java 21, Gradle, PostgreSQL, Flyway |
| `client/` | Next.js web app – TypeScript (added in M00 CLIENT part) |
| `deploy/` | `docker-compose.yml`, `nginx.conf`, `.env.example` |
| `doc/` | BRD, System Architecture & Design, Tech Stack, **Master Task Breakdown** (build plan and progress) |
| `prototype/` | Approved clickable prototype – the UI and business-rule reference |
| `.github/` | CI workflows and the pull request template |

## Software needed

- Java 21 (Temurin) · Docker Desktop · Git · Node.js 22 (for the client)
- Gradle is **not** needed – use the wrapper `server/gradlew`.

## Run everything with Docker

```bash
cd deploy
cp .env.example .env          # first time only – then set POSTGRES_PASSWORD and JWT_SECRET
docker compose up -d --build db api     # database + API (until the client exists)
# docker compose up -d --build          # all four: db, api, web, nginx (after M00 CLIENT part)
```

- API: http://localhost:8080/api  ·  Swagger UI (local and UAT only): http://localhost:8080/api/swagger-ui.html
- Through Nginx (after the client part): http://localhost → web, http://localhost/api → API
- Stop: `docker compose down` (add `-v` to delete the database too)

**Local / UAT logins** (seed data from the prototype, password `demo1234` for all):
`nimal.admin` (Admin) · `shanika.acc` (Accountant) · `kasun.d` (Delivery Staff) · `chamara.d` is deactivated.

**Production** starts with an empty database: set `ADMIN_USERNAME` and `ADMIN_TEMP_PASSWORD` in `deploy/.env` for the first
start; that Admin must change the password at the first login.

## Server development (without Docker for the API)

```bash
cd deploy && docker compose up -d db          # database only
cd ../server
./gradlew bootRun --args='--spring.profiles.active=local'   # needs DB_PASSWORD set to the value in deploy/.env
./gradlew test                                 # all tests – needs Docker running (Testcontainers)
```

Profiles: `local` (seed data, Swagger, cookie works on http) · `uat` (seed data, Swagger) · `prod` (no seed data, no Swagger).

## How we work

- Build order, tasks and progress: `doc/Master_Task_Breakdown.md`.
- Branches: `feature/Mnn-name` → `dev` (UAT) → `main` (production, tagged `vX.Y.Z`). Pull requests only; CI must pass.
- Commit messages: `M05: add customer registration validation`.
- Never commit secrets – `deploy/.env` stays on each machine / server.
