#!/usr/bin/env bash
set -ex
IMAGE_NAME=$1
mkdir ./$CI_COMMIT_SHA
docker build --build-arg CI_COMMIT_SHA=$CI_COMMIT_SHA -t $IMAGE_NAME .
docker create --name "app" "$IMAGE_NAME"
docker cp app:/usr/app/js ./
cp ./js/vendor* ./js/runtime* ./$CI_COMMIT_SHA/
if [ "$CI_COMMIT_BRANCH" = "master" ]; then
    aws s3 cp ./$CI_COMMIT_SHA s3://tzn-pilot-cdn-ota/js/$CI_COMMIT_SHA --recursive
else
    aws s3 cp ./$CI_COMMIT_SHA s3://tzn-dev-cdn-ota/js/$CI_COMMIT_SHA --recursive
fi
docker push "$IMAGE_NAME"