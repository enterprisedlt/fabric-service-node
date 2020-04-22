package org.enterprisedlt.fabric.service.node.process

/**
 * @author Alexey Polubelov
 */
trait ManagedBox {

    def startOrderingNode(request: StartOSNRequest): Either[String, String]

    def startPeerNode(request: StartPeerRequest): Either[String, String]

    def osnAwaitJoinedToRaft(osnFullName: String): Either[String, Unit]

    def osnAwaitJoinedToChannel(osnFullName: String, channelName: String): Either[String, Unit]

    def peerAwaitForBlock(peerFullName: String, blockNumber: Long): Either[String, Unit]

    def terminateChainCode(peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit]
}
