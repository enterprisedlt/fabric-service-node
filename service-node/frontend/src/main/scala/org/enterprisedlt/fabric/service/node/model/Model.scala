package org.enterprisedlt.fabric.service.node.model

import monocle.macros.Lenses
import upickle.default.{macroRW, ReadWriter => RW}

/**
 * @author Alexey Polubelov
 */

@Lenses case class ComponentCandidate(
    box: String,
    name: String,
    port: Int,
    componentType: String
)

object ComponentCandidate {
    val OSN = "OSN"
    val Peer = "Peer"
    val Types = Seq(OSN, Peer)

    implicit val rw: RW[ComponentCandidate] = macroRW
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
    lang: String,
    contractType: String,
    channelName: String,
    parties: Array[ContractParticipant],
    initArgs: Array[String]
)

object CreateContractRequest {

    val Defaults: CreateContractRequest = CreateContractRequest(
        name = "",
        version = "",
        lang = "java",
        contractType = "",
        channelName = "",
        parties = Array.empty[ContractParticipant],
        initArgs = Array.empty[String]
    )

    implicit val rw: RW[CreateContractRequest] = macroRW
    val ChaincodeLanguages = Seq("java", "scala", "go", "node")

}


@Lenses case class ContractParticipant(
    mspId: String,
    role: String
)

object ContractParticipant {
    implicit val rw: RW[ContractParticipant] = macroRW
}


@Lenses case class Contract(
    name: String,
    lang: String,
    chainCodeName: String,
    chainCodeVersion: String,
    founder: String,
    participants: Array[String],
    timestamp: Long
)

object Contract {
    implicit val rw: RW[Contract] = macroRW
}


@Lenses case class ContractJoinRequest(
    name: String,
    founder: String
)

object ContractJoinRequest {

    val Defaults: ContractJoinRequest = ContractJoinRequest(
        name = "",
        founder = ""
    )

    implicit val rw: RW[ContractJoinRequest] = macroRW
}

@Lenses case class Box(
    name: String,
    information: BoxInformation
)

object Box {
    implicit val rw: RW[Box] = macroRW
}

@Lenses case class BoxInformation(
    externalAddress: String,
    details: String
)

object BoxInformation {
    implicit val rw: RW[BoxInformation] = macroRW
}


@Lenses case class RegisterBoxManager(
    name: String,
    url: String
)


object RegisterBoxManager {
    implicit val rw: RW[RegisterBoxManager] = macroRW
}
