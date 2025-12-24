package com.fvlaenix.ocr

import com.fvlaenix.image.protobuf.Image
import com.fvlaenix.ocr.protobuf.OcrImageRequest
import com.fvlaenix.ocr.protobuf.OcrUrlImageRequest
import com.fvlaenix.ocr.protobuf.ocrImageResponse
import com.fvlaenix.ocr.protobuf.ocrTextResponse
import com.google.protobuf.ByteString
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OCRModuleServiceTest {
  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun delegatesOcrImage() = runTest {
    val expected = ocrTextResponse { this.error = "ok" }
    mockkObject(OCRUtils)
    every { OCRUtils.ocrBytesArrayToText(any()) } returns expected
    val service = OCRModuleService()
    val request = OcrImageRequest.newBuilder()
      .setImage(
        Image.newBuilder().setContent(ByteString.copyFrom(byteArrayOf(1, 2))).setFileName("a.png")
      )
      .build()

    val result = service.ocrImage(request)

    assertEquals(expected, result)
  }

  @Test
  fun delegatesOcrUrlImage() = runTest {
    val expected = ocrTextResponse { this.error = "ok" }
    mockkObject(OCRUtils)
    every { OCRUtils.ocrUrlToText("http://example.com") } returns expected
    val service = OCRModuleService()
    val request = OcrUrlImageRequest.newBuilder().setUrl("http://example.com").build()

    val result = service.ocrUrlImage(request)

    assertEquals(expected, result)
  }

  @Test
  fun delegatesOcrImageToImage() = runTest {
    val expected = ocrImageResponse { this.error = "ok" }
    mockkObject(OCRUtils)
    every { OCRUtils.ocrBytesArrayToImage(any(), "a.png") } returns expected
    val service = OCRModuleService()
    val request = OcrImageRequest.newBuilder()
      .setImage(
        Image.newBuilder().setContent(ByteString.copyFrom(byteArrayOf(1, 2))).setFileName("a.png")
      )
      .build()

    val result = service.ocrImageToImage(request)

    assertEquals(expected, result)
  }

  @Test
  fun delegatesOcrUrlToImage() = runTest {
    val expected = ocrImageResponse { this.error = "ok" }
    mockkObject(OCRUtils)
    every { OCRUtils.ocrUrlToImage("http://example.com") } returns expected
    val service = OCRModuleService()
    val request = OcrUrlImageRequest.newBuilder().setUrl("http://example.com").build()

    val result = service.ocrUrlToImage(request)

    assertEquals(expected, result)
  }
}
