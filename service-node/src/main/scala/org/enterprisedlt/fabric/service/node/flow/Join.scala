package org.enterprisedlt.fabric.service.node.flow

import java.io.File
import java.util.Base64

import org.enterprisedlt.fabric.service.model.{ServiceVersion, Organization, OrganizationsOrdering}
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.model.{Invite, JoinRequest, JoinResponse}
import org.enterprisedlt.fabric.service.node.proto.FabricBlock
import org.hyperledger.fabric.protos.common.Configtx
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Join {

    private val logger = LoggerFactory.getLogger(this.getClass)

    def join(config: ServiceConfig, cryptoManager: FabricCryptoManager, processManager: FabricProcessManager, invite: Invite): FabricNetworkManager = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        logger.info(s"[ $organizationFullName ] - Generating certificates ...")
        cryptoManager.generateCryptoMaterial()

        //
        logger.info(s"[ $organizationFullName ] - Creating JoinRequest ...")
        val genesisDefinition = Bootstrap.newGenesisDefinition("/opt/profile", config)
        val genesis = FabricBlock.create(genesisDefinition)
        val genesisConfig = Util.extractConfig(genesis)
        val joinRequest = JoinRequest(
            genesisConfig = Base64.getEncoder.encodeToString(genesisConfig.toByteArray),
            mspId = config.organization.name
        )

        //
        logger.info(s"[ $organizationFullName ] - Sending JoinRequest to ${invite.address} ...")
        val joinResponse = Util.executePostRequest(s"http://${invite.address}/join-network", joinRequest, classOf[JoinResponse])

        //
        logger.info(s"[ $organizationFullName ] - Saving genesis to boot from ...")
        Util.storeToFile("/opt/profile/artifacts/genesis.block", Base64.getDecoder.decode(joinResponse.genesis))

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
        config.network.orderingNodes.headOption.foreach { osnConfig =>
            processManager.startOrderingNode(osnConfig.name, osnConfig.port)
        }
        config.network.orderingNodes.headOption.foreach { osnConfig =>
            processManager.osnAwaitJoinedToRaft(osnConfig.name)
            processManager.osnAwaitJoinedToChannel(osnConfig.name, SystemChannelName)
            processManager.osnAwaitJoinedToChannel(osnConfig.name, ServiceChannelName)
        }

        //
        logger.info(s"[ $organizationFullName ] - Starting peer nodes ...")
        config.network.peerNodes.foreach { peerConfig =>
            processManager.startPeerNode(peerConfig.name, peerConfig.port)
        }

        //
        logger.info(s"[ $organizationFullName ] - Initializing network ...")
        val orderingAdmin = cryptoManager.loadOrderingAdmin
        val executionAdmin = cryptoManager.loadExecutionAdmin
        val network = new FabricNetworkManager(config, orderingAdmin, executionAdmin)


        //
        logger.info(s"[ $organizationFullName ] - Connecting to channel ...")
        network.defineChannel(ServiceChannelName)

        //
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            network.addPeerToChannel(ServiceChannelName, peerConfig.name)
            // get latest channel block number and await for peer to commit it
            network.fetchLatestChannelBlock(ServiceChannelName) match {
                case Right(block) =>
                    val lastBlockNum = block.getHeader.getNumber
                    processManager.peerAwaitForBlock(peerConfig.name, lastBlockNum)
                case Left(error) =>
                    logger.error(error)
                    throw new Exception(error)
            }
        }

        //
        logger.info(s"[ $organizationFullName ] - Updating anchors for channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            network.addAnchorsToChannel(ServiceChannelName, peerConfig.name)
        }

        //
        logger.info(s"[ $organizationFullName ] - Preparing service chain code ...")
        val chainCodePkg = Util.generateTarGzInputStream(new File(s"/opt/chaincode/common"))

        logger.info(s"[ $organizationFullName ] - Installing service chain code ...")
        network.installChainCode(ServiceChannelName, ServiceChainCodeName, joinResponse.version, chainCodePkg)

        // fetch current network version
        logger.info(s"[ $organizationFullName ] - Warming up service chain code ...")
        network
          .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
          .map(_.head) //TODO: proper handling of empty result
          .map(Util.codec.fromJson(_, classOf[ServiceVersion]))

        network
    }

    def joinOrgToNetwork(
        config: ServiceConfig, cryptoManager: FabricCryptoManager,
        processManager: FabricProcessManager, network: FabricNetworkManager,
        joinRequest: JoinRequest
    ): Either[String, JoinResponse] = {
        logger.info(s"Joining ${joinRequest.mspId} to network ...")
        val newOrgConfig = Configtx.Config.parseFrom(Base64.getDecoder.decode(joinRequest.genesisConfig))
        logger.info(s"Joining ${joinRequest.mspId} to consortium ...")
        network.joinToNetwork(newOrgConfig)

        logger.info("Joining to channel 'service' ...")

        for {
            // join new org to service channel
            _ <- network.joinToChannel(ServiceChannelName, newOrgConfig)

            // fetch current network version
            chainCodeVersion <- network
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "getServiceVersion")
              .map(_.head) //TODO: proper handling of empty result
              .map(Util.codec.fromJson(_, classOf[ServiceVersion]))

            // fetch current list of organizations
            orgs <- network
              .queryChainCode(ServiceChannelName, ServiceChainCodeName, "listOrganizations")
              .map(_.head) //TODO: proper handling of empty result
              .map(Util.codec.fromJson(_, classOf[Array[Organization]]).sorted(OrganizationsOrdering))

            // fetch list of private collections if require
            currentCollections <- fetchCurrentCollection(config, network, orgs)

        } yield {
            // increment network version
            val nextNetworkVersion = chainCodeVersion.networkVersion.toInt + 1
            val nextVersion = s"${chainCodeVersion.chainCodeVersion}.$nextNetworkVersion"
            logger.info(s"Installing next version of service $nextVersion ...")
            val gunZipFile = Util.generateTarGzInputStream(new File(s"/opt/chaincode/common"))
            network.installChainCode(ServiceChannelName, ServiceChainCodeName, nextVersion, gunZipFile)
            // update endorsement policy and private collections config
            val orgCodes = orgs.map(_.mspId) :+ joinRequest.mspId
            val policyForCCUpgrade = Util.policyAnyOf(orgCodes)
            val nextCollections = currentCollections ++ mkCollectionsToAdd(orgCodes, joinRequest.mspId)
            logger.info(s"Upgrading version of service to $nextVersion ...")
            network.upgradeChainCode(ServiceChannelName, ServiceChainCodeName, nextVersion,
                endorsementPolicy = Option(policyForCCUpgrade),
                collectionConfig = Option(Util.createCollectionsConfig(nextCollections)),
                arguments = Array(
                    Util.codec.toJson(
                        Organization(
                            mspId = joinRequest.mspId,
                            name = joinRequest.mspId,
                            memberNumber = orgs.length
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
            config.network.peerNodes.foreach { peer =>
                val previousVersion = s"${chainCodeVersion.chainCodeVersion}.${chainCodeVersion.networkVersion}"
                logger.info(s"Removing previous version [$previousVersion] of service on ${peer.name} ...")
                processManager.terminateChainCode(peer.name, ServiceChainCodeName, previousVersion)
            }

            // create result
            logger.info(s"Preparing JoinResponse ...")
            val latestBlock = network.fetchLatestSystemBlock
            JoinResponse(
                genesis = Base64.getEncoder.encodeToString(latestBlock.toByteArray),
                version = nextVersion
            )
        }
    }

    private def fetchCurrentCollection(
        config: ServiceConfig,
        network: FabricNetworkManager,
        currentOrgs: Array[Organization]
    ): Either[String, Iterable[PrivateCollectionConfiguration]] =
        if (currentOrgs.length == 1) {
            Right(Seq.empty)
        } else {
            network
              .fetchCollectionsConfig(
                  config.network.peerNodes.head.name,
                  ServiceChannelName, ServiceChainCodeName
              )
        }


    //=========================================================================
    def mkCollectionsToAdd(
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
