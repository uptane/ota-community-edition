#!/bin/bash

set -u

MARIADB_VERSION=10.11
docker rm --force ota_tuf-mariadb

# Some jobs don't behave, nuke them all
if [[ `docker ps -q | wc -l` -gt 0 ]]; then
    docker ps -q | xargs docker rm --force
fi

MYSQL_PORT=${MYSQL_PORT-3306}

function start_mariadb() {
    docker run -d \
           --name ota_tuf-mariadb \
           -p $MYSQL_PORT:3306 \
           -e MYSQL_ROOT_PASSWORD=root \
           -e MYSQL_USER=ota_tuf \
           -e MYSQL_PASSWORD=ota_tuf \
           mariadb:${MARIADB_VERSION} \
           --character-set-server=utf8 --collation-server=utf8_unicode_ci \
           --max_connections=1000
}

function mysqladmin_alive {
    docker run \
           --rm \
           --link ota_tuf-mariadb \
           mariadb:${MARIADB_VERSION} \
           mysqladmin ping --protocol=TCP -h ota_tuf-mariadb -P 3306 -u root -proot
}

function wait_for_mysql {
    local tries=60
    local timeout=1s

    for t in `seq $tries`; do
        res=$(mysqladmin_alive)
        if [[ $res =~ "mysqld is alive" ]]; then
            echo "mysql is ready"
            echo
            break
        else
            echo "Waiting for mariadb"
            echo
            sleep $timeout
        fi
    done
}

function start_nginx {
    local nginx=$(realpath cli/src/test/resources/nginx)
    local repodata=$(realpath cli/src/test/resources/fake-repo)

    docker rm --force tuf-cli-nginx || true

    docker run -d --name tuf-cli-nginx \
           --publish 8181:8181 \
           --volume $nginx/nginx.conf:/etc/nginx/nginx.conf \
           --volume $nginx:/cli-nginx \
           --volume $repodata:/data/html \
           nginx:stable
}

if [ $# -gt 0 ] && [ "$1" == "nginx-only" ]; then
    start_nginx
else
    start_nginx

    start_mariadb

    wait_for_mysql

    ${BASH_SOURCE%/*}/gitlab-db-setup.sh docker ota_tuf-mariadb
fi
