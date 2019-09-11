package org.enterprisedlt.fabric.service.node

import java.io.FileReader

import org.eclipse.jetty.server.Server
import org.enterprisedlt.fabric.service.node.client.FabricNetworkManager
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.rest.JsonRestEndpoint
import org.enterprisedlt.fabric.service.node.util.Util
import org.slf4j.LoggerFactory

/**
  * @author Maxim Fedin
  */
object AdministrationNode extends App {
    //
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val AdministrationServiceBindPort = Option(Environment.get("ADMINISTRATION_SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing ADMINISTRATION_SERVICE_BIND_PORT!"))
    //
    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)
    //
    logger.info("Starting...")
    //
    private val cryptoPath = "/opt/profile/crypto"
    private val config = loadConfig("/opt/profile/service.json")
    private val user = Util.loadDefaultAdmin(config.organization.name, cryptoPath)
    private val fabricNetworkManager = new FabricNetworkManager(config.organization, config.network.orderingNodes.head, user)
    //
    private val server = new Server(AdministrationServiceBindPort)
    server.setHandler(new JsonRestEndpoint(Util.createCodec, new AdministrationRestEndpoint(fabricNetworkManager)))
    //
    setupShutdownHook()
    server.start()
    logger.info("Started.")

    //
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

    private def loadConfig(configFile: String): ServiceConfig =
        Util.codec.fromJson(new FileReader(configFile), classOf[ServiceConfig])

}

//
case class ExternalAddress(
    host: String,
    port: Int
)