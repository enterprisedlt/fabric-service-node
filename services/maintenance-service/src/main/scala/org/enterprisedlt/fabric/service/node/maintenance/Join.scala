package org.enterprisedlt.fabric.service.node.maintenance

import java.util.Base64
import java.util.concurrent.TimeUnit

import org.enterprisedlt.fabric.service.model.{KnownHostRecord, Organization, OrganizationsOrdering, ServiceVersion}
import org.enterprisedlt.fabric.service.node.ExternalAddress
import org.enterprisedlt.fabric.service.node.client.OperationTimeout
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.constant.Constant.{ServiceChainCodeName, ServiceChannelName, SystemChannelName}
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.rest.JsonRestClient
import org.enterprisedlt.fabric.service.node.services._
import org.enterprisedlt.fabric.service.node.util.{PrivateCollectionConfiguration, Util}
import org.hyperledger.fabric.protos.common.Common.Block
import org.hyperledger.fabric.sdk.User
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Join {

    private val logger = LoggerFactory.getLogger(this.getClass)

    def join(
        config: ServiceConfig,
        user: User,
        processManagementClient: ProcessManagementManager,
        administrationClient: AdministrationManager,
        proxyClient: ProxyManager,
        externalAddress: Option[ExternalAddress],
        cryptoPath: String,
        invite: Invite,
        hostsManager: HostsManager
    ): Either[String, Unit] = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        val firstOrderingNode = config.network.orderingNodes.head
        logger.info(s"[ $organizationFullName ] - Generating certificates ...")
        //
        logger.info(s"[ $organizationFullName ] - Creating JoinRequest ...")
        //

        val joinRequest = JoinRequest(
            organization = Organization(
                mspId = config.organization.name,
                name = config.organization.name,
                memberNumber = 0,
                knownHosts = externalAddress.map { address =>
                    config.network.orderingNodes.map(osn => KnownHostRecord(address.host, s"${osn.name}.$organizationFullName")) ++
                      config.network.peerNodes.map(peer => KnownHostRecord(address.host, s"${peer.name}.$organizationFullName")) :+
                      KnownHostRecord(address.host, s"service.$organizationFullName")
                }
                  .getOrElse(Array.empty)
            ),
            organizationCertificates = OrganizationCertificates(
                caCerts = Util.readAsByteString(s"$cryptoPath/ca/ca.crt").toString,
                tlsCACerts = Util.readAsByteString(s"$cryptoPath/tlsca/tlsca.crt").toString,
                adminCerts = Util.readAsByteString(s"$cryptoPath/users/admin/admin.crt").toString
            ),
            osnCertificates = OsnCertificates(
                clientTlsCert = Util.base64Encode(Util.readAsByteString(s"$cryptoPath/orderers/${firstOrderingNode.name}.$organizationFullName/tls/server.crt")),
                serverTlsCert = Util.base64Encode(Util.readAsByteString(s"$cryptoPath/orderers/${firstOrderingNode.name}.$organizationFullName/tls/server.crt"))
            ),
            osnHost = s"${firstOrderingNode.name}.$organizationFullName",
            osnPort = firstOrderingNode.port
        )

        //
        logger.info(s"[ $organizationFullName ] - Sending JoinRequest to ${invite.address} ...")
        val password = "join me" // TODO: password should be taken from request
        val key = Util.keyStoreFromBase64(invite.key, password) // TODO password
        val maintenanceClient = JsonRestClient.create[MaintenanceManager](s"http://${invite.address}")
        //        val joinResponse = Util.executePostRequest(s"https://${invite.address}/join-network", key, password, joinRequest, classOf[JoinResponse])
        maintenanceClient.joinOrgToNetwork(joinRequest) match {
            case Right(response) =>
                response.knownOrganizations.foreach(hostsManager.addOrganization)
                //
                logger.info(s"[ $organizationFullName ] - Saving genesis to boot from ...")
                Util.storeToFile("/opt/profile/artifacts/genesis.block", Base64.getDecoder.decode(response.genesis))

                //
                logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
                config.network.orderingNodes.headOption.foreach { osnConfig =>
                    processManagementClient.startOrderingNode(osnConfig.name)
                    processManagementClient.osnAwaitJoinedToRaft(osnConfig.name)
                    processManagementClient.osnAwaitJoinedToChannel(osnConfig.name, SystemChannelName)
                    processManagementClient.osnAwaitJoinedToChannel(osnConfig.name, ServiceChannelName)
                }

                //
                logger.info(s"[ $organizationFullName ] - Initializing network ...")


                administrationClient.defineChannel(ServiceChannelName)
                logger.info(s"[ $organizationFullName ] - Connecting to channel ...")
                config.network.orderingNodes.tail.foreach { osnConfig =>
                    logger.info(s"[ ${osnConfig.name}.$organizationFullName ] - Adding ordering service to channel ...")
                    administrationClient.defineOsn(osnConfig)
                    administrationClient.addOsnToConsortium(osnConfig.name)
                    administrationClient.addOsnToChannel(AddOsnToChannelRequest(ServiceChannelName, osnConfig.name))
                    //
                    processManagementClient.startOrderingNode(osnConfig.name)
                    processManagementClient.osnAwaitJoinedToRaft(osnConfig.name)
                    processManagementClient.osnAwaitJoinedToChannel(osnConfig.name, SystemChannelName)
                    processManagementClient.osnAwaitJoinedToChannel(osnConfig.name, ServiceChannelName)
                }
                //
                logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
                config.network.peerNodes.foreach { peerConfig =>
                    processManagementClient.startPeerNode(peerConfig.name)
                    administrationClient.definePeer(peerConfig)
                }
                logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
                config.network.peerNodes.foreach { peerConfig =>
                    administrationClient.addPeerToChannel(AddPeerToChannelRequest(ServiceChannelName, peerConfig.name))
                      .flatMap { _ =>
                          // get latest channel block number and await for peer to commit it
                          administrationClient.fetchLatestChannelBlock(ServiceChannelName)
                      }
                      .map { block =>
                          val lastBlockNum = block.asInstanceOf[Block].getHeader.getNumber
                          processManagementClient.peerAwaitForBlock(peerConfig.name, lastBlockNum)
                      }
                    match {
                        case Right(_) => // NoOp
                        case Left(error) =>
                            logger.error(error)
                            throw new Exception(error)
                    }
                }

                //
                logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
                config.network.peerNodes.foreach { peerConfig =>
                    administrationClient.addAnchorsToChannel(AddAnchorToChannelRequest(ServiceChannelName, peerConfig.name))
                }

                //
                logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
                //                val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))

                logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
                administrationClient.installChainCode(InstallChainCodeRequest(ServiceChannelName, ServiceChainCodeName, response.version))

                // fetch current network version
                logger.info(s"[ $organizationFullName ] - Warming up service chain code ...")
                implicit val timeout: OperationTimeout = OperationTimeout(5, TimeUnit.MINUTES)
                proxyClient
                  .queryChainCode(ChaincodeRequest(ServiceChannelName, ServiceChainCodeName, "getServiceVersion"))
                  .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
                  .map(Util.codec.fromJson(_, classOf[ServiceVersion]))
                match {
                    case Left(error) => throw new Exception(s"Failed to warn up service chain code: $error")
                    case Right(serviceVersion) =>
                        //                network.setupBlockListener(ServiceChannelName, new NetworkMonitor(config, network, processManager, hostsManager, serviceVersion)) TODO
                        //                network
                        Right(())
                }
            case Left(error) => throw new Error(s"Unable to get response: $error")
        }
    }

    def joinOrgToNetwork(
        processManagementClient: ProcessManagementManager,
        administrationClient: AdministrationManager,
        proxyClient: ProxyManager,
        config: ServiceConfig,
        cryptoPath: String,
        joinRequest: JoinRequest,
        hostsManager: HostsManager
    ): Either[String, JoinResponse] = {

        logger.info(s"Joining ${joinRequest.organization.name} to network ...")
        administrationClient.addOrgToConsortium(AddOrgToConsortiumRequest(joinRequest.organization, joinRequest.organizationCertificates))
        logger.info(s"Joining ${joinRequest.organization.name} to consortium ...")
        administrationClient.addOsnToConsortium(joinRequest.osnHost)
        logger.info("Joining to channel 'service' ...")

        for {
            // join new org to service channel
            _ <- administrationClient.addOrgToChannel(AddOrgToChannelRequest(ServiceChannelName, joinRequest.organization, joinRequest.organizationCertificates))
            _ <- administrationClient.addOsnToChannel(AddOsnToChannelRequest(ServiceChannelName, joinRequest.osnHost))
            // fetch current network version
            chainCodeVersion <- proxyClient
              .queryChainCode(ChaincodeRequest(ServiceChannelName, ServiceChainCodeName, "getServiceVersion"))
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
              .map(Util.codec.fromJson(_, classOf[ServiceVersion]))

            // fetch current list of organizations
            currentOrganizations <- proxyClient
              .queryChainCode(ChaincodeRequest(ServiceChannelName, ServiceChainCodeName, "listOrganizations"))
              .flatMap(_.headOption.map(_.toStringUtf8).filter(_.nonEmpty).toRight("Empty result"))
              .map(Util.codec.fromJson(_, classOf[Array[Organization]]).sorted(OrganizationsOrdering))
        } yield {
            // increment network version
            val nextNetworkVersion = chainCodeVersion.networkVersion.toInt + 1
            val nextVersion = s"${chainCodeVersion.chainCodeVersion}.$nextNetworkVersion"
            logger.info(s"Installing next version of service $nextVersion ...")
            //            val chainCodePkg = new BufferedInputStream(new FileInputStream(ServiceChainCodePath))
            administrationClient.installChainCode(InstallChainCodeRequest(ServiceChannelName, ServiceChainCodeName, nextVersion))
            // update endorsement policy and private collections config
            val existingMspIds = currentOrganizations.map(_.mspId)
            val orgCodes = existingMspIds :+ joinRequest.organization.mspId
            val policyForCCUpgrade = Util.policyAnyOf(orgCodes) // TODO
            val nextCollections = calculateCollectionsConfiguration(orgCodes)
            logger.info(s"Next collections: ${nextCollections.map(_.name).mkString("[", ",", "]")}")
            logger.info(s"Upgrading version of service to $nextVersion ...")
            administrationClient.upgradeChainCode(InstantiateChainCodeRequest(
                ServiceChannelName,
                ServiceChainCodeName,
                nextVersion))
            // clean out old chain code container
            config.network.peerNodes.foreach { peer =>
                val previousVersion = s"${chainCodeVersion.chainCodeVersion}.${chainCodeVersion.networkVersion}"
                logger.info(s"Removing previous version [$previousVersion] of service on ${peer.name} ...")
                processManagementClient.terminateChainCode(peer.name, ServiceChainCodeName, previousVersion)
            }
            hostsManager.addOrganization(joinRequest.organization)
            // create result
            logger.info(s"Preparing JoinResponse ...")
            administrationClient.fetchLatestSystemBlock match {
                case Right(x) => JoinResponse(
                    genesis = Base64.getEncoder.encodeToString(x),
                    version = nextVersion,
                    knownOrganizations = currentOrganizations
                )
                case Left(_) => throw new Error("Unable to fetch block") // TODO
            }
        }
    }

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

}
