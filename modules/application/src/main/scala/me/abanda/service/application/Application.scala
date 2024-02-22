package me.abanda.service.application

import me.abanda.service.application.api.ApiGateway
import me.abanda.service.application.models.Config.{ AppServerConfig, AuthConfig, MongodbConfig }
import me.abanda.service.application.services.MetricsService
import me.abanda.service.application.services.security.AuthTokenService
import me.abanda.service.application.utils.Logging
import mongo4cats.zio.ZMongoClient
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._
import zio.metrics.connectors.{ prometheus, MetricsConfig }
import zio.metrics.jvm.DefaultJvmMetrics

import java.net.http.HttpClient

object Application extends ZIOAppDefault with Logging {

  val mongodbClient: ZLayer[Any, Throwable, ZMongoClient] = AppConfig.appConfig.flatMap(layer =>
    ZLayer.scoped[Any](ZMongoClient.fromConnectionString(layer.get.mongodb.uri))
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Logging.logger ++ AppConfig.appConfig

  private val backend =
    ZLayer.succeed(
      HttpClientZioBackend.usingClient(
        HttpClient.newHttpClient()
      )
    )

  val app = for {
    appConfig     <- ZIO.service[AppServerConfig]
    mongodbConfig <- ZIO.service[MongodbConfig]
    authConfig    <- ZIO.service[AuthConfig]
    _ <- (ZIO
      .service[ApiGateway]
      .flatMap(_.start) <& logInfo(
      s"Started ${me.abanda.service.application.build.BuildInfo.name} API Gateway Server"
    ))
      .provide(
        ApiGateway.live(appConfig),
        AuthTokenService.live,
        mongodbClient,
        backend,
        ZLayer.succeed(authConfig),
        ZLayer.succeed(mongodbConfig),

        // Metrics ZLayers
        ZLayer.succeed(MetricsConfig(15.seconds)),
        prometheus.publisherLayer,
        prometheus.prometheusLayer,

        // Enable the ZIO internal metrics and the default JVM metricsConfig
        Runtime.enableRuntimeMetrics,
        DefaultJvmMetrics.live.unit,
        MetricsService.live
      )
  } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    app.provide(
      AppConfig.live
    )
}
