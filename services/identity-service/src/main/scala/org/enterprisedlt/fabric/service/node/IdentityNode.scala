package org.enterprisedlt.fabric.service.node

import java.io.FileReader

import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.identity.FileBasedCryptoManager
import org.enterprisedlt.fabric.service.node.rest.JsonRestEndpoint
import org.enterprisedlt.fabric.service.node.util.Util
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
object IdentityNode extends App {

    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val ServiceBindPort = Option(Environment.get("SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_BIND_PORT!"))

    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)

    logger.info("Starting...")

    private val cryptoPath = "/opt/profile/crypto"
    private val config = loadConfig("/opt/profile/service.json")
    private val cryptoManager = new FileBasedCryptoManager(config,cryptoPath)
    private val restEndpoint = new JsonRestEndpoint(ServiceBindPort, new IdentityRestEndpoint(cryptoManager))

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

    private def parseExternalAddress(address: String, defaultPort: Int): ExternalAddress = {
        address.split(":") match {
            case Array(host, port) => ExternalAddress(host, port.toInt)
            case Array(host) => ExternalAddress(host, defaultPort)
            case _ => throw new IllegalArgumentException(s"Invalid format of external address: '$address', expected: 'HOST[:PROT]'")
        }
    }

    private def loadConfig(configFile: String): ServiceConfig =
        Util.codec.fromJson(new FileReader(configFile), classOf[ServiceConfig])

}

case class ExternalAddress(
    host: String,
    port: Int
)

