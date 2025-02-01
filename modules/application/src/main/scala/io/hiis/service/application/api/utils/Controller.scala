package io.hiis.service.application.api.utils

import io.circe.Json
import io.circe.syntax.EncoderOps
import io.hiis.service.application.services.security.AuthTokenService
import Api.ApiError
import Api.ApiError.{ unauthorized, Unauthorized }
import io.hiis.service.application.api.utils.tapir.{ PartialServerEndpointT, TapirT }
import io.hiis.service.application.api.utils.tapir.TapirT._
import io.hiis.service.core.models.Constants.CustomHeaders
import io.hiis.service.core.models.Constants.CustomHeaders.{
  REQUEST_ID_HEADER,
  SESSION_ID_HEADER,
  USER_ID_HEADER
}
import io.hiis.service.core.models.auth.Identity.StringToUserId
import io.hiis.service.core.models.auth.RequestId.ToRequest
import io.hiis.service.core.models.auth.{
  Identity,
  RequestId,
  SecuredRequest,
  UnsecuredRequest,
  UserAwareRequest
}
import io.hiis.service.core.utils.Logging
import sttp.tapir.model.ServerRequest
import sttp.tapir.{ Endpoint, EndpointInput, Tapir }
import zio.ZIO

/** Created by Ludovic Temgoua Abanda (icemc) on 19/01/2023 */

private[api] trait Controller extends Api with Tapir with TapirT { self: Logging =>
  protected def BaseUrl: EndpointInput[Unit] = "api" / "v1"

  def tag: String

  def endpoints: List[ServerEndpointT[Any, Any]]

  /**
   * Defines a secured endpoint, that verifies if user is authenticated before proceeding
   *
   * @param otherErrors
   *   the other APIErrors returned by this endpoints
   * @return
   *   a PartialServerEndpoint
   */
  final protected def SecuredEndpoint(
      otherErrors: Class[_ <: ApiError]*
  )(implicit
      authTokenService: AuthTokenService
  ): PartialServerEndpointT[
    Any,
    (String, String, String, ServerRequest),
    SecuredRequest[Identity],
    Unit,
    ApiError,
    Unit,
    Any
  ] =
    endpoint
      .securityIn(header[String](authTokenService.header))
      .securityIn(header[String](CustomHeaders.REQUEST_ID_HEADER))
      .securityIn(header[String](CustomHeaders.SESSION_ID_HEADER))
      .securityIn(extractFromRequest(identity))
      .errorOut(ExtraErrors(otherErrors.+:(unauthorized): _*))
      .prependIn(BaseUrl)
      .serverSecurityLogicT { input =>
        val request = input._4
        for {
          identity <- (authTokenService
            .getBody(input._1) <*>
            authTokenService
              .isValid(input._1))
            .flatMap {
              case (Some(value), true) => ZIO.succeed(value)
              case _                   => ZIO.fail(Unauthorized("Could not authenticate user"))
            }
            .flatMapError { implicit error =>
              val response = Unauthorized("Unauthorized")
              logError(
                "Incoming http request error. Reason: Unauthorized",
                Some(
                  Json
                    .obj(
                      "type" -> Json.fromString("api-gateway-request-error"),
                      "userId" -> request
                        .header(USER_ID_HEADER)
                        .map(_.toIdentity.id.asJson)
                        .getOrElse(Json.Null),
                      "requestId" -> request
                        .header(REQUEST_ID_HEADER)
                        .map(_.toRequestId.id.asJson)
                        .getOrElse(Json.Null),
                      "sessionId" -> request
                        .header(SESSION_ID_HEADER)
                        .map(_.toRequestId.id.asJson)
                        .getOrElse(Json.Null),
                      "method"   -> request.method.method.asJson,
                      "response" -> Json.obj("message" -> response.asJson),
                      "path"     -> request.uri.toJavaUri.asJson
                    )
                    .dropEmptyValues
                    .dropNullValues
                )
              ) *> ZIO.succeed(response)
            }
        } yield SecuredRequest(
          identity,
          RequestId(input._2),
          request.uri.toJavaUri.toString,
          request.method.method,
          input._4.headers.toList,
          Some(input._3.toRequestId)
        )
      }

  /**
   * Defines a user aware endpoint. It isn't strict like the SecuredEndpoint since authentication is
   * not obligatory
   *
   * @param otherErrors
   *   the other APIErrors returned by this endpoint
   * @return
   *   a PartialServerEndpoint
   */
  final protected def UserAwareEndpoint(
      otherErrors: Class[_ <: ApiError]*
  )(implicit
      authTokenService: AuthTokenService
  ): PartialServerEndpointT[
    Any,
    (Option[String], String, Option[String], ServerRequest),
    UserAwareRequest[Identity],
    Unit,
    ApiError,
    Unit,
    Any
  ] =
    endpoint
      .securityIn(header[Option[String]](authTokenService.header))
      .securityIn(header[String](CustomHeaders.REQUEST_ID_HEADER))
      .securityIn(header[Option[String]](CustomHeaders.SESSION_ID_HEADER))
      .securityIn(extractFromRequest(identity))
      .errorOut(ExtraErrors(otherErrors: _*))
      .prependIn(BaseUrl)
      .serverSecurityLogicT(input =>
        for {
          identity <- ZIO
            .fromOption(input._1)
            .flatMap(authTokenService.getBody)
            .fold(identity, identity)
          _path   <- ZIO.succeed(input._4.uri.toJavaUri.toString)
          _method <- ZIO.succeed(input._4.method.method)
        } yield UserAwareRequest(
          identity,
          input._2.toRequestId,
          _path,
          _method,
          input._4.headers.toList,
          input._3.map(_.toRequestId)
        )
      )

  /**
   * Defines an unsecured endpoint
   *
   * @param otherErrors
   *   the other APIErrors returned by this endpoint
   * @return
   *   an Endpoint
   */
  final protected def UnsecuredEndpoint(
      otherErrors: Class[_ <: ApiError]*
  ): PartialServerEndpointT[
    Any,
    (String, ServerRequest),
    UnsecuredRequest,
    Unit,
    ApiError,
    Unit,
    Any
  ] =
    endpoint
      .securityIn(header[String](CustomHeaders.REQUEST_ID_HEADER))
      .securityIn(extractFromRequest(identity))
      .errorOut(ExtraErrors(otherErrors: _*))
      .prependIn(BaseUrl)
      .serverSecurityLogicT(input =>
        for {
          _path   <- ZIO.succeed(input._2.uri.toJavaUri.toString)
          _method <- ZIO.succeed(input._2.method.method)
        } yield UnsecuredRequest(input._1.toRequestId, _path, _method)
      )

  /**
   * Defines a simple endpoint with API Error enhancement
   *
   * @param otherErrors
   *   the other APIErrors returned by this endpoint
   * @return
   *   an Endpoint
   */
  final protected def SimpleEndpoint(
      otherErrors: Class[_ <: ApiError]*
  ): Endpoint[Unit, Unit, ApiError, Unit, Any] =
    endpoint.errorOut(ExtraErrors(otherErrors: _*))
}
