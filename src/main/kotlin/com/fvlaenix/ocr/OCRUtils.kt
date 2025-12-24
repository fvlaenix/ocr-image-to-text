package com.fvlaenix.ocr

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fvlaenix.ocr.protobuf.*
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.extension

class RectangleSerializer : StdSerializer<OcrRectangle>(OcrRectangle::class.java) {
  override fun serialize(rectangle: OcrRectangle, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) {
    jsonGenerator.writeStartObject()
    jsonGenerator.writeNumberField("x", rectangle.x)
    jsonGenerator.writeNumberField("y", rectangle.y)
    jsonGenerator.writeNumberField("width", rectangle.width)
    jsonGenerator.writeNumberField("height", rectangle.height)
    jsonGenerator.writeStringField("text", rectangle.text)
    jsonGenerator.writeEndObject()
  }
}

class RectanglesSerializer : StdSerializer<OcrRectangles>(OcrRectangles::class.java) {
  override fun serialize(rectangles: OcrRectangles, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) {
    jsonGenerator.writeStartArray()
    rectangles.rectanglesList.forEach { rectangle ->
      jsonGenerator.writeObject(rectangle)
    }
    jsonGenerator.writeEndArray()
  }
}

class OcrTextResponseSerializer : StdSerializer<OcrTextResponse>(OcrTextResponse::class.java) {
  override fun serialize(responce: OcrTextResponse, jsonGenerator: JsonGenerator, p2: SerializerProvider?) {
    jsonGenerator.writeObject(responce.rectangles)
  }

}

object OCRUtils {
  private val defaultAnnotatorClientFactory: () -> ImageAnnotatorClient = {
    ImageAnnotatorClient.create(
      ImageAnnotatorSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(CredentialsUtils.getCredentials()))
        .build()
    )
  }
  @Volatile
  private var annotatorClientFactory: () -> ImageAnnotatorClient = defaultAnnotatorClientFactory
  @Volatile
  private var cachedAnnotatorClient: ImageAnnotatorClient? = null

  private fun getAnnotatorClient(): ImageAnnotatorClient {
    val cached = cachedAnnotatorClient
    if (cached != null) return cached
    val created = annotatorClientFactory()
    cachedAnnotatorClient = created
    return created
  }

  internal fun setAnnotatorClientForTesting(client: ImageAnnotatorClient) {
    cachedAnnotatorClient = client
  }

  internal fun resetAnnotatorClientForTesting() {
    cachedAnnotatorClient = null
    annotatorClientFactory = defaultAnnotatorClientFactory
  }

  private fun resolveImageFormat(name: String): String {
    val raw = runCatching { Path(name).extension.lowercase() }.getOrDefault("")
    val fallback = "png"
    if (raw.isBlank()) return fallback
    return if (ImageIO.getImageWritersByFormatName(raw).hasNext()) raw else fallback
  }

  fun ObjectMapper.patchMapper(): ObjectMapper {
    val module = SimpleModule()
    module.addSerializer(OcrRectangle::class.java, RectangleSerializer())
    module.addSerializer(OcrRectangles::class.java, RectanglesSerializer())
    module.addSerializer(OcrTextResponse::class.java, OcrTextResponseSerializer())
    this.registerModules(module)
    return this
  }

  fun ocrUrlToImage(url: String): OcrImageResponse {
    val imageBytes = DownloadUtils.downloadImage(url) ?: return ocrImageResponse { this.error = "Can't download image" }
    val imgBytes = ByteString.readFrom(imageBytes.inputStream())
    val extension = resolveImageFormat(URL(url).path)

    return ocrGoogleImageToImage(Image.newBuilder().setContent(imgBytes).build(), extension)
  }

  fun ocrUrlToText(url: String): OcrTextResponse {
    val imageBytes = DownloadUtils.downloadImage(url) ?: return ocrTextResponse { this.error = "Can't download image" }
    val imgBytes = ByteString.readFrom(imageBytes.inputStream())

    return ocrGoogleImageToText(Image.newBuilder().setContent(imgBytes).build())
  }

  fun ocrFileToImage(file: File, name: String): OcrImageResponse {
    return ocrBytesArrayToImage(file.readBytes(), name)
  }

  fun ocrFileToText(image: File): OcrTextResponse {
    return ocrBytesArrayToText(image.readBytes())
  }

  fun ocrBytesArrayToImage(image: ByteArray, name: String): OcrImageResponse {
    return ocrGoogleImageToImage(Image.newBuilder().setContent(ByteString.copyFrom(image)).build(), name)
  }

  fun ocrBytesArrayToText(image: ByteArray): OcrTextResponse {
    return ocrGoogleImageToText(Image.newBuilder().setContent(ByteString.copyFrom(image)).build())
  }

  fun ocrGoogleImageToImage(googleImage: Image, name: String): OcrImageResponse {
    val responseRectangles = ocrGoogleImageToText(googleImage)
    if (responseRectangles.rectangles == null) {
      return ocrImageResponse {
        this.error = responseRectangles.error
      }
    }
    val rectangles = responseRectangles.rectangles
    val bytes = googleImage.content.toByteArray()
    val bufferedImage = ImageIO.read(bytes.inputStream())
      ?: return ocrImageResponse { this.error = "Unsupported image format" }
    val graphics = bufferedImage.createGraphics()
    for (rectangle in rectangles.rectanglesList) {
      var red = 0L
      var green = 0L
      var blue = 0L
      var count = 0L
      val maxDimension = maxOf(rectangle.width, rectangle.height)
      val step = maxOf(1, (maxDimension / 200).toInt())
      for (x in rectangle.x..(rectangle.x + rectangle.width) step step.toLong()) {
        for (y in rectangle.y..(rectangle.y + rectangle.height) step step.toLong()) {
          if (x >= 0 && y >= 0 && x < bufferedImage.width && y < bufferedImage.height) {
            count++
            val rgb = bufferedImage.getRGB(x.toInt(), y.toInt())
            val color = Color(rgb)
            red += color.red
            green += color.green
            blue += color.blue
          }
        }
      }
      if (count == 0L) {
        continue
      }
      val backgroundColor = Color(255 - (red / count).toInt(), 255 - (green / count).toInt(), 255 - (blue / count).toInt(), 150)
      graphics.color = backgroundColor
      graphics.fillRect(rectangle.x.toInt(), rectangle.y.toInt(), rectangle.width.toInt(), rectangle.height.toInt())
    }
    graphics.dispose()

    val arrayBytesOutput = ByteArrayOutputStream()
    val format = resolveImageFormat(name)
    val writeOk = ImageIO.write(bufferedImage, format, arrayBytesOutput)
    if (!writeOk) {
      return ocrImageResponse { this.error = "Can't encode image using $format" }
    }
    return ocrImageResponse {
      this.result = ocrImageSuccessfulResponse {
        this.text = responseRectangles
        this.image = ByteString.readFrom(arrayBytesOutput.toByteArray().inputStream())
        this.name = name
      }
    }
  }

  fun ocrGoogleImageToText(image: Image): OcrTextResponse {
    val requests: MutableList<AnnotateImageRequest> = ArrayList()
    val feat: Feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
    val annotateImageRequest: AnnotateImageRequest = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(image).build()
    requests.add(annotateImageRequest)

    val response: BatchAnnotateImagesResponse = getAnnotatorClient().batchAnnotateImages(requests)
    val responses: List<AnnotateImageResponse> = response.responsesList
    if (responses.size != 1) {
      return ocrTextResponse { this.error = "Unexpected OCR response size: ${responses.size}" }
    }
    val first = responses[0]
    if (first.hasError() && first.error.message.isNotBlank()) {
      return ocrTextResponse { this.error = "Vision API error: ${first.error.message}" }
    }
    val annotation = first.fullTextAnnotation
    val textBlocks = mutableListOf<OcrRectangle>()

    val blocks = annotation.pagesList.flatMap { it.blocksList }

    for (block in blocks) {
      val text = block.paragraphsList.joinToString(separator = " ") { paragraph ->
        paragraph.wordsList.joinToString(separator = " ") { word ->
          word.symbolsList.joinToString(separator = "") { symbol -> symbol.text }
        }
      }.replace("\n", " ")
      val vertices = block.boundingBox.verticesList
      if (vertices.size != 4) {
        return ocrTextResponse { this.error = "Invalid OCR bounding box" }
      }
      val x = vertices.minOf { it.x }
      val y = vertices.minOf { it.y }
      val width = vertices.maxOf { it.x } - x
      val height = vertices.maxOf { it.y } - y
      val textBlock = ocrRectangle {
        this.text = text
        this.x = x.toLong()
        this.y = y.toLong()
        this.width = width.toLong()
        this.height = height.toLong()
      }
      textBlocks.add(textBlock)
    }
    return ocrTextResponse {
      this.rectangles = OcrRectangles.newBuilder().addAllRectangles(textBlocks).build()
    }
  }
}
