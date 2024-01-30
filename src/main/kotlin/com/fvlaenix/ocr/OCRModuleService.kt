package com.fvlaenix.ocr

import com.fvlaenix.alive.protobuf.IsAliveRequest
import com.fvlaenix.alive.protobuf.IsAliveResponse
import com.fvlaenix.alive.protobuf.isAliveResponse
import com.fvlaenix.ocr.protobuf.*
import kotlin.io.path.Path
import kotlin.io.path.extension

class OCRModuleService : OcrServiceGrpcKt.OcrServiceCoroutineImplBase() {

  override suspend fun ocrImage(request: OcrImageRequest): OcrTextResponse {
    return OCRUtils.ocrBytesArrayToText(request.image.toByteArray())
  }

  override suspend fun ocrUrlImage(request: OcrUrlImageRequest): OcrTextResponse {
    return OCRUtils.ocrUrlToText(request.url)
  }

  override suspend fun ocrImageToImage(request: OcrImageRequest): OcrImageResponse {
    return OCRUtils.ocrBytesArrayToImage(request.image.toByteArray(), Path(request.name).extension)
  }

  override suspend fun ocrUrlToImage(request: OcrUrlImageRequest): OcrImageResponse {
    return OCRUtils.ocrUrlToImage(request.url)
  }

  override suspend fun isAlive(request: IsAliveRequest): IsAliveResponse {
    return isAliveResponse {  }
  }
}