# OTA (mono)-Lith

Services marked with X are already included:

- [X] tuf-keyserver
- [X] tuf-reposerver
- [X] director
- [X] treehub
- [X] device-registry
- [X] campaigner
- [ ] web events (?)
- [ ] auditor ?

Non open source:

- [X] user-profile
- [X] api provider

Not libats/jvm based:

- [ ] webapp
- [ ] crypt service
- [ ] api-gateway
- [ ] device gateway
- [ ] ~~auth+~~

## Missing infrastructure

This container will need to be deployed somewhere with a kafka instance and a running mariadb database.

## Building

To build a container running the services, run `sbt docker:publishLocal`

## Parts missing

Besides the services mentioned above, we are missing:

- [ ] A reverse proxy (nginx?) to proxy to each port depending on hostname
- [ ] kafka instances
- [ ] mysql instances
- [ ] A docker compose file to setup all of the above with the `ota-lith` container

## Configuration

Configuration is done through `application.conf` when possible, rather than using enviroment variables. This is meant to simplify the deployment scripts and just pass `configFile=file.conf` argument to the container, or when needed, using system properties (`-Dkey=value`). `file.conf` can just be a file defined in a `YAML` config for kubernetes, for example. The idea is to be able to configure all included services using a single `application.conf` file.

## Deployment

The scala apps run in a single container, but you'll need kafka and mariadb. Write a valid ota-lith.conf.

```
sbt docker:publishLocal
docker run --name=ota-lith -v $(pwd)/ota-lith.conf:/tmp/ota-lith.conf advancedtelematic/ota-lith:latest -Dconfig.file=/tmp/ota-lith.conf
```

You'll need to mount `application.conf` somewhere the app can access it from inside the container.
