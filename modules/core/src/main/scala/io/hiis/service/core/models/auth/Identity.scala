package io.hiis.service.core.models.auth

import io.circe.{ Decoder, Encoder, HCursor, Json }

/** Created by Ludovic Temgoua Abanda (icemc) on 17/01/2023 */

sealed trait Identity {
  def id: String
}

object Identity {
  implicit class StringToUserId(value: String) {
    def toIdentity: Identity = new Identity {
      override def id: String = value
    }
  }

  implicit def encode: Encoder[Identity] = (a: Identity) =>
    Json.obj("userId" -> Json.fromString(a.id))
  implicit def decode: Decoder[Identity] = (c: HCursor) =>
    for {
      id <- c.downField("userId").as[String]
    } yield id.toIdentity
}
