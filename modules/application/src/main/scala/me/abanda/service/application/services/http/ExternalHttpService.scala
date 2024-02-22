package me.abanda.service.application.services.http

import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import io.circe.{ parser, Decoder, Encoder, Json }
import me.abanda.service.application.models.Config.ExternalServiceConfig
import me.abanda.service.application.models.auth.RequestId.DUMMY_REQUEST_ID
import me.abanda.service.application.models.Constants.CustomHeaders
import me.abanda.service.application.models.auth.Request
import me.abanda.service.application.utils.{ Logging, Metrics }
import me.abanda.service.application.utils.Metrics.Request.External
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.model.{ Header, StatusCode, Uri }
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{ endpoint, headers, statusCode, stringBody, DecodeResult, Endpoint, Schema }
import zio.{ Duration, Task, ZIO }

import java.time.Instant
import scala.reflect.ClassTag

trait ExternalHttpService extends Logging {
  implicit final protected def requestToHeaders(implicit request: Request): List[Header] =
    filterHeaders(request.headers, CustomHeaders.ALL_IMPORTANT_HEADERS).toList

  protected def config: ExternalServiceConfig

  final protected def clientInterpreter: SttpClientInterpreter = SttpClientInterpreter()

  protected def backend: SttpBackend[Task, ZioStreams with capabilities.WebSockets]

  /**
   * Make a GET HTTP call to remote service and get response as raw String
   * @param _path
   *   api path
   * @param _headers
   *   headers to include in the request
   * @return
   *   response as String
   */
  final protected def get(
      _path: String
  )(implicit
      _headers: List[Header]
  ): Task[String] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .out(stringBody)
      .get

    for {
      response <- performRequest(
        endpoint = ep,
        input = (),
        _headers = _headers :+ Header(
          "accept",
          "text/plain"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a POST HTTP call to remote service and get response as raw String
   *
   * @param _path
   *   api path
   * @param body
   *   the body to include in the request
   * @param _headers
   *   headers to include in the request
   * @return
   *   response as String
   */
  final protected def post(
      _path: String,
      body: String
  )(implicit
      _headers: List[Header]
  ): Task[String] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .in(stringBody)
      .out(stringBody)
      .post

    for {
      response <- performRequest(
        endpoint = ep,
        input = body,
        _headers = _headers :+ Header(
          "accept",
          "text/plain"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a PUT HTTP call to remote service and get response as raw String
   *
   * @param _path
   *   api path
   * @param body
   *   the body to include in the request
   * @param _headers
   *   headers to include in the request
   * @return
   *   response as String
   */
  final protected def put(
      _path: String,
      body: String
  )(implicit
      _headers: List[Header]
  ): Task[String] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .in(stringBody)
      .out(stringBody)
      .put

    for {
      response <- performRequest[String, String](
        endpoint = ep,
        input = body,
        _headers = _headers :+ Header(
          "accept",
          "text/plain"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a DELETE HTTP call to remote service and get response as raw String
   *
   * @param _path
   *   api path
   * @param _headers
   *   headers to include in the request
   * @return
   *   response as String
   */
  final protected def delete(
      _path: String
  )(implicit
      _headers: List[Header]
  ): Task[String] = {

    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .out(stringBody)
      .get

    for {
      response <- performRequest(
        endpoint = ep,
        input = (),
        _headers = _headers :+ Header(
          "accept",
          "text/plain"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a GET HTTP call to remote service and get response as object of type [[O]]
   * @param _path
   *   api path
   * @param _headers
   *   headers to include in the request
   * @tparam O
   *   type of the response
   * @return
   *   response as [[O]]
   */
  final protected def getJson[O: Decoder: Encoder: Schema: ClassTag](
      _path: String
  )(implicit
      _headers: List[Header]
  ): Task[O] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .out(jsonBody[O])
      .get

    for {
      response <- performRequest[Unit, O](
        endpoint = ep,
        input = (),
        _headers = _headers :+ Header(
          "accept",
          "application/json"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a POST HTTP call to remote service and get response as object of type [[O]]
   *
   * @param _path
   *   api path
   * @param body
   *   to include in the request as type of [[I]]
   * @param _headers
   *   headers to include in the request
   * @tparam I
   *   type of the request body
   * @tparam O
   *   type of the response
   * @return
   *   response as [[O]]
   */
  final protected def postJson[
      I: Decoder: Encoder: Schema,
      O: Decoder: Encoder: Schema: ClassTag
  ](
      _path: String,
      body: I
  )(implicit
      _headers: List[Header]
  ): Task[O] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .in(jsonBody[I])
      .out(jsonBody[O])
      .post

    for {
      response <- performRequest(
        endpoint = ep,
        input = body,
        _headers = _headers :+ Header(
          "accept",
          "application/json"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a PUT HTTP call to remote service and get response as object of type [[O]]
   *
   * @param _path
   *   api path
   * @param body
   *   to include in the request as type of [[I]]
   * @param _headers
   *   headers to include in the request
   * @tparam I
   *   type of the request body
   * @tparam O
   *   type of the response
   * @return
   *   response as [[O]]
   */
  final protected def putJson[
      I: Decoder: Encoder: Schema,
      O: Decoder: Encoder: Schema: ClassTag
  ](
      _path: String,
      body: I
  )(implicit
      _headers: List[Header]
  ): Task[O] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .in(jsonBody[I])
      .out(jsonBody[O])
      .put

    for {
      response <- performRequest(
        endpoint = ep,
        input = body,
        _headers = _headers :+ Header(
          "accept",
          "application/json"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Make a DELETE HTTP call to remote service and get response as object of type [[O]]
   *
   * @param _path
   *   api path
   * @param _headers
   *   headers to include in the request
   * @tparam O
   *   type of the response
   * @return
   *   response as [[O]]
   */
  final protected def deleteJson[O: Decoder: Encoder: Schema: ClassTag](
      _path: String
  )(implicit
      _headers: List[Header]
  ): Task[O] = {
    val ep = endpoint
      .errorOut(statusCode)
      .errorOut(headers)
      .errorOut(stringBody)
      .out(jsonBody[O])
      .delete

    for {
      response <- performRequest(
        endpoint = ep,
        input = (),
        _headers = _headers :+ Header(
          "accept",
          "application/json"
        ),
        _path = _path
      )
    } yield response
  }

  /**
   * Filter the input header list to contain only headers found in allowedHeader
   * @param headers
   *   the input header list
   * @param allowedHeader
   *   allowed header list
   * @return
   *   a new header list containing only allowed headers
   */
  protected def filterHeaders(headers: Seq[Header], allowedHeader: Seq[String]): Seq[Header] =
    headers.filter(header => allowedHeader.contains(header.name))

  /**
   * Filter the input header list to contain only headers not found in disallowedHeader
   *
   * @param headers
   *   the input header list
   * @param disallowedHeader
   *   disallowed header list
   * @return
   *   a new header list containing headers not found in disallowed headers
   */
  protected def filterHeadersNot(
      headers: Seq[Header],
      disallowedHeader: Seq[String]
  ): Seq[Header] =
    headers.filterNot(header => disallowedHeader.contains(header.name))

  private def performRequest[
      I: Decoder: Encoder,
      O: Decoder: Encoder: ClassTag
  ](
      endpoint: Endpoint[Unit, I, (StatusCode, List[Header], String), O, Any],
      input: I,
      _headers: List[Header],
      _path: String
  ) = {
    import java.net._

    val localhost: String = InetAddress.getLocalHost.getHostAddress
    (for {
      req <- ZIO.succeed(
        clientInterpreter
          .toRequest(endpoint, Some(Uri.unsafeParse(s"${config.host}/${_path}")))
          .andThen(
            _.headers(
              _headers :+ Header("Remote-Address", localhost),
              replaceExisting = true
            ).followRedirects(true)
          )(input)
          .mapResponse {
            case DecodeResult.Value(v) => Right(v)
            case DecodeResult.Error(str, e) =>
              val message = Json
                .obj(
                  "type" -> Json.fromString("external-http-request-parse-error"),
                  "message" -> Json.fromString(
                    s"Failed to complete external http request call. Cannot decode: $str to ${implicitly[ClassTag[O]].runtimeClass.getName}"
                  ),
                  "service" -> Json.fromString(config.name),
                  "method"  -> Json.fromString(endpoint.method.get.method),
                  "host"    -> Json.fromString(config.host),
                  "path"    -> Json.fromString(_path),
                  "status"  -> Json.fromInt(200),
                  "body"    -> parser.parse(str).getOrElse(Json.fromString(str))
                )
              Left(new Throwable(message.spaces2, e))
            case f =>
              val message = Json
                .obj(
                  "type" -> Json.fromString("external-http-request-parse-error"),
                  "message" -> Json.fromString(
                    s"Failed to complete external http request call. Cannot decode: $f to ${implicitly[ClassTag[O]].runtimeClass.getName}"
                  ),
                  "service" -> Json.fromString(config.name),
                  "method"  -> Json.fromString(endpoint.method.get.method),
                  "host"    -> Json.fromString(config.host),
                  "path"    -> Json.fromString(_path),
                  "status"  -> Json.fromInt(200)
                )
              Left(new Throwable(message.spaces2))
          }
          .mapResponse(_.flatMap {
            case Left(value) =>
              val message = Json
                .obj(
                  "type"    -> Json.fromString("external-http-request-error"),
                  "message" -> Json.fromString("Could not complete http call."),
                  "service" -> Json.fromString(config.name),
                  "method"  -> Json.fromString(endpoint.method.get.method),
                  "host"    -> Json.fromString(config.host),
                  "path"    -> Json.fromString(_path),
                  "status"  -> Json.fromInt(value._1.code),
                  "body"    -> parser.parse(value._3).getOrElse(Json.fromString(value._3))
                )
              Left(new Throwable(message.spaces2))
            case Right(value) => Right(value)
          })
      )

      _ <- logInfo(
        s"Sending external http request to ${req.uri.toString}",
        Some(
          Json
            .obj(
              "type"    -> Json.fromString("external-http-request"),
              "service" -> Json.fromString(config.name),
              "method"  -> Json.fromString(endpoint.method.get.method),
              "host"    -> Json.fromString(config.host),
              "path"    -> Json.fromString(_path),
              "body"    -> input.asJson,
              "headers" -> req.headers.asJson
            )
        )
      )

      _ <- External
        .request_active(config.name, _path, endpoint.method.get.method)
        .incrementBy(1.0d)

      receivedAt = Instant.now()
      response <- backend
        .send(req)
        .map(_.body)
        .flatMap(result => ZIO.fromEither(result))
        .flatMap(result =>
          logInfo(
            s"Success sending external http request to ${req.uri.toString}",
            Some(
              Json
                .obj(
                  "type"    -> Json.fromString("external-http-request-response"),
                  "service" -> Json.fromString(config.name),
                  "method"  -> Json.fromString(endpoint.method.get.method),
                  "host"    -> Json.fromString(config.host),
                  "path"    -> Json.fromString(_path),
                  "status"  -> Json.fromInt(200),
                  "body"    -> result.asJson
                )
            )
          ) *> ZIO.succeed(result)
        )
        .flatMapError { implicit error =>
          val json = parser
            .parse(error.getMessage)
            .getOrElse(
              Json
                .obj(
                  "type" -> Json.fromString("external-http-request-error"),
                  "message" -> Json
                    .fromString(
                      s"Error sending external http request to ${req.uri.toString}. Reason: ${error.getMessage}"
                    ),
                  "service" -> Json.fromString(config.name),
                  "method"  -> Json.fromString(endpoint.method.get.method),
                  "host"    -> Json.fromString(config.host),
                  "path"    -> Json.fromString(_path),
                  "status"  -> Json.fromInt(500)
                )
            )

          for {
            _ <- logError(
              json.hcursor
                .downField("message")
                .as[String]
                .getOrElse(
                  s"Error sending external http request to ${req.uri.toString}. Reason: ${error.getMessage}"
                ),
              json.hcursor.downField("message").delete.top
            )

            status = json.hcursor.downField("status").as[Int].getOrElse(500)

            // Add metrics for request duration
            _ <- Metrics.Request.External
              .request_duration_millis(
                config.name,
                _path,
                endpoint.method.get.method,
                status
              )
              .update(
                Duration.fromInstant(Instant.now().minusMillis(receivedAt.toEpochMilli))
              )
            // Add metrics for active requests
            _ <- Metrics.Request.External
              .request_active(config.name, _path, endpoint.method.get.method)
              .decrementBy(1.0d)
            // Add metrics for total requests
            _ <- ZIO.unit @@ Metrics.Request.External.request_total(
              config.name,
              _path,
              req.method.method,
              status
            )
          } yield error
        }
      // Add metrics for request duration
      _ <- Metrics.Request.External
        .request_duration_millis(config.name, _path, endpoint.method.get.method, 200)
        .update(
          Duration.fromInstant(Instant.now().minusMillis(receivedAt.toEpochMilli))
        )
      // Add metrics for active requests
      _ <- Metrics.Request.External
        .request_active(config.name, _path, endpoint.method.get.method)
        .decrementBy(1.0d)
      // Add metrics for total requests
      _ <- ZIO.unit @@ Metrics.Request.External.request_total(
        config.name,
        _path,
        req.method.method,
        200
      )
    } yield response) @@ Logging.Annotation.externalRequestId(
      DUMMY_REQUEST_ID
    ) @@ Logging.Annotation.path(_path) @@ Logging.Annotation.method(endpoint.method.get.method)
  }
}
