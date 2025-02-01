package io.hiis.service.application.api

import io.hiis.service.application.api
import io.hiis.service.application.services.MetricsService
import api.controllers.{
  HealthController,
  HomeController,
  MetricsController,
  OptionsController,
  VersionController
}
import api.utils.{ ApiGatewayT, Controller }
import io.hiis.service.core.models.Config.AppServerConfig
import io.hiis.service.core.utils.Logging
import zio._

/** Created by Ludovic Temgoua Abanda (icemc) on 19/01/2023 */

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
        MetricsController(metricsService),
        HomeController,
        OptionsController
      )(config)
    )

  // Helper ApiGateway ZLayer with default Server config
  val live: ZLayer[MetricsService, Nothing, ApiGateway] = live(
    AppServerConfig("http://localhost", 9090, None)
  )
}
