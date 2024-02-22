package me.abanda.service.application.models.auth

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }

import java.time.Instant

case class JwtToken(token: String, expiresOn: Instant)

object JwtToken {

  implicit val encoder: Encoder[JwtToken] = deriveEncoder
  implicit val decoder: Decoder[JwtToken] = deriveDecoder
}
