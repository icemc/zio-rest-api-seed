package io.hiis.service.application.api.controllers

import io.circe.parser
import io.circe.syntax.EncoderOps
import io.hiis.service.application.api.utils.Controller
import io.hiis.service.core.build.BuildInfo
import io.hiis.service.application.api.utils.tapir.TapirT.ServerEndpointT
import io.hiis.service.core.utils.Logging
import zio.ZIO

object VersionController extends Controller with Logging {
  val versionEndpoint: ServerEndpointT[Any, Any] = SimpleEndpoint().get
    .in("version")
    .out(stringJsonBody)
    .name("version")
    .summary("version")
    .description("Get the build and version info of the application")
    .serverLogicT(_ =>
      ZIO.succeed(parser.parse(BuildInfo.toJson).getOrElse(BuildInfo.toJson.asJson).spaces2)
    )
    .excludeFromDocs

  override def tag: String = "Version"

  override def endpoints: List[ServerEndpointT[Any, Any]] = List(versionEndpoint)
}
