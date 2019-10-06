package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Alexey Polubelov
  */
case class NetworkConfig(
    orderingNodes: Array[OSNConfig],
    peerNodes: Array[PeerConfig]
)
