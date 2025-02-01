package io.hiis.service.core.models

object Constants {
  object CustomHeaders {
    val REQUEST_ID_HEADER         = "X-REQUEST-ID".toLowerCase
    val SESSION_ID_HEADER         = "X-SESSION-ID".toLowerCase
    val USER_ID_HEADER            = "X-USER-ID".toLowerCase
    val AUTH_TOKEN_HEADER         = "X-AUTH-TOKEN".toLowerCase
    val REQUEST_TIME_STAMP_HEADER = "X-REQUEST-RECEIVED-AT".toLowerCase
    val REFRESH_TOKEN_HEADER      = "X-REFRESH-TOKEN".toLowerCase

    val ALL_CUSTOM_HEADERS =
      List(
        REQUEST_ID_HEADER,
        SESSION_ID_HEADER,
        AUTH_TOKEN_HEADER,
        REFRESH_TOKEN_HEADER,
        USER_ID_HEADER
      )

    val ALL_IMPORTANT_HEADERS = ALL_CUSTOM_HEADERS ++ List("Authorization")
  }
}
