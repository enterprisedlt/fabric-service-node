package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.rest.Get

/**
  * @author Alexey Polubelov
  */
trait FabricProcessManager {

    @Get("/start-ordering-node")
    def startOrderingNode(name: String): Either[String, String]

    @Get("/start-peer-node")
    def startPeerNode(name: String): Either[String, String]

    @Get("/terminate-chaincode")
    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit]

    @Get("/osn-await-joined-to-raft")
    def osnAwaitJoinedToRaft(name: String): Either[String, Unit]

    @Get("/osn-await-joined-to-channel")
    def osnAwaitJoinedToChannel(name: String, channelName: String): Either[String, Unit]

    @Get("/peer-await-for-block")
    def peerAwaitForBlock(name: String, blockNumber: Long): Either[String, Unit]

}
