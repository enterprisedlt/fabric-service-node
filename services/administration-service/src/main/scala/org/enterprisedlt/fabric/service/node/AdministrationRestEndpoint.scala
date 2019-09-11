package org.enterprisedlt.fabric.service.node

import java.io.{BufferedInputStream, FileInputStream}

import org.enterprisedlt.fabric.service.node.client.FabricNetworkManager
import org.enterprisedlt.fabric.service.node.configuration.{OSNConfig, PeerConfig}
import org.enterprisedlt.fabric.service.node.constant.Constant._
import org.enterprisedlt.fabric.service.node.genesis.Genesis
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.proto.{FabricBlock, FabricChannel}
import org.enterprisedlt.fabric.service.node.services.AdministrationManager
import org.enterprisedlt.fabric.service.node.util.Util
import org.slf4j.LoggerFactory


/**
  * @author Maxim Fedin
  */
class AdministrationRestEndpoint(fabricNetworkManager: FabricNetworkManager) extends AdministrationManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def definePeer(peerNode: PeerConfig): Either[String, Unit] =
        fabricNetworkManager.definePeer(peerNode)

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

    override def defineOsn(osnConfig: OSNConfig): Either[String, Unit] =
        fabricNetworkManager.defineOsn(osnConfig)

    override def addOsnToChannel(request: AddOsnToChannelRequest): Either[String, Unit] = {
        logger.info(s"Adding osn to channel ...")
        fabricNetworkManager.addOsnToChannel(request.channelName, request.cryptoPath, Option(request.osnName))
        Right(())
    }

    override def fetchLatestChannelBlock(channelName: String): Either[String, Array[Byte]] = {
        logger.info(s"Fetching latest channel block ...")
        fabricNetworkManager.fetchLatestChannelBlock(channelName).map(_.toByteArray)
    }

    override def fetchLatestSystemBlock: Either[String, Array[Byte]] = {
        logger.info(s"Fetching latest system block ...")
        fabricNetworkManager.fetchLatestSystemBlock.map(_.toByteArray)
    }

    override def installChainCode(request: InstallChainCodeRequest): Either[String, Unit] = {
        logger.info(s"Installing chaincode ...")
        val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))
        fabricNetworkManager.installChainCode(request.channelName,
            request.chainCodeName, request.chainCodeVersion, chainCodePkg)
        Right(())
    }

    override def instantiateChainCode(request: InstantiateChainCodeRequest): Either[String, Unit] = {
        logger.info(s"Instantiating chaincode ...")
        fabricNetworkManager.instantiateChainCode(request.channelName,
            request.chainCodeName, request.version, request.endorsementPolicy,
            request.collectionConfig, request.arguments)
        Right(())
    }

    override def upgradeChainCode(request: InstantiateChainCodeRequest): Either[String, Unit] = {
        logger.info(s"Upgrading chaincode ...")
        fabricNetworkManager.upgradeChainCode(request.channelName,
            request.chainCodeName, request.version, request.endorsementPolicy,
            request.collectionConfig, request.arguments)
        Right(())
    }

    override def joinToNetwork(request: JoinRequest): Either[String, Unit] = {
        logger.info(s"Joining to network ...")
        fabricNetworkManager.joinToNetwork(request)
    }

    override def joinToChannel(request: JoinToChannelRequest): Either[String, Unit] = {
        logger.info(s"Joining to channel ...")
        fabricNetworkManager.joinToChannel(request.channelName, request.joinRequest)
    }

    override def createGenesisBlock(request: CreateBlockRequest): Either[String, Unit] = {
        logger.info(s"Creating genesis ...")
        val genesisDefinition = Genesis.newDefinition(request.profilePath, request.config, request.bootstrapOptions)
        val genesis = FabricBlock.create(genesisDefinition, request.bootstrapOptions)
        Util.storeToFile("/opt/profile/artifacts/genesis.block", genesis)
        Right(())
    }
}
