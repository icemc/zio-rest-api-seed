package me.abanda.service.application

import me.abanda.service.application.models.Config._
import zio.config.ReadError
import zio.config.magnolia.descriptor
import zio.config.syntax.ZIOConfigNarrowOps
import zio.config.typesafe.TypesafeConfig
import zio.{ Layer, TaskLayer }

object AppConfig {
  final case class ConfigDescriptor(
      mongodb: MongodbConfig,
      appServer: AppServerConfig,
      auth: AuthConfig
  )

  val appConfig: Layer[ReadError[String], ConfigDescriptor] =
    TypesafeConfig.fromResourcePath(descriptor[ConfigDescriptor])

  val live: TaskLayer[AllConfig] =
    appConfig.narrow(_.mongodb) >+>
      appConfig.narrow(_.appServer) >+>
      appConfig.narrow(_.auth)

}
