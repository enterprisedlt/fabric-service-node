package org.enterprisedlt.fabric.service.node.model

import java.util.{Map => JavaMap}

import org.enterprisedlt.fabric.service.model.Organization
import org.hyperledger.fabric.sdk.TransactionRequest.Type

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

case class JoinRequest(
    organization: Organization,
    organizationCertificates: OrganizationCertificates,
)

case class AddOrgToChannelRequest(
    mspId: String,
    channelName: String,
    organizationCertificates: OrganizationCertificates
)

case class JoinResponse(
    genesis: String,
    version: String,
    knownOrganizations: Array[Organization],
    osnHost: String,
    osnPort: Int,
    osnTLSCert: String //base64
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
    language: String,
    roles: Array[String],
    initMethod: String,
    warmUpMethod: String,
    endorsement: String,
    collections: Array[ContractCollectionDescriptor]
)

case class ContractCollectionDescriptor(
    name: String,
    members: Array[String]
)

case class CreateContractRequest(
    name: String,
    version: String,
    contractType: String,
    channelName: String,
    parties: Array[ContractParticipant],
    initArgs: Array[String]
)

case class UpgradeContractRequest(
    name: String,
    version: String,
    contractType: String,
    channelName: String,
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

object CCLanguage {

    val GO = "GO"
    val JAVA = "JAVA"
    val SCALA = "SCALA"
    val JS = "NODE"

    object GoLang {
        def unapply(arg: String): Option[Type] = Option(arg).filter { name =>
            name.equalsIgnoreCase(GO) || name.equalsIgnoreCase("GO_LANG")
        }.map(_ => Type.GO_LANG)
    }

    object JVM {
        def unapply(arg: String): Option[Type] = Option(arg).filter { name =>
            name.equalsIgnoreCase(JAVA) || name.equalsIgnoreCase(SCALA)
        }.map(_ => Type.JAVA)
    }

    object NodeJS {
        def unapply(arg: String): Option[Type] = Option(arg).filter { name =>
            name.equalsIgnoreCase(JS)
        }.map(_ => Type.NODE)
    }

}

