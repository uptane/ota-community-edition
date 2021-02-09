# OTA Campaigner

A service to create and schedule campaigns for a given update, targeting all devices in a given device group.

- The Campaigner processes a campaign in configurable intervals, fetching batches of devices (by device group) from the Device-Registry service.
- For each device, it will initiate the given update in the Director service.
- The Director indicates whether the targeted device is affected by the update in the API response.
- For all affected devices, Campaigner tracks the status of the update when it receives the `DeviceUpdateReport` message event.
- For all campaigns, it tracks the count of processed and affected devices, and completed and failed updates.

The API documentation is here:

[Campaigner API](http://advancedtelematic.github.io/rvi_sota_server/swagger/sota-core.html?url=https://s3.eu-central-1.amazonaws.com/ats-end-to-end-tests/swagger-docs/latest/Campaigner.json)

See [arch.mmd](docs/arch.mmd) for a diagram of the dependencies.

## Build

To build a docker image and push it to dockerhub:

```
  sbt release
```

To build an image locally without pushing it to dockerhub:

```
  sbt docker:publishLocal
```

## Run

To run the service as a docker container:

```
  docker run -d -p 8084:8084 --env ENV_NAME=env-value --name campaigner advancedtelematic/campaigner:latest
```

To run the service from `sbt` without docker:

```
  sbt run
```

The service is now accessible at `localhost:8084`.

## Deployment

The service is configured by setting environment variables:

Variable                    | Description
-------------------:        | :------------------
`DB_MIGRATE`                | Whether to auto update the database schema
`DB_URL`                    | Database URL
`DB_USER`                   | Database username
`DB_PASSWORD`               | Database password
`DEVICE_REGISTRY_HOST`      | Host of Device Registry service
`DEVICE_REGISTRY_PORT`      | Port of Device Registry service
`DIRECTOR_HOST`             | Host of Director service
`DIRECTOR_PORT`             | Port of Director service
`KAFKA_HOST`                | Kafka bootstrap servers
`SCHEDULER_BATCH_SIZE`      | Number of devices to process at a time
`SCHEDULER_DELAY`           | Delay between processing batches
`SCHEDULER_POLLING_TIMEOUT` | Interval between processing unfinished campaigns

For a description of the Device Registry and Director services, refer to their respective repositories:

Service                     | Repository
-------------------:        | :------------------
Device Registry             | https://github.com/advancedtelematic/ota-device-registry
Director                    | https://github.com/advancedtelematic/director

## Test

To setup a database instance (inside a docker container) for testing:

```
  ./deploy/ci_setup.sh
```

To run the tests:

```
sbt test
```

## License

This code is licensed under the [Mozilla Public License 2.0](LICENSE), a copy of which can be found in this repository. All code is copyright [ATS Advanced Telematic Systems GmbH](https://www.advancedtelematic.com), 2016-2018.
