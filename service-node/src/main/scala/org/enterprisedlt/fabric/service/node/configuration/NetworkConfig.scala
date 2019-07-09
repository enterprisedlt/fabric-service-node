package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Alexey Polubelov
  */
case class NetworkConfig(
    name: String,
    orderingNodes: Array[OSNConfig],
    peerNodes: Array[PeerConfig]
)