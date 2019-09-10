package org.enterprisedlt.fabric.service.node.codec

import java.io.{Reader, Writer}

import com.google.gson.GsonBuilder


/**
  * @author Maxim Fedin
  */


trait Codec {
    def writeResult(x: Any, stream: Writer)

    def readParameter[T](p: String, pType: Class[T]): T

    def readParameter[T](p: Reader, pType: Class[T]): T
}

class JsonCodec extends Codec {
    private val GSON = new GsonBuilder().setPrettyPrinting().create()

    override def writeResult(x: Any, writer: Writer): Unit = writer.write(GSON.toJson(x))

    override def readParameter[T](p: String, pType: Class[T]): T = GSON.fromJson(p, pType)

    override def readParameter[T](p: Reader, pType: Class[T]): T = GSON.fromJson(p, pType)
}
