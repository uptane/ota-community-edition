#!/usr/bin/env bash

set -euo pipefail

MARIADB_VERSION=${MARIADB_VERSION:-10.11}
CONTAINER_NAME=${CONTAINER_NAME:-ota-lith-mariadb}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-root}

# Remove existing container if it exists
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Removing existing container: ${CONTAINER_NAME}"
    docker rm --force "${CONTAINER_NAME}" > /dev/null 2>&1 || true
fi

echo "Starting MariaDB container: ${CONTAINER_NAME}"
docker run -d \
    --name "${CONTAINER_NAME}" \
    -p "${MYSQL_PORT}:3306" \
    -e MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
    mariadb:${MARIADB_VERSION} \
    --character-set-server=utf8 \
    --collation-server=utf8_unicode_ci \
    --max-connections=10000

echo "Waiting for MariaDB to be ready..."

function mysqladmin_alive() {
    docker exec "${CONTAINER_NAME}" \
        mysqladmin ping --protocol=TCP -h localhost -P 3306 -u root -p"${MYSQL_ROOT_PASSWORD}" \
        > /dev/null 2>&1 || return 1
}

function wait_for_mysql() {
    local tries=60
    local timeout=1s

    for t in $(seq $tries); do
        if mysqladmin_alive > /dev/null 2>&1; then
            echo "MariaDB is ready!"
            return 0
        else
            echo "Waiting for MariaDB... (attempt $t/$tries)"
            sleep $timeout
        fi
    done

    echo "ERROR: MariaDB failed to start within expected time"
    return 1
}

wait_for_mysql

echo "Setting up databases and users..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SQL_FILE="${PROJECT_ROOT}/db-bootstrap/01-create-databases.sql"

if [ ! -f "${SQL_FILE}" ]; then
    echo "ERROR: SQL file not found: ${SQL_FILE}"
    exit 1
fi

# Execute the SQL file to create databases and users
docker exec -i "${CONTAINER_NAME}" \
    mariadb --skip-ssl -u root -p"${MYSQL_ROOT_PASSWORD}" < "${SQL_FILE}"

echo "Database setup complete!"
echo ""
echo "Connection details:"
echo "  Host: localhost"
echo "  Port: ${MYSQL_PORT}"
echo "  Root password: ${MYSQL_ROOT_PASSWORD}"
echo ""
echo "To connect:"
echo "  docker exec -it ${CONTAINER_NAME} mariadb -u root -p${MYSQL_ROOT_PASSWORD}"
echo "  or"
echo "  mariadb -h 127.0.0.1 -P ${MYSQL_PORT} -u root -p${MYSQL_ROOT_PASSWORD}"
