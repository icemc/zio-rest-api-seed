package me.abanda.service.application.services.security

import me.abanda.service.application.models.Config.AuthConfig
import me.abanda.service.application.models.auth.Identity
import pdi.jwt.JwtAlgorithm
import pdi.jwt.algorithms.JwtHmacAlgorithm
import zio.ZLayer

/** Created by Abanda Ludovic on 27/10/2022 */

trait AuthTokenService extends JwtService[Identity] {
  override protected def algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS384
}

final case class AuthTokenServiceImpl(config: AuthConfig) extends AuthTokenService {
  override protected def key: String = config.key

  override def header: String = config.authTokenHeader

  override def maxAge: Long = config.authTokenMaxAge
}

object AuthTokenService {
  val live: ZLayer[AuthConfig, Nothing, AuthTokenServiceImpl] =
    ZLayer.fromFunction(AuthTokenServiceImpl.apply _)
}
