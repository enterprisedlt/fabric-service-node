package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.ApplicationDistributive
import org.enterprisedlt.spec.{ContractOperation, ContractResult, _}

import scala.util.Try

/**
 * @author Maxim Fedin
 */
trait ApplicationDistributivesOperations {
    self: Main.type =>

    @ContractOperation(OperationType.Invoke)
    def publishApplicationDistributive(application: ApplicationDistributive): ContractResult[Unit] = {
        for {
            _ <- Option(application.name).filter(_.nonEmpty).toRight("Application name must be non empty")
        } yield {
            OperationContext.store.put[ApplicationDistributive](application.name, application)
        }
    }


    @ContractOperation(OperationType.Query)
    def listApplicationDistributives: ContractResult[Array[ApplicationDistributive]] = Try {
        OperationContext.store.list[ApplicationDistributive].map(_.value).toArray
    }


    @ContractOperation(OperationType.Query)
    def getApplicationDistributive(name: String): ContractResult[Option[ApplicationDistributive]] = {
        for {
            _ <- Option(name).filter(_.nonEmpty).toRight("Application name must be non empty")
            name <- Try(OperationContext.store.get[ApplicationDistributive](name)).toEither.left.map(_.getMessage)
        } yield name
    }

}
