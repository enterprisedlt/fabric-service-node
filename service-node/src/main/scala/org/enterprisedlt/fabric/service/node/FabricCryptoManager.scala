package org.enterprisedlt.fabric.service.node

import org.hyperledger.fabric.sdk.User

/**
  * @author Alexey Polubelov
  */
trait FabricCryptoManager {
    def loadAdmin: User
}
