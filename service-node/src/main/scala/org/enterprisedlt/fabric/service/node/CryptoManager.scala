package org.enterprisedlt.fabric.service.node

import java.security.KeyStore

import org.hyperledger.fabric.sdk.User

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {
    def loadAdmin: User
    def createServiceTLSKeyStore(password: String): KeyStore
    def createServiceTrustStore(password: String): KeyStore
    def createFabricUser(name: String): Unit
    def getFabricUserKeyStore(name: String, password: String): KeyStore
    def createServiceUserKeyStore(name: String, password: String): KeyStore
}
