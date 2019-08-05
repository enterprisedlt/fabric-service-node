package org.enterprisedlt.fabric.service.node.model

import org.enterprisedlt.fabric.service.model.Organization

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
    address: String,
    key: String
)

case class JoinRequest(
    genesisConfig: String,
    organization: Organization
)

case class JoinResponse(
    genesis: String,
    version: String,
    knownOrganizations: Array[Organization]
)

case class SendMessageRequest(
    to: String,
    body: String
)

case class GetMessageRequest(
    messageKey: String,
    sender: String
)

case class DeleteMessageRequest(
    messageKey: String,
    sender: String
)
