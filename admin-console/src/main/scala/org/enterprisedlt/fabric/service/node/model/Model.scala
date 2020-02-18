package org.enterprisedlt.fabric.service.node.model

import monocle.macros.Lenses
import upickle.default.{ReadWriter => RW, macroRW}

/**
 * @author Alexey Polubelov
 */

@Lenses case class BootstrapOptions(
    block: BlockConfig,
    raft: RaftConfig,
    networkName: String,
    network: NetworkConfig
)

object BootstrapOptions {
    val Defaults: BootstrapOptions =
        new BootstrapOptions(
            networkName = "test_net",
            block = BlockConfig(
                maxMessageCount = 150,
                absoluteMaxBytes = 103809024,
                preferredMaxBytes = 524288,
                batchTimeOut = "1s"
            ),
            raft = RaftConfig(
                tickInterval = "500ms",
                electionTick = 10,
                heartbeatTick = 1,
                maxInflightBlocks = 5,
                snapshotIntervalSize = 20971520
            ),
            network = NetworkConfig(
                orderingNodes = Array.empty[OSNConfig],
                peerNodes = Array.empty[PeerConfig]
            )
        )

    implicit val rw: RW[BootstrapOptions] = macroRW
}


@Lenses case class JoinOptions(
    network: NetworkConfig,
    invite: Invite
)

object JoinOptions {
    val Defaults: JoinOptions =
        JoinOptions(
            network = NetworkConfig(
                orderingNodes = Array.empty[OSNConfig],
                peerNodes =
                  Array(
                      PeerConfig(
                          name = "peer0",
                          port = 7014,
                          couchDB = CouchDBConfig(
                              port = 7015
                          )
                      )
                  )
            ),
            invite = Invite(
                networkName = "",
                address = "",
                key = ""
            )
        )
}

@Lenses case class BlockConfig(
    maxMessageCount: Int,
    absoluteMaxBytes: Int,
    preferredMaxBytes: Int,
    batchTimeOut: String
)

object BlockConfig {
    implicit val rw: RW[BlockConfig] = macroRW
}

@Lenses case class RaftConfig(
    tickInterval: String,
    electionTick: Int,
    heartbeatTick: Int,
    maxInflightBlocks: Int,
    snapshotIntervalSize: Int
)

object RaftConfig {
    implicit val rw: RW[RaftConfig] = macroRW
}

@Lenses case class NetworkConfig(
    orderingNodes: Array[OSNConfig],
    peerNodes: Array[PeerConfig]
)

object NetworkConfig {
    implicit val rw: RW[NetworkConfig] = macroRW
}

@Lenses case class OSNConfig(
    name: String,
    port: Int
)

object OSNConfig {
    implicit val rw: RW[OSNConfig] = macroRW
}

@Lenses case class PeerConfig(
    name: String,
    port: Int,
    couchDB: CouchDBConfig
)

object PeerConfig {
    implicit val rw: RW[PeerConfig] = macroRW
}

@Lenses case class CouchDBConfig(
    port: Int
)

object CouchDBConfig {
    implicit val rw: RW[CouchDBConfig] = macroRW
}

case class Invite(
    networkName: String,
    address: String,
    key: String
)
