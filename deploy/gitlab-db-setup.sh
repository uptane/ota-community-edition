#!/usr/bin/env bash

set -xeuo pipefail

MYSQL_COMMAND=$1
HOST=$2

if [ "$MYSQL_COMMAND" = "mysql" ]; then
    MYSQL=mysql
else
    MYSQL="docker run -i --rm --link $HOST mariadb:10.4 mysql"
fi

# If using local mysql and mysqladmin is also installed locally use it
if [[ "$MYSQL_COMMAND" == "mysql" ]] && command -v mysqladmin &> /dev/null;
then
    MYSQLADMIN=mysqladmin
else
    MYSQLADMIN="docker run -i --rm --link $HOST mariadb:10.4 mysqladmin"
fi

until $MYSQLADMIN ping --silent --protocol=TCP -h $HOST -P 3306 -u root -proot; do echo waiting for mysql; sleep 1; done

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
