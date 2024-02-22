import sbt._

/** Created by Abanda Ludovic on 19/09/2022 */
object Dependencies {

  private object Versions {
    val zio                     = "2.0.15"
    val zioKafka                = "2.4.2"
    val zioConfig               = "3.0.7"
    val pureConfig              = "0.17.1"
    val zioLogging              = "2.1.8"
    val zioLog4j                = "2.1.8"
    val zioSlick                = "0.5.0"
    val circe                   = "0.14.3"
    val playJson                = "2.9.3"
    val log4j                   = "2.19.0"
    val disruptor               = "3.4.4"
    val kafka                   = "3.3.1"
    val zioInteropCats          = "23.0.0.0"
    val zioResilience           = "0.9.0"
    val ZioHttp                 = "2.0.0-RC9"
    val zioJson                 = "0.3.0-RC10"
    val ZioPrelude              = "1.0.0-RC15"
    val redis                   = "3.42"
    val embeddedRedis           = "0.4.0"
    val reactivemongo           = "1.1.0-RC6"
    val embeddedMongodb         = "3.5.1"
    val postgresSql             = "42.5.1"
    val flyway                  = "9.12.0"
    val slickPostgres           = "0.21.1"
    val slick                   = "3.4.1"
    val scalaTime               = "2.32.0"
    val sttpAuth                = "0.15.2"
    val pbkdf2                  = "0.7.0"
    val jwtCirce                = "9.1.2"
    val organizeImportsVersion  = "0.6.0"
    val tapir                   = "1.9.9"
    val refined                 = "0.10.1"
    val betterMonadicForVersion = "0.3.1"
    val semanticDBVersion       = "4.5.13"
    val kindProjectorVersion    = "0.13.2"
    val mongo4Cats              = "0.6.5"
    val zioSchemaProtobuf       = "0.4.9"
    val zioRedis                = "0.2.0"
    val logback                 = "1.4.5"
    val AkkaVersion             = "2.7.0"
    val AkkaHttpVersion         = "10.4.0"
    val zioSttpClient           = "3.8.15"
    val nimbusJose              = "9.31"
    val logbackEncoderV         = "7.3"
    val zioMetrics              = "2.2.0"
    val lokiAppender            = "1.4.1"
    val zioCache                = "0.2.3"
    val classGraph              = "4.8.157"
    val listing                 = "0.0.1-RC1"
    val jsonSchemaCirce         = "0.7.4"
    val scalatest               = "3.2.17"
    val hiisGalleryLibrary      = "0.0.2-RC1"

  }

  object Libraries {

    import Versions._

    val zio = Seq(
      "dev.zio"   %% "zio"          % Versions.zio,
      "dev.zio"   %% "zio-streams"  % Versions.zio,
      "dev.zio"   %% "zio-macros"   % Versions.zio,
      "dev.zio"   %% "zio-prelude"  % ZioPrelude,
      "nl.vroste" %% "rezilience"   % zioResilience,
      "dev.zio"   %% "zio-cache"    % zioCache,
      "dev.zio"   %% "zio-test"     % Versions.zio % Test,
      "dev.zio"   %% "zio-test-sbt" % Versions.zio % Test
    )

    val http = Seq(
      "io.d11" %% "zhttp" % ZioHttp
    )

    val tests = Seq(
      "dev.zio"       %% "zio-test"          % Versions.zio % Test,
      "dev.zio"       %% "zio-test-sbt"      % Versions.zio % Test,
      "dev.zio"       %% "zio-test-magnolia" % Versions.zio % Test,
      "org.scalatest" %% "scalatest"         % scalatest    % Test
    )

    val integrationTest = Seq(
      "dev.zio" %% "zio-test"          % Versions.zio % "it,test",
      "dev.zio" %% "zio-test-sbt"      % Versions.zio % "it,test",
      "dev.zio" %% "zio-test-magnolia" % Versions.zio % "it,test"
    )

    val tapir = Seq(
      "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server"   % Versions.tapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-stub-server"  % Versions.tapir,
      "com.softwaremill.sttp.client3" %% "zio"                     % zioSttpClient,
      "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-bundle" % Versions.tapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-apispec-docs"      % Versions.tapir
    )

    val metrics = Seq(
      "dev.zio" %% "zio-metrics-connectors"            % Versions.zioMetrics, // core library
      "dev.zio" %% "zio-metrics-connectors-prometheus" % Versions.zioMetrics  // Prometheus client
    )

    val zioConfig = Seq(
      "dev.zio" %% "zio-config"          % Versions.zioConfig,
      "dev.zio" %% "zio-config-typesafe" % Versions.zioConfig,
      "dev.zio" %% "zio-config-magnolia" % Versions.zioConfig
    )

    val logging = Seq(
      "dev.zio"             %% "zio-logging"              % zioLogging,
      "dev.zio"             %% "zio-logging-slf4j"        % zioLog4j,
      "ch.qos.logback"       % "logback-classic"          % logback,
      "net.logstash.logback" % "logstash-logback-encoder" % logbackEncoderV,
      "com.github.loki4j"    % "loki-logback-appender"    % lokiAppender
    )

    val json = Seq(
      "io.circe"                      %% "circe-core"       % circe,
      "io.circe"                      %% "circe-generic"    % circe,
      "io.circe"                      %% "circe-parser"     % circe,
      "io.github.kirill5k"            %% "mongo4cats-circe" % mongo4Cats,
      "com.softwaremill.sttp.apispec" %% "jsonschema-circe" % jsonSchemaCirce,
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe" % Versions.tapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-json-play"  % Versions.tapir
    )

    val mongodb = Seq(
      "io.github.kirill5k" %% "mongo4cats-zio"          % mongo4Cats,
      "io.github.kirill5k" %% "mongo4cats-circe"        % mongo4Cats,
      "io.github.kirill5k" %% "mongo4cats-zio-embedded" % mongo4Cats % Test
    )

    val redis = Seq(
      "dev.zio" %% "zio-redis"           % zioRedis,
      "dev.zio" %% "zio-schema-protobuf" % zioSchemaProtobuf,
      "dev.zio" %% "zio-redis-embedded"  % zioRedis % Test
    )

    val auth = Seq(
      "com.github.jwt-scala" %% "jwt-circe"       % jwtCirce,
      "com.ocadotechnology"  %% "sttp-oauth2"     % sttpAuth,
      "io.github.nremond"    %% "pbkdf2-scala"    % pbkdf2,
      "com.nimbusds"          % "nimbus-jose-jwt" % nimbusJose
    )

    val apache = Seq(
      "commons-codec" % "commons-codec" % "1.15"
    )

    val compilerPlugins = Seq(
      compilerPlugin(
        "com.olegpy" %% "better-monadic-for" % betterMonadicForVersion
      ),
      compilerPlugin(
        "org.typelevel" %% "kind-projector" % kindProjectorVersion cross CrossVersion.full
      )
    )

    val cats = Seq("dev.zio" %% "zio-interop-cats" % zioInteropCats)

    // Scalafix rules
    val organizeImports = "com.github.liancheng" %% "organize-imports" % organizeImportsVersion
  }
}
