package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.model.ComponentsState

/**
  * @author
  */
trait StateManager {

    def marshalNetworkState(state: ComponentsState): Either[String, Unit]


    def unmarshalNetworkState(): Either[String, ComponentsState]


}
