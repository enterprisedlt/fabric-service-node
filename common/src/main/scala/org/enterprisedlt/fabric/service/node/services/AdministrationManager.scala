package org.enterprisedlt.fabric.service.node.services

import java.io.InputStream
import java.util.concurrent.TimeUnit

import org.enterprisedlt.fabric.service.node.configuration.{OSNConfig, PeerConfig}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope}
import org.hyperledger.fabric.sdk._

/**
  * @author Maxim Fedin
  */
trait AdministrationManager {


    def definePeer(peerNode: PeerConfig): Either[String, Unit]

    def addPeerToChannel(channelName: String, peerName: String): Either[String, Peer]

    def addAnchorsToChannel(channelName: String, peerName: String): Either[String, Unit]

    def defineChannel(channelName: String): Either[String, Unit]

    def createChannel(channelName: String, channelTx: Envelope): Either[String, Unit]

    def defineOsn(osnConfig: OSNConfig): Either[String, Unit]

    def addOsnToChannel(osnName: String, cryptoPath: String, channelName: Option[String] = None): Either[String, Unit]

    def fetchLatestChannelBlock(channelName: String): Either[String, Block]

    def fetchLatestSystemBlock: Either[String, Block]

    def installChainCode(channelName: String, chainCodeName: String, version: String, chainCodeTarGzStream: InputStream): Either[String, java.util.Collection[ProposalResponse]]

    def instantiateChainCode(
        channelName: String, chainCodeName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    ): Either[String, BlockEvent#TransactionEvent]

    def upgradeChainCode(
        channelName: String, ccName: String, version: String,
        endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
        collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
        arguments: Array[String] = Array.empty
    )(implicit timeout: OperationTimeout = OperationTimeout(5, TimeUnit.MINUTES)): Either[String, Unit]

    // def joinToNetwork(joinRequest: JoinRequest): Unit TODO

    // def joinToChannel(channelName: String, joinRequest: JoinRequest): Either[String, Unit] TODO

}

case class OperationTimeout(
    value: Long,
    timeUnit: TimeUnit = TimeUnit.MILLISECONDS
) {
    def milliseconds: Long = timeUnit.toMillis(value)
}
