package org.enterprisedlt.fabric.service.contract.contract

import com.github.apolubelov.fabric.contract.annotation.ContractInit
import com.github.apolubelov.fabric.contract.{ContractContext, ContractResponse}
import org.enterprisedlt.fabric.service.contract.Main

/**
  * @author pandelie
  */
trait ContractInitialize {
    self: Main.type =>

    @ContractInit
    def init(context: ContractContext, organizationCode: String, organizationName: String, chainCodeVersion: String, networkVersion: String): ContractResponse = {
        addOrganisation(context, organizationCode, organizationName)
        setCCVersion(context, chainCodeVersion, networkVersion)
    }
}