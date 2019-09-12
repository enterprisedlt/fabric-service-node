package org.enterprisedlt.fabric.service.node

import java.io.FileReader

import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.security.{ConstraintMapping, ConstraintSecurityHandler}
import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler.{ContextHandler, ContextHandlerCollection, ResourceHandler}
import org.eclipse.jetty.util.security.Constraint
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.server.WebSocketHandler
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.enterprisedlt.fabric.service.node.auth.{FabricAuthenticator, Role}
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.util.Util
import org.enterprisedlt.fabric.service.node.websocket.ServiceWebSocketManager
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  * @author Andrew Pudovikov
  */
object ServiceNode extends App {
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val ServiceBindPort = Option(Environment.get("SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_BIND_PORT!"))
    private val ServiceExternalAddress = Option(Environment.get("SERVICE_EXTERNAL_ADDRESS")).filter(_.trim.nonEmpty).map(parseExternalAddress(_, ServiceBindPort))

    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val cryptoPath = "/opt/profile/crypto"
    logger.info("Starting...")
    private val config = loadConfig("/opt/profile/service.json")
    private val restEndpoint = new RestEndpoint(
        ServiceBindPort, ServiceExternalAddress, config,
        processManager = ???, // TODO to add
        hostsManager = new HostsManager(
            "/opt/profile/hosts",
            config
        )
    )
    //TODO: make web app optional, based on configuration
    private val server = createServer(ServiceBindPort, restEndpoint, "/opt/profile/webapp")

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
            case _ => throw new IllegalArgumentException(s"Invalid format of external address: '$address', expected: 'HOST[:PORT]'")
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

    private def createServer(bindPort: Int, endpoint: Handler, webAppResource: String): Server = {
        val server = new Server()

        val connector = createTLSConnector(bindPort, server)
        server.setConnectors(Array(connector))

        val security = new ConstraintSecurityHandler
        security.setConstraintMappings(
            Array(
                newConstraint("admin", "/admin/*", Role.Admin),
                newConstraint("join", "/join-network", Role.Admin, Role.JoinToken),
                newConstraint("service", "/service/*", Role.Admin, Role.User),
                newConstraint("webapp", "/webapp/*", Role.Admin, Role.User),
                newConstraint("socket", "/socket/*", Role.Admin, Role.User),
            )
        )
        security.setAuthenticator(new FabricAuthenticator(config.organization, cryptoPath))

        //
        val endpointContext = new ContextHandler("/")
        endpointContext.setHandler(endpoint)

        // add serving for web app:
        Util.mkDirs(webAppResource)
        val webAppContext = new ContextHandler()
        webAppContext.setContextPath("/webapp")
        val webApp = new ResourceHandler()
        webApp.setResourceBase(webAppResource)
        webApp.setDirectoriesListed(false)
        webApp.setWelcomeFiles(Array("index.html"))
        webAppContext.setHandler(webApp)

        //TODO: make websocket server optional, based on configuration
        val webSocketContext = new ContextHandler("/socket")
        val wsHandler = new WebSocketHandler() {
            def configure(factory: WebSocketServletFactory): Unit = {
                factory.setCreator(ServiceWebSocketManager)
            }
        }
        webSocketContext.setHandler(wsHandler)

        security.setHandler(new ContextHandlerCollection(webAppContext, webSocketContext, endpointContext))
        server.setHandler(security)
        server
    }

    private def createTLSConnector(bindPort: Int, server: Server): ServerConnector = {
        val httpConfiguration = new HttpConfiguration()
        httpConfiguration.setSecureScheme("https")
        httpConfiguration.setSecurePort(bindPort)
        //        httpConfiguration.setOutputBufferSize(32768)
        //
        val sslContextFactory = new SslContextFactory.Server()
        val password = "password" // this will live only in our process memory so could be anything
        sslContextFactory.setKeyStorePassword(password)

        sslContextFactory.setTrustStorePassword(password)
        sslContextFactory.setNeedClientAuth(true)
        //
        val httpsConfiguration = new HttpConfiguration(httpConfiguration)
        val src = new SecureRequestCustomizer()
        //        src.setStsMaxAge(2000)
        //        src.setStsIncludeSubDomains(true)
        httpsConfiguration.addCustomizer(src)
        //
        val httpsConnector = new ServerConnector(server,
            new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
            new HttpConnectionFactory(httpsConfiguration))
        httpsConnector.setPort(bindPort)
        //        https.setIdleTimeout(500000)
        //
        httpsConnector
    }

    private def newConstraint(name: String, path: String, roles: String*): ConstraintMapping = {
        val c = new Constraint()
        c.setName(name)
        c.setAuthenticate(true)
        c.setRoles(roles.toArray)
        val result = new ConstraintMapping
        result.setConstraint(c)
        result.setPathSpec(path)
        result
    }
}

case class ExternalAddress(
    host: String,
    port: Int
)
