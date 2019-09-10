package org.enterprisedlt.fabric.service.node.codec

import java.io.{OutputStream, Reader}
import java.nio.charset.StandardCharsets

import com.google.gson.GsonBuilder
import org.enterprisedlt.fabric.service.node.rest.ServerCodec


/**
  * @author Maxim Fedin
  */

class JsonServerCodec extends ServerCodec {
    private val GSON = new GsonBuilder().setPrettyPrinting().create()

    override def writeResult(x: Any, writer: OutputStream): Unit = {
        x match {
            case bytes: Array[Byte] =>
                writer.write(bytes)
                writer.flush()
            case _ =>
                writer.write(GSON.toJson(x).getBytes(StandardCharsets.UTF_8))
                writer.flush()
        }
    }

    override def readParameter[T](p: String, pType: Class[T]): T = GSON.fromJson(p, pType)

    override def readParameter[T](p: Reader, pType: Class[T]): T = GSON.fromJson(p, pType)
}
