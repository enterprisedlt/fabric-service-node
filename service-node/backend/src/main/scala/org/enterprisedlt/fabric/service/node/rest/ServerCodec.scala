package org.enterprisedlt.fabric.service.node.rest

import java.io.{OutputStream, Reader}

/**
  * @author Maxim Fedin
  */
trait ServerCodec {
    def writeResult(x: Any, stream: OutputStream)

    def readParameter[T](p: String, pType: Class[T]): T

    def readParameter[T](p: Reader, pType: Class[T]): T
}
