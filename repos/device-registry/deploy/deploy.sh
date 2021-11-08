#!/bin/bash
set -e

if [[ "$1" == "" ]]; then
    echo "usage: $0 <docker_tag>"
    exit -1
fi

export DOCKER_TAG=$1
export JOB_NAME="${JOB_NAME-campaigner}"
export VAULT_SECRET=$(echo $JOB_NAME | tr "-" "_")
export VAULT_ENDPOINT="http://secrets.prod01.internal.advancedtelematic.com:8200/v1/secret/${VAULT_SECRET}"
export IMAGE_NAME="campaigner"
export REGISTRY="advancedtelematic"
export IMAGE_ARTIFACT=${REGISTRY}/${IMAGE_NAME}:${DOCKER_TAG}
export USE_MEM="1024.0"
export USE_CPU="0.5"
export JAVA_OPTS="-Xmx800m"

# Merge service environment variables with secrets from this vault endpoint.
export MARATHON="http://marathon.prod01.internal.advancedtelematic.com:8080"

cat deploy/service.json |
    envsubst |
    curl --show-error --silent --fail \
         --header "X-Vault-Token: ${VAULT_TOKEN}" \
         --request POST \
         --data @- \
         ${CATALOG_ADDR}/service/${VAULT_ENDPOINT}
