package me.abanda.service.application.api.controllers

import me.abanda.service.application.api.utils.tapir.TapirT.ServerEndpointT
import me.abanda.service.application.api.utils.Controller
import me.abanda.service.application.services.MetricsService
import me.abanda.service.application.utils.Logging
import zio.ZLayer

case class MetricsController(metricsService: MetricsService) extends Controller with Logging {
  val metricsEndpoint: ServerEndpointT[Any, Any] = SimpleEndpoint().get
    .in("metrics")
    .out(stringBody)
    .name("metrics")
    .summary("metrics")
    .description("Get the prometheus metrics for the application")
    .serverLogicT(_ => metricsService.underlying.get)
    .excludeFromDocs

  override def tag: String = "Metrics"

  override def endpoints: List[ServerEndpointT[Any, Any]] = List(metricsEndpoint)
}

object MetricsController {
  val live: ZLayer[MetricsService, Nothing, MetricsController] =
    ZLayer.fromFunction(MetricsController.apply _)
}
