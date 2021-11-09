#!/usr/bin/env bash

set -xeuo pipefail

rm -vrf repo nginx

cp -r ../cli/src/test/resources/nginx nginx

cp -r ../cli/src/test/resources/fake-repo repo

docker build --file ci.nginx.Dockerfile . -t advancedtelematic/tuf-nginx:latest -t uptane/tuf-nginx:latest
