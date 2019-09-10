package org.enterprisedlt.fabric.service.node

import java.io.ByteArrayOutputStream

import org.enterprisedlt.fabric.service.node.identity.FileBasedCryptoManager
import org.enterprisedlt.fabric.service.node.services.CryptoManager
import org.slf4j.LoggerFactory

/**
  * @author Maxim Fedin
  */
class IdentityRestEndpoint(
    cryptoManager: FileBasedCryptoManager) extends CryptoManager {
    private val logger = LoggerFactory.getLogger(this.getClass)


    override def getFabricUserKeyStore(userName: String, password: String): Either[String, Array[Byte]] = {
        logger.info(s"Obtaining user key for $userName ...")
        cryptoManager.getFabricUserKeyStore(userName, password).map { key =>
            val buffer = new ByteArrayOutputStream(1024)
            key.store(buffer, password.toCharArray)
            buffer.toByteArray
        }
    }


    override def createFabricUser(userName: String): Either[String, Unit] = {
        logger.info(s"Creating new user $userName ...")
        cryptoManager.createFabricUser(userName)
        logger.info(s"User $userName is created.")
        Right(())
    }
}
