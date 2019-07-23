package org.enterprisedlt.fabric.service.node.flow

import org.enterprisedlt.fabric.service.node.Util
import org.enterprisedlt.fabric.service.node.configuration.ServiceConfig
import org.enterprisedlt.fabric.service.node.flow.Constant.{DefaultConsortiumName, SystemChannelName}
import org.enterprisedlt.fabric.service.node.proto._
import org.hyperledger.fabric.protos.common.MspPrincipal.MSPRole

/**
  * @author Alexey Polubelov
  */
object Genesis {

    def newDefinition(profilePath: String, config: ServiceConfig): ChannelDefinition = {
        val organizationFullName = s"${config.organization.name}.${config.organization.domain}"
        val cryptoMaterialPath = s"$profilePath/crypto"

        val orgMspId = config.organization.name
        val organizationDefinition =
            OrganizationDefinition(
                mspId = orgMspId,
                caCerts = Seq(Util.readAsByteString(s"$cryptoMaterialPath/msp/cacerts/ca.$organizationFullName-cert.pem")),
                tlsCACerts = Seq(Util.readAsByteString(s"$cryptoMaterialPath/msp/tlscacerts/tlsca.$organizationFullName-cert.pem")),
                adminCerts = Seq(Util.readAsByteString(s"$cryptoMaterialPath/msp/admincerts/Admin@$organizationFullName-cert.pem")),
                policies = PoliciesDefinition(
                    admins = SignedByOneOf(MemberClassifier(orgMspId, MSPRole.MSPRoleType.ADMIN)),
                    writers = SignedByOneOf(MemberClassifier(orgMspId, MSPRole.MSPRoleType.MEMBER)),
                    readers = SignedByOneOf(MemberClassifier(orgMspId, MSPRole.MSPRoleType.MEMBER))
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
                                clientTlsCert = Util.readAsByteString(s"$cryptoMaterialPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt"),
                                serverTlsCert = Util.readAsByteString(s"$cryptoMaterialPath/orderers/${osnConfig.name}.$organizationFullName/tls/server.crt")
                            )
                        },
                      organizations = Seq(organizationDefinition)
                  )
              ),
            application =
              Option(
                  ApplicationDefinition(
                      capabilities = Set(CapabilityValue.V1_3),
                      organizations = Seq(organizationDefinition)
                  )
              ),
            consortiumDetails =
              ConsortiumsDefinition(
                  Seq(
                      ConsortiumDefinition(
                          name = DefaultConsortiumName,
                          organizations = Seq(organizationDefinition)
                      )
                  )
              )
        )
    }

}
