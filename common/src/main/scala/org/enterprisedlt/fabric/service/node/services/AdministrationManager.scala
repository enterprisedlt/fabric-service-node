package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, OSNConfig, PeerConfig}
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.rest.{Get, Post}

/**
  * @author Maxim Fedin
  */
trait AdministrationManager {

    @Post("/define-peer")
    def definePeer(peerNode: PeerConfig): Either[String, Unit]

    @Post("/add-peer-to-channel")
    def addPeerToChannel(request: AddPeerToChannelRequest): Either[String, Unit]

    @Post("/add-anchors-to-channel")
    def addAnchorsToChannel(request: AddAnchorToChannelRequest): Either[String, Unit]

    @Get("/define-channel")
    def defineChannel(channelName: String): Either[String, Unit]

    @Post("/create-channel")
    def createChannel(request: CreateChannelRequest): Either[String, Unit]

    @Post("/define-osn")
    def defineOsn(osnConfig: OSNConfig): Either[String, Unit]

    @Get("/fetch-latest-channel-block")
    def fetchLatestChannelBlock(channelName: String): Either[String, Array[Byte]]

    @Get("/fetch-latest-system-block")
    def fetchLatestSystemBlock: Either[String, Array[Byte]]

    @Post("/install-chaincode")
    def installChainCode(request: InstallChainCodeRequest): Either[String, Unit]

    @Post("/instantiate-chaincode")
    def instantiateChainCode(request: InstantiateChainCodeRequest): Either[String, Unit]

    @Post("/upgrade-chaincode")
    def upgradeChainCode(request: InstantiateChainCodeRequest): Either[String, Unit]

    @Post("/add-org-to-consortium")
    def addOrgToConsortium(request: AddOrgToConsortiumRequest): Either[String, Unit]

    @Get("/add-osn-to-consortium")
    def addOsnToConsortium(osnName: String): Either[String, Unit]

    @Post("/add-org-to-channel")
    def addOrgToChannel(request: AddOrgToChannelRequest): Either[String, Unit]

    @Post("/add-osn-to-channel")
    def addOsnToChannel(request: AddOsnToChannelRequest): Either[String, Unit]

    @Post("/create-genesis-block")
    def createGenesisBlock(bootstrapOptions: BootstrapOptions): Either[String, Unit]

}
