package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, ServiceConfig}
import org.enterprisedlt.fabric.service.node.maintenance.Bootstrap
import org.enterprisedlt.fabric.service.node.services.{AdministrationManager, MaintenanceManager, ProcessManagementManager}
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

}
