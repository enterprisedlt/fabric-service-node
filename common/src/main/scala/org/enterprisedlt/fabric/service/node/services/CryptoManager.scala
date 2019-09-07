package org.enterprisedlt.fabric.service.node.services

import java.security.KeyStore

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {

    def getFabricUserKeyStore(name: String, password: String): KeyStore

    def createFabricUser(name: String): Unit
}
