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

  fun removeIntersectingBlocks(): ImageText {
    val newBlocks = this.blocks.filterNot { block ->
      this.blocks.any { it != block && block.rectangle.isInside(it.rectangle) }
    }

    return ImageText(newBlocks)
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

data class Rectangle(val points: List<Point>){
  fun toJson(): ArrayNode {
    val mapper = JsonMapper()
    val node = mapper.createArrayNode()
    points.forEach { point ->
      val pointNode = point.toJson()
      node.add(pointNode)
    }
    return node
  }

  fun isInside(other: Rectangle): Boolean {
    val topLeft = points[0]
    val bottomRight = points[2]
    val otherTopLeft = other.points[0]
    val otherBottomRight = other.points[2]

    return (otherTopLeft.X <= topLeft.X && otherTopLeft.Y <= topLeft.Y
        && otherBottomRight.X >= bottomRight.X && otherBottomRight.Y >= bottomRight.Y)
  }
}

data class Point(val X: Int, val Y: Int){
  fun toJson(): ObjectNode {
    val objectMapper = JsonMapper()
    val pointNode = objectMapper.createObjectNode()
    pointNode.put("x", X)
    pointNode.put("y", Y)
    return pointNode
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
        val rectangle = Rectangle(listOf(
          Point(vertices[0].x, vertices[0].y),
          Point(vertices[1].x, vertices[1].y),
          Point(vertices[2].x, vertices[2].y),
          Point(vertices[3].x, vertices[3].y)
        ))
        val textBlock = TextBlock(text, rectangle)
        textBlocks.add(textBlock)
      }
      textBlocks
    }
    return ImageText(textBlocks)
  }
}