package org.enterprisedlt.fabric.service.node

import org.eclipse.jetty.server.Server
import org.enterprisedlt.fabric.service.node.configuration.DockerConfig
import org.enterprisedlt.fabric.service.node.process.DockerManagedBox
import org.enterprisedlt.fabric.service.node.rest.JsonRestEndpoint
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */
object BoxManager extends App {
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    Util.setupLogging(LogLevel)
    private val BoxManagerName = Option(Environment.get("BOX_MANAGER_NAME")).filter(_.trim.nonEmpty).getOrElse(throw new Exception("Mandatory environment variable missing BOX_MANAGER_NAME!"))
    private val BoxManagerBindPort = Option(Environment.get("BOX_MANAGER_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_BIND_PORT!"))
    private val BoxManagerAddress = Option(Environment.get("BOX_MANAGER_EXTERNAL_ADDRESS")).filter(_.trim.nonEmpty)
    private val FabricServiceNetwork = Option(Environment.get("FABRIC_SERVICE_NETWORK")).filter(_.trim.nonEmpty).getOrElse(throw new Exception("Mandatory environment variable missing FABRIC_SERVICE_NETWORK!"))
    private val ProfilePath = Option(Environment.get("PROFILE_PATH")).getOrElse(throw new Exception("Mandatory environment variable missing PROFILE_PATH!"))
    private val FabricComponentsLogLevel = Option(Environment.get("FABRIC_COMPONENTS_LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val DockerSocket = Option(Environment.get("DOCKER_SOCKET")).getOrElse(throw new Exception("Mandatory environment variable missing DOCKER_SOCKET!"))
    private val LogFileSize = Option(Environment.get("LOG_FILE_SIZE")).filter(_.trim.nonEmpty).getOrElse("100m")
    private val LogMaxFiles = Option(Environment.get("LOG_MAX_FILES")).filter(_.trim.nonEmpty).getOrElse("5")

    private val logger = LoggerFactory.getLogger(this.getClass)

    logger.info("Starting process manager ...")
    private val processConfig: DockerConfig = DockerConfig(
        DockerSocket,
        LogFileSize,
        LogMaxFiles,
        FabricComponentsLogLevel
    )

    private val hostsManager = new HostsManager("/opt/profile/hosts", BoxManagerAddress)
    private val box =
        new DockerManagedBox(
            hostPath = ProfilePath,
            containerName = BoxManagerName,
            address = BoxManagerAddress,
            networkName = FabricServiceNetwork,
            hostsManager = hostsManager,
            processConfig = processConfig
        )

    private val server = new Server(BoxManagerBindPort)
    setupShutdownHook()

    server.setHandler(new JsonRestEndpoint(Util.createCodec, box))
    server.start()
    logger.info("Started.")

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
