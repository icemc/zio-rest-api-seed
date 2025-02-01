package io.hiis.service.application.api.controllers

import io.hiis.service.application.api.utils.Controller
import io.hiis.service.application.api.utils.tapir.TapirT.ServerEndpointT
import io.hiis.service.core.utils.Logging
import sttp.model.{ HeaderNames, StatusCode }
import zio.ZIO

object HomeController extends Controller with Logging {
  val homeEndpoint: ServerEndpointT[Any, Any] = SimpleEndpoint().get
    .in("")
    .out(statusCode(StatusCode.TemporaryRedirect))
    .out(header[String](HeaderNames.Location))
    .name("home")
    .summary("home")
    .description("Home page")
    .serverLogicT { _ =>
      ZIO.succeed("/docs")
    }
    .excludeFromDocs

  override def tag: String                                = "Home"
  override def endpoints: List[ServerEndpointT[Any, Any]] = List(homeEndpoint)
}
