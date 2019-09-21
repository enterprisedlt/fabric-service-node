package org.enterprisedlt.fabric.service.node

import java.io.FileReader

import org.eclipse.jetty.server.Server
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.rest.{JsonRestClient, JsonRestEndpoint}
import org.enterprisedlt.fabric.service.node.services.{AdministrationManager, HostsManager, ProcessManagementManager, ProxyManager}
import org.enterprisedlt.fabric.service.node.util.Util
import org.slf4j.LoggerFactory

/**
  * @author Maxim Fedin
  */
object MaintenanceNode extends App {
    //
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val MaintenanceServiceBindPort = Option(Environment.get("MAINTENANCE_SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing MAINTENANCE_SERVICE_BIND_PORT!"))
    private val ProcessManagementBindPort = Option(Environment.get("PROCESS_MANAGEMENT_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing PROCESS_MANAGEMENT_BIND_PORT!"))
    private val ProcessManagementServiceHost = Option(Environment.get("PROCESS_MANAGEMENT_HOST")).getOrElse(throw new Exception("Mandatory environment variable missing PROCESS_MANAGEMENT_HOST!"))
    private val AdministrationServiceBindPort = Option(Environment.get("ADMINISTRATION_SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing ADMINISTRATION_SERVICE_BIND_PORT!"))
    private val AdministrationServiceHost = Option(Environment.get("ADMINISTRATION_SERVICE_HOST")).getOrElse(throw new Exception("Mandatory environment variable missing ADMINISTRATION_SERVICE_HOST!"))
    private val ProxyServiceBindPort = Option(Environment.get("PROXY_SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing PROXY_SERVICE_BIND_PORT!"))
    private val ProxyServiceHost = Option(Environment.get("PROXY_SERVICE_HOST")).getOrElse(throw new Exception("Mandatory environment variable missing PROXY_SERVICE_HOST!"))
    private val ServiceExternalAddress = Option(Environment.get("SERVICE_EXTERNAL_ADDRESS")).filter(_.trim.nonEmpty).map(parseExternalAddress(_, MaintenanceServiceBindPort))
    //
    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)
    //
    logger.info("Starting...")
    //
    private val cryptoPath = "/opt/profile/crypto"
    private val config = loadConfig("/opt/profile/service.json")
    private val user = Util.loadDefaultAdmin(config.organization.name, cryptoPath)
    private val hostsManager = new HostsManager(
        "/opt/profile/hosts",
        config
    )
    //
    private val processManagementClient = JsonRestClient.create[ProcessManagementManager](s"http://$ProcessManagementServiceHost:$ProcessManagementBindPort")
    private val administrationClient = JsonRestClient.create[AdministrationManager](s"http://$AdministrationServiceHost:$AdministrationServiceBindPort")
    private val proxyClient = JsonRestClient.create[ProxyManager](s"http://$ProxyServiceHost:$ProxyServiceBindPort")
    //
    private val server = new Server(MaintenanceServiceBindPort)
    server.setHandler(new JsonRestEndpoint(Util.createCodec, new MaintenanceRestEndpoint(processManagementClient, administrationClient, proxyClient, config, user, cryptoPath, MaintenanceServiceBindPort, ServiceExternalAddress,hostsManager)))
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

    private def parseExternalAddress(address: String, defaultPort: Int): ExternalAddress = {
        address.split(":") match {
            case Array(host, port) => ExternalAddress(host, port.toInt)
            case Array(host) => ExternalAddress(host, defaultPort)
            case _ => throw new IllegalArgumentException(s"Invalid format of external address: '$address', expected: 'HOST[:PORT]'")
        }
    }

    private def loadConfig(configFile: String): ServiceConfig =
        Util.codec.fromJson(new FileReader(configFile), classOf[ServiceConfig])

}

//
case class ExternalAddress(
    host: String,
    port: Int
)
