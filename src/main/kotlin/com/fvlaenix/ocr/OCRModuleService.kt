package com.fvlaenix.ocr

import com.fvlaenix.alive.protobuf.IsAliveRequest
import com.fvlaenix.alive.protobuf.IsAliveResponse
import com.fvlaenix.alive.protobuf.isAliveResponse
import com.fvlaenix.ocr.protobuf.*
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.extension

class OCRModuleService : OcrServiceGrpcKt.OcrServiceCoroutineImplBase() {

  override suspend fun ocrImage(request: OcrImageRequest): OcrTextResponse {
    return getTextResponse(request.image.toByteArray(), request.name)
  }

  override suspend fun ocrUrlImage(request: OcrUrlImageRequest): OcrTextResponse {
    val name = URL(request.url).file
    val imageBytes = DownloadUtils.downloadImage(request.url) ?: return ocrTextResponse { this.error = "Can't download image from url ${request.url}" }
    return getTextResponse(imageBytes, name)
  }

  override suspend fun ocrImageToImage(request: OcrImageRequest): OcrImageResponse {
    return getImageResponse(request.image.toByteArray(), request.name)
  }

  override suspend fun ocrUrlToImage(request: OcrUrlImageRequest): OcrImageResponse {
    val name = URL(request.url).file
    val imageBytes = DownloadUtils.downloadImage(request.url) ?: return ocrImageResponse { this.error = "Can't download image from url ${request.url}" }
    return getImageResponse(imageBytes, name)
  }

  override suspend fun isAlive(request: IsAliveRequest): IsAliveResponse {
    return isAliveResponse {  }
  }

  private fun getImageResponse(image: ByteArray, name: String): OcrImageResponse {
    val ocrTextResponse = getTextResponse(image, name)
    if (ocrTextResponse.rectangles == null) {
      return ocrImageResponse {
        this.error = ocrTextResponse.error
      }
    } else {
      val ocrText = ocrTextResponse.rectangles!!
      val originalImage = ImageIO.read(image.inputStream())

      val graphics = originalImage.createGraphics()
      for (rectangle in ocrText.rectanglesList) {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (x in (rectangle.x..(rectangle.x + rectangle.width))) {
          for (y in (rectangle.y..(rectangle.y + rectangle.height))) {
            if (x >= 0 && y >= 0 && x < originalImage.width && y < originalImage.height) {
              count++
              val rgb = originalImage.getRGB(x.toInt(), y.toInt())
              val color = Color(rgb)
              red += color.red
              green += color.green
              blue += color.blue
            }
          }
        }
        val backgroundColor = Color((red / count).toInt(), (green / count).toInt(), (blue / count).toInt(), 170)
        graphics.color = backgroundColor
        graphics.fillRect(rectangle.x.toInt(), rectangle.y.toInt(), rectangle.width.toInt(), rectangle.height.toInt())
      }
      graphics.dispose()

      val arrayBytesOutput = ByteArrayOutputStream()
      ImageIO.write(originalImage, Path(name).extension, arrayBytesOutput)
      return ocrImageResponse {
        this.result = ocrImageSuccessfulResponse {
          this.text = ocrTextResponse
          this.image = ByteString.readFrom(arrayBytesOutput.toByteArray().inputStream())
          this.name = name
        }
      }
    }
  }

  private fun getTextResponse(image: ByteArray, name: String): OcrTextResponse {
    val imageBytes = ByteString.readFrom(image.inputStream())

    val img: Image = Image.newBuilder().setContent(imageBytes).build()
    val response = OCRUtils.ocr(img)
    return ocrTextResponse { this.rectangles = response }
  }
}