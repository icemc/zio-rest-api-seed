package me.abanda.service.application.models.auth

import io.circe.Decoder.Result
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.syntax.EncoderOps
import io.circe.{ Decoder, Encoder, HCursor, Json }
import RequestId.DUMMY_REQUEST_ID
import sttp.model.Header

/**
 * Simple wrapper around original http request. Made for internal use as the original request is too
 * verbose
 * @tparam I
 *   user identity type
 */
sealed trait Request {

  /**
   * Request ID of the request
   *
   * @return
   *   requestId
   */
  def requestId: RequestId = DUMMY_REQUEST_ID

  /**
   * Path through which the request was sent
   *
   * @return
   *   path as string
   */
  def path: String

  /**
   * The HTTP method of the request
   *
   * @return
   *   method as string
   */
  def method: String

  /**
   * The request headers
   * @return
   */
  def headers: List[Header]
}

object Request {
  implicit val headerEncoder: Encoder[Header] = new Encoder[Header] {
    override def apply(a: Header): Json = Json.obj(
      "name"  -> a.name.asJson,
      "value" -> a.value.asJson
    )
  }

  implicit val headerDecoder: Decoder[Header] = new Decoder[Header] {
    override def apply(c: HCursor): Result[Header] = for {
      name  <- c.downField("name").as[String]
      value <- c.downField("value").as[String]
    } yield new Header(name, value)
  }
}

final case class SecuredRequest[+I <: Identity](
    identity: I,
    override val requestId: RequestId,
    override val path: String,
    override val method: String,
    override val headers: List[Header] = Nil,
    sessionId: Option[RequestId] = None
) extends Request

object SecuredRequest {

  import Request._
  implicit def encoder[I <: Identity: Encoder]: Encoder[SecuredRequest[I]] =
    deriveEncoder[SecuredRequest[I]]
  implicit def decoder[I <: Identity: Decoder]: Decoder[SecuredRequest[I]] = deriveDecoder
}
final case class UnsecuredRequest(
    override val requestId: RequestId,
    override val path: String,
    override val method: String,
    override val headers: List[Header] = Nil
) extends Request

object UnsecuredRequest {
  import Request._
  implicit def encoder: Encoder[UnsecuredRequest] = deriveEncoder
  implicit def decoder: Decoder[UnsecuredRequest] = deriveDecoder
}
final case class UserAwareRequest[+I <: Identity](
    identity: Option[Identity],
    override val requestId: RequestId,
    override val path: String,
    override val method: String,
    override val headers: List[Header] = Nil,
    sessionId: Option[RequestId] = None
) extends Request

object UserAwareRequest {
  import Request._
  implicit def encoder[I <: Identity: Encoder]: Encoder[UserAwareRequest[I]] = deriveEncoder
  implicit def decoder[I <: Identity: Decoder]: Decoder[UserAwareRequest[I]] = deriveDecoder
}
