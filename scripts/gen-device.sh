#!/bin/bash

set -euo pipefail

DEVICE_UUID=${DEVICE_UUID:-$(uuidgen | tr "[:upper:]" "[:lower:]")}
CWD=$(dirname "$0")

#BEGIN UPTANE DEMO DIR CONF
#END UPTANE DEMO DIR CONF
#BEGIN CE DIR CONF
SERVER_DIR=$CWD/../ota-ce-gen
DEVICES_DIR=${SERVER_DIR}/devices
#END CE DIR CONF

device_id=$DEVICE_UUID
device_dir="${DEVICES_DIR}/${DEVICE_UUID}"

mkdir -p "${device_dir}"

openssl ecparam -genkey -name prime256v1 | openssl ec -out "${device_dir}/pkey.ec.pem"
openssl pkcs8 -topk8 -nocrypt -in "${device_dir}/pkey.ec.pem" -out "${device_dir}/pkey.pem"

openssl req -new -key "${device_dir}/pkey.pem" \
          -config <(sed "s/\$ENV::DEVICE_UUID/${DEVICE_UUID}/g" "${CWD}/certs/client.cnf") \
          -out "${device_dir}/${device_id}.csr"

openssl x509 -req -days 365 -extfile "${CWD}/certs/client.ext" -in "${device_dir}/${device_id}.csr" \
        -CAkey "${DEVICES_DIR}/ca.key" -CA "${DEVICES_DIR}/ca.crt" -CAcreateserial -out "${device_dir}/client.pem"

cat "${device_dir}/client.pem" "${DEVICES_DIR}/ca.crt" > "${device_dir}/${device_id}.chain.pem"

server_ca=$(realpath ${SERVER_DIR}/server_ca.pem)
ln -s "${server_ca}" "${device_dir}/ca.pem" || true

openssl x509 -in "${device_dir}/client.pem" -text -noout

credentials="$(cat ${DEVICES_DIR}/${DEVICE_UUID}/client.pem | sed -z -r -e 's@\n@\\n@g')"

body=$(cat <<END
{"credentials":"${credentials}","deviceId":"${device_id}","deviceName":"${device_id}","deviceType":"Other","uuid":"${DEVICE_UUID}"}
END
    )

cat > ${device_dir}/config.toml <<EOF
[provision]
primary_ecu_hardware_id = "ota-ce-device"

[tls]
server_url_path = "gateway.url"

[logger]
loglevel = 0

[storage]
path = "storage"

[pacman]
type = "none"
images_path = "storage/images"

[import]
base_path = "."
tls_cacert_path = "ca.pem"
tls_clientcert_path = "client.pem"
tls_pkey_path = "pkey.pem"
EOF

#BEGIN UPTANE DEMO API CONF
#END UPTANE DEMO API CONF
#BEGIN CE API CONF
curl -X PUT -d "${body}" http://deviceregistry.ota.ce/api/v1/devices -s -S -v -H "Content-Type: application/json" -H "Accept: application/json, */*"

echo "https://ota.ce:30443" > ${device_dir}/gateway.url
#END CE API CONF

