package org.enterprisedlt.fabric.service.node.process

import org.enterprisedlt.fabric.service.node.rest.{Get, Post}

/**
 * @author Alexey Polubelov
 */
trait ManagedBox {

    @Post("/start-ordering-node")
    def startOrderingNode(request: StartOSNRequest): Either[String, String]

    @Post("/start-peer-node")
    def startPeerNode(request: StartPeerRequest): Either[String, String]

    @Get("/osn-await-joined-raft")
    def osnAwaitJoinedToRaft(osnFullName: String): Either[String, Unit]

    @Get("/osn-await-joined-channel")
    def osnAwaitJoinedToChannel(osnFullName: String, channelName: String): Either[String, Unit]

    @Get("/peer-await-for-block")
    def peerAwaitForBlock(peerFullName: String, blockNumber: Long): Either[String, Unit]

    @Get("/terminate-chain-code")
    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit]
}
