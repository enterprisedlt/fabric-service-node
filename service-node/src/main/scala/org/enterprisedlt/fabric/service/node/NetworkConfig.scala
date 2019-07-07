package org.enterprisedlt.fabric.service.node

//PEER
case class PeerConfig(
    name: String,
    host: String,
    port: Int,
    tls: String
)

//OSN
case class OSNConfig(
    name: String,
    host: String,
    port: Int,
    tls: String
)

//USER
case class UserConfig(
    name: String,
    msp: String
)


case class NetworkConfig(
    orgID: String,
    orgAdmin: UserConfig,
    orderingAdmin: UserConfig,
    peerNodes: Array[PeerConfig],
    orderingNodes: Array[OSNConfig]
)
