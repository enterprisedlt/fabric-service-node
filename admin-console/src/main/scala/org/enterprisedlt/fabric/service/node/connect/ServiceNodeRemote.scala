package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model.{BootstrapOptions, JoinOptions}
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.JSON

/**
 * @author Alexey Polubelov
 */
object ServiceNodeRemote {

    def executeBootstrap(bootstrapOptions: BootstrapOptions): Future[Unit] = {
        Ajax
          .post("/admin/bootstrap", JSON.stringify(bootstrapOptions))
          .map { _ => () } // JSON.parse(xhr.responseText).asInstanceOf[String]
    }

    def executeJoin(joinOptions: JoinOptions): Future[Unit] = {
        Ajax
          .post("/admin/join", JSON.stringify(joinOptions))
          .map { _ => () } // JSON.parse(xhr.responseText).asInstanceOf[String]
    }
}
