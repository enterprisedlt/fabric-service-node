package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.ContractContext
import org.enterprisedlt.fabric.contract.annotation.ContractInit
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{Organization, ServiceVersion}

/**
  * @author Andrew Pudovikov
  */
trait ContractInitialize {
    self: Main.type =>

    @ContractInit
    def init(context: ContractContext, organization: Organization, version: ServiceVersion): Unit = {
        putOrganization(context, organization)
        updateServiceVersion(context, version)
    }
}