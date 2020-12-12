# OTA (mono)-Lith

Services marked with X are already included:

- [X] tuf-keyserver
- [X] tuf-reposerver
- [X] director
- [ ] device-registry
- [ ] user-profile
- [ ] campaigner
- [ ] api gateway
- [ ] treehub
- [ ] web app
- [ ] device gateway
- [ ] auth+
- [ ] crypt service

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
- [ ] helm chart for ota-lith

## Configuration

Configuration is done through `application.conf` when possible, rather than using enviroment variables. This is meant to simplify the deployment scripts and just pass `configFile=file.conf` argument to the container, or when needed, using system properties (`-Dkey=value`). `file.conf` can just be a file defined in a `YAML` config for kubernetes, for example. The idea is to be able to configure all included services using a single `application.conf` file.

## Deployment

The scala apps run in a single container, but you'll need kafka and mariadb.

Write a valid `application.conf` and run `docker run advancedtelematic/ota-lith:20201212T113900 -DconfigFile=/path/to/application.conf`

You'll need to mount `application.conf` somewhere the app can access it from inside the container.
