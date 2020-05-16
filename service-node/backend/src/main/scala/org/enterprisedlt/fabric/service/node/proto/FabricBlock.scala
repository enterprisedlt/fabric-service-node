package org.enterprisedlt.fabric.service.node.proto

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.concurrent.TimeUnit

import com.google.protobuf.{ByteString, Timestamp}
import org.bouncycastle.util.encoders.Hex
import org.enterprisedlt.fabric.service.node.shared.{BootstrapOptions, RaftConfig}
import org.hyperledger.fabric.protos.common.Common._
import org.hyperledger.fabric.protos.common.Configtx._
import org.hyperledger.fabric.protos.common.Configuration._
import org.hyperledger.fabric.protos.common.MspPrincipal.{MSPPrincipal, MSPRole}
import org.hyperledger.fabric.protos.common.Policies.ImplicitMetaPolicy.Rule
import org.hyperledger.fabric.protos.common.Policies.Policy.PolicyType
import org.hyperledger.fabric.protos.common.Policies.SignaturePolicy.NOutOf
import org.hyperledger.fabric.protos.common.Policies.{ImplicitMetaPolicy, Policy, SignaturePolicy, SignaturePolicyEnvelope}
import org.hyperledger.fabric.protos.ext.orderer.Configuration.{BatchSize, BatchTimeout, ChannelRestrictions, ConsensusType}
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration.{ConfigMetadata, Consenter, Options}
import org.hyperledger.fabric.protos.msp.MspConfig._

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
object FabricBlock {

    def create(channelDefinition: ChannelDefinition, bootstrapOptions: BootstrapOptions): Block = {
        val payloadSignatureHeader = newSignatureHeader(ByteString.copyFrom(newNonce()))
        val payloadChannelHeader = newChannelHeader(
            HeaderType.CONFIG,
            version = 1,
            channelDefinition.channelName,
            epoch = 0,
            txId = computeTxID(payloadSignatureHeader)
        )
        val payloadEnvelope =
            newEnvelop(
                newPayload(
                    newPayloadHeader(
                        payloadChannelHeader,
                        payloadSignatureHeader
                    ),
                    newConfigEnvelop(
                        newConfig(createChannelConfig(channelDefinition, bootstrapOptions))
                    )
                )
            )

        val blockData =
            BlockData.newBuilder()
              .addData(payloadEnvelope.toByteString)
              .build

        val metadata = BlockMetadata.newBuilder()
        for (i <- 0 to 3) {
            metadata.addMetadata(
                {
                    val md = Metadata.newBuilder()
                    if (BlockMetadataIndex.LAST_CONFIG.getNumber == i) {
                        md.setValue(
                            LastConfig.newBuilder()
                              .setIndex(0)
                              .build.toByteString
                        )
                    }
                    md.build.toByteString
                }
            )
        }

        Block.newBuilder()
          .setData(blockData)
          .setHeader(
              BlockHeader.newBuilder()
                .setNumber(0)
                .setDataHash(ByteString.copyFrom(sha256HashBytes(payloadEnvelope.toByteArray)))
                //.setPreviousHash(null)
                .build
          )
          .setMetadata(metadata)
          .build
    }

    def newChannelHeader(headerType: HeaderType, version: Int, channelId: String, epoch: Int, txId: String): ChannelHeader =
        ChannelHeader.newBuilder()
          .setType(headerType.getNumber)
          .setChannelId(channelId)
          .setVersion(version)
          .setTxId(txId)
          .setTimestamp(
              Timestamp.newBuilder()
                .setSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
                .setNanos(0)
          )
          .build

    def newPayloadHeader(channelHeader: ChannelHeader, signatureHeader: SignatureHeader): Header =
        Header.newBuilder()
          .setChannelHeader(channelHeader.toByteString)
          .setSignatureHeader(signatureHeader.toByteString)
          .build

    def newPayload(header: Header, envelope: ConfigEnvelope): Payload =
        Payload.newBuilder()
          .setHeader(header)
          .setData(envelope.toByteString)
          .build

    def newConfigEnvelop(config: Config): ConfigEnvelope =
        ConfigEnvelope.newBuilder
          .setConfig(config)
          .build

    def newEnvelop(payload: Payload): Envelope =
        Envelope.newBuilder
          //.setSignature(signature)
          .setPayload(payload.toByteString)
          .build

    def newSignatureHeader(nonce: ByteString): SignatureHeader =
        SignatureHeader.newBuilder
          //.setCreator(creator)
          .setNonce(nonce)
          .build

    def newNonce(): Array[Byte] = {
        val nonce = new Array[Byte](24)
        val random = new SecureRandom()
        random.nextBytes(nonce)
        nonce
    }

    def computeTxID(signatureHeader: SignatureHeader): String = {
        val hashBytes = sha256HashBytes(
            signatureHeader.getNonce.toByteArray
            //signatureHeader.getCreator.toByteArray
        )
        new String(Hex.encode(hashBytes), StandardCharsets.UTF_8)
    }

    def sha256HashBytes(data: Array[Byte]*): Array[Byte] = {
        val digest = MessageDigest.getInstance("SHA-256")
        data.foreach(digest.update)
        digest.digest()
    }

    def newConfig(group: ConfigGroup): Config = {
        Config.newBuilder
          .setChannelGroup(group)
          .build
    }

    def createChannelConfig(channelDefinition: ChannelDefinition, bootstrapOptions: BootstrapOptions): ConfigGroup = {
        val channelGroup = ConfigGroup.newBuilder()
        putPolicies(channelGroup, channelDefinition.policies)
        channelGroup.putValues(
            ConfigKey.HashingAlgorithmKey,
            ConfigValue.newBuilder()
              .setValue(HashingAlgorithm.newBuilder().setName("SHA256").build().toByteString)
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )
        channelGroup.putValues(
            ConfigKey.BlockDataHashingStructureKey,
            ConfigValue.newBuilder()
              .setValue(
                  BlockDataHashingStructure.newBuilder()
                    .setWidth(-1) // equivalent to MAX_UInt32, the only supported value
                    .build().toByteString
              )
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )
        putCapabilities(channelGroup, channelDefinition.capabilities)
        val ordererAddresses = OrdererAddresses.newBuilder()
        channelDefinition.ordering.map(_.orderingNodes).getOrElse(Seq.empty).foreach { osn =>
            ordererAddresses.addAddresses(osn.address)
        }
        channelGroup.putValues(
            ConfigKey.OrdererAddressesKey,
            ConfigValue.newBuilder()
              .setValue(ordererAddresses.build().toByteString)
              .setModPolicy(ModPolicyValue.OrdererAdmins)
              .build()
        )
        channelDefinition.ordering.foreach { ordering =>
            channelGroup.putGroups(ConfigKey.OrdererGroupKey, newOrderingServiceGroup(ordering, bootstrapOptions))
        }
        channelDefinition.consortiumDetails match {
            case ConsortiumName(name) =>
                channelGroup.putValues(
                    ConfigKey.ConsortiumKey,
                    ConfigValue.newBuilder()
                      .setValue(
                          Consortium.newBuilder()
                            .setName(name)
                            .build().toByteString
                      )
                      .setModPolicy(ModPolicyValue.Admins)
                      .build()
                )

            case ConsortiumsDefinition(values) =>
                channelGroup.putGroups(
                    ConfigKey.ConsortiumsGroupKey,
                    newConsortiumsGroup(values)
                )
        }

        channelDefinition.application.foreach { apps =>
            channelGroup.putGroups(ConfigKey.ApplicationGroupKey, newApplicationGroup(apps))
        }

        channelGroup
          .setModPolicy(ModPolicyValue.Admins)
          .build()
    }

    def newConsortiumsGroup(consortiums: Iterable[ConsortiumDefinition]): ConfigGroup = {
        val consortiumsGroup = ConfigGroup.newBuilder()
        consortiumsGroup.putPolicies(
            PolicyKeys.Admins,
            ConfigPolicy.newBuilder()
              .setPolicy(PolicyValue.AcceptAllPolicy)
              .setModPolicy(ModPolicyValue.OrdererAdmins)
              .build()
        )

        consortiums.foreach { consortium =>
            consortiumsGroup.putGroups(consortium.name, newConsortiumGroup(consortium))
        }

        consortiumsGroup
          .setModPolicy(ModPolicyValue.OrdererAdmins)
          .build()
    }

    def newConsortiumGroup(consortium: ConsortiumDefinition): ConfigGroup = {
        val consortiumGroup = ConfigGroup.newBuilder()
        consortium.organizations.foreach { org =>
            consortiumGroup.putGroups(org.mspId, newOrderingOrganizationGroup(org))
        }
        consortiumGroup.putValues(
            ConfigKey.ChannelCreationPolicyKey,
            ConfigValue.newBuilder()
              .setValue(
                  newImplicitMetaPolicy(ImplicitMetaPolicy.Rule.ANY, SubPolicyValue.Admins).toByteString
              )
              .setModPolicy(ModPolicyValue.OrdererAdmins)
              .build()
        )
        consortiumGroup
          .setModPolicy(ModPolicyValue.OrdererAdmins)
          .build()
    }

    def newImplicitMetaPolicy(rule: ImplicitMetaPolicy.Rule, subPolicy: String): Policy = {
        Policy.newBuilder
          .setType(PolicyType.IMPLICIT_META.getNumber)
          .setValue(
              ImplicitMetaPolicy.newBuilder()
                .setRule(rule)
                .setSubPolicy(subPolicy)
                .build().toByteString
          )
          .build()
    }

    def newSignedByPolicy(mspId: String, mspRole: MSPRole.MSPRoleType): Policy = {
        Policy.newBuilder()
          .setType(PolicyType.SIGNATURE.getNumber)
          .setValue(
              newSignedByPolicyEnvelope(mspId, mspRole).toByteString
          )
          .build()
    }

    def newSignedByPolicyEnvelope(mspId: String, mspRole: MSPRole.MSPRoleType): SignaturePolicyEnvelope = {
        // requires exactly 1 signature from the first (and only) principal
        SignaturePolicyEnvelope.newBuilder()
          .setRule(
              SignaturePolicy.newBuilder()
                .setNOutOf(
                    NOutOf.newBuilder()
                      .setN(1)
                      .addRules(
                          SignaturePolicy.newBuilder()
                            .setSignedBy(0)
                      )
                )
          )
          .addIdentities(
              MSPPrincipal.newBuilder()
                .setPrincipalClassification(MSPPrincipal.Classification.ROLE)
                .setPrincipal(
                    MSPRole.newBuilder()
                      .setRole(mspRole)
                      .setMspIdentifier(mspId)
                      .build().toByteString
                )
          )
          .build()
    }

    def newSignedByConfigPolicy(mspId: String, mspRole: MSPRole.MSPRoleType, modPolicy: String): ConfigPolicy =
        ConfigPolicy.newBuilder()
          .setModPolicy(modPolicy)
          .setPolicy(newSignedByPolicy(mspId, mspRole))
          .build()

    def newImplicitMetaConfigPolicy(rule: ImplicitMetaPolicy.Rule, subPolicy: String, modPolicy: String): ConfigPolicy =
        ConfigPolicy.newBuilder()
          .setModPolicy(modPolicy)
          .setPolicy(newImplicitMetaPolicy(rule, subPolicy))
          .build()

    def putPolicies(group: ConfigGroup.Builder, policiesDefinition: PoliciesDefinition): ConfigGroup.Builder = {
        putPolicy(group, PolicyKeys.Admins, policiesDefinition.admins)
        putPolicy(group, PolicyKeys.Writers, policiesDefinition.writers)
        putPolicy(group, PolicyKeys.Readers, policiesDefinition.readers)
    }

    def putPolicy(group: ConfigGroup.Builder, key: String, policyDefinition: PolicyDefinition): ConfigGroup.Builder = {
        policyDefinition match {
            case implicitPolicy: ImplicitPolicy =>
                group.putPolicies(
                    key,
                    newImplicitMetaConfigPolicy(
                        implicitPolicy.rule,
                        implicitPolicy.value.stringValue,
                        ModPolicyValue.Admins
                    )
                )

            case SignedByOneOf(participants) =>
                group.putPolicies(
                    key,
                    newSignedByOneOfConfigPolicy(
                        participants,
                        ModPolicyValue.Admins
                    )
                )
        }
    }

    def newSignedByOneOfConfigPolicy(classifiers: Iterable[MemberClassifier], modPolicy: String): ConfigPolicy =
        ConfigPolicy.newBuilder()
          .setModPolicy(modPolicy)
          .setPolicy(
              Policy.newBuilder()
                .setType(PolicyType.SIGNATURE.getNumber)
                .setValue(
                    newSignedByOneOf(classifiers).toByteString
                )
                .build()
          )
          .build()

    def newSignedByOneOf(classifiers: Iterable[MemberClassifier]): SignaturePolicyEnvelope = {
        val rules = SignaturePolicy.NOutOf.newBuilder.setN(1)
        val identities = classifiers.zipWithIndex.map { case (member, index) =>
            rules.addRules(SignaturePolicy.newBuilder.setSignedBy(index))
            MSPPrincipal.newBuilder()
              .setPrincipalClassification(MSPPrincipal.Classification.ROLE)
              .setPrincipal(
                  MSPRole.newBuilder
                    .setRole(member.mspRoleType)
                    .setMspIdentifier(member.mspId)
                    .build
                    .toByteString
              ).build
        }
        SignaturePolicyEnvelope.newBuilder
          .setVersion(0)
          .addAllIdentities(identities.asJava)
          .setRule(SignaturePolicy.newBuilder.setNOutOf(rules))
          .build
    }

    def putCapabilities(group: ConfigGroup.Builder, capabilities: Set[String]): Unit = {
        if (capabilities.nonEmpty) {
            val channelCaps = Capabilities.newBuilder()
            capabilities.map { cap =>
                channelCaps.putCapabilities(cap, Capability.newBuilder().build())
            }
            group.putValues(
                ConfigKey.CapabilitiesKey,
                ConfigValue.newBuilder()
                  .setValue(channelCaps.build().toByteString)
                  .setModPolicy(ModPolicyValue.Admins)
                  .build()
            )
        }
    }

    def newOrderingServiceGroup(ordering: OrderingServiceDefinition, bootstrapOptions: BootstrapOptions): ConfigGroup = {
        val orderingGroup = ConfigGroup.newBuilder()
        putPolicies(orderingGroup, ordering.policies)

        orderingGroup.putPolicies(
            ConfigKey.BlockValidationPolicyKey,
            newImplicitMetaConfigPolicy(ImplicitMetaPolicy.Rule.ANY, SubPolicyValue.Writers, ModPolicyValue.Admins)
        )

        orderingGroup.putValues(
            ConfigKey.BatchSizeKey,
            ConfigValue.newBuilder()
              .setValue(
                  BatchSize.newBuilder()
                    .setMaxMessageCount(ordering.maxMessageCount)
                    .setAbsoluteMaxBytes(ordering.absoluteMaxBytes)
                    .setPreferredMaxBytes(ordering.preferredMaxBytes)
                    .build().toByteString
              )
              .setModPolicy(ModPolicyValue.Admins)
              .build()

        )

        orderingGroup.putValues(
            ConfigKey.BatchTimeoutKey,
            ConfigValue.newBuilder()
              .setValue(
                  BatchTimeout.newBuilder()
                    .setTimeout(ordering.batchTimeOut)
                    .build().toByteString
              )
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )

        orderingGroup.putValues(
            ConfigKey.ChannelRestrictionsKey,
            ConfigValue.newBuilder()
              .setValue(
                  ChannelRestrictions.newBuilder()
                    .setMaxCount(ordering.maxChannelsCount)
                    .build().toByteString
              )
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )

        putCapabilities(orderingGroup, ordering.capabilities)
        val blockConfig = Option(bootstrapOptions.raft).getOrElse(
            RaftConfig(
                tickInterval = "500ms",
                electionTick = 10,
                heartbeatTick = 1,
                maxInflightBlocks = 5,
                snapshotIntervalSize = 20971520
            ))
        orderingGroup.putValues(
            ConfigKey.ConsensusTypeKey,
            ConfigValue.newBuilder()
              .setValue(
                  ConsensusType.newBuilder()
                    .setType("etcdraft")
                    .setMetadata(
                        ConfigMetadata.newBuilder()
                          .setOptions(
                              Options.newBuilder()
                                .setTickInterval(blockConfig.tickInterval)
                                .setElectionTick(blockConfig.electionTick)
                                .setHeartbeatTick(blockConfig.heartbeatTick)
                                .setMaxInflightBlocks(blockConfig.maxInflightBlocks)
                                .setSnapshotIntervalSize(blockConfig.snapshotIntervalSize)
                          )
                          .addAllConsenters(
                              ordering.orderingNodes.map { osn =>
                                  Consenter.newBuilder()
                                    .setHost(osn.host)
                                    .setPort(osn.port)
                                    .setClientTlsCert(osn.clientTlsCert)
                                    .setServerTlsCert(osn.serverTlsCert)
                                    .build()
                              }.asJava
                          )
                          .build().toByteString
                    )
                    .build().toByteString
              )
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )

        ordering.organizations.foreach { org =>
            orderingGroup.putGroups(org.mspId, newOrderingOrganizationGroup(org))
        }

        orderingGroup
          .setModPolicy(ModPolicyValue.Admins)
          .build()
    }


    def newOrderingOrganizationGroup(organization: OrganizationDefinition): ConfigGroup = {
        val orderingOrgGroup = ConfigGroup.newBuilder()
        putPolicies(orderingOrgGroup, organization.policies)

        orderingOrgGroup.putValues(
            ConfigKey.MSPKey,
            ConfigValue.newBuilder()
              .setValue(newMSPConfig(organization).toByteString)
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )

        orderingOrgGroup
          .setModPolicy(ModPolicyValue.Admins)
          .build()
    }

    def newApplicationGroup(applications: ApplicationDefinition): ConfigGroup = {
        val applicationsGroup = ConfigGroup.newBuilder()

        putPolicies(applicationsGroup, applications.policies)

        //TODO: implement support of ACLs

        putCapabilities(applicationsGroup, applications.capabilities)

        applications.organizations.foreach { org =>
            applicationsGroup.putGroups(org.mspId, newApplicationOrg(org))
        }

        applicationsGroup
          .setModPolicy(ModPolicyValue.Admins)
          .build()
    }

    def newApplicationOrg(organization: OrganizationDefinition): ConfigGroup = {
        val applicationOrgGroup = ConfigGroup.newBuilder()

        putPolicies(applicationOrgGroup, organization.policies)

        applicationOrgGroup.putValues(
            ConfigKey.MSPKey,
            ConfigValue.newBuilder()
              .setValue(newMSPConfig(organization).toByteString)
              .setModPolicy(ModPolicyValue.Admins)
              .build()
        )

        //TODO: do we need anchors in genesis?

        applicationOrgGroup
          .setModPolicy(ModPolicyValue.Admins)
          .build()
    }

    def newMSPConfig(organization: OrganizationDefinition): MSPConfig = {
        val fabricMSPConfig = FabricMSPConfig.newBuilder()
        fabricMSPConfig.setName(organization.mspId)
        // CA and Intermediate CA
        organization.caCerts.foreach(fabricMSPConfig.addRootCerts)
        organization.intermediateCerts.foreach(fabricMSPConfig.addIntermediateCerts)
        // TLS CA and Intermediate
        organization.tlsCACerts.foreach(fabricMSPConfig.addTlsRootCerts)
        organization.intermediateTLSCerts.foreach(fabricMSPConfig.addTlsIntermediateCerts)

        organization.adminCerts.foreach(fabricMSPConfig.addAdmins)
        organization.CRLs.foreach(fabricMSPConfig.addRevocationList)

        fabricMSPConfig.setCryptoConfig(
            // fabric configTX defaults:
            FabricCryptoConfig.newBuilder()
              .setIdentityIdentifierHashFunction("SHA256")
              .setSignatureHashFamily("SHA2")
        )

        //TODO: implement support for OrganizationalUnitIdentifiers

        organization.nodesVerification.foreach { verificationConfig =>
            fabricMSPConfig.setFabricNodeOus(
                FabricNodeOUs.newBuilder()
                  .setEnable(true)
                  .setPeerOuIdentifier(
                      FabricOUIdentifier.newBuilder()
                        .setOrganizationalUnitIdentifier(verificationConfig.peerOUValue)
                        .setCertificate(verificationConfig.peerOUCerts)
                  )
                  .setClientOuIdentifier(
                      FabricOUIdentifier.newBuilder()
                        .setOrganizationalUnitIdentifier(verificationConfig.clientOUValue)
                        .setCertificate(verificationConfig.clientOUCerts)
                  )
            )
        }


        MSPConfig.newBuilder()
          .setType(0) // Type: FABRIC
          .setConfig(fabricMSPConfig.build().toByteString)
          .build()
    }

}

object PolicyKeys {
    val Readers = "Readers"
    val Writers = "Writers"
    val Admins = "Admins"
}

object PolicyValue {
    val AcceptAllPolicy: Policy =
        Policy.newBuilder()
          .setType(PolicyType.SIGNATURE.getNumber)
          .setValue(
              SignaturePolicyEnvelope.newBuilder()
                .setRule(
                    SignaturePolicy.newBuilder()
                      .setNOutOf(
                          NOutOf.newBuilder()
                            .setN(0)
                      )
                ).build().toByteString
          ).build()

}

object SubPolicyValue {
    val Readers = "Readers"
    val Writers = "Writers"
    val Admins = "Admins"
}

object ModPolicyValue {
    val Readers = "Readers"
    val Writers = "Writers"
    val Admins = "Admins"

    val OrdererAdmins = "/Channel/Orderer/Admins"
}

object ConfigKey {
    // ConsortiumKey is the key for the cb.ConfigValue for the Consortium message
    val ConsortiumKey = "Consortium"

    // ConsortiumsGroupKey is the group name for the consortiums config
    val ConsortiumsGroupKey = "Consortiums"

    // HashingAlgorithmKey is the cb.ConfigItem type key name for the HashingAlgorithm message
    val HashingAlgorithmKey = "HashingAlgorithm"

    // BlockDataHashingStructureKey is the cb.ConfigItem type key name for the BlockDataHashingStructure message
    val BlockDataHashingStructureKey = "BlockDataHashingStructure"

    // OrdererAddressesKey is the cb.ConfigItem type key name for the OrdererAddresses message
    val OrdererAddressesKey = "OrdererAddresses"

    // GroupKey is the name of the channel group
    val ChannelGroupKey = "Channel"

    // CapabilitiesKey is the name of the key which refers to capabilities, it appears at the channel,
    // application, and orderer levels and this constant is used for all three.
    val CapabilitiesKey = "Capabilities"

    // ApplicationGroupKey is the group name for the Application config
    val ApplicationGroupKey = "Application"

    // OrdererGroupKey is the group name for the orderer config.
    val OrdererGroupKey = "Orderer"

    // MSPKey is the key for the MSP definition in orderer groups
    val MSPKey = "MSP"

    // ConsensusTypeKey is the cb.ConfigItem type key name for the ConsensusType message.
    val ConsensusTypeKey = "ConsensusType"

    // BatchSizeKey is the cb.ConfigItem type key name for the BatchSize message.
    val BatchSizeKey = "BatchSize"

    // BatchTimeoutKey is the cb.ConfigItem type key name for the BatchTimeout message.
    val BatchTimeoutKey = "BatchTimeout"

    // ChannelRestrictionsKey is the key name for the ChannelRestrictions message.
    val ChannelRestrictionsKey = "ChannelRestrictions"

    // KafkaBrokersKey is the cb.ConfigItem type key name for the KafkaBrokers message.
    val KafkaBrokersKey = "KafkaBrokers"

    val BlockValidationPolicyKey = "BlockValidation"

    // ChannelCreationPolicyKey is the key used in the consortium config to denote the policy
    // to be used in evaluating whether a channel creation request is authorized
    val ChannelCreationPolicyKey = "ChannelCreationPolicy"
}

object CapabilityValue {
    val V1_1 = "V1_1"
    val V1_2 = "V1_2"
    val V1_3 = "V1_3"
}

case class ChannelDefinition(
    channelName: String,
    capabilities: Set[String],
    policies: PoliciesDefinition = PoliciesDefinition(
        admins = ImplicitPolicy(ImplicitMetaPolicy.Rule.MAJORITY, ImplicitSubPolicyValue.Admins), //ImplicitMetaPolicy.Rule.MAJORITY
        writers = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Writers),
        readers = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Readers)
    ),
    ordering: Option[OrderingServiceDefinition] = None,
    application: Option[ApplicationDefinition],
    consortiumDetails: ConsortiumDetails
)

sealed trait ConsortiumDetails

case class ConsortiumName(value: String) extends ConsortiumDetails

case class ConsortiumsDefinition(
    values: Iterable[ConsortiumDefinition]
) extends ConsortiumDetails

case class ConsortiumDefinition(
    name: String,
    organizations: Iterable[OrganizationDefinition]
)

case class OrderingServiceDefinition(
    maxMessageCount: Int,
    absoluteMaxBytes: Int,
    preferredMaxBytes: Int,
    batchTimeOut: String,
    maxChannelsCount: Long = 0,
    capabilities: Set[String],
    policies: PoliciesDefinition = PoliciesDefinition(
        admins = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Admins), //ImplicitMetaPolicy.Rule.MAJORITY
        writers = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Writers),
        readers = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Readers)
    ),
    orderingNodes: Iterable[OrderingNodeDefinition],
    organizations: Iterable[OrganizationDefinition]
)


case class OrderingNodeDefinition(
    host: String,
    port: Int,
    clientTlsCert: ByteString,
    serverTlsCert: ByteString
) {
    val address: String = s"$host:$port"
}

case class ApplicationDefinition(
    capabilities: Set[String],
    policies: PoliciesDefinition = PoliciesDefinition(
        admins = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Admins), //ImplicitMetaPolicy.Rule.MAJORITY
        writers = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Writers),
        readers = ImplicitPolicy(ImplicitMetaPolicy.Rule.ANY, ImplicitSubPolicyValue.Readers)
    ),
    organizations: Iterable[OrganizationDefinition]
)

case class OrganizationDefinition(
    mspId: String,
    policies: PoliciesDefinition,

    caCerts: Iterable[ByteString],
    intermediateCerts: Iterable[ByteString] = Seq.empty,

    tlsCACerts: Iterable[ByteString],
    intermediateTLSCerts: Iterable[ByteString] = Seq.empty,

    adminCerts: Iterable[ByteString],
    CRLs: Iterable[ByteString] = Seq.empty,

    nodesVerification: Option[NodeVerificationConfig] = None
) {

}

case class NodeVerificationConfig(
    clientOUValue: String,
    clientOUCerts: ByteString,
    peerOUValue: String,
    peerOUCerts: ByteString
)

sealed trait PolicyDefinition

case class PoliciesDefinition(
    admins: PolicyDefinition,
    writers: PolicyDefinition,
    readers: PolicyDefinition
)

trait ImplicitSubPolicyValue {
    def stringValue: String
}

object ImplicitSubPolicyValue {

    case object Readers extends ImplicitSubPolicyValue {
        def stringValue = "Readers"
    }

    case object Writers extends ImplicitSubPolicyValue {
        def stringValue = "Writers"
    }

    case object Admins extends ImplicitSubPolicyValue {
        def stringValue = "Admins"
    }

}

case class ImplicitPolicy(
    rule: Rule,
    value: ImplicitSubPolicyValue
) extends PolicyDefinition

case class SignedByOneOf(
    participants: Iterable[MemberClassifier]
) extends PolicyDefinition

object SignedByOneOf {
    def apply(participants: MemberClassifier*): SignedByOneOf = new SignedByOneOf(participants)
}

case class MemberClassifier(
    mspId: String,
    mspRoleType: MSPRole.MSPRoleType
)
