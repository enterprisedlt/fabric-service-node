package org.enterprisedlt.fabric.service.node.model

case class CreateChannelRequest(
    name: String
//    orderer: String,
//    fileName: String
)

case class AddPeerRequest(
    channelName: String,
    peer: String
)

case class AddAnchorToChannelRequest(
    channelName: String,
    peerName: String
)

case class InstallChainCodeRequest(
    channelName: String,
    chainCodeName: String,
    chainCodeVersion: String
)

case class InstantiateChainCodeRequest(
    channelName: String,
    chainCodeName: String,
    version: String,
    arguments: Array[String]
)

case class QueryChainCodeRequest(
    channelName: String,
    chainCodeName: String,
    functionName: String,
    arguments: Array[String]
)

case class Invite(
    address: String
)

case class JoinRequest(
    genesisConfig: String,
    mspId: String,
    externalHost: String
)

case class JoinResponse(
    genesis: String,
    version: String
)
