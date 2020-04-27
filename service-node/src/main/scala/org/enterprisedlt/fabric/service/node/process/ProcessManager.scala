package org.enterprisedlt.fabric.service.node.process

import org.enterprisedlt.fabric.service.model.KnownHostRecord
import org.enterprisedlt.fabric.service.node.model.Box

import scala.collection.concurrent.TrieMap
import scala.util.Try

/**
  * @author Alexey Polubelov
  */
class ProcessManager {

    private val boxes: TrieMap[String, (ManagedBox, Box)] = TrieMap.empty[String, (ManagedBox, Box)]

    def listBoxes: Either[String, Array[Box]] = Try {
        boxes.values.toArray.map(_._2)
    }.toEither.left.map(_.getMessage)

    def getBoxAddress(box: String): Option[String] =
        Option(
            boxes
              .getOrElse(box, throw new Exception)
              ._2.boxAddress
        ).filter(_.trim.nonEmpty)


    def registerBox(box: ManagedBox, boxName: String, boxAddress: String): Unit = {
        boxes += boxName -> ((box, Box(boxName, boxAddress)))
    }


    def startOrderingNode(box: String, request: StartOSNRequest): Either[String, String] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_._1.startOrderingNode(request))


    def startPeerNode(box: String, request: StartPeerRequest): Either[String, String] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_._1.startPeerNode(request))


    def osnAwaitJoinedToRaft(box: String, osnFullName: String): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_._1.osnAwaitJoinedToRaft(osnFullName))


    def osnAwaitJoinedToChannel(box: String, osnFullName: String, channelName: String): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_._1.osnAwaitJoinedToChannel(osnFullName, channelName))


    def peerAwaitForBlock(box: String, peerFullName: String, blockNumber: Long): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_._1.peerAwaitForBlock(peerFullName, blockNumber))


    def terminateChainCode(box: String, peerName: String, chainCodeName: String, chainCodeVersion: String): Either[String, Unit] =
        boxes
          .get(box).toRight(s"Unknown box $box")
          .flatMap(_._1.terminateChainCode(peerName, chainCodeName, chainCodeVersion))

    def updateKnownHosts(hosts: Array[KnownHostRecord]): Either[String, Unit] = {
        import org.enterprisedlt.fabric.service.node.ConversionHelper._
        boxes.map { case (_, box) =>
            box._1.updateKnownHosts(hosts)
        }.fold2Either.map(_ => ())
    }
}
