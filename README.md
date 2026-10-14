# Contracts Management System
Spring Boot webapp to manage contracs, subscriptions etc.

<img width="800px" src="screenshot.png" />

## Run with Docker

Starts the app together with a MongoDB database (data is kept in the `mongo-data` volume). For every version tag
(e.g. `v1.2.0`), CI publishes a ready-built image to `ghcr.io/j4velin/contractsmanagementsystem`, so a server only
needs to download it:

```bash
cp .env.example .env   # then set REMEMBER_ME_KEY, e.g. to the output of: openssl rand -hex 32
docker compose pull && docker compose up -d
```

The same two commands (after a `git pull`, which keeps the compose file up to date) update to the latest released
version. To release a version, tag a commit on `main` and push the tag; the image is published once the tests and
the smoke test passed for it:

```bash
git tag v1.2.0 && git push origin v1.2.0
```
To build the image from the checkout instead, e.g. for local changes, use `docker compose up -d --build`.

The app is then available at http://localhost:8080 (change the port with `APP_PORT` in `.env`). Behind a reverse
proxy on the same server, use `APP_PORT=127.0.0.1:8081`, so the app is only reachable through the proxy: Docker opens
published ports past host firewalls like ufw.
The default memory limits (`APP_MAX_HEAP`, `MONGO_CACHE_GB`) suit a server with 2 GB of RAM.
Dates such as the cancel-by date are based on today's date in the `Europe/Berlin` time zone (change it with `TIMEZONE` in `.env`).
The database isn't reachable from outside the compose network.

`scripts/smoke-test.sh` runs an end-to-end check (registration, login, contracts, logout) against a running instance:

```bash
scripts/smoke-test.sh http://localhost:8080
```

## Run without Docker

Requires Java 21. Copy `src/main/resources/application.properties.sample` to `application.properties`, set the MongoDB URI, then:

```bash
./gradlew bootRun
```
