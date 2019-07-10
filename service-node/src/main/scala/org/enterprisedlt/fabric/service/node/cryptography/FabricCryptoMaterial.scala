package org.enterprisedlt.fabric.service.node.cryptography

import java.io.{File, FileWriter}
import java.time.{LocalDate, ZoneOffset}
import java.util.Date

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig

/**
  * @author Alexey Polubelov
  */
object FabricCryptoMaterial {

    def generateOrgCrypto(orgConfig: OrganizationConfig, orgFullName: String, path: String, componentsName: String, componentsType: Option[String], components: Array[String]): Unit = {
        //    CA
        val caCert = FabricCryptoMaterial.generateCACert(
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country
        )
        val caDir = s"$path/ca"
        mkDir(caDir)
        writeToPemFile(s"$caDir/ca.$orgFullName-cert.pem", caCert.certificate)
        writeToPemFile(s"$caDir/ca_sk", caCert.key)

        //    TLS CA
        val tlscaCert = FabricCryptoMaterial.generateTLSCACert(
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country
        )
        val tlscaDir = s"$path/tlsca"
        mkDir(tlscaDir)
        writeToPemFile(s"$tlscaDir/tlsca.$orgFullName-cert.pem", tlscaCert.certificate)
        writeToPemFile(s"$tlscaDir/tlsca_sk", tlscaCert.key)

        //    Admin
        val adminCert = FabricCryptoMaterial.generateAdminCert(
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            caCert
        )
        val adminDir = s"$path/users/Admin@$orgFullName/msp"
        mkDir(s"$adminDir/admincerts")
        writeToPemFile(s"$adminDir/admincerts/Admin@$orgFullName-cert.pem", adminCert.certificate)

        mkDir(s"$adminDir/cacerts")
        writeToPemFile(s"$adminDir/cacerts/ca.$orgFullName-cert.pem", caCert.certificate)

        mkDir(s"$adminDir/keystore")
        writeToPemFile(s"$adminDir/keystore/admin_sk", adminCert.key)

        mkDir(s"$adminDir/signcerts")
        writeToPemFile(s"$adminDir/signcerts/Admin@$orgFullName-cert.pem", adminCert.certificate)

        mkDir(s"$adminDir/tlscacerts")
        writeToPemFile(s"$adminDir/tlscacerts/tlsca.$orgFullName-cert.pem", tlscaCert.certificate)

        // MSP
        val mspDir = s"$path/msp"
        mkDir(s"$mspDir/admincerts")
        writeToPemFile(s"$mspDir/admincerts/Admin@$orgFullName-cert.pem", adminCert.certificate)

        mkDir(s"$mspDir/cacerts")
        writeToPemFile(s"$mspDir/cacerts/ca.$orgFullName-cert.pem", caCert.certificate)

        mkDir(s"$mspDir/tlscacerts")
        writeToPemFile(s"$mspDir/tlscacerts/tlsca.$orgFullName-cert.pem", tlscaCert.certificate)

        components.foreach { name =>
            createComponentDir(orgConfig, orgFullName, name, componentsType, s"$path/$componentsName/$name.$orgFullName", caCert, tlscaCert, adminCert)
        }
    }

    private def createComponentDir(orgConfig: OrganizationConfig, orgFullName: String, component: String, componentType: Option[String], path: String, caCert: CertAndKey, tlscaCert: CertAndKey, adminCert: CertAndKey): Unit = {

        mkDir(s"$path/msp/admincerts")
        writeToPemFile(s"$path/msp/admincerts/Admin@$orgFullName-cert.pem", adminCert.certificate)

        mkDir(s"$path/msp/cacerts")
        writeToPemFile(s"$path/msp/cacerts/ca.$orgFullName-cert.pem", caCert.certificate)

        mkDir(s"$path/msp/tlscacerts")
        writeToPemFile(s"$path/msp/tlscacerts/tlsca.$orgFullName-cert.pem", tlscaCert.certificate)

        val theCert = FabricCryptoMaterial.generateComponentCert(
            componentName = component,
            organizationUnit = componentType,
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            caCert
        )
        mkDir(s"$path/msp/keystore")
        writeToPemFile(s"$path/msp/keystore/${component}_sk", theCert.key)

        mkDir(s"$path/msp/signcerts")
        writeToPemFile(s"$path/msp/signcerts/$component.$orgFullName-cert.pem", theCert.certificate)

        val tlsCert = FabricCryptoMaterial.generateComponentTlsCert(
            componentName = component,
            organization = orgFullName,
            location = orgConfig.location,
            state = orgConfig.state,
            country = orgConfig.country,
            tlscaCert
        )
        mkDir(s"$path/tls")
        writeToPemFile(s"$path/tls/ca.crt", tlscaCert.certificate)
        writeToPemFile(s"$path/tls/server.crt", tlsCert.certificate)
        writeToPemFile(s"$path/tls/server.key", tlsCert.key)
    }


    private def writeToPemFile(fileName: String, o: AnyRef): Unit = {
        val writer = new JcaPEMWriter(new FileWriter(fileName))
        writer.writeObject(o)
        writer.close()
    }

    private def mkDir(path: String): Boolean = new File(path).mkdirs()

    private def generateCACert(
        organization: String,
        location: String,
        state: String,
        country: String
    ): CertAndKey = {
        CryptoUtil.createSelfSignedCert(
            OrgMeta(
                name = s"ca.$organization",
                organization = Option(organization),
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
            Date.from(LocalDate.of(2035, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
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
        country: String
    ): CertAndKey = {
        CryptoUtil.createSelfSignedCert(
            OrgMeta(
                name = s"tlsca.$organization",
                organization = Option(organization),
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
            Date.from(LocalDate.of(2035, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
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
        signCert: CertAndKey
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"Admin@$organization",
                organizationUnit = Option("client"),
                location = Option(location),
                state = Option(state),
                country = Option(country),
            ),
            Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
            Date.from(LocalDate.of(2035, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
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
        signCert: CertAndKey
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"$componentName.$organization",
                organizationUnit = organizationUnit,
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
            Date.from(LocalDate.of(2035, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
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
        signCert: CertAndKey
    ): CertAndKey = {
        CryptoUtil.createSignedCert(
            OrgMeta(
                name = s"$componentName.$organization",
                location = Option(location),
                state = Option(state),
                country = Option(country)
            ),
            Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
            Date.from(LocalDate.of(2035, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant),
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