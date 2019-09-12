package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.configuration.BootstrapOptions
import org.enterprisedlt.fabric.service.node.rest.Post

/**
  * @author Maxim Fedin
  */
trait MaintenanceManager {

    @Post("/bootstrap")
    def bootstrap(bootstrapOptions: BootstrapOptions): Either[String, Unit]


}
