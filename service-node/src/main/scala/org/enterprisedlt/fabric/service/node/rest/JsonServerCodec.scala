package org.enterprisedlt.fabric.service.node.rest

import java.io.{OutputStream, Reader}
import java.nio.charset.StandardCharsets

import com.google.gson.Gson


/**
  * @author Maxim Fedin
  */

class JsonServerCodec(codec: Gson) extends ServerCodec {

    override def writeResult(x: Any, writer: OutputStream): Unit = {
        x match {
            case bytes: Array[Byte] =>
                writer.write(bytes)
                writer.flush()
            case _ =>
                writer.write(codec.toJson(x).getBytes(StandardCharsets.UTF_8))
                writer.flush()
        }
    }

    override def readParameter[T](p: String, pType: Class[T]): T = codec.fromJson(p, pType)

    override def readParameter[T](p: Reader, pType: Class[T]): T = codec.fromJson(p, pType)
}
