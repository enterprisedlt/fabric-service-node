package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.process.ComponentsDistributor

/**
  * @author Maxim Fedin
  */
class ComponentsDistributorRestEndpoint() extends ComponentsDistributor {

    override def provideComponentTypeDistributive(componentName: String): Either[String, String] = Right("Hello World")

}
