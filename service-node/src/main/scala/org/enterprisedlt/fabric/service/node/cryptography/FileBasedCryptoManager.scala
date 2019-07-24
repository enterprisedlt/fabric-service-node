package org.enterprisedlt.fabric.service.node.cryptography

import java.io.{File, FileReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.PrivateKey
import java.util.Collections

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.enterprisedlt.fabric.service.node.FabricCryptoManager
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
) extends FabricCryptoManager {
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
        val signedCert = loadSignedCertFromFile(s"$mspPath/$userName.crt")
        val privateKey = loadPrivateKeyFromFile(s"$mspPath/$userName.key")
        val enrollment = new X509Enrollment(privateKey, signedCert)
        FabricUserImpl(userName, Collections.emptySet(), "", "", enrollment, mspId)
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
    private def loadSignedCertFromFile(fileName: String): String = {
        val file = new File(fileName)
        val r = Files.readAllBytes(Paths.get(file.toURI))
        new String(r, StandardCharsets.UTF_8)
    }
}
