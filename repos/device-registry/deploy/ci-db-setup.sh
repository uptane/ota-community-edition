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
CREATE DATABASE device_registry_test; CREATE DATABASE device_registry_ptest;

GRANT ALL PRIVILEGES ON
  \`device\_registry%\`.* TO 'device_registry'@'%';

FLUSH PRIVILEGES;
EOF
