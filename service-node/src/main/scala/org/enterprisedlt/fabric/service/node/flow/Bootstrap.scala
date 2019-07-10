package org.enterprisedlt.fabric.service.node.flow

import java.io.{File, FileInputStream, FileOutputStream}

import com.google.protobuf.ByteString
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.proto.{ApplicationDefinition, CapabilityValue, ChannelDefinition, ConsortiumDefinition, ConsortiumsDefinition, FabricBlock, MemberClassifier, NodeVerificationConfig, OrderingNodeDefinition, OrderingServiceDefinition, OrganizationDefinition, PoliciesDefinition, SignedByOneOf}
import org.enterprisedlt.fabric.service.node.{FabricCryptoManager, FabricNetworkManager, FabricProcessManager, Util}
import org.hyperledger.fabric.protos.common.Common.{Block, Envelope}
import org.hyperledger.fabric.protos.common.MspPrincipal.MSPRole

/**
  * @author Alexey Polubelov
  */
object Bootstrap {

    def bootstrapOrganization(config: ServiceConfig, cryptoManager: FabricCryptoManager, processManager: FabricProcessManager): FabricNetworkManager = {
        //
        cryptoManager.generateCryptoMaterial()

        //
        val genesisDefinition = newGenesisDefinition("/opt/profile", config)
        val genesis = FabricBlock.create(genesisDefinition)
        storeToFile("/opt/profile/artifacts/genesis.block", genesis)

        //
        config.network.orderingNodes.foreach { osnConfig =>
            processManager.startOrderingNode(osnConfig.name, osnConfig.port)
        }
//        config.network.orderingNodes.foreach { osnConfig =>
//            processManager.awaitOrderingJoinedRaft(osnConfig.name)
//        }
//
//        //
//        config.network.peerNodes.foreach { peerConfig =>
//            processManager.startPeerNode(peerConfig.name, peerConfig.port)
//        }
//
//        //
        val orderingAdmin = cryptoManager.loadOrderingAdmin
        val executionAdmin = cryptoManager.loadExecutionAdmin
        val network = new FabricNetworkManager(config, orderingAdmin, executionAdmin)
//        //
//        createChannel(network, "service")
//
//        //
//        config.network.peerNodes.foreach { peerConfig =>
//            network.addPeerToChannel("service", peerConfig.name)
//        }
//
//        //
//        config.network.peerNodes.foreach { peerConfig =>
//            network.addAnchorsToChannel("service", peerConfig.name)
//        }
//
//        //
//        val chainCodePkg = Util.generateTarGzInputStream(new File(s"/opt/chaincode/common"))
//        network.installChainCode("service", "service", "1.0.0", chainCodePkg)
//
//        //
//        network.instantiateChainCode(
//            "service",
//            "service",
//            "1.0.0", // {chainCodeVersion}.{networkVersion}
//            arguments = Array(
//                config.organization.name, // organizationCode
//                config.organization.name, // organizationName
//                "1.0", // chainCodeVersion
//                "0" // networkVersion
//            )
//        )

        //
        network
    }

    //=========================================================================
    private def newGenesisDefinition(profilePath: String, config: ServiceConfig): ChannelDefinition = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        val orderingOrganizationPath = s"$profilePath/crypto/ordererOrganizations/$organizationFullName"
        val executionOrganizationPath = s"$profilePath/crypto/peerOrganizations/$organizationFullName"

        val executionOrgMspId = config.organization.name
        val ExecutionOrg =
            OrganizationDefinition(
                mspId = executionOrgMspId,
                caCerts = Seq(readAsByteString(s"$executionOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem")),
                tlsCACerts = Seq(readAsByteString(s"$executionOrganizationPath/msp/tlscacerts/tlsca.$organizationFullName-cert.pem")),
                adminCerts = Seq(readAsByteString(s"$executionOrganizationPath/msp/admincerts/Admin@$organizationFullName-cert.pem")),
                nodesVerification = Option(
                    NodeVerificationConfig(
                        peerOUValue = "peer",
                        peerOUCerts = readAsByteString(s"$executionOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem"),

                        clientOUValue = "client",
                        clientOUCerts = readAsByteString(s"$executionOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem")
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
                caCerts = Seq(readAsByteString(s"$orderingOrganizationPath/msp/cacerts/ca.$organizationFullName-cert.pem")),
                tlsCACerts = Seq(readAsByteString(s"$orderingOrganizationPath/msp/tlscacerts/tlsca.$organizationFullName-cert.pem")),
                adminCerts = Seq(readAsByteString(s"$orderingOrganizationPath/msp/admincerts/Admin@$organizationFullName-cert.pem")),
                policies = PoliciesDefinition(
                    admins = SignedByOneOf(MemberClassifier(orderingMspId, MSPRole.MSPRoleType.ADMIN)),
                    writers = SignedByOneOf(MemberClassifier(orderingMspId, MSPRole.MSPRoleType.MEMBER)),
                    readers = SignedByOneOf(MemberClassifier(orderingMspId, MSPRole.MSPRoleType.MEMBER))
                )
            )

        ChannelDefinition(
            channelName = "system-channel",
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
                                clientTlsCert = readAsByteString(s"$orderingOrganizationPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt"),
                                serverTlsCert = readAsByteString(s"$orderingOrganizationPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt")
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
                          name = "SampleConsortium",
                          organizations = Seq(ExecutionOrg)
                      )
                  )
              )
        )
    }

    //=========================================================================
    private def createChannel(network: FabricNetworkManager, channelName: String): Unit = {
        val channelTx = Envelope.parseFrom(new FileInputStream(s"/opt/profile/artifacts/$channelName.tx"))
        network.createChannel(channelName, channelTx)
    }

    //=========================================================================
    private def readAsByteString(path: String): ByteString =
        ByteString.readFrom(new FileInputStream(path))

    //=========================================================================
    private def storeToFile(path: String, block: Block): Unit = {
        val parent = new File(path).getParentFile
        if(!parent.exists()){
            parent.mkdirs()
        }
        val out = new FileOutputStream(path)
        try {
            block.writeTo(out)
            out.flush()
        } finally {
            out.close()
        }
    }
}
