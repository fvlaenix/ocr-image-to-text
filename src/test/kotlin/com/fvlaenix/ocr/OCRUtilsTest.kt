package com.fvlaenix.ocr

import com.fvlaenix.ocr.protobuf.OcrImageResponse
import com.fvlaenix.ocr.protobuf.OcrTextResponse
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.AnnotateImageResponse
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse
import com.google.cloud.vision.v1.Block
import com.google.cloud.vision.v1.BoundingPoly
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.Page
import com.google.cloud.vision.v1.Paragraph
import com.google.cloud.vision.v1.Symbol
import com.google.cloud.vision.v1.TextAnnotation
import com.google.cloud.vision.v1.Vertex
import com.google.cloud.vision.v1.Word
import com.google.rpc.Status
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class OCRUtilsTest {
  @AfterEach
  fun tearDown() {
    OCRUtils.resetAnnotatorClientForTesting()
  }

  @Test
  fun returnsErrorWhenVisionError() {
    val error = Status.newBuilder().setMessage("boom").build()
    val response = AnnotateImageResponse.newBuilder().setError(error).build()
    val batch = BatchAnnotateImagesResponse.newBuilder().addResponses(response).build()
    val client = mockClient(batch)
    OCRUtils.setAnnotatorClientForTesting(client)

    val result = OCRUtils.ocrBytesArrayToText(ByteArray(0))

    assertEquals("Vision API error: boom", result.error)
    assertEquals(OcrTextResponse.ResponseCase.ERROR, result.responseCase)
  }

  @Test
  fun insertsSpacesBetweenWords() {
    val annotation = TextAnnotation.newBuilder()
      .addPages(
        Page.newBuilder().addBlocks(
          Block.newBuilder()
            .setBoundingBox(
              BoundingPoly.newBuilder().addAllVertices(
                listOf(
                  Vertex.newBuilder().setX(0).setY(0).build(),
                  Vertex.newBuilder().setX(10).setY(0).build(),
                  Vertex.newBuilder().setX(10).setY(10).build(),
                  Vertex.newBuilder().setX(0).setY(10).build()
                )
              )
            )
            .addParagraphs(
              Paragraph.newBuilder().addWords(
                Word.newBuilder()
                  .addSymbols(Symbol.newBuilder().setText("H"))
                  .addSymbols(Symbol.newBuilder().setText("i"))
              ).addWords(
                Word.newBuilder()
                  .addSymbols(Symbol.newBuilder().setText("B"))
                  .addSymbols(Symbol.newBuilder().setText("o"))
                  .addSymbols(Symbol.newBuilder().setText("b"))
              )
            )
        )
      )
      .build()
    val response = AnnotateImageResponse.newBuilder().setFullTextAnnotation(annotation).build()
    val batch = BatchAnnotateImagesResponse.newBuilder().addResponses(response).build()
    val client = mockClient(batch)
    OCRUtils.setAnnotatorClientForTesting(client)

    val result = OCRUtils.ocrBytesArrayToText(ByteArray(0))

    assertEquals(OcrTextResponse.ResponseCase.RECTANGLES, result.responseCase)
    val rectangle = result.rectangles.rectanglesList.first()
    assertEquals("Hi Bob", rectangle.text)
  }

  @Test
  fun returnsErrorWhenImageUnsupported() {
    val response = AnnotateImageResponse.newBuilder().build()
    val batch = BatchAnnotateImagesResponse.newBuilder().addResponses(response).build()
    val client = mockClient(batch)
    OCRUtils.setAnnotatorClientForTesting(client)

    val result = OCRUtils.ocrBytesArrayToImage(byteArrayOf(1, 2, 3), "test.png")

    assertEquals("Unsupported image format", result.error)
  }

  @Test
  fun handlesOutOfBoundsRectangles() {
    val annotation = TextAnnotation.newBuilder()
      .addPages(
        Page.newBuilder().addBlocks(
          Block.newBuilder()
            .setBoundingBox(
              BoundingPoly.newBuilder().addAllVertices(
                listOf(
                  Vertex.newBuilder().setX(100).setY(100).build(),
                  Vertex.newBuilder().setX(120).setY(100).build(),
                  Vertex.newBuilder().setX(120).setY(120).build(),
                  Vertex.newBuilder().setX(100).setY(120).build()
                )
              )
            )
            .addParagraphs(
              Paragraph.newBuilder().addWords(
                Word.newBuilder().addSymbols(Symbol.newBuilder().setText("X"))
              )
            )
        )
      )
      .build()
    val response = AnnotateImageResponse.newBuilder().setFullTextAnnotation(annotation).build()
    val batch = BatchAnnotateImagesResponse.newBuilder().addResponses(response).build()
    val client = mockClient(batch)
    OCRUtils.setAnnotatorClientForTesting(client)

    val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "png", output)
    val bytes = output.toByteArray()

    val result = OCRUtils.ocrBytesArrayToImage(bytes, "test.png")

    assertEquals(OcrImageResponse.ResponseCase.RESULT, result.responseCase)
    assertTrue(result.result.image.size() > 0)
  }

  private fun mockClient(response: BatchAnnotateImagesResponse): ImageAnnotatorClient {
    val client = mockk<ImageAnnotatorClient>()
    every { client.batchAnnotateImages(any<List<AnnotateImageRequest>>()) } returns response
    return client
  }
}
