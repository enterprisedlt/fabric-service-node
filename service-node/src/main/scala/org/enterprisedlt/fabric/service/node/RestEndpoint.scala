package org.enterprisedlt.fabric.service.node

import java.util.concurrent.locks.ReentrantLock

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.model.Message
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.flow.{Bootstrap, Join}
import org.enterprisedlt.fabric.service.node.model.{Invite, JoinRequest, delMessageRequest, getMessageRequest}
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class RestEndpoint(
  bindPort: Int,
  externalAddress: Option[ExternalAddress],
  config: ServiceConfig,
  cryptoManager: CryptoManager,
  processManager: FabricProcessManager,
  hostsManager: HostsManager
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        request.getMethod match {
            case "GET" =>
                request.getPathInfo match {
                    case "/ping" =>
                        logger.info(s"Ping -> Pong")
                        response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                        val user = Util.getUserCertificate(request)
                          .flatMap(Util.getCNFromCertificate)
                          .getOrElse("Anon")

                        response.getWriter.println(s"Hello $user")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/list-organizations" =>
                        logger.info(s"ListOrganizations ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/collections" =>
                        logger.info(s"Collections ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listCollections")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/bootstrap" =>
                        logger.info(s"Bootstrapping organization ${config.organization.name}...")
                        val start = System.currentTimeMillis()
                        try {
                            initNetworkManager(Bootstrap.bootstrapOrganization(config, cryptoManager, processManager, hostsManager, externalAddress))
                            val end = System.currentTimeMillis() - start
                            logger.info(s"Bootstrap done ($end ms)")
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
                        val address = externalAddress
                          .map(ea => s"${ea.host}:${ea.port}")
                          .getOrElse(s"service.${config.organization.name}.${config.organization.domain}:$bindPort")
                        //TODO: password should be taken from request
                        val password = "join me"
                        val key = cryptoManager.createServiceUserKeyStore(s"join-${System.currentTimeMillis()}", password)
                        val invite = Invite(
                            address,
                            Util.keyStoreToBase64(key, password)
                        )
                        out.println(Util.codec.toJson(invite))
                        out.flush()
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/create-user" =>
                        val userName = request.getParameter("name")
                        logger.info(s"Creating new user $userName ...")
                        cryptoManager.createFabricUser(userName)
                        response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                        response.getWriter.println("OK")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/get-user-key" =>
                        val userName = request.getParameter("name")
                        val password = request.getParameter("password")
                        logger.info(s"Obtaining user key for $userName ...")
                        val key = cryptoManager.getFabricUserKeyStore(userName, password)
                        response.setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType)
                        key.store(response.getOutputStream, password.toCharArray)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/list-messages" =>
                        logger.info(s"Querying messages for ${config.organization.name}...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listMessages")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
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
                        val start = System.currentTimeMillis()
                        val invite = Util.codec.fromJson(request.getReader, classOf[Invite])
                        initNetworkManager(Join.join(config, cryptoManager, processManager, invite, externalAddress, hostsManager))
                        val end = System.currentTimeMillis() - start
                        logger.info(s"Joined ($end ms)")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/join-network" =>
                        networkManager
                          .toRight("Network is not initialized yet")
                          .flatMap { network =>
                              val joinRequest = Util.codec.fromJson(request.getReader, classOf[JoinRequest])
                              Join.joinOrgToNetwork(
                                  config, cryptoManager, processManager,
                                  network, joinRequest, hostsManager
                              )
                          } match {
                            case Right(joinResponse) =>
                                val out = response.getOutputStream
                                out.print(Util.codec.toJson(joinResponse))
                                out.flush()
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/put-message" =>
                        val putMessageRequest = Util.codec.fromJson(request.getReader, classOf[Message])
                        logger.info("Requesting for getting message ...")
                        networkManager
                          .toRight("Network is not initialized yet")
                          .flatMap { network =>
                              network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "putMessage", Util.codec.toJson(putMessageRequest))
                          } match {
                            case Right(answer) =>
                                answer.get()
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(answer)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(err) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(err)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/get-message" =>
                        val getMessageRequest = Util.codec.fromJson(request.getReader, classOf[getMessageRequest])
                        logger.info("Requesting for getting message ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getMessage", getMessageRequest.messageKey, getMessageRequest.sender)
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/del-message" =>
                        val delMessageRequest = Util.codec.fromJson(request.getReader, classOf[delMessageRequest])
                        logger.info("Requesting for deleting message ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "delMessage", delMessageRequest.messageKey, delMessageRequest.sender)
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

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
