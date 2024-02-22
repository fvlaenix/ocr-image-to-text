package com.fvlaenix.ocr

import com.fvlaenix.alive.protobuf.IsAliveRequest
import com.fvlaenix.alive.protobuf.IsAliveResponse
import com.fvlaenix.alive.protobuf.isAliveResponse
import com.fvlaenix.ocr.protobuf.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

private val LOG = Logger.getLogger(OCRModuleService::class.java.name)

class OCRModuleService : OcrServiceGrpcKt.OcrServiceCoroutineImplBase() {

  private val atomicId = AtomicInteger(0)

  private fun logEnter(method: String, id: Int, info: String) {
    LOG.info("Called $method with id $id: $info")
  }

  private fun logEnd(method: String, id: Int) {
    LOG.info("Completed $method with id $id")
  }

  private fun <T> withLog(method: String, info: String, body: () -> T): T {
    val id = atomicId.incrementAndGet()
    logEnter(method, id, info)
    val result = try {
      body()
    } catch (e: Exception) {
      LOG.log(Level.SEVERE, "Failed to complete $method with id $id", e)
      throw e
    } finally {
      logEnd(method, id)
    }
    return result
  }

  override suspend fun ocrImage(request: OcrImageRequest): OcrTextResponse {
    return withLog("ocrImage", "filename: ${request.image.fileName}") {
      OCRUtils.ocrBytesArrayToText(request.image.content.toByteArray())
    }
  }

  override suspend fun ocrUrlImage(request: OcrUrlImageRequest): OcrTextResponse {
    return withLog("ocrUrlImage", "url: ${request.url}") {
      OCRUtils.ocrUrlToText(request.url)
    }
  }

  override suspend fun ocrImageToImage(request: OcrImageRequest): OcrImageResponse {
    return withLog("ocrImageToImage", "filename: ${request.image.fileName}") {
      OCRUtils.ocrBytesArrayToImage(request.image.content.toByteArray(), request.image.fileName)
    }
  }

  override suspend fun ocrUrlToImage(request: OcrUrlImageRequest): OcrImageResponse {
    return withLog("ocrUrlToImage", "url: ${request.url}") {
      OCRUtils.ocrUrlToImage(request.url)
    }
  }

  override suspend fun isAlive(request: IsAliveRequest): IsAliveResponse {
    return withLog("isAlive", "") {
      isAliveResponse {  }
    }
  }
}