package org.enterprisedlt.fabric.service.node.util

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import java.time._
import java.util.{Base64, Collections, Date}

import com.google.gson.{Gson, GsonBuilder}
import com.google.protobuf.{ByteString, MessageLite}
import javax.security.auth.x500.X500Principal
import javax.servlet.ServletRequest
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.{NoopHostnameVerifier, SSLConnectionSocketFactory, TrustAllStrategy}
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.{BCStyle, IETFUtils}
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.{JcaPEMKeyConverter, JcaPEMWriter}
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.enterprisedlt.fabric.service.node.configuration.{OrganizationConfig, ServiceConfig}
import org.hyperledger.fabric.protos.common.Collection.{CollectionConfig, CollectionConfigPackage, CollectionPolicyConfig, StaticCollectionConfig}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope, Payload}
import org.hyperledger.fabric.protos.common.Configtx
import org.hyperledger.fabric.protos.common.Configtx.{ConfigEnvelope, ConfigGroup}
import org.hyperledger.fabric.protos.common.MspPrincipal.{MSPPrincipal, MSPRole}
import org.hyperledger.fabric.protos.common.Policies.{SignaturePolicy, SignaturePolicyEnvelope}
import org.hyperledger.fabric.protos.orderer.Configuration.ConsensusType
import org.hyperledger.fabric.protos.orderer.etcdraft.Configuration.ConfigMetadata
import org.hyperledger.fabric.sdk.identity.X509Enrollment
import org.hyperledger.fabric.sdk.{ChaincodeCollectionConfiguration, ChaincodeEndorsementPolicy}
import org.enterprisedlt.fabric.service.node.util.FabricCryptoMaterial._
import org.slf4j.{Logger, LoggerFactory}
import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
object Util {
    private val logger = LoggerFactory.getLogger(this.getClass)

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

    def base64Encode(bs: ByteString): String = Base64.getEncoder.encodeToString(bs.toByteArray)

    def base64Decode(b64: String): ByteString = ByteString.copyFrom(Base64.getDecoder.decode(b64))

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

    //=========================================================================
    def findUser(organizationConfig: OrganizationConfig, user: X509Certificate, rootDir: String): Either[String, UserAccount] = {
        val orgFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        Util.certificateRDN(user, BCStyle.CN)
          .toRight("No CN in certificate")
          .flatMap { cn =>
              cn.split("@") match {
                  case Array(name, organization) if organization == orgFullName =>
                      findUser(organizationConfig, s"$rootDir/users", name, AccountType.Fabric)

                  case Array(name, organization) if organization == s"service.$orgFullName" =>
                      findUser(organizationConfig, s"$rootDir/service/users", name, AccountType.Service)

                  case _ => Left(s"Invalid CN: $cn")
              }
          }
    }

    //=========================================================================
    def findUser(organizationConfig: OrganizationConfig, usersBaseDir: String, userName: String, aType: AccountType): Either[String, UserAccount] = {
        val userBaseDir = s"$usersBaseDir/$userName"
        val f = new File(userBaseDir)
        if (f.exists() && f.isDirectory) {
            Right(loadUser(userName, organizationConfig.name, userBaseDir, aType))
        } else {
            Left(s"Unknown user $userName")
        }
    }

    private def loadUser(userName: String, mspId: String, mspPath: String, aType: AccountType): UserAccount = {
        val signedCert = readPEMFile(s"$mspPath/$userName.crt")
        val privateKey = loadPrivateKeyFromFile(s"$mspPath/$userName.key")
        val enrollment = new X509Enrollment(privateKey, signedCert)
        UserAccount(userName, Collections.emptySet(), "", "", enrollment, mspId, aType)
    }

    //=========================================================================
    private def loadPrivateKeyFromFile(fileName: String): PrivateKey = {
        val pemReader = new FileReader(fileName)
        val pemParser = new PEMParser(pemReader)
        try {
            pemParser.readObject() match {
                case pemKeyPair: PEMKeyPair => new JcaPEMKeyConverter().getKeyPair(pemKeyPair).getPrivate
                case keyInfo: PrivateKeyInfo => new JcaPEMKeyConverter().getPrivateKey(keyInfo)
                case null => throw new Exception(s"Unable to read PEM object")
                case other => throw new Exception(s"Unsupported PEM object ${other.getClass.getCanonicalName}")
            }
        } finally {
            pemParser.close()
            pemReader.close()
        }
    }

    //=========================================================================
    private def readPEMFile(fileName: String): String = {
        val file = new File(fileName)
        val r = Files.readAllBytes(Paths.get(file.toURI))
        new String(r, StandardCharsets.UTF_8)
    }

    //=========================================================================

    //=========================================================================
    def createServiceUserKeyStore(config: ServiceConfig, name: String, password: String, rootDir: String): KeyStore = {
        val orgFullName = s"${config.organization.name}.${config.organization.domain}"
        val notBefore = new Date
        val notAfter = Util.futureDate(Util.parsePeriod(config.certificateDuration))
        val path = s"$rootDir/service"
        val orgConfig = config.organization
        val serviceCACert = loadCertAndKey(s"$path/ca/server")
        val theCert = FabricCryptoMaterial.generateUserCert(
            userName = name,
            organization = s"service.$orgFullName",
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            signCert = serviceCACert,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val userDir = s"$path/users/$name"
        Util.mkDirs(userDir)
        writeToPemFile(s"$userDir/$name.crt", theCert.certificate)
        writeToPemFile(s"$userDir/$name.key", theCert.key)
        createP12KeyStoreWith(theCert, password)
    }

    //=========================================================================
    private def loadCertAndKey(path: String): CertAndKey = {
        CertAndKey(
            certificate = loadCertificateFromFile(s"$path.crt"),
            key = loadPrivateKeyFromFile(s"$path.key")
        )
    }

    //=========================================================================
    private def loadCertificateFromFile(fileName: String): X509Certificate = {
        val pemReader = new FileReader(fileName)
        val pemParser = new PEMParser(pemReader)
        try {
            pemParser.readObject() match {
                case holder: X509CertificateHolder => new JcaX509CertificateConverter().getCertificate(holder)
                case null => throw new Exception(s"Unable to read PEM object")
                case other => throw new Exception(s"Unsupported PEM object ${other.getClass.getCanonicalName}")
            }
        } finally {
            pemParser.close()
            pemReader.close()
        }
    }



    //=========================================================================
    private def createP12KeyStoreWith(cert: CertAndKey, password: String): KeyStore =
        createP12KeyStoreWith(cert.certificate, cert.key, password)


    //=========================================================================
    private def createP12KeyStoreWith(cert: X509Certificate, key: PrivateKey, password: String): KeyStore = {
        val keystore = KeyStore.getInstance("pkcs12")
        keystore.load(null)
        keystore.setKeyEntry("key", key, password.toCharArray, Array(cert))
        keystore
    }

}

case class PrivateCollectionConfiguration(
    name: String,
    memberIds: Iterable[String],
    blocksToLive: Long = 0, // Infinity by default
    minPeersToSpread: Int = 0, // not require to disseminate before commit
    maxPeersToSpread: Int = 0 // can be disseminated before commit
)

