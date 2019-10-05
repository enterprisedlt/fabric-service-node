package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Maxim Fedin
  */
case class BootstrapOptions(
    block: BlockConfig,
    raft: RaftConfig,
    network: NetworkConfig,
    certificateDuration: String
)
