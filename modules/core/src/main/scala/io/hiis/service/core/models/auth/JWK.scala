package io.hiis.service.core.models.auth

final case class JWK(kty: String, e: String, n: String, alg: Option[String], kid: Option[String])

object JWK {
  val default = JWK(
    "RSA",
    "AQAB",
    "yeea9tV9OT74jgasXBZfUEgWlAYt9t_DWSw_OUjSkNL2Vff4Z10CrlzDOAXdL-RqXXhZLIELlZRd1vRYgOI8NHyGOkLXixU63Kl86GnhiFFJFc5N1jPLTKWP3K6RP2o3sY2qo_AK098vtIUilid8K3mCR2LVnu3J2MGQbtZJKfxNPrmXk_MAwrYS7sIkjFrdg6yzGB8lG2yx3QT__YR6NUFjSuGv4ROU5zGK22crhEwvfg-zOlGwMGhNNUBzRHC9ZWuU5KjDMBuKWDfevrynghm68aaF-9py8VfCwP7GlKYBctrtC-Smfa54kzNq7vgD2dn5pSIOWVUx0OLjdRlraQ",
    Some("RS256"),
    None
  )
}
