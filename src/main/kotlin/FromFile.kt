import com.fvlaenix.ocr.OCRUtils
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.writeText

val logger: Logger = Logger.getLogger(OCRUtils::class.java.name)

fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: <path to edits>")
  }
  val editsDirectory = Path(args[0])
  editsDirectory.resolve("untranlsated").toFile().listFiles()!!
    .sortedBy { it.nameWithoutExtension.toInt() }
    .map { file ->
      val json = OCRUtils.ocr(file)
      val jsonString = json.toJson().toPrettyString()
      logger.info("Write ${file.nameWithoutExtension}.json")
      editsDirectory.resolve("text").resolve("${file.nameWithoutExtension}.json").writeText(jsonString)
    }
}