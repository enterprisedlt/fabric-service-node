package org.enterprisedlt.fabric.service.node.flow

import org.enterprisedlt.fabric.service.node.Util
import org.enterprisedlt.fabric.service.node.configuration.OrganizationConfig
import org.enterprisedlt.fabric.service.node.flow.Constant.{DefaultConsortiumName, SystemChannelName}
import org.enterprisedlt.fabric.service.node.proto._
import org.enterprisedlt.fabric.service.node.shared.{BlockConfig, BootstrapOptions}
import org.hyperledger.fabric.protos.common.MspPrincipal.MSPRole

/**
  * @author Alexey Polubelov
  */
object Genesis {

    def newDefinition(
       profilePath: String,
       organizationConfig: OrganizationConfig,
       bootstrapOptions: BootstrapOptions
     ): ChannelDefinition = {
        val organizationFullName = s"${organizationConfig.name}.${organizationConfig.domain}"
        val certificatesPath = s"$profilePath/crypto"

        val orgMspId = organizationConfig.name
        val organizationDefinition =
            OrganizationDefinition(
                mspId = orgMspId,
                caCerts = Seq(Util.readAsByteString(s"$certificatesPath/ca/ca.crt")),
                tlsCACerts = Seq(Util.readAsByteString(s"$certificatesPath/tlsca/tlsca.crt")),
                adminCerts = Seq(Util.readAsByteString(s"$certificatesPath/users/admin/admin.crt")),
                policies = PoliciesDefinition(
                    admins = SignedByOneOf(MemberClassifier(orgMspId, MSPRole.MSPRoleType.ADMIN)),
                    writers = SignedByOneOf(MemberClassifier(orgMspId, MSPRole.MSPRoleType.MEMBER)),
                    readers = SignedByOneOf(MemberClassifier(orgMspId, MSPRole.MSPRoleType.MEMBER))
                )
            )
        val blockConfig = Option(bootstrapOptions.block).getOrElse(
            BlockConfig(
                maxMessageCount = 150,
                absoluteMaxBytes = 99 * 1024 * 1024,
                preferredMaxBytes = 512 * 1024,
                batchTimeOut = "1s"
            ))
        ChannelDefinition(
            channelName = SystemChannelName,
            capabilities = Set(CapabilityValue.V1_3),
            ordering = Option(
                OrderingServiceDefinition(
                    maxMessageCount = blockConfig.maxMessageCount,
                    absoluteMaxBytes = blockConfig.absoluteMaxBytes,
                    preferredMaxBytes = blockConfig.preferredMaxBytes,
                    batchTimeOut = blockConfig.batchTimeOut,
                    capabilities = Set(CapabilityValue.V1_1),
                    orderingNodes =
                      bootstrapOptions.network.orderingNodes.map { osnConfig =>
                          OrderingNodeDefinition(
                              host = osnConfig.name,
                              port = osnConfig.port,
                              clientTlsCert = Util.readAsByteString(s"$certificatesPath/orderers/${osnConfig.name}/tls/server.crt"),
                              serverTlsCert = Util.readAsByteString(s"$certificatesPath/orderers/${osnConfig.name}/tls/server.crt")
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
