# Deploy an update using the API

These steps use the services directly through the reverse proxy. The
default ota-lith configuration runs without authentication; all requests
use the `default` namespace.

    NS='x-ats-namespace: default'

## Prerequisites

1. Run `scripts/get-credentials.sh`. You don't need the credentials
   themselves for the API calls below, but the script creates the image
   and director repositories on the server.

2. Provision a device with `scripts/gen-device.sh` and run `aktualizr`
   once so it registers its ECUs. Note the device uuid (the name of the
   directory under `ota-ce-gen/devices/`). `gen-device.sh` uses the
   hardware id `ota-ce-device`.

       DEVICE=<device uuid>
       HWID=ota-ce-device

## Upload a target to the image repository

Any file works as a test payload. Reposerver stores the file and signs
the new `targets.json` with its online targets key.

    echo "hello from ota-ce" > /tmp/mypkg-0.0.1.bin
    HASH=$(sha256sum /tmp/mypkg-0.0.1.bin | cut -d' ' -f1)
    LEN=$(stat -c%s /tmp/mypkg-0.0.1.bin)

    curl -sf -X PUT -H "$NS" \
      "http://reposerver.ota.ce/api/v1/user_repo/targets/mypkg_0.0.1?name=mypkg&version=0.0.1&hardwareIds=$HWID" \
      -F file=@/tmp/mypkg-0.0.1.bin

Check that the target is listed:

    curl -s -H "$NS" http://reposerver.ota.ce/api/v1/user_repo/targets.json | jq '.signed.targets["mypkg_0.0.1"]'

## Assign the update to a device

The keys of `targets` are ECU hardware ids. The response is the id of
the new update.

    curl -s -X POST -H "$NS" -H 'Content-Type: application/json' \
      "http://director.ota.ce/api/v1/updates/devices/$DEVICE" -d @- <<EOF
    {
      "targets": {
        "$HWID": {
          "to": {
            "target": "mypkg_0.0.1",
            "checksum": { "method": "sha256", "hash": "$HASH" },
            "targetLength": $LEN
          }
        }
      }
    }
    EOF

To assign the same update to several devices at once, `POST` to
`http://director.ota.ce/api/v1/updates/devices` with a `devices` array
next to `targets`.

The assignment is now queued for the device:

    curl -s -H "$NS" "http://director.ota.ce/api/v1/assignments/$DEVICE"

## Install the update

Run aktualizr again from the device directory:

    aktualizr --run-mode=once --config=config.toml

aktualizr finds the target in director's `targets.json`, verifies it
against the image repository metadata, downloads it through the
gateway and installs it. With `[pacman] type = "none"` (as in the
generated `config.toml`) nothing is installed on the host system.

## Check the result

    curl -s -H "$NS" "http://director.ota.ce/api/v1/updates/devices/$DEVICE"
    curl -s -H "$NS" "http://director.ota.ce/api/v1/assignments/$DEVICE"   # empty once installed

## Legacy API

director still supports the multi-target-update flow used by previous
versions: `POST /api/v1/multi_target_updates` with the same `targets`
body, followed by `POST /api/v1/assignments` with
`{"correlationId": "urn:here-ota:mtu:<id>", "devices": [...], "mtuId": "<id>"}`.
Campaigns and device groups are not available, as campaigner was
removed in v3.
