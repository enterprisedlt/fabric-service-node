package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model.BootstrapOptions
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.JSON

/**
 * @author Alexey Polubelov
 */
object ServiceNodeRemote {

    def executeBootstrap(bootstrapSettings: BootstrapOptions): Future[Unit] = {
        Ajax
          .post("/admin/bootstrap", JSON.stringify(bootstrapSettings))
          .map { _ => () } // JSON.parse(xhr.responseText).asInstanceOf[String]
    }
}
