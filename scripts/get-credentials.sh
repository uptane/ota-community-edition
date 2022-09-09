#!/bin/bash

set -euox pipefail

SERVER_DIR=ota-ce-gen

namespace="x-ats-namespace:default"
keyserver="https://keyserver.uptanedemo.org"
reposerver="https://reposerver.uptanedemo.org"
director="https://director.uptanedemo.org"

curl --silent --fail ${director}/health/version || echo "$director not running"
curl --silent --fail ${keyserver}/health/version || echo "$keyserver not running"
curl --silent --fail ${reposerver}/health/version || echo "$reposerver not running"

curl -X POST "${reposerver}/api/v1/user_repo" -H "${namespace}"

# sleep 5s

id=$(curl --fail --silent -vv "${reposerver}/api/v1/user_repo/root.json" -H "${namespace}" 2>&1 | grep -i x-ats-tuf-repo-id | awk '{print $3}' | tr -d '\r')

curl --silent -X POST "${director}/api/v1/admin/repo" -H "${namespace}"

curl --silent "${keyserver}/api/v1/root/${id}"

curl --fail --silent "${reposerver}/api/v1/user_repo/root.json" -o ${SERVER_DIR}/root.json

keys=$(curl -s -f "${keyserver}/api/v1/root/${id}/keys/targets/pairs")
echo ${keys} | jq '.[0] | {keytype, keyval: {public: .keyval.public}}'   > "${SERVER_DIR}/targets.pub"
echo ${keys} | jq '.[0] | {keytype, keyval: {private: .keyval.private}}' > "${SERVER_DIR}/targets.sec"

echo "http://reposerver.uptanedemo.org" > "${SERVER_DIR}/tufrepo.url"
echo "http://uptanedemo.org:30443" > "${SERVER_DIR}/autoprov.url"

cat > "${SERVER_DIR}/treehub.json" <<END
{
    "no_auth": true,
    "ostree": {
        "server": "http://treehub.uptanedemo.org/api/v3/"
    }
}
END

zip --quiet --junk-paths ${SERVER_DIR}/{credentials.zip,autoprov.url,server_ca.pem,tufrepo.url,targets.pub,targets.sec,treehub.json,root.json}
