package org.enterprisedlt.fabric.service.node.configuration

/**
  * @author Maxim Fedin
  */
case class RaftSettings (
    tickInterval: String,
    electionTick: Int,
    heartbeatTick: Int,
    maxInflightBlocks: Int,
    snapshotIntervalSize: Int

)
