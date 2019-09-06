package org.enterprisedlt.fabric.service.node

import java.security.KeyStore

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {

    def getFabricUserKeyStore(name: String, password: String): KeyStore // /admin/get-user-key

    def createFabricUser(name: String): Unit // /admin/create-user
}
