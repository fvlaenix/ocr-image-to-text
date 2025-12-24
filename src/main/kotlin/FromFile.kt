import com.fasterxml.jackson.databind.ObjectMapper
import com.fvlaenix.ocr.OCRUtils
import com.fvlaenix.ocr.OCRUtils.patchMapper
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

val logger: Logger = Logger.getLogger(OCRUtils::class.java.name)

fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: <path to edits>")
    return
  }
  val editsDirectory = Path(args[0])
  editsDirectory.resolve("text").createDirectories()
  val objectMapper = ObjectMapper().patchMapper()
  val prettyPrinter = objectMapper.writerWithDefaultPrettyPrinter()
  val sourceDir = editsDirectory.resolve("subimage_untranslated").toFile()
  val files = sourceDir.listFiles()
  if (files == null) {
    println("No files found in ${sourceDir.path}")
    return
  }
  val jsons = files
    .mapNotNull { file ->
      val index = file.nameWithoutExtension.toIntOrNull()
      if (index == null) {
        logger.warning("Skipping unexpected file: ${file.name}")
        null
      } else {
        index to file
      }
    }
    .sortedBy { it.first }
    .map { (index, file) ->
      val rectangles = OCRUtils.ocrFileToText(file)
      val jsonString = prettyPrinter.writeValueAsString(rectangles)
      logger.info("Write ${file.nameWithoutExtension}.json")
      editsDirectory.resolve("text").resolve("${file.nameWithoutExtension}.json").writeText(jsonString)
      Pair(index, rectangles)
    }
    .sortedBy { it.first }
  val o = objectMapper.createObjectNode()
  jsons.forEach { (number, json) ->
    o.replace(number.toString(), objectMapper.valueToTree(json))
  }
  editsDirectory.resolve("text").resolve("output.json").writeText(o.toPrettyString())
}
