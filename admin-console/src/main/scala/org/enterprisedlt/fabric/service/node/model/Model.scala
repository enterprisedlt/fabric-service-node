package org.enterprisedlt.fabric.service.node.model

import monocle.macros.Lenses

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

@Lenses case class RaftConfig(
    tickInterval: String,
    electionTick: Int,
    heartbeatTick: Int,
    maxInflightBlocks: Int,
    snapshotIntervalSize: Int
)

@Lenses case class NetworkConfig(
    orderingNodes: Array[OSNConfig],
    peerNodes: Array[PeerConfig]
)

@Lenses case class OSNConfig(
    name: String,
    port: Int
)

@Lenses case class PeerConfig(
    name: String,
    port: Int,
    couchDB: CouchDBConfig
)

@Lenses case class CouchDBConfig(
    port: Int
)

case class Invite(
    networkName: String,
    address: String,
    key: String
)
