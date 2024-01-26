package com.fvlaenix.ocr

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fvlaenix.ocr.protobuf.OcrRectangle
import com.fvlaenix.ocr.protobuf.OcrRectangles
import com.fvlaenix.ocr.protobuf.ocrRectangle
import com.fvlaenix.ocr.protobuf.ocrRectangles
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.io.File

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

object OCRUtils {

  fun ObjectMapper.patchMapper(): ObjectMapper {
    val module = SimpleModule()
    module.addSerializer(OcrRectangle::class.java, RectangleSerializer())
    module.addSerializer(OcrRectangles::class.java, RectanglesSerializer())
    this.registerModules(module)
    return this
  }

  fun ocr(url: String): OcrRectangles? {
    val imageBytes = DownloadUtils.downloadImage(url) ?: return null
    val imgBytes = ByteString.readFrom(imageBytes.inputStream())

    return ocr(Image.newBuilder().setContent(imgBytes).build())
  }

  fun ocr(image: File): OcrRectangles {
    val imageBytes: ByteArray = image.readBytes()
    val imgBytes: ByteString = ByteString.copyFrom(imageBytes)

    return ocr(Image.newBuilder().setContent(imgBytes).build())
  }

  fun ocr(image: Image): OcrRectangles {
    val requests: MutableList<AnnotateImageRequest> = ArrayList()
    val feat: Feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
    val annotateImageRequest: AnnotateImageRequest = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(image).build()
    requests.add(annotateImageRequest)

    val textBlocks = ImageAnnotatorClient.create().use { client ->
      val response: BatchAnnotateImagesResponse = client.batchAnnotateImages(requests)
      val responses: List<AnnotateImageResponse> = response.responsesList
      if (responses.size != 1) throw IllegalStateException()
      val annotation = responses[0].fullTextAnnotation
      val textBlocks = mutableListOf<OcrRectangle>()

      val blocks = annotation.pagesList.flatMap { it.blocksList }

      for (block in blocks) {
        val text = block.paragraphsList.joinToString(separator = " ") { paragraph ->
          paragraph.wordsList.joinToString(separator = "") { word ->
            word.symbolsList.joinToString(separator = "") { symbol -> symbol.text }
          }
        }.replace("\n", " ")
        val vertices = block.boundingBox.verticesList
        if (vertices.size != 4) throw IllegalStateException()
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
      textBlocks
    }
    return OcrRectangles.newBuilder().addAllRectangles(textBlocks).build()
  }
}