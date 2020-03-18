package org.enterprisedlt.fabric.service.node.flow

import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.model.ServiceVersion
import org.enterprisedlt.fabric.service.node.configuration.{DockerConfig, NetworkConfig, OrganizationConfig}
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.{FabricComponentsState, FabricServiceState, ProcessManagerState}
import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.enterprisedlt.fabric.service.node._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
 * @author Andrew Pudovikov
 */
object RestoreState {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def restoreOrganizationState(
        organizationConfig: OrganizationConfig,
        cryptography: CryptoManager,
        fabricComponentsState: FabricComponentsState,
        processManagerState: ProcessManagerState,
        hostsManager: HostsManager,
        profilePath: String,
        processConfig: DockerConfig,
        state: AtomicReference[FabricServiceState]
    ): GlobalState = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"[ $organizationFullName ] - Restoring state ...")
        state.set(FabricServiceState(FabricServiceState.RestoringState))
        val admin = cryptography.loadDefaultAdmin
        val networkConfig = NetworkConfig(
            fabricComponentsState.osns.asScala.values.toArray,
            fabricComponentsState.peers.asScala.values.toArray,
        )
        val processManager = new DockerBasedProcessManager(
            profilePath,
            organizationConfig,
            processManagerState.networkName,
            networkConfig,
            processConfig
        )
        val existComponents = processManager.verifyContainersExistence()
        val network = new FabricNetworkManager(organizationConfig, fabricComponentsState.osns.entrySet().iterator().next().getValue, admin)

        state.set(FabricServiceState(FabricServiceState.DefiningFabricComponents))
        network.getHFClient(admin)


        logger.debug(s"[ $organizationFullName ] - Defining peers ...")
        existComponents.peerNodes.foreach(e => network.definePeer(e))
        logger.debug(s"[ $organizationFullName ] - Defining channels ...")
        fabricComponentsState.channels.foreach { channelConfig =>
            network.restoreChannelWithComponents(
                channelConfig,
                existComponents.orderingNodes,
                existComponents.peerNodes
            )
        }
        network
          .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
          .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
          .map(Util.codec.fromJson(_, classOf[ServiceVersion]))
        match {
            case Left(error) => throw new Exception(s"Failed to warn up service chain code: $error")
            case Right(serviceVersion) =>
                state.set(FabricServiceState(FabricServiceState.JoinSettingUpBlockListener))
                network.setupBlockListener(ServiceChannelName, new NetworkMonitor(organizationConfig, networkConfig, network, processManager, hostsManager, serviceVersion))
                //
                logger.info(s"[ $organizationFullName ] - Restoring done.")
                state.set(FabricServiceState(FabricServiceState.Ready))
                GlobalState(network, processManager, networkConfig, processManagerState.networkName)
        }
    }
}