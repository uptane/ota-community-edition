#!/bin/bash

set -euo pipefail

DEVICE_UUID=${DEVICE_UUID:-$(uuidgen | tr "[:upper:]" "[:lower:]")}


CWD=$(dirname "$0")
CERTS_DIR=$CWD/certs

SERVER_DIR=$CWD/../ota-ce-gen
DEVICES_DIR=${SERVER_DIR}/devices

# if there's no gen-server-certs in this directory, we're probably not running
# the script from the repo. In that case, we can just pull the keys, etc. from
# the uptanedemo server.
if [ ! -f gen-server-certs.sh ]; then
  SERVER_DIR=$CWD/ota-ce-gen
  mkdir -p "${CWD}/certs"
  mkdir -p "${CWD}/ota-ce-gen"
  mkdir -p "${SERVER_DIR}/devices"

  wget -O ${CERTS_DIR}/client.cnf https://uptanedemo.org/client.cnf
  wget -O ${CERTS_DIR}/client.ext https://uptanedemo.org/client.ext
  wget -O ${SERVER_DIR}/server_ca.pem https://uptanedemo.org/server_ca.pem
  wget -O ${DEVICES_DIR}/ca.crt https://uptanedemo.org/ca.crt
  wget -O ${DEVICES_DIR}/ca.key https://uptanedemo.org/ca.key
fi


device_id=$DEVICE_UUID
device_dir="${DEVICES_DIR}/${DEVICE_UUID}"


mkdir -p "${device_dir}"

# create a private key for the device
openssl ecparam -genkey -name prime256v1 | openssl ec -out "${device_dir}/pkey.ec.pem"

# put the key in PKCS#8 format
openssl pkcs8 -topk8 -nocrypt -in "${device_dir}/pkey.ec.pem" -out "${device_dir}/pkey.pem"

# Create a certificate signing request using the private key you just created
openssl req -new -key "${device_dir}/pkey.pem" \
          -config <(sed "s/\$ENV::DEVICE_UUID/${DEVICE_UUID}/g" "${CERTS_DIR}/client.cnf") \
          -out "${device_dir}/${device_id}.csr"

# sign the device certificate using the server's device-signing certificate (ota-ce-get/devices/ca.{crt,key})
# This is the certificate that aktualizr has to present to dgw.uptanedemo.org to allow it to connect.
# You'll have to pull down ca.key and ca.crt from the server first.
openssl x509 -req -days 365 -extfile "${CERTS_DIR}/client.ext" -in "${device_dir}/${device_id}.csr" \
        -CAkey "${DEVICES_DIR}/ca.key" -CA "${DEVICES_DIR}/ca.crt" -CAcreateserial -out "${device_dir}/client.pem"

# This just creates a certificate chain. Not even 100% sure we need this.
cat "${device_dir}/client.pem" "${DEVICES_DIR}/ca.crt" > "${device_dir}/${device_id}.chain.pem"

# put the device gateway's cert into the device directory, so aktualizr can use it. Need to get from server
server_ca=$(realpath ${SERVER_DIR}/server_ca.pem)
ln -s "${server_ca}" "${device_dir}/ca.pem" || true

# print the client cert in human-readable form
openssl x509 -in "${device_dir}/client.pem" -text -noout

# store the pem-encoded device cert in a variable, so you can include it in the API call you'll make
# later requesting the device to be created
credentials="$(cat ${DEVICES_DIR}/${DEVICE_UUID}/client.pem | sed -z -r -e 's@\n@\\n@g')"

# construct the body of the API call to create the device
body=$(cat <<END
{"credentials":"${credentials}","deviceId":"${device_id}","deviceName":"${device_id}","deviceType":"Other","uuid":"${DEVICE_UUID}"}
END
    )

# This is an aktualizr config file. Note the relevant inputs, coming from the rest of the script:
#
#   * `base_path` is the current directory, so it's appropriate only for running aktualizr from the 
#     directory where all the certs got saved.
#   * `tls_cacert_path` is the server's certificate authority, originally from `ota-ce-gen/ca.pem`.
#     Will need to be downloaded from the server, where in line 37 it's just linked from the filesystem
#   * `tls_clientcert_path` is the client cert that this script generated already (output of line 28).
#   * `tls_pkey_path` is the private key of the client cert, generated on line 16 of this script.
#   * `server_url_path` is a string read from a file, for some reason. `gateway.url` is created on 
#      line 89 of this script
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

# This calls the API that adds a device to the Uptane server, using the body from line 47
curl -X PUT -d "${body}" https://deviceregistry.uptanedemo.org/api/v1/devices -s -S -v -H "Content-Type: application/json" -H "Accept: application/json, */*"

echo "https://dgw.uptanedemo.org:30443" > ${device_dir}/gateway.url
