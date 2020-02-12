package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{Organization, ServiceVersion}
import org.enterprisedlt.spec.ContractInit

/**
  * @author Andrew Pudovikov
  */
trait ContractInitialize {
    self: Main.type =>

    @ContractInit
    def init(organization: Organization, version: ServiceVersion): Unit = {
        putOrganization(organization)
        updateServiceVersion(version)
    }
}
