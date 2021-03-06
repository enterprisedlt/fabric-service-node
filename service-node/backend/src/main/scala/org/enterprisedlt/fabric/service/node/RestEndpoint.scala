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
import scala.language.postfixOps

/**
 * @author Alexey Polubelov
 * @author Maxim Fedin
 */
class RestEndpoint(
    bindPort: Int,
    componentsDistributorBindPort: Int,
    externalAddress: Option[ExternalAddress],
    organizationConfig: OrganizationConfig,
    cryptoManager: CryptoManager,
    hostsManager: HostsManager,
    profilePath: String,
    processManager: ProcessManager
) {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val serviceNodeName = s"service.${organizationConfig.name}.${organizationConfig.domain}"

    @PostMultipart("/admin/upload-application")
    def uploadApplication(multipart: Iterable[Part]): Either[String, Unit] = {
        val fileDir = "/opt/profile/application-distributives"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- Util.saveParts(multipart, fileDir)
        } yield FabricServiceStateHolder.updateStateFullOption(
            FabricServiceStateHolder.compose(
                globalState.eventsMonitor.updateApplications(),
                globalState.eventsMonitor.updateContractDescriptors()
            )
        )
    }

    @PostMultipart("/admin/upload-chaincode")
    def uploadChaincode(multipart: Iterable[Part]): Either[String, Unit] = {
        val fileDir = "/opt/profile/chain-code"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- Util.saveParts(multipart, fileDir)
        } yield FabricServiceStateHolder.updateStateFullOption(globalState.eventsMonitor.updateCustomComponentDescriptors())

    }


    @PostMultipart("/admin/upload-custom-component")
    def uploadCustomComponent(multipart: Iterable[Part]): Either[String, Unit] = {
        val fileDir = "/opt/profile/components"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- Util.saveParts(multipart, fileDir)
        } yield FabricServiceStateHolder.updateStateFullOption(globalState.eventsMonitor.updateCustomComponentDescriptors())
    }

    @Post("/admin/create-application")
    def createApplication(applicationRequest: CreateApplicationRequest): Either[String, String] = {
        logger.info("Creating application ...")
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        logger.info(s"applicationRequest =  ${applicationRequest.toString}")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            applicationDescriptor <- FabricServiceStateHolder.fullState.applications.find(_.applicationType == applicationRequest.applicationType)
              .toRight(s"No such application type ${applicationRequest.applicationType} in application registry")
            _ <- applicationDescriptor.contracts.foldRight[Either[String, String]](Right("")) { case (contract, current) =>
                current.flatMap { _ =>
                    val contractCreateRequest = CreateContractRequest(
                        name = contract.name,
                        version = applicationRequest.version,
                        contractType = contract.contractType,
                        channelName = applicationRequest.channelName,
                        parties = applicationRequest.parties,
                        initArgs = contract.initArgsNames
                    )
                    for {deploymentDescriptor <- getDeploymentDescriptor(contractCreateRequest.contractType)
                         _ <- createContractBase(contractCreateRequest, deploymentDescriptor)
                         } yield s"Successfully created contract ${contract.name}"
                }
            }
            mergedApplicationProperties = applicationDescriptor.properties.map { property =>
                applicationRequest.properties.find(_.key == property.key)
                  .getOrElse(property)
            }
            _ <- applicationDescriptor.components.foldRight[Either[String, String]](Right("")) { case (component, current) =>
                current.flatMap { _ =>
                    val enrichedProperties = Util.fillPropertiesPlaceholders(
                        component.environmentVariables,
                        mergedApplicationProperties,
                    )
                    val request = CustomComponentRequest(
                        box = applicationRequest.box,
                        name = s"${applicationRequest.name}.${component.componentType}.$organizationFullName",
                        componentType = component.componentType,
                        properties = enrichedProperties
                    )
                    startCustomNode(request)
                }
            }
            _ <- {
                logger.info(s"Invoking 'createApplication' method...")
                val application = ApplicationInvite(
                    founder = organizationFullName,
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
                    "createApplicationInvite",
                    Util.codec.toJson(application)
                )
            }
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
            s"Creating application ${applicationRequest.applicationType} has been completed successfully"
        }
    }

    @Post("/admin/application-join")
    def applicationJoin(joinReq: JoinApplicationRequest): Either[String, String] = {
        logger.info("Joining deployed application ...")
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        for {
            state <- globalState.toRight("Node is not initialized yet")
            network = state.networkManager
            queryResult <- {
                logger.info(s"Querying application with getApplication...")
                network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getApplicationInvite", joinReq.name, joinReq.founder)
                  .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight(s"There is an error with querying getApplicationInvite method in system chain-code"))
            }
            applicationDetails <- Util.try2EitherWithLogging(Util.codec.fromJson(queryResult, classOf[ApplicationInvite]))
            applicationDescriptorPath = s"/opt/profile/applications/${applicationDetails.applicationType}.json"
            _ <- ensureApplicationDownloaded(applicationDetails.applicationType, applicationDescriptorPath)
            applicationDescriptor <- Util.try2EitherWithLogging(
                Util.codec.fromJson(new FileReader(applicationDescriptorPath), classOf[ApplicationDescriptor])
            )
            _ <- applicationDescriptor.contracts.foldRight[Either[String, String]](Right("")) { case (contract, current) =>
                logger.info(s"Contract is $contract")
                current.flatMap { _ =>
                    val filePath = s"/opt/profile/chain-code/${contract.contractType}.tgz"
                    for {
                        chaincodeFile <- Option(new File(filePath)).filter(_.exists()).toRight(s"File $filePath does not exist ")
                        path = chaincodeFile.toPath
                        chaincodeDescriptor <- Util.readFromTarAs[ContractDeploymentDescriptor](chaincodeFile.toPath,
                            s"${contract.contractType}.json").toRight(s"Descriptor hasn't been found in $path")
                        chainCodePkg = new BufferedInputStream(new FileInputStream(chaincodeFile))
                        _ = logger.info(s"[ $organizationFullName ] - Installing ${contract.name}:${applicationDetails.version} chaincode ...")
                        _ <- network.installChainCode(
                            applicationDetails.channel,
                            contract.name,
                            applicationDetails.version,
                            chaincodeDescriptor.language,
                            chainCodePkg
                        )
                        invokeResultFuture <- network.invokeChainCode(
                            ServiceChannelName,
                            ServiceChainCodeName,
                            "delContract",
                            joinReq.name,
                            joinReq.founder
                        )
                        invokeAwait <- Util.try2EitherWithLogging(invokeResultFuture.get())
                    } yield s"invokeResult is $invokeAwait"
                }
            }
            mergedProperties = applicationDescriptor.properties.map { property =>
                joinReq.properties.find(_.key == property.key)
                  .getOrElse(property)
            }
            _ <- applicationDescriptor.components.foldRight[Either[String, String]](Right("")) { case (component, current) =>
                current.flatMap { _ =>
                    val enrichedProperties = Util.fillPropertiesPlaceholders(
                        component.environmentVariables,
                        mergedProperties,
                    )
                    val request = CustomComponentRequest(
                        box = joinReq.box,
                        name = s"${joinReq.name}.${component.componentType}.$organizationFullName",
                        componentType = component.componentType,
                        properties = enrichedProperties
                    )
                    startCustomNode(request)
                }
            }
            invokeResultFuture <- network.invokeChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "delApplicationInvite",
                joinReq.name,
                joinReq.founder
            )
            invokeAwait <- Util.try2EitherWithLogging(invokeResultFuture.get())
        } yield {
            FabricServiceStateHolder.updateStateFullOption(
                FabricServiceStateHolder.compose(
                    state =>
                        Some(
                            state.copy(
                                deployedApplications = state.deployedApplications :+ ApplicationInfo(
                                    name = applicationDetails.name,
                                    version = applicationDetails.version,
                                    channelName = applicationDetails.channel
                                )
                            )
                        ),
                    state.eventsMonitor.updateApplications(),
                    state.eventsMonitor.updateContractDescriptors()
                )
            )
            s"Joining to application ${joinReq.name} has been completed successfully $invokeAwait"
        }
    }

    @Post("/admin/start-custom-node")
    def startCustomNode(request: CustomComponentRequest): Either[String, String] = {
        //
        val crypto = cryptoManager.generateCustomComponentCrypto(request.box)
        val startCustomComponentRequest = StartCustomComponentRequest(
            serviceNodeName,
            request.copy(properties = addDefaultProperties(request.properties)),
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


    @Post("/admin/download-application")
    def downloadApplication(request: DownloadApplicationRequest): Either[String, Unit] = {
        val distributorClient = JsonRestClient.create[ComponentsDistributor](request.componentsDistributorUrl)
        val destinationDir = s"/opt/profile/application-distributives"
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            distributiveBase64 <- distributorClient.getApplicationDistributive(request.applicationFileName)
            applicationDistributive <- Util.try2EitherWithLogging(Base64.getDecoder.decode(distributiveBase64))
        } yield {
            Util.storeToFile(s"$destinationDir/${request.applicationFileName}.tgz", applicationDistributive)
            logger.info(s"Application ${request.applicationFileName} has been successfully downloaded")
            FabricServiceStateHolder.updateStateFullOption(globalState.eventsMonitor.updateApplications())
        }
    }

    @Post("/admin/publish-application")
    def publishApplication(request: PublishApplicationRequest): Either[String, String] = {
        val componentsDistributorAddress = externalAddress
          .map(ea => s"http://${ea.host}:$componentsDistributorBindPort")
          .getOrElse(s"http://service.${organizationConfig.name}.${organizationConfig.domain}:$componentsDistributorBindPort")
        val application = ApplicationDistributive(
            applicationName = request.applicationName,
            applicationType = request.applicationType,
            founder = serviceNodeName,
            componentsDistributorAddress = componentsDistributorAddress
        )
        for {
            globalState <- globalState.toRight("Node is not initialized yet")
            _ <- globalState.networkManager.invokeChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                "publishApplicationDistributive",
                Util.codec.toJson(application))
        } yield {
            FabricServiceStateHolder.updateStateFullOption(globalState.eventsMonitor.updateApplications())
            s"Application ${request.applicationName} has been successfully published"
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
            r <- Util.try2EitherWithLogging((new GsonBuilder).create().fromJson(res, classOf[Array[Organization]]))
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
            r <- Util.try2EitherWithLogging((new GsonBuilder).create().fromJson(collections, classOf[Array[String]]))
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
            key <- Util.try2EitherWithLogging(cryptoManager.createServiceUserKeyStore(s"join-${System.currentTimeMillis()}", password))
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
        Util.try2EitherWithLogging(cryptoManager.createFabricUser(name))
          .map(_ => ())
    }


    @Get("/admin/get-user-key")
    def getUserKey(name: String, password: String): Either[String, Array[Byte]] = {
        logger.info(s"Obtaining user key for $name ...")
        for {
            keyStore <- Util.try2EitherWithLogging(cryptoManager.getFabricUserKeyStore(name, password))
            buffer = new ByteArrayOutputStream(1024)
            _ <- Util.try2EitherWithLogging(keyStore.store(buffer, password.toCharArray))
            result <- Util.try2EitherWithLogging(buffer.toByteArray)

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
            blockNumberLong <- Util.try2EitherWithLogging(blockNumber.toLong)
            state <- globalState.toRight("Node is not initialized yet")
            block <- state.networkManager.fetchChannelBlockByNum(channelName, blockNumberLong)
            buffer = new ByteArrayOutputStream(1024)
            _ <- Util.try2EitherWithLogging(block.writeTo(buffer))
            result <- Util.try2EitherWithLogging(buffer.toByteArray)
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
            result <- Util.try2EitherWithLogging((new GsonBuilder).create().fromJson(contracts, classOf[Array[Contract]]))
        } yield result
    }


    @Get("/service/list-applications")
    def listApplications: Either[String, Array[ApplicationInfo]] = {
        Util.try2EitherWithLogging(FabricServiceStateHolder.fullState.deployedApplications)
    }

    @Get("/service/list-application-state")
    def listApplicationState: Either[String, Array[ApplicationState]] = {
        Util.try2EitherWithLogging(FabricServiceStateHolder.fullState.applicationState)
    }

    @Get("/service/list-custom-component-descriptors")
    def listCustomComponentDescriptors: Either[String, Array[CustomComponentDescriptor]] = {
        Util.try2EitherWithLogging(FabricServiceStateHolder.fullState.customComponentDescriptors)
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
        Util.try2EitherWithLogging(FabricServiceStateHolder.fullState.contractDescriptors)
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
            gState <- Util.try2EitherWithLogging(
                Bootstrap.bootstrapOrganization(
                    organizationConfig,
                    bootstrapOptions,
                    cryptoManager,
                    hostsManager,
                    externalAddress,
                    profilePath,
                    processManager,
                )
            )
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
            caCerts <- Util.try2EitherWithLogging(addToChannelRequest.organizationCertificates.caCerts.map(Util.base64Decode).toIterable)
            tlsCACerts <- Util.try2EitherWithLogging(addToChannelRequest.organizationCertificates.tlsCACerts.map(Util.base64Decode).toIterable)
            adminCerts <- Util.try2EitherWithLogging(addToChannelRequest.organizationCertificates.adminCerts.map(Util.base64Decode).toIterable)
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
            gState <- Util.try2EitherWithLogging(
                Join.join(
                    organizationConfig,
                    cryptoManager,
                    joinOptions,
                    externalAddress,
                    hostsManager,
                    profilePath,
                    processManager,
                )
            )
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
            deploymentDescriptor <- Util.try2EitherWithLogging(Util.codec.fromJson(
                new FileReader(s"/opt/profile/chain-code/$filesBaseName.json"),
                classOf[ContractDeploymentDescriptor]
            ))
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
            r <- Util.try2EitherWithLogging(response.get())
        } yield {
            FabricServiceStateHolder.incrementVersion()
            s"Upgrading contract ${upgradeContractRequest.contractType} has been completed successfully $r"
        }
    }


    @Post("/admin/create-contract")
    def createContract(contractRequest: CreateContractRequest): Either[String, String] = {
        logger.info("Creating contract ...")
        for {
            state <- globalState.toRight("Node is not initialized yet")
            deploymentDescriptor <- getDeploymentDescriptor(contractRequest.contractType)
            _ <- createContractBase(contractRequest, deploymentDescriptor)
            r <- createInvitations(state, contractRequest, deploymentDescriptor)
        } yield {
            FabricServiceStateHolder.incrementVersion()
            s"Creating contract ${contractRequest.contractType} has been completed successfully ${r}"
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
                Util.try2EitherWithLogging(Util.codec.fromJson(queryResult, classOf[Contract]))
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
            invokeAwait <- Util.try2EitherWithLogging(invokeResultFuture.get())
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
            invokeAwait <- Util.try2EitherWithLogging(invokeResultFuture.get())
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
        FabricServiceStateHolder.fullState.events
        logger.info(s"Querying events for ${organizationConfig.name}...")
        Util.try2EitherWithLogging(FabricServiceStateHolder.fullState.events)
    }

    // ================================================================================================================

    private def ensureApplicationDownloaded(applicationType: String, applicationDescriptorPath: String): Either[String, Unit] = {
        val applicationDescriptorFile = new File(applicationDescriptorPath).getAbsoluteFile
        if (applicationDescriptorFile.exists()) {
            logger.info(s"Application type $applicationType is already downloaded")
            Right(())
        } else {
            logger.info(s"Component type $applicationType isn't downloaded...")
            for {
                state <- globalState.toRight("Node is not initialized yet")
                network = state.networkManager
                queryResult <- {
                    logger.info(s"Querying application with getApplication...")
                    network.queryChainCode(ServiceChannelName, ServiceChainCodeName, "getApplicationDistributive", applicationType)
                      .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight(s"There is an error with querying getApplication method in system chain-code"))
                }
                applicationDistributive <- Util.try2EitherWithLogging(Util.codec.fromJson(queryResult, classOf[ApplicationDistributive]))
            } yield {
                val request = DownloadApplicationRequest(
                    componentsDistributorUrl = applicationDistributive.componentsDistributorAddress,
                    applicationFileName = applicationType
                )
                downloadApplication(request)
                FabricServiceStateHolder.updateStateFullOption(state.eventsMonitor.updateApplications())
            }
        }
    }

    private def addDefaultProperties(props: Array[Property]): Array[Property] = {
        val orgNameDescriptor = Property("org", organizationConfig.name)
        val domainDescriptor = Property("domain", organizationConfig.domain)
        // TODO add other properties
        props ++ Array(orgNameDescriptor, domainDescriptor)
    }


    private def getDeploymentDescriptor(contractType: String): Either[String, ContractDeploymentDescriptor] = {
        val path = s"/opt/profile/chain-code/${contractType}.tgz"
        for {
            file <- Option(new File(path)).filter(_.exists()).toRight(s"File $contractType.tgz doesn't exist")
            deploymentDescriptor <- Util.readFromTarAs[ContractDeploymentDescriptor](file.toPath,
                s"$contractType.json").toRight(s"Descriptor hasn't been found in $path")
        } yield deploymentDescriptor
    }


    private def createContractBase(contractRequest: CreateContractRequest, deploymentDescriptor: ContractDeploymentDescriptor): Either[String, Unit] = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        val chainCodeName = s"${contractRequest.name}-${contractRequest.version}" //
        val path = s"/opt/profile/chain-code/${contractRequest.contractType}.tgz"
        for {
            state <- globalState.toRight("Node is not initialized yet")
            file <- Option(new File(path)).filter(_.exists()).toRight(s"File ${contractRequest.contractType}.tgz doesn't exist")
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
        } yield {
        }
    }

    private def createInvitations(state: GlobalState, contractRequest: CreateContractRequest, deploymentDescriptor: ContractDeploymentDescriptor): Either[String, String] = {
        for {
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
            r <- Util.try2EitherWithLogging(response.get())
        } yield r.toString
    }


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
