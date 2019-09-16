package org.enterprisedlt.fabric.service.node

import java.io.{BufferedInputStream, FileInputStream}

import org.enterprisedlt.fabric.service.node.client.FabricNetworkManager
import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, PeerConfig, ServiceConfig}
import org.enterprisedlt.fabric.service.node.constant.Constant._
import org.enterprisedlt.fabric.service.node.genesis.Genesis
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.proto.{FabricBlock, FabricChannel}
import org.enterprisedlt.fabric.service.node.services.AdministrationManager
import org.enterprisedlt.fabric.service.node.util.{PrivateCollectionConfiguration, Util}
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
class AdministrationRestEndpoint(
    fabricNetworkManager: FabricNetworkManager,
    config: ServiceConfig,
    cryptoPath: String)
  extends AdministrationManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def definePeer(peerNode: PeerConfig): Either[String, Unit] = {
        logger.info(peerNode.toString)
        fabricNetworkManager.definePeer(peerNode)
    }

    override def addPeerToChannel(request: AddPeerToChannelRequest): Either[String, Unit] = {
        logger.info(s"Adding peer to channel ...")
        fabricNetworkManager.addPeerToChannel(request.channelName, request.peer)
        Right(())
    }

    override def addAnchorsToChannel(request: AddAnchorToChannelRequest): Either[String, Unit] = {
        logger.info(s"Adding anchors to channel...")
        fabricNetworkManager.addAnchorsToChannel(request.channelName, request.peerName)
    }

    override def defineChannel(channelName: String): Either[String, Unit] = {
        logger.info(s"Defining channel...")
        fabricNetworkManager.defineChannel(channelName)
        Right(())
    }

    override def createChannel(channel: CreateChannelRequest): Either[String, Unit] = {
        logger.info(s"Creating channel ...")
        val envelope = FabricChannel.CreateChannel(channel.channelName, channel.consortiumName, channel.orgName)
        fabricNetworkManager.createChannel(channel.channelName, envelope)
    }

    override def defineOsn(osnDescriptor: OSNDescriptor): Either[String, Unit] =
        fabricNetworkManager.defineOsn(osnDescriptor)


    override def fetchLatestChannelBlock(channelName: String): Either[String, Array[Byte]] = {
        logger.info(s"Fetching latest channel block ...")
        fabricNetworkManager.fetchLatestChannelBlock(channelName).map(_.toByteArray)
    }

    override def fetchLatestSystemBlock: Either[String, Array[Byte]] = {
        logger.info(s"Fetching latest system block ...")
        fabricNetworkManager.fetchLatestSystemBlock.map(_.toByteArray)
    }

    override def installChainCode(request: InstallChainCodeRequest): Either[String, Unit] = {
        logger.info(s"Preparing service chain code ...")
        val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))
        logger.info(s"Installing chaincode ...")
        fabricNetworkManager.installChainCode(request.channelName,
            request.chainCodeName, request.chainCodeVersion, chainCodePkg)
        Right(())
    }

    override def instantiateChainCode(request: InstantiateChainCodeRequest): Either[String, Unit] = {
        logger.info(s"Instantiating chaincode ...")
        val endorsementPolicy = Option(request.endorsement).map(Util.policyAnyOf(_))
        val collections = Option(request.collections).map(
            _.map { cd =>
                PrivateCollectionConfiguration(
                    name = cd.name,
                    memberIds = cd.members)
            }
        ).map(Util.createCollectionsConfig(_))
        fabricNetworkManager.instantiateChainCode(request.channelName,
            request.chainCodeName, request.version, endorsementPolicy,
            collections, Option(request.arguments).getOrElse(Array.empty))
        Right(())
    }

    override def upgradeChainCode(request: InstantiateChainCodeRequest): Either[String, Unit] = {
        logger.info(s"Upgrading chaincode ...")
        val endorsementPolicy = Option(request.endorsement).map(Util.policyAnyOf(_))
        val collections = Option(request.collections).map(
            _.map { cd =>
                PrivateCollectionConfiguration(
                    name = cd.name,
                    memberIds = cd.members)
            }
        ).map(Util.createCollectionsConfig(_))
        fabricNetworkManager.upgradeChainCode(request.channelName,
            request.chainCodeName, request.version, endorsementPolicy,
            collections, request.arguments)
        Right(())
    }

    override def addOrgToConsortium(request: AddOrgToConsortiumRequest): Either[String, Unit] = {
        logger.info(s"Adding org to consortium ...")

        fabricNetworkManager.addOrgToChannel(request.organization, request.organizationCertificates)
    }

    override def addOsnToConsortium(osnName: String): Either[String, Unit] = {
        logger.info(s"Adding osn to consortium ...")
        fabricNetworkManager.addOsnToChannel(osnName)
        Right(())
    }

    override def addOrgToChannel(request: AddOrgToChannelRequest): Either[String, Unit] = {
        logger.info(s"Adding org to channel ...")

        fabricNetworkManager.addOrgToChannel(request.organization, request.organizationCertificates, Option(request.channelName))
    }

    override def addOsnToChannel(request: AddOsnToChannelRequest): Either[String, Unit] = {
        logger.info(s"Adding osn to channel ...")
        fabricNetworkManager.addOsnToChannel(request.osnName, Option(request.channelName))
        Right(())
    }

    override def createGenesisBlock(bootstrapOptions: BootstrapOptions): Either[String, Unit] = {
        logger.info(s"Creating genesis ...")
        val genesisDefinition = Genesis.newDefinition(cryptoPath, config, bootstrapOptions)
        val genesis = FabricBlock.create(genesisDefinition, bootstrapOptions)
        Util.storeToFile("/opt/profile/artifacts/genesis.block", genesis)
        Right(())
    }
}
