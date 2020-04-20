package org.enterprisedlt.fabric.service.node.cryptography

import java.math.BigInteger
import java.security._
import java.security.cert.X509Certificate
import java.util.Date

import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.{X500Name, X500NameBuilder}
import org.bouncycastle.asn1.x509._
import org.bouncycastle.asn1.{ASN1Encodable, ASN1ObjectIdentifier, DERSequence}
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509ExtensionUtils, JcaX509v3CertificateBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * @author Alexey Polubelov
 */
object CryptoUtil {
    private val random = new SecureRandom()

    def newKeyPair(): KeyPair = {
        val keypairGen = KeyPairGenerator.getInstance("ECDSA")
        keypairGen.initialize(256, random)
        keypairGen.generateKeyPair()
    }

    def createSelfSignedCert(
        subject: OrgMeta,
        notBefore: Date,
        notAfter: Date,
        extensions: Array[CertExtension]
    ): CertAndKey = {
        val keyPair = newKeyPair()
        val serial = new BigInteger(160, random)
        val x500subject = toX500Name(subject)
        val certificate = new JcaX509v3CertificateBuilder(
            x500subject,
            serial,
            notBefore,
            notAfter,
            x500subject,
            keyPair.getPublic
        )
        addExtensions(certificate, extensions)

        val extUtils = new JcaX509ExtensionUtils()
        val ski = extUtils.createSubjectKeyIdentifier(keyPair.getPublic)
        certificate.addExtension(Extension.subjectKeyIdentifier, false, ski)
        //        certificate.addExtension(Extension.authorityKeyIdentifier, false, id)
        // sign certificate:
        val signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate)
        val holder = certificate.build(signer)

        // convert to JRE certificate
        val x509cert = new JcaX509CertificateConverter().getCertificate(holder)
        CertAndKey(x509cert, keyPair.getPrivate)
    }

    def createSignedCert(
        subject: OrgMeta,
        notBefore: Date,
        notAfter: Date,
        extensions: Array[CertExtension],
        signBy: CertAndKey
    ): CertAndKey = {
        val keyPair = newKeyPair()
        val serial = new BigInteger(160, random)
        val x500subject = toX500Name(subject)
        val certificate = new JcaX509v3CertificateBuilder(
            signBy.certificate,
            serial,
            notBefore,
            notAfter,
            x500subject,
            keyPair.getPublic
        )
        addExtensions(certificate, extensions)

        val extUtils = new JcaX509ExtensionUtils()
        val ski = extUtils.createSubjectKeyIdentifier(keyPair.getPublic)
        certificate.addExtension(Extension.subjectKeyIdentifier, false, ski)
        val aki = extUtils.createAuthorityKeyIdentifier(signBy.certificate)
        certificate.addExtension(Extension.authorityKeyIdentifier, false, aki)

        // sign certificate:
        val signer = new JcaContentSignerBuilder("SHA256withECDSA").build(signBy.key)
        val holder = certificate.build(signer)

        // convert to JRE certificate
        val x509cert = new JcaX509CertificateConverter().getCertificate(holder)
        CertAndKey(x509cert, keyPair.getPrivate)
    }

    private def toX500Name(org: OrgMeta): X500Name = {
        val result = new X500NameBuilder(BCStyle.INSTANCE)
          .addRDN(BCStyle.CN, org.name)
        org.country.foreach(result.addRDN(BCStyle.C, _))
        org.state.foreach(result.addRDN(BCStyle.ST, _))
        org.location.foreach(result.addRDN(BCStyle.L, _))
        org.organization.foreach(result.addRDN(BCStyle.O, _))
        org.organizationUnit.foreach(result.addRDN(BCStyle.OU, _))
        result.build()
    }

    private def addExtensions(certificate: JcaX509v3CertificateBuilder, extensions: Array[CertExtension]): Unit = {
        extensions.groupBy(_.id).foreach { case (extId, extValues) =>
            val extInfo = foldById(extId, extValues)
            certificate.addExtension(extId, extInfo._1, extInfo._2)
        }
    }

    private def foldById(id: ASN1ObjectIdentifier, values: Array[CertExtension]): (Boolean, ASN1Encodable) = id match {
        case Extension.basicConstraints => values match {
            case Array(CACert(isCA)) => (true, new BasicConstraints(isCA))
            case other => throw new IllegalArgumentException(s"Only 1 basic constraint is allowed, but got ${other.toList}")
        }
        case Extension.keyUsage =>
            (true, new KeyUsage(values.map(_.asInstanceOf[CertUsage]).foldRight(0) { case (u, r) => u.v | r }))

        case Extension.extendedKeyUsage =>
            (false, new ExtendedKeyUsage(values.map(_.asInstanceOf[CertPurpose].purposeId)))

        case Extension.subjectAlternativeName =>
            (
              false,
              GeneralNames.getInstance(
                  new DERSequence(
                      values
                        .map(_.asInstanceOf[AlternativeName])
                        .map { an =>
                            new GeneralName(an.nameType, an.value)
                              .asInstanceOf[ASN1Encodable]
                        }
                  )
              )
            )

        case x => throw new UnsupportedOperationException(s"Unsupported ASN identifier $x")
    }
}

case class OrgMeta(
    name: String,
    organization: Option[String] = None,
    organizationUnit: Option[String] = None,
    location: Option[String] = None,
    state: Option[String] = None,
    country: Option[String] = None
)

case class CertAndKey(
    certificate: X509Certificate,
    key: PrivateKey
)

case class ComponentCerts(
    organizationCryptoMaterial: OrganizationCryptoMaterial,
    componentCert: CertAndKey,
    componentTLSCert: CertAndKey
)

trait Component

object Peer extends Component
object Orderer extends Component


trait CertExtension {
    def id: ASN1ObjectIdentifier
}

abstract class CertExtensionBase(
    identifier: ASN1ObjectIdentifier
) extends CertExtension {
    override def id: ASN1ObjectIdentifier = identifier
}

class CACert(
    val isCA: Boolean
) extends CertExtensionBase(Extension.basicConstraints)

object CACert {
    def unapply(value: CACert): Option[Boolean] = Option(value.isCA)
}

case object CertForCA extends CACert(true)

case object CertNotForCA extends CACert(false)

class CertUsage(val v: Int) extends CertExtensionBase(Extension.keyUsage)

case object UseForDigitalSignature extends CertUsage(KeyUsage.digitalSignature)

case object UseForEncipherment extends CertUsage(KeyUsage.keyEncipherment)

case object UseForCertSign extends CertUsage(KeyUsage.keyCertSign)

case object UseForCRLSign extends CertUsage(KeyUsage.cRLSign)

class CertPurpose(val purposeId: KeyPurposeId) extends CertExtensionBase(Extension.extendedKeyUsage)

case object UseForClientAuth extends CertPurpose(KeyPurposeId.id_kp_clientAuth)

case object UseForServerAuth extends CertPurpose(KeyPurposeId.id_kp_serverAuth)

class AlternativeName(val nameType: Int, val value: String) extends CertExtensionBase(Extension.subjectAlternativeName)

case class AlternativeDNSName(name: String) extends AlternativeName(GeneralName.dNSName, name)

case class AlternativeIPAddress(ip: String) extends AlternativeName(GeneralName.iPAddress, ip)


