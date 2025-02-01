package io.hiis.service.application.api.utils

import sttp.tapir.ztapir.{ RichZEndpoint, ZServerEndpoint }
import sttp.tapir.{ endpoint, htmlBodyUtf8 }
import zio.ZIO

object DocsEndpoint {
  def get(path: String, file: String): ZServerEndpoint[Any, Any] = endpoint.get
    .in(path)
    .out(htmlBodyUtf8)
    .description("API endpoint to build serve rapidoc api documentation")
    .zServerLogic(_ =>
      ZIO.succeed(
        html.docs(file).body
      )
    )
}
