package com.fvlaenix.ocr

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.util.logging.Logger

object DownloadUtils {
  private const val MAX_IMAGE_BYTES: Long = 8L * 1024L * 1024L
  private const val CONNECT_TIMEOUT_MS = 5_000
  private const val READ_TIMEOUT_MS = 10_000
  private val logger: Logger = Logger.getLogger(DownloadUtils::class.java.name)

  fun downloadImage(url: String): ByteArray? = downloadImage(url) { parsedUrl -> parsedUrl.openConnection() }

  internal fun downloadImage(url: String, openConnection: (URL) -> URLConnection): ByteArray? {
    val parsedUrl = runCatching { URL(url) }.getOrElse {
      logger.warning("Invalid URL: $url")
      return null
    }
    val protocol = parsedUrl.protocol.lowercase()
    if (protocol != "http" && protocol != "https") {
      logger.warning("Unsupported URL scheme: $protocol")
      return null
    }
    var reconnections = 7
    while (reconnections != 0) {
      try {
        val connection = openConnection(parsedUrl)
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:221.0) Gecko/20100101 Firefox/31.0")
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_IMAGE_BYTES) {
          logger.warning("Image too large ($contentLength bytes): $url")
          return null
        }
        connection.getInputStream().use { input ->
          val output = ByteArrayOutputStream()
          val buffer = ByteArray(8 * 1024)
          var total: Long = 0
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > MAX_IMAGE_BYTES) {
              logger.warning("Image exceeded max size ($total bytes): $url")
              return null
            }
            output.write(buffer, 0, read)
          }
          return output.toByteArray()
        }
      } catch (error: IOException) {
        logger.warning("Download attempt failed (${8 - reconnections}/7): ${error.message}")
      }
      reconnections--
    }
    return null
  }
}
