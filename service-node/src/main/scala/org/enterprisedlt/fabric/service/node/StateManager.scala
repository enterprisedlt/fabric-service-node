package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.model.ServiceNodeState

/**
  * @author
  */
trait StateManager {

    def marshalNetworkState(state: ServiceNodeState): Either[String, Unit]


    def unmarshalNetworkState(): Either[String, ServiceNodeState]

}
