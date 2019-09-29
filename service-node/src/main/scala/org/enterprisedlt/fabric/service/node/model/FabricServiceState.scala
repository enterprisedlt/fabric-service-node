package org.enterprisedlt.fabric.service.node.model

/**
  * @author Alexey Polubelov
  */
case class FabricServiceState(
    stateCode: Int
)

object FabricServiceState {
    // Initial state of service
    val NotInitialized = 0
    //
    val BootstrapStarted = 11
    val BootstrapCreatingGenesis = 12
    val BootstrapStartingOrdering = 13
    val BootstrapAwaitingOrdering = 14
    val BootstrapStartingPeers = 15
    val BootstrapCreatingServiceChannel = 16
    val BootstrapAddingPeersToChannel = 17
    val BootstrapUpdatingAnchors = 18
    val BootstrapInstallingServiceChainCode = 19
    val BootstrapInitializingServiceChainCode = 20
    val BootstrapSettingUpBlockListener = 21

    val BootstrapMaxValue: Int = BootstrapSettingUpBlockListener

    //
    val JoinStarted = 50
    val JoinCreatingJoinRequest = 51
    val JoinAwaitingJoin = 52
    val JoinStartingOrdering = 53
    val JoinConnectingToNetwork = 54
    val JoinStartingPeers = 55
    val JoinAddingPeersToChannel = 56
    val JoinUpdatingAnchors = 57
    val JoinInstallingServiceChainCode = 58
    val JoinInitializingServiceChainCode = 59
    val JoinSettingUpBlockListener = 60


    val JoinMaxValue: Int = JoinSettingUpBlockListener

    // Ready to work
    val Ready = 100
}
