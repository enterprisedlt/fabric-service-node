package org.enterprisedlt.fabric.service.node.flow

import java.io.{BufferedInputStream, FileInputStream}
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, ServiceVersion}
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, DockerConfig, OrganizationConfig}
import org.enterprisedlt.fabric.service.node.cryptography.{FabricCryptoMaterial, Orderer, Peer}
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.model.FabricServiceState
import org.enterprisedlt.fabric.service.node.process._
import org.enterprisedlt.fabric.service.node.proto._
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
        processConfig: DockerConfig,
        state: AtomicReference[FabricServiceState]
    ): GlobalState = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        //
        logger.info(s"[ $organizationFullName ] - Starting process manager ...")
        val componentsPath = s"$profilePath/components"
        val processManager = new DockerBoxManager(
            hostPath = componentsPath,
            containerName = s"service.$organizationFullName",
            networkName = bootstrapOptions.networkName,
            processConfig
        )
        //
        val componentsCrypto = bootstrapOptions.network.orderingNodes.map { osnConfig =>
            val componentCrypto = cryptography.generateComponentCrypto(Orderer, osnConfig.name)
            cryptography.saveComponentCrypto(Orderer, osnConfig.name, componentCrypto)
            osnConfig.name -> componentCrypto
        }.toMap

        logger.info(s"[ $organizationFullName ] - Creating genesis ...")
        state.set(FabricServiceState(FabricServiceState.BootstrapCreatingGenesis))

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

        state.set(FabricServiceState(FabricServiceState.BootstrapStartingOrdering))
        bootstrapOptions.network.orderingNodes.foreach { osnConfig =>
            processManager.startOrderingNode(
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
        state.set(FabricServiceState(FabricServiceState.BootstrapAwaitingOrdering))
        bootstrapOptions.network.orderingNodes.foreach { osnConfig =>
            processManager.osnAwaitJoinedToRaft(osnConfig.name)
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
        state.set(FabricServiceState(FabricServiceState.BootstrapStartingPeers))
        bootstrapOptions.network.peerNodes.foreach { peerConfig =>
            val componentCrypto = cryptography.generateComponentCrypto(Peer, peerConfig.name)
            cryptography.saveComponentCrypto(Peer, peerConfig.name, componentCrypto)
            processManager.startPeerNode(
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
        state.set(FabricServiceState(FabricServiceState.BootstrapCreatingServiceChannel))
        network.createChannel(ServiceChannelName, FabricChannel.CreateChannel(ServiceChannelName, DefaultConsortiumName, organizationConfig.name))

        //
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        state.set(FabricServiceState(FabricServiceState.BootstrapAddingPeersToChannel))
        bootstrapOptions.network.peerNodes.foreach { peerConfig =>
            network.addPeerToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
        state.set(FabricServiceState(FabricServiceState.BootstrapUpdatingAnchors))
        bootstrapOptions.network.peerNodes.foreach { peerConfig =>
            network.addAnchorsToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        state.set(FabricServiceState(FabricServiceState.BootstrapInstallingServiceChainCode))
        logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
        val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))

        logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
        network.installChainCode(
            ServiceChannelName,
            ServiceChainCodeName,
            "1.0.0",
            "java",
            chainCodePkg)

        //
        state.set(FabricServiceState(FabricServiceState.BootstrapInitializingServiceChainCode))
        logger.info(s"[ $organizationFullName ] - Instantiating service chain code ...")
        val organization =
            Organization(
                mspId = organizationConfig.name,
                name = organizationConfig.name,
                memberNumber = 1,
                knownHosts = externalAddress.map { address =>
                    bootstrapOptions.network.orderingNodes.map(osn => KnownHostRecord(address.host, osn.name)) ++
                      bootstrapOptions.network.peerNodes.map(peer => KnownHostRecord(address.host, peer.name)) :+
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
            "1.0.0", // {chainCodeVersion}.{networkVersion},
            "java",
            arguments = Array(
                Util.codec.toJson(organization),
                Util.codec.toJson(serviceVersion)
            )
        )

        state.set(FabricServiceState(FabricServiceState.BootstrapSettingUpBlockListener))
        network.setupBlockListener(ServiceChannelName, new NetworkMonitor(organizationConfig, bootstrapOptions.network, network, processManager, hostsManager, serviceVersion))

        //
        logger.info(s"[ $organizationFullName ] - Bootstrap done.")
        state.set(FabricServiceState(FabricServiceState.Ready))
        GlobalState(network, processManager, bootstrapOptions.network, bootstrapOptions.networkName)
    }
}
