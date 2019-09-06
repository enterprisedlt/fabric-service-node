package org.enterprisedlt.fabric.service.node.identity

import java.io.FileWriter
import java.util.Date

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.enterprisedlt.fabric.service.node.Util
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig

/**
  * @author Alexey Polubelov
  */
object FabricCryptoMaterial {

    def generateOrgCrypto(orgConfig: OrganizationConfig, orgFullName: String, path: String, components: Array[FabricComponent], certificateDuration: String): Unit = {
        val notBefore = new Date()
        val notAfter = Util.futureDate(Util.parsePeriod(certificateDuration))
        //    CA
        val caCert = FabricCryptoMaterial.generateCACert(
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val caDir = s"$path/ca"
        Util.mkDirs(caDir)
        writeToPemFile(s"$caDir/ca.crt", caCert.certificate)
        writeToPemFile(s"$caDir/ca.key", caCert.key)

        //    TLS CA
        val tlscaCert = FabricCryptoMaterial.generateTLSCACert(
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val tlscaDir = s"$path/tlsca"
        Util.mkDirs(tlscaDir)
        writeToPemFile(s"$tlscaDir/tlsca.crt", tlscaCert.certificate)
        writeToPemFile(s"$tlscaDir/tlsca.key", tlscaCert.key)

        //    Admin
        val adminCert = FabricCryptoMaterial.generateAdminCert(
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            signCert = caCert,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val adminDir = s"$path/users/admin/"
        Util.mkDirs(adminDir)
        writeToPemFile(s"$adminDir/admin.crt", adminCert.certificate)
        writeToPemFile(s"$adminDir/admin.key", adminCert.key)

        components.foreach { component =>
            createComponentDir(orgConfig, orgFullName, component, path, caCert, tlscaCert, adminCert, notBefore, notAfter)
        }

        createServiceDir(orgConfig, orgFullName, path, caCert, tlscaCert, notBefore, notAfter)
    }

    private def createComponentDir(
        orgConfig: OrganizationConfig,
        orgFullName: String,
        component: FabricComponent,
        path: String,
        caCert: CertAndKey,
        tlscaCert: CertAndKey,
        adminCert: CertAndKey,
        notBefore: Date,
        notAfter: Date
    ): Unit = {
        val outPath = s"$path/${component.group}/${component.name}.$orgFullName"
        Util.mkDirs(s"$outPath/msp/admincerts")
        writeToPemFile(s"$outPath/msp/admincerts/Admin@$orgFullName-cert.pem", adminCert.certificate)

        Util.mkDirs(s"$outPath/msp/cacerts")
        writeToPemFile(s"$outPath/msp/cacerts/ca.$orgFullName-cert.pem", caCert.certificate)

        Util.mkDirs(s"$outPath/msp/tlscacerts")
        writeToPemFile(s"$outPath/msp/tlscacerts/tlsca.$orgFullName-cert.pem", tlscaCert.certificate)

        val theCert = FabricCryptoMaterial.generateComponentCert(
            componentName = component.name,
            organizationUnit = component.unit,
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            signCert = caCert,
            notBefore = notBefore,
            notAfter = notAfter
        )
        Util.mkDirs(s"$outPath/msp/keystore")
        writeToPemFile(s"$outPath/msp/keystore/${component}_sk", theCert.key)

        Util.mkDirs(s"$outPath/msp/signcerts")
        writeToPemFile(s"$outPath/msp/signcerts/$component.$orgFullName-cert.pem", theCert.certificate)

        val tlsCert = FabricCryptoMaterial.generateComponentTlsCert(
            componentName = component.name,
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            signCert = tlscaCert,
            notBefore = notBefore,
            notAfter = notAfter
        )
        Util.mkDirs(s"$outPath/tls")
        writeToPemFile(s"$outPath/tls/ca.crt", tlscaCert.certificate)
        writeToPemFile(s"$outPath/tls/server.crt", tlsCert.certificate)
        writeToPemFile(s"$outPath/tls/server.key", tlsCert.key)
    }

    private def createServiceDir(
        orgConfig: OrganizationConfig,
        organizationFullName: String,
        path: String,
        caCert: CertAndKey,
        tlscaCert: CertAndKey,
        notBefore: Date,
        notAfter: Date
    ): Unit = {
        val outPath = s"$path/service"

        val tlsCert = FabricCryptoMaterial.generateComponentTlsCert(
            componentName = "service",
            organization = organizationFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            signCert = tlscaCert,
            notBefore = notBefore,
            notAfter = notAfter
        )
        Util.mkDirs(s"$outPath/tls")
        writeToPemFile(s"$outPath/tls/server.crt", tlsCert.certificate)
        writeToPemFile(s"$outPath/tls/server.key", tlsCert.key)

        val serviceCACert = FabricCryptoMaterial.generateCACert(
            organization = s"service.$organizationFullName",
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            notBefore = notBefore,
            notAfter = notAfter
        )
        Util.mkDirs(s"$outPath/ca")
        writeToPemFile(s"$outPath/ca/server.crt", serviceCACert.certificate)
        writeToPemFile(s"$outPath/ca/server.key", serviceCACert.key)
    }

    def writeToPemFile(fileName: String, o: AnyRef): Unit = {
        val writer = new JcaPEMWriter(new FileWriter(fileName))
        writer.writeObject(o)
        writer.close()
    }

    private def generateCACert(
        organization: String,
        location: String,
        state: String,
        country: String,
        notBefore: Date,
        notAfter: Date
    ): CertAndKey = {
        CryptoUtil.createSelfSignedCert(
            OrgMeta(
                name = s"ca.$organization",
                organization = Option(organization),
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            notBefore,
            notAfter,
            Array(
                CertForCA,
                UseForDigitalSignature,
                UseForEncipherment,
                UseForCertSign,
                UseForCRLSign,
                UseForClientAuth,
                UseForServerAuth
            )
        )
    }

    private def generateTLSCACert(
        organization: String,
        location: String,
        state: String,
        country: String,
        notBefore: Date,
        notAfter: Date
    ): CertAndKey = {
        CryptoUtil.createSelfSignedCert(
            OrgMeta(
                name = s"tlsca.$organization",
                organization = Option(organization),
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            notBefore,
            notAfter,
            Array(
                CertForCA,
                UseForDigitalSignature,
                UseForEncipherment,
                UseForCertSign,
                UseForCRLSign,
                UseForClientAuth,
                UseForServerAuth
            )
        )
    }

    private def generateAdminCert(
        organization: String,
        location: String,
        state: String,
        country: String,
        signCert: CertAndKey,
        notBefore: Date,
        notAfter: Date
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"admin@$organization",
                organizationUnit = Option("client"),
                location = Option(location),
                state = Option(state),
                country = Option(country),
            ),
            notBefore: Date,
            notAfter: Date,
            Array(
                CertNotForCA,
                UseForDigitalSignature
            ),
            signCert
        )
    }

    def generateUserCert(
        userName: String,
        organization: String,
        location: String,
        state: String,
        country: String,
        signCert: CertAndKey,
        notBefore: Date,
        notAfter: Date
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"$userName@$organization",
                organizationUnit = Option("client"),
                location = Option(location),
                state = Option(state),
                country = Option(country),
            ),
            notBefore,
            notAfter,
            Array(
                CertNotForCA,
                UseForDigitalSignature
            ),
            signCert
        )
    }

    private def generateComponentCert(
        componentName: String,
        organizationUnit: Option[String],
        organization: String,
        location: String,
        state: String,
        country: String,
        signCert: CertAndKey,
        notBefore: Date,
        notAfter: Date
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"$componentName.$organization",
                organizationUnit = organizationUnit,
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            notBefore,
            notAfter,
            Array(
                CertNotForCA,
                UseForDigitalSignature
            ),
            signCert
        )
    }

    private def generateComponentTlsCert(
        componentName: String,
        organization: String,
        location: String,
        state: String,
        country: String,
        signCert: CertAndKey,
        notBefore: Date,
        notAfter: Date
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"$componentName.$organization",
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            notBefore,
            notAfter,
            Array(
                CertNotForCA,
                UseForDigitalSignature,
                UseForEncipherment,
                UseForClientAuth,
                UseForServerAuth,
                AlternativeDNSName(s"$componentName.$organization"),
                AlternativeDNSName(componentName),
            ),
            signCert
        )
    }

}

case class FabricComponent(
    group: String,
    name: String,
    unit: Option[String] = None
)
