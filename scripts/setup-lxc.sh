#!/usr/bin/env bash
# Installs the app natively (without Docker) in a Debian 12 container or VM, e.g. a Proxmox LXC created with:
#   pct create 120 local:vztmpl/debian-12-standard_12.7-1_amd64.tar.zst --hostname contracts --cores 2 \
#     --memory 2048 --rootfs local-lvm:16 --net0 name=eth0,bridge=vmbr0,ip=dhcp --unprivileged 1 --onboot 1
#
# Sets up Java 21 (Temurin), MongoDB 8.0 (only reachable from inside the container) and a systemd service
# "contracts" that runs the app as its own user.
#
# Usage, as root inside the container, from a clone of this repository:
#   scripts/setup-lxc.sh             builds the app from this checkout
#   scripts/setup-lxc.sh app.jar     uses an already built jar instead (built without a local application.properties)
#
# Settings for the first run (same names as in .env.example), e.g. APP_PORT=80 scripts/setup-lxc.sh:
#   APP_PORT (8080), TIMEZONE (Europe/Berlin), FORWARD_HEADERS_STRATEGY (native)
# They are written to /etc/contracts/contracts.env, together with a generated remember-me key. Later runs keep
# that file, so to update the app: git pull && scripts/setup-lxc.sh
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR=/opt/contracts
CONFIG_DIR=/etc/contracts
ENV_FILE="$CONFIG_DIR/contracts.env"
SERVICE_USER=contracts
JAR="${1:-}"

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

step() {
    echo
    echo "==> $*"
}

[ "$(id -u)" -eq 0 ] || fail "run as root"
[ -z "$JAR" ] || [ -f "$JAR" ] || fail "jar not found: $JAR"
[ -z "$JAR" ] || JAR="$(realpath "$JAR")"

# shellcheck source=/dev/null
. /etc/os-release
[ "${VERSION_CODENAME:-}" = bookworm ] || fail "needs Debian 12 (bookworm), found ${PRETTY_NAME:-unknown}"
# MongoDB 5.0 and newer only runs on CPUs with AVX; on Proxmox the CPU type of a VM must pass it through
grep -qw avx /proc/cpuinfo || fail "the CPU doesn't support AVX, which MongoDB 8.0 requires"

step "Installing packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q curl gnupg ca-certificates
install -d -m 755 /etc/apt/keyrings

curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | gpg --dearmor --yes -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" \
    > /etc/apt/sources.list.d/adoptium.list

curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc \
    | gpg --dearmor --yes -o /etc/apt/keyrings/mongodb-server-8.0.gpg
echo "deb [signed-by=/etc/apt/keyrings/mongodb-server-8.0.gpg] http://repo.mongodb.org/apt/debian bookworm/mongodb-org/8.0 main" \
    > /etc/apt/sources.list.d/mongodb-org-8.0.list

apt-get update -q
# the JDK is only needed for building; the JRE is enough to run a given jar
if [ -n "$JAR" ]; then
    apt-get install -y -q temurin-21-jre mongodb-org
else
    apt-get install -y -q temurin-21-jdk mongodb-org
fi
JAVA="$(ls -d /usr/lib/jvm/temurin-21-*/bin/java | head -n 1)"
[ -x "$JAVA" ] || fail "Java 21 not found under /usr/lib/jvm"

step "Starting MongoDB"
# the default /etc/mongod.conf binds to 127.0.0.1, so only the app can reach the database
systemctl enable --now mongod

if [ -z "$JAR" ]; then
    step "Building the app"
    [ -f "$REPO_DIR/gradlew" ] || fail "no gradlew in $REPO_DIR; pass a jar instead"
    # a local application.properties would end up in the jar, with its port and database settings
    [ ! -f "$REPO_DIR/src/main/resources/application.properties" ] \
        || echo "WARNING: src/main/resources/application.properties is packaged into the jar" >&2
    (cd "$REPO_DIR" && JAVA_HOME="$(dirname "$(dirname "$JAVA")")" ./gradlew --no-daemon bootJar -x test)
    JAR="$(find "$REPO_DIR/build/libs" -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
    [ -n "$JAR" ] || fail "no jar found in $REPO_DIR/build/libs"
fi

step "Installing the app to $INSTALL_DIR"
id "$SERVICE_USER" > /dev/null 2>&1 || useradd --system --home-dir "$INSTALL_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
install -d -m 755 "$INSTALL_DIR"
install -m 644 "$JAR" "$INSTALL_DIR/app.jar"

if [ -f "$ENV_FILE" ]; then
    echo "keeping the existing settings in $ENV_FILE"
else
    install -d -m 750 -g "$SERVICE_USER" "$CONFIG_DIR"
    (
        umask 027
        cat > "$ENV_FILE" <<EOF
# Settings of the contracts service, read by systemd. Restart after changes: systemctl restart contracts
SERVER_PORT=${APP_PORT:-8080}
SPRING_MONGODB_URI=mongodb://127.0.0.1:27017/contracts
# creates the unique index on the username
SPRING_DATA_MONGODB_AUTO_INDEX_CREATION=true
# secret for signing remember-me cookies; keep it stable, otherwise remembered logins are invalidated
CONTRACTS_REMEMBERME_KEY=$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')
# behind a reverse proxy: use its X-Forwarded-* headers; "none" if the app is reachable directly
SERVER_FORWARD_HEADERS_STRATEGY=${FORWARD_HEADERS_STRATEGY:-native}
# time zone of the app: decides which contracts are active today and the cancel-by dates
TZ=${TIMEZONE:-Europe/Berlin}
EOF
    )
    chgrp "$SERVICE_USER" "$ENV_FILE"
fi

step "Installing the systemd service"
cat > /etc/systemd/system/contracts.service <<EOF
[Unit]
Description=Contracts Management System
After=network-online.target mongod.service
Wants=network-online.target
Requires=mongod.service

[Service]
User=$SERVICE_USER
Group=$SERVICE_USER
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$ENV_FILE
ExecStart=$JAVA -jar $INSTALL_DIR/app.jar
# Spring Boot exits with 143 on SIGTERM
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable contracts
systemctl restart contracts

step "Waiting for the app"
PORT="$(sed -n 's/^SERVER_PORT=//p' "$ENV_FILE")"
for _ in $(seq 1 60); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/login" || true)" != 000 ]; then
        echo "The app is running on port $PORT. Logs: journalctl -u contracts -f"
        exit 0
    fi
    sleep 2
done
fail "the app didn't respond on port $PORT within 2 minutes, see: journalctl -u contracts"
