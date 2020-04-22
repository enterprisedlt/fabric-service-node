package org.enterprisedlt.fabric.service.node.process

import scala.collection.concurrent.TrieMap

/**
 * @author Alexey Polubelov
 */
class ProcessManager {
    private val boxes = TrieMap.empty[String, ManagedBox]

    def registerBox(boxName: String, box: ManagedBox): Unit =
        boxes += boxName -> box

    def startOrderingNode(box: String, request: StartOSNRequest): Either[String, String] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_.startOrderingNode(request))


    def startPeerNode(box: String, request: StartPeerRequest): Either[String, String] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_.startPeerNode(request))


    def osnAwaitJoinedToRaft(box: String, osnFullName: String): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_.osnAwaitJoinedToRaft(osnFullName))


    def osnAwaitJoinedToChannel(box: String, osnFullName: String, channelName: String): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_.osnAwaitJoinedToChannel(osnFullName, channelName))


    def peerAwaitForBlock(box: String, peerFullName: String, blockNumber: Long): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_.peerAwaitForBlock(peerFullName, blockNumber))


    def terminateChainCode(box: String, peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_.terminateChainCode(peerName, chainCodeName, chainCodeVersion))

}
