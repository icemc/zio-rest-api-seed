package io.hiis.service.application.api.controllers

import io.hiis.service.application.api.utils.Api.ApiError.InternalServerError
import io.hiis.service.application.api.utils.Controller
import io.hiis.service.application.api.utils.tapir.TapirT.ServerEndpointT
import io.hiis.service.core.utils.Logging
import zio.CanFail.canFailAmbiguous1
import zio.ZIO

case object OptionsController extends Controller with Logging {
  val optionsEndpoint: ServerEndpointT[Any, Any] = SimpleEndpoint().options
    .in(paths)
    .out(stringBody)
    .name("option")
    .summary("option")
    .description("Dummy option call endpoint for browsers preflight requests")
    .serverLogicT(_ =>
      logInfo("Options call") *> ZIO
        .succeed("OK")
        .mapError(_ => InternalServerError())
    )
    .excludeFromDocs

  override def endpoints: List[ServerEndpointT[Any, Any]] = List(optionsEndpoint)

  override def tag: String = "Preflight"
}
