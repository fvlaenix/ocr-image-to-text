package com.fvlaenix.ocr

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection

class DownloadUtilsTest {
  @Test
  fun rejectsUnsupportedSchemes() {
    val result = DownloadUtils.downloadImage("file:///tmp/test.png")

    assertNull(result)
  }

  @Test
  fun rejectsOversizedDownloads() {
    val data = ByteArray(16) { 1 }
    val result = DownloadUtils.downloadImage("http://example.com/test.png") { url ->
      FakeURLConnection(url, data, contentLength = 9L * 1024L * 1024L)
    }

    assertNull(result)
  }

  @Test
  fun downloadsSmallPayload() {
    val data = byteArrayOf(1, 2, 3, 4)
    val result = DownloadUtils.downloadImage("http://example.com/test.png") { url ->
      FakeURLConnection(url, data)
    }

    assertArrayEquals(data, result)
  }

  private class FakeURLConnection(
    url: URL,
    private val data: ByteArray,
    private val contentLength: Long = data.size.toLong()
  ) : URLConnection(url) {
    override fun connect() = Unit

    override fun getContentLengthLong(): Long = contentLength

    override fun getInputStream(): ByteArrayInputStream = ByteArrayInputStream(data)
  }
}
