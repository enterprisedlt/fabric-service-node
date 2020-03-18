package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.enterprisedlt.fabric.service.node.model.ServiceNodeState
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Andrew Pudovikov
 */
class PesistingStateManager(
    stateFilePath: String
) extends StateManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def marshalNetworkState(state: ServiceNodeState): Either[String, Unit] = {
        logger.debug(s"persisting component's state")
        for {
            state <- Try(Util.codec.toJson(state)).toEither.left.map(_.getMessage)
            _ <- Try(storeStateToFile(stateFilePath, state)).toEither.left.map(_.getMessage)
        } yield ()
    }


    override def unmarshalNetworkState(): Either[String, ServiceNodeState] = {
        logger.debug(s"getting state from file")
        for {
            stateJson <- readStateFromFile()
            state <- Try {
                logger.debug(s"during restoring parsted state $stateJson")
                Util.codec.fromJson(stateJson, classOf[ServiceNodeState])
            }.toEither.left.map(_.getMessage)
        } yield state
    }


    private def readStateFromFile(): Either[String, String] = {
        Try {
            val file = new File(stateFilePath)
            val r = Files.readAllBytes(Paths.get(file.toURI))
            new String(r, StandardCharsets.UTF_8)
        }.toEither.left.map(_.getMessage)
    }

    //=========================================================================
    private def storeStateToFile(stateFilePath: String, state: String): Unit = {
        val parent = new File(stateFilePath).getParentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val out = new FileOutputStream(stateFilePath)
        try {
            logger.debug(s"Saving state to file $stateFilePath")
            val s = state.getBytes(StandardCharsets.UTF_8)
            out.write(s)
            out.flush()
        } finally {
            out.close()
        }
    }
}
