package org.enterprisedlt.fabric.service.node

import java.io.FileReader

import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.enterprisedlt.fabric.service.node.rest.JsonRestEndpoint
import org.enterprisedlt.fabric.service.node.util.Util
import org.slf4j.LoggerFactory

/**
  * @author Maxim Fedin
  */
object ProcessManagementNode extends App {
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val ProcessManagementBindPort = Option(Environment.get("PROCESS_MANAGEMENT_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_BIND_PORT!"))
    private val ProfilePath = Option(Environment.get("PROFILE_PATH")).getOrElse(throw new Exception("Mandatory environment variable missing PROFILE_PATH!"))
    private val DockerSocket = Option(Environment.get("DOCKER_SOCKET")).getOrElse(throw new Exception("Mandatory environment variable missing DOCKER_SOCKET!"))
    private val InitialName = Option(Environment.get("INITIAL_NAME")).getOrElse(throw new Exception("Mandatory environment variable missing INITIAL_NAME!"))

    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val cryptoPath = "/opt/profile/crypto"
    logger.info("Starting...")
    private val config = loadConfig("/opt/profile/service.json")

    private val processManager = new DockerBasedProcessManager(
        ProfilePath, DockerSocket,
        InitialName, config
    )


    private val restEndpoint = new JsonRestEndpoint(ProcessManagementBindPort, new ProcessManagementEndpoint(processManager))

    setupShutdownHook()
    restEndpoint.start()
    logger.info("Started.")

    //=========================================================================
    private def setupShutdownHook(): Unit = {
        Runtime.getRuntime.addShutdownHook(new Thread("shutdown-hook") {
            override def run(): Unit = {
                logger.info("Shutting down...")
                restEndpoint.stop()
                logger.info("Shutdown complete.")
            }
        })
    }

    private def loadConfig(configFile: String): ServiceConfig =
        Util.codec.fromJson(new FileReader(configFile), classOf[ServiceConfig])

}

case class ExternalAddress(
    host: String,
    port: Int
)
