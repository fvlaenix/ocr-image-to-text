package com.fvlaenix.ocr

import io.grpc.Server
import io.grpc.ServerBuilder

class OCRModuleServer(port: Int) {
  private val server: Server = ServerBuilder.forPort(port).addService(OCRModuleService()).build()

  fun start() {
    server.start()
    Runtime.getRuntime().addShutdownHook(
      Thread {
        this@OCRModuleServer.stop()
      }
    )
  }

  private fun stop() {
    server.shutdown()
  }

  fun blockUntilShutdown() {
    server.awaitTermination()
  }
}