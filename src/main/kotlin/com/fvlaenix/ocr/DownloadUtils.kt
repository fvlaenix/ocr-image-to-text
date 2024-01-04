package com.fvlaenix.ocr

import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URL
import java.util.concurrent.ExecutionException
import javax.imageio.IIOException
import javax.imageio.ImageIO

object DownloadUtils {
  fun downloadImage(url: String): ByteArray? {
    var reconnections = 7
    while (reconnections != 0) {
      try {
        val connection = URL(url).openConnection()
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:221.0) Gecko/20100101 Firefox/31.0")
        return connection.getInputStream().readBytes()
      } catch (ignored: IOException) { }
      reconnections--
    }
    return null
  }
}