package com.fvlaenix.ocr

import com.google.auth.oauth2.ServiceAccountCredentials
import kotlin.io.path.Path
import kotlin.io.path.inputStream

val pathToCredentials = System.getenv("OCR_CREDENTIALS_PATH")?.let { Path(it) } ?: Path("credentials", "credentials.json")

object CredentialsUtils {
  fun getCredentials(): ServiceAccountCredentials {
    return ServiceAccountCredentials
      .fromStream(pathToCredentials.inputStream())
  }
}