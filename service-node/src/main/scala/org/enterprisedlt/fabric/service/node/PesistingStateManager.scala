package org.enterprisedlt.fabric.service.node

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets

import org.enterprisedlt.fabric.service.node.model.ComponentsState
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Andrew Pudovikov
 */
class PesistingStateManager extends StateManager {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def persistNetworkState(state: ComponentsState): Either[String, Unit] = {
        logger.debug(s"persisting component's state")
        for {
            content <- Try(Util.codec.toJson(state)).toEither.left.map(_.getMessage)
            _ <- Try {
                logger.debug(s"state is : $content")
                storeStateToFile("state", content)
            }.toEither.left.map(_.getMessage)
        } yield ()
    }

    //=========================================================================
    def storeStateToFile(file: String, payload: String): Unit = {
        val parent = new File(s"/opt/profile/state/$file.json").getParentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val out = new FileOutputStream(s"/opt/profile/state/$file.json")
        try {
            logger.debug(s"Saving state to file $file")
            val v = payload.getBytes(StandardCharsets.UTF_8)
            out.write(v)
            out.flush()
        } finally {
            out.close()
        }
    }
}
