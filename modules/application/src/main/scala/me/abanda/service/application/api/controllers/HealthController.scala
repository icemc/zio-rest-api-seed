package me.abanda.service.application.api.controllers

import io.circe.Json
import me.abanda.service.application.api.utils.tapir.TapirT.ServerEndpointT
import me.abanda.service.application.api.utils.Controller
import me.abanda.service.application.utils.Logging
import zio.ZIO

/** Created by Abanda Ludovic on 19/01/2023 */

object HealthController extends Controller with Logging {
  val healthEndpoint: ServerEndpointT[Any, Any] = SimpleEndpoint().get
    .in("health")
    .out(stringJsonBody)
    .name("health")
    .summary("health")
    .description("Get the health status of the application")
    .serverLogicT { _ =>
      for {
        res <- ZIO.succeed(Json.obj("message" -> Json.fromString("OK")).spaces2)
      } yield res
    }
    .excludeFromDocs

  override def tag: String                                = "Health"
  override def endpoints: List[ServerEndpointT[Any, Any]] = List(healthEndpoint)
}
