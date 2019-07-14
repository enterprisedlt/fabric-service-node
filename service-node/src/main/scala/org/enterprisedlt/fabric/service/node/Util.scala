package org.enterprisedlt.fabric.service.node

import java.io._
import java.nio.charset.StandardCharsets

import com.google.gson.{Gson, GsonBuilder}
import com.google.protobuf.{ByteString, MessageLite}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
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
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
object Util {
    private val logger = LoggerFactory.getLogger(this.getClass)

//    //=========================================================================
//    //TODO: this is adopted "copy paste" from SDK tests, quite ugly inefficient code, need to rewrite.
//    def generateTarGzInputStream(folderPath: File): InputStream = {
//        val sourceDirectory = folderPath
//        val bos = new ByteArrayOutputStream(500000)
//        val sourcePath = sourceDirectory.getAbsolutePath
//        val archiveOutputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(bos)))
//        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
//        try {
//            val childrenFiles = org.apache.commons.io.FileUtils.listFiles(sourceDirectory, null, true)
//            import scala.collection.JavaConverters._
//            for (childFile <- childrenFiles.asScala) {
//                val childPath = childFile.getAbsolutePath
//                var relativePath = childPath.substring(sourcePath.length + 1, childPath.length)
//                relativePath = Utils.combinePaths("src", relativePath)
//                relativePath = FilenameUtils.separatorsToUnix(relativePath)
//                val archiveEntry = new TarArchiveEntry(childFile, relativePath)
//                val fileInputStream = new FileInputStream(childFile)
//                try {
//                    archiveOutputStream.putArchiveEntry(archiveEntry)
//                    IOUtils.copy(fileInputStream, archiveOutputStream)
//                } finally {
//                    fileInputStream.close()
//                    archiveOutputStream.closeArchiveEntry()
//                }
//            }
//        } finally {
//            archiveOutputStream.close()
//        }
//        new ByteArrayInputStream(bos.toByteArray)
//    }

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

    //=========================================================================
    def parseCollectionPackage(configPackage: org.hyperledger.fabric.sdk.CollectionConfigPackage): Iterable[PrivateCollectionConfiguration] = {
        configPackage.getCollectionConfigs.asScala.map { config =>
            PrivateCollectionConfiguration(
                name = config.getName,
                blocksToLive = config.getBlockToLive,
                minPeersToSpread = config.getRequiredPeerCount,
                maxPeersToSpread = config.getMaximumPeerCount,
                memberIds = parseCollectionPolicy(config.getCollectionConfig.getStaticCollectionConfig.getMemberOrgsPolicy)
            )
        }
    }

    //=========================================================================
    def parseCollectionPolicy(policy: CollectionPolicyConfig): Iterable[String] = {
        policy.getSignaturePolicy.getIdentitiesList.asScala.map(_.getPrincipal.toStringUtf8)
    }

    //=========================================================================
    def storeToFile(path: String, protoMsg: MessageLite): Unit = {
        val parent = new File(path).getParentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val out = new FileOutputStream(path)
        try {
            protoMsg.writeTo(out)
            out.flush()
        } finally {
            out.close()
        }
    }

    //=========================================================================
    def storeToFile(path: String, msg: Array[Byte]): Unit = {
        val parent = new File(path).getParentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val out = new FileOutputStream(path)
        try {
            out.write(msg)
            out.flush()
        } finally {
            out.close()
        }
    }

    //=========================================================================
    def readAsByteString(path: String): ByteString =
        ByteString.readFrom(new FileInputStream(path))

    //=========================================================================
    def codec: Gson = (new GsonBuilder).create()

    //=========================================================================
    def setupLogging(logLevel: String): Unit = {
        LoggerFactory
          .getLogger(Logger.ROOT_LOGGER_NAME)
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.toLevel(logLevel))
    }

    //=========================================================================
    def executePostRequest[T](url: String, request: AnyRef, responseClass: Class[T]): T = {
        logger.info(s"Executing request to $url ...")
        val post = new HttpPost(url)
        val body = codec.toJson(request).getBytes(StandardCharsets.UTF_8)
        val entity = new ByteArrayEntity(body, ContentType.APPLICATION_JSON)
        post.setEntity(entity)
        val response = HttpClients.createDefault().execute(post)
        try {
            logger.info(s"Got status from remote: ${response.getStatusLine.toString}")
            val entity = response.getEntity
            val result = codec.fromJson(new InputStreamReader(entity.getContent), responseClass)
            EntityUtils.consume(entity) // ensure it is fully consumed
            result
        } finally {
            response.close()
        }
    }
}

case class PrivateCollectionConfiguration(
    name: String,
    memberIds: Iterable[String],
    blocksToLive: Long = 0, // Infinity by default
    minPeersToSpread: Int = 0, // not require to disseminate before commit
    maxPeersToSpread: Int = 0 // can be disseminated before commit
)
