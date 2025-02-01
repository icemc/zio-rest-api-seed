package io.hiis.service.application.api.utils

import io.circe.{ Decoder, Encoder, HCursor, Json }
import io.circe.syntax.EncoderOps
import io.hiis.service.core.utils.Logging
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._
import sttp.tapir.EndpointOutput

/** Created by Ludovic Temgoua Abanda (icemc) on 19/01/2023 */
private[api] trait Api extends ApiUtils { self: Logging =>
  import Api.ApiError
  import Api.ApiError._

  /**
   * Provides the API error to Status code mappings using oneOf. Computes all the available status
   * code mapping, by default only BadRequest and InternalServerError are defined
   * @param otherErrors
   *   the other API errors to consider
   * @return
   *   Endpoint output with errorOut already defined
   */
  final def ExtraErrors(
      otherErrors: Class[_ <: ApiError]*
  ): EndpointOutput.OneOf[ApiError, ApiError] = {
    val all = Seq(badRequest, internalServerError) ++ otherErrors

    val variants = all.distinct.map {
      case classType if classType.isAssignableFrom(badRequest) =>
        oneOfVariant(
          statusCode(StatusCode.BadRequest).and(jsonBody[BadRequest].description("Bad request"))
        )

      case classType if classType.isAssignableFrom(internalServerError) =>
        oneOfVariant(
          statusCode(StatusCode.InternalServerError).and(
            jsonBody[InternalServerError].description("Internal server error")
          )
        )

      case classType if classType.isAssignableFrom(serviceUnavailable) =>
        oneOfVariant(
          statusCode(StatusCode.ServiceUnavailable).and(
            jsonBody[ServiceUnavailable].description("Service unavailable")
          )
        )

      case classType if classType.isAssignableFrom(notFound) =>
        oneOfVariant(
          statusCode(StatusCode.NotFound).and(jsonBody[NotFound].description("Not found"))
        )

      case classType if classType.isAssignableFrom(forbidden) =>
        oneOfVariant(
          statusCode(StatusCode.Forbidden).and(jsonBody[Forbidden].description("Forbidden"))
        )

      case classType if classType.isAssignableFrom(unauthorized) =>
        oneOfVariant(
          statusCode(StatusCode.Unauthorized).and(
            jsonBody[Unauthorized].description("Unauthorized")
          )
        )

      case classType if classType.isAssignableFrom(conflict) =>
        oneOfVariant(
          statusCode(StatusCode.Conflict).and(jsonBody[Conflict].description("Conflict"))
        )
    }

    oneOf[ApiError](variants.head, variants.tail: _*)
  }
}

object Api {

  class ApiError(val message: String, val code: Int = 500)
      extends Throwable(message)
      with Serializable

  object ApiError {

    implicit val encoder: Encoder[ApiError] = (a: ApiError) =>
      Json.obj("message" -> a.message.asJson, "code" -> a.code.asJson)

    implicit val decoder: Decoder[ApiError] = (c: HCursor) =>
      for {
        message <- c.downField("message").as[String]
        code    <- c.downField("code").as[Int]
      } yield new ApiError(message, code)

    implicit def decoderSubtypes[T <: ApiError]: Decoder[T] = decoder.asInstanceOf[Decoder[T]]
    implicit def encoderSubTypes[T <: ApiError]: Encoder[T] = encoder.contramap(identity)

    final case class Forbidden(override val message: String)        extends ApiError(message, 403)
    final case class BadRequest(override val message: String)       extends ApiError(message, 400)
    final case class NotFound(override val message: String)         extends ApiError(message, 404)
    final case class Conflict(override val message: String)         extends ApiError(message, 409)
    final case class Unauthorized(override val message: String)     extends ApiError(message, 401)
    final case class MethodNotAllowed(override val message: String) extends ApiError(message, 405)
    final case class InternalServerError(
        override val message: String = "Server error!"
    ) extends ApiError(message, 500)
    final case class ServiceUnavailable(
        override val message: String = "The service is unavailable"
    ) extends ApiError(message, 503)

    // Simple definitions to help obtaining the class type of subsequent API errors
    val forbidden: Class[Forbidden]                     = classOf[Forbidden]
    val badRequest: Class[BadRequest]                   = classOf[BadRequest]
    val notFound: Class[NotFound]                       = classOf[NotFound]
    val conflict: Class[Conflict]                       = classOf[Conflict]
    val unauthorized: Class[Unauthorized]               = classOf[Unauthorized]
    val internalServerError: Class[InternalServerError] = classOf[InternalServerError]
    val serviceUnavailable: Class[ServiceUnavailable]   = classOf[ServiceUnavailable]
  }
}
