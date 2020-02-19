package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model.{BootstrapOptions, FabricServiceState}
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author Alexey Polubelov
 */
object ServiceNodeRemote {

    def getServiceState(): Future[FabricServiceState] = {
        Future.successful(FabricServiceState(0))
    }

    def executeBootstrap(bootstrapOptions: BootstrapOptions): Future[Unit] = {
        val json = upickle.default.write(bootstrapOptions)
        println(json)
        Future.successful(())
        //        Ajax
        //          .post("/admin/bootstrap", json)
        //          .map { _ => () }
    }

    //    def executeJoin(joinOptions: JoinOptions): Future[Unit] = {
    //        Ajax
    //          .post("/admin/join", JSON.stringify(joinOptions))
    //          .map { _ => () } // JSON.parse(xhr.responseText).asInstanceOf[String]
    //    }
}
