package org.enterprisedlt.fabric.service.node

import java.security.KeyStore
import java.security.cert.X509Certificate

import org.enterprisedlt.fabric.service.node.identity.UserAccount

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {
    def createServiceTrustStore(password: String): KeyStore

    def createServiceUserKeyStore(name: String, password: String): KeyStore

    def createServiceTLSKeyStore(password: String): KeyStore // on launch
    // Common
    def findUser(user: X509Certificate): Either[String, UserAccount]

    // REST
    def getFabricUserKeyStore(name: String, password: String): KeyStore

    def createFabricUser(name: String): Unit
}
