package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Maxim Fedin
  */
case class BlockConfig(
    tickInterval: Option[String],
    electionTick: Option[Int],
    heartbeatTick: Option[Int],
    maxInflightBlocks: Option[Int],
    snapshotIntervalSize: Option[Int]
)
