import com.fvlaenix.ocr.OCRModuleServer

fun main() {
  val port = 50051
  val server = OCRModuleServer(port)
  server.start()
  println("Started")
  server.blockUntilShutdown()
}