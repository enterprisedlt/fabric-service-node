package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.enterprisedlt.fabric.service.node.model.ComponentsState
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Andrew Pudovikov
 */
class PesistingStateManager extends StateManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def marshalNetworkState(state: ComponentsState): Either[String, Unit] = {
        logger.debug(s"persisting component's state")
        for {
            content <- Try(Util.codec.toJson(state)).toEither.left.map(_.getMessage)
            _ <- Try {
                logger.debug(s"state is : $content")
                storeStateToFile("state", content)
            }.toEither.left.map(_.getMessage)
        } yield ()
    }


    override def unmarshalNetworkState(): Either[String, ComponentsState] = {
        logger.debug(s"getting state from file")
        for {
            stateJson <- readStateFromFile("state")
            state <- Try(Util.codec.fromJson(stateJson,classOf[ComponentsState])).toEither.left.map(_.getMessage)
        } yield state
    }


    private def readStateFromFile(fileName: String): Either[String, String] = {
        Try {
            val file = new File(s"/opt/profile/state/$fileName.json")
            val r = Files.readAllBytes(Paths.get(file.toURI))
            new String(r, StandardCharsets.UTF_8)
        }.toEither.left.map(_.getMessage)
    }

    //=========================================================================
    private def storeStateToFile(fileName: String, payload: String): Unit = {
        val parent = new File(s"/opt/profile/state/$fileName.json").getParentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val out = new FileOutputStream(s"/opt/profile/state/$fileName.json")
        try {
            logger.debug(s"Saving state to file $fileName")
            val v = payload.getBytes(StandardCharsets.UTF_8)
            out.write(v)
            out.flush()
        } finally {
            out.close()
        }
    }
}
