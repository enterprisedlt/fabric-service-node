package org.enterprisedlt.fabric.service.node

case class CreateChannelRequest(
    name: String,
//    orderer: String,
//    fileName: String
)

case class AddPeerRequest(
    channelName: String,
    peer: String
)

case class AddAnchorsToChRequest(
    channelName: String,
    peerName: String
)

case class InstallCCRequest(
    channelName: String,
    chainCodeName: String,
    chainCodeVersion: String
)

case class InitCCRequest(
    channelName: String,
    chainCodeName: String,
    version: String,
    arguments: Array[String]
)

case class QuertyRequest(
    channelName: String,
    chainCodeName: String,
    functionName: String,
    arguments: Array[String]
)

case class Invite(
    address: String
)

case class JoinRequest(
    genesisConfig : String,
    mspId: String
)

case class JoinResponse(
    genesis: String,
    version: String
)