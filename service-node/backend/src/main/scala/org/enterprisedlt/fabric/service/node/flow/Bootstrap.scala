package org.enterprisedlt.fabric.service.node.flow

import java.io.{BufferedInputStream, FileInputStream}
import java.nio.charset.StandardCharsets
import java.util.Base64

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, ServiceVersion}
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig
import org.enterprisedlt.fabric.service.node.cryptography.{FabricCryptoMaterial, Orderer, Peer}
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.model.{CCLanguage, FabricServiceStateHolder}
import org.enterprisedlt.fabric.service.node.process._
import org.enterprisedlt.fabric.service.node.proto._
import org.enterprisedlt.fabric.service.node.shared.{BootstrapOptions, FabricServiceState}
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */
object Bootstrap {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def bootstrapOrganization(
        organizationConfig: OrganizationConfig,
        bootstrapOptions: BootstrapOptions,
        cryptography: CryptoManager,
        hostsManager: HostsManager,
        externalAddress: Option[ExternalAddress],
        profilePath: String,
        processManager: ProcessManager,
    ): GlobalState = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        //
        val componentsCrypto = bootstrapOptions.network.orderingNodes.map { osnConfig =>
            val componentCrypto = cryptography.generateComponentCrypto(Orderer, osnConfig.name)
            cryptography.saveComponentCrypto(Orderer, osnConfig.name, componentCrypto)
            osnConfig.name -> componentCrypto
        }.toMap

        logger.info(s"[ $organizationFullName ] - Generating list of known hosts ...")
        import ConversionHelper._
        val knownHosts =
            (
              bootstrapOptions.network.orderingNodes.map(osn => osn.name -> osn.box) ++
                bootstrapOptions.network.peerNodes.map(peer => peer.name -> peer.box)
              )
              .map { case (name, box) =>
                  processManager.getBoxAddress(box).map {
                      _.map { boxAddress =>
                          KnownHostRecord(
                              dnsName = name,
                              ipAddress = boxAddress
                          )
                      }
                  }
              }.toSeq.fold2Either.map { list =>
                (list.toSeq :+ externalAddress.map { serviceAddress =>
                    KnownHostRecord(
                        dnsName = s"service.$organizationFullName",
                        ipAddress = serviceAddress.host
                    )
                }).flatten
            }
              .left.map { msg =>
                logger.error(s"Failed to obtain known hosts list: $msg")
            }
              .getOrElse {
                  throw new Exception(s"Failed to get known hosts list")
              }.toArray

        logger.info(s"List of known hosts: ${knownHosts.toSeq}")
        hostsManager.updateHosts(knownHosts)
        processManager.updateKnownHosts(knownHosts)

        logger.info(s"[ $organizationFullName ] - Creating genesis ...")
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapCreatingGenesis))

        val genesisDefinition = Genesis.newDefinition("/opt/profile", organizationConfig, bootstrapOptions)
        val genesis = FabricBlock.create(genesisDefinition, bootstrapOptions)
        Util.storeToFile("/opt/profile/artifacts/genesis.block", genesis)

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")

        val organizationInfo =
            process.OrganizationConfig(
                mspId = organizationConfig.name,
                fullName = organizationFullName,
                cryptoMaterial = cryptography.getOrgCryptoMaterialPem
            )

        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapStartingOrdering))
        bootstrapOptions.network.orderingNodes.foreach { osnConfig =>
            processManager.startOrderingNode(
                osnConfig.box,
                StartOSNRequest(
                    port = osnConfig.port,
                    genesis = new String(Base64.getEncoder.encode(genesis.toByteArray), StandardCharsets.UTF_8),
                    organization = organizationInfo,
                    component = ComponentConfig(
                        fullName = osnConfig.name,
                        cryptoMaterial = ComponentCryptoMaterialPEM(
                            msp = FabricCryptoMaterial.asPem(componentsCrypto(osnConfig.name).componentCert),
                            tls = FabricCryptoMaterial.asPem(componentsCrypto(osnConfig.name).componentTLSCert)
                        )
                    )
                )
            )
        }
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapAwaitingOrdering))
        bootstrapOptions.network.orderingNodes.foreach { osnConfig =>
            processManager.osnAwaitJoinedToRaft(osnConfig.box, osnConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Initializing network ...")
        val admin = cryptography.loadDefaultAdmin
        val network = new FabricNetworkManager(organizationConfig, bootstrapOptions.network.orderingNodes.head, admin)
        //
        bootstrapOptions.network.orderingNodes.tail.foreach { osnConfig =>
            network.defineOsn(osnConfig)
        }

        logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapStartingPeers))
        bootstrapOptions.network.peerNodes.foreach { peerConfig =>
            val componentCrypto = cryptography.generateComponentCrypto(Peer, peerConfig.name)
            cryptography.saveComponentCrypto(Peer, peerConfig.name, componentCrypto)
            processManager.startPeerNode(
                peerConfig.box,
                StartPeerRequest(
                    port = peerConfig.port,
                    organization = organizationInfo,
                    component = ComponentConfig(
                        fullName = peerConfig.name,
                        cryptoMaterial = ComponentCryptoMaterialPEM(
                            msp = FabricCryptoMaterial.asPem(componentCrypto.componentCert),
                            tls = FabricCryptoMaterial.asPem(componentCrypto.componentTLSCert)
                        )
                    )
                )
            )
            network.definePeer(peerConfig)
        }

        //
        logger.info(s"[ $organizationFullName ] - Creating channel ...")
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapCreatingServiceChannel))
        network.createChannel(ServiceChannelName, FabricChannel.CreateChannel(ServiceChannelName, DefaultConsortiumName, organizationConfig.name))

        //
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapAddingPeersToChannel))
        bootstrapOptions.network.peerNodes.foreach { peerConfig =>
            network.addPeerToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapUpdatingAnchors))
        bootstrapOptions.network.peerNodes.foreach { peerConfig =>
            network.addAnchorsToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapInstallingServiceChainCode))
        logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
        val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))

        logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
        network.installChainCode(
            ServiceChannelName,
            ServiceChainCodeName,
            "1.0.0",
            CCLanguage.SCALA,
            chainCodePkg)

        //
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapInitializingServiceChainCode))
        logger.info(s"[ $organizationFullName ] - Instantiating service chain code ...")
        val organization =
            Organization(
                mspId = organizationConfig.name,
                name = organizationConfig.name,
                memberNumber = 1,
                knownHosts = knownHosts
            )
        val serviceVersion =
            ServiceVersion(
                chainCodeVersion = "1.0",
                networkVersion = "0"
            )

        network.instantiateChainCode(
            ServiceChannelName, ServiceChainCodeName,
            "1.0.0", // {chainCodeVersion}.{networkVersion},
            "java",
            arguments = Array(
                Util.codec.toJson(organization),
                Util.codec.toJson(serviceVersion)
            )
        )

        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapSettingUpBlockListener))
        network.setupBlockListener(ServiceChannelName, new NetworkMonitor(organizationConfig, bootstrapOptions.network, network, processManager, hostsManager, serviceVersion))

        //
        logger.info(s"[ $organizationFullName ] - Bootstrap done.")
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.Ready))
        GlobalState(network, bootstrapOptions.network, bootstrapOptions.networkName)
    }
}
