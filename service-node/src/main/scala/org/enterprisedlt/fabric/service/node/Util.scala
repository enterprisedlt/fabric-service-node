package org.enterprisedlt.fabric.service.node

import java.io._

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.hyperledger.fabric.protos.common.Collection.{CollectionConfig, CollectionConfigPackage, CollectionPolicyConfig, StaticCollectionConfig}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope, Payload}
import org.hyperledger.fabric.protos.common.Configtx
import org.hyperledger.fabric.protos.common.Configtx.{ConfigEnvelope, ConfigGroup}
import org.hyperledger.fabric.protos.common.MspPrincipal.{MSPPrincipal, MSPRole}
import org.hyperledger.fabric.protos.common.Policies.{SignaturePolicy, SignaturePolicyEnvelope}
import org.hyperledger.fabric.protos.ext.orderer.Configuration.ConsensusType
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration.ConfigMetadata
import org.hyperledger.fabric.sdk.helper.Utils
import org.hyperledger.fabric.sdk.{ChaincodeCollectionConfiguration, ChaincodeEndorsementPolicy}

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
object Util {

    //=========================================================================
    //TODO: this is adopted "copy paste" from SDK tests, quite ugly inefficient code, need to rewrite.
    def generateTarGzInputStream(folderPath: File): InputStream = {
        val sourceDirectory = folderPath
        val bos = new ByteArrayOutputStream(500000)
        val sourcePath = sourceDirectory.getAbsolutePath
        val archiveOutputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(bos)))
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
        try {
            val childrenFiles = org.apache.commons.io.FileUtils.listFiles(sourceDirectory, null, true)
            import scala.collection.JavaConverters._
            for (childFile <- childrenFiles.asScala) {
                val childPath = childFile.getAbsolutePath
                var relativePath = childPath.substring(sourcePath.length + 1, childPath.length)
                relativePath = Utils.combinePaths("src", relativePath)
                relativePath = FilenameUtils.separatorsToUnix(relativePath)
                val archiveEntry = new TarArchiveEntry(childFile, relativePath)
                val fileInputStream = new FileInputStream(childFile)
                try {
                    archiveOutputStream.putArchiveEntry(archiveEntry)
                    IOUtils.copy(fileInputStream, archiveOutputStream)
                } finally {
                    fileInputStream.close()
                    archiveOutputStream.closeArchiveEntry()
                }
            }
        } finally {
            archiveOutputStream.close()
        }
        new ByteArrayInputStream(bos.toByteArray)
    }

    //=========================================================================
    def policyAnyOf(members: Iterable[String]): ChaincodeEndorsementPolicy = {
        val signaturePolicy = signaturePolicyAnyMemberOf(members)
        val result = new ChaincodeEndorsementPolicy()
        result.fromBytes(signaturePolicy.toByteArray)
        result
    }

    def signaturePolicyAnyMemberOf(members: Iterable[String]): SignaturePolicyEnvelope = {
        val rules = SignaturePolicy.NOutOf.newBuilder.setN(1)
        val identities = members.zipWithIndex.map { case (member, index) =>
            rules.addRules(SignaturePolicy.newBuilder.setSignedBy(index))
            MSPPrincipal.newBuilder()
              .setPrincipalClassification(MSPPrincipal.Classification.ROLE)
              .setPrincipal(
                  MSPRole.newBuilder
                    .setRole(MSPRole.MSPRoleType.MEMBER)
                    .setMspIdentifier(member)
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

    //=========================================================================
    def createCollectionsConfig(collections: Iterable[PrivateCollectionConfiguration]): ChaincodeCollectionConfiguration = {
        val collectionsPackage = CollectionConfigPackage.newBuilder()
        collections.foreach { collectionConfig =>
            collectionsPackage.addConfig(
                CollectionConfig.newBuilder
                  .setStaticCollectionConfig(
                      StaticCollectionConfig.newBuilder
                        .setName(collectionConfig.name)
                        .setMemberOrgsPolicy(
                            CollectionPolicyConfig.newBuilder
                              .setSignaturePolicy(
                                  signaturePolicyAnyMemberOf(collectionConfig.memberIds)
                              )
                              .build
                        )
                        .setBlockToLive(collectionConfig.blocksToLive)
                        .setRequiredPeerCount(collectionConfig.minPeersToSpread)
                        .setMaximumPeerCount(collectionConfig.maxPeersToSpread)
                        .build
                  )
                  .build
            )
        }
        ChaincodeCollectionConfiguration.fromCollectionConfigPackage(collectionsPackage.build)
    }

    //=========================================================================
    def loadConfigurationBlock(input: InputStream): Block = Block.parseFrom(input)

    //=========================================================================
    def extractConsensusMetadata(config: Configtx.Config): ConfigMetadata = {
        val orderer: ConfigGroup.Builder = config.getChannelGroup.getGroupsMap.get("Orderer").toBuilder
        val consensusType = ConsensusType.parseFrom(orderer.getValuesMap.get("ConsensusType").getValue)
        ConfigMetadata.parseFrom(consensusType.getMetadata)
    }

    //=========================================================================
    def extractConfig(block: Block): Configtx.Config = {
        val envelope = Envelope.parseFrom(block.getData.getData(0))
        val payload = Payload.parseFrom(envelope.getPayload)
        val configEnvelope = ConfigEnvelope.parseFrom(payload.getData)
        configEnvelope.getConfig
    }

}

case class PrivateCollectionConfiguration(
    name: String,
    memberIds: Iterable[String],
    blocksToLive: Int = 0, // Infinity by default
    minPeersToSpread: Int = 0, // not require to disseminate before commit
    maxPeersToSpread: Int = 0 // can be disseminated before commit
)
