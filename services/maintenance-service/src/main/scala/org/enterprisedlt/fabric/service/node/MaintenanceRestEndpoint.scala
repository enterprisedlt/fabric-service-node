package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, ServiceConfig}
import org.enterprisedlt.fabric.service.node.maintenance.{Bootstrap, Join}
import org.enterprisedlt.fabric.service.node.model.{Invite, JoinRequest, JoinResponse}
import org.enterprisedlt.fabric.service.node.services._
import org.enterprisedlt.fabric.service.node.util.Util
import org.hyperledger.fabric.sdk.User
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
class MaintenanceRestEndpoint(
    processManagementClient: ProcessManagementManager,
    administrationClient: AdministrationManager,
    proxyClient: ProxyManager,
    config: ServiceConfig,
    user: User,
    cryptoPath: String,
    maintenanceServiceBindPort: Int,
    externalAddress: Option[ExternalAddress],
    hostsManager: HostsManager
)
  extends MaintenanceManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def bootstrap(bootstrapOptions: BootstrapOptions): Either[String, Unit] = {
        logger.info(s"Bootstrapping organization ${config.organization.name}...")
        val start = System.currentTimeMillis()
        try {
            logger.info(s"$bootstrapOptions")
            Bootstrap.bootstrapOrganization(config, user, externalAddress, administrationClient, processManagementClient, bootstrapOptions)
            val end = System.currentTimeMillis() - start
            logger.info(s"Bootstrap done ($end ms)")
            Right(())

        } catch {
            case ex: Exception =>
                logger.error("Bootstrap failed:", ex)
                Left("Failed")
        }
    }

    override def createInvite: Either[String, Invite] = {
        logger.info(s"Creating invite ${config.organization.name}...")
        val start = System.currentTimeMillis()
        try {
            val address = externalAddress
              .map(ea => s"${ea.host}:${ea.port}")
              .getOrElse(s"maintenance.service.${config.organization.name}.${config.organization.domain}:$maintenanceServiceBindPort")
            //TODO: password should be taken from request
            val password = "join me"
            val key = Util.createServiceUserKeyStore(config, s"join-${System.currentTimeMillis()}", password, cryptoPath)
            val invite = Invite(
                address,
                Util.keyStoreToBase64(key, password)
            )
            val end = System.currentTimeMillis() - start
            logger.info(s"Create invite done ($end ms)")
            Right(invite)
        } catch {
            case ex: Exception =>
                logger.error("Failed to create invite:", ex)
                Left("Failed")
        }
    }

    override def requestJoin(invite: Invite): Either[String, Unit] = {
        logger.info("Requesting to join network ...")
        val start = System.currentTimeMillis()
        try {
            Join.join(config, user, processManagementClient, administrationClient, proxyClient, externalAddress, cryptoPath, invite, hostsManager)
            val end = System.currentTimeMillis() - start
            logger.info(s"Joined ($end ms)")
            Right(())
        } catch {
            case ex: Exception =>
                logger.error("Request failed:", ex)
                Left("Failed")
        }
    }

    override def joinOrgToNetwork(joinRequest: JoinRequest): Either[String, JoinResponse] = {
        logger.info("Requesting to join network ...")
        val start = System.currentTimeMillis()
        try {
            val joinResponse = Join.joinOrgToNetwork(processManagementClient, administrationClient, proxyClient, config, cryptoPath, joinRequest, hostsManager)
            val end = System.currentTimeMillis() - start
            logger.info(s"Joined ($end ms)")
            joinResponse
        } catch {
            case ex: Exception =>
                logger.error("Request to join network failed:", ex)
                Left("Failed")
        }
    }
}
