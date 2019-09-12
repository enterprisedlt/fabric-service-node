package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, ServiceConfig}
import org.enterprisedlt.fabric.service.node.maintenance.Bootstrap
import org.enterprisedlt.fabric.service.node.model.Invite
import org.enterprisedlt.fabric.service.node.services.{AdministrationManager, MaintenanceManager, ProcessManagementManager}
import org.enterprisedlt.fabric.service.node.util.Util
import org.hyperledger.fabric.sdk.User
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
class MaintenanceRestEndpoint(
    processManagementClient: ProcessManagementManager,
    administrationClient: AdministrationManager,
    config: ServiceConfig,
    user: User,
    cryptoPath: String,
    maintenanceServiceBindPort: Int,
    externalAddress: Option[ExternalAddress]
)
  extends MaintenanceManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def bootstrap(bootstrapOptions: BootstrapOptions): Either[String, Unit] = {
        logger.info(s"Bootstrapping organization ${config.organization.name}...")
        val start = System.currentTimeMillis()
        try {
            logger.info(s"$bootstrapOptions")
            Bootstrap.bootstrapOrganization(config, user, externalAddress, administrationClient, processManagementClient)(bootstrapOptions)
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
        val address = externalAddress
          .map(ea => s"${ea.host}:${ea.port}")
          .getOrElse(s"maintenance.${config.organization.name}.${config.organization.domain}:$maintenanceServiceBindPort")
        //TODO: password should be taken from request
        val password = "join me"
        val key = Util.createServiceUserKeyStore(config, s"join-${System.currentTimeMillis()}", password, cryptoPath)
        val invite = Invite(
            address,
            Util.keyStoreToBase64(key, password)
        )
        Right(invite)
    }

}
