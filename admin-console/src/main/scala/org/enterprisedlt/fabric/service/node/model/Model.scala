package org.enterprisedlt.fabric.service.node.model

import monocle.macros.Lenses
import upickle.default.{macroRW, ReadWriter => RW}

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
                peerNodes = Array.empty[PeerConfig]
            ),
            invite = Invite(
                networkName = "",
                address = "",
                key = ""
            )
        )
    implicit val rw: RW[JoinOptions] = macroRW
}

@Lenses case class ComponentCandidate(
    name: String,
    port: Int,
    componentType: String
)

object ComponentCandidate {
    implicit val rw: RW[ComponentCandidate] = macroRW
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


trait ComponentConfig

@Lenses case class OSNConfig(
    name: String,
    port: Int
) extends ComponentConfig

object OSNConfig {
    implicit val rw: RW[OSNConfig] = macroRW
}

@Lenses case class PeerConfig(
    name: String,
    port: Int,
    couchDB: CouchDBConfig
) extends ComponentConfig

object PeerConfig {
    implicit val rw: RW[PeerConfig] = macroRW
}

@Lenses case class CouchDBConfig(
    port: Int
)

object CouchDBConfig {
    implicit val rw: RW[CouchDBConfig] = macroRW
}

@Lenses case class Invite(
    networkName: String,
    address: String,
    key: String
)

object Invite {
    implicit val rw: RW[Invite] = macroRW
}

case class FabricServiceState(
    stateCode: Int
)

object FabricServiceState {
    implicit val rw: RW[FabricServiceState] = macroRW
}


@Lenses case class JoinRequest(
    organization: Organization,
    organizationCertificates: OrganizationCertificates,
)

object JoinRequest {
    val Defaults = new JoinRequest(
        organization = Organization(
            mspId = "",
            name = "",
            memberNumber = 0,
            knownHosts = Array.empty[KnownHostRecord]
        ),
        organizationCertificates = OrganizationCertificates(
            caCerts = Array.empty[String],
            tlsCACerts = Array.empty[String],
            adminCerts = Array.empty[String]
        )
    )

    implicit val rw: RW[JoinRequest] = macroRW
}

@Lenses case class Organization(
    mspId: String,
    name: String,
    memberNumber: Long,
    knownHosts: Array[KnownHostRecord]
)

object Organization {
    implicit val rw: RW[Organization] = macroRW
}


@Lenses case class KnownHostRecord(
    ipAddress: String,
    dnsName: String
)

object KnownHostRecord {
    implicit val rw: RW[KnownHostRecord] = macroRW
}

@Lenses case class OrganizationCertificates(
    caCerts: Array[String],
    tlsCACerts: Array[String],
    adminCerts: Array[String]
)

object OrganizationCertificates {
    implicit val rw: RW[OrganizationCertificates] = macroRW
}


@Lenses case class CreateContractRequest(
    name: String,
    version: String,
    contractType: String,
    channelName: String,
    parties: Array[ContractParticipant],
    initArgs: Array[String]
)

object CreateContractRequest {

    val Defaults: CreateContractRequest = CreateContractRequest(
        name = "",
        version = "",
        contractType = "",
        channelName = "",
        parties = Array.empty[ContractParticipant],
        initArgs = Array.empty[String]
    )

    implicit val rw: RW[CreateContractRequest] = macroRW

}


@Lenses case class ContractParticipant(
    mspId: String,
    role: String
)

object ContractParticipant {
    implicit val rw: RW[ContractParticipant] = macroRW
}
