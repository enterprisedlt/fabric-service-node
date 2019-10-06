package org.enterprisedlt.fabric.service.node

import java.io.{BufferedInputStream, File, FileInputStream, FileReader}
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.atomic.AtomicReference

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.model.Contract
import org.enterprisedlt.fabric.service.node.auth.FabricAuthenticator
import org.enterprisedlt.fabric.service.node.configuration._
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.flow.{Bootstrap, Join}
import org.enterprisedlt.fabric.service.node.model._
import org.hyperledger.fabric.sdk.User
import org.slf4j.LoggerFactory

import scala.util.Try

/**
  * @author Alexey Polubelov
  */
class RestEndpoint(
    bindPort: Int,
    externalAddress: Option[ExternalAddress],
    organizationConfig: OrganizationConfig,
    cryptoManager: CryptoManager,
    hostsManager: HostsManager,
    profilePath: String,
    dockerSocket: String,
    state: AtomicReference[FabricServiceState]
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)

    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        implicit val user: Option[User] = FabricAuthenticator.getFabricUser(request)
        request.getMethod match {
            case "GET" =>
                request.getPathInfo match {
                    case "/service/organization-msp-id" =>
                        response.getWriter.println(Util.codec.toJson(organizationConfig.name))
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/state" =>
                        response.getWriter.println(Util.codec.toJson(state.get()))
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-organizations" =>
                        logger.info(s"ListOrganizations ...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { manager =>
                                  manager.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-collections" =>
                        logger.info(s"Collections ...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listCollections")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/admin/create-invite" =>
                        logger.info(s"Creating invite ${organizationConfig.name}...")
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        val out = response.getOutputStream
                        val address = externalAddress
                          .map(ea => s"${ea.host}:${ea.port}")
                          .getOrElse(s"service.${organizationConfig.name}.${organizationConfig.domain}:$bindPort")
                        //TODO: password should be taken from request
                        val password = "join me"
                        val key = cryptoManager.createServiceUserKeyStore(s"join-${System.currentTimeMillis()}", password)
                        val invite = Invite(
                            address,
                            Util.keyStoreToBase64(key, password)
                        )
                        out.println(Util.codec.toJson(invite))
                        out.flush()
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/admin/create-user" =>
                        val userName = request.getParameter("name")
                        logger.info(s"Creating new user $userName ...")
                        cryptoManager.createFabricUser(userName)
                        response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                        response.getWriter.println("OK")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/admin/get-user-key" =>
                        val userName = request.getParameter("name")
                        val password = request.getParameter("password")
                        logger.info(s"Obtaining user key for $userName ...")
                        val key = cryptoManager.getFabricUserKeyStore(userName, password)
                        response.setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType)
                        key.store(response.getOutputStream, password.toCharArray)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-messages" =>
                        logger.info(s"Querying messages for ${organizationConfig.name}...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listMessages")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-confirmations" =>
                        logger.info(s"Querying confirmations for ${organizationConfig.name}...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContractConfirmations")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-contracts" =>
                        logger.info(s"Querying contracts for ${organizationConfig.name}...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)


                    // unknown GET path
                    case path =>
                        logger.info(s"Unknown path: $path")
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                }
            case "POST" =>

                request.getPathInfo match {
                    case "/admin/bootstrap" =>
                        logger.info(s"Bootstrapping organization ${organizationConfig.name}...")
                        val start = System.currentTimeMillis()
                        try {
                            val bootstrapOptions = Util.codec.fromJson(request.getReader, classOf[BootstrapOptions])
                            state.set(FabricServiceState(FabricServiceState.BootstrapStarted))
                            init(Bootstrap.bootstrapOrganization(
                                organizationConfig,
                                bootstrapOptions,
                                cryptoManager,
                                hostsManager,
                                externalAddress,
                                profilePath,
                                dockerSocket,
                                state)
                            )
                            val end = System.currentTimeMillis() - start
                            logger.info(s"Bootstrap done ($end ms)")
                            response.setStatus(HttpServletResponse.SC_OK)
                        } catch {
                            case ex: Exception =>
                                logger.error("Bootstrap failed:", ex)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/join-network" =>
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              val joinRequest = Util.codec.fromJson(request.getReader, classOf[JoinRequest])
                              Join.joinOrgToNetwork(
                                  state,
                                  cryptoManager,
                                  joinRequest,
                                  hostsManager
                              )
                          } match {
                            case Right(joinResponse) =>
                                val out = response.getOutputStream
                                out.print(Util.codec.toJson(joinResponse))
                                out.flush()
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/admin/request-join" =>
                        logger.info("Requesting to joining network ...")
                        val start = System.currentTimeMillis()
                        val joinOptions = Util.codec.fromJson(request.getReader, classOf[JoinOptions])
                        state.set(FabricServiceState(FabricServiceState.JoinStarted))
                        init(Join.join(organizationConfig,
                            cryptoManager,
                            joinOptions,
                            externalAddress,
                            hostsManager,
                            profilePath,
                            dockerSocket,
                            state)
                        )
                        val end = System.currentTimeMillis() - start
                        logger.info(s"Joined ($end ms)")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/admin/create-contract" =>
                        logger.info("Creating contract ...")
                        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
                        val createContractRequest = Util.codec.fromJson(request.getReader, classOf[CreateContractRequest])
                        logger.info(s"createContractRequest =  $createContractRequest")
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              logger.info(s"[ $organizationFullName ] - Preparing ${createContractRequest.name} chain code ...")
                              val filesBaseName = s"${createContractRequest.contractType}-${createContractRequest.version}"
                              val chainCodeName = s"${createContractRequest.name}-${createContractRequest.version}"
                              val deploymentDescriptor = Util.codec.fromJson(new FileReader(s"/opt/profile/chain-code/$filesBaseName.json"), classOf[ContractDeploymentDescriptor])
                              val path = s"/opt/profile/chain-code/$filesBaseName.tgz"
                              for {
                                  file <- Option(new File(path)).filter(_.exists()).toRight(s"File $filesBaseName.tgz doesn't exist")
                                  chainCodePkg <- Option(new BufferedInputStream(new FileInputStream(file))).toRight(s"Can't prepare cc pkg stream")
                                  _ <- {
                                      logger.info(s"[ $organizationFullName ] - Installing $chainCodeName chain code ...")
                                      state.networkManager.installChainCode(ServiceChannelName, createContractRequest.name, createContractRequest.version, chainCodePkg)
                                  }
                                  _ <- {
                                      logger.info(s"[ $organizationFullName ] - Instantiating $chainCodeName chain code ...")
                                      val endorsementPolicy = Util.policyAnyOf(
                                          deploymentDescriptor.endorsement
                                            .map(r => createContractRequest.parties.find(_.role == r).map(_.mspId).get)
                                      )
                                      val collections = deploymentDescriptor.collections.map { cd =>
                                          PrivateCollectionConfiguration(
                                              name = cd.name,
                                              memberIds = cd.members.map(m =>
                                                  createContractRequest.parties.find(_.role == m).map(_.mspId).get
                                              )
                                          )
                                      }
                                      state.networkManager.instantiateChainCode(
                                          ServiceChannelName, createContractRequest.name,
                                          createContractRequest.version,
                                          endorsementPolicy = Option(endorsementPolicy),
                                          collectionConfig = Option(Util.createCollectionsConfig(collections)),
                                          arguments = createContractRequest.initArgs
                                      )
                                  }
                                  response <- {
                                      logger.info(s"Invoking 'createContract' method...")
                                      val contract = CreateContract(createContractRequest.contractType,
                                          createContractRequest.name,
                                          createContractRequest.version,
                                          createContractRequest.parties.map(_.mspId)
                                      )
                                      state.networkManager.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "createContract", Util.codec.toJson(contract))
                                  }
                                  result <- Try(response.get()).toEither.left.map(_.getMessage)
                              } yield result
                          } match {
                            case Right(answer) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(answer)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(err) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(err)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/admin/contract-join" =>
                        logger.info("Joining deployed contract ...")
                        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
                        val joinReq = Util.codec.fromJson(request.getReader, classOf[ContractJoinRequest])
                        logger.info(s"joinReq is $joinReq")
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              for {
                                  queryResult <- {
                                      state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getContract", joinReq.name, joinReq.founder)
                                        .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight(s"There is an error with querying getContract method in system chain-code"))
                                  }
                                  contractDetails <- {
                                      logger.debug(s"queryResult is $queryResult")
                                      Option(Util.codec.fromJson(queryResult, classOf[Contract])).filter(_ != null).toRight(s"Can't parse response from getContract")
                                  }
                                  file <- {
                                      val path = s"/opt/profile/chain-code/${contractDetails.chainCodeName}-${contractDetails.chainCodeVersion}.tgz"
                                      Option(new File(path)).filter(_.exists()).toRight(s"File  doesn't exist ")
                                  }
                                  _ <- {
                                      val chainCodePkg = new BufferedInputStream(new FileInputStream(file))
                                      logger.info(s"[ $organizationFullName ] - Installing ${contractDetails.chainCodeName}:${contractDetails.chainCodeVersion} chaincode ...")
                                      state.networkManager.installChainCode(ServiceChannelName, contractDetails.chainCodeName, contractDetails.chainCodeVersion, chainCodePkg)
                                  }
                              } yield {
                                  state.networkManager.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "delContract", joinReq.name, joinReq.founder)
                              } match {
                                  case Right(invokeResult) =>
                                      invokeResult.get()
                                      response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                      response.getWriter.println(invokeResult)
                                      response.setStatus(HttpServletResponse.SC_OK)
                                  case Left(err) =>
                                      response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                      response.getWriter.println(err)
                                      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                              }
                          }


                    case "/service/send-message" =>
                        val message = Util.codec.fromJson(request.getReader, classOf[SendMessageRequest])
                        logger.info(s"Sending message to ${message.to} ...")
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              state.networkManager.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "putMessage", Util.codec.toJson(message))
                          } match {
                            case Right(answer) =>
                                answer.get()
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(answer)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(err) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(err)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }


                    case "/service/get-message" =>
                        val messageRequest = Util.codec.fromJson(request.getReader, classOf[GetMessageRequest])
                        logger.info("Obtaining message ...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getMessage", messageRequest.messageKey, messageRequest.sender)
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/del-message" =>
                        val delMessageRequest = Util.codec.fromJson(request.getReader, classOf[DeleteMessageRequest])
                        logger.info("Requesting for deleting message ...")
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  state.networkManager.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "delMessage", delMessageRequest.messageKey, delMessageRequest.sender)
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/call-contract" =>
                        logger.info("Processing request to contract ...")
                        val contractRequest = Util.codec.fromJson(request.getReader, classOf[CallContractRequest])
                        val result =
                            globalState
                              .toRight("Node is not initialized yet")
                              .flatMap { state =>
                                  contractRequest.callType match {
                                      case "query" =>
                                          state.networkManager
                                            .queryChainCode(
                                                ServiceChannelName,
                                                contractRequest.contractName,
                                                contractRequest.functionName,
                                                contractRequest.arguments: _*
                                            )
                                            .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))

                                      case "invoke" =>
                                          import scala.collection.JavaConverters._
                                          implicit val transient: Option[util.Map[String, Array[Byte]]] =
                                              Option(contractRequest.transient)
                                                .map(_.asScala.mapValues(_.getBytes(StandardCharsets.UTF_8)).asJava)

                                          state.networkManager
                                            .invokeChainCode(
                                                ServiceChannelName,
                                                contractRequest.contractName,
                                                contractRequest.functionName,
                                                contractRequest.arguments: _*
                                            )
                                            .flatMap { futureResult =>
                                                if (contractRequest.awaitTransaction) {
                                                    try {
                                                        futureResult.get()
                                                        Right(""""OK"""")
                                                    } catch {
                                                        case e: Throwable =>
                                                            Left(e.getMessage)
                                                    }
                                                } else Right(""""OK"""")
                                            }

                                      case _ =>
                                          Left(""""Invalid contract request type"""")

                                  }
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)


                    // unknown POST path
                    case path =>
                        logger.error(s"Unknown path: $path")
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                }
            case m =>
                logger.error(s"Unsupported method: $m")
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
        }

        logger.info("==================================================")
        baseRequest.setHandled(true)
    }


    private val _globalState = new AtomicReference[GlobalState]()

    private def globalState: Option[GlobalState] = Option(_globalState.get())

    private def init(globalState: GlobalState): Unit = this._globalState.set(globalState)


}

case class GlobalState(
    networkManager: FabricNetworkManager,
    processManager: FabricProcessManager,
    network: NetworkConfig
)
