# director

This is part of the ATS open-source over-the-air (OTA) server software
available on [Github](https://github.com/advancedtelematic),
implementing the [Uptane](https://uptane.github.io/) framework.

This repository contains the ATS director service, the implementation
of one of the key features of Uptane which orchestrates the
installation of ECU-specific images on vehicles. It uses online keys
to sign metadata concerning which updates to install on which ECUs.

## Running tests

You'll need a mariadb instance running with the users configured in
`application.conf`. If you want it quick you can use
`deploy/ci_setup.sh`. This will create a new docker container running
a database with the proper permissions.

To run tests simply run `sbt test`.

## Teamcity jobs

In the `deploy` directory there are some scripts you can use to setup
the jobs in Teamcity.

## License

This code is licensed under the [Mozilla Public License 2.0](LICENSE), a copy of which can be found in this repository. All code is copyright [ATS Advanced Telematic Systems GmbH](https://www.advancedtelematic.com), 2016-2018.
