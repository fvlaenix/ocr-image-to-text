import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fvlaenix.ocr.OCRUtils
import com.fvlaenix.ocr.OCRUtils.patchMapper
import com.google.gson.JsonArray
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

val logger: Logger = Logger.getLogger(OCRUtils::class.java.name)

fun main(args: Array<String>) {
  if (args.size != 1) {
    println("Usage: <path to edits>")
  }
  val editsDirectory = Path(args[0])
  editsDirectory.resolve("text").createDirectories()
  val objectMapper = ObjectMapper().patchMapper()
  val prettyPrinter = objectMapper.writerWithDefaultPrettyPrinter()
  val jsons = editsDirectory.resolve("untranslated").toFile().listFiles()!!
    .sortedBy { it.nameWithoutExtension.toInt() }
    .map { file ->
      val rectangles = OCRUtils.ocr(file)
      val jsonString = prettyPrinter.writeValueAsString(rectangles)
      logger.info("Write ${file.nameWithoutExtension}.json")
      editsDirectory.resolve("text").resolve("${file.nameWithoutExtension}.json").writeText(jsonString)
      Pair(file.nameWithoutExtension.toInt(), rectangles)
    }
    .sortedBy { it.first }
  val o = objectMapper.createObjectNode()
  jsons.forEach { (number, json) ->
    o.replace(number.toString(), objectMapper.valueToTree(json))
  }
  editsDirectory.resolve("text").resolve("output.json").writeText(o.toPrettyString())
}