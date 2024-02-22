import com.fvlaenix.ocr.OCRModuleServer
import com.fvlaenix.ocr.pathToCredentials
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.io.path.isReadable

const val PORT = 50051
const val LOGGING_PATH = "/logging.properties"

class RunServer

fun main() {
  try {
    LogManager.getLogManager().readConfiguration(RunServer::class.java.getResourceAsStream(LOGGING_PATH))
  } catch (e: Exception) {
    throw IllegalStateException("Failed while trying to read logs", e)
  }
  val runServerLog = Logger.getLogger(RunServer::class.java.name)

  if (!pathToCredentials.isReadable()) {
    runServerLog.log(Level.SEVERE, "Path to credentials is incorrect and can't be read: $pathToCredentials")
    return
  }

  runServerLog.log(Level.INFO, "Launching server")
  val server = OCRModuleServer(PORT)
  server.start()
  runServerLog.log(Level.INFO, "Launched")
  server.blockUntilShutdown()
}