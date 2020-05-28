package org.enterprisedlt.fabric.service.node.model

import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.shared.{EnvironmentVariable, PortBind, VolumeBind}
import upickle.default.{macroRW, ReadWriter => RW}

/**
 * @author Alexey Polubelov
 */

@Lenses case class ComponentCandidate(
    box: String,
    name: String,
    port: Int,
    componentType: String,
    //
    environmentVariables: Array[EnvironmentVariable],
    ports: Array[PortBind],
    volumes: Array[VolumeBind]
)

object ComponentCandidate {
    val OSN = "OSN"
    val Peer = "Peer"
    val Types = Seq(OSN, Peer)

    implicit val rw: RW[ComponentCandidate] = macroRW
}

@Lenses case class JoinRequest(
    organization: Organization,
    organizationCertificates: OrganizationCertificates,
)

object JoinRequest {
    implicit val rw: RW[JoinRequest] = macroRW
}

@Lenses case class Organization(
    mspId: String,
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