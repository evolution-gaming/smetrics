package com.evolutiongaming.smetrics

import cats.effect.Sync
import org.http4s.{HttpRoutes, Response, Status}

object MetricsRoutes {

  import org.http4s.dsl.io._
  import cats.syntax.functor._

  def routes[F[_]: Sync](prometheus: Prometheus[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "metrics" =>
        prometheus.write004.map(metrics => Response[F](Status.Ok).withEntity(metrics))
    }
}
