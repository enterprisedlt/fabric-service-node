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
import scala.util.Try

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
    ): Either[String, GlobalState] = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"[ $organizationFullName ] - Restoring state ...")
        for {
            admin <- Try(cryptography.loadDefaultAdmin).toEither.left.map(_.getMessage)
            _ = state.set(FabricServiceState(FabricServiceState.RestoringState))
            networkConfig = NetworkConfig(
                fabricComponentsState.osns.asScala.values.toArray,
                fabricComponentsState.peers.asScala.values.toArray,
            )
            _ = state.set(FabricServiceState(FabricServiceState.DefiningFabricComponents))
            processManager <- Try(new DockerBasedProcessManager(
                profilePath,
                organizationConfig,
                processManagerState.networkName,
                networkConfig,
                processConfig
            )).toEither.left.map(_.getMessage)
            _ <- processManager.checkContainersRunningForRestore()
            network = new FabricNetworkManager(organizationConfig, fabricComponentsState.osns.entrySet().iterator().next().getValue, admin)
            _ = state.set(FabricServiceState(FabricServiceState.DefiningProcessComponents))
            _ = {
                logger.debug(s"[ $organizationFullName ] - Defining peers ...")
                networkConfig.peerNodes.foreach(e => network.definePeer(e))
            }
            _ = {
                logger.debug(s"[ $organizationFullName ] - Defining channels ...")
                fabricComponentsState.channels.map { channelConfig =>
                    network.restoreChannelWithComponents(
                        channelConfig,
                        networkConfig.orderingNodes,
                        networkConfig.peerNodes
                    )
                }.filter(_.isLeft).map(_.left.get).mkString("\n")
            }
            serviceVersion <- network
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty)
                .map(Util.codec.fromJson(_, classOf[ServiceVersion])).toRight(s"Failed to warn up service chain code: Empty result"))
            _ <- {
                state.set(FabricServiceState(FabricServiceState.JoinSettingUpBlockListener))
                network.setupBlockListener(ServiceChannelName, new NetworkMonitor(organizationConfig, networkConfig, network, processManager, hostsManager, serviceVersion))
            }
            _ = {
                logger.info(s"[ $organizationFullName ] - Restoring done.")
                state.set(FabricServiceState(FabricServiceState.Ready))
            }
        } yield GlobalState(
            network,
            processManager,
            networkConfig,
            processManagerState.networkName
        )
    }
}