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
CREATE DATABASE IF NOT EXISTS ota_treehub;

CREATE USER IF NOT EXISTS 'treehub' IDENTIFIED BY 'treehub';

GRANT ALL PRIVILEGES ON \`ota\_treehub%\`.* TO 'treehub'@'%';
FLUSH PRIVILEGES;
EOF
