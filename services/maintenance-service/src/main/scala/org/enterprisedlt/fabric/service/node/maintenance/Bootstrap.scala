package org.enterprisedlt.fabric.service.node.maintenance

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, ServiceVersion}
import org.enterprisedlt.fabric.service.node.ExternalAddress
import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, ServiceConfig}
import org.enterprisedlt.fabric.service.node.constant.Constant.{DefaultConsortiumName, ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.services.{AdministrationManager, ProcessManagementManager}
import org.enterprisedlt.fabric.service.node.util.Util
import org.hyperledger.fabric.sdk.User
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Bootstrap {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def bootstrapOrganization(
        config: ServiceConfig,
        user: User,
        externalAddress: Option[ExternalAddress],
        administrationClient: AdministrationManager,
        processManagementManager: ProcessManagementManager
    )(
        bootstrapOptions: BootstrapOptions,
    ): Either[String, Unit] = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        logger.info(s"[ $organizationFullName ] - Generating certificates ...")

        //
        logger.info(s"[ $organizationFullName ] - Creating genesis ...")
        administrationClient.createGenesisBlock(bootstrapOptions)

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
        config.network.orderingNodes.foreach { osnConfig =>
            processManagementManager.startOrderingNode(osnConfig.name)
        }
        config.network.orderingNodes.foreach { osnConfig =>
            processManagementManager.osnAwaitJoinedToRaft(osnConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
        config.network.peerNodes.foreach { peerConfig =>
            processManagementManager.startPeerNode(peerConfig.name)
            administrationClient.definePeer(peerConfig)
        }

        //
        logger.info(s"[ $organizationFullName ] - Initializing network ...")
        config.network.orderingNodes.foreach { osnConfig =>
            administrationClient.defineOsn(osnConfig)
        }

        //
        logger.info(s"[ $organizationFullName ] - Creating channel ...")
        val createChannelRequest = CreateChannelRequest(ServiceChannelName, DefaultConsortiumName, config.organization.name)
        administrationClient.createChannel(createChannelRequest)

        //
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            val addPeerToChannelRequest = AddPeerToChannelRequest(ServiceChannelName, peerConfig.name)
            administrationClient.addPeerToChannel(addPeerToChannelRequest)
        }

        //
        logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            val addAnchorToChannelRequest = AddAnchorToChannelRequest(ServiceChannelName, peerConfig.name)
            administrationClient.addAnchorsToChannel(addAnchorToChannelRequest)
        }

        //
        logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
        logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
        val installChainCodeRequest = InstallChainCodeRequest(ServiceChannelName, ServiceChainCodeName, "1.0.0")
        administrationClient.installChainCode(installChainCodeRequest)

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

        val instantiateChainCodeRequest = InstantiateChainCodeRequest(ServiceChannelName, ServiceChainCodeName, "1.0.0", arguments = Array(
            Util.codec.toJson(organization),
            Util.codec.toJson(serviceVersion)
        ))

        administrationClient.instantiateChainCode(instantiateChainCodeRequest)

        //        network.setupBlockListener(ServiceChannelName, new NetworkMonitor(config, network, processManager, hostsManager, serviceVersion)) TODO

        //
        logger.info(s"[ $organizationFullName ] - Bootstrap done.")
        //        network
        Right(())
    }
}
