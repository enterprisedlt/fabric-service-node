package org.enterprisedlt.fabric.service.node

import java.io.FileReader

import org.eclipse.jetty.server.Server
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.cryptography.FileBasedCryptoManager
import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  * @author pandelie
  */
object ServiceNode extends App {
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val ServiceBindPort = Option(Environment.get("SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_BIND_PORT!"))
    private val ServiceExternalPort = Option(Environment.get("SERVICE_EXTERNAL_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_EXTERNAL_PORT!"))
    private val ServiceExternalIP = Option(Environment.get("SERVICE_EXTERNAL_IP")).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_EXTERNAL_IP!"))
    private val ProfilePath = Option(Environment.get("PROFILE_PATH")).getOrElse(throw new Exception("Mandatory environment variable missing PROFILE_PATH!"))
    private val DockerSocket = Option(Environment.get("DOCKER_SOCKET")).getOrElse(throw new Exception("Mandatory environment variable missing DOCKER_SOCKET!"))
    private val InitialName = Option(Environment.get("INITIAL_NAME")).getOrElse(throw new Exception("Mandatory environment variable missing INITIAL_NAME!"))

    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(getClass)

    logger.info("Starting...")
    logger.info(s"\tSERVICE_BIND_PORT\t:\t$ServiceBindPort")
    logger.info(s"\tSERVICE_EXTERNAL_PORT\t:\t$ServiceExternalPort")
    logger.info(s"\tSERVICE_EXTERNAL_IP\t:\t$ServiceExternalIP")

    private val config = loadConfig("/opt/profile/service.json")
    logger.info("Loaded configuration")
    logger.info(s"\tOrganization\t:\t${config.organization.name}.${config.organization.domain}")
    logger.info(s"\tOrdering nodes count\t:\t${config.network.orderingNodes.length}")
    logger.info(s"\tPeer node count\t:\t${config.network.peerNodes.length}")

    private val server = new Server(ServiceBindPort)
    server.setHandler(
        new RestEndpoint(
            s"$ServiceExternalIP:$ServiceExternalPort",
            config,
            cryptoManager = new FileBasedCryptoManager(
                config,"/opt/profile/crypto"
            ),
            processManager = new DockerBasedProcessManager(
                ProfilePath,
                DockerSocket,
                InitialName,
                config
            )
        )
    )
    setupShutdownHook()
    server.start()
    logger.info("Started.")
    server.join()


    //=========================================================================
    // Utilities
    //=========================================================================
    private def loadConfig(configFile: String): ServiceConfig =
        Util.codec.fromJson(new FileReader(configFile), classOf[ServiceConfig])

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
