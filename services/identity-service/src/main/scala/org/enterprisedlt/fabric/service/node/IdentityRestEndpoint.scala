package org.enterprisedlt.fabric.service.node

import javax.servlet.http.HttpServletResponse
import org.apache.http.entity.ContentType
import org.enterprisedlt.fabric.service.node.rest.{Get, Post, RestEndpointContext}
import org.enterprisedlt.fabric.service.node.services.CryptoManager
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class IdentityRestEndpoint(cryptoManager: CryptoManager) {
    private val logger = LoggerFactory.getLogger(this.getClass)

    @Get("/services/identity/get-user-key")
    def getUserKey(): Either[String, Unit] = {
        RestEndpointContext.get.map { context =>
            val userName = context.request.getParameter("name")
            val password = context.request.getParameter("password")
            logger.info(s"Obtaining user key for $userName ...")
            val key = cryptoManager.getFabricUserKeyStore(userName, password)
            context.response.setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType)
            key.store(context.response.getOutputStream, password.toCharArray)
            context.response.setStatus(HttpServletResponse.SC_OK)
        }.toRight("Not context!")
    }

    @Post("/services/identity/create-user")
    def createUser(): Either[String, Unit] = {
        RestEndpointContext.get.map { context =>
            val userName = context.request.getParameter("name")
            logger.info(s"Creating new user $userName ...")
            cryptoManager.createFabricUser(userName)
            context.response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
            context.response.getWriter.println("OK")
            context.response.setStatus(HttpServletResponse.SC_OK)
        }
    }.toRight("Not context!")

}
