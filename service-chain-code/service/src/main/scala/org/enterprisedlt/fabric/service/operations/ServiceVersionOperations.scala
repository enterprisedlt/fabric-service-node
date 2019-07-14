package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.{ContractContext, ContractResponse}
import org.enterprisedlt.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.service.model.{Constant, ServiceVersion}
import org.enterprisedlt.fabric.contract.ContractResponseConversions._
import org.enterprisedlt.fabric.service.Main

/**
  * @author Alexey Polubelov
  */
trait ServiceVersionOperations {
    self: Main.type =>

    @ContractOperation
    def updateServiceVersion(context: ContractContext, version: ServiceVersion): Unit =
        context.store.put[ServiceVersion](Constant.ServiceVersionKey, version)


    @ContractOperation
    def getServiceVersion(context: ContractContext): ContractResponse =
        context.store
          .get[ServiceVersion](Constant.ServiceVersionKey)
          .toRight(s"There is no chaincode entity")

}
