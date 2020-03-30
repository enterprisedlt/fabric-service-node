package org.enterprisedlt.fabric.service.node.flow

import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.configuration.{DockerConfig, OrganizationConfig}
import org.enterprisedlt.fabric.service.node.model.{FabricServiceState, GlobalState}
import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Andrew Pudovikov
 */
object RestoreState {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def restoreOrganizationState(
        stateFilePath: String,
        organizationConfig: OrganizationConfig,
        cryptoManager: CryptoManager,
        hostsManager: HostsManager,
        profilePath: String,
        processConfig: DockerConfig,
        state: AtomicReference[FabricServiceState]
    ): Either[String, GlobalState] = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"[ $organizationFullName ] - Restoring state ...")
        for {
            admin <- Try(cryptoManager.loadDefaultAdmin).toEither.left.map(_.getMessage)
            processManager = new DockerBasedProcessManager(
                profilePath,
                organizationConfig,
                processConfig
            )
            networkManager = new FabricNetworkManager(organizationConfig, admin)
            stateManager = new FilePersistingStateManager(
                stateFilePath,
                processManager,
                networkManager,
                organizationConfig,
                hostsManager,
                state
            )
            restoredState <- stateManager.restoreState()
        } yield GlobalState(
            restoredState.networkManager,
            restoredState.processManager,
            stateManager,
            restoredState.network,
            restoredState.networkName
        )
    }
}