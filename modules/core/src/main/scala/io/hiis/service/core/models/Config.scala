package io.hiis.service.core.models

/** Created by Ludovic Temgoua Abanda (icemc) on 17/01/2023 */

object Config {

  final case class MongodbConfig(uri: String, database: String)

  final case class AuthConfig(
      key: String,
      authTokenHeader: String,
      authTokenMaxAge: Long,
      refreshTokenHeader: String
  )

  final case class AppServerConfig(host: String, port: Int, serviceURL: Option[String])

  abstract class ExternalServiceConfig(val name: String, val host: String)

  type AllConfig = MongodbConfig with AppServerConfig with AuthConfig
}
