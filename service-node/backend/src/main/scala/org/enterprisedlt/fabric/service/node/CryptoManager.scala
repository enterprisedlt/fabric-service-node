package org.enterprisedlt.fabric.service.node

import java.security.KeyStore
import java.security.cert.X509Certificate

import org.enterprisedlt.fabric.service.node.cryptography.{Component, ComponentCerts, UserAccount}
import org.enterprisedlt.fabric.service.node.process.{CertAndKeyPEM, CustomComponentCerts, OrganizationCryptoMaterialPEM}
import org.hyperledger.fabric.sdk.User

/**
 * @author Alexey Polubelov
 */
trait CryptoManager {

    def getOrgCryptoMaterialPem: OrganizationCryptoMaterialPEM

    def generateCustomComponentCrypto(componentName:String): CustomComponentCerts

    def generateComponentCrypto(componentType: Component, componentName: String): ComponentCerts

    def saveComponentCrypto(componentType: Component, componentName: String, componentCerts: ComponentCerts): Unit

    def loadDefaultAdmin: User

    def findUser(user: X509Certificate): Either[String, UserAccount]

    def createServiceTLSKeyStore(password: String): KeyStore

    def createServiceTrustStore(password: String): KeyStore

    def createFabricUser(name: String): CertAndKeyPEM

    def getFabricUserKeyStore(name: String, password: String): KeyStore

    def createServiceUserKeyStore(name: String, password: String): KeyStore
}
