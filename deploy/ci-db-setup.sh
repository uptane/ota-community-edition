#!/usr/bin/env bash

set -xeuo pipefail

MYSQL_COMMAND=mysql
HOST=$1
MYSQL=mysql
MYSQLADMIN=mysqladmin

until $MYSQLADMIN ping --silent --protocol=TCP -h $HOST -P 3306 -u root -proot; do
    echo waiting for mysql; sleep 1
done

$MYSQL -v -h $HOST -u root -proot <<EOF
CREATE DATABASE IF NOT EXISTS director_v2;

CREATE USER IF NOT EXISTS 'director_v2' IDENTIFIED BY 'director_v2';

GRANT ALL PRIVILEGES ON \`director%\`.* TO 'director_v2'@'%'; FLUSH PRIVILEGES;
EOF
