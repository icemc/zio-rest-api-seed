package io.hiis.service.application.services.cache

import zio.Task
import zio.redis._
import zio.schema.Schema
import zio.schema.codec.{ BinaryCodec, ProtobufCodec }

trait RedisService {

  /**
   * The Redis client as a ZIO effect
   * @return
   *   The redis client
   */
  protected def client: Redis

  /**
   * Adds a new object to the cache using its id
   *
   * @param id
   *   the id
   * @param obj
   *   the object
   * @return
   *   the object
   */
  def add[T: Schema](id: String, obj: T): Task[T] = client.set(id, obj).map(_ => obj)

  /**
   * Gets an object from using its id
   *
   * @param id
   *   the object's id
   * @return
   *   The object if found
   */
  def get[T: Schema](id: String): Task[Option[T]] = client.get(id).returning[T]

  /**
   * Updates the value of an object in the cache
   *
   * @param id
   *   the object's id
   * @param obj
   *   the object
   * @return
   *   The updated object
   */
  def update[T: Schema](id: String, obj: T): Task[T] = for {
    _ <- remove(id)
    _ <- add(id, obj)
  } yield obj

  /**
   * Removes an object from the cache using its id
   *
   * @param id
   *   the object's id
   * @return
   *   Unit
   */
  def remove(id: String): Task[Unit] = client.del(id).unit
}

object RedisService {

  /** The protobuf codec supplier for serialization and deserialization */
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
}
