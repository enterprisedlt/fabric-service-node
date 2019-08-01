package org.enterprisedlt.fabric.service.node.cryptography

import java.io.{File, FileReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey}
import java.util.Collections

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.cryptography.FabricCryptoMaterial.writeToPemFile
import org.enterprisedlt.fabric.service.node.{CryptoManager, Util}
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
    override def loadDefaultAdmin: UserAccount =
        loadUser(
            "admin",
            config.organization.name,
            s"$rootDir/users/admin",
            AccountType.Fabric
        )

    //=========================================================================
    override def findUser(user: X509Certificate): Either[String, UserAccount] = {
        Util.certificateRDN(user, BCStyle.CN)
          .toRight("No CN in certificate")
          .flatMap { cn =>
              cn.split("@") match {
                  case Array(name, organization) if organization == orgFullName =>
                      findUser(s"$rootDir/users", name, AccountType.Fabric)

                  case Array(name, organization) if organization == s"service.$orgFullName" =>
                      findUser(s"$rootDir/service/users", name, AccountType.Service)

                  case _ => Left(s"Invalid CN: $cn")
              }
          }
    }

    //=========================================================================
    private def findUser(usersBaseDir: String, userName: String, aType: AccountType): Either[String, UserAccount] = {
        val userBaseDir = s"$usersBaseDir/$userName"
        val f = new File(userBaseDir)
        if (f.exists() && f.isDirectory) {
            Right(loadUser(userName, config.organization.name, userBaseDir, aType))
        } else {
            Left(s"Unknown user $userName")
        }
    }

    //=========================================================================
    override def createServiceTLSKeyStore(password: String): KeyStore = {
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
    override def createServiceTrustStore(password: String): KeyStore = {
        val keystore = KeyStore.getInstance("JKS")
        keystore.load(null)
        keystore.setCertificateEntry("ca", loadCertificateFromFile(s"$rootDir/ca/ca.crt")) // accept Fabric users
        keystore.setCertificateEntry("service-ca", loadCertificateFromFile(s"$rootDir/service/ca/server.crt")) // accept Service users
        keystore
    }

    //=========================================================================
    override def createFabricUser(name: String): Unit = {
        val orgConfig = config.organization
        val caCert = loadCertAndKey(s"$rootDir/ca/ca")
        val theCert = FabricCryptoMaterial.generateUserCert(
            userName = name,
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            caCert
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
    override def createServiceUserKeyStore(name: String, password: String): KeyStore = {
        val path = s"$rootDir/service"
        val orgConfig = config.organization
        val serviceCACert = loadCertAndKey(s"$path/ca/server")
        val theCert = FabricCryptoMaterial.generateUserCert(
            userName = name,
            organization = s"service.$orgFullName",
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            serviceCACert
        )
        val userDir = s"$path/users/$name"
        Util.mkDirs(userDir)
        writeToPemFile(s"$userDir/$name.crt", theCert.certificate)
        writeToPemFile(s"$userDir/$name.key", theCert.key)
        createP12KeyStoreWith(theCert, password)
    }

    //=========================================================================
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
