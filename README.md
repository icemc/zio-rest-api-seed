# ZIO microservice seed project

![Scala Version](https://img.shields.io/badge/Scala-2.13.8-red)
![SBT Version](https://img.shields.io/badge/SBT-1.7.2-blueviolet)
![Scala CI](https://github.com/icemc/zio-rest-api-seed/actions/workflows/scala.yml/badge.svg)

## Description

This is a seed template for scala microservice applications. This should serve as a base template for scala microservice
applications development. It made up of the following main components

- ZIO (zio 2, zio-http, zio-config, zio-test etc)
- Tapir
- Circe for JSON
- JWT-Circe and STTP-OAuth2 for Authentication
- Logback with zio-logging
- Mongodb connector
- Redis Connector or ZIO cache for caching
- Prometheus Connector for Prometheus metrics
- SBT-Docker for Docker build
- Scala formatting (scalafix, scalafmt)

## Structure

This structure is standard across most microservice code bases at Hiis and should be maintained if possible.

- [modules/application](./modules/application) should contain the main zio application.
- [modules/it](./modules/it) should contain the integration test code for the main application.


- [grafana-dashboard.json](./grafa-dashboard.json) contains sample grafana dashboard for the application metrics.
- [docker-compose.yml](./docker-compose.yml) contain all external services required for unit and integration test.

## Running the application

- With Hot-reload: To run the application with hot-reload enabled simply run `sbt start`. This will run the application
  on the port specified in application.conf appServer.port with hot-reload enabled
- Without Hot-reload: To run the application without hot-reload enabled simply run `sbt application/run`

## Building application (SBT assembly & Docker)

- To build the fat jar using sbt assembly simply run the command `sbt build` with your desired configurations set up
  in [`application.conf`](./modules/application/src/main/resources/application.conf)
- To build the docker image simply run `sbt build-docker` with your desired configurations set up
  in [`application.conf`](./modules/application/src/main/resources/application.conf). Make sure you change the docker
  user in [`build.sbt`](./build.sbt)

## Development

- Clone the project
- Make sure you have `sbt` installed in your computer.
- Import the project in your preferred sbt supported IDE (we recommend Intellij Idea, the community version will
  suffice)
- Make sure all external dependencies are up and running (See [docker-compose.yml](./docker-compose.yml) file)
- Start coding and testing.
- Pull requests are welcomed.

## Contributing

This project is open to contributions be it through issues or pull request. Have a look at
our [`contribution guide`](./CONTRIBUTING.md) before you get started.

## License

[MIT](./LICENSE)