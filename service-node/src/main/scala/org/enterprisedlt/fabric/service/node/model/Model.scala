package org.enterprisedlt.fabric.service.node.model

import java.util.{Map => JavaMap}

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
    networkName: String,
    address: String,
    key: String
)

case class JoinRequest(
    organization: Organization,
    organizationCertificates: OrganizationCertificates,
)

case class JoinResponse(
    genesis: String,
    version: String,
    knownOrganizations: Array[Organization],
    osnHost: String,
    osnPort: Int
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

case class ContractDeploymentDescriptor(
    name: String,
    roles: Array[String],
    initMethod: String,
    warmUpMethod: String,
    endorsement: Array[String],
    collections: Array[ContractCollectionDescriptor]
)

case class ContractCollectionDescriptor(
    name: String,
    members: Array[String]
)

case class CreateContract(
    name: String,
    chainCodeName: String,
    chainCodeVersion: String,
    participants: Array[String]
)

case class CreateContractRequest(
    name: String,
    version: String,
    contractType: String,
    parties: Array[ContractParticipant],
    initArgs: Array[String]
)

case class ContractParticipant(
    mspId: String,
    role: String
)

case class ContractJoinRequest(
    name: String,
    founder: String
)

case class CallContractRequest(
    callType: String,
    contractName: String,
    functionName: String,
    arguments: Array[String],
    transient: JavaMap[String, String],
    awaitTransaction: Boolean
)

case class OrganizationCertificates(
    caCerts: Array[String],
    tlsCACerts: Array[String],
    adminCerts: Array[String],

)


case class OsnCertificates(
    clientTlsCert: String,
    serverTlsCert: String
)
