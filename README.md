# Contracts Management System
Spring Boot webapp to manage contracs, subscriptions etc.

<img width="800px" src="screenshot.png" />

## Run with Docker

Starts the app together with a MongoDB database (data is kept in the `mongo-data` volume):

```bash
cp .env.example .env   # then set REMEMBER_ME_KEY, e.g. to the output of: openssl rand -hex 32
docker compose up -d --build
```

The app is then available at http://localhost:8080 (change the port with `APP_PORT` in `.env`).
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
