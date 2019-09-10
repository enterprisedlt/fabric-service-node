package org.enterprisedlt.fabric.service.node.services

import java.io.InputStream

import org.enterprisedlt.fabric.service.node.configuration.{OSNConfig, PeerConfig}
import org.enterprisedlt.fabric.service.node.model.JoinRequest
import org.enterprisedlt.fabric.service.node.rest.{Get, Post}
import org.hyperledger.fabric.protos.common.Common.Envelope
import org.hyperledger.fabric.sdk._

/**
  * @author Maxim Fedin
  */
trait AdministrationManager {

    @Post("/services/administration/define-peer")
    def definePeer(peerNode: PeerConfig): Either[String, Unit]

    @Get("/services/administration/add-peer-to-channel")
    def addPeerToChannel(channelName: String, peerName: String): Either[String, Array[Byte]]

    @Get("/services/administration/add-anchors-to-channel")
    def addAnchorsToChannel(channelName: String, peerName: String): Either[String, Unit]

    @Get("/services/administration/define-channel")
    def defineChannel(channelName: String): Either[String, Unit]

    @Post("/services/administration/create-channel")
    def createChannel(channelName: String, channelTx: Envelope): Either[String, Unit]

    @Post("/services/administration/define-osn")
    def defineOsn(osnConfig: OSNConfig): Either[String, Unit]

    @Post("/services/administration/add-osn-to-channel")
    def addOsnToChannel(osnName: String, cryptoPath: String, channelName: String): Either[String, Unit]

    @Post("/services/administration/fetch-latest-channel-block")
    def fetchLatestChannelBlock(channelName: String): Either[String, Array[Byte]]

    @Post("/services/administration/fetch-latest-system-block")
    def fetchLatestSystemBlock: Either[String, Array[Byte]]

    @Post("/services/administration/install-chaincode")
    def installChainCode(channelName: String, chainCodeName: String, version: String, chainCodeTarGzStream: InputStream): Either[String, Array[Byte]]

    @Post("/services/administration/instantiate-chaincode")
    def instantiateChainCode(
        channelName: String, chainCodeName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    ): Either[String, Array[Byte]]

    @Post("/services/administration/upgrade-chaincode")
    def upgradeChainCode(
        channelName: String, ccName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    ): Either[String, Unit]

    @Post("/services/administration/join-to-network")
    def joinToNetwork(joinRequest: JoinRequest): Either[String, Unit]

    @Post("/services/administration/join-to-channel")
    def joinToChannel(channelName: String, joinRequest: JoinRequest): Either[String, Unit]

}
