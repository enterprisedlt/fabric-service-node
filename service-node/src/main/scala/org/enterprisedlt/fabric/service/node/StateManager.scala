package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.model.ComponentsState

/**
  * @author
  */
trait StateManager {

    def persistNetworkState(state: ComponentsState): Either[String, Unit]

}
