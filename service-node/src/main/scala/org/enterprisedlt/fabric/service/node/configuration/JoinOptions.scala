package org.enterprisedlt.fabric.service.node.configuration

import org.enterprisedlt.fabric.service.node.model.Invite

/**
  * @author Maxim Fedin
  */
case class JoinOptions(
    invite: Invite,
    network: NetworkConfig,
)
