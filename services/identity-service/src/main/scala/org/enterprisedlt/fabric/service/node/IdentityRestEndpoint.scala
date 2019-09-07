package org.enterprisedlt.fabric.service.node

import javax.servlet.http.HttpServletResponse
import org.apache.http.entity.ContentType
import org.enterprisedlt.fabric.service.node.rest.{Get, Post, RestEndpointContext}
import org.enterprisedlt.fabric.service.node.services.CryptoManager
import org.slf4j.LoggerFactory

/**
  * @author Maxim Fedin
  */
class IdentityRestEndpoint(cryptoManager: CryptoManager) {
    private val logger = LoggerFactory.getLogger(this.getClass)

    @Get("/services/identity/get-user-key")
    def getUserKey(userName: String, password: String): Either[String, Unit] = {
        RestEndpointContext.get.map { context =>
            logger.info(s"Obtaining user key for $userName ...")
            val key = cryptoManager.getFabricUserKeyStore(userName, password)
            context.response.setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType)
            key.store(context.response.getOutputStream, password.toCharArray)
            context.response.setStatus(HttpServletResponse.SC_OK)
        }.toRight("Not context!")
    }

    @Get("/services/identity/create-user")
    def createUser(userName: String): Either[String, String] = {
        logger.info(s"Creating new user $userName ...")
        cryptoManager.createFabricUser(userName)
        logger.info(s"User $userName is created.")
        Right("OK")
    }
}
