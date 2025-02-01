package io.hiis.service.core.utils

import io.circe.{ Decoder, Encoder }
import java.time.Instant
import java.util.Date
import scala.concurrent.duration.{ DurationLong, FiniteDuration }
import scala.util.Try

object ImplicitJsonFormats {

  object DateFormat {
    implicit val dateEncoder: Encoder[Date] =
      Encoder.encodeString.contramap(date => date.toInstant.toString)
    implicit val dateDecoder: Decoder[Date] =
      Decoder.decodeString.emapTry(date => Try(Date.from(Instant.parse(date))))
  }

  object InstantFormat {
    implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
    implicit val instantDecoder: Decoder[Instant] =
      Decoder.decodeString.emapTry(str => Try(Instant.parse(str)))
  }

  object FiniteDurationFormat {
    implicit val dateEncoder: Encoder[FiniteDuration] =
      Encoder.encodeLong.contramap(duration => duration.toMillis)
    implicit val dateDecoder: Decoder[FiniteDuration] = Decoder.decodeLong.map(_.milli)
  }
}
