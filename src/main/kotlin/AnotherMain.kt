import com.fvlaenix.ocr.protobuf.OcrServiceGrpcKt
import com.fvlaenix.ocr.protobuf.ocrRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

class OCRClient(private val channel: ManagedChannel): Closeable {
  private val stub: OcrServiceGrpcKt.OcrServiceCoroutineStub = OcrServiceGrpcKt.OcrServiceCoroutineStub(channel)

  suspend fun send() {
    val request = ocrRequest { this.url = "" }
    val response = stub.ocr(request)
    println("Response: ${response.text}")
  }

  override fun close() {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
  }
}

suspend fun main() {
  val port = 50051

  val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
  val client = OCRClient(channel)

  client.send()
}