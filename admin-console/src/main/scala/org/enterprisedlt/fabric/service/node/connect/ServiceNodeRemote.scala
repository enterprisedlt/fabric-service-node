package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model.{BootstrapOptions, FabricServiceState, JoinOptions}
import org.scalajs.dom.ext.Ajax

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Alexey Polubelov
 */
object ServiceNodeRemote {

    def getOrganisationFullName: Future[String] = {
        Ajax
          .get("/service/organization-full-name")
          .map(_.responseText)
          .map(r => upickle.default.read[String](r))
    }

    def getServiceState: Future[FabricServiceState] = {
        Ajax
          .get("/service/state")
          .map(_.responseText)
          .map(r => upickle.default.read[FabricServiceState](r))
    }

    def executeBootstrap(bootstrapOptions: BootstrapOptions): Future[Unit] = {
        val json = upickle.default.write(bootstrapOptions)
        Ajax
          .post("/admin/bootstrap", json)
          .map { _ => () }
    }

    def executeJoin(joinOptions: JoinOptions): Future[Unit] = {
        val json = upickle.default.write(joinOptions)
        Ajax
          .post("/admin/request-join", json)
          .map { _ => () }
    }

    def createInvite: Future[String] = {
        Ajax
          .get("/admin/create-invite")
          .map(_.responseText)
    }
}
