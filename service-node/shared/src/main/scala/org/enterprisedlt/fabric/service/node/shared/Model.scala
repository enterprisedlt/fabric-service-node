package org.enterprisedlt.fabric.service.node.shared

import monocle.macros.Lenses
import upickle.default.{macroRW, ReadWriter => RW}

/**
 * @author Alexey Polubelov
 */

// ------------------------------------------------------------------------
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
            block = BlockConfig.Default,
            raft = RaftConfig.Default,
            network = NetworkConfig.Default
        )

    implicit val rw: RW[BootstrapOptions] = macroRW
}


// ------------------------------------------------------------------------
@Lenses case class JoinOptions(
    network: NetworkConfig,
    invite: Invite
)

object JoinOptions {
    val Defaults: JoinOptions =
        JoinOptions(
            network = NetworkConfig.Default,
            invite = Invite(
                networkName = "",
                address = "",
                key = ""
            )
        )
    implicit val rw: RW[JoinOptions] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class BlockConfig(
    maxMessageCount: Int,
    absoluteMaxBytes: Int,
    preferredMaxBytes: Int,
    batchTimeOut: String
)

object BlockConfig {
    val Default: BlockConfig = BlockConfig(
        maxMessageCount = 150,
        absoluteMaxBytes = 103809024,
        preferredMaxBytes = 524288,
        batchTimeOut = "1s"
    )
    implicit val rw: RW[BlockConfig] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class RaftConfig(
    tickInterval: String,
    electionTick: Int,
    heartbeatTick: Int,
    maxInflightBlocks: Int,
    snapshotIntervalSize: Int
)

object RaftConfig {
    val Default: RaftConfig = RaftConfig(
        tickInterval = "500ms",
        electionTick = 10,
        heartbeatTick = 1,
        maxInflightBlocks = 5,
        snapshotIntervalSize = 20971520
    )
    implicit val rw: RW[RaftConfig] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class NetworkConfig(
    orderingNodes: Array[OSNConfig],
    peerNodes: Array[PeerConfig]
)

object NetworkConfig {
    val Default: NetworkConfig = NetworkConfig(
        orderingNodes = Array.empty[OSNConfig],
        peerNodes = Array.empty[PeerConfig]
    )

    implicit val rw: RW[NetworkConfig] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class Invite(
    networkName: String,
    address: String,
    key: String
)

object Invite {
    implicit val rw: RW[Invite] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class OSNConfig(
    box: String,
    name: String,
    port: Int
)

object OSNConfig {
    implicit val rw: RW[OSNConfig] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class PeerConfig(
    box: String,
    name: String,
    port: Int,
    //    couchDB: CouchDBConfig
)

object PeerConfig {
    implicit val rw: RW[PeerConfig] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class CouchDBConfig(
    port: Int
)

object CouchDBConfig {
    implicit val rw: RW[CouchDBConfig] = macroRW
}

// ------------------------------------------------------------------------
case class ChainCodeInfo(
    name: String,
    version: String,
    language: String,
    channelName: String,
)

object ChainCodeInfo {
    implicit val rw: RW[ChainCodeInfo] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class Box(
    name: String,
    information: BoxInformation
)

object Box {
    implicit val rw: RW[Box] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class BoxInformation(
    externalAddress: String,
    details: String
)

object BoxInformation {
    implicit val rw: RW[BoxInformation] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class RegisterBoxManager(
    name: String,
    url: String
)

object RegisterBoxManager {
    implicit val rw: RW[RegisterBoxManager] = macroRW
}

// ------------------------------------------------------------------------
case class ContractDescriptor(
    name: String,
    roles: Array[String],
    initArgsNames: Array[String],
)

object ContractDescriptor {
    implicit val rw: RW[ContractDescriptor] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class CreateContractRequest(
    name: String,
    version: String,
    contractType: String,
    channelName: String,
    parties: Array[ContractParticipant],
    initArgs: Array[String]
)

object CreateContractRequest {
    implicit val rw: RW[CreateContractRequest] = macroRW
}

// ------------------------------------------------------------------------
case class UpgradeContractRequest(
    name: String,
    version: String,
    contractType: String,
    channelName: String,
    parties: Array[ContractParticipant],
    initArgs: Array[String]
)

object UpgradeContractRequest {
    implicit val rw: RW[UpgradeContractRequest] = macroRW
}

// ------------------------------------------------------------------------
@Lenses case class ContractParticipant(
    mspId: String,
    role: String
)

object ContractParticipant {
    implicit val rw: RW[ContractParticipant] = macroRW
}

// ------------------------------------------------------------------------
case class Events(
    messages: Array[PrivateMessageEvent],
    contractInvitations: Array[ContractInvitation],
    customComponentDescriptors: Array[CustomComponentDescriptor],
    applications: Array[Application]
)

object Events {
    implicit val rw: RW[Events] = macroRW
}

// ------------------------------------------------------------------------
case class PrivateMessageEvent(

)

object PrivateMessageEvent {
    implicit val rw: RW[PrivateMessageEvent] = macroRW
}

// ------------------------------------------------------------------------
case class ContractInvitation(
    initiator: String,
    name: String,
    chainCodeName: String,
    chainCodeVersion: String,
    participants: Array[String],
)

object ContractInvitation {
    implicit val rw: RW[ContractInvitation] = macroRW
}


@Lenses case class PortBind(
    externalPort: String,
    internalPort: String
)

object PortBind {
    implicit val rw: RW[PortBind] = macroRW
}

@Lenses case class VolumeBind(
    externalHost: String,
    internalHost: String
)

object VolumeBind {
    implicit val rw: RW[VolumeBind] = macroRW
}

@Lenses case class EnvironmentVariable(
    key: String,
    value: String
)

object EnvironmentVariable {
    implicit val rw: RW[EnvironmentVariable] = macroRW
}

// ------------------------------------------------------------------------
case class CustomComponentDescriptor(
    componentType: String,
    image: Image,
    command: String,
    workingDir: String,
    environmentVariablesDescriptor: Array[EnvironmentVariable],
    portBindDescriptor: Array[PortBind],
    volumeBindDescriptor: Array[VolumeBind]
)

object CustomComponentDescriptor {

    implicit val rw: RW[CustomComponentDescriptor] = macroRW
}


case class Image(
    name: String,
    tag: String = "latest"
) {
    def getName = s"$name:$tag"
}


object Image {

    implicit val rw: RW[Image] = macroRW
}


case class Application(
    name: String
)

object Application {

    implicit val rw: RW[Application] = macroRW
}
