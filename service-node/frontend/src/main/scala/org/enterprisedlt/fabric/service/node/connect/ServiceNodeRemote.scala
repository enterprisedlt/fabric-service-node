package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.shared.{BootstrapOptions, FabricServiceState, JoinOptions}
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Alexey Polubelov
  */
object ServiceNodeRemote {

    def getOrganisationFullName: Future[String] = Ajax
      .get("/service/organization-full-name")
      .map(_.responseText)
      .map(r => upickle.default.read[String](r))

    def getOrganisationMspId: Future[String] = Ajax
      .get("/service/organization-msp-id")
      .map(_.responseText)
      .map(r => upickle.default.read[String](r))


    def getServiceState: Future[FabricServiceState] = Ajax
      .get("/service/state")
      .map(_.responseText)
      .map(r => upickle.default.read[FabricServiceState](r))

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

    def createInvite: Future[String] = Ajax
      .get("/admin/create-invite")
      .map(_.responseText)

    def joinNetwork(joinRequest: JoinRequest): Future[Unit] = {
        val json = upickle.default.write(joinRequest)
        Ajax
          .post("/join-network", json)
          .map(_ => ())
    }

    def listContractPackages: Future[Array[String]] = Ajax
      .get("/admin/list-contract-packages")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[String]](r))


    def listBoxes: Future[Array[Box]] = Ajax
      .get("/service/list-boxes")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[Box]](r))

    def createContract(createContractRequest: CreateContractRequest): Future[Unit] = {
        val json = upickle.default.write(createContractRequest)
        Ajax.post("/admin/create-contract", json)
          .map(_ => ())
    }


    def listOrganizations: Future[Array[Organization]] = Ajax
      .get("/service/list-organizations")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[Organization]](r))


    def listContracts: Future[Array[Contract]] = Ajax
      .get("/service/list-contracts")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[Contract]](r))

    def listChannels: Future[Array[String]] = Ajax
      .get("/service/list-channels")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[String]](r))


    def contractJoin(contractJoinRequest: ContractJoinRequest): Future[String] = {
        val json = upickle.default.write(contractJoinRequest)
        Ajax.post("/admin/contract-join", json)
          .map(_.responseText)
    }


    def registerBox(request: RegisterBoxManager): Future[Box] = {
        val json = upickle.default.write(request)
        Ajax.post("/admin/register-box-manager", json)
        .map(_.responseText)
        .map(r => upickle.default.read[Box](r))

    }
}
