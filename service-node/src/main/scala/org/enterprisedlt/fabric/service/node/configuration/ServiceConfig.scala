package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Alexey Polubelov
  */
case class ServiceConfig(
    organization: OrganizationConfig,
    network: NetworkConfig,
    block: BlockConfig,
    raftSettings: RaftSettings
)
