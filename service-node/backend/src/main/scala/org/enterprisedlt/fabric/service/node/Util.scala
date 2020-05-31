package org.enterprisedlt.fabric.service.node

import java.io._
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time._
import java.util.{Base64, Date}

import com.google.gson.{Gson, GsonBuilder}
import com.google.protobuf.{ByteString, MessageLite}
import javax.security.auth.x500.X500Principal
import javax.servlet.ServletRequest
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.{NoopHostnameVerifier, SSLConnectionSocketFactory, TrustAllStrategy}
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.{BCStyle, IETFUtils}
import org.enterprisedlt.fabric.service.node.endorsement.EndorsementPolicyCompiler
import org.enterprisedlt.fabric.service.node.rest.JsonServerCodec
import org.enterprisedlt.fabric.service.node.shared.ContractParticipant
import org.hyperledger.fabric.protos.common.Collection.{CollectionConfig, CollectionConfigPackage, CollectionPolicyConfig, StaticCollectionConfig}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope, Payload}
import org.hyperledger.fabric.protos.common.Configtx
import org.hyperledger.fabric.protos.common.Configtx.{ConfigEnvelope, ConfigGroup}
import org.hyperledger.fabric.protos.common.MspPrincipal.{MSPPrincipal, MSPRole}
import org.hyperledger.fabric.protos.common.Policies.{SignaturePolicy, SignaturePolicyEnvelope}
import org.hyperledger.fabric.protos.ext.orderer.Configuration.ConsensusType
import org.hyperledger.fabric.protos.ext.orderer.etcdraft.Configuration.ConfigMetadata
import org.hyperledger.fabric.sdk.{ChaincodeCollectionConfiguration, ChaincodeEndorsementPolicy}
import org.slf4j.{Logger, LoggerFactory}
import oshi.SystemInfo

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal

/**
 * @author Alexey Polubelov
 * @author Maxim Fedin
 */
object Util {
    private val logger = LoggerFactory.getLogger(this.getClass)

    //=========================================================================]
    def makeEndorsementPolicy(expression: String, parties: Array[ContractParticipant]): Either[String, ChaincodeEndorsementPolicy] = {
        val roles = parties.foldLeft(Map.empty[String, List[String]]) { case (r, c) =>
            r + (c.role -> (r.getOrElse(c.role, List.empty) :+ c.mspId))
        }
        EndorsementPolicyCompiler.compile(expression, roles(_))
    }

    //=========================================================================
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
    def policyAnyOf(members: Iterable[String]): ChaincodeEndorsementPolicy = {
        val signaturePolicy = signaturePolicyAnyMemberOf(members)
        val result = new ChaincodeEndorsementPolicy()
        result.fromBytes(signaturePolicy.toByteArray)
        result
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

    def base64Encode(bs: ByteString): String = Base64.getEncoder.encodeToString(bs.toByteArray)

    def base64Decode(b64: String): ByteString = ByteString.copyFrom(Base64.getDecoder.decode(b64))

    //=========================================================================
    def codec: Gson = (new GsonBuilder).create()

    def createCodec: () => JsonServerCodec = () => new JsonServerCodec(codec)

    //=========================================================================
    def setupLogging(logLevel: String): Unit = {
        LoggerFactory
          .getLogger(Logger.ROOT_LOGGER_NAME)
          .asInstanceOf[ch.qos.logback.classic.Logger]
          .setLevel(ch.qos.logback.classic.Level.toLevel(logLevel))
    }

    //=========================================================================
    def keyStoreToBase64(keyStore: KeyStore, password: String): String = {
        val buffer = new ByteArrayOutputStream(1536) // approx. estimated size is 1.5 Kb
        keyStore.store(buffer, password.toCharArray)
        Base64.getEncoder.encodeToString(buffer.toByteArray)
    }

    //=========================================================================
    def keyStoreFromBase64(encoded: String, password: String): KeyStore = {
        val decoded = Base64.getDecoder.decode(encoded)
        val keystore = KeyStore.getInstance("pkcs12")
        keystore.load(new ByteArrayInputStream(decoded), password.toCharArray)
        keystore
    }

    //=========================================================================
    def mkDirs(path: String): Boolean = new File(path).mkdirs()


    //=========================================================================
    def writeTextFile(filePath: String, content: String): Unit = {
        val writer = new FileWriter(filePath)
        writer.write(content)
        writer.close()
    }

    //=========================================================================
    def writeBase64BinaryFile(filePath: String, content: String): Unit = {
        val writer = new FileOutputStream(filePath)
        val binary = Base64.getDecoder.decode(content)
        writer.write(binary)
        writer.close()
    }

    //=========================================================================
    def getUserCertificate(request: ServletRequest): Option[X509Certificate] = {
        request.getAttribute("javax.servlet.request.X509Certificate") match {
            case x: Array[java.security.cert.X509Certificate] if x.length == 1 => x.headOption
            case _ => None
        }
    }

    //=========================================================================
    def getCNFromCertificate(cert: X509Certificate): Option[String] =
        Option(cert.getSubjectX500Principal)
          .map(_.getName(X500Principal.RFC1779))
          .map(x => new X500Name(x))
          .flatMap(x => Option(x.getRDNs(BCStyle.CN)))
          .flatMap(_.headOption)
          .flatMap(x => Option(x.getFirst).map(_.getValue))
          .map(IETFUtils.valueToString)

    //=========================================================================
    def certificateRDN(cert: X509Certificate, id: ASN1ObjectIdentifier): Option[String] =
        Option(cert.getSubjectX500Principal)
          .map(_.getName(X500Principal.RFC1779))
          .map(x => new X500Name(x))
          .flatMap(x => Option(x.getRDNs(id)))
          .flatMap(_.headOption)
          .flatMap(x => Option(x.getFirst).map(_.getValue))
          .map(IETFUtils.valueToString)


    //=========================================================================
    def executePostRequest[T](url: String, key: KeyStore, password: String, request: AnyRef, responseClass: Class[T]): T = {
        logger.info(s"Executing request to $url ...")
        val post = new HttpPost(url)
        val body = codec.toJson(request).getBytes(StandardCharsets.UTF_8)
        val entity = new ByteArrayEntity(body, ContentType.APPLICATION_JSON)
        post.setEntity(entity)
        val response = httpsClient(key, password).execute(post)
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

    //=========================================================================
    private def httpsClient(keyStore: KeyStore, password: String): CloseableHttpClient =
        HttpClients.custom()
          .setSSLSocketFactory(
              new SSLConnectionSocketFactory(
                  SSLContexts.custom()
                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                    .loadKeyMaterial(keyStore, password.toCharArray)
                    // TODO: .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                    .build(),
                  null,
                  null,
                  NoopHostnameVerifier.INSTANCE // TODO
              )
          ).build()

    def futureDate(shift: Period): Date = Date.from(LocalDate.now().plus(shift).atStartOfDay(ZoneOffset.UTC).toInstant)

    def parsePeriod(periodString: String): Period = Period.parse(periodString)

    import UnitsHelper._

    def getServerInfo: String = {
        val si = new SystemInfo()
        val hal = si.getHardware
        val available = hal.getMemory.getTotal
        val total = (available * 1d) / (1 GByte)
        val coreCount = hal.getProcessor.getLogicalProcessorCount
        val freq = (hal.getProcessor.getProcessorIdentifier.getVendorFreq * 1d) / (1 GHz)
        s"${coreCount}Core ${freq.formatted("%.2f")}GHz ${total.formatted("%.2f")}Gb"
    }


    //=========================================================================
    def untarFile(file: Array[Byte], destinationDir: String): Either[String, Unit] = {
        withResources(
            new TarArchiveInputStream(
                new GzipCompressorInputStream(
                    new ByteArrayInputStream(file)
                )
            )
        ) { tarIn =>
            new TarIterator(tarIn)
              .foreach { record =>
                  record.tarEntry.getCurrentEntry match {
                      case entry if entry.isDirectory =>
                          val f = new File(s"$destinationDir/${entry.getName}")
                          f.mkdirs()
                      case entry if entry.isFile =>
                          val outputFile = new File(s"$destinationDir/${entry.getName}")
                          IOUtils.copy(tarIn, new FileOutputStream(outputFile))
                  }

              }


            Right(())
        }
    }

    def getFileFromTar[T: ClassTag](tarFile: Array[Byte], filename: String): Either[String, T] =
        withResources(
            new TarArchiveInputStream(
                new GzipCompressorInputStream(
                    new ByteArrayInputStream(tarFile)
                )
            )
        ) { tarIterator =>
            new TarIterator(tarIterator)
              .find(_.name == filename)
              .map { tarRecord =>
                  Util.codec.fromJson(new InputStreamReader(tarRecord.tarEntry), classTag[T].runtimeClass)
                    .asInstanceOf[T]
              }.toRight("No file has been found")
        }


    def withResources[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
        val resource: T = r
        require(resource != null, "resource is null")
        var exception: Throwable = null
        try {
            f(resource)
        } catch {
            case NonFatal(e) =>
                exception = e
                throw e
        } finally {
            closeAndAddSuppressed(exception, resource)
        }
    }

    private def closeAndAddSuppressed(e: Throwable,
        resource: AutoCloseable): Unit = {
        if (e != null) {
            try {
                resource.close()
            } catch {
                case NonFatal(suppressed) =>
                    e.addSuppressed(suppressed)
            }
        } else {
            resource.close()
        }
    }

}

class TarIterator(val input: TarArchiveInputStream) extends Iterator[TarRecord] {
    var current: TarArchiveEntry = _

    override def hasNext: Boolean = {
        current = input.getNextTarEntry
        current != null
    }

    override def next(): TarRecord = if (current == null) null else {
        TarRecord(current.getName, input)
    }

}

case class TarRecord(
    name: String,
    tarEntry: TarArchiveInputStream
)


object ConversionHelper {

    implicit class EitherSeqOps[L, R](values: Iterable[Either[L, R]]) {

        def fold2Either: Either[L, Iterable[R]] =
            values.foldLeft[Either[L, Seq[R]]](Right(Seq.empty[R])) { case (r, i) =>
                for {
                    rv <- r
                    iv <- i
                } yield rv :+ iv
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

object UnitsHelper {

    implicit class UnitsSuffix(v: Int) {
        def KByte: Int = v * 1024

        def MByte: Int = v * 1024 * 1024

        def GByte: Int = v * 1024 * 1024 * 1024

        def KHz: Int = v * 1000

        def MHz: Int = v * 1000 * 1000

        def GHz: Int = v * 1000 * 1000 * 1000

    }

}

