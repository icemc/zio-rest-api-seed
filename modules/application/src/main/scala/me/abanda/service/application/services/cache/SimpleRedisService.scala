package me.abanda.service.application.services.cache

import zio.ZLayer
import zio.redis.Redis

sealed trait SimpleRedisService extends RedisService

final case class SimpleRedisServiceImpl(client: Redis) extends SimpleRedisService

object SimpleRedisService {
  val live = ZLayer.fromFunction(SimpleRedisServiceImpl.apply _)
}
