package io.hiis.service.application.api.controllers

import io.hiis.service.application.api.utils.ControllerSpec
import io.hiis.service.application.services.MetricsService
import sttp.client3.{ basicRequest, UriContext }
import sttp.model.{ Header, StatusCode }
import zio.metrics.connectors.{ prometheus, MetricsConfig }
import zio.test.assertTrue
import zio.{ durationInt, ZLayer }

import java.util.UUID

object MetricsControllerSpec extends ControllerSpec {
  override def spec = suite("Metrics Controller Spec")(
    test("metrics endpoint should respond with OK") {
      for {
        backend <- backendStub
        response <- basicRequest
          .header(Header("x-request-id", UUID.randomUUID().toString))
          .get(uri"http://test.com/metrics")
          .send(backend)
      } yield assertTrue(response.code == StatusCode.Ok)
    }
  ).provide(
    MetricsController.live,
    MetricsService.live,
    ZLayer.succeed(MetricsConfig(15.seconds)),
    prometheus.publisherLayer,
    prometheus.prometheusLayer
  )
}
