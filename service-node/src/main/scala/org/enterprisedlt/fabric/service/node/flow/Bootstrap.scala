package org.enterprisedlt.fabric.service.node.flow

import java.io.File

import org.enterprisedlt.fabric.service.model.{Organization, ServiceVersion}
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.Constant._
import org.enterprisedlt.fabric.service.node.proto._
import org.enterprisedlt.fabric.service.node.{FabricCryptoManager, FabricNetworkManager, FabricProcessManager, Util}
import org.hyperledger.fabric.protos.common.MspPrincipal.MSPRole
import org.slf4j.LoggerFactory

/**
  * @author Alexey Polubelov
  */
object Bootstrap {
    private val logger = LoggerFactory.getLogger(this.getClass)

    def bootstrapOrganization(config: ServiceConfig, cryptoManager: FabricCryptoManager, processManager: FabricProcessManager): FabricNetworkManager = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        logger.info(s"[ $organizationFullName ] - Generating certificates ...")
        cryptoManager.generateCryptoMaterial()

        //
        logger.info(s"[ $organizationFullName ] - Creating genesis ...")
        val genesisDefinition = newGenesisDefinition("/opt/profile", config)
        val genesis = FabricBlock.create(genesisDefinition)
        Util.storeToFile("/opt/profile/artifacts/genesis.block", genesis)

        //
        logger.info(s"[ $organizationFullName ] - Starting ordering nodes ...")
        config.network.orderingNodes.foreach { osnConfig =>
            processManager.startOrderingNode(osnConfig.name, osnConfig.port)
        }
        config.network.orderingNodes.foreach { osnConfig =>
            processManager.osnAwaitJoinedToRaft(osnConfig.name)
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
        logger.info(s"[ $organizationFullName ] - Creating channel ...")
        network.createChannel(ServiceChannelName, FabricChannel.CreateChannel(ServiceChannelName, DefaultConsortiumName, config.organization.name))

        //
        logger.info(s"[ $organizationFullName ] - Adding peers to channel ...")
        config.network.peerNodes.foreach { peerConfig =>
            network.addPeerToChannel(ServiceChannelName, peerConfig.name)
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
        network.installChainCode(ServiceChannelName, ServiceChainCodeName, "1.0.0", chainCodePkg)

        //
        logger.info(s"[ $organizationFullName ] - Instantiating service chain code ...")
        network.instantiateChainCode(
            ServiceChannelName, ServiceChainCodeName,
            "1.0.0", // {chainCodeVersion}.{networkVersion}
            arguments = Array(
                Util.codec.toJson(
                    Organization(
                        mspId = config.organization.name,
                        name = config.organization.name,
                        memberNumber = 1
                    )
                ),
                Util.codec.toJson(
                    ServiceVersion(
                        chainCodeVersion = "1.0",
                        networkVersion = "0"
                    )
                )
            )
        )

        //
        logger.info(s"[ $organizationFullName ] - Bootstrap done.")
        network
    }

    //=========================================================================
    def newGenesisDefinition(profilePath: String, config: ServiceConfig): ChannelDefinition = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        val orderingOrganizationPath = s"$profilePath/crypto/ordererOrganizations/$organizationFullName"
        val executionOrganizationPath = s"$profilePath/crypto/peerOrganizations/$organizationFullName"

        val executionOrgMspId = config.organization.name
        val ExecutionOrg =
            OrganizationDefinition(
                mspId = executionOrgMspId,
                caCerts = Seq(Util.readAsByteString(s"$executionOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem")),
                tlsCACerts = Seq(Util.readAsByteString(s"$executionOrganizationPath/msp/tlscacerts/tlsca.$organizationFullName-cert.pem")),
                adminCerts = Seq(Util.readAsByteString(s"$executionOrganizationPath/msp/admincerts/Admin@$organizationFullName-cert.pem")),
                nodesVerification = Option(
                    NodeVerificationConfig(
                        peerOUValue = "peer",
                        peerOUCerts = Util.readAsByteString(s"$executionOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem"),

                        clientOUValue = "client",
                        clientOUCerts = Util.readAsByteString(s"$executionOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem")
                    )
                ),
                policies = PoliciesDefinition(
                    admins =
                      SignedByOneOf(
                          MemberClassifier(executionOrgMspId, MSPRole.MSPRoleType.ADMIN)
                      ),
                    writers =
                      SignedByOneOf(
                          MemberClassifier(executionOrgMspId, MSPRole.MSPRoleType.ADMIN),
                          MemberClassifier(executionOrgMspId, MSPRole.MSPRoleType.CLIENT)
                      ),
                    readers =
                      SignedByOneOf(
                          MemberClassifier(executionOrgMspId, MSPRole.MSPRoleType.ADMIN),
                          MemberClassifier(executionOrgMspId, MSPRole.MSPRoleType.PEER),
                          MemberClassifier(executionOrgMspId, MSPRole.MSPRoleType.CLIENT)
                      )
                )
            )

        val orderingMspId = s"osn-${config.organization.name}"
        val OrderingOrg =
            OrganizationDefinition(
                mspId = orderingMspId,
                caCerts = Seq(Util.readAsByteString(s"$orderingOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem")),
                tlsCACerts = Seq(Util.readAsByteString(s"$orderingOrganizationPath/msp/tlscacerts/tlsca.$organizationFullName-cert.pem")),
                adminCerts = Seq(Util.readAsByteString(s"$orderingOrganizationPath/msp/admincerts/Admin@$organizationFullName-cert.pem")),
                policies = PoliciesDefinition(
                    admins = SignedByOneOf(MemberClassifier(orderingMspId, MSPRole.MSPRoleType.ADMIN)),
                    writers = SignedByOneOf(MemberClassifier(orderingMspId, MSPRole.MSPRoleType.MEMBER)),
                    readers = SignedByOneOf(MemberClassifier(orderingMspId, MSPRole.MSPRoleType.MEMBER))
                )
            )

        ChannelDefinition(
            channelName = SystemChannelName,
            capabilities = Set(CapabilityValue.V1_3),
            ordering =
              Option(
                  OrderingServiceDefinition(
                      maxMessageCount = 150,
                      absoluteMaxBytes = 99 * 1024 * 1024,
                      preferredMaxBytes = 512 * 1024,
                      batchTimeOut = "1s",
                      capabilities = Set(CapabilityValue.V1_1),
                      orderingNodes =
                        config.network.orderingNodes.map { osnConfig =>
                            OrderingNodeDefinition(
                                host = s"${osnConfig.name}.$organizationFullName",
                                port = osnConfig.port,
                                clientTlsCert = Util.readAsByteString(s"$orderingOrganizationPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt"),
                                serverTlsCert = Util.readAsByteString(s"$orderingOrganizationPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt")
                            )
                        },
                      organizations = Seq(OrderingOrg)
                  )
              ),
            application =
              Option(
                  ApplicationDefinition(
                      capabilities = Set(CapabilityValue.V1_3),
                      organizations = Seq(OrderingOrg)
                  )
              ),
            consortiumDetails =
              ConsortiumsDefinition(
                  Seq(
                      ConsortiumDefinition(
                          name = DefaultConsortiumName,
                          organizations = Seq(ExecutionOrg)
                      )
                  )
              )
        )
    }

}
