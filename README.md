# OTA-Community-Edition

- The OTA Community Edition is an open-source server software using uptane to deliver over-the-air (OTA) updates to compatible clients.

- Uptane is an open and secure software update framework design which protects software delivered over-the-air to automobile electronic control units (ECUs).

- OTA-CE comprises of a number of services which together make up the OTA system.
  `reposerver, keyserver, director, deviceregistry, campaigner and treehub`

- The source code for the servers is available on [Advanced Telematic's Github](https://github.com/advancedtelematic) and is licensed under the MPL2.0

- Docker container images of the latest build are available on Docker Hub : [Advanced Telematic](https://hub.docker.com/u/advancedtelematic) & [ota-lith](https://hub.docker.com/r/uptane/ota-lith)

## Prerequisite
### Linux/Debian setup with the following installed:
```
sudo apt install asn1c build-essential cmake curl libarchive-dev libboost-dev libboost-filesystem-dev libboost-log-dev libboost-program-options-dev libcurl4-openssl-dev libpthread-stubs0-dev libsodium-dev libsqlite3-dev pkg-config libssl-dev python3 uuid-runtime
```
- [docker](https://docs.docker.com/engine/install/) (with [docker compose](https://docs.docker.com/compose/#compose-v2-and-the-new-docker-compose-command) version 2.6.x & above)

- [aktualizr](https://github.com/advancedtelematic/aktualizr)


## Install OTA-CE
#### All the following commands are run from the `ota-community-edition` directory

### 1. Add the following host names to `/etc/hosts` :
> paste the following on the last line of `/etc/hosts`
```
0.0.0.0         reposerver.ota.ce
0.0.0.0         keyserver.ota.ce
0.0.0.0         director.ota.ce
0.0.0.0         treehub.ota.ce
0.0.0.0         deviceregistry.ota.ce
0.0.0.0         campaigner.ota.ce
0.0.0.0         app.ota.ce
0.0.0.0         ota.ce
```
### 2. Run the script `gen-server-certs.sh` :
> Once you clone the repository and are inside it, generate the required certificates.
> This is required to provision the device using aktualizr.
```bash
#make sure the script is executable
chmod +x scripts/gen-server-certs.sh

./scripts/gen-server-certs.sh
```


### 3. Pull latest ota-lith image from docker
> Read more about [ota-lith](https://github.com/simao/ota-lith)
```bash
export img=uptane/ota-lith:$(git rev-parse master)
docker pull $img
docker tag $img uptane/ota-lith:latest
```
###  4. Run docker compose
> Run in daemon mode
```bash
docker compose -f ota-ce.yaml up -d

# stop using followinng
docker compose -f ota-ce.yaml down
```
> Run without daemon mode 
```bash
docker compose -f ota-ce.yaml up 

#stop by hitting ctrl+c or by running the following command in another terminal 
docker compose -f ota-ce.yaml down
```

###  5. You can now create a virtual device

> This will create new directories `certs` and `ota-ce-gen` the first time you run it and a device in `ota-ce-gen/devices/:uuid` where `uuid` is the id of the new device. 
```bash
chmod +x scripts/gen-device.sh

./scripts/gen-device.sh
```    


### 6. Provision that device with aktualizr

> Before you try to update your virtual device, make sure it connects with aktualizr
```bash
cd ota-ce-gen/devices/:uuid

aktualizr --run-mode=once --config=config.toml
```    

### 7. Create credentials
> Credentials are needed to send updates to device. This will create a `credentials.zip` file in the `ota-ce-gen` directory
```bash
chmod +x scripts/get-credentials.sh

./scripts/get-credentials.sh
```    

## Deploy updates

You can use [ota-cli](https://github.com/simao/ota-cli/) to deploy updates. After provisioning devices (see above).

### 1. Install ota-cli
```bash
git clone https://github.com/simao/ota-cli.git

cd ota-cli

#run the following and follow the onscreen instructions
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

#Build the tool
make ota
```

### 2. Initialize ota-cli
```bash
ota init --credentials /path/to/ota-ce-gen/credentials.zip --campaigner http://campaigner.ota.ce --director http://director.ota.ce --registry http://deviceregistry.ota.ce
```

### 3. Add package to update 
> Name a file with content in it that you would like to update to device as `update.bin` . It is not necessary to call it that, you can name it anything but for the sake of this tutorial name it `update.bin`. 
```bash
# The output of this command is important to create the target file in the next step
ota package add -n mypkg -v 0.0.1 --path /path/to/update.bin --binary --hardware ota-ce-device

# You can regenerate the same output with this step
ota package list
```
 example output:
```json
{
  "signatures": [
    {
      "keyid": "f62cfe66e8e986f4aac9161dcb231a9ccbff6db7b7907a4cd797f4db975d727c",
      "method": "ed25519",
      "sig": "dD2y2kX08uPsLLC9Uab8b7g0xdUqd3W/GUWc8dri6CE2OaA7rBKFO+M/GgZSIKXq8xzsO1tWP4POcei60NymAA=="
    }
  ],
  "signed": {
    "_type": "Targets",
    "expires": "2022-10-12T14:06:56Z",
    "targets": {
      "mypkg-0.0.1": {
    "custom": {
      "createdAt": "2022-09-11T14:06:55Z",
      "hardwareIds": [
        "ota-ce-device"
      ],
      "name": "mypkg",
      "targetFormat": "BINARY",
      "updatedAt": "2022-09-11T14:06:55Z",
      "uri": null,
      "version": "0.0.1"
    },
    "hashes": {
      "sha256": "70b22c2b8c857d1416a16e04cbfdf2b4d00d8b84000cc809af2061301ccfecb1"
    },
    "length": 830
      }
    },
    "version": 1
  }
}
```

### 4. Create target.toml file
> You have to use the name, version, length and hash using the json output recieved above and write a `target.toml` file. 

For example the content of `target.toml` file wrt the above example output will be:
```
[ota-ce-device]
target_format = "binary"
generate_diff = true
 
# required metadata specifying update target
[ota-ce-device.to]
name = "mypkg"
version = "0.0.1"
length = 830
hash = "70b22c2b8c857d1416a16e04cbfdf2b4d00d8b84000cc809af2061301ccfecb1"
method = "sha256"
```
### 5. Create update
> The output of this command will be an `uuid` which you will use next to --update parameter in next step
```bash
ota update create -t /path/to/target.toml
```
### 6. Serve the Update
> Using the uuid you get in the above command and the uuid of the device you want to update.
```bash
ota update launch --update <uuid> --device <uuid>
```
> If it works , the o/p of the command will be `OK` or else it will give an error

### 7. Update the device
```bash
cd ota-ce-gen/devices/:uuid

aktualizr --run-mode=once --config=config.toml

# you should find the update in the following path

cd ota-ce-gen/devices/[uuid]/storage/images/

```



To deploy an update using the API and a custom campaign, see [api-updates.md](docs/api-updates.md).

To deploy an update using [ota-cli](https://github.com/simao/ota-cli/) with a custom campaign see [updates-ota-cli.md](docs/updates-ota-cli.md).

## Dependency management
  
This project follows the upstream [UPTANE OTA](https://uptane.github.io/) (see [sources](https://github.com/uptane/)) projects.

The following forks/branches are included:

- tuf https://github.com/uptane/ota-tuf
- director https://github.com/uptane/director
- device-registry https://github.com/uptane/ota-device-registry
- campaigner https://github.com/simao/campaigner
- treehub https://github.com/uptane/treehub
- libats https://github.com/uptane/libats

The dependencies are managed using
[git-subtree](https://man.archlinux.org/man/git-subtree.1) under the
`repos` directory on this repository.

To update to the latest changes from upstream ota-tuf, you could use for example:

    git subtree pull --prefix repos/ota-tuf git@github.com:uptane/ota-tuf.git master --squash

If you wish to make changes to ota-tuf, you could edit directly
`repos/ota-tuf` and then commit the changes, then use `git-subtree` to
split the changes and open a pull request upstream. However, a simpler
way would be to just use `git diff` to generate a patch and apply that
patch to the repository separately:

    cd repos/ota-tuf
    git diff . > tuf.patch
    cd /home/user/my-ota-tuf
    patch -p3 < tuf.patch
    
    # normal git flow to create a PR for ota-tuf
    
And then once those changes are merged upstream you could use `git
subtree pull` to incorporate those changes.
    

## FAQ

### How does it keep up with changes on the Uptane Standard?

All the [UPTANE repositories](https://github.com/uptane) are updated
frequently and docker images are built and pushed to
`hub.docker.io`. Periodically, these images are incorporated into
ota-lith. This is usually just a case of updating the docker tags used
and creating the containers again.

However, `webapp` and `campaigner` projects are not part of the UPTANE
repositories and they are independently maintained by HERE Technologies
GmbH. 

### What is the relationship with Advanced Telematic OTA Community Edition?

The OTA Community Edition (OTA CE) was initially created and developed
by Advanced Telematic Systems GmbH. Advanced Telematic Systems GmbH (ATS)
was acquired by HERE Technologies GmbH and developed OTA CE
further. All changes to the already open source components continued
to be published in the [ats github
repository](https://github.com/advancedtelematic/).

Recently, the [uptane github organization](https://github.com/uptane/)
was created and all projects were forked into that organization. HERE
Technologies continues to develop their OTA solution, publishing
their open source changes to the ATS github repository.

Changes made to the ATS github repository are merged back to the
Uptane Repositories by the uptane contributors if they are considered
important, and they are then used by ota-lith once the docker images
are updated.

The HERE OTA CE solution appears to be on a bug fix only mode, while
the UPTANE OTA CE is actively developing new features. 

## Related

- https://github.com/simao/ota-cli
- https://github.com/advancedtelematic/ota-community-edition
- https://docs.ota.here.com/getstarted/dev/index.html
- https://docs.ota.here.com/ota-client/latest/aktualizr-config-options.html
