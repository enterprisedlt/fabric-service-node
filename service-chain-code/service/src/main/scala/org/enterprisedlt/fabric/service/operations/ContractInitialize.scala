package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{Organization, ServiceVersion}
import org.enterprisedlt.spec.{ContractInit, ContractResult}

/**
 * @author Andrew Pudovikov
 */
trait ContractInitialize {
    self: Main.type =>

    @ContractInit
    def init(organization: Organization, version: ServiceVersion): ContractResult[Unit] = {
        putOrganization(organization)
        updateServiceVersion(version)
    }
}
