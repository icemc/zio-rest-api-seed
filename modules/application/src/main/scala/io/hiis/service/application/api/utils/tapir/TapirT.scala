package io.hiis.service.application.api.utils.tapir

import io.circe.{ parser, Encoder, Json }
import io.circe.syntax.EncoderOps
import io.hiis.service.application.api.utils.Api.ApiError
import PartialServerEndpointT.BasePartialServerEndpointT
import TapirT.{ ServerEndpointT, ServerEndpointWithProp }
import io.hiis.service.core.models.auth.Request
import io.hiis.service.core.utils.Logging
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorValueOps
import sttp.tapir.server.ServerEndpoint
import sttp.tapir._
import zio.{ RIO, ZIO }

trait TapirT { self: Logging =>

  implicit class RichEndpointT[SECURITY_INPUT, INPUT, ERROR_OUTPUT <: ApiError, OUTPUT, C](
      e: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
  ) {

    /**
     * Combine this public endpoint description with a function, which implements the server-side
     * logic. The logic returns a result, which is either an error or a successful output, wrapped
     * in an effect type `F`. For secure endpoints, use [[zServerSecurityLogic]].
     *
     * A server endpoint can be passed to a server interpreter. Each server interpreter supports
     * effects of a specific type(s).
     *
     * Both the endpoint and logic function are considered complete, and cannot be later extended
     * through the returned [[ServerEndpoint]] value (except for endpoint meta-data). Secure
     * endpoints allow providing the security logic before all the inputs and outputs are specified.
     */
    def serverLogicT[R](
        logic: INPUT => ZIO[R, ERROR_OUTPUT, OUTPUT]
    )(implicit aIsUnit: SECURITY_INPUT =:= Unit): ServerEndpointT[R, C] =
      ServerEndpointWithProp.public(
        e.asInstanceOf[Endpoint[Unit, INPUT, ERROR_OUTPUT, OUTPUT, C]],
        _ => logic(_: INPUT).either.resurrect
      )

    /**
     * Combine this endpoint description with a function, which implements the security logic of the
     * endpoint.
     *
     * Subsequently, the endpoint inputs and outputs can be extended (but not error outputs!). Then
     * the main server logic can be provided, given a function which accepts as arguments the result
     * of the security logic and the remaining input. The final result is then a [[ServerEndpoint]].
     *
     * A complete server endpoint can be passed to a server interpreter. Each server interpreter
     * supports effects of a specific type(s).
     *
     * An example use-case is defining an endpoint with fully-defined errors, and with security
     * logic built-in. Such an endpoint can be then extended by multiple other endpoints, by
     * specifying different inputs, outputs and the main logic.
     */
    def serverSecurityLogicT[R, U <: Request](
        f: SECURITY_INPUT => ZIO[R, ERROR_OUTPUT, U]
    ): PartialServerEndpointT[R, SECURITY_INPUT, U, INPUT, ERROR_OUTPUT, OUTPUT, C] =
      BasePartialServerEndpointT(e, f)
  }

  implicit class RichHiisServerEndpoint[R, C](zse: ServerEndpointT[R, C]) {

    /** Extends the environment so that it can be made uniform across multiple endpoints. */
    def widen[R2 <: R]: ServerEndpointT[R2, C] =
      zse.asInstanceOf[ServerEndpointT[R2, C]] // this is fine
  }
}

object TapirT {

  /**
   * Rich Server endpoint with ability to exclude endpoint from API doc
   * @param isExcluded
   *   exclusion flag
   * @tparam R
   * @tparam F
   */
  abstract class ServerEndpointWithProp[-R, F[_]](val isExcluded: Boolean = false)
      extends ServerEndpoint[R, F] {

    override def name(
        n: String
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      withInfo(info.name(n))

    override def summary(
        s: String
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      withInfo(info.summary(s))

    override def description(
        d: String
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      withInfo(info.description(d))

    override def tags(
        ts: List[String]
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      withInfo(info.tags(ts))

    override def tag(
        t: String
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      withInfo(info.tag(t))

    override def withInfo(
        info: EndpointInfo
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      ServerEndpointWithProp(endpoint.info(info), securityLogic, logic, isExcluded)
    def excludeFromDocs: ServerEndpointWithProp[R, F] =
      ServerEndpointWithProp(this.endpoint, this.securityLogic, this.logic, isExcluded = true)
  }

  object ServerEndpointWithProp {
    private def emptySecurityLogic[E, F[_]]: MonadError[F] => Unit => F[Either[E, Unit]] =
      implicit m => _ => (Right(()): Either[E, Unit]).unit

    /**
     * The full type of a server endpoint, capturing the types of all input/output parameters. Most
     * of the time, the simpler `ServerEndpoint[R, F]` can be used instead.
     */
    type Full[_SECURITY_INPUT, _PRINCIPAL, _INPUT, _ERROR_OUTPUT, _OUTPUT, -R, F[_]] =
      ServerEndpointWithProp[R, F] {
        type SECURITY_INPUT = _SECURITY_INPUT
        type PRINCIPAL      = _PRINCIPAL
        type INPUT          = _INPUT
        type ERROR_OUTPUT   = _ERROR_OUTPUT
        type OUTPUT         = _OUTPUT
      }

    /**
     * Create a public server endpoint, with an empty (no-op) security logic, which always succeeds.
     */
    def public[INPUT, ERROR_OUTPUT, OUTPUT, R, F[_]](
        endpoint: Endpoint[Unit, INPUT, ERROR_OUTPUT, OUTPUT, R],
        logic: MonadError[F] => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
    ): ServerEndpointWithProp.Full[Unit, Unit, INPUT, ERROR_OUTPUT, OUTPUT, R, F] =
      ServerEndpointWithProp(endpoint, emptySecurityLogic, m => _ => logic(m))

    /**
     * Create a server endpoint, with the given security and main logic functions, which match the
     * shape defined by `endpoint`.
     */
    def apply[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F[_]](
        endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R],
        securityLogic: MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]],
        logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]],
        isExcluded: Boolean = false
    ): ServerEndpointWithProp.Full[SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, R, F] = {
      type _SECURITY_INPUT = SECURITY_INPUT
      type _PRINCIPAL      = PRINCIPAL
      type _INPUT          = INPUT
      type _ERROR_OUTPUT   = ERROR_OUTPUT
      type _OUTPUT         = OUTPUT
      val e  = endpoint
      val s  = securityLogic
      val l  = logic
      val ex = isExcluded
      new ServerEndpointWithProp[R, F](isExcluded = ex) {
        override type SECURITY_INPUT = _SECURITY_INPUT
        override type PRINCIPAL      = _PRINCIPAL
        override type INPUT          = _INPUT
        override type ERROR_OUTPUT   = _ERROR_OUTPUT
        override type OUTPUT         = _OUTPUT

        override def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, R] = e

        override def securityLogic
            : MonadError[F] => SECURITY_INPUT => F[Either[ERROR_OUTPUT, PRINCIPAL]] = s

        override def logic: MonadError[F] => PRINCIPAL => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]] =
          l
      }
    }
  }

  type ServerEndpointT[R, -C] = ServerEndpointWithProp[C, RIO[R, *]]
}

import sttp.tapir.{
  Endpoint,
  EndpointInfo,
  EndpointInfoOps,
  EndpointInput,
  EndpointInputsOps,
  EndpointMetaOps,
  EndpointOutput,
  EndpointOutputsOps
}

/**
 * An endpoint with the security logic provided, and the main logic yet unspecified. See.
 *
 * The provided security part of the server logic transforms inputs of type `SECURITY_INPUT`, either
 * to an error of type `ERROR_OUTPUT`, or value of type `PRINCIPAL`.
 *
 * The part of the server logic which is not provided, will have to transform a tuple: `(PRINCIPAL,
 * INPUT)` either into an error, or a value of type `OUTPUT`.
 *
 * Inputs/outputs can be added to partial endpoints as to regular endpoints, however the shape of
 * the error outputs is fixed and cannot be changed. Hence, it's possible to create a base, secured
 * input, and then specialise it with inputs, outputs and logic as needed.
 *
 * @tparam SECURITY_INPUT
 *   Type of the security inputs, transformed into PRINCIPAL
 * @tparam PRINCIPAL
 *   Type of transformed security input.
 * @tparam INPUT
 *   Input parameter types.
 * @tparam ERROR_OUTPUT
 *   Error output parameter types.
 * @tparam OUTPUT
 *   Output parameter types.
 * @tparam C
 *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no
 *   requirements.
 */
sealed trait PartialServerEndpointT[
    R,
    SECURITY_INPUT,
    PRINCIPAL <: Request,
    INPUT,
    ERROR_OUTPUT <: ApiError,
    OUTPUT,
    -C
] extends EndpointInputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
    with EndpointOutputsOps[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
    with EndpointInfoOps[C]
    with EndpointMetaOps { self: Logging =>

  def isExcludedFromDocs: Boolean

  def endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]

  def securityLogic: SECURITY_INPUT => ZIO[R, ERROR_OUTPUT, PRINCIPAL]

  override type ThisType[-_R] =
    PartialServerEndpointT[R, SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, _R]
  override type EndpointType[_A, _I, _E, _O, -_R] =
    PartialServerEndpointT[R, _A, PRINCIPAL, _I, _E with ApiError, _O, _R]

  override def securityInput: EndpointInput[SECURITY_INPUT] = endpoint.securityInput
  override def input: EndpointInput[INPUT]                  = endpoint.input
  def errorOutput: EndpointOutput[ERROR_OUTPUT]             = endpoint.errorOutput
  override def output: EndpointOutput[OUTPUT]               = endpoint.output
  override def info: EndpointInfo                           = endpoint.info

  def withInput[I2, C2](
      input: EndpointInput[I2]
  ): PartialServerEndpointT[
    R,
    SECURITY_INPUT,
    PRINCIPAL,
    I2,
    ERROR_OUTPUT with ApiError,
    OUTPUT,
    C with C2
  ] =
    BasePartialServerEndpointT(endpoint.copy(input = input), securityLogic)

  def withOutput[O2, C2](output: EndpointOutput[O2]) =
    BasePartialServerEndpointT(endpoint.copy(output = output), securityLogic)

  def withInfo(info: EndpointInfo) =
    BasePartialServerEndpointT(endpoint.copy(info = info), securityLogic)

  override protected def showType: String = "PartialServerEndpoint"

  def excludeFromDocs = BasePartialServerEndpointT(
    endpoint.copy(info = info),
    securityLogic,
    isExcludedFromDocs = true
  )

  def serverLogic[R0](
      logic: PRINCIPAL => INPUT => ZIO[R0, ERROR_OUTPUT, OUTPUT]
  )(implicit
      encoderO: Encoder[OUTPUT],
      encoderE: Encoder[ERROR_OUTPUT]
  ): ServerEndpointT[R with R0, C] = {
//    import zio.IsSubtypeOfError.impl

    ServerEndpointWithProp(
      endpoint,
      _ => securityLogic(_: SECURITY_INPUT).either.resurrect,
      _ =>
        (u: PRINCIPAL) =>
          (i: INPUT) =>
            {
              implicit val u2: PRINCIPAL = u
              Logging.Annotation.annotateWithRequest(
                logic(u)(i)
                  .flatMap { output =>
                    logInfo(
                      "Request processed successfully",
                      Some(
                        Json.obj(
                          "type"   -> Json.fromString("api-gateway-response"),
                          "method" -> u2.method.asJson,
                          "path"   -> u2.path.asJson,
                          "body"   -> parser.parse(output.toString).getOrElse(output.asJson)
                        )
                      )
                    ) *> ZIO.succeed(
                      output
                    )
                  }
                  .flatMapError { implicit error =>
                    logError(
                      s"Incoming http request error. Reason ${error.message}",
                      Some(
                        Json.obj(
                          "type"   -> Json.fromString("api-gateway-request-error"),
                          "method" -> u2.method.asJson,
                          "path"   -> u2.path.asJson,
                          "body"   -> error.asJson
                        )
                      )
                    ) *> ZIO.succeed(error)
                  }
              )
            }.either.resurrect, // This is fine
      isExcluded = isExcludedFromDocs
    )
  }
}

object PartialServerEndpointT {

  /**
   * Wrapper class for PartialServerEndpointT
   * @param endpoint
   *   the endpoint instance
   * @param securityLogic
   *   the security logic function
   * @param isExcludedFromDocs
   *   flag to exclude this endpoint from docs
   * @tparam R
   *   the requirement
   * @tparam SECURITY_INPUT
   *   Type of the security inputs, transformed into PRINCIPAL
   * @tparam PRINCIPAL
   *   Type of transformed security input.
   * @tparam INPUT
   *   Input parameter types.
   * @tparam ERROR_OUTPUT
   *   Error output parameter types.
   * @tparam OUTPUT
   *   Output parameter types.
   * @tparam C
   *   The capabilities that are required by this endpoint's inputs/outputs. `Any`, if no
   *   requirements.
   */
  final case class BasePartialServerEndpointT[
      R,
      SECURITY_INPUT,
      PRINCIPAL <: Request,
      INPUT,
      ERROR_OUTPUT <: ApiError,
      OUTPUT,
      -C
  ](
      endpoint: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C],
      securityLogic: SECURITY_INPUT => ZIO[R, ERROR_OUTPUT, PRINCIPAL],
      isExcludedFromDocs: Boolean = false
  ) extends PartialServerEndpointT[R, SECURITY_INPUT, PRINCIPAL, INPUT, ERROR_OUTPUT, OUTPUT, C]
      with Logging
}
