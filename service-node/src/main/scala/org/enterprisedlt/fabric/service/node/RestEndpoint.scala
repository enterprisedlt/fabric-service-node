package org.enterprisedlt.fabric.service.node

import java.io.{BufferedInputStream, File, FileInputStream, FileReader}
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util
import java.util.concurrent.atomic.AtomicReference

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.http.entity.ContentType
import org.eclipse.jetty.http.MimeTypes
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.enterprisedlt.fabric.service.model.{Contract, UpgradeContract}
import org.enterprisedlt.fabric.service.node.auth.FabricAuthenticator
import org.enterprisedlt.fabric.service.node.configuration._
import org.enterprisedlt.fabric.service.node.flow.Constant.{DefaultConsortiumName, ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.flow.{Bootstrap, Join, RestoreState}
import org.enterprisedlt.fabric.service.node.model.{AddOrgToChannelRequest, CallContractRequest, ContractDeploymentDescriptor, ContractJoinRequest, CreateContractRequest, DeleteMessageRequest, FabricServiceState, GetMessageRequest, Invite, JoinRequest, SendMessageRequest, UpgradeContractRequest, _}
import org.enterprisedlt.fabric.service.node.proto.FabricChannel
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
    stateFilePath: String,
    profilePath: String,
    processConfig: DockerConfig,
    state: AtomicReference[FabricServiceState]
) extends AbstractHandler {

    private val logger = LoggerFactory.getLogger(this.getClass)

    def storeState(): Either[String, Unit] = {
        for {
            state <- globalState.toRight("network isn't initialized")
            s <- state.stateManager.storeState()
        } yield s
    }

    def restoreState(): Either[String, Unit] = {
        logger.debug(s"restoring state")
        RestoreState.restoreOrganizationState(
            stateFilePath,
            organizationConfig,
            cryptoManager,
            hostsManager,
            profilePath,
            processConfig,
            state) match {
            case Right(s) => init(s)
                Right(())
            case Left(e) => logger.error(s"Error during restoring state: $e")
                Left(s"Error during restoring state: $e")
        }
    }

    override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
        implicit val user: Option[User] = FabricAuthenticator.getFabricUser(request)
        request.getMethod match {
            case "GET" =>
                request.getPathInfo match {
                    case "/service/organization-msp-id" =>
                        response.getWriter.println(Util.codec.toJson(organizationConfig.name))
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/organization-full-name" =>
                        val orgFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
                        response.getWriter.println(Util.codec.toJson(orgFullName))
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/state" =>
                        response.getWriter.println(Util.codec.toJson(state.get()))
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/service/list-channels" =>
                        val result = for {
                            state <- globalState.toRight("Node is not initialized yet")
                            network = state.networkManager
                            channels = network.getChannelNames
                        } yield channels
                        result match {
                            case Right(result) =>
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.getWriter.println(Util.codec.toJson(result))
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }

                    case "/service/list-organizations" =>
                        logger.info(s"ListOrganizations ...")
                        val result = for {
                            state <- globalState.toRight("Node is not initialized yet")
                            network = state.networkManager
                            organization <- network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
                            res <- organization.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
                        } yield res
                        result match {
                            case Right(result) =>
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.getWriter.println(result)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)

                        }

                    case "/service/list-collections" =>
                        logger.info(s"ListCollections ...")
                        val result = for {
                            state <- globalState.toRight("Node is not initialized yet")
                            network = state.networkManager
                            collection <- network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listCollections")
                            res <- collection.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
                        } yield res
                        result match {
                            case Right(result) =>
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.getWriter.println(result)
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }


                    case "/admin/create-invite" =>
                        globalState
                          .toRight("Node is not initialized yet")
                          .map { state =>
                              logger.info(s"Creating invite ${organizationConfig.name}...")
                              val address = externalAddress
                                .map(ea => s"${ea.host}:${ea.port}")
                                .getOrElse(s"service.${organizationConfig.name}.${organizationConfig.domain}:$bindPort")
                              //TODO: password should be taken from request
                              val password = "join me"
                              val key = cryptoManager.createServiceUserKeyStore(s"join-${System.currentTimeMillis()}", password)
                              Invite(
                                  state.networkName,
                                  address,
                                  Util.keyStoreToBase64(key, password)
                              )
                          } match {
                            case Right(invite) =>
                                val out = response.getOutputStream
                                out.println(Util.codec.toJson(invite))
                                out.flush()
                                response.setStatus(HttpServletResponse.SC_OK)

                            case Left(error) =>
                                logger.error(error)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }


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

                    case "/admin/create-channel" =>
                        val channelName = request.getParameter("channel")
                        logger.info(s"Creating new channel $channelName ...")
                        (
                          for {
                              state <- globalState.toRight("Node is not initialized yet")
                              _ <- state.networkManager.createChannel(
                                  channelName,
                                  FabricChannel.CreateChannel(channelName,
                                      DefaultConsortiumName,
                                      organizationConfig.name
                                  ))
                              _ <- state.networkManager.addPeerToChannel(channelName, state.network.peerNodes.head.name)
                          } yield ()
                          ) match {
                            case Right(()) =>
                                response.getWriter.println(s"$channelName has been created")
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(errorMsg) =>
                                response.getWriter.println(errorMsg)
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }


                    case "/service/get-block" =>
                        val channelName = request.getParameter("channelName")
                        val blockNumber: Long = request.getParameter("blockNumber").toLong
                        logger.info(s"Getting block number $blockNumber ...")
                        globalState
                          .toRight("Node is not initialized yet")
                          .map { state =>
                              state.networkManager.fetchChannelBlockByNum(channelName, blockNumber) match {
                                  case Right(block) =>
                                      val out = response.getOutputStream
                                      block.writeTo(out)
                                      response.setStatus(HttpServletResponse.SC_OK)
                                      response.setContentType(ContentType.APPLICATION_OCTET_STREAM.getMimeType)
                                      out.flush()
                                  case Left(errorMsg) =>
                                      response.getWriter.println(errorMsg)
                                      response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                              }
                          }

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
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              state.networkManager.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
                                .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results"))
                          } match {
                            case Right(result) =>
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.getWriter.println(result)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(errorMsg) =>
                                response.getWriter.println(errorMsg)
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }


                    case "/admin/list-contract-packages" =>
                        logger.info("Listing contract packages...")
                        val chaincodePath = new File(s"/opt/profile/chain-code/").getAbsoluteFile
                        if (!chaincodePath.exists()) chaincodePath.mkdirs()
                        //
                        Try {
                            chaincodePath
                              .listFiles()
                              .filter(_.getName.endsWith(".tgz"))
                              .map(file => file.getName)
                              .map(name => name.substring(0, name.length - 4))
                        }.toEither match {
                            case Right(contracts) =>
                                logger.info(s"The list of packages is: ${contracts.mkString(" ")}")
                                response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                                response.getWriter.println(Util.codec.toJson(contracts))
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(err) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                logger.error("Got error", err)
                                response.getWriter.println(err.getMessage)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }



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
                        (for {
                            _ <- Try {
                                val bootstrapOptions = Util.codec.fromJson(request.getReader, classOf[BootstrapOptions])
                                state.set(FabricServiceState(FabricServiceState.BootstrapStarted))
                                init(Bootstrap.bootstrapOrganization(
                                    organizationConfig,
                                    bootstrapOptions,
                                    cryptoManager,
                                    hostsManager,
                                    externalAddress,
                                    profilePath,
                                    processConfig,
                                    stateFilePath,
                                    state
                                ))
                            }.toEither.left.map(_.getMessage)
                            result = {
                                val duration = System.currentTimeMillis() - start
                                s"Bootstrap done ($duration ms)"
                            }
                            _ = (logger.info(result))
                            gState <- globalState.toRight("Service node is not initialized yet")
                            _ <- gState.stateManager.storeState()
                        } yield result
                          ) match {
                            case Right(r) =>
                                response.getWriter.println(r)
                                response.setContentType(MimeTypes.Type.APPLICATION_JSON.toString)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(e) =>
                                logger.error("Bootstrap failed:", e)
                                response.getWriter.println(e)
                                response.setContentType(MimeTypes.Type.APPLICATION_JSON.toString)
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
                                  hostsManager,
                                  organizationConfig
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

                    case "/admin/add-to-channel" => {
                        for {
                            state <- globalState.toRight("Node is not initialized yet")
                            addToChannelRequest <- Try(Util.codec.fromJson(request.getReader, classOf[AddOrgToChannelRequest]))
                              .toEither.left.map(_.getMessage)
                            caCerts <- Try(addToChannelRequest.organizationCertificates.caCerts.map(Util.base64Decode).toIterable)
                              .toEither.left.map(_.getMessage)
                            tlsCACerts <- Try(addToChannelRequest.organizationCertificates.tlsCACerts.map(Util.base64Decode).toIterable)
                              .toEither.left.map(_.getMessage)
                            adminCerts <- Try(addToChannelRequest.organizationCertificates.adminCerts.map(Util.base64Decode).toIterable)
                              .toEither.left.map(_.getMessage)
                            _ = {
                                logger.info(s"Adding org to channel ${addToChannelRequest.mspId} ...")
                                state.networkManager.joinToChannel(
                                    addToChannelRequest.channelName,
                                    addToChannelRequest.mspId,
                                    caCerts,
                                    tlsCACerts,
                                    adminCerts)
                            }
                        } yield s"org ${addToChannelRequest.mspId} has been added to channel ${addToChannelRequest.channelName}"
                    } match {
                        case Right(r) =>
                            response.getWriter.println(r)
                            response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
                            response.setStatus(HttpServletResponse.SC_OK)
                        case Left(errorMsg) =>
                            response.getWriter.println(errorMsg)
                            response.setContentType(ContentType.APPLICATION_JSON.getMimeType)
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
                            processConfig,
                            stateFilePath,
                            state)
                        )
                        val end = System.currentTimeMillis() - start
                        logger.info(s"Joined ($end ms)")
                        response.setStatus(HttpServletResponse.SC_OK)

                    case "/admin/contract-upgrade" =>
                        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
                        val upgradeContractRequest = Util.codec.fromJson(request.getReader, classOf[UpgradeContractRequest])
                        logger.info(s"Upgrading contract ${upgradeContractRequest.contractType}...")
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              logger.info(s"[ $organizationFullName ] - Preparing ${upgradeContractRequest.name} chain code ...")
                              val filesBaseName = s"${upgradeContractRequest.contractType}-${upgradeContractRequest.version}"
                              val chainCodeName = s"${upgradeContractRequest.name}-${upgradeContractRequest.version}"
                              val deploymentDescriptor = Util.codec.fromJson(new FileReader(s"/opt/profile/chain-code/$filesBaseName.json"), classOf[ContractDeploymentDescriptor])
                              val path = s"/opt/profile/chain-code/$filesBaseName.tgz"
                              for {
                                  file <- Option(new File(path)).filter(_.exists()).toRight(s"File $filesBaseName.tgz doesn't exist")
                                  chainCodePkg <- Option(new BufferedInputStream(new FileInputStream(file))).toRight(s"Can't prepare cc pkg stream")
                                  _ <- {
                                      logger.info(s"[ $organizationFullName ] - Installing $chainCodeName chain code ...")
                                      state.networkManager.installChainCode(
                                          upgradeContractRequest.channelName,
                                          upgradeContractRequest.name,
                                          upgradeContractRequest.version,
                                          upgradeContractRequest.lang,
                                          chainCodePkg)
                                  }
                                  _ <- {
                                      logger.info(s"[ $organizationFullName ] - Upgrading $chainCodeName chain code up to ${upgradeContractRequest.version}...")
                                      val endorsementPolicy = Util.policyAnyOf(
                                          deploymentDescriptor.endorsement
                                            .flatMap(r => upgradeContractRequest.parties.filter(_.role == r).map(_.mspId))
                                      )
                                      val collections = deploymentDescriptor.collections.map { cd =>
                                          PrivateCollectionConfiguration(
                                              name = cd.name,
                                              memberIds = cd.members.flatMap(m =>
                                                  upgradeContractRequest.parties.filter(_.role == m).map(_.mspId)
                                              )
                                          )
                                      }
                                      state.networkManager.upgradeChainCode(
                                          upgradeContractRequest.channelName,
                                          upgradeContractRequest.name,
                                          upgradeContractRequest.version,
                                          upgradeContractRequest.lang,
                                          endorsementPolicy = Option(endorsementPolicy),
                                          collectionConfig = Option(Util.createCollectionsConfig(collections)),
                                          arguments = upgradeContractRequest.initArgs
                                      )
                                  }
                                  response <- {
                                      logger.info(s"Invoking 'createContract' method...")
                                      val contract = UpgradeContract(
                                          upgradeContractRequest.name,
                                          upgradeContractRequest.lang,
                                          upgradeContractRequest.contractType,
                                          upgradeContractRequest.version,
                                          organizationConfig.name,
                                          upgradeContractRequest.parties.map(_.mspId),
                                          Instant.now.toEpochMilli
                                      )
                                      state.networkManager.invokeChainCode(
                                          ServiceChannelName,
                                          ServiceChainCodeName,
                                          "upgradeContract",
                                          Util.codec.toJson(contract))
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

                    case "/admin/create-contract" =>
                        logger.info("Creating contract ...")
                        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
                        val contractRequest = Util.codec.fromJson(request.getReader, classOf[CreateContractRequest])
                        logger.info(s"createContractRequest =  $contractRequest")
                        globalState
                          .toRight("Node is not initialized yet")
                          .flatMap { state =>
                              logger.info(s"[ $organizationFullName ] - Preparing ${contractRequest.name} chain code ...")
                              val filesBaseName = s"${contractRequest.contractType}-${contractRequest.version}"
                              val chainCodeName = s"${contractRequest.name}-${contractRequest.version}"
                              val deploymentDescriptor = Util.codec.fromJson(new FileReader(s"/opt/profile/chain-code/$filesBaseName.json"), classOf[ContractDeploymentDescriptor])
                              val path = s"/opt/profile/chain-code/$filesBaseName.tgz"
                              for {
                                  file <- Option(new File(path)).filter(_.exists()).toRight(s"File $filesBaseName.tgz doesn't exist")
                                  chainCodePkg <- Option(new BufferedInputStream(new FileInputStream(file))).toRight(s"Can't prepare cc pkg stream")
                                  _ <- {
                                      logger.info(s"[ $organizationFullName ] - Installing $chainCodeName chain code ...")
                                      state.networkManager.installChainCode(
                                          contractRequest.channelName,
                                          contractRequest.name,
                                          contractRequest.version,
                                          contractRequest.lang,
                                          chainCodePkg)
                                  }
                                  _ <- {
                                      logger.info(s"[ $organizationFullName ] - Instantiating $chainCodeName chain code ...")
                                      val endorsementPolicy = Util.policyAnyOf(
                                          deploymentDescriptor.endorsement
                                            .flatMap(r => contractRequest.parties.filter(_.role == r).map(_.mspId))
                                      )
                                      val collections = deploymentDescriptor.collections.map { cd =>
                                          PrivateCollectionConfiguration(
                                              name = cd.name,
                                              memberIds = cd.members.flatMap(m =>
                                                  contractRequest.parties.filter(_.role == m).map(_.mspId)
                                              )
                                          )
                                      }
                                      state.networkManager.instantiateChainCode(
                                          contractRequest.channelName,
                                          contractRequest.name,
                                          contractRequest.version,
                                          contractRequest.lang,
                                          endorsementPolicy = Option(endorsementPolicy),
                                          collectionConfig = Option(Util.createCollectionsConfig(collections)),
                                          arguments = contractRequest.initArgs
                                      )
                                  }
                                  response <- {
                                      logger.info(s"Invoking 'createContract' method...")
                                      val contract = Contract(
                                          contractRequest.name,
                                          contractRequest.lang,
                                          contractRequest.contractType,
                                          contractRequest.version,
                                          organizationConfig.name,
                                          contractRequest.parties.map(_.mspId),
                                          Instant.now.toEpochMilli
                                      )
                                      state.networkManager.invokeChainCode(
                                          ServiceChannelName,
                                          ServiceChainCodeName,
                                          "createContract",
                                          Util.codec.toJson(contract)
                                      )
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
                        val result = for {
                            state <- globalState.toRight("Node is not initialized yet")
                            network = state.networkManager
                            queryResult <- {
                                logger.info(s"Querying chaincode with getContract...")
                                network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getContract", joinReq.name, joinReq.founder)
                                  .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight(s"There is an error with querying getContract method in system chain-code"))
                            }
                            contractDetails <- {
                                logger.info(s"queryResult is $queryResult")
                                Option(Util.codec.fromJson(queryResult, classOf[Contract])).filter(_ != null).toRight(s"Can't parse response from getContract")
                            }
                            file <- {
                                val path = s"/opt/profile/chain-code/${contractDetails.chainCodeName}-${contractDetails.chainCodeVersion}.tgz"
                                Option(new File(path)).filter(_.exists()).toRight(s"File  doesn't exist ")
                            }
                            _ <- {
                                val chainCodePkg = new BufferedInputStream(new FileInputStream(file))
                                logger.info(s"[ $organizationFullName ] - Installing ${contractDetails.chainCodeName}:${contractDetails.chainCodeVersion} chaincode ...")
                                network.installChainCode(
                                    ServiceChannelName,
                                    contractDetails.name,
                                    contractDetails.chainCodeVersion,
                                    "java",
                                    chainCodePkg)
                            }
                            invokeResultFuture <- network.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "delContract", joinReq.name, joinReq.founder)
                            invokeResult <- Try(invokeResultFuture.get()).toEither.left.map(_.getMessage)
                        } yield invokeResult
                        result match {
                            case Right(invokeAwait) =>
                                logger.info(s"invokeResult is $invokeAwait ...")
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(invokeAwait)
                                response.setStatus(HttpServletResponse.SC_OK)
                            case Left(err) =>
                                response.setContentType(ContentType.TEXT_PLAIN.getMimeType)
                                response.getWriter.println(err)
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                        }


                    case "/service/send-message" =>
                        val message = Util.codec.fromJson(request.getReader, classOf[SendMessageRequest])
                        logger.info(s"Sending message to ${message.to} ...")
                        val result = for {
                            state <- globalState.toRight("Node is not initialized yet")
                            res <- state.networkManager.invokeChainCode(ServiceChannelName, ServiceChainCodeName, "putMessage", Util.codec.toJson(message))
                        } yield res
                        result match {
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


