package io.hiis.service.application

import io.hiis.service.core.models.Config.{ AllConfig, AppServerConfig, AuthConfig, MongodbConfig }
import io.hiis.service.core.utils.ZIOConfigNarrowOps
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.TypesafeConfigProvider
import zio.{ Config, TaskLayer, ZLayer }

object AppConfig {
  final case class ConfigDescriptor(
      mongodb: MongodbConfig,
      appServer: AppServerConfig,
      auth: AuthConfig
  )

  val appConfig: ZLayer[Any, Config.Error, ConfigDescriptor] =
    ZLayer.fromZIO(TypesafeConfigProvider.fromResourcePath().load(deriveConfig[ConfigDescriptor]))

  val live: TaskLayer[AllConfig] =
    appConfig.narrow(_.mongodb) >+>
      appConfig.narrow(_.appServer) >+>
      appConfig.narrow(_.auth)

}
