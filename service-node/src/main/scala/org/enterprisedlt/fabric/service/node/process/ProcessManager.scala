package org.enterprisedlt.fabric.service.node.process

import org.enterprisedlt.fabric.service.model.KnownHostRecord

import scala.collection.concurrent.TrieMap
import scala.util.Try

/**
  * @author Alexey Polubelov
  */
class ProcessManager {
    private val boxes = TrieMap.empty[String, ManagedBox]

    def listBoxes: Either[String, Array[String]] = Try {
        boxes.keys.toArray
    }.toEither.left.map(_.getMessage)

    def getBoxAddress(box: String): Either[String, Option[String]] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(
              _.getBoxAddress
                .map(addr =>
                    Option(addr).filter(_.nonEmpty)
                )
          )

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

    def updateKnownHosts(hosts: Array[KnownHostRecord]): Either[String, Unit] = {
        import org.enterprisedlt.fabric.service.node.ConversionHelper._
        boxes.map { case (_, box) =>
            box.updateKnownHosts(hosts)
        }.fold2Either.map(_ => ())
    }
}
