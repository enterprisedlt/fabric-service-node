package org.enterprisedlt.fabric.service.node

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import org.enterprisedlt.fabric.service.model.{Organization, ServiceVersion}
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.websocket.ServiceWebSocketManager
import org.hyperledger.fabric.protos.peer.Chaincode.ChaincodeDeploymentSpec
import org.hyperledger.fabric.sdk.{BlockEvent, BlockListener}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._


/**
  * @author Alexey Polubelov
  */
class NetworkMonitor(
    config: ServiceConfig,
    network: FabricNetworkManager,
    processManager: FabricProcessManager,
    hostsManager: HostsManager,
    initialVersion: ServiceVersion
) extends BlockListener {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
    private var currentVersion = initialVersion

    override def received(blockEvent: BlockEvent): Unit = {
        logger.info(s"Received block ${blockEvent.getBlockNumber} with ${blockEvent.getTransactionCount} transactions.")
        blockEvent.getTransactionEvents.iterator().asScala.foreach { txEvent =>
            txEvent.getTransactionActionInfos.iterator().asScala.foreach { tai =>
                val ccId = tai.getChaincodeIDName
                if ("lscc" == ccId) {
                    logger.info(s"\tDetected CC Lifecycle management Tx.")
                    val lsccFunctionName = new String(tai.getChaincodeInputArgs(0), StandardCharsets.UTF_8)
                    logger.info(s"Operation type is: $lsccFunctionName")
                    if ("upgrade" == lsccFunctionName) {
                        val channelName = new String(tai.getChaincodeInputArgs(1), StandardCharsets.UTF_8)
                        val deploymentSpec = ChaincodeDeploymentSpec.parseFrom(tai.getChaincodeInputArgs(2))
                        val ccSpec = deploymentSpec.getChaincodeSpec
                        val ccId = ccSpec.getChaincodeId
                        logger.info(s"Upgrade for $channelName / ${ccId.getName}")
                        if (ServiceChainCodeName == ccId.getName) {
                            val args = ccSpec.getInput.getArgsList
                            onServiceUpgrade(
                                txEvent.getCreator.getMspid,
                                Util.codec.fromJson(args.get(1).toStringUtf8, classOf[Organization]),
                                Util.codec.fromJson(args.get(2).toStringUtf8, classOf[ServiceVersion])
                            )
                        }
                    }
                }
            }
        }
        ServiceWebSocketManager.broadcastText(s"new block ${blockEvent.getBlockNumber}")
    }

    def onServiceUpgrade(updaterMspId: String, organization: Organization, version: ServiceVersion): Unit = {
        logger.info(s"Detected service upgrade [ From $updaterMspId, Version: $version] ")
        val oldVersion = currentVersion
        currentVersion = version
        if (updaterMspId != config.organization.name) {
            logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
            val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))

            logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
            val chainCodeVersion = s"${version.chainCodeVersion}.${version.networkVersion}"
            network.installChainCode(ServiceChannelName, ServiceChainCodeName, chainCodeVersion, chainCodePkg)

            // fetch current network version
            logger.info(s"[ $organizationFullName ] - Warming up service chain code ...")
            implicit val timeout: OperationTimeout = OperationTimeout(5, TimeUnit.MINUTES)
            network
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
              .map(Util.codec.fromJson(_, classOf[ServiceVersion]))

            logger.info(s"[ $organizationFullName ] - Cleaning up old service chain codes ...")
            config.network.peerNodes.foreach { peer =>
                val previousVersion = s"${oldVersion.chainCodeVersion}.${oldVersion.networkVersion}"
                logger.info(s"Removing previous version [$previousVersion] of service on ${peer.name} ...")
                processManager.terminateChainCode(peer.name, ServiceChainCodeName, previousVersion)
            }
            hostsManager.addOrganization(organization)
        } else {
            logger.info("Skipping upgrade from self")
        }

    }
}
