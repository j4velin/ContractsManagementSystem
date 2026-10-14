#!/usr/bin/env bash
# End-to-end smoke test against a running instance of the app (e.g. started with `docker compose up -d --build`).
# Walks through registration, login, adding a contract, user separation and logout via plain HTTP requests.
#
# Usage: scripts/smoke-test.sh [base url]   (default: http://localhost:8080)
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

RUN_ID="$(date +%s)$RANDOM"
PASSWORD="smoke-$RUN_ID"
COMPANY="SmokeTestCompany$RUN_ID"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

pass() {
    echo "ok:   $*"
}

# curl with the cookie jar of the given client (one jar per simulated browser), English locale
client() {
    local jar="$WORK_DIR/$1.cookies"
    shift
    curl --silent --show-error --cookie "$jar" --cookie-jar "$jar" --header "Accept-Language: en" "$@"
}

# prints the CSRF token of the first form on the given page
csrf_token() {
    local page
    page="$(client "$1" "$BASE_URL$2")"
    grep -o -m 1 'name="_csrf" value="[^"]*"' <<< "$page" | sed 's/.*value="//; s/"$//' || true
}

# prints "<status code> <redirect location>" of a request, without following redirects
status_and_location() {
    client "$@" --output /dev/null --write-out '%{http_code} %{redirect_url}'
}

register() {
    local browser="$1" username="$2"
    local token
    token="$(csrf_token "$browser" /register)"
    [ -n "$token" ] || fail "no CSRF token on /register"
    client "$browser" "$BASE_URL/register" --write-out '\n%{http_code} %{redirect_url}' \
        --data-urlencode "_csrf=$token" --data-urlencode "username=$username" \
        --data-urlencode "mail=$username@example.com" --data-urlencode "password=$PASSWORD"
}

login() {
    local browser="$1" username="$2"
    local token
    token="$(csrf_token "$browser" /login)"
    status_and_location "$browser" "$BASE_URL/login" \
        --data-urlencode "_csrf=$token" --data-urlencode "username=$username" --data-urlencode "password=$PASSWORD"
}

logout() {
    local browser="$1"
    local token
    token="$(csrf_token "$browser" /contracts)"
    status_and_location "$browser" "$BASE_URL/logout" --data-urlencode "_csrf=$token"
}

contracts_page() {
    client "$1" "$BASE_URL/contracts"
}

echo "Waiting for $BASE_URL ..."
for _ in $(seq 1 60); do
    if [ "$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/login" || true)" = "200" ]; then
        break
    fi
    sleep 2
done
[ "$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/login")" = "200" ] \
    || fail "app not reachable at $BASE_URL"
pass "app is up"

# --- registration logs the new user in
result="$(register alice "alice$RUN_ID" | tail -n 1)"
[[ "$result" == 302\ * && "$result" != *register* ]] || fail "registration of alice failed: $result"
[ "$(status_and_location alice "$BASE_URL/contracts")" = "200 " ] || fail "alice not logged in after registration"
pass "register and auto-login"

# --- add a contract
token="$(csrf_token alice /contracts/add)"
result="$(status_and_location alice "$BASE_URL/contracts" \
    --data-urlencode "_csrf=$token" --data-urlencode "type=Internet" --data-urlencode "company=$COMPANY" \
    --data-urlencode "notes=created by smoke test" --data-urlencode "startDate=2020-01-15" \
    --data-urlencode "minDurationValue=2" --data-urlencode "minDurationUnit=Y" \
    --data-urlencode "noticePeriodValue=3" --data-urlencode "noticePeriodUnit=M" \
    --data-urlencode "extendPeriodValue=" --data-urlencode "extendPeriodUnit=")"
[[ "$result" == 302\ */contracts ]] || fail "adding a contract failed: $result"
[[ "$(contracts_page alice)" == *"$COMPANY"* ]] || fail "new contract not listed"
pass "add contract and list it"

# --- invalid input is rejected instead of crashing
token="$(csrf_token alice /contracts/add)"
result="$(status_and_location alice "$BASE_URL/contracts" \
    --data-urlencode "_csrf=$token" --data-urlencode "type=Broken" --data-urlencode "company=Broken" \
    --data-urlencode "noticePeriodValue=-1" --data-urlencode "noticePeriodUnit=M")"
[[ "$result" == 400\ * ]] || fail "invalid period not rejected with 400: $result"
pass "invalid input rejected with 400"

# --- a second user doesn't see alice's contracts
result="$(register bob "bob$RUN_ID" | tail -n 1)"
[[ "$result" == 302\ * && "$result" != *register* ]] || fail "registration of bob failed: $result"
page="$(contracts_page bob)"
[[ "$page" == *"<table"* ]] || fail "bob can't open the contracts page"
[[ "$page" != *"$COMPANY"* ]] || fail "bob can see alice's contract"
pass "users only see their own contracts"

# --- alice's export can be imported by bob, replacing his contracts on a second import
client alice "$BASE_URL/contracts/export" --fail --output "$WORK_DIR/export.csv" || fail "export failed"
grep -q "$COMPANY" "$WORK_DIR/export.csv" || fail "export doesn't contain alice's contract"
pass "export contracts as CSV"

import_csv() {
    local browser="$1"
    shift
    local token
    token="$(csrf_token "$browser" /contracts)"
    status_and_location "$browser" "$BASE_URL/contracts/import" \
        --form "_csrf=$token" --form "file=@$WORK_DIR/export.csv;type=text/csv" "$@"
}
result="$(import_csv bob)"
[[ "$result" == 302\ */contracts ]] || fail "import failed: $result"
page="$(contracts_page bob)"
[[ "$page" == *"1 contract(s) imported"* && "$page" == *"$COMPANY"* ]] || fail "imported contract not listed"
result="$(import_csv bob --form "replace=true")"
[[ "$result" == 302\ */contracts ]] || fail "import with replace failed: $result"
[ "$(contracts_page bob | grep -c "<td>$COMPANY</td>")" = "1" ] || fail "import with replace kept the old contracts"
pass "import contracts from CSV, also replacing the existing ones"

# --- registering an existing username is rejected
[[ "$(register carol "alice$RUN_ID")" == *"User already exists"* ]] || fail "duplicate username not rejected"
pass "duplicate username rejected"

# --- logout, then log in again: the contract is still there
result="$(logout alice)"
[[ "$result" == 302\ *logout* ]] || fail "logout failed: $result"
[[ "$(status_and_location alice "$BASE_URL/contracts")" == 302\ */login ]] || fail "contracts reachable after logout"
pass "logout"

result="$(login alice "alice$RUN_ID")"
[[ "$result" == 302\ * && "$result" != *error* ]] || fail "login of alice failed: $result"
[[ "$(contracts_page alice)" == *"$COMPANY"* ]] || fail "contract missing after logging in again"
pass "login again, contract still there"

result="$(PASSWORD=wrong login dave "alice$RUN_ID")"
[[ "$result" == 302\ *error* ]] || fail "login with wrong password not rejected: $result"
pass "wrong password rejected"

echo "All smoke tests passed"
