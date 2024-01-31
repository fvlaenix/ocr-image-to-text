import com.fvlaenix.ocr.OCRUtils
import com.fvlaenix.ocr.protobuf.OcrRectangle
import com.fvlaenix.ocr.protobuf.OcrRectangleKt
import com.fvlaenix.ocr.protobuf.ocrRectangle
import com.google.protobuf.TextFormat
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: <path to image>")
    return
  }
  val stringPath = args[0]
  val pathToImage = Path(stringPath)
  val response = OCRUtils.ocrFileToImage(pathToImage.toFile(), pathToImage.extension)
  if (response.hasError()) {
    println("Error: ${response.error}")
  }
  if (response.hasResult()) {
    val result = response.result!!
    val image = result.image
    val textResult = result.text
    val rectangles = textResult.rectangles
    Path("output-image.${pathToImage.extension}").writeBytes(image.toByteArray())
    Path("output-text.txt").writeText(rectangles.rectanglesList.joinToString(separator = "\n") { it.text })
  }
}