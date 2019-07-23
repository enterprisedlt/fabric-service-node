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
    private val ServiceExternalAddress = Option(Environment.get("SERVICE_EXTERNAL_ADDRESS")).filter(_.trim.nonEmpty).map(parseExternalAddress(_, ServiceBindPort))
    private val ProfilePath = Option(Environment.get("PROFILE_PATH")).getOrElse(throw new Exception("Mandatory environment variable missing PROFILE_PATH!"))
    private val DockerSocket = Option(Environment.get("DOCKER_SOCKET")).getOrElse(throw new Exception("Mandatory environment variable missing DOCKER_SOCKET!"))
    private val InitialName = Option(Environment.get("INITIAL_NAME")).getOrElse(throw new Exception("Mandatory environment variable missing INITIAL_NAME!"))

    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)

    logger.info("Starting...")
    private val config = loadConfig("/opt/profile/service.json")
    private val server = new Server(ServiceBindPort)
    val cryptography = new FileBasedCryptoManager(config,"/opt/profile/crypto")
    server.setHandler(
        new RestEndpoint(
            ServiceBindPort, ServiceExternalAddress, config, cryptography,
            processManager = new DockerBasedProcessManager(
                ProfilePath, DockerSocket,
                InitialName, config
            ),
            hostsManager = new HostsManager(
                "/opt/profile/hosts",
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
    private def parseExternalAddress(address: String, defaultPort: Int): ExternalAddress = {
        address.split(":") match {
            case Array(host, port) => ExternalAddress(host, port.toInt)
            case Array(host) => ExternalAddress(host, defaultPort)
            case _ => throw new IllegalArgumentException(s"Invalid format of external address: '$address', expected: 'HOST[:PROT]'")
        }
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

case class ExternalAddress(
    host: String,
    port: Int
)
