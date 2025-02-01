package io.hiis.service.application.services

import zio.ZLayer
import zio.metrics.connectors.prometheus.PrometheusPublisher

trait MetricsService {
  def underlying: PrometheusPublisher
}

final case class MetricsServiceImpl(underlying: PrometheusPublisher) extends MetricsService

object MetricsService {
  val live = ZLayer.fromFunction(MetricsServiceImpl.apply _)
}
