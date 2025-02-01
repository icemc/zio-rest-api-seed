package io.hiis.service.application.services.security

import io.circe.syntax._
import io.circe.{ Decoder, Encoder }
import io.hiis.service.core.models.auth.JwtToken
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{ Jwt, JwtCirce, JwtClaim }
import zio.{ UIO, ZIO }

import java.time.Instant

/** Created by Ludovic Temgoua Abanda (icemc) on 27/10/2022 */

private[security] trait JwtService[C] {

  protected def key: String

  def maxAge: Long

  def header: String

  protected def algorithm: JwtHmacAlgorithm

  /**
   * Encodes a jwt token
   * @param body
   *   the content to be included
   * @param encoder
   *   te json encoder of the body
   * @return
   *   a jwt token
   */
  final private def encode(body: C)(implicit encoder: Encoder[C]): JwtToken = {
    val claim = JwtClaim(
      expiration = Some(Instant.now.plusSeconds(maxAge).toEpochMilli),
      issuedAt = Some(Instant.now.toEpochMilli),
      content = body.asJson.noSpaces
    )

    JwtToken(JwtCirce.encode(claim, key, algorithm), Instant.ofEpochMilli(claim.expiration.get))
  }

  /**
   * Decodes a jwt token if possible
   * @param token
   *   the token to be decoded
   * @param decoder
   *   the json decoder of the body
   * @return
   *   the body content of the decoded jwt if possible
   */
  final private def decode(token: String)(implicit decoder: Decoder[C]): Option[C] =
    Jwt
      .decode(token, key, Seq(algorithm))
      .toOption
      .flatMap(claim => io.circe.parser.decode[C](claim.content).toOption)

  /**
   * Creates a new jwt token with the given body
   * @param body
   *   the body of the jwt
   * @param encoder
   *   the json encoder of the body
   * @return
   *   an effect with the Jwt token
   */
  def create(body: C)(implicit encoder: Encoder[C]): UIO[JwtToken] = ZIO.succeed(encode(body))

  /**
   * Validates a jwt token
   * @param token
   *   the jwt token
   * @param decoder
   *   the json decoder of the jwt body
   * @return
   *   effect with true if token has not expired otherwise false
   */
  def isValid(token: String)(implicit decoder: Decoder[C]): UIO[Boolean] = ZIO.succeed(
    Jwt
      .decode(token, key, Seq(algorithm))
      .toOption
      .exists(claim => Instant.ofEpochMilli(claim.expiration.get).isAfter(Instant.now()))
  )

  /**
   * Tries to get the body of a jwt token if possible
   * @param token
   *   the jwt token
   * @param decoder
   *   the json decoder of the body
   * @return
   *   effect with body if available
   */
  def getBody(token: String)(implicit decoder: Decoder[C]): UIO[Option[C]] =
    ZIO.succeed(decode(token))

}
