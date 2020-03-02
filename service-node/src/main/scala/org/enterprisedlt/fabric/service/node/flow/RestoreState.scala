package org.enterprisedlt.fabric.service.node.flow

import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.node.{CryptoManager, FabricNetworkManager, GlobalState}
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig
import org.enterprisedlt.fabric.service.node.model.{ComponentsState, FabricServiceState}
import org.slf4j.LoggerFactory

/**
 * @author Andrew Pudovikov
 */
object RestoreState {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def bootstrapOrganization(
        organizationConfig: OrganizationConfig,
        cryptography: CryptoManager,
        componentState: ComponentsState,
        state: AtomicReference[FabricServiceState]
    ): GlobalState = {

        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"[ $organizationFullName ] - Restoring state ...")
        state.set(FabricServiceState(FabricServiceState.RestoringState))
        val admin = cryptography.loadDefaultAdmin
        val network = new FabricNetworkManager(organizationConfig, componentState.osns.entrySet().iterator().next(), admin)
        //

    }
}