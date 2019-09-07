package org.enterprisedlt.fabric.service.node

import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class IdentityRestEndpoint(cryptoManager: CryptoManager) {
    private val logger = LoggerFactory.getLogger(this.getClass)


    //    case "/admin/create-user" =>
    //    val userName = request.getParameter("name")
    //    logger.info(s"Creating new user $userName ...")
    //    cryptoManager.createFabricUser(userName)
    //    response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
    //    response.getWriter.println("OK")
    //    response.setStatus(HttpServletResponse.SC_OK)
    //
    //    case "/admin/get-user-key" =>
    //    val userName = request.getParameter("name")
    //    val password = request.getParameter("password")
    //    logger.info(s"Obtaining user key for $userName ...")
    //    val key = cryptoManager.getFabricUserKeyStore(userName, password)
    //    response.setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType)
    //    key.store(response.getOutputStream, password.toCharArray)
    //    response.setStatus(HttpServletResponse.SC_OK)

}
