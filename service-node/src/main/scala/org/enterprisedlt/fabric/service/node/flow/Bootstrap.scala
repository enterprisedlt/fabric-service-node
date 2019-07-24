package org.enterprisedlt.fabric.service.node.flow

import java.io.{BufferedInputStream, File, FileInputStream}

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, ServiceVersion}
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.proto._
import org.enterprisedlt.fabric.service.node._
import org.hyperledger.fabric.protos.common.MspPrincipal.MSPRole
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Bootstrap {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def bootstrapOrganization(
        config: ServiceConfig,
        cryptography: CryptoManager,
        processManager: FabricProcessManager,
        hostsManager: HostsManager,
        externalAddress: Option[ExternalAddress]
    ): FabricNetworkManager = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        logger.info(s"[ $organizationFullName ] - Generating certificates ...")

        //
        logger.info(s"[ $organizationFullName ] - Creating genesis ...")
        val genesisDefinition = Genesis.newDefinition("/opt/profile", config)
        val genesis = FabricBlock.create(genesisDefinition)
        Util.storeToFile("/opt/profile/artifacts/genesis.block", genesis)

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
        config.network.orderingNodes.foreach { osnConfig =>
            processManager.startOrderingNode(osnConfig.name)
        }
        config.network.orderingNodes.foreach { osnConfig =>
            processManager.osnAwaitJoinedToRaft(osnConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
        config.network.peerNodes.foreach { peerConfig =>
            processManager.startPeerNode(peerConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Initializing network ...")
        val admin = cryptography.loadAdmin
        val network = new FabricNetworkManager(config, admin)

        //
        logger.info(s"[ $organizationFullName ] - Creating channel ...")
        network.createChannel(ServiceChannelName, FabricChannel.CreateChannel(ServiceChannelName, DefaultConsortiumName, config.organization.name))

        //
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            network.addPeerToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            network.addAnchorsToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
        val chainCodePkg = new BufferedInputStream(new FileInputStream("/opt/service-chain-code/chain-code.tgz"))

        logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
        network.installChainCode(ServiceChannelName, ServiceChainCodeName, "1.0.0", chainCodePkg)

        //
        logger.info(s"[ $organizationFullName ] - Instantiating service chain code ...")
        val organization =
            Organization(
                mspId = config.organization.name,
                name = config.organization.name,
                memberNumber = 1,
                knownHosts = externalAddress.map { address =>
                    config.network.orderingNodes.map(osn => KnownHostRecord(address.host, s"${osn.name}.$organizationFullName")) ++
                      config.network.peerNodes.map(peer => KnownHostRecord(address.host, s"${peer.name}.$organizationFullName")) :+
                      KnownHostRecord(address.host, s"service.$organizationFullName")
                }
                  .getOrElse(Array.empty)
            )
        val serviceVersion =
            ServiceVersion(
                chainCodeVersion = "1.0",
                networkVersion = "0"
            )

        network.instantiateChainCode(
            ServiceChannelName, ServiceChainCodeName,
            "1.0.0", // {chainCodeVersion}.{networkVersion}
            arguments = Array(
                Util.codec.toJson(organization),
                Util.codec.toJson(serviceVersion)
            )
        )

        network.setupBlockListener(ServiceChannelName, new NetworkMonitor(config, network, processManager, hostsManager, serviceVersion))

        //
        logger.info(s"[ $organizationFullName ] - Bootstrap done.")
        network
    }
}
