#!/usr/bin/env bash

set -xeuo pipefail

MYSQL_COMMAND=$1
HOST=$2

if [ "$MYSQL_COMMAND" = "mysql" ]; then
    MYSQL=mysql
else
    MYSQL="docker run -i --rm --link $HOST mariadb:10.2 mysql"
fi

$MYSQL -v -h $HOST -u root -proot <<EOF
CREATE USER 'tuf_repo' identified by 'tuf_repo' ;
CREATE DATABASE tuf_repo ;
GRANT ALL PRIVILEGES ON \`tuf_repo%\`.* TO 'tuf_repo'@'%' ;

CREATE USER 'tuf_keyserver' identified by 'tuf_keyserver' ;
CREATE DATABASE tuf_keyserver ;
GRANT ALL PRIVILEGES ON \`tuf_keyserver%\`.* TO 'tuf_keyserver'@'%' ;

GRANT ALL PRIVILEGES ON ota_tuf.* TO 'tuf_repo'@'%' ;
GRANT ALL PRIVILEGES ON ota_tuf.* TO 'tuf_keyserver'@'%' ;

FLUSH PRIVILEGES

EOF
