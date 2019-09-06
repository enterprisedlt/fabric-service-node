package org.enterprisedlt.fabric.service.node

import java.security.KeyStore

/**
  * @author Alexey Polubelov
  */
trait CryptoManager {
    // Common
    //    def createServiceUserKeyStore(name: String, password: String): KeyStore
    //
    //    def findUser(user: X509Certificate): Either[String, UserAccount]

    // REST
    def getFabricUserKeyStore(name: String, password: String): KeyStore // /admin/get-user-key

    def createFabricUser(name: String): Unit // /admin/create-user
}
