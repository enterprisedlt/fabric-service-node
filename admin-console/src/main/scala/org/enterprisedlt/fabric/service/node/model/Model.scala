package org.enterprisedlt.fabric.service.node.model

import scala.scalajs.js

/**
  * @author Alexey Polubelov
  */

class BootstrapOptions(
    val block: BlockConfig,
    val raft: RaftConfig,
    val networkName: String,
    val network: NetworkConfig
) extends js.Object {
    def copy(
        block: BlockConfig = this.block,
        raft: RaftConfig = this.raft,
        networkName: String = this.networkName,
        network: NetworkConfig = this.network
    ): BootstrapOptions =
        new BootstrapOptions(block, raft, networkName, network)
}

object BootstrapOptions {
    val Defaults: BootstrapOptions =
        new BootstrapOptions(
            networkName = "",
            block = new BlockConfig(
                maxMessageCount = 150,
                absoluteMaxBytes = 103809024,
                preferredMaxBytes = 524288,
                batchTimeOut = "1s"
            ),
            raft = new RaftConfig(
                tickInterval = "500ms",
                electionTick = 10,
                heartbeatTick = 1,
                maxInflightBlocks = 5,
                snapshotIntervalSize = 20971520
            ),
            network = new NetworkConfig(
                orderingNodes = new js.Array[OSNConfig](),
                peerNodes = new js.Array[PeerConfig]()
            )
        )
}

class JoinOptions(
    network: NetworkConfig,
    invite: Invite
) extends js.Object

object JoinOptions {
    val Defaults: JoinOptions =
        new JoinOptions(
            network = new NetworkConfig(
                orderingNodes = new js.Array[OSNConfig](),
                peerNodes = js.Array[PeerConfig](
                    new PeerConfig(
                        name = "peer0",
                        port = 7014,
                        couchDB = new CouchDBConfig(
                            port = 7015
                        )))),
            invite = new Invite(
                networkName = "",
                address = "",
                key = ""
            )
        )
}

class BlockConfig(
    val maxMessageCount: Int,
    val absoluteMaxBytes: Int,
    val preferredMaxBytes: Int,
    val batchTimeOut: String
) extends js.Object {
    def copy(
        maxMessageCount: Int = this.maxMessageCount,
        absoluteMaxBytes: Int = this.absoluteMaxBytes,
        preferredMaxBytes: Int = this.preferredMaxBytes,
        batchTimeOut: String = this.batchTimeOut
    ): BlockConfig =
        new BlockConfig(maxMessageCount, absoluteMaxBytes, preferredMaxBytes, batchTimeOut)
}

class RaftConfig(
    val tickInterval: String,
    val electionTick: Int,
    val heartbeatTick: Int,
    val maxInflightBlocks: Int,
    val snapshotIntervalSize: Int
) extends js.Object

class NetworkConfig(
    val orderingNodes: js.Array[OSNConfig],
    val peerNodes: js.Array[PeerConfig]
) extends js.Object

class OSNConfig(
    val name: String,
    val port: Int
) extends js.Object

class PeerConfig(
    val name: String,
    val port: Int,
    val couchDB: CouchDBConfig
) extends js.Object

class CouchDBConfig(
    val port: Int
) extends js.Object

class Invite(
    networkName: String,
    address: String,
    key: String
) extends js.Object
