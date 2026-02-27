# OTA Community Edition (mono)-lith

Easy to run, secure, open source [tuf](https://theupdateframework.io/)/[uptane](https://uptane.github.io/) over the air (OTA) updates.

You may not want or need to run [ota-community-edition](https://github.com/advancedtelematic/ota-community-edition) using a microservice architecure. A monolith might fit your use case better if you just want to try `ota-community-edition` or if your organization doesn't need to serve millions of devices. A monolith architecture is easier to deploy and manage, and uses less resources.

This project bundles all the scala apps included in [ota-community-edition](https://github.com/advancedtelematic/ota-community-edition) into a application that can be executed in a single container. The app can be easily configured using a single configuration file and avoids the usage of environment variables to simplifly configuration. Additionally, a `docker-compose` file is provided to run the application. This means you no longer need a kubernetes cluster if you just want to try or test `ota-community-edition`.

For small deployments, you don't need kubernetes. This solution can fit your organization better. With this app you could run ota in a single machine/vm + mariadb and kafka.

## Active branches

Currently there are three active branches in this repository:

- `master`. `ota-community-edition` running without webapp and with kafka, using a single scala app.

- `webapp`. The webapp is broken in `ota-community-edition` and therefore is not included in `ota-lith/master`. The `webapp` branch includes patched version of `webapp` that does not rely on user profile and is therefore working with both `ota-community-edition` and `ota-lith`.

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
    
## Building

To build a container running the services, run `sbt docker:publishLocal`

## Configuration

Configuration is done through a single config file, rather than using enviroment variables. This is meant to simplify the deployment scripts and just pass a `configFile=file.conf` argument to the container, or when needed, using system properties (`-Dkey=value`). An example config file is provied in `ota-lith-ce.conf`.

## Running

If you already have kafka and mariadb instances you can just run the ota-lith binary using sbt or docker.

### Using sbt

You'll need a valid ota-lith.conf, then run:

    sbt -Dconfig.file=$(pwd)/ota-lith.conf run

### Using docker

The scala apps run in a single container, but you'll need kafka and mariadb. Write a valid ota-lith.conf.

    sbt docker:publishLocal
    docker run --name=ota-lith -v $(pwd)/ota-lith.conf:/tmp/ota-lith.conf uptane/ota-lith:latest -Dconfig.file=/tmp/ota-lith.conf
    
If you don't have `sbt` or prefer to use a pre built image, you can use:

    export img=uptane/ota-lith:$(git rev-parse master)
    docker run --name=ota-lith -v $(pwd)/ota-lith.conf:/tmp/ota-lith.conf $img -Dconfig.file=/tmp/ota-lith.conf

## GUI interface

A lightweight GUI for OTA CE is available in `gui/`, including service health, device/group management, quick update creation (text/file/drag-drop artifact sources), campaign monitoring, preflight checks, remote copy command generation for target devices, and an API explorer. See [GUI interface guide](docs/gui-interface.md) for usage instructions.

## GUI quickstart and feature guide

### Start the GUI

From the repository root:

```bash
# recommended: serves GUI and proxies API to http://ota.ce (avoids CORS issues)
python3 scripts/gui-dev-server.py --port 8080 --target http://ota.ce
```

Open `http://localhost:8080/gui/`.

If port `8080` is already in use:

```bash
# option 1: use another port
python3 scripts/gui-dev-server.py --port 8081 --target http://ota.ce

# option 2: identify process using 8080
ss -ltnp | rg :8080
```

Then open `http://localhost:8081/gui/` if you used port 8081.

You can still use plain static hosting (`python3 -m http.server`) but cross-origin API requests may fail with `Failed to fetch` if CORS is not enabled on the API host.

### What each panel does

1. **Connection Settings**
   - Set base URL (default `http://ota.ce`).
   - Optionally paste bearer token.
   - Use token show/hide and clear buttons.
   - Keep **Safety mode** enabled to require confirmations for sensitive actions.

2. **Service Health**
   - Click **Check all** to query health endpoints of director/treehub/deviceregistry/campaigner/reposerver/keyserver.

3. **Device & Group Management**
   - Save local custom name ↔ device UUID mappings (stored in browser `localStorage`).
   - Refresh devices from API and pick one into the forms.
   - Create/refresh groups and add/remove device membership.

4. **Quick Update**
   - Fill device UUID, group UUID, package/update/campaign fields.
   - Choose artifact source:
     - text input,
     - file picker,
     - drag & drop.
   - Run **preflight checks** before creation.
   - Click **Create update** to run: target upload → MTU → update → campaign (and optional launch).

5. **Remote Copy Helper**
   - Generates `scp` or `rsync` commands to copy `ota-ce-gen/devices/:uuid` to a remote target.
   - Generates SSH command to run `aktualizr` on the remote target.
   - Use **Copy commands** and run them in your terminal.

6. **Campaign Monitor**
   - Set campaign UUID.
   - Refresh manually or enable polling interval.
   - View campaign summary and deliveries.
   - Cancel campaign if needed.

7. **Python E2E Flow Helper**
   - Generates a ready-to-run command for `scripts/ota_e2e_flow.py` using values from Quick Update.
   - Use this when you prefer a terminal automation flow with detailed logs.

8. **API Explorer**
   - Send custom API requests with method/path/json body.
   - Use presets for common OTA endpoints.

### Important operational notes

- The GUI is browser-based and cannot execute host commands directly (for example, `scp`, `rsync`, `ssh`, or running `aktualizr`); it can only generate commands for you. For API calls, prefer `scripts/gui-dev-server.py` to avoid browser CORS issues.
- For external target devices, copy the generated `ota-ce-gen/devices/:uuid` folder to the target and run `aktualizr` there manually.
- You can also run `python3 scripts/ota_e2e_flow.py --help` for a CLI-driven full workflow.
- If APIs are protected, use a valid token and confirm hostnames in `/etc/hosts`.

## Running With Docker Compose

If you don't have kafka or mariadb running and just want to try ota-ce, run using docker-compose:

1. Generate the required certificates using `scripts/gen-server-certs.sh` 

2. Update /etc/hosts with the following host names:

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

3. build docker image or pull from docker

`sbt docker:publishLocal`

Or:

    export img=uptane/ota-lith:$(git rev-parse master)
    docker pull $img
    docker tag $img uptane/ota-lith:latest

4. Run docker-compose
 
`docker-compose -f ota-ce.yaml up`

5. Test

For example `curl director.ota.ce/health/version`

6. You can now create device credentials and provision devices

Run `scripts/gen-device.sh`. This will create a new dir in `ota-ce-gen/devices/:uuid` where `uuid` is the id of the new device. You can run `aktualizr` in that directory using:

    aktualizr --run-mode=once --config=config.toml
    
7. You can now deploy updates to the devices

## Deploy updates

You can either use the API directly or use [ota-cli](https://github.com/simao/ota-cli/) to deploy updates. After provisioning devices (see above).

Before using the api or `ota-cli` you will need to generate a valid `credentials.zip`. Run `scripts/get-credentials.zip`.

To deploy an update using the API and a custom campaign, see [api-updates.md](docs/api-updates.md).

To deploy an update using [ota-cli](https://github.com/simao/ota-cli/) with or without a custom campaign see [updates-ota-cli.md](docs/updates-ota-cli.md).

## FAQ

### Who maintains ota-lith

ota-lith is a collection of scripts and configurations that aggregates
upstream projects and makes it easier to run a complete OTA
solution. These scripts and configurations are maintained by
[simao](https://github.com/simao).

The actual OTA implementation is implemented and maintained by
multiple uptane contributors under the [uptane
organization](https://github.com/uptane/).

There are currently multiple contributors to the upstream UPTANE
repositories, most notably [toradex](https://toradex.com) which runs
the same uptane implementation as part of the [torizon
platform](https://app.torizon.io)

### How does it keep up with changes on the Uptane Standard?

All the [UPTANE repositories](https://github.com/uptane) are updated
frequently and docker images are built and pushed to
`hub.docker.io`. Periodically, these images are incorporated into
ota-lith. This is usually just a case of updating the docker tags used
and creating the containers again.

However, `webapp` and `campaigner` projects are not part of the UPTANE
repositories and they are independently maintained by HERE Technologies
GmbH. These changes might or might not be merged back into `ota-lith`,
depending on the complexity of merging the changes.

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
