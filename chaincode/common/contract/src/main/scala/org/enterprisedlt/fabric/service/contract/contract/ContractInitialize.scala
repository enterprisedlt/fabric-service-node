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
    def init(context: ContractContext, code: String, name: String, orgNumber: Long, ccVer: String, networkVer: String): ContractResponse = {
        addOrganisation(context, code, name, orgNumber)
        setCCVersion(context, "service", ccVer, networkVer)
    }
}