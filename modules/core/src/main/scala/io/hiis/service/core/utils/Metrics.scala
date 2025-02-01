package io.hiis.service.core.utils

import io.hiis.service.core.build.BuildInfo
import zio.{ Chunk, Duration }
import zio.metrics.MetricKeyType.Histogram
import zio.metrics.{ Metric, MetricKeyType, MetricLabel, MetricState }

import java.time.temporal.ChronoUnit

object Metrics {
  val METRICS_PREFIX = s"${BuildInfo.name.replace(" ", "_").replace("-", "_").toLowerCase}"

  def timer(
      name: String,
      chronoUnit: ChronoUnit,
      boundaries: Chunk[Double]
  ): Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] = {
    val base = Metric
      .histogram(name, Histogram.Boundaries.fromChunk(boundaries))
      .tagged(MetricLabel("time_unit", chronoUnit.toString.toLowerCase()))

    base.contramap[Duration] { (duration: Duration) =>
      duration.toNanos.toDouble / chronoUnit.getDuration.toNanos.toDouble
    }
  }

  object Request {
    object Internal {
      def request_total(path: String, method: String, statusCode: Int) = Metric
        .counter(s"${METRICS_PREFIX}_request_total")
        .fromConst(1L)
        .tagged(
          MetricLabel("path", path),
          MetricLabel("method", method),
          MetricLabel("status", s"${statusCode.toString.take(1)}xx")
        )

      def request_duration_millis(path: String, method: String, statusCode: Int) = Metrics
        .timer(
          name = s"${METRICS_PREFIX}_request_duration_millis",
          chronoUnit = ChronoUnit.MILLIS,
          boundaries = Chunk.iterate(1.0, 10)(_ + 1.0)
        )
        .tagged(
          MetricLabel("path", path),
          MetricLabel("method", method),
          MetricLabel("status", s"${statusCode.toString.take(1)}xx")
        )

      def request_active(path: String, method: String) = Metric
        .gauge(s"${METRICS_PREFIX}_request_active")
        .tagged(
          MetricLabel("path", path),
          MetricLabel("method", method)
        )
    }

    object External {
      def request_total(service: String, path: String, method: String, statusCode: Int) = Metric
        .counter(s"${METRICS_PREFIX}_external_request_total")
        .fromConst(1L)
        .tagged(
          MetricLabel("service", service),
          MetricLabel("path", path),
          MetricLabel("method", method),
          MetricLabel("status", s"${statusCode.toString.take(1)}xx")
        )

      def request_duration_millis(service: String, path: String, method: String, statusCode: Int) =
        Metrics
          .timer(
            name = s"${METRICS_PREFIX}_external_request_duration_millis",
            chronoUnit = ChronoUnit.MILLIS,
            boundaries = Chunk.iterate(1.0, 10)(_ + 1.0)
          )
          .tagged(
            MetricLabel("service", service),
            MetricLabel("path", path),
            MetricLabel("method", method),
            MetricLabel("status", s"${statusCode.toString.take(1)}xx")
          )

      def request_active(service: String, path: String, method: String) = Metric
        .gauge(s"${METRICS_PREFIX}_external_request_active")
        .tagged(
          MetricLabel("service", service),
          MetricLabel("path", path),
          MetricLabel("method", method)
        )
    }
  }
}
