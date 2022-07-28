# OTA-Community-Edition

- The OTA Community Edition is open-source server software to allow over-the-air (OTA) updates of compatible clients

- OTA-CE comprises of a number of services which together make up the OTA system.
>  `reposerver, keyserver, director, deviceregistry, campaigner and treehub`

- The source code for the servers is available on [Github](https://github.com/advancedtelematic) and is licensed under the MPL2.0

- [Advanced Telematic](https://hub.docker.com/u/advancedtelematic) & [ota-lith](https://hub.docker.com/r/uptane/ota-lith): Docker container images of the latest build are available on Docker Hub

## Prerequisite
> On a  linux/debian setup install the following:

- Curl
```
sudo apt-get update
sudo apt-get install curl
```
- [Docker](https://docs.docker.com/engine/install/) (with [docker compose](https://docs.docker.com/compose/#compose-v2-and-the-new-docker-compose-command) version 2.6.x)
- [Aktualizr](https://github.com/advancedtelematic/aktualizr)

## Installing OTA-CE
1. Add the following host names to `/etc/hosts` :
> open the file /etc/hosts using any editor of your choice with root access
```
sudo gedit /etc/hosts
```
> paste the following on the last line  , save the file and close it
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

2. Run the script `gen-server-certs.sh` :
> Generates the required certificates.
> This is required to provision the aktualizr.
```
sudo ./scripts/gen-server-certs.sh
```

3. Pull docker image (or build docker image) :

|     Options     |Pull from Docker            | Build Docker Image            |
|----------------|-------------------------------|-----------------------------|
|Number of Steps|3 steps|1 step only|
|I|`export img=uptane/ota-lith:$(git rev-parse master)`|`sbt docker:publishLocal`|
|II|`docker pull $img`|      ~     |
|III|`docker tag $img uptane/ota-lith:latest`|~|

4. Operate with docker-compose :

> Start
```
docker compose -f ota-ce.yaml up
```
> Stop
```
docker compose -f ota-ce.yaml down
```
> Remove & Clean
```
docker compose -f ota-ce.yaml rm
docker volume ls | grep ota-lith | awk '{print $2}'| xargs -n1 docker volume rm
```

5. Test if the servers are up
> Check if the services are running
```
curl director.ota.ce/health/version
curl keyserver.ota.ce/health/version
curl reposerver.ota.ce/health/version
curl treehub.ota.ce/health/version
curl deviceregistry.ota.ce/health/version
curl campaigner.ota.ce/health/version
```
> If you get an output in the following syntax, then the service is up and running
```
{
    "builtAtMillis": "1645552143860",
    "name": "<service-name>",
    "scalaVersion": "2.12.15",
    "version": "3d60bb94256e58dfe0a42a9bec90ae58dea1d1ab",
    "sbtVersion": "1.4.7",
    "builtAtString": "2022-02-22 17:49:03.860+0000"
}
```


6. Run the scripts `get-credentials.sh` and `gen-device.sh` (in that order):
> Creates credentials.zip in `ota-ce-gen/` directory
 ```
sudo ./scripts/get-credentials.sh
 ```

> It provides a new device, by creating a new directory `ota-ce-gen/devices/:uuid` where **uuid** is the id of the new device.

 ```
sudo ./scripts/gen-device.sh
 ```
7. Run **aktualizr** :
- Go in the directory `ota-ce-gen/devices/:uuid`, there we run the following command to connect aktualizr.
> The aktualizr client is intended to be installed on devices that wish to receive OTA updates from an Uptane-compatible OTA server
```
sudo aktualizr --run-mode=once --config=config.toml
```   

---
## Working with APIs

>List all devices
```
curl deviceregistry.ota.ce/api/v1/devices
```

>Detailed information about a specific device
```
curl deviceregistry.ota.ce/api/v1/devices/<device-uuid>
```
>Packages updated on a specific device
```
curl director.ota.ce/api/v1/admin/devices/<device-uuid>
```

>Package update installation history of a specific device
```
curl deviceregistry.ota.ce/api/v1/devices/<device-uuid>/installation_history
```

>Updates assigned to be installed on a specific device
```
curl director.ota.ce/api/v1/assignments/<device-uuid>
```

---
## License

This code is licensed under the [Mozilla Public License 2.0](LICENSE), a copy of which can be found in this repository. All code is copyright [ATS Advanced Telematic Systems GmbH](https://www.advancedtelematic.com), 2016-2018.
