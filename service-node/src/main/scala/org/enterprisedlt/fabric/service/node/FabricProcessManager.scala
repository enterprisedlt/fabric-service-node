package org.enterprisedlt.fabric.service.node

/**
  * @author Alexey Polubelov
  */
trait FabricProcessManager {
    def startOrderingNode(name: String, port: Int): String

    def osnAwaitJoinedToRaft(name: String): Unit

    def osnAwaitJoinedToChannel(name: String, channelName: String): Unit

    def startPeerNode(name: String, port: Int): String

    def peerAwaitForBlock(name: String, blockNumber: Long): Unit

    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Unit
}
