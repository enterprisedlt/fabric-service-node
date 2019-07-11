package org.enterprisedlt.fabric.service.node

import java.io.{FileInputStream, FileOutputStream}
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.{Bootstrap, Join}
import org.enterprisedlt.fabric.service.node.model.{Invite, JoinRequest, JoinResponse}
import org.enterprisedlt.fabric.service.node.proto.FabricBlock
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class RestEndpoint(
    externalURL: String,
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

                    case "/create-invite" =>
                        logger.info(s"Creating invite ${config.organization.name}...")
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        val out = response.getOutputStream
                        val invite = Invite(externalURL)
                        out.println(Util.codec.toJson(invite))
                        out.flush()
                        response.setStatus(HttpServletResponse.SC_OK)

                    // unknown GET path
                    case path =>
                        logger.info(s"Unknown path: $path")
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                }
            case "POST" =>
                request.getPathInfo match {
                    case "/request-join" =>
                        logger.info("Requesting to joining network ...")
                        val invite = Util.codec.fromJson(request.getReader, classOf[Invite])
                        initNetworkManager(Join.join(config, cryptoManager, processManager, invite))
//                        val writer = response.getWriter
//                        writer.println(joinResponse.version)
//                        response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/join-network" =>
                        val joinRequest = Util.codec.fromJson(request.getReader, classOf[JoinRequest])
                        Join.joinOrgToNetwork(
                            config, cryptoManager, processManager,
                            networkManager.getOrElse(throw new IllegalStateException("Network is not initialized yet")),
                            joinRequest
                        ) match {
                            case Right(joinResponse) =>
                                val out = response.getOutputStream
                                out.print(Util.codec.toJson(joinResponse))
                                out.flush()
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    // unknown POST path
                    case path =>
                        logger.error(s"Unknown path: $path")
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                }
            case m =>
                logger.error(s"Unsupported method: $m")
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
