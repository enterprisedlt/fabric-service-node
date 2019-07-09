package org.enterprisedlt.fabric.service.node

import java.io._
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.google.gson.{Gson, GsonBuilder}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Request, Server}
import org.enterprisedlt.fabric.service.contract.model.{CCVersion, Organisation}
import org.hyperledger.fabric.protos.common.Common.Envelope
import org.hyperledger.fabric.protos.common.Configtx
import org.slf4j.{Logger, LoggerFactory}


/**
  * @author Alexey Polubelov
  * @author pandelie
  */
object ServiceNode extends App {

    val ENVS = System.getenv()
    val LOG_LEVEL = Option(ENVS.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    val MAINTENANCE_PORT = Option(ENVS.get("MAINTENANCE_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing MAINTENANCE_PORT!"))
    val MAINTENANCE_EXTERNAL_HOST = Option(ENVS.get("MAINTENANCE_EXTERNAL_HOST")).getOrElse(throw new Exception("Mandatory environment variable missing MAINTENANCE_EXTERNAL_HOST!"))
    val networkConfigFile = Option(ENVS.get("NETWORK_CONFIG")).getOrElse(throw new Exception("Mandatory environment variable missing NETWORK_CONFIG!"))
    val CHAINCODE_NAME = Option(ENVS.get("CHAINCODE_NAME")).filter(_.trim.nonEmpty).getOrElse("common")
    val DOCKER_HOST_IP = Option(ENVS.get("DOCKER_HOST_IP")).filter(_.trim.nonEmpty).getOrElse("unix:///host/var/run/docker.sock")
    //
    setupLogging(LOG_LEVEL)
    private val logger = LoggerFactory.getLogger(getClass)
    //
    logger.info("Starting...")
    val networkConfig = loadNetworkConfig(networkConfigFile)
    val server: Server = createServer(
        new FabricNetworkManager(networkConfig),
        new FabricProcessManager(DOCKER_HOST_IP)
    )
    setupShutdownHook()
    server.start()
    logger.info("Started.")
    server.join()


    //=========================================================================
    // Utilities
    //=========================================================================
    def codec: Gson = (new GsonBuilder).create()

    //=========================================================================
    def loadNetworkConfig(networkConfigFile: String): NetworkConfig =
        codec.fromJson(new FileReader(networkConfigFile), classOf[NetworkConfig])


    //=========================================================================
    private def createServer(
        network: FabricNetworkManager,
        processManager: FabricProcessManager
    ): Server = {
        val server = new Server(MAINTENANCE_PORT)
        server.setHandler(new EndpointHandler(network, processManager))
        server
    }

    //=========================================================================
    private class EndpointHandler(
        network: FabricNetworkManager,
        processManager: FabricProcessManager
    ) extends AbstractHandler {
        override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
            request.getMethod match {
                case "GET" =>
                    request.getPathInfo match {
                        case "/network-config" =>
                            response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                            val out = response.getOutputStream
                            out.println(codec.toJson(networkConfig))
                            out.flush()
                            response.setStatus(HttpServletResponse.SC_OK)

                        case "/create-invite" =>
                            response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                            val out = response.getOutputStream
                            val invite = Invite(s"$MAINTENANCE_EXTERNAL_HOST:$MAINTENANCE_PORT")
                            out.println(codec.toJson(invite))
                            out.flush()
                            response.setStatus(HttpServletResponse.SC_OK)

                        // unknown path
                        case path =>
                            logger.info(s"Unknown path: $path")
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            logger.info("==================================================")
                    }
                case "POST" =>
                    request.getPathInfo match {
                        case "/create-channel" =>
                            try {
                                logger.info("Request for channel creation.")
                                val channelReq = codec.fromJson(request.getReader, classOf[CreateChannelRequest])
                                val channelTx = Envelope.parseFrom(new FileInputStream(s"/opt/profile/artifacts/${channelReq.name}.tx"))
                                network.createChannel(channelReq.name, channelTx)
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.setStatus(HttpServletResponse.SC_OK)
                            } catch {
                                case ex: Exception =>
                                    logger.error("Failed to process CreateChannel request:", ex)
                                    ex.printStackTrace(response.getWriter)
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/define-channel" =>
                            try {
                                logger.info("Request for channel creation.")
                                val channelReq = codec.fromJson(request.getReader, classOf[CreateChannelRequest])
                                network.defineChannel(channelReq.name)
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.setStatus(HttpServletResponse.SC_OK)
                            } catch {
                                case ex: Exception =>
                                    logger.error("Failed to process CreateChannel request:", ex)
                                    ex.printStackTrace(response.getWriter)
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/add-peer" =>
                            logger.info("Request for peer adding.")
                            val addPeerReq = codec.fromJson(request.getReader, classOf[AddPeerRequest])
                            network.addPeerToChannel(addPeerReq.channelName, addPeerReq.peer) match {
                                case Right(_) =>
                                    network.fetchLatestChannelBlock(addPeerReq.channelName) match {
                                        case Right(block) =>
                                            val lastBlkNum = block.getHeader.getNumber
                                            logger.info(s"Got last block number $lastBlkNum")
                                            val writer = response.getWriter
                                            writer.println(lastBlkNum)
                                            response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                            response.setStatus(HttpServletResponse.SC_OK)
                                        case Left(error) =>
                                            logger.info(s"Failed to fetch latest block: $error")
                                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                                    }
                                case Left(err) =>
                                    logger.error("Failed to process AddPeer request:", err)
                                    val writer = response.getWriter
                                    writer.println(err)
                                    writer.close()
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/add-anchors-to-channel" =>
                            logger.info("Request for adding anchors to channel.")
                            val addAnchorToChReq = codec.fromJson(request.getReader, classOf[AddAnchorsToChRequest])
                            network.addAnchorsToChannel(addAnchorToChReq.channelName, addAnchorToChReq.peerName) match {
                                case Right(_) =>
                                    response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                    response.setStatus(HttpServletResponse.SC_OK)

                                case Left(err) =>
                                    logger.error("Failed to process Add Anchors to  request:", err)
                                    val writer = response.getWriter
                                    writer.println(err)
                                    writer.close()
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/install-chaincode" =>
                            logger.info("Request for chain code installing")
                            val installCCReq = codec.fromJson(request.getReader, classOf[InstallCCRequest])
                            val gunZipFile = Util.generateTarGzInputStream(new File(s"/opt/chaincode")) ///${installCCReq.ccName}
                            network.installChainCode(installCCReq.channelName, installCCReq.chainCodeName, installCCReq.chainCodeVersion, gunZipFile) match {
                                case Right(_) =>
                                    response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                    response.setStatus(HttpServletResponse.SC_OK)
                                case Left(err) =>
                                    logger.error("Failed to process CC installation:", err)
                                    val writer = response.getWriter
                                    writer.println(err)
                                    writer.close()
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/instantiate-chaincode" =>
                            logger.info("Request for chaincode instantiation")
                            val instantiateCCReq = codec.fromJson(request.getReader, classOf[InitCCRequest])
                            network.instantiateChainCode(instantiateCCReq.channelName, instantiateCCReq.chainCodeName, instantiateCCReq.version, arguments = instantiateCCReq.arguments) match {
                                case Right(_) =>
                                    val writer = response.getWriter
                                    writer.println(s"CC version set ${instantiateCCReq.arguments(3)} and network version ${instantiateCCReq.arguments(4)}")
                                    response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                    response.setStatus(HttpServletResponse.SC_OK)

                                case Left(err) =>
                                    logger.error("Failed to process cc instantiate:", err)
                                    val writer = response.getWriter
                                    writer.println(err)
                                    writer.close()
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/join-network" =>
                            logger.info("Request for join network")
                            val invite = codec.fromJson(request.getReader, classOf[Invite])
                            val genesis = Util.loadConfigurationBlock(new FileInputStream("/opt/profile/artifacts/config.genesis.block"))
                            val genesisConfig = Util.extractConfig(genesis)
                            val joinRequest = JoinRequest(
                                genesisConfig = Base64.getEncoder.encodeToString(genesisConfig.toByteArray),
                                mspId = network.config.orgID
                            )

                            val joinResponse = executeRequest(s"http://${invite.address}/join", joinRequest, classOf[JoinResponse])
                            val out = new FileOutputStream("/opt/profile/artifacts/genesis.block")
                            try {
                                out.write(Base64.getDecoder.decode(joinResponse.genesis))
                                out.flush()
                            } finally {
                                out.close()
                            }
                            val writer = response.getWriter
                            writer.println(joinResponse.version)
                            response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                            response.setStatus(HttpServletResponse.SC_OK)
                            logger.info("==================================================")

                        case "/join" =>
                            logger.info("Got Join Request")
                            val joinRequest = codec.fromJson(request.getReader, classOf[JoinRequest])
                            logger.info("Joining to network...")
                            val newOrgConfig = Configtx.Config.parseFrom(Base64.getDecoder.decode(joinRequest.genesisConfig))
                            val newOrgId = joinRequest.mspId
                            network.joinToNetwork(newOrgConfig)
                            logger.info("Joining to channel common...")
                            network.joinToChannel("common", newOrgConfig) match {
                                case Right(_) =>
                                    network.queryChainCode("common", "common", "getCCVersion") match {
                                        case Right(value) =>
                                            logger.info("Got latest common CC version from blkchain...")
                                            val ccLatestVer = codec.fromJson(value.head, classOf[CCVersion])
                                            val incrementedNetworkVersion = (ccLatestVer.networkVer.toInt + 1).toString
                                            val incrementedCCVerStringifed = s"${ccLatestVer.ccVer}.$incrementedNetworkVersion"
                                            logger.info(s"Installing new common chain code $incrementedCCVerStringifed version")
                                            val installCCReq = InstallCCRequest("common", "common", incrementedCCVerStringifed)
                                            val gunZipFile = Util.generateTarGzInputStream(new File(s"/opt/chaincode/java/${installCCReq.chainCodeName}"))
                                            network.installChainCode(installCCReq.channelName, installCCReq.chainCodeName, installCCReq.chainCodeVersion, gunZipFile) match {
                                                case Right(_) =>
                                                    logger.info("common CC installed successfully")
                                                    network.queryChainCode("common", "common", "listOrganisations") match {
                                                        case Right(queryResult) =>
                                                            val orgList: Array[String] = codec.fromJson(queryResult.head, classOf[Array[Organisation]])
                                                              .map(a => a.code)
                                                            orgList :+ newOrgId
                                                            logger.info(s"orgs list is : ${orgList.toList}")
                                                            val policyForCCUpgrade = Util.policyAnyOf(orgList)
                                                            logger.info(s"Upgrading common chaincode to ver $incrementedCCVerStringifed ...")
                                                            network.upgradeChainCode("common", "common", incrementedCCVerStringifed,
                                                                endorsementPolicy = Option(policyForCCUpgrade),
                                                                arguments = Array(newOrgId, newOrgId, ccLatestVer.ccVer, incrementedNetworkVersion)
                                                            )
                                                            logger.info(s"Preparing config block and latest CC ver. for sending to joining $newOrgId org")
                                                            response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                                            val latestBlock = network.fetchLatestSystemBlock
                                                            val joinResponse = JoinResponse(
                                                                genesis = Base64.getEncoder.encodeToString(latestBlock.toByteArray),
                                                                version = incrementedCCVerStringifed
                                                            )
                                                            val out = response.getOutputStream
                                                            out.print(codec.toJson(joinResponse))
                                                            out.flush()
                                                            response.setStatus(HttpServletResponse.SC_OK)
                                                        case Left(err) =>
                                                            logger.error("Invoke failed while getting org list:", err)
                                                            val writer = response.getWriter
                                                            writer.println(err)
                                                            writer.close()
                                                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                                                    }
                                                case Left(err) =>
                                                    logger.error("Failed to process CC installation:", err)
                                                    val writer = response.getWriter
                                                    writer.println(err)
                                                    writer.close()
                                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                                            }
                                        case Left(err) =>
                                            logger.error("Failed while common CC instantiation:", err)
                                            val writer = response.getWriter
                                            writer.println(err)
                                            writer.close()
                                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                                    }
                                case Left(err) =>
                                    logger.error("Failed to join network:", err)
                                    val writer = response.getWriter
                                    writer.println(err)
                                    writer.close()
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        case "/query" =>
                            logger.info("Request for CC query")
                            val queryCCReq = codec.fromJson(request.getReader, classOf[QuertyRequest])
                            network.queryChainCode(queryCCReq.channelName, queryCCReq.chainCodeName, queryCCReq.functionName, Option(queryCCReq.arguments).getOrElse(Array.empty): _*) match {
                                case Right(answer) =>
                                    logger.info(s"$answer")
                                    val writer = response.getWriter
                                    answer.foreach(writer.println)
                                    writer.close()
                                    response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                    response.setStatus(HttpServletResponse.SC_OK)
                                case Left(err) => logger.error("Failed to process query:", err)
                                    val writer = response.getWriter
                                    writer.println(err)
                                    writer.close()
                                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            }
                            logger.info("==================================================")

                        // unknown path
                        case path =>
                            logger.info(s"Unknown path: $path")
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                            logger.info("==================================================")
                    }
                case m =>
                    logger.info(s"Unsupported method: $m")
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                    logger.info("==================================================")
            }
            baseRequest.setHandled(true)
        }
    }


    private def executeRequest[T](url: String, request: AnyRef, responseClass: Class[T]): T = {
        logger.info(s"Executing request to $url ...")
        val post = new HttpPost(url)
        val body = codec.toJson(request).getBytes(StandardCharsets.UTF_8)
        val entity = new ByteArrayEntity(body, ContentType.APPLICATION_JSON)
        post.setEntity(entity)
        val response = HttpClients.createDefault().execute(post)
        try {
            logger.info(s"Got status from remote: ${response.getStatusLine.toString}")
            val entity = response.getEntity
            val result = codec.fromJson(new InputStreamReader(entity.getContent), responseClass)
            EntityUtils.consume(entity) // ensure it is fully consumed
            result
        } finally {
            response.close()
        }
    }

    //=========================================================================
    private def setupLogging(logLevel: String): Unit = {
        LoggerFactory
          .getLogger(Logger.ROOT_LOGGER_NAME)
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.INFO)

        LoggerFactory
          .getLogger("io.netty")
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.OFF)

        LoggerFactory
          .getLogger("io.grpc")
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.OFF)

        LoggerFactory
          .getLogger("org.hyperledger.fabric.sdk.PeerEventServiceClient")
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.OFF)

        LoggerFactory
          .getLogger("com.github.dockerjava.api")
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.INFO)

    }

    //=========================================================================
    private def setupShutdownHook(): Unit = {
        Runtime.getRuntime.addShutdownHook(new Thread("shutdown-hook") {
            override def run(): Unit = {
                logger.info("Shutting down...")
                server.stop()
                logger.info("Shutdown complete.")
            }
        })
    }
}
