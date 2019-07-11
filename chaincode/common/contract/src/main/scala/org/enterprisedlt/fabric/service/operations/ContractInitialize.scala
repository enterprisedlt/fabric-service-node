package org.enterprisedlt.fabric.service.operations

import com.github.apolubelov.fabric.contract.ContractContext
import com.github.apolubelov.fabric.contract.annotation.ContractInit
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{Organization, ServiceVersion}

/**
  * @author pandelie
  */
trait ContractInitialize {
    self: Main.type =>

    @ContractInit
    def init(context: ContractContext, organization: Organization, version: ServiceVersion): Unit = {
        putOrganization(context, organization)
        updateServiceVersion(context, version)
    }
}