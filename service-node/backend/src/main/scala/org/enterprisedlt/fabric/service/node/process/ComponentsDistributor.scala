package org.enterprisedlt.fabric.service.node.process

import org.enterprisedlt.fabric.service.node.rest.Get

/**
  * @author Maxim Fedin
  */
trait ComponentsDistributor {

    @Get("/service/provide-component-type-distributive")
    def provideComponentTypeDistributive(componentName: String): Either[String, String]

}
