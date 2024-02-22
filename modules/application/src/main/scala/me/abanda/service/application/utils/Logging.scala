package me.abanda.service.application.utils

import io.circe.Json
import Logging.Annotation
import me.abanda.service.application.models.auth.RequestId.ToRequest
import me.abanda.service.application.models.auth.{
  Request,
  RequestId,
  SecuredRequest,
  UnsecuredRequest,
  UserAwareRequest
}
import zio.logging.backend.SLF4J
import zio.logging.{ LogAnnotation, LogFormat }
import zio.{ Runtime, ZIO }

/** Created by Abanda Ludovic on 17/10/2022 */

trait Logging {

  protected val loggerName: String = s"${this.getClass.getName}"

  private def loggerValue = SLF4J.loggerName(loggerName)

  /**
   * Log a message with log level info. Deprecated, use the json version
   * @param message
   *   message to log
   * @param misc
   *   optional miscellaneous JSON data to add to log MDC
   * @return
   *   unit
   */
  final protected def logInfo(message: String, misc: Option[Json] = None) = {
    misc match {
      case Some(value) =>
        (for {
          _ <- ZIO.logInfo(message) @@ loggerValue
          _ <- ZIO.unit
        } yield ()) @@ Annotation.jsonMessage(value)
      case None => ZIO.logInfo(message) @@ loggerValue
    }
  }

  /**
   * Log a message with log level warning. Deprecated, use the json version
   *
   * @param message
   *   message to log
   * @param misc
   *   optional miscellaneous JSON data to add to log MDC
   * @return
   *   unit
   */
  final protected def logWarning(
      message: String,
      misc: Option[Json] = None
  ): ZIO[Any, Nothing, Unit] = {
    misc match {
      case Some(value) =>
        (for {
          _ <- ZIO.logWarning(message) @@ loggerValue
          _ <- ZIO.unit
        } yield ()) @@ Annotation.jsonMessage(value)
      case None => ZIO.logWarning(message) @@ loggerValue
    }
  }

  /**
   * Log a message with log level error. Deprecated, use the json version
   *
   * @param message
   *   message to log
   * @param misc
   *   optional miscellaneous JSON data to add to log MDC
   * @param cause
   *   the cause of this error
   * @return
   *   unit
   */
  final protected def logError(message: String, misc: Option[Json] = None)(implicit
      cause: Throwable = new Throwable(message)
  ): ZIO[Any, Nothing, Unit] = {
    val log: ZIO[Any, Nothing, Unit] = for {
      _ <- ZIO.logError(message) @@ loggerValue @@ Annotation.stackTrace(
        cause.getStackTrace.map(_.toString).mkString("\n")
      )
    } yield ()

    misc match {
      case Some(value) =>
        log @@ Annotation.jsonMessage(value)
      case None => log
    }
  }

  /**
   * Log a message with log level debug. Deprecated, use the json version
   *
   * @param message
   *   message to log
   * @param misc
   *   optional miscellaneous JSON data to add to log MDC
   * @return
   *   unit
   */
  final protected def logDebug(
      message: String,
      misc: Option[Json] = None
  ): ZIO[Any, Nothing, Unit] = {
    misc match {
      case Some(value) =>
        (for {
          _ <- ZIO.logDebug(message) @@ loggerValue
          _ <- ZIO.unit
        } yield ()) @@ Annotation.jsonMessage(value)
      case None => ZIO.logDebug(message) @@ loggerValue
    }
  }
}

object Logging {
  private val spanFormat: LogFormat =
    LogFormat.make { (builder, _, _, _, _, _, _, logSpans, _) =>
      val now = System.currentTimeMillis()
      logSpans.foreach { span =>
        builder.appendKeyValue("label", span.label)
        builder.appendKeyValue(s"${span.label}_time_elapsed", s"${now - span.startTime}ms")
      }
    }

  val logger = Runtime.removeDefaultLoggers >>> SLF4J.slf4j(SLF4J.logFormatDefault + spanFormat)

  object Annotation {
    val userId = LogAnnotation[String](
      name = "user_id",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val requestId = LogAnnotation[String](
      name = "request_id",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val externalRequestId = LogAnnotation[String](
      name = "external_request_id",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val sessionId = LogAnnotation[String](
      name = "session_id",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val path = LogAnnotation[String](
      name = "path",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val method = LogAnnotation[String](
      name = "method",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val `type` = LogAnnotation[String](
      name = "type",
      combine = (_: String, r: String) => r,
      render = identity
    )

    val jsonMessage = LogAnnotation[Json](
      name = "misc",
      combine = (_: Json, r: Json) => r,
      render = _.spaces2
    )

    val stackTrace = LogAnnotation[String](
      name = "stack_trace",
      combine = (_: String, r: String) => r,
      render = identity
    )

    def annotateWithRequest[R, E, T](
        zio: => ZIO[R, E, T]
    )(implicit request: Request): ZIO[R, E, T] = {
      request match {
        case SecuredRequest(_identity, _requestId, path, method, _, _sessionId) =>
          _sessionId match {
            case Some(value) =>
              zio @@ this.method(method) @@ this.path(path) @@ userId(_identity.id) @@ requestId(
                _requestId.id
              ) @@ sessionId(value.id)
            case None =>
              zio @@ this.method(method) @@ this.path(path) @@ userId(_identity.id) @@ requestId(
                _requestId.id
              )
          }
          zio @@ this.method(method) @@ this.path(path) @@ userId(_identity.id) @@ requestId(
            _requestId.id
          ) @@ sessionId(
            _sessionId.getOrElse(RequestId.DUMMY_REQUEST_ID.toRequestId).id
          )
        case UnsecuredRequest(requestId, path, method, _) =>
          zio @@ this.method(method) @@ this.path(path) @@ this.requestId(requestId.id)
        case UserAwareRequest(identity, requestId, path, method, _, sessionId) =>
          (identity, sessionId) match {
            case (Some(i), Some(s)) =>
              zio @@ this.method(method) @@ this.path(path) @@ this.userId(i.id) @@ this.requestId(
                requestId.id
              ) @@ this
                .sessionId(s.id)
            case (_, Some(s)) =>
              zio @@ this.method(method) @@ this.path(path) @@ this.requestId(requestId.id) @@ this
                .sessionId(s.id)
            case (Some(i), _) =>
              zio @@ this.method(method) @@ this.path(path) @@ this.userId(i.id) @@ this.requestId(
                requestId.id
              )
            case _ => zio @@ this.method(method) @@ this.path(path) @@ this.requestId(requestId.id)
          }
        case r @ _ =>
          zio @@ this.method(r.method) @@ this.path(r.path) @@ this.requestId(r.requestId.id)
      }
    }
  }
}
