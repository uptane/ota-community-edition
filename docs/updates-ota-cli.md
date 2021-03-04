# Setup device and credentials

1. Run `scripts/gen-device.sh`.

2. Run `scripts/get-credentials.zip`

Use ota-cli. In the commands bellow use `cargo run --` instead of `ota` if you are running `ota-cli` from source.

In the following instructions, the uuids used are outputs from previous commands.

# Setup ota-cli

    ota init --campaigner http://campaigner.ota.ce --director http://director.ota.ce --registry http://deviceregistry.ota.ce --credentials path/to/credentials.zip -t http://reposerver.ota.ce

# Add the package

    ota package add -n mypkg -v 0.0.2 --path path/to/binary.bin --binary --hardware ota-ce-device

# Get info about package

    ota package list

Create a file describing the update

``` examples/targets-ota-ce.toml
# specify each hardware identifier in square brackets
[ota-ce-device]
target_format = "binary"
generate_diff = true

# required metadata specifying update target
[ota-ce-device.to]
name = "mypkg"
version = "0.0.1"
length = 1110186
hash = "e44664c98575081dc778de960cb69627505fb4ff033ec3d8827dd9eff80ffce3"
method = "sha256"

```

# Serving the update

Choose one of the following:


## Without a campaign

    ota update create -t examples/targets-ota-ce.toml

    ota update launch --update f3b7950c-97d0-44ef-8ee9-93158b8f0f0f --device 5a04529c-3f30-418b-b8e8-92bfcc36f54b

## With a campaign

    ota group create -n esc-dev-group

    ota group add --group 1c15b3f3-2fae-4f66-ae22-1de4603d8eb9 --device 5a04529c-3f30-418b-b8e8-92bfcc36f54b

    ota campaign createupdate --description to-mypkg --name to-mypkg --update f3b7950c-97d0-44ef-8ee9-93158b8f0f0f

    ota campaign create --update 395b9142-6468-4a36-b519-ec5a304dbfd4 --name esc-campaign-01 -g 1c15b3f3-2fae-4f66-ae22-1de4603d8eb9

    ota campaign launch --campaign cc1eb168-3906-4d75-99c5-ee25c09657f5
