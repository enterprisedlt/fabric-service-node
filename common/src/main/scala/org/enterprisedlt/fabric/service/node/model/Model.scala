package org.enterprisedlt.fabric.service.node.model

import java.util.{Map => JavaMap}

import org.enterprisedlt.fabric.service.model.Organization
import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, ServiceConfig}
import org.hyperledger.fabric.sdk.{ChaincodeCollectionConfiguration, ChaincodeEndorsementPolicy}

case class CreateChannelRequest(
    channelName: String,
    consortiumName: String,
    orgName: String
)

case class AddPeerToChannelRequest(
    channelName: String,
    peer: String
)

case class AddOsnToChannelRequest(
    osnName: String,
    cryptoPath: String,
    channelName: String
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
    endorsementPolicy: Option[ChaincodeEndorsementPolicy] = None,
    collectionConfig: Option[ChaincodeCollectionConfiguration] = None,
    arguments: Array[String] = Array.empty
)

case class QueryChainCodeRequest(
    channelName: String,
    chainCodeName: String,
    functionName: String,
    arguments: Array[String]
)


case class JoinRequest(
    organization: Organization,
    //
    organizationCertificates: OrganizationCertificates,
    //
    osnCertificates: OsnCertificates,
    osnHost: String,
    osnPort: Int
)

case class JoinToChannelRequest(
    joinRequest: JoinRequest,
    channelName: String
)

case class JoinResponse(
    genesis: String,
    version: String,
    knownOrganizations: Array[Organization]
)

case class CreateBlockRequest(
    profilePath: String,
    config: ServiceConfig,
    bootstrapOptions: BootstrapOptions
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

case class Invite(
    address: String,
    key: String
)
