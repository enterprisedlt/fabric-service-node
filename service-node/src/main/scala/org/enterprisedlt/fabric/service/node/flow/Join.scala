package org.enterprisedlt.fabric.service.node.flow

import java.io.{File, FileOutputStream}
import java.util.Base64

import org.enterprisedlt.fabric.service.contract.model.{CCVersion, Organisation, OrganizationsOrdering}
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.model.{Invite, JoinRequest, JoinResponse}
import org.enterprisedlt.fabric.service.node.proto.FabricBlock
import org.enterprisedlt.fabric.service.node._
import org.hyperledger.fabric.protos.common.Configtx
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Join {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def join(config: ServiceConfig, cryptoManager: FabricCryptoManager, processManager: FabricProcessManager, invite: Invite): Unit = {
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
        }
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
            _ <- network
              .joinToChannel("service", newOrgConfig)

            // fetch current network version
            chainCodeVersion <- network
              .queryChainCode("service", "service", "getCCVersion", "service")
              .map(_.head) //TODO: proper handling of empty result
              .map(Util.codec.fromJson(_, classOf[CCVersion]))

            // fetch current list of organizations
            orgs <- network
              .queryChainCode("service", "service", "listOrganisations")
              .map(_.head) //TODO: proper handling of empty result
              .map(Util.codec.fromJson(_, classOf[Array[Organisation]]).sorted(OrganizationsOrdering))

            // fetch list of private collections if require
            currentCollections <- fetchCurrentCollection(config, network, orgs)

        } yield {
            // increment network version
            val nextNetworkVersion = chainCodeVersion.networkVer.toInt + 1
            val nextVersion = s"${chainCodeVersion.ccVer}.$nextNetworkVersion"
            val gunZipFile = Util.generateTarGzInputStream(new File(s"/opt/chaincode/common"))
            network.installChainCode("service", "service", nextVersion, gunZipFile)
            // update endorsement policy and private collections config
            val orgCodes = orgs.map(_.code) :+ joinRequest.mspId
            val policyForCCUpgrade = Util.policyAnyOf(orgCodes)
            val nextCollections = currentCollections ++ mkCollectionsToAdd(orgCodes, joinRequest.mspId)
            network.upgradeChainCode("service", "service", nextVersion,
                endorsementPolicy = Option(policyForCCUpgrade),
                collectionConfig = Option(Util.createCollectionsConfig(nextCollections)),
                arguments = Array(
                    joinRequest.mspId, // organizationCode
                    joinRequest.mspId, // organizationName
                    orgs.length.toString, // orgNumber
                    chainCodeVersion.ccVer, // chainCodeVersion
                    nextNetworkVersion.toString // networkVersion
                )
            )
            // create result
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
        currentOrgs: Array[Organisation]
    ): Either[String, Iterable[PrivateCollectionConfiguration]] =
        if (currentOrgs.length == 1) {
            Right(Seq.empty)
        } else {
            network
              .fetchCollectionsConfig(
                  config.network.peerNodes.head.name,
                  "service", "service"
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
