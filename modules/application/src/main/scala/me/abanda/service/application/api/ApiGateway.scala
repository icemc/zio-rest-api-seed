package me.abanda.service.application.api

import me.abanda.service.application.api
import me.abanda.service.application.api.controllers.{
  HealthController,
  MetricsController,
  VersionController
}
import me.abanda.service.application.api.utils.{ ApiGatewayT, Controller }
import me.abanda.service.application.models.Config.AppServerConfig
import me.abanda.service.application.services.MetricsService
import me.abanda.service.application.utils.Logging
import zio._

/** Created by Abanda Ludovic on 19/01/2023 */

final case class ApiGateway(routes: Controller*)(override val config: AppServerConfig)
    extends ApiGatewayT
    with Logging

object ApiGateway {
  // Compose your ApiGateway layer here
  def live(
      config: AppServerConfig
  ): ZLayer[MetricsService, Nothing, ApiGateway] =
    ZLayer.fromZIO(
      for {
        metricsService <- ZIO.service[MetricsService]
      } yield api.ApiGateway(
        HealthController,
        VersionController,
        MetricsController(metricsService)
      )(config)
    )

  // Helper ApiGateway ZLayer with default Server config
  val live: ZLayer[MetricsService, Nothing, ApiGateway] = live(
    AppServerConfig("http://localhost", 9090, None)
  )
}
