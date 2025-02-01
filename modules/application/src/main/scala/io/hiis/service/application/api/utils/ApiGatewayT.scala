package io.hiis.service.application.api.utils

import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import io.circe.{ parser, Json }
import Api.ApiError
import Api.ApiError.{ InternalServerError, MethodNotAllowed, NotFound }
import io.hiis.service.application.api.utils.tapir.TapirT.ServerEndpointT
import io.hiis.service.core.build.BuildInfo
import io.hiis.service.core.models.Config.AppServerConfig
import io.hiis.service.core.models.Constants.CustomHeaders.{
  REQUEST_ID_HEADER,
  REQUEST_TIME_STAMP_HEADER,
  SESSION_ID_HEADER,
  USER_ID_HEADER
}
import io.hiis.service.core.models.auth.Identity.StringToUserId
import io.hiis.service.core.models.auth.RequestId.{ DUMMY_REQUEST_ID, ToRequest }
import io.hiis.service.core.models.auth
import io.hiis.service.core.models.auth.{ Identity, UserAwareRequest }
import io.hiis.service.core.utils.Metrics.Request
import io.hiis.service.core.utils.Metrics.Request.Internal
import io.hiis.service.core.utils.Logging
import io.hiis.service.core.utils.Logging.Annotation.annotateWithRequest
import io.hiis.service.core.utils.Logging.logger
import sttp.apispec.openapi.{ Server => OpenApiServer }
import sttp.model.{ Header, StatusCode }
import sttp.monad.MonadError
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.RequestInterceptor.RequestResultTransform
import sttp.tapir.server.interceptor.cors.{ CORSConfig, CORSInterceptor }
import sttp.tapir.server.interceptor.cors.CORSConfig.{
  AllowedCredentials,
  AllowedHeaders,
  AllowedMethods,
  AllowedOrigin,
  ExposedHeaders,
  MaxAge
}
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.FailureMessages
import sttp.tapir.server.interceptor.reject.{ RejectContext, RejectHandler }
import sttp.tapir.server.interceptor.{ DecodeFailureContext, RequestInterceptor, RequestResult }
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.ziohttp.{ ZioHttpInterpreter, ZioHttpServerOptions }
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.{ headers, server, statusCode, stringBody, EndpointInput }
import zio._
import zio.http.Server

import java.net.InetSocketAddress
import java.time.Instant

trait ApiGatewayT extends Interceptors { self: Logging =>
  def routes: Seq[Controller]
  def config: AppServerConfig

  private def endpoints: List[ServerEndpointT[Any, Any]] = {
    def endpointsWithTags(routes: Seq[Controller]): List[ServerEndpointT[Any, Any]] =
      routes.flatMap(controller => controller.endpoints.map(_.tags(List(controller.tag)))).toList

    endpointsWithTags(routes)
  }

  // End user endpoints swagger docs
  private val endUserSwaggerEndpoint =
    SwaggerInterpreter(
      customiseDocsModel = _.copy(servers =
        List(OpenApiServer(config.serviceURL.getOrElse(s"${config.host}:${config.port}")))
      ),
      swaggerUIOptions = SwaggerUIOptions.default
        .copy(pathPrefix = List("swagger"), yamlName = "docs.yaml")
    ).fromServerEndpoints(endpoints.filterNot(_.isExcluded), BuildInfo.name, BuildInfo.version)

  // Excluded End user endpoints swagger docs
  private val excludedEndUserSwaggerEndpoint =
    SwaggerInterpreter(
      openAPIInterpreterOptions = OpenAPIDocsOptions.default,
      swaggerUIOptions = SwaggerUIOptions.default
        .copy(pathPrefix = List("docs-excluded"), yamlName = "docs.yaml"),
      addServerWhenContextPathPresent = true
    )
      .fromServerEndpoints(endpoints.filter(_.isExcluded), BuildInfo.name, BuildInfo.version)

  val cors: CORSInterceptor[Task] = CORSInterceptor.customOrThrow[Task](
    CORSConfig(
      allowedOrigin = AllowedOrigin.All,
      allowedCredentials = AllowedCredentials.Deny,
      allowedMethods = AllowedMethods.All,
      allowedHeaders = AllowedHeaders.All,
      exposedHeaders = ExposedHeaders.None,
      maxAge = MaxAge.Default,
      preflightResponseStatusCode = StatusCode.NoContent
    )
  )

  private val APP =
    ZioHttpInterpreter(
      ZioHttpServerOptions.customiseInterceptors
        .rejectHandler(HiisRejectHandler.apply)
        .decodeFailureHandler(failureHandler)
        .prependInterceptor(requestLoggingInterceptor) // Do not append this interceptor
        .appendInterceptor(errorLoggingInterceptor)    // Do not prepend this interceptor
        .appendInterceptor(cors)
        .options
    ).toHttp(
      endpoints
        ++ endUserSwaggerEndpoint
        ++ excludedEndUserSwaggerEndpoint
        :+ DocsEndpoint.get("docs", "swagger/docs.yaml")
        :+ DocsEndpoint.get("docs-excluded", "docs-excluded/docs.yaml")
    )

  def start = for {
    conf <- ZIO.succeed(
      zio.http.Server.Config.default.copy(address = new InetSocketAddress(config.port))
    )
    _ <- Server
      .serve(APP)
      .provide(
        ZLayer.succeed(
          conf
        ) >>> Server.live
      )
  } yield ()
}

trait Interceptors {
  self: Logging =>

  /** Logs http errors */
  val errorLoggingInterceptor =
    RequestInterceptor.transformResult(new RequestResultTransform[Task] {
      override def apply[B](
          request: ServerRequest,
          result: RequestResult[B]
      ): Task[RequestResult[B]] = {
        val receivedAt = Instant.parse(
          request
            .header(REQUEST_TIME_STAMP_HEADER)
            .getOrElse(Instant.now().toString)
        )

        val path   = request.uri.toJavaUri.toString
        val method = request.method.method
        result match {
          case RequestResult.Response(response) =>
            for {
              _ <-
                if (
                  !path.contains("/docs") && !path
                    .contains("/backoffice-docs") && !path.contains("/metrics")
                ) {
                  for {
                    // Add metrics for request duration
                    _ <- Internal
                      .request_duration_millis(path, method, response.code.code)
                      .update(
                        Duration.fromInstant(Instant.now().minusMillis(receivedAt.toEpochMilli))
                      )
                    // Add metrics for active requests
                    _ <- Request.Internal
                      .request_active(path, method)
                      .decrementBy(1.0d)
                    // Add metrics for request count
                    _ <- ZIO.unit @@ Request.Internal
                      .request_total(path, method, response.code.code)
                  } yield ()

                  // Ignore docs and metrics endpoint during metricsF
                } else ZIO.unit
            } yield result
          case failure @ RequestResult.Failure(failures) =>
            for {
              // Only not found http errors throw RequestResult.Failure with empty list of failures
              // Handle not found and method not allowed http errors here.
              statusCode <-
                if (failures.isEmpty || HiisRejectHandler.hasMethodMismatch(failure)) {
                  for {
                    statusCodeAndBody <- ZIO.succeed {
                      if (HiisRejectHandler.hasMethodMismatch(failure))
                        (StatusCode.MethodNotAllowed, MethodNotAllowed("Method Not Allowed"))
                      else
                        (StatusCode.NotFound, NotFound("Not Found"))
                    }
                    implicit0(_request: UserAwareRequest[Identity]) <- ZIO.succeed(
                      UserAwareRequest[Identity](
                        request
                          .header(USER_ID_HEADER)
                          .map(_.toIdentity),
                        request
                          .header(REQUEST_ID_HEADER)
                          .map(_.toRequestId)
                          .get,
                        request.uri.toJavaUri.toString,
                        request.method.method,
                        request.headers.toList,
                        request
                          .header(SESSION_ID_HEADER)
                          .map(_.toRequestId)
                      )
                    )
                    _ <- annotateWithRequest(
                      logError(
                        s"Incoming http request error. Reason: ${statusCodeAndBody._2.message}",
                        Some(
                          Json
                            .obj(
                              "type" -> Json.fromString("api-gateway-request-error"),
                              "userId" -> _request.identity
                                .map(_.id.asJson)
                                .getOrElse(Json.Null),
                              "requestId" -> _request.requestId.id.asJson,
                              "sessionId" -> _request.sessionId
                                .map(_.id.asJson)
                                .getOrElse(Json.Null),
                              "method" -> request.method.method.asJson,
                              "status" -> statusCodeAndBody._1.code.asJson,
                              "body"   -> statusCodeAndBody._2.asJson,
                              "path"   -> request.uri.toJavaUri.asJson
                            )
                            .dropEmptyValues
                            .dropNullValues
                        )
                      )
                    )
                  } yield statusCodeAndBody._1.code
                } else {
                  for {
                    statusCodeAndBody <- ZIO.succeed(
                      failures
                        .flatMap(ctx =>
                          DefaultDecodeFailureHandler
                            .respond(
                              ctx
                            )
                            .map(_._1)
                            .map(status =>
                              (
                                status,
                                new ApiError(FailureMessages.failureMessage(ctx), status.code)
                              )
                            )
                        )
                        .headOption
                        .getOrElse((StatusCode.InternalServerError, InternalServerError()))
                    )

                    implicit0(_request: UserAwareRequest[Identity]) <- ZIO.succeed(
                      UserAwareRequest[Identity](
                        request
                          .header(USER_ID_HEADER)
                          .map(_.toIdentity),
                        request
                          .header(REQUEST_ID_HEADER)
                          .map(_.toRequestId)
                          .get,
                        request.uri.toJavaUri.toString,
                        request.method.method,
                        request.headers.toList,
                        request
                          .header(SESSION_ID_HEADER)
                          .map(_.toRequestId)
                      )
                    )
                    _ <- annotateWithRequest(
                      logError(
                        s"Incoming http request error. Reason: ${statusCodeAndBody._2.message}",
                        Some(
                          Json
                            .obj(
                              "type" -> Json.fromString("api-gateway-request-error"),
                              "userId" -> _request.identity
                                .map(_.id.asJson)
                                .getOrElse(Json.Null),
                              "requestId" -> _request.requestId.id.asJson,
                              "sessionId" -> _request.sessionId
                                .map(_.id.asJson)
                                .getOrElse(Json.Null),
                              "method" -> request.method.method.asJson,
                              "status" -> statusCodeAndBody._1.code.asJson,
                              "body"   -> statusCodeAndBody._2.asJson,
                              "path"   -> request.uri.toJavaUri.asJson
                            )
                            .dropEmptyValues
                            .dropNullValues
                        )
                      )
                    )
                  } yield statusCodeAndBody._1.code
                }

              _ <-
                if (
                  !path.contains("/docs") && !path
                    .contains("/backoffice-docs") && !path.contains("/metrics")
                ) {
                  for {
                    // Add metrics for request duration
                    _ <- Request.Internal
                      .request_duration_millis(path, method, statusCode)
                      .update(
                        Duration.fromInstant(Instant.now().minusMillis(receivedAt.toEpochMilli))
                      )
                    // Add metrics for active requests
                    _ <- Request.Internal
                      .request_active(path, method)
                      .decrementBy(1.0d)
                    // Add metrics for request count
                    _ <- ZIO.unit @@ Request.Internal
                      .request_total(path, method, statusCode)
                  } yield ()
                  // Ignore docs and metrics endpoint during metrics calculations but in case of an error log them
                } else ZIO.unit
            } yield result
        }
      }
    })

  /** Logs all incoming request and starts active requests metrics gauge */
  val requestLoggingInterceptor = RequestInterceptor.transformServerRequest {
    request: ServerRequest =>
      val zioHttpRequest = request.underlying.asInstanceOf[zio.http.Request]
      for {
        body <- zioHttpRequest.body.asString.map(body => if (body.isEmpty) None else Some(body))
        headers       = request.headers.toList
        requestId     = request.header(REQUEST_ID_HEADER).getOrElse(DUMMY_REQUEST_ID)
        sessionId     = request.header(SESSION_ID_HEADER)
        userId        = request.header(USER_ID_HEADER)
        url           = request.uri.toJavaUri.toString.stripSuffix("/")
        method        = request.method.method
        remoteAddress = zioHttpRequest.remoteAddress.map(_.toString)
        _request = auth.UserAwareRequest(
          userId.map(_.toIdentity),
          requestId.toRequestId,
          url,
          method,
          headers,
          sessionId.map(_.toRequestId)
        )
        _ <-
          if (
            !url.contains("/docs") && !url.contains("/docs-excluded") && !url.endsWith(
              "/metrics"
            ) && !url.endsWith("/health") && !url.endsWith("/version")
          ) {
            annotateWithRequest(
              logInfo(
                "Incoming http request",
                Some(
                  Json
                    .obj(
                      "type"           -> Json.fromString("api-gateway-request"),
                      "method"         -> Json.fromString(method),
                      "path"           -> Json.fromString(url),
                      "remote-address" -> remoteAddress.map(_.asJson).getOrElse(Json.Null),
                      "body" -> body
                        .map(b => parser.parse(b).getOrElse(Json.fromString(b)))
                        .getOrElse(Json.Null),
                      "headers" -> headers.asJson
                    )
                    .dropEmptyValues
                    .dropNullValues
                )
              )
            )(_request) *> Request.Internal.request_active(url, method).incrementBy(1.0d)

            // Ignore docs and metrics endpoints
          } else ZIO.unit
      } yield request
        .withOverride( // Pimp the request with requestId in case there isn't one and set the request received time stamp header value
          protocolOverride = Some(request.protocol),
          connectionInfoOverride = Some(request.connectionInfo),
          headersOverride = Some(
            request.headers.filterNot(_.name == REQUEST_ID_HEADER) ++ List(
              Header(
                REQUEST_ID_HEADER,
                requestId
              ),
              Header(
                REQUEST_TIME_STAMP_HEADER,
                Instant.now().toString
              )
            )
          )
        )
  }

  /**
   * Pimps the failure response to contain status code as it is done for other errors
   *
   * @param c
   *   status code
   * @param hs
   *   headers
   * @param m
   *   message
   * @return
   *   valued endpoint output
   */
  private def failureResponse(
      c: StatusCode,
      hs: List[Header],
      m: String
  ): ValuedEndpointOutput[_] =
    server.model.ValuedEndpointOutput(
      statusCode.and(headers).and(stringBody),
      (c, hs, new ApiError(m, c.code).asJson.spaces2)
    )

  private def processFailure(ctx: DecodeFailureContext): String = {
    val runtime = Runtime.default

    val apiError = {
      val error = FailureMessages.failureMessage(ctx)

      val status: Option[StatusCode] =
        DefaultDecodeFailureHandler
          .respond(
            ctx
          )
          .map(_._1)
      status
        .map(statusCode => new ApiError(error, statusCode.code))
        .getOrElse(new ApiError(error, 500))
    }

    val path   = ctx.request.uri.toJavaUri.toString
    val method = ctx.request.method.method

    implicit val cause: Throwable = new Throwable(
      s"Incoming http request error. Reason: ${apiError.message}"
    )
    // FIXME figure a better way of doing this without using unsafe.run
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe
        .run {
          (for {
            implicit0(request: UserAwareRequest[Identity]) <- ZIO.succeed(
              auth.UserAwareRequest(
                ctx.request
                  .header(USER_ID_HEADER)
                  .map(_.toIdentity),
                ctx.request
                  .header(REQUEST_ID_HEADER)
                  .map(_.toRequestId)
                  .get,
                path,
                method,
                ctx.request.headers.toList,
                ctx.request
                  .header(SESSION_ID_HEADER)
                  .map(_.toRequestId)
              )
            )
            _ <- annotateWithRequest(
              logError(
                s"Incoming http request error. Reason: ${apiError.message}"
              )
            )
          } yield ()).provide(logger)
        }
        .getOrThrowFiberFailure()
      apiError.message
    }
  }

  /**
   * Failure handler, it is called when a request decoding error occurs such as missing required
   * header, missing query param, bad body etc. Here the failed request is logged
   */
  val failureHandler: DefaultDecodeFailureHandler[Task] = DefaultDecodeFailureHandler[Task].copy(
    failureMessage = processFailure,
    response = failureResponse
  )
}

/**
 * Handles 404 and 405 http errors. Since for 404 errors the failures list is empty and we can't get
 * the original request, logging for 404 errors is ignored here but is handled by
 * [[errorLoggingInterceptor]] Both 404 and 405 responses are pimped to contain the status code as
 * it is done for all other errors.
 *
 * @param response
 *   the status code and message of response
 */
case class HiisRejectHandler(
    response: (StatusCode, String) => ValuedEndpointOutput[_]
) extends RejectHandler[Task] {
  override def apply(
      ctx: RejectContext
  )(implicit monad: MonadError[Task]): Task[Option[ValuedEndpointOutput[_]]] = {
    for {
      statusCodeAndBody <- ZIO.succeed {
        if (HiisRejectHandler.hasMethodMismatch(ctx.failure))
          (StatusCode.MethodNotAllowed, MethodNotAllowed("Method Not Allowed"))
        else
          (StatusCode.NotFound, NotFound("Not Found"))
      }
    } yield Some(statusCodeAndBody)
      .map(value => (value._1, value._2.asJson.spaces2))
      .map(response.tupled)
  }
}

object HiisRejectHandler {
  def apply: RejectHandler[Task] =
    HiisRejectHandler((sc: StatusCode, m: String) =>
      ValuedEndpointOutput(statusCode.and(stringBody), (sc, m))
    )

  def hasMethodMismatch(f: RequestResult.Failure): Boolean =
    f.failures.map(_.failingInput).exists {
      case _: EndpointInput.FixedMethod[_] => true
      case _                               => false
    }
}
