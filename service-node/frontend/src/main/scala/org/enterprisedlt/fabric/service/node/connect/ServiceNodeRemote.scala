package org.enterprisedlt.fabric.service.node.connect

import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.shared._
import org.scalajs.dom.FormData
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author Alexey Polubelov
 */
object ServiceNodeRemote {

    def uploadCustomComponent(inputData: FormData): Future[Unit] = {
        Ajax
          .post(url = "/admin/upload-custom-component", data = inputData)
          .map(_.responseText)
          .map { _ => () }
    }


    def uploadContract(inputData: FormData): Future[Unit] = {
        Ajax
          .post(url = "/admin/upload-chaincode", data = inputData)
          .map(_.responseText)
          .map { _ => () }
    }


    def addCustomComponent(component: ComponentCandidate): Future[Unit] = {
        val json = upickle.default.write(component)
        Ajax
          .post("/admin/start-custom-node", json)
          .map { _ => () }
    }

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

    def listContractPackages: Future[Array[ContractDescriptor]] = Ajax
      .get("/admin/list-contract-packages")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[ContractDescriptor]](r))

    def listCustomComponentDescriptors: Future[Array[CustomComponentDescriptor]] = Ajax
      .get("/admin/list-custom-component-descriptors")
      .map(_.responseText)
      .map(r => upickle.default.read[Array[CustomComponentDescriptor]](r))

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

    def listChannels: Future[Array[String]] =
        Ajax
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

    def getNetworkConfig: Future[NetworkConfig] =
        Ajax
          .get("/admin/network-config")
          .map(_.responseText)
          .map { r =>
              upickle.default.read[NetworkConfig](r)
          }

    def listChainCodes: Future[Array[ChainCodeInfo]] =
        Ajax
          .get("/service/list-chain-codes")
          .map(_.responseText)
          .map(r => upickle.default.read[Array[ChainCodeInfo]](r))

    def createChannel(channelName: String): Future[String] = {
        val req = upickle.default.write(channelName)
        Ajax
          .post("/admin/create-channel", req)
          .map(_.responseText)
          .map(r => upickle.default.read[String](r))
    }

    def listEvents: Future[Events] =
        Ajax
          .get("/service/get-events")
          .map(_.responseText)
          .map(r => upickle.default.read[Events](r))


    def listComponentTypes: Future[Array[String]] =
        Ajax
          .get("/admin/list-custom-node-component-types")
          .map(_.responseText)
          .map(r => upickle.default.read[Array[String]](r))

}
