package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.rest.{Get, ResponseContentType}

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {

    @Get("/services/identity/get-user-key")
    @ResponseContentType("application/octet-stream")
    def getFabricUserKeyStore(name: String, password: String): Either[String, Array[Byte]]

    @Get("/services/identity/create-user")
    def createFabricUser(name: String): Either[String, Unit]
}
