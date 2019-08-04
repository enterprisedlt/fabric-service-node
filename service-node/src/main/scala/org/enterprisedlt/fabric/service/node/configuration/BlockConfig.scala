package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Maxim Fedin
  */
case class BlockConfig(
    maxMessageCount: Int,
    absoluteMaxBytes: Int,
    preferredMaxBytes: Int,
    batchTimeOut: String
)
