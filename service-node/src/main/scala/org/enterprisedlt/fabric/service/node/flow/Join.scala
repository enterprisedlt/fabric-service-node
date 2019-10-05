package org.enterprisedlt.fabric.service.node.flow

import java.io.{BufferedInputStream, FileInputStream}
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, OrganizationsOrdering, ServiceVersion}
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.configuration.{JoinOptions, ServiceConfig}
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.process.DockerBasedProcessManager
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Join {

    private val logger = LoggerFactory.getLogger(this.getClass)

    def join(
        config: ServiceConfig, cryptoManager: CryptoManager,
        joinOptions: JoinOptions,
        externalAddress: Option[ExternalAddress],
        hostsManager: HostsManager,
        profilePath: String,
        dockerSocket: String,
        initialName: String,
        state: AtomicReference[FabricServiceState]
    ): GlobalState = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        val cryptoPath = "/opt/profile/crypto"

        val firstOrderingNode = joinOptions.network.orderingNodes.head
        //
        logger.info(s"[ $organizationFullName ] - Creating JoinRequest ...")
        state.set(FabricServiceState(FabricServiceState.JoinCreatingJoinRequest))
        //
        val joinRequest = JoinRequest(
            organization = Organization(
                mspId = config.organization.name,
                name = config.organization.name,
                memberNumber = 0,
                knownHosts = externalAddress.map { address =>
                    joinOptions.network.orderingNodes.map(osn => KnownHostRecord(address.host, s"${osn.name}.$organizationFullName")) ++
                      joinOptions.network.peerNodes.map(peer => KnownHostRecord(address.host, s"${peer.name}.$organizationFullName")) :+
                      KnownHostRecord(address.host, s"service.$organizationFullName")
                }
                  .getOrElse(Array.empty)
            ),
            organizationCertificates = OrganizationCertificates(
                caCerts = Array(Util.readAsByteString(s"$cryptoPath/ca/ca.crt")).map(Util.base64Encode),
                tlsCACerts = Array(Util.readAsByteString(s"$cryptoPath/tlsca/tlsca.crt")).map(Util.base64Encode),
                adminCerts = Array(Util.readAsByteString(s"$cryptoPath/users/admin/admin.crt")).map(Util.base64Encode)

            ),
            osnCertificates = OsnCertificates(
                clientTlsCert = Util.base64Encode(Util.readAsByteString(s"$cryptoPath/orderers/${firstOrderingNode.name}.$organizationFullName/tls/server.crt")),
                serverTlsCert = Util.base64Encode(Util.readAsByteString(s"$cryptoPath/orderers/${firstOrderingNode.name}.$organizationFullName/tls/server.crt"))
            ),
            osnHost = s"${firstOrderingNode.name}.$organizationFullName",
            osnPort = firstOrderingNode.port
        )

        //
        logger.info(s"[ $organizationFullName ] - Sending JoinRequest to ${joinOptions.invite.address} ...")
        state.set(FabricServiceState(FabricServiceState.JoinAwaitingJoin))
        val password = "join me" // TODO: password should be taken from request
        val key = Util.keyStoreFromBase64(joinOptions.invite.key, password)
        val joinResponse = Util.executePostRequest(s"https://${joinOptions.invite.address}/join-network", key, password, joinRequest, classOf[JoinResponse])

        joinResponse.knownOrganizations.foreach(hostsManager.addOrganization)

        logger.info(s"[ $organizationFullName ] - Starting process manager ...")
        val processManager = new DockerBasedProcessManager(
            profilePath,
            dockerSocket,
            initialName,
            config,
            joinOptions.network
        )
        //
        logger.info(s"[ $organizationFullName ] - Saving genesis to boot from ...")
        Util.storeToFile("/opt/profile/artifacts/genesis.block", Base64.getDecoder.decode(joinResponse.genesis))

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
        state.set(FabricServiceState(FabricServiceState.JoinStartingOrdering))

        joinOptions.network.orderingNodes.headOption.foreach { osnConfig =>
            processManager.startOrderingNode(osnConfig.name)
            processManager.osnAwaitJoinedToRaft(osnConfig.name)
            processManager.osnAwaitJoinedToChannel(osnConfig.name, SystemChannelName)
            processManager.osnAwaitJoinedToChannel(osnConfig.name, ServiceChannelName)
        }

        //
        logger.info(s"[ $organizationFullName ] - Initializing network ...")
        val admin = cryptoManager.loadDefaultAdmin
        val network = new FabricNetworkManager(config.organization, joinOptions.network.orderingNodes.head, admin)
        network.defineChannel(ServiceChannelName)

        state.set(FabricServiceState(FabricServiceState.JoinConnectingToNetwork))

        logger.info(s"[ $organizationFullName ] - Connecting to channel ...")
        joinOptions.network.orderingNodes.tail.foreach { osnConfig =>
            logger.info(s"[ ${osnConfig.name}.$organizationFullName ] - Adding ordering service to channel ...")
            network.defineOsn(osnConfig)
            network.addOsnToChannel(osnConfig.name, cryptoPath)
            network.addOsnToChannel(osnConfig.name, cryptoPath, Some(ServiceChannelName))
            //
            processManager.startOrderingNode(osnConfig.name)
            processManager.osnAwaitJoinedToRaft(osnConfig.name)
            processManager.osnAwaitJoinedToChannel(osnConfig.name, SystemChannelName)
            processManager.osnAwaitJoinedToChannel(osnConfig.name, ServiceChannelName)
        }
        //
        state.set(FabricServiceState(FabricServiceState.JoinStartingPeers))
        logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
        joinOptions.network.peerNodes.foreach { peerConfig =>
            processManager.startPeerNode(peerConfig.name)
            network.definePeer(peerConfig)
        }
        state.set(FabricServiceState(FabricServiceState.JoinAddingPeersToChannel))
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        joinOptions.network.peerNodes.foreach { peerConfig =>
            network.addPeerToChannel(ServiceChannelName, peerConfig.name)
              .flatMap { _ =>
                  // get latest channel block number and await for peer to commit it
                  network.fetchLatestChannelBlock(ServiceChannelName)
              }
              .map { block =>
                  val lastBlockNum = block.getHeader.getNumber
                  processManager.peerAwaitForBlock(peerConfig.name, lastBlockNum)
              }
            match {
                case Right(_) => // NoOp
                case Left(error) =>
                    logger.error(error)
                    throw new Exception(error)
            }
        }

        //
        state.set(FabricServiceState(FabricServiceState.JoinUpdatingAnchors))
        logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
        joinOptions.network.peerNodes.foreach { peerConfig =>
            network.addAnchorsToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        state.set(FabricServiceState(FabricServiceState.JoinInstallingServiceChainCode))
        logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
        val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))

        logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
        network.installChainCode(ServiceChannelName, ServiceChainCodeName, joinResponse.version, chainCodePkg)

        // fetch current network version
        state.set(FabricServiceState(FabricServiceState.JoinInitializingServiceChainCode))
        logger.info(s"[ $organizationFullName ] - Warming up service chain code ...")
        implicit val timeout: OperationTimeout = OperationTimeout(5, TimeUnit.MINUTES)
        network
          .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
          .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
          .map(Util.codec.fromJson(_, classOf[ServiceVersion]))
        match {
            case Left(error) => throw new Exception(s"Failed to warn up service chain code: $error")
            case Right(serviceVersion) =>
                state.set(FabricServiceState(FabricServiceState.JoinSettingUpBlockListener))
                network.setupBlockListener(ServiceChannelName, new NetworkMonitor(config, joinOptions.network, network, processManager, hostsManager, serviceVersion))
                state.set(FabricServiceState(FabricServiceState.Ready))
                GlobalState(network, processManager,joinOptions.network)
        }
    }

    def joinOrgToNetwork(
        config: ServiceConfig, state: GlobalState,
        cryptoManager: CryptoManager,
        joinRequest: JoinRequest, hostsManager: HostsManager,
    ): Either[String, JoinResponse] = {

        logger.info(s"Joining ${joinRequest.organization.name} to network ...")
        logger.info(s"Joining ${joinRequest.organization.name} to consortium ...")
        state.networkManager.joinToNetwork(joinRequest)
        logger.info("Joining to channel 'service' ...")

        for {
            // join new org to service channel
            _ <- state.networkManager.joinToChannel(ServiceChannelName, joinRequest)

            // fetch current network version
            chainCodeVersion <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
              .map(Util.codec.fromJson(_, classOf[ServiceVersion]))

            // fetch current list of organizations
            currentOrganizations <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
              .map(Util.codec.fromJson(_, classOf[Array[Organization]]).sorted(OrganizationsOrdering))

        } yield {
            // increment network version
            val nextNetworkVersion = chainCodeVersion.networkVersion.toInt + 1
            val nextVersion = s"${chainCodeVersion.chainCodeVersion}.$nextNetworkVersion"
            logger.info(s"Installing next version of service $nextVersion ...")
            val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))
            state.networkManager.installChainCode(ServiceChannelName, ServiceChainCodeName, nextVersion, chainCodePkg)
            // update endorsement policy and private collections config
            val existingMspIds = currentOrganizations.map(_.mspId)
            val orgCodes = existingMspIds :+ joinRequest.organization.mspId
            val policyForCCUpgrade = Util.policyAnyOf(orgCodes)
            val nextCollections = calculateCollectionsConfiguration(orgCodes)
            logger.info(s"Next collections: ${nextCollections.map(_.name).mkString("[", ",", "]")}")
            logger.info(s"Upgrading version of service to $nextVersion ...")
            state.networkManager.upgradeChainCode(ServiceChannelName, ServiceChainCodeName, nextVersion,
                endorsementPolicy = Option(policyForCCUpgrade),
                collectionConfig = Option(Util.createCollectionsConfig(nextCollections)),
                arguments = Array(
                    Util.codec.toJson(
                        joinRequest.organization.copy(
                            memberNumber = currentOrganizations.length + 1
                        )
                    ),
                    Util.codec.toJson(
                        ServiceVersion(
                            chainCodeVersion = chainCodeVersion.chainCodeVersion,
                            networkVersion = nextNetworkVersion.toString
                        )
                    )
                )
            )

            // clean out old chain code container
            state.network.peerNodes.foreach { peer =>
                val previousVersion = s"${chainCodeVersion.chainCodeVersion}.${chainCodeVersion.networkVersion}"
                logger.info(s"Removing previous version [$previousVersion] of service on ${peer.name} ...")
                state.processManager.terminateChainCode(peer.name, ServiceChainCodeName, previousVersion)
            }

            hostsManager.addOrganization(joinRequest.organization)

            // create result
            logger.info(s"Preparing JoinResponse ...")
            val latestBlock = state.networkManager.fetchLatestSystemBlock
            JoinResponse(
                genesis = Base64.getEncoder.encodeToString(latestBlock.toByteArray),
                version = nextVersion,
                knownOrganizations = currentOrganizations
            )
        }
    }

    //    private def fetchCurrentCollection(
    //        config: ServiceConfig,
    //        network: FabricNetworkManager,
    //        currentOrgs: Array[Organization]
    //    ): Either[String, Iterable[PrivateCollectionConfiguration]] =
    //        if (currentOrgs.length == 1) {
    //            Right(Seq.empty)
    //        } else {
    //            network
    //              .fetchCollectionsConfig(
    //                  config.network.peerNodes.head.name,
    //                  ServiceChannelName, ServiceChainCodeName
    //              )
    //        }


    //=========================================================================
    private def mkCollectionsToAdd(
        currentOrgCodes: Iterable[String],
        joiningOrgCode: String
    ): Iterable[PrivateCollectionConfiguration] =
        currentOrgCodes.map(existingOrgCode =>
            PrivateCollectionConfiguration(
                s"$joiningOrgCode-$existingOrgCode",
                Array(joiningOrgCode, existingOrgCode)
            )
        )

    private def calculateCollectionsConfiguration(
        organizations: Array[String]
    ): Iterable[PrivateCollectionConfiguration] =
        organizations
          .foldLeft(
              (
                List.empty[String], // initially we have no organization
                List.empty[PrivateCollectionConfiguration]) // initially we have no collections
          ) {
              case ((currentOrganizationsList, collectionsList), newOrganiztion) =>
                  (
                    currentOrganizationsList :+ newOrganiztion,
                    collectionsList ++ mkCollectionsToAdd(currentOrganizationsList, newOrganiztion)
                  )
          }._2


}
