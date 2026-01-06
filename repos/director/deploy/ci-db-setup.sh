#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <mariadb|docker> <host|container name>"
    echo "example: $0 mariadb 0.0"
    echo "example: $0 docker ota-mariadb"
    exit 1
fi

set -x

MYSQL_COMMAND=$1
HOST=$2

if [ "$MYSQL_COMMAND" = "mariadb" ]; then
    MYSQL=mariadb
else
    MYSQL="docker run -i --rm --link $HOST mariadb:10.11 mariadb"
fi

# If using local mysql and mysqladmin is also installed locally use it
if [[ "$MYSQL_COMMAND" == "mariadb" ]] && command -v mariadb-admin &> /dev/null;
then
    MYSQLADMIN=mariadb-admin
else
    MYSQLADMIN="docker run -i --rm --link $HOST mariadb:10.11 mariadb-admin"
fi

until $MYSQLADMIN ping --skip-ssl --silent --protocol=TCP -h $HOST -P 3306 -u root -proot; do
    echo waiting for mysql; sleep 1
done

$MYSQL --skip-ssl -v -h $HOST -u root -proot <<EOF
CREATE DATABASE IF NOT EXISTS director_v2;

CREATE DATABASE IF NOT EXISTS device_registry;

CREATE USER IF NOT EXISTS 'director_v2' IDENTIFIED BY 'director_v2';

GRANT ALL PRIVILEGES ON \`director%\`.* TO 'director_v2'@'%';

GRANT ALL PRIVILEGES ON \`device_registry%\`.* TO 'director_v2'@'%';

FLUSH PRIVILEGES;
EOF
