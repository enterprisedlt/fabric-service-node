package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model.{BootstrapOptions, FabricServiceState, JoinOptions}

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

    def executeJoin(joinOptions: JoinOptions): Future[Unit] = {
        val json = upickle.default.write(joinOptions)
        println(json)
        Future.successful(())
        //        Ajax
        //          .post("/admin/join", json)
        //          .map { _ => () } // JSON.parse(xhr.responseText).asInstanceOf[String]
    }
}
