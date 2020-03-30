package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.model.ServiceVersion
import org.enterprisedlt.fabric.service.node.configuration.{NetworkConfig, OrganizationConfig}
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model.{FabricComponentsState, FabricServiceState, ProcessManagerState, RestoredState, ServiceNodeState}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * @author Andrew Pudovikov
 */
class FilePersistingStateManager(
    stateFilePath: String,
    processManager: FabricProcessManager,
    networkManager: FabricNetworkManager,
    organizationConfig: OrganizationConfig,
    hostsManager: HostsManager,
    state: AtomicReference[FabricServiceState]
) extends StateManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def storeState(): Either[String, Unit] = {
        logger.debug(s"persisting service node state")
        for {
            networkState <- networkManager.getState().toRight("Error getting network network manager state")
            processState <- processManager.getState().toRight("Error getting process manager state")
            stateToStore = ServiceNodeState(networkState, ProcessManagerState(processState))
            marshalledState <- Try(Util.codec.toJson(stateToStore)).toEither.left.map(_.getMessage)
            _ <- Try(storeStateToFile(stateFilePath, marshalledState)).toEither.left.map(_.getMessage)
        } yield ()
    }


    override def restoreState(): Either[String, RestoredState] = {
        logger.debug(s"restoring globalState from file")
        for {
            gStateJson <- getStateFromFile()
            _ = state.set(FabricServiceState(FabricServiceState.RestoringState))
            gState <- Try {
                logger.debug(s"during restoring parsted gState $gStateJson")
                Util.codec.fromJson(gStateJson, classOf[ServiceNodeState])
            }.toEither.left.map(_.getMessage)
            processState <- Either.cond(
                gState.processManagerState.networkName != null,
                gState.processManagerState,
                "There is no channel at the gState"
            )
            networkState <- Either.cond(
                gState.fabricComponentsState.channels.head.nonEmpty,
                gState.fabricComponentsState,
                "There is no channel at the gState"
            )
            networkConfig = NetworkConfig(
                networkState.osns.asScala.values.toArray,
                networkState.peers.asScala.values.toArray
            )
            _ = state.set(FabricServiceState(FabricServiceState.DefiningProcessComponents))
            _ <- processManager.defineNetwork(processState.networkName, networkConfig)
            _ = state.set(FabricServiceState(FabricServiceState.DefiningFabricComponents))
            _ = networkConfig.orderingNodes.foreach(e => networkManager.defineOsn(e))
            _ = networkConfig.peerNodes.foreach(e => networkManager.definePeer(e))
            _ = networkState.channels.map { channelConfig =>
                networkManager.restoreChannelWithComponents(
                    channelConfig,
                    networkConfig.orderingNodes,
                    networkConfig.peerNodes
                )
            }.filter(_.isLeft).map(_.left.get).mkString("\n")

            serviceVersion <- networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty)
                .map(Util.codec.fromJson(_, classOf[ServiceVersion])).toRight(s"Failed to warn up service chain code: Empty result"))
            _ <- networkManager.setupBlockListener(
                ServiceChannelName,
                new NetworkMonitor(
                    organizationConfig,
                    networkConfig,
                    networkManager,
                    processManager,
                    hostsManager,
                    serviceVersion
                )
            )
            restoredState = RestoredState(
                networkManager,
                processManager,
                networkConfig,
                processState.networkName
            )
            _ = state.set(FabricServiceState(FabricServiceState.Ready))
        } yield restoredState
    }


    private def getStateFromFile(): Either[String, String] = {
        Try {
            val file = new File(stateFilePath)
            val r = Files.readAllBytes(Paths.get(file.toURI))
            new String(r, StandardCharsets.UTF_8)
        }.toEither.left.map(_.getMessage)
    }

    //=========================================================================
    private def storeStateToFile(stateFilePath: String, state: String): Unit = {
        val parent = new File(stateFilePath).getParentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val out = new FileOutputStream(stateFilePath)
        try {
            logger.debug(s"Saving state to file $stateFilePath")
            val s = state.getBytes(StandardCharsets.UTF_8)
            out.write(s)
            out.flush()
        } finally {
            out.close()
        }
    }
}