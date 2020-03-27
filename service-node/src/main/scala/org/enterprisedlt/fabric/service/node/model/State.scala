package org.enterprisedlt.fabric.service.node.model

import org.enterprisedlt.fabric.service.node.{FabricNetworkManager, FabricProcessManager, StateManager}
import org.enterprisedlt.fabric.service.node.configuration.NetworkConfig

/**
 * @author Andrew Pudovikov
 */
case class GlobalState(
    networkManager: FabricNetworkManager,
    processManager: FabricProcessManager,
    stateManager: StateManager,
    network: NetworkConfig,
    networkName: String
)


case class RestoredState(
    networkManager: FabricNetworkManager,
    processManager: FabricProcessManager,
    network: NetworkConfig,
    networkName: String
)




