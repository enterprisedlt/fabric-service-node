package org.enterprisedlt.fabric.service.node.identity

import java.io.{File, FileReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import java.util.Date

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.enterprisedlt.fabric.service.node.CryptoManager
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.identity.FabricCryptoMaterial.writeToPemFile
import org.enterprisedlt.fabric.service.node.util.{CertAndKey, Util}
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
class FileBasedCryptoManager(
    config: ServiceConfig,
    rootDir: String
) extends CryptoManager {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val orgFullName = s"${config.organization.name}.${config.organization.domain}"

    //
    // Initialization
    //

    logger.info(s"Generating crypto for $orgFullName...")
    FabricCryptoMaterial.generateOrgCrypto(
        config.organization, orgFullName, rootDir,
        config.network.orderingNodes.map(o => FabricComponent("orderers", o.name)) ++
          config.network.peerNodes.map(p => FabricComponent("peers", p.name, Option("peer"))),
        config.certificateDuration
    )
    logger.info(s"Generated crypto for $orgFullName.")

    //=========================================================================
    def createServiceTLSKeyStore(password: String): KeyStore = {
        val keystore = KeyStore.getInstance("JKS")
        keystore.load(null)
        //
        val path = s"$rootDir/service/tls"
        val key = loadPrivateKeyFromFile(s"$path/server.key")
        val cert = loadCertificateFromFile(s"$path/server.crt")
        keystore.setKeyEntry("key", key, password.toCharArray, Array(cert))
        keystore
    }

    //=========================================================================
    def createServiceTrustStore(password: String): KeyStore = {
        val keystore = KeyStore.getInstance("JKS")
        keystore.load(null)
        keystore.setCertificateEntry("ca", loadCertificateFromFile(s"$rootDir/ca/ca.crt")) // accept Fabric users
        keystore.setCertificateEntry("service-ca", loadCertificateFromFile(s"$rootDir/service/ca/server.crt")) // accept Service users
        keystore
    }

    //=========================================================================
    override def createFabricUser(name: String): Unit = {
        val notBefore = new Date
        val notAfter = Util.futureDate(Util.parsePeriod(config.certificateDuration))
        val orgConfig = config.organization
        val caCert = loadCertAndKey(s"$rootDir/ca/ca")
        val theCert = FabricCryptoMaterial.generateUserCert(
            userName = name,
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            signCert = caCert,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val userDir = s"$rootDir/users/$name"
        Util.mkDirs(userDir)
        writeToPemFile(s"$userDir/$name.crt", theCert.certificate)
        writeToPemFile(s"$userDir/$name.key", theCert.key)
    }

    //=========================================================================
    override def getFabricUserKeyStore(name: String, password: String): KeyStore = {
        createP12KeyStoreFromPems(s"$rootDir/users/$name/$name", password)
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
    private def loadCertAndKey(path: String): CertAndKey = {
        CertAndKey(
            certificate = loadCertificateFromFile(s"$path.crt"),
            key = loadPrivateKeyFromFile(s"$path.key")
        )
    }

    //=========================================================================
    private def createP12KeyStoreFromPems(path: String, password: String): KeyStore = {
        createP12KeyStoreWith(
            loadCertificateFromFile(s"$path.crt"),
            loadPrivateKeyFromFile(s"$path.key"),
            password
        )
    }

    //=========================================================================
    private def createP12KeyStoreWith(cert: X509Certificate, key: PrivateKey, password: String): KeyStore = {
        val keystore = KeyStore.getInstance("pkcs12")
        keystore.load(null)
        keystore.setKeyEntry("key", key, password.toCharArray, Array(cert))
        keystore
    }
}
