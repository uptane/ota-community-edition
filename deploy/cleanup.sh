#!/bin/bash
set -eu

docker rm --force mariadb-device-registry || true
