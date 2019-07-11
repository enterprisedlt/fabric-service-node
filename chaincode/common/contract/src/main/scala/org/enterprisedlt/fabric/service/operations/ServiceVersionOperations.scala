package org.enterprisedlt.fabric.service.operations

import com.github.apolubelov.fabric.contract.{ContractContext, ContractResponse}
import com.github.apolubelov.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.service.model.{Constant, ServiceVersion}
import com.github.apolubelov.fabric.contract.ContractResponseConversions._
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
