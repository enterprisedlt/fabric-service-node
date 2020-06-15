package org.enterprisedlt.fabric.service.node

import java.io._
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

import com.google.gson.GsonBuilder
import javax.servlet.http.Part
import org.enterprisedlt.fabric.service.model._
import org.enterprisedlt.fabric.service.node.configuration._
import org.enterprisedlt.fabric.service.node.flow.Constant.{DefaultConsortiumName, ServiceChainCodeName, ServiceChannelName}
import org.enterprisedlt.fabric.service.node.flow.{Bootstrap, Join}
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.process.{ComponentsDistributor, CustomComponentRequest, ProcessManager, StartCustomComponentRequest}
import org.enterprisedlt.fabric.service.node.proto.FabricChannel
import org.enterprisedlt.fabric.service.node.rest.{Get, JsonRestClient, Post, PostMultipart}
import org.enterprisedlt.fabric.service.node.shared._
import org.hyperledger.fabric.sdk.Peer
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class RestEndpoint(
                      bindPort: Int,
                      componentsDistributorBindPort: Int,
                      externalAddress: Option[ExternalAddress],
                      organizationConfig: OrganizationConfig,
                      cryptoManager: CryptoManager,
                      hostsManager: HostsManager,
                      profilePath: String,
                      processManager: ProcessManager,
                  ) {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val serviceNodeName = s"service.${organizationConfig.name}.${organizationConfig.domain}"

    @PostMultipart("/admin/upload-application")
    def uploadApplication(multipart: Iterable[Part]): Either[String, Unit] = {
        val fileDir = "/opt/profile/application-distributives"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- Util.saveMultipart(multipart, fileDir)
        } yield globalState.eventsMonitor.updateApplications()
    }

    @PostMultipart("/admin/upload-chaincode")
    def uploadChaincode(multipart: Iterable[Part]): Either[String, Unit] = {
        val fileDir = "/opt/profile/chain-code"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- Util.saveMultipart(multipart, fileDir)
        } yield globalState.eventsMonitor.updateCustomComponentDescriptors()

    }


    @PostMultipart("/admin/upload-custom-component")
    def uploadCustomComponent(multipart: Iterable[Part]): Either[String, Unit] = {
        val fileDir = "/opt/profile/components"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- Util.saveMultipart(multipart, fileDir)
        } yield globalState.eventsMonitor.updateCustomComponentDescriptors()
    }

    @Post("/admin/create-application")
    def createApplication(applicationRequest: CreateApplicationRequest): Either[String, String] = {
        logger.info("Creating application ...")
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"applicationRequest =  ${applicationRequest.toString}")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            descriptor <- FabricServiceStateHolder.fullState.applications.find(_.filename == applicationRequest.applicationType).toRight("No such ...")
            responseContract <- descriptor.contracts.foldRight[Either[String, String]](Right("")) { case (contract, current) =>
                current.flatMap { _ =>
                    val contractCreateRequest = CreateContractRequest(
                        name = applicationRequest.name,
                        version = applicationRequest.version,
                        contractType = contract.name,
                        channelName = applicationRequest.channelName,
                        parties = applicationRequest.parties,
                        initArgs = applicationRequest.initArgs
                    )
                    createContract(contractCreateRequest)
                }
            }
            response <- {
                logger.info(s"Invoking 'createApplication' method...")
                val application = Application(
                    founder = organizationConfig.name,
                    name = applicationRequest.name,
                    channel = applicationRequest.channelName,
                    applicationType = applicationRequest.applicationType,
                    version = applicationRequest.version,
                    participants = applicationRequest.parties.map(_.mspId),
                    timestamp = Instant.now.toEpochMilli
                )
                state.networkManager.invokeChainCode(
                    ServiceChannelName,
                    ServiceChainCodeName,
                    "createApplication",
                    Util.codec.toJson(application)
                )
            }
            r <- Try(response.get()).toEither.left.map(_.getMessage)
        } yield {
            FabricServiceStateHolder.updateStateFull(state =>
                state.copy(
                    deployedApplications = state.deployedApplications :+ ApplicationInfo(
                        name = applicationRequest.name,
                        version = applicationRequest.version,
                        channelName = applicationRequest.channelName
                    )
                )
            )
            s"Creating application ${applicationRequest.applicationType} has been completed successfully $r"
        }
    }

    @Post("/admin/start-custom-node")
    def startCustomNode(request: CustomComponentRequest): Either[String, String] = {
        val crypto = cryptoManager.generateCustomComponentCrypto(request.box)
        val startCustomComponentRequest = StartCustomComponentRequest(
            serviceNodeName,
            request,
            crypto
        )
        processManager.startCustomNode(request.box, startCustomComponentRequest)
    }

    @Get("/service/list-boxes")
    def listBoxes: Either[String, Array[Box]] = processManager.listBoxes

    @Get("/service/state")
    def getState: Either[String, FabricServiceState] = Right(FabricServiceStateHolder.get)

    @Get("/admin/network-config")
    def getNetworkConfig: Either[String, NetworkConfig] =
        globalState
          .toRight("Node is not initialized yet")
          .map(_.network)


    @Get("/admin/download-application")
    def downloadApplication(componentsDistributorUrl: String, applicationFileName: String): Either[String, Unit] = {
        val distributorClient = JsonRestClient.create[ComponentsDistributor](componentsDistributorUrl)
        val destinationDir = s"/opt/profile/application-distributives"
        for {
            distributiveBase64 <- distributorClient.getApplicationDistributive(applicationFileName)
            applicationDistributive <- Try(Base64.getDecoder.decode(distributiveBase64)).toEither.left.map(_.getMessage)
        } yield {
            Util.storeToFile(s"$destinationDir/$applicationFileName.tgz", applicationDistributive)
            logger.info(s"Application $applicationFileName has been successfully downloaded")
        }
    }

    @Get("/admin/publish-application")
    def publishApplication(name: String, filename: String): Either[String, String] = {
        val componentsDistributorAddress = externalAddress
          .map(ea => s"http://${ea.host}:$componentsDistributorBindPort")
          .getOrElse(s"http://service.${organizationConfig.name}.${organizationConfig.domain}:$componentsDistributorBindPort")
        val application = ApplicationDistributive(
            name = name,
            filename = filename,
            founder = serviceNodeName,
            componentsDistributorAddress = componentsDistributorAddress
        )
        for {
            state <- globalState.toRight("Node is not initialized yet")
            _ <- state.networkManager.invokeChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "publishApplicationDistributive",
                Util.codec.toJson(application))
        } yield {
            FabricServiceStateHolder.incrementVersion()
            s"Application $name has been successfully published"
        }
    }

    @Get("/service/list-channels")
    def listChannels: Either[String, Array[String]] = {
        globalState
          .toRight("Node is not initialized yet")
          .map(_.networkManager.getChannelNames)
    }

    @Get("/service/list-organizations")
    def listOrganizations: Either[String, Array[Organization]] = {
        logger.info(s"ListOrganizations ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            network = state.networkManager
            organization <- network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
            res <- organization.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            r <- Try((new GsonBuilder).create().fromJson(res, classOf[Array[Organization]])).toEither.left.map(_.getMessage)
        } yield r
    }

    @Get("/service/list-collections")
    def listCollections: Either[String, Array[String]] = {
        logger.info(s"ListCollections ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            network = state.networkManager
            queryResult <- network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "listCollections")
            collections <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            r <- Try((new GsonBuilder).create().fromJson(collections, classOf[Array[String]])).toEither.left.map(_.getMessage)
        } yield r
    }

    @Get("/admin/create-invite")
    def createInvite: Either[String, Invite] = {
        for {
            state <- globalState.toRight("Node is not initialized yet")
            address = {
                logger.info(s"Creating invite ${organizationConfig.name}...")
                externalAddress
                  .map(ea => s"${ea.host}:${ea.port}")
                  .getOrElse(s"service.${organizationConfig.name}.${organizationConfig.domain}:$bindPort")
            }
            //TODO: password should be taken from request
            password = "join me"
            key <- Try(cryptoManager.createServiceUserKeyStore(s"join-${System.currentTimeMillis()}", password))
              .toEither.left.map(_.getMessage)
            invite = Invite(
                state.networkName,
                address,
                Util.keyStoreToBase64(key, password)
            )
        } yield invite
    }

    @Get("/admin/create-user")
    def createUser(name: String): Either[String, Unit] = {
        logger.info(s"Creating new user $name ...")
        Try(cryptoManager.createFabricUser(name))
          .toEither
          .map(_ => ())
          .left.map(_.getMessage)
    }


    @Get("/admin/get-user-key")
    def getUserKey(name: String, password: String): Either[String, Array[Byte]] = {
        logger.info(s"Obtaining user key for $name ...")
        for {
            keyStore <- Try(cryptoManager.getFabricUserKeyStore(name, password))
              .toEither.left.map(_.getMessage)
            buffer = new ByteArrayOutputStream(1024)
            _ <- Try(keyStore.store(buffer, password.toCharArray))
              .toEither.left.map(_.getMessage)
            result <- Try(buffer.toByteArray)
              .toEither.left.map(_.getMessage)
        } yield result
    }

    @Post("/admin/create-channel")
    def createChannel(channel: String): Either[String, String] = {
        logger.info(s"Creating new channel $channel ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            _ <- state.networkManager.createChannel(
                channel,
                FabricChannel.CreateChannel(
                    channel,
                    DefaultConsortiumName,
                    organizationConfig.name
                ))
            _ <- state.networkManager.addPeerToChannel(channel, state.network.peerNodes.head.name)
        } yield {
            FabricServiceStateHolder.incrementVersion()
            s"$channel has been created"
        }
    }

    @Get("/service/get-block")
    def getBlockByNumber(channelName: String, blockNumber: String): Either[String, Array[Byte]] = {
        logger.info(s"Getting block number $blockNumber ...")
        for {
            blockNumberLong <- Try(blockNumber.toLong)
              .toEither.left.map(_.getMessage)
            state <- globalState.toRight("Node is not initialized yet")
            block <- state.networkManager.fetchChannelBlockByNum(channelName, blockNumberLong)
            buffer = new ByteArrayOutputStream(1024)
            _ <- Try(block.writeTo(buffer)).toEither.left.map(_.getMessage)
            result <- Try(buffer.toByteArray).toEither.left.map(_.getMessage)
        } yield result
    }

    @Get("/admin/join-to-channel")
    def joinToChannel(channelName: String): Either[String, String] = {
        logger.info(s"Joining to channel $channelName ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            _ = state.networkManager.defineChannel(channelName)
            _ <- state.network.peerNodes.map(
                peer => state.networkManager.addPeerToChannel(channelName, peer.name)
            ).foldRight(Right(Nil): Either[String, List[Peer]]) {
                (e, p) => for (xs <- p.right; x <- e.right) yield x :: xs
            }
        } yield s"Successfully has been joined to channel $channelName"
    }

    @Get("/service/list-messages")
    def getListMessages: Either[String, String] = {
        logger.info(s"Querying messages for ${organizationConfig.name}...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            queryResult <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "listMessages")
            messages <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
        } yield messages
    }

    @Get("/service/list-confirmations")
    def getListConfirmations: Either[String, String] = {
        logger.info(s"Querying confirmations for ${organizationConfig.name}...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            queryResult <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContractConfirmations")
            confirmations <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
        } yield confirmations
    }

    @Get("/service/list-contracts")
    def getListContracts: Either[String, Array[Contract]] = {
        logger.info(s"Querying contracts for ${organizationConfig.name}...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            queryResult <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "listContracts")
            contracts <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
            result <- Try((new GsonBuilder).create().fromJson(contracts, classOf[Array[Contract]])).toEither.left.map(_.getMessage)
        } yield result
    }


        @Get("/service/list-applications")
        def listApplications: Either[String, Array[ApplicationInfo]] = {
            Try(FabricServiceStateHolder.fabricServiceStateFull.deployedApplications)
              .toEither.left.map(_.getMessage)
        }


    @Get("/service/list-chain-codes")
    def listChainCodes: Either[String, Array[ChainCodeInfo]] =
        globalState
          .toRight("Node is not initialized yet")
          .map(_.networkManager)
          .map(_.listChainCodes)


    @Get("/admin/list-contract-packages")
    def getListContractPackages: Either[String, Array[ContractDescriptor]] = {
        logger.info("Listing contract packages...")
        val chaincodePath = new File(s"/opt/profile/chain-code/").getAbsoluteFile
        if (!chaincodePath.exists()) chaincodePath.mkdirs()
        Try(
            chaincodePath
              .listFiles()
              .filter(_.getName.endsWith(".json"))
              .map(_.getName)
              .flatMap { packageName =>
                  Try {
                      val descriptor = Util.codec.fromJson(
                          new FileReader(s"/opt/profile/chain-code/$packageName"),
                          classOf[ContractDeploymentDescriptor]
                      )
                      val name = packageName.substring(0, packageName.length - 5)
                      ContractDescriptor(
                          name = name,
                          roles = descriptor.roles,
                          initArgsNames = descriptor.initArgsNames
                      )
                  }.toOption
              }
        ).toEither.left.map(_.getMessage)
    }


    @Post("/admin/register-box-manager")
    def registerBox(request: RegisterBoxManager): Either[String, Box] = {
        val componentsDistributorAddress = externalAddress
          .map(ea => s"http://${ea.host}:$componentsDistributorBindPort")
          .getOrElse(s"http://service.${organizationConfig.name}.${organizationConfig.domain}:$componentsDistributorBindPort")
        for {
            box <- processManager.registerBox(serviceNodeName, componentsDistributorAddress, request.name, request.url)
        } yield {
            FabricServiceStateHolder.incrementVersion()
            box
        }
    }


    @Post("/admin/bootstrap")
    def bootstrap(bootstrapOptions: BootstrapOptions): Either[String, String] = {
        logger.info(s"Bootstrapping organization ${organizationConfig.name}...")
        val start = System.currentTimeMillis()
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.BootstrapStarted))
        for {
            gState <- Try(
                Bootstrap.bootstrapOrganization(
                    organizationConfig,
                    bootstrapOptions,
                    cryptoManager,
                    hostsManager,
                    externalAddress,
                    profilePath,
                    processManager,
                )
            ).toEither.left.map(e => s"Bootstrap failed: ${e.getMessage}")
            _ = init(gState)
            end = System.currentTimeMillis() - start
            _ = logger.info(s"Bootstrap done ($end ms)")
        } yield s"Bootstrap done ($end ms)"
    }

    @Post("/join-network")
    def joinNetwork(joinRequest: JoinRequest): Either[String, JoinResponse] = {
        for {
            state <- globalState.toRight("Node is not initialized yet")
            join <- Join.joinOrgToNetwork(
                state,
                cryptoManager,
                processManager,
                joinRequest,
                hostsManager,
                organizationConfig
            )
        } yield {
            FabricServiceStateHolder.incrementVersion()
            join
        }
    }


    @Post("/admin/add-to-channel")
    def addToChannel(addToChannelRequest: AddOrgToChannelRequest): Either[String, String] = {
        for {
            state <- globalState.toRight("Node is not initialized yet")
            caCerts <- Try(addToChannelRequest.organizationCertificates.caCerts.map(Util.base64Decode).toIterable)
              .toEither.left.map(_.getMessage)
            tlsCACerts <- Try(addToChannelRequest.organizationCertificates.tlsCACerts.map(Util.base64Decode).toIterable)
              .toEither.left.map(_.getMessage)
            adminCerts <- Try(addToChannelRequest.organizationCertificates.adminCerts.map(Util.base64Decode).toIterable)
              .toEither.left.map(_.getMessage)
            _ <- {
                logger.info(s"Adding org to channel ${addToChannelRequest.mspId} ...")
                state.networkManager.joinToChannel(
                    addToChannelRequest.channelName,
                    addToChannelRequest.mspId,
                    caCerts,
                    tlsCACerts,
                    adminCerts)
            }
        } yield s"org ${addToChannelRequest.mspId} has been added to channel ${addToChannelRequest.channelName}"
    }

    @Post("/admin/request-join")
    def requestJoin(joinOptions: JoinOptions): Either[String, String] = {
        logger.info("Requesting to joining network ...")
        val start = System.currentTimeMillis()
        FabricServiceStateHolder.update(_.copy(stateCode = FabricServiceState.JoinStarted))
        for {
            gState <- Try(
                Join.join(
                    organizationConfig,
                    cryptoManager,
                    joinOptions,
                    externalAddress,
                    hostsManager,
                    profilePath,
                    processManager,
                )
            ).toEither.left.map(_.getMessage)
            _ = init(gState)
            end = System.currentTimeMillis() - start
            _ = logger.info(s"Joined ($end ms)")
        } yield s"Joined ($end ms)"
    }

    @Post("/admin/contract-upgrade")
    def contractUpgrade(upgradeContractRequest: UpgradeContractRequest): Either[String, String] = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"Upgrading contract ${upgradeContractRequest.contractType}...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            _ = logger.info(s"[ $organizationFullName ] - Preparing ${upgradeContractRequest.name} chain code ...")
            filesBaseName = s"${upgradeContractRequest.contractType}-${upgradeContractRequest.version}"
            chainCodeName = s"${upgradeContractRequest.name}-${upgradeContractRequest.version}"
            deploymentDescriptor <- Try(Util.codec.fromJson(
                new FileReader(s"/opt/profile/chain-code/$filesBaseName.json"),
                classOf[ContractDeploymentDescriptor]
            )).toEither.left.map(_.getMessage)
            path = s"/opt/profile/chain-code/$filesBaseName.tgz"
            file <- Option(new File(path)).filter(_.exists()).toRight(s"File $filesBaseName.tgz doesn't exist")
            chainCodePkg <- Option(new BufferedInputStream(new FileInputStream(file))).toRight(s"Can't prepare cc pkg stream")
            _ <- {
                logger.info(s"[ $organizationFullName ] - Installing $chainCodeName chain code ...")
                state.networkManager.installChainCode(
                    upgradeContractRequest.channelName,
                    upgradeContractRequest.name,
                    upgradeContractRequest.version,
                    deploymentDescriptor.language,
                    chainCodePkg)
            }
            endorsementPolicy <- Util.makeEndorsementPolicy(
                deploymentDescriptor.endorsement,
                upgradeContractRequest.parties
            )
            collections = deploymentDescriptor.collections.map { cd =>
                PrivateCollectionConfiguration(
                    name = cd.name,
                    memberIds = cd.members.flatMap(m =>
                        upgradeContractRequest.parties.filter(_.role == m).map(_.mspId)
                    )
                )
            }
            _ <- state.networkManager.upgradeChainCode(
                upgradeContractRequest.channelName,
                upgradeContractRequest.name,
                upgradeContractRequest.version,
                deploymentDescriptor.language,
                endorsementPolicy = Option(endorsementPolicy),
                collectionConfig = Option(Util.createCollectionsConfig(collections)),
                arguments = upgradeContractRequest.initArgs
            )
            response <- {
                logger.info(s"Invoking 'createContract' method...")
                val contract = UpgradeContract(
                    upgradeContractRequest.name,
                    deploymentDescriptor.language,
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
            r <- Try(response.get()).toEither.left.map(_.getMessage)
        } yield {
            FabricServiceStateHolder.incrementVersion()
            s"Upgrading contract ${upgradeContractRequest.contractType} has been completed successfully $r"
        }
    }


    @Post("/admin/create-contract")
    def createContract(contractRequest: CreateContractRequest): Either[String, String] = {
        logger.info("Creating contract ...")
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"createContractRequest =  $contractRequest")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            _ = logger.info(s"[ $organizationFullName ] - Preparing ${contractRequest.name} chain code ...")
            filesBaseName = s"${contractRequest.contractType}" // -${contractRequest.version}
            chainCodeName = s"${contractRequest.name}-${contractRequest.version}" //
            deploymentDescriptor <- Try(Util.codec.fromJson(
                new FileReader(s"/opt/profile/chain-code/$filesBaseName.json"),
                classOf[ContractDeploymentDescriptor]
            )).toEither.left.map(_.getMessage)
            path = s"/opt/profile/chain-code/$filesBaseName.tgz"
            file <- Option(new File(path)).filter(_.exists()).toRight(s"File $filesBaseName.tgz doesn't exist")
            chainCodePkg <- Option(new BufferedInputStream(new FileInputStream(file))).toRight(s"Can't prepare cc pkg stream")
            _ <- {
                logger.info(s"[ $organizationFullName ] - Installing $chainCodeName chain code ...")
                state.networkManager.installChainCode(
                    contractRequest.channelName,
                    contractRequest.name,
                    contractRequest.version,
                    deploymentDescriptor.language,
                    chainCodePkg
                )
            }
            endorsementPolicy <- Util.makeEndorsementPolicy(
                deploymentDescriptor.endorsement,
                contractRequest.parties
            )
            collections = deploymentDescriptor.collections.map { cd =>
                PrivateCollectionConfiguration(
                    name = cd.name,
                    memberIds = cd.members.flatMap(m =>
                        contractRequest.parties.filter(_.role == m).map(_.mspId)
                    )
                )
            }
            _ = logger.info(s"[ $organizationFullName ] - Instantiating $chainCodeName chain code ...")
            _ <- state.networkManager.instantiateChainCode(
                contractRequest.channelName,
                contractRequest.name,
                contractRequest.version,
                deploymentDescriptor.language,
                endorsementPolicy = Option(endorsementPolicy),
                collectionConfig = Option(Util.createCollectionsConfig(collections)),
                arguments = contractRequest.initArgs
            )
            response <- {
                logger.info(s"Invoking 'createContract' method...")
                val contract = Contract(
                    founder = organizationConfig.name,
                    name = contractRequest.name,
                    channel = contractRequest.channelName,
                    lang = deploymentDescriptor.language,
                    contractType = contractRequest.contractType,
                    version = contractRequest.version,
                    participants = contractRequest.parties.map(_.mspId),
                    timestamp = Instant.now.toEpochMilli
                )
                state.networkManager.invokeChainCode(
                    ServiceChannelName,
                    ServiceChainCodeName,
                    "createContract",
                    Util.codec.toJson(contract)
                )
            }
            r <- Try(response.get()).toEither.left.map(_.getMessage)
        } yield {
            FabricServiceStateHolder.incrementVersion()
            s"Creating contract ${contractRequest.contractType} has been completed successfully $r"
        }
    }


    @Post("/admin/contract-join")
    def contractJoin(joinReq: ContractJoinRequest): Either[String, String] = {
        logger.info("Joining deployed contract ...")
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        for {
            state <- globalState.toRight("Node is not initialized yet")
            network = state.networkManager
            queryResult <- {
                logger.info(s"Querying chaincode with getContract...")
                network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getContract", joinReq.name, joinReq.founder)
                  .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight(s"There is an error with querying getContract method in system chain-code"))
            }
            contractDetails <- {
                logger.info(s"queryResult is $queryResult")
                Try(Util.codec.fromJson(queryResult, classOf[Contract]))
                  .toEither.left.map(_.getMessage)
            }
            file <- {
                val path = s"/opt/profile/chain-code/${contractDetails.contractType}.tgz"
                Option(new File(path)).filter(_.exists()).toRight(s"File $path does not exist ")
            }
            _ <- {
                val chainCodePkg = new BufferedInputStream(new FileInputStream(file))
                logger.info(s"[ $organizationFullName ] - Installing ${contractDetails.contractType}:${contractDetails.version} chaincode ...")
                network.installChainCode(
                    contractDetails.channel,
                    contractDetails.name,
                    contractDetails.version,
                    contractDetails.lang,
                    chainCodePkg)
            }
            invokeResultFuture <- network.invokeChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "delContract",
                joinReq.name,
                joinReq.founder
            )
            invokeAwait <- Try(invokeResultFuture.get()).toEither.left.map(_.getMessage)
        } yield s"invokeResult is $invokeAwait"
    }

    @Post("/service/send-message")
    def sendMessage(message: SendMessageRequest): Either[String, String] = {
        logger.info(s"Sending message to ${message.to} ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            res <- state.networkManager.invokeChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "putMessage",
                Util.codec.toJson(message))
        } yield s"message send $res"
    }

    @Post("/service/get-message")
    def getMessage(messageRequest: GetMessageRequest): Either[String, String] = {
        logger.info("Obtaining message ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            queryResult <- state.networkManager.queryChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "getMessage",
                messageRequest.messageKey,
                messageRequest.sender
            )
            messages <- queryResult.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("No results")
        } yield messages
    }

    @Post("/service/del-message")
    def delMessage(delMessageRequest: DeleteMessageRequest): Either[String, String] = {
        logger.info("Requesting for deleting message ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            invokeResultFuture <- state.networkManager.invokeChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "delMessage",
                delMessageRequest.messageKey,
                delMessageRequest.sender
            )
            invokeAwait <- Try(invokeResultFuture.get()).toEither.left.map(_.getMessage)
        } yield s"Message deleted $invokeAwait"
    }

    @Post("/service/call-contract")
    def callContract(contractRequest: CallContractRequest): Either[String, String] = {
        logger.info("Processing request to contract ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            result <- {
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
            }
        } yield result
    }

    @Get("/service/get-events")
    def getEvents: Either[String, Events] = {
        logger.info(s"Querying events for ${organizationConfig.name}...")
        globalState
          .toRight("Node is not initialized yet")
          .map(_.eventsMonitor.getEvents)
    }

    // ================================================================================================================

    private val _globalState = new AtomicReference[GlobalState]()

    private def globalState: Option[GlobalState] = Option(_globalState.get())

    private def init(globalState: GlobalState): Unit = this._globalState.set(globalState)


    def cleanup(): Unit = {
        globalState.foreach { state =>
            state.eventsMonitor.shutdown()
        }
    }
}

case class GlobalState(
                          networkManager: FabricNetworkManager,
                          network: NetworkConfig,
                          networkName: String,
                          eventsMonitor: EventsMonitor
                      )
