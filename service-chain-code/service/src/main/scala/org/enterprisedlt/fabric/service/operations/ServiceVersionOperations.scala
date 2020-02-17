package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{Constant, ServiceVersion}
import org.enterprisedlt.spec.{ContractOperation, ContractResult, _}

import scala.util.Try

/**
 * @author Alexey Polubelov
 */
trait ServiceVersionOperations {
    self: Main.type =>

    @ContractOperation(OperationType.Invoke)
    def updateServiceVersion(version: ServiceVersion): ContractResult[Unit] = Try {
        OperationContext.store.put[ServiceVersion](Constant.ServiceVersionKey, version)
    }


    @ContractOperation(OperationType.Query)
    def getServiceVersion: ContractResult[ServiceVersion] =
        OperationContext.store
          .get[ServiceVersion](Constant.ServiceVersionKey)
          .toRight(s"There is no chaincode entity")

}
