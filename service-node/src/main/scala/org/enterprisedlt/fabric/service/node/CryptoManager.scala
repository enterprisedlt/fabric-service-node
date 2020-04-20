package org.enterprisedlt.fabric.service.node

import java.security.KeyStore
import java.security.cert.X509Certificate

import org.enterprisedlt.fabric.service.node.cryptography.{ComponentCerts, UserAccount}
import org.hyperledger.fabric.sdk.User

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {


    def generatePeerCrypto(peerName: String): ComponentCerts

    def generateOsnCrypto(osnName: String): ComponentCerts

    def loadDefaultAdmin: User

    def findUser(user: X509Certificate): Either[String, UserAccount]

    def createServiceTLSKeyStore(password: String): KeyStore

    def createServiceTrustStore(password: String): KeyStore

    def createFabricUser(name: String): Unit

    def getFabricUserKeyStore(name: String, password: String): KeyStore

    def createServiceUserKeyStore(name: String, password: String): KeyStore
}
