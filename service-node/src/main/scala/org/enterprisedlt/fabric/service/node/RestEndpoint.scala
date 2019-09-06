package org.enterprisedlt.fabric.service.node

import java.io.{BufferedInputStream, File, FileInputStream, FileReader}
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.model.Contract
import org.enterprisedlt.fabric.service.node.auth.FabricAuthenticator
import org.enterprisedlt.fabric.service.node.configuration.{BootstrapOptions, ServiceConfig}
import org.enterprisedlt.fabric.service.node.flow.Constant.{ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.flow.{Bootstrap, Join}
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.util.Util._
import org.enterprisedlt.fabric.service.node.util.{PrivateCollectionConfiguration, Util}
import org.hyperledger.fabric.sdk.User
import org.slf4j.LoggerFactory

import scala.util.Try

/**
  * @author Alexey Polubelov
  */
class RestEndpoint(
    bindPort: Int,
    externalAddress: Option[ExternalAddress],
    config: ServiceConfig,
    processManager: FabricProcessManager,
    hostsManager: HostsManager
) extends AbstractHandler {
    private val logger = LoggerFactory.getLogger(this.getClass)
    val cryptoPath = "/opt/profile/crypto"

    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        implicit val user: Option[User] = FabricAuthenticator.getFabricUser(request)
        request.getMethod match {
            case "GET" =>
                request.getPathInfo match {
                    case "/service/organization-msp-id" =>
                        response.getWriter.println(Util.codec.toJson(config.organization.name))
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-organizations" =>
                        logger.info(s"ListOrganizations ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-collections" =>
                        logger.info(s"Collections ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listCollections")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)


                    case "/admin/create-invite" =>
                        logger.info(s"Creating invite ${config.organization.name}...")
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        val out = response.getOutputStream
                        val address = externalAddress
                          .map(ea => s"${ea.host}:${ea.port}")
                          .getOrElse(s"service.${config.organization.name}.${config.organization.domain}:$bindPort")
                        //TODO: password should be taken from request
                        val password = "join me"
                        val key = createServiceUserKeyStore(config, s"join-${System.currentTimeMillis()}", password, cryptoPath)
                        val invite = Invite(
                            address,
                            Util.keyStoreToBase64(key, password)
                        )
                        out.println(Util.codec.toJson(invite))
                        out.flush()
                        response.setStatus(HttpServletResponse.SC_OK)


                    case "/service/list-messages" =>
                        logger.info(s"Querying messages for ${config.organization.name}...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listMessages")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-confirmations" =>
                        logger.info(s"Querying confirmations for ${config.organization.name}...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContractConfirmations")
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }
                              .merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-contracts" =>
                        logger.info(s"Querying contracts for ${config.organization.name}...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
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
                        logger.info(s"Bootstrapping organization ${config.organization.name}...")
                        val start = System.currentTimeMillis()
                        try {
                            val bootstrapOptions = Util.codec.fromJson(request.getReader, classOf[BootstrapOptions])
                            initNetworkManager(Bootstrap.bootstrapOrganization(config, bootstrapOptions, processManager, hostsManager, externalAddress, user.getOrElse(throw new Exception("Need to provide user!")))) // TODO to fix
                            val end = System.currentTimeMillis() - start
                            logger.info(s"Bootstrap done ($end ms)")
                            response.setStatus(HttpServletResponse.SC_OK)
                        } catch {
                            case ex: Exception =>
                                logger.error("Bootstrap failed:", ex)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/join-network" =>
                        networkManager
                          .toRight("Network is not initialized yet")
                          .flatMap { network =>
                              val joinRequest = Util.codec.fromJson(request.getReader, classOf[JoinRequest])
                              Join.joinOrgToNetwork(
                                  config, processManager,
                                  network, joinRequest, hostsManager
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
                        val invite = Util.codec.fromJson(request.getReader, classOf[Invite])
                        initNetworkManager(Join.join(config, processManager, invite, externalAddress, hostsManager, user.getOrElse(throw new Exception("Need to provide user!")))) // TODO to fix
                    val end = System.currentTimeMillis() - start
                        logger.info(s"Joined ($end ms)")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/admin/create-contract" =>
                        logger.info("Creating contract ...")
                        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
                        val createContractRequest = Util.codec.fromJson(request.getReader, classOf[CreateContractRequest])
                        logger.info(s"createContractRequest =  $createContractRequest")
                        networkManager
                          .toRight("Network is not initialized yet")
                          .flatMap { network =>
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
                                      network.installChainCode(ServiceChannelName, createContractRequest.name, createContractRequest.version, chainCodePkg)
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
                                      network.instantiateChainCode(
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
                                      network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "createContract", Util.codec.toJson(contract))
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
                        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
                        val joinReq = Util.codec.fromJson(request.getReader, classOf[ContractJoinRequest])
                        logger.info(s"joinReq is $joinReq")
                        networkManager
                          .toRight("Network is not initialized yet")
                          .flatMap { network =>
                              for {
                                  queryResult <- {
                                      network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getContract", joinReq.name, joinReq.founder)
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
                                      network.installChainCode(ServiceChannelName, contractDetails.chainCodeName, contractDetails.chainCodeVersion, chainCodePkg)
                                  }
                              } yield {
                                  network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "delContract", joinReq.name, joinReq.founder)
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
                        networkManager
                          .toRight("Network is not initialized yet")
                          .flatMap { network =>
                              network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "putMessage", Util.codec.toJson(message))
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
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getMessage", messageRequest.messageKey, messageRequest.sender)
                                    .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/del-message" =>
                        val delMessageRequest = Util.codec.fromJson(request.getReader, classOf[DeleteMessageRequest])
                        logger.info("Requesting for deleting message ...")
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "delMessage", delMessageRequest.messageKey, delMessageRequest.sender)
                              }.merge
                        response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                        response.getWriter.println(result)
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/call-contract" =>
                        logger.info("Processing request to contract ...")
                        val contractRequest = Util.codec.fromJson(request.getReader, classOf[CallContractRequest])
                        val result =
                            networkManager
                              .toRight("Network is not initialized yet")
                              .flatMap { network =>
                                  contractRequest.callType match {
                                      case "query" =>
                                          network
                                            .queryChainCode(
                                                ServiceChannelName,
                                                contractRequest.contractName,
                                                contractRequest.functionName,
                                                contractRequest.arguments: _*
                                            )
                                            .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))

                                      case "invoke" =>
                                          import scala.collection.JavaConverters._
                                          implicit val transient: Option[java.util.Map[String, Array[Byte]]] =
                                              Option(contractRequest.transient)
                                                .map(_.asScala.mapValues(_.getBytes(StandardCharsets.UTF_8)).asJava)

                                          network
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


    private val networkManagerLock = new ReentrantLock()
    private var networkManager_ : Option[FabricNetworkManager] = None

    private def networkManager: Option[FabricNetworkManager] = {
        networkManagerLock.lock()
        try {
            networkManager_
        } finally {
            networkManagerLock.unlock()
        }
    }

    private def initNetworkManager(value: FabricNetworkManager): Unit = {
        networkManagerLock.lock()
        try {
            networkManager_ = Option(value)
        } finally {
            networkManagerLock.unlock()
        }
    }
}
