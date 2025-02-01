package io.hiis.service.application.api.utils

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.tapir.server.stub.TapirStubInterpreter
import zio.{ Task, ZIO }
import zio.test.ZIOSpecDefault

/** Created by Ludovic Temgoua Abanda (icemc) on 20/01/2023 */

trait ControllerSpec extends ZIOSpecDefault {
  final def backendStub
      : ZIO[Controller, Nothing, SttpBackend[Task, ZioStreams with capabilities.WebSockets]] = {
    for {
      controller <- ZIO.service[Controller]
      backend = TapirStubInterpreter(HttpClientZioBackend.stub)
        .whenServerEndpointsRunLogic(
          controller.endpoints
        )
        .backend()
    } yield backend

  }
}
