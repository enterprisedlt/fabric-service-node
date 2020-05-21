package org.enterprisedlt.fabric.service.node

import java.security.Security
import java.util.concurrent.atomic.AtomicReference

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.security.{ConstraintMapping, ConstraintSecurityHandler}
import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler.{ContextHandler, ContextHandlerCollection, ResourceHandler}
import org.eclipse.jetty.util.security.Constraint
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.websocket.server.WebSocketHandler
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.enterprisedlt.fabric.service.node.auth.{FabricAuthenticator, Role}
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig
import org.enterprisedlt.fabric.service.node.cryptography.FileBasedCryptoManager
import org.enterprisedlt.fabric.service.node.model.FabricServiceStateHolder
import org.enterprisedlt.fabric.service.node.process.ProcessManager
import org.enterprisedlt.fabric.service.node.rest.JsonRestEndpoint
import org.enterprisedlt.fabric.service.node.shared.FabricServiceState
import org.enterprisedlt.fabric.service.node.websocket.ServiceWebSocketManager
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 * @author Andrew Pudovikov
 * @author Maxim Fedin
 */
object ServiceNode extends App {
    private val Environment = System.getenv()
    private val LogLevel = Option(Environment.get("LOG_LEVEL")).filter(_.trim.nonEmpty).getOrElse("INFO")
    private val ServiceBindPort = Option(Environment.get("SERVICE_BIND_PORT")).map(_.toInt).getOrElse(throw new Exception("Mandatory environment variable missing SERVICE_BIND_PORT!"))
    private val ServiceExternalAddress = Option(Environment.get("SERVICE_EXTERNAL_ADDRESS")).filter(_.trim.nonEmpty).map(parseExternalAddress(_, ServiceBindPort))
    private val ProfilePath = Option(Environment.get("PROFILE_PATH")).getOrElse(throw new Exception("Mandatory environment variable missing PROFILE_PATH!"))
    // Org variables
    private val OrgName = Option(Environment.get("ORG")).getOrElse(throw new Exception("Mandatory environment variable missing ORG!"))
    private val Domain = Option(Environment.get("DOMAIN")).getOrElse(throw new Exception("Mandatory environment variable missing DOMAIN!"))
    private val AdminPassword = Option(Environment.get("ADMIN_PASSWORD")).filter(_.trim.nonEmpty)
    private val Location = Option(Environment.get("ORG_LOCATION")).filter(_.trim.nonEmpty).getOrElse("San Francisco")
    private val State = Option(Environment.get("ORG_STATE")).filter(_.trim.nonEmpty).getOrElse("California")
    private val Country = Option(Environment.get("ORG_COUNTRY")).filter(_.trim.nonEmpty).getOrElse("US")
    private val CertificateDuration = Option(Environment.get("CERTIFICATION_DURATION")).filter(_.trim.nonEmpty).getOrElse("P2Y")
    //
    Security.addProvider(new BouncyCastleProvider)
    Util.setupLogging(LogLevel)
    private val logger = LoggerFactory.getLogger(this.getClass)

    logger.info(s"Starting service node for $OrgName.$Domain ...")
    FabricServiceStateHolder.update(_ =>
        FabricServiceState(
            mspId = OrgName,
            organizationFullName = s"$OrgName.$Domain",
            stateCode = FabricServiceState.NotInitialized,
            version = 0
        )
    )
    private val organizationConfig: OrganizationConfig = OrganizationConfig(
        OrgName,
        Domain,
        Location,
        State,
        Country,
        CertificateDuration
    )
    private val processManager = new ProcessManager
    private val cryptoManager = new FileBasedCryptoManager(organizationConfig, "/opt/profile/crypto", AdminPassword)
    private val restEndpoint = new RestEndpoint(
        ServiceBindPort, ServiceExternalAddress, organizationConfig, cryptoManager,
        hostsManager = new HostsManager("/opt/profile/hosts", ServiceExternalAddress.map(_.host)),
        ProfilePath, processManager
    )
    //TODO: make web app optional, based on configuration
    private val server =
        createServer(
            ServiceBindPort, cryptoManager, restEndpoint,
            "/opt/profile/webapp",
            "/opt/service/admin-console"
        )

    setupShutdownHook()
    server.start()
    logger.info("Started.")
    //    server.join()


    //=========================================================================
    // Utilities
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

    private def createServer(bindPort: Int, cryptography: CryptoManager, endpoint: AnyRef, webAppResource: String, adminConsole: String): Server = {
        val server = new Server()

        val connector = createTLSConnector(bindPort, server, cryptography)
        server.setConnectors(Array(connector))

        val security = new ConstraintSecurityHandler
        security.setConstraintMappings(
            Array(
                newConstraint("admin", "/admin/*", Role.Admin),
                newConstraint("admin-console", "/admin-console/*", Role.Admin),
                newConstraint("join", "/join-network", Role.Admin, Role.JoinToken),
                newConstraint("service", "/service/*", Role.Admin, Role.User),
                newConstraint("webapp", "/application/*", Role.Admin, Role.User),
                newConstraint("socket", "/socket/*", Role.Admin, Role.User),
            )
        )
        security.setAuthenticator(new FabricAuthenticator(cryptography))

        //
        val endpointContext = new ContextHandler("/")
        endpointContext.setHandler(
            new JsonRestEndpoint(
                Util.createCodec,
                endpoint
            )
        )

        // add serving for web app:
        Util.mkDirs(webAppResource)
        val webAppContext = new ContextHandler()
        webAppContext.setContextPath("/application")
        val webApp = new ResourceHandler()
        webApp.setResourceBase(webAppResource)
        webApp.setDirectoriesListed(false)
        webApp.setWelcomeFiles(Array("index.html"))
        webAppContext.setHandler(webApp)

        // add serving for web app:
        Util.mkDirs(adminConsole)
        val adminApp = new ResourceHandler()
        adminApp.setResourceBase(adminConsole)
        adminApp.setDirectoriesListed(false)
        adminApp.setWelcomeFiles(Array("index.html"))

        val adminAppContext = new ContextHandler()
        adminAppContext.setContextPath("/admin-console")
        adminAppContext.setHandler(adminApp)


        //TODO: make websocket server optional, based on configuration
        val webSocketContext = new ContextHandler("/socket")
        val wsHandler = new WebSocketHandler() {
            def configure(factory: WebSocketServletFactory): Unit = {
                factory.setCreator(ServiceWebSocketManager)
            }
        }
        webSocketContext.setHandler(wsHandler)

        security.setHandler(new ContextHandlerCollection(webAppContext, adminAppContext, webSocketContext, endpointContext))
        server.setHandler(security)
        server
    }

    private def createTLSConnector(bindPort: Int, server: Server, cryptography: CryptoManager): ServerConnector = {
        val httpConfiguration = new HttpConfiguration()
        httpConfiguration.setSecureScheme("https")
        httpConfiguration.setSecurePort(bindPort)
        //        httpConfiguration.setOutputBufferSize(32768)
        //
        val sslContextFactory = new SslContextFactory.Server()
        val password = "password" // this will live only in our process memory so could be anything
        val keyStore = cryptography.createServiceTLSKeyStore(password)
        sslContextFactory.setKeyStore(keyStore)
        sslContextFactory.setKeyStorePassword(password)

        val trustStore = cryptography.createServiceTrustStore(password)
        sslContextFactory.setTrustStore(trustStore)
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
