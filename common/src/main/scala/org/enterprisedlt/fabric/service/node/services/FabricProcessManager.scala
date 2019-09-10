package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.rest.Get

/**
  * @author Alexey Polubelov
  */
trait FabricProcessManager {

    @Get("/services/process-management/start-ordering-node")
    def startOrderingNode(name: String): Either[String, String]

    @Get("/services/process-management/start-peer-node")
    def startPeerNode(name: String): Either[String, String]

    @Get("/services/process-management/terminate-chaincode")
    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit]

    @Get("/services/process-management/osn-await-joined-to-raft")
    def osnAwaitJoinedToRaft(name: String): Either[String, Unit]

    @Get("/services/process-management/osn-await-joined-to-channel")
    def osnAwaitJoinedToChannel(name: String, channelName: String): Either[String, Unit]

    @Get("/services/process-management/peer-await-for-block")
    def peerAwaitForBlock(name: String, blockNumber: Long): Either[String, Unit]

}
