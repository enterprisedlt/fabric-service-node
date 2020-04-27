package org.enterprisedlt.fabric.service.node.flow

import java.io.{BufferedInputStream, FileInputStream}
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, OrganizationsOrdering, ServiceVersion}
import org.enterprisedlt.fabric.service.node.configuration.{JoinOptions, OSNConfig, OrganizationConfig}
import org.enterprisedlt.fabric.service.node.cryptography.{FabricCryptoMaterial, Orderer, Peer}
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.process._
import org.enterprisedlt.fabric.service.node.{process, _}
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Alexey Polubelov
 */
object Join {

    private val logger = LoggerFactory.getLogger(this.getClass)

    def join(
        organizationConfig: OrganizationConfig,
        cryptoManager: CryptoManager,
        joinOptions: JoinOptions,
        externalAddress: Option[ExternalAddress],
        hostsManager: HostsManager,
        profilePath: String,
        processManager: ProcessManager,
        state: AtomicReference[FabricServiceState]
    ): GlobalState = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        val cryptoPath = "/opt/profile/crypto"
        //
        logger.info(s"[ $organizationFullName ] - Creating JoinRequest ...")
        state.set(FabricServiceState(FabricServiceState.JoinCreatingJoinRequest))

        logger.info(s"[ $organizationFullName ] - Generating list of known hosts ...")
        val knownHosts =
            (
              joinOptions.network.orderingNodes.map(osn => osn.name -> osn.box) ++
                joinOptions.network.peerNodes.map(peer => peer.name -> peer.box)
              )
              .map { case (name, box) =>
                  processManager.getBoxAddress(box).map { boxAddress =>
                      KnownHostRecord(
                          dnsName = name,
                          ipAddress = boxAddress
                      )
                  }
              }.flatMap { list =>
                list.toSeq ++ externalAddress.map { serviceAddress =>
                    KnownHostRecord(
                        dnsName = s"service.$organizationFullName",
                        ipAddress = serviceAddress.host
                    )
                }
            }

        logger.debug(s"List of known hosts: ${knownHosts.toSeq}")
        hostsManager.updateHosts(knownHosts)
        processManager.updateKnownHosts(knownHosts)

        //
        val joinRequest = JoinRequest(
            organization = Organization(
                mspId = organizationConfig.name,
                name = organizationConfig.name,
                memberNumber = 0,
                knownHosts = knownHosts
            ),
            organizationCertificates = OrganizationCertificates(
                caCerts = Array(Util.readAsByteString(s"$cryptoPath/ca/ca.crt")).map(Util.base64Encode),
                tlsCACerts = Array(Util.readAsByteString(s"$cryptoPath/tlsca/tlsca.crt")).map(Util.base64Encode),
                adminCerts = Array(Util.readAsByteString(s"$cryptoPath/users/admin/admin.crt")).map(Util.base64Encode)

            )
        )
        //
        logger.info(s"[ $organizationFullName ] - Sending JoinRequest to ${joinOptions.invite.address} ...")
        state.set(FabricServiceState(FabricServiceState.JoinAwaitingJoin))
        val password = "join me" // TODO: password should be taken from request
        val key = Util.keyStoreFromBase64(joinOptions.invite.key, password)
        val joinResponse = Util.executePostRequest(s"https://${joinOptions.invite.address}/join-network", key, password, joinRequest, classOf[JoinResponse])

        logger.info(s"[ $organizationFullName ] - Updating known hosts")
        val knownHostFromJoin = joinResponse.knownOrganizations.flatMap(_.knownHosts)
        hostsManager.updateHosts(knownHostFromJoin)
        processManager.updateKnownHosts(knownHostFromJoin)

        //
        logger.info(s"[ $organizationFullName ] - Saving Osn cert to file ...")
        Util.storeToFile(s"/opt/profile/crypto/orderers/${joinResponse.osnHost}/tls/server.crt", Base64.getDecoder.decode(joinResponse.osnTLSCert))
        //
        logger.info(s"[ $organizationFullName ] - Saving genesis to boot from ...")
        Util.storeToFile("/opt/profile/artifacts/genesis.block", Base64.getDecoder.decode(joinResponse.genesis))

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
        state.set(FabricServiceState(FabricServiceState.JoinStartingOrdering))
        //
        logger.info(s"[ $organizationFullName ] - Initializing network ...")
        val admin = cryptoManager.loadDefaultAdmin
        val network = new FabricNetworkManager(organizationConfig, OSNConfig("", joinResponse.osnHost, joinResponse.osnPort), admin)
        network.defineChannel(ServiceChannelName)

        state.set(FabricServiceState(FabricServiceState.JoinConnectingToNetwork))

        logger.info(s"[ $organizationFullName ] - Connecting to channel ...")
        val organizationInfo =
            process.OrganizationConfig(
                mspId = organizationConfig.name,
                fullName = organizationFullName,
                cryptoMaterial = cryptoManager.getOrgCryptoMaterialPem
            )
        joinOptions.network.orderingNodes.foreach { osnConfig =>
            logger.info(s"[ ${osnConfig.name} ] - Adding ordering service to channel ...")
            val componentCrypto = cryptoManager.generateComponentCrypto(Orderer, osnConfig.name)
            cryptoManager.saveComponentCrypto(Orderer, osnConfig.name, componentCrypto)
            network.defineOsn(osnConfig)
            network.addOsnToChannel(osnConfig.name, cryptoPath)
            network.addOsnToChannel(osnConfig.name, cryptoPath, Some(ServiceChannelName))
            //
            processManager.startOrderingNode(
                osnConfig.box,
                StartOSNRequest(
                    port = osnConfig.port,
                    genesis = joinResponse.genesis,
                    organization = organizationInfo,
                    component = ComponentConfig(
                        fullName = osnConfig.name,
                        cryptoMaterial = ComponentCryptoMaterialPEM(
                            msp = FabricCryptoMaterial.asPem(componentCrypto.componentCert),
                            tls = FabricCryptoMaterial.asPem(componentCrypto.componentTLSCert)
                        )
                    )
                )
            )
            processManager.osnAwaitJoinedToRaft(osnConfig.box, osnConfig.name)
            processManager.osnAwaitJoinedToChannel(osnConfig.box, osnConfig.name, SystemChannelName)
            processManager.osnAwaitJoinedToChannel(osnConfig.box, osnConfig.name, ServiceChannelName)
        }
        //
        state.set(FabricServiceState(FabricServiceState.JoinStartingPeers))
        logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
        joinOptions.network.peerNodes.foreach { peerConfig =>
            val componentCrypto = cryptoManager.generateComponentCrypto(Peer, peerConfig.name)
            cryptoManager.saveComponentCrypto(Peer, peerConfig.name, componentCrypto)
            processManager.startPeerNode(
                peerConfig.box,
                StartPeerRequest(
                    port = peerConfig.port,
                    organization = organizationInfo,
                    component = ComponentConfig(
                        fullName = peerConfig.name,
                        cryptoMaterial = ComponentCryptoMaterialPEM(
                            msp = FabricCryptoMaterial.asPem(componentCrypto.componentCert),
                            tls = FabricCryptoMaterial.asPem(componentCrypto.componentTLSCert)
                        )
                    )
                )
            )
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
                  processManager.peerAwaitForBlock(peerConfig.box, peerConfig.name, lastBlockNum)
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
        network.installChainCode(
            ServiceChannelName,
            ServiceChainCodeName,
            joinResponse.version,
            "java",
            chainCodePkg)

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
                network.setupBlockListener(ServiceChannelName, new NetworkMonitor(organizationConfig, joinOptions.network, network, processManager, hostsManager, serviceVersion))
                state.set(FabricServiceState(FabricServiceState.Ready))
                GlobalState(network, joinOptions.network, joinOptions.invite.networkName)
        }
    }

    def joinOrgToNetwork(
        state: GlobalState,
        cryptoManager: CryptoManager,
        processManager: ProcessManager,
        joinRequest: JoinRequest,
        hostsManager: HostsManager,
        organizationConfig: OrganizationConfig
    ): Either[String, JoinResponse] = {

        logger.info(s"Joining ${joinRequest.organization.name} to network ...")
        logger.info(s"Joining ${joinRequest.organization.name} to consortium ...")
        state.networkManager.joinToNetwork(joinRequest)
        logger.info("Joining to channel 'service' ...")

        for {
            // join new org to service channel
            caCerts <- Try(joinRequest.organizationCertificates.caCerts.map(Util.base64Decode).toIterable)
              .toEither.left.map(_.getMessage)
            tlsCACerts <- Try(joinRequest.organizationCertificates.tlsCACerts.map(Util.base64Decode).toIterable)
              .toEither.left.map(_.getMessage)
            adminCerts <- Try(joinRequest.organizationCertificates.adminCerts.map(Util.base64Decode).toIterable)
              .toEither.left.map(_.getMessage)
            _ <- state.networkManager.joinToChannel(
                ServiceChannelName,
                joinRequest.organization.mspId,
                caCerts,
                tlsCACerts,
                adminCerts
            )
            // fetch current network version
            chainCodeVersion <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("getServiceVersion - Empty result"))
              .map(Util.codec.fromJson(_, classOf[ServiceVersion]))

            // fetch current list of organizations
            currentOrganizations <- state.networkManager
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("listOrganizations - Empty result"))
              .map(Util.codec.fromJson(_, classOf[Array[Organization]]).sorted(OrganizationsOrdering))

        } yield {
            // increment network version
            val nextNetworkVersion = chainCodeVersion.networkVersion.toInt + 1
            val nextVersion = s"${chainCodeVersion.chainCodeVersion}.$nextNetworkVersion"
            logger.info(s"Installing next version of service $nextVersion ...")
            val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))
            state.networkManager.installChainCode(
                ServiceChannelName,
                ServiceChainCodeName,
                nextVersion,
                "java",
                chainCodePkg)
            // update endorsement policy and private collections config
            val existingMspIds = currentOrganizations.map(_.mspId)
            val orgCodes = existingMspIds :+ joinRequest.organization.mspId
            val policyForCCUpgrade = Util.policyAnyOf(orgCodes)
            val nextCollections = calculateCollectionsConfiguration(orgCodes)
            logger.info(s"Next collections: ${nextCollections.map(_.name).mkString("[", ",", "]")}")
            logger.info(s"Upgrading version of service to $nextVersion ...")
            state.networkManager.upgradeChainCode(ServiceChannelName, ServiceChainCodeName, nextVersion, "JAVA",
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
                processManager.terminateChainCode(peer.box, peer.name, ServiceChainCodeName, previousVersion)
            }

            hostsManager.updateHosts(joinRequest.organization.knownHosts)
            processManager.updateKnownHosts(joinRequest.organization.knownHosts)

            // create result
            logger.info(s"Preparing JoinResponse ...")
            val latestBlock = state.networkManager.fetchLatestSystemBlock
            val osnConfigFirstOrg = state.network.orderingNodes.head
            val osnTLSCert = Util.base64Encode(Util.readAsByteString(s"/opt/profile/crypto/orderers/${osnConfigFirstOrg.name}/tls/server.crt"))
            JoinResponse(
                genesis = Base64.getEncoder.encodeToString(latestBlock.toByteArray),
                version = nextVersion,
                knownOrganizations = currentOrganizations,
                osnHost = osnConfigFirstOrg.name,
                osnPort = osnConfigFirstOrg.port,
                osnTLSCert = osnTLSCert
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
