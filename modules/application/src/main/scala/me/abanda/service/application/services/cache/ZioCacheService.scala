package me.abanda.service.application.services.cache

import zio.{ durationInt, Task, URIO, ZIO }
import zio.cache.{ Cache, Lookup }

/**
 * ZIO Cache service
 *
 * @tparam K
 *   key type
 * @tparam T
 *   cache value type
 */
trait ZioCacheService[K, +T] {
  protected def capacity: Int = 100

  protected def timeToLive: zio.Duration = 24 hours

  protected def lookup: K => Task[T]

  final protected def cache: URIO[Any, Cache[K, Throwable, T]] = Cache.make(
    capacity = this.capacity,
    timeToLive = this.timeToLive,
    lookup = Lookup.apply(lookup)
  )

  /**
   * Get value from cache
   * @param key
   *   key
   * @return
   *   cache value
   */
  def get(key: K): ZIO[Any, Throwable, T] = cache.flatMap(_.get(key))

  /**
   * Refresh value in cache
   * @param key
   *   key of the value to be refreshed
   * @return
   *   new value
   */
  def refresh(key: K): ZIO[Any, Throwable, T] = cache.flatMap(_.refresh(key)) *> get(key)

  /**
   * Invalidate cache value
   * @param key
   *   key of value to be invalidated
   * @return
   *   Nothing
   */
  def invalidate(key: K): ZIO[Any, Nothing, Unit] = cache.flatMap(_.invalidate(key))

  /**
   * Invalidate all values in the cache
   * @return
   *   Nothing
   */
  def invalidateAll: ZIO[Any, Nothing, Unit] = cache.flatMap(_.invalidateAll)
}
