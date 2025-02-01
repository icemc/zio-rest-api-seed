package io.hiis.service.core.models.auth

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

import java.util.UUID

/** Created by Ludovic Temgoua Abanda (icemc) on 17/01/2023 */

final case class RequestId(id: String) extends AnyVal

object RequestId {

  implicit val requestIdEncoder: Encoder[RequestId] = deriveEncoder
  implicit val requestIdDecoder: Decoder[RequestId] = deriveDecoder

  def DUMMY_REQUEST_ID: String = UUID.randomUUID().toString

  implicit def StringToRequest(value: String): RequestId = RequestId(value)

  implicit class ToRequest(value: String) {
    def toRequestId: RequestId =
      if (value.nonEmpty) value
      else DUMMY_REQUEST_ID
  }
}
