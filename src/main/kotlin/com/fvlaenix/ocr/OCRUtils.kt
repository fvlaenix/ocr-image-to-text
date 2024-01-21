package com.fvlaenix.ocr

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.io.File

data class ImageText(val blocks: List<TextBlock>) {
  fun toJson(): ArrayNode {
    val mapper = JsonMapper()
    val rootNode = mapper.createArrayNode()
    for (block in blocks) {
      rootNode.add(block.toJson())
    }
    return rootNode
  }
}

data class TextBlock(val text: String, val rectangle: Rectangle){
  fun toJson(): ObjectNode {
    val mapper = JsonMapper()
    val node = mapper.createObjectNode()
    node.put("text", text)
    node.replace("rectangle", rectangle.toJson())
    return node
  }
}

data class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int){
  fun toJson(): ObjectNode {
    val mapper = JsonMapper()
    val node = mapper.createObjectNode()
    node.put("x", x)
    node.put("y", y)
    node.put("width", width)
    node.put("height", height)
    return node
  }
}

object OCRUtils {

  fun ocr(url: String): ImageText? {
    val imageBytes = DownloadUtils.downloadImage(url) ?: return null
    val imgBytes = ByteString.readFrom(imageBytes.inputStream())

    return ocr(Image.newBuilder().setContent(imgBytes).build())
  }

  fun ocr(image: File): ImageText {
    val imageBytes: ByteArray = image.readBytes()
    val imgBytes: ByteString = ByteString.copyFrom(imageBytes)

    return ocr(Image.newBuilder().setContent(imgBytes).build())
  }

  fun ocr(image: Image): ImageText {
    val requests: MutableList<AnnotateImageRequest> = ArrayList()
    val feat: Feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
    val annotateImageRequest: AnnotateImageRequest = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(image).build()
    requests.add(annotateImageRequest)

    val textBlocks = ImageAnnotatorClient.create().use { client ->
      val response: BatchAnnotateImagesResponse = client.batchAnnotateImages(requests)
      val responses: List<AnnotateImageResponse> = response.responsesList
      if (responses.size != 1) throw IllegalStateException()
      val annotation = responses[0].fullTextAnnotation
      val textBlocks = mutableListOf<TextBlock>()

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
        val rectangle = Rectangle(x, y, width, height)
        val textBlock = TextBlock(text, rectangle)
        textBlocks.add(textBlock)
      }
      textBlocks
    }
    return ImageText(textBlocks)
  }
}