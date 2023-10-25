import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import java.io.FileInputStream

fun main(args: Array<String>) {
  val filePath = "example.png"
  val requests: MutableList<AnnotateImageRequest> = ArrayList()

  val imgBytes = ByteString.readFrom(FileInputStream(filePath))

  val img: Image = Image.newBuilder().setContent(imgBytes).build()
  val feat: Feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
  val request: AnnotateImageRequest = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build()
  requests.add(request)

  // Initialize client that will be used to send requests. This client only needs to be created
  // once, and can be reused for multiple requests. After completing all of your requests, call
  // the "close" method on the client to safely clean up any remaining background resources.
  ImageAnnotatorClient.create().use { client ->
    val response: BatchAnnotateImagesResponse = client.batchAnnotateImages(requests)
    val responses: List<AnnotateImageResponse> = response.responsesList
    for (res in responses) {
      if (res.hasError()) {
        System.out.format("Error: %s%n", res.error.getMessage())
        return
      }

      // For full list of available annotations, see http://g.co/cloud/vision/docs
      for (annotation in res.textAnnotationsList) {
        System.out.format("Text: %s%n", annotation.getDescription())
        System.out.format("Position : %s%n", annotation.boundingPoly)
      }
    }
  }
}