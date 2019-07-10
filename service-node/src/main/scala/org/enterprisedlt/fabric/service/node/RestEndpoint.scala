package org.enterprisedlt.fabric.service.node

import java.util.concurrent.locks.ReentrantLock

import org.enterprisedlt.fabric.service.node.flow.Bootstrap
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class RestEndpoint(
    config: ServiceConfig,
    cryptoManager: FabricCryptoManager,
    processManager: FabricProcessManager
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        request.getMethod match {
            case "GET" =>
                request.getPathInfo match {
                    case "/ping" =>
                        logger.info(s"Ping -> Pong")
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println("Pong")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/bootstrap" =>
                        logger.info(s"Bootstrapping organization ${config.organization.name}...")
                        try {
                            initNetworkManager(Bootstrap.bootstrapOrganization(config, cryptoManager, processManager))
                            response.setStatus(HttpServletResponse.SC_OK)
                        } catch {
                            case ex: Exception =>
                                logger.error("Bootstrap failed:", ex)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    // unknown GET path
                    case path =>
                        logger.info(s"Unknown path: $path")
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                }
            case "POST" =>
                request.getPathInfo match {

                    // unknown POST path
                    case path =>
                        logger.info(s"Unknown path: $path")
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                }
            case m =>
                logger.info(s"Unsupported method: $m")
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
        }
        logger.info("==================================================")
        baseRequest.setHandled(true)
    }


    private val networkManagerLock = new ReentrantLock()
    private var networkManager_ : Option[FabricNetworkManager] = None

    private def networkManager: Option[FabricNetworkManager] = {
        networkManagerLock.lock()
        try {
            networkManager_
        } finally {
            networkManagerLock.unlock()
        }
    }

    private def initNetworkManager(value: FabricNetworkManager): Unit = {
        networkManagerLock.lock()
        try {
            networkManager_ = Option(value)
        } finally {
            networkManagerLock.unlock()
        }
    }
}
