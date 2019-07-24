package org.enterprisedlt.fabric.service.node.cryptography

import java.io.{File, FileReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import java.util.Collections

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.enterprisedlt.fabric.service.node.CryptoManager
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.hyperledger.fabric.sdk.User
import org.hyperledger.fabric.sdk.identity.X509Enrollment
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
          config.network.peerNodes.map(p => FabricComponent("peers", p.name, Option("peer")))
    )
    logger.info(s"Generated crypto for $orgFullName.")

    //=========================================================================
    // Methods
    //=========================================================================

    override def loadAdmin: User =
        loadUser(
            "Admin",
            config.organization.name,
            s"/opt/profile/crypto/users/admin"
        )

    //=========================================================================
    private def loadUser(userName: String, mspId: String, mspPath: String): User = {
        val signedCert = readPEMFile(s"$mspPath/$userName.crt")
        val privateKey = loadPrivateKeyFromFile(s"$mspPath/$userName.key")
        val enrollment = new X509Enrollment(privateKey, signedCert)
        FabricUserImpl(userName, Collections.emptySet(), "", "", enrollment, mspId)
    }

    //=========================================================================
    override def createServiceTLSKeyStore(password: String): KeyStore = {
        val keystore = KeyStore.getInstance("JKS")
        keystore.load(null)
        //
        val path = "/opt/profile/crypto/service/tls"
        val key = loadPrivateKeyFromFile(s"$path/server.key")
        val cert = loadCertificateFromFile(s"$path/server.crt")
        keystore.setKeyEntry("key", key, password.toCharArray, Array(cert))
        keystore
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

}
