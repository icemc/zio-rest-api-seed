package io.hiis.service.application.api.utils

import reflect.runtime.universe.TypeTag
import Api.ApiError
import Api.ApiError.InternalServerError
import io.hiis.service.core.utils.Logging
import zio.{ IO, ZIO }

/** Created by Ludovic Temgoua Abanda (icemc) on 19/01/2023 */

trait ApiUtils { self: Logging =>

  type ApiTask[T] = IO[ApiError, T]

  implicit def taskToApiTask[E <: Throwable: TypeTag, T](task: ZIO[Any, E, T]): ApiTask[T] =
    task.flatMapError { implicit error =>
      (error match {
        case apiError: ApiError => ZIO.succeed(apiError)
        case _                  => ZIO.succeed(InternalServerError())
      }).tap(_ => logError(s"Failed to perform operation, error: ${error.getMessage}"))
    }

  implicit def UioToApiTask[T](task: ZIO[Any, _, T]): ApiTask[T] = task.flatMapError { _ =>
    self.logError(
      s"Failed to perform operation"
    ) *>
      ZIO.succeed(InternalServerError())
  }
}
