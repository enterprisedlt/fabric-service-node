package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.rest.{Get, ResponseContentType}

/**
  * @author Alexey Polubelov
  */
trait IdentityManager {

    @Get("/get-user-key")
    @ResponseContentType("application/octet-stream")
    def getFabricUserKeyStore(name: String, password: String): Either[String, Array[Byte]]

    @Get("/create-user")
    def createFabricUser(name: String): Either[String, Unit]
}
