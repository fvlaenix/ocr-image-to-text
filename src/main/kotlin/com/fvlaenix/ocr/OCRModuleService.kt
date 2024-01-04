package com.fvlaenix.ocr

import com.fvlaenix.ocr.protobuf.OcrRequest
import com.fvlaenix.ocr.protobuf.OcrResponse
import com.fvlaenix.ocr.protobuf.OcrServiceGrpcKt
import com.fvlaenix.ocr.protobuf.ocrResponse
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString

class OCRModuleService : OcrServiceGrpcKt.OcrServiceCoroutineImplBase() {
  override suspend fun ocr(request: OcrRequest): OcrResponse {
    val url = request.url
    val requests: MutableList<AnnotateImageRequest> = ArrayList()

    val imageBytes = DownloadUtils.downloadImage(url) ?: return ocrResponse { this.error = "Failed to read image URL" }
    val imgBytes = ByteString.readFrom(imageBytes.inputStream())

    val img: Image = Image.newBuilder().setContent(imgBytes).build()
    val feat: Feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
    val annotateImageRequest: AnnotateImageRequest = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build()
    requests.add(annotateImageRequest)

    val answer = ImageAnnotatorClient.create().use { client ->
      val response: BatchAnnotateImagesResponse = client.batchAnnotateImages(requests)
      val responses: List<AnnotateImageResponse> = response.responsesList
      var result: String? = ""
      for (res in responses) {
        val annotator = res.fullTextAnnotation
        result = if (res.hasError()) {
          null
        } else {
          annotator.pagesList
            .flatMap { page -> page.blocksList }
            .joinToString(separator = "\n") { block ->
              block.paragraphsList.joinToString(separator = " ") { paragraph ->
                paragraph.wordsList.joinToString(separator = "") { word ->
                  word.symbolsList.joinToString(separator = "") { symbol -> symbol.text }
                }
              }.replace("\n", " ")
            }
        }
      }
      result
    }
    return ocrResponse {
      if (answer == null) error = "Can't get text from image"
      else text = answer
    }
  }
}