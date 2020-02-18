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
) extends js.Object

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
) extends js.Object

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