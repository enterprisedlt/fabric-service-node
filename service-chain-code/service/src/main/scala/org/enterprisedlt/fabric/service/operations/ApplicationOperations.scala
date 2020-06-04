package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.Application
import org.enterprisedlt.spec.{ContractOperation, ContractResult, _}

import scala.util.Try

/**
 * @author Maxim Fedin
 */
trait ApplicationOperations {
    self: Main.type =>

    @ContractOperation(OperationType.Invoke)
    def publishApplication(application: Application): ContractResult[Unit] = {
        for {
            _ <- Option(application.name).filter(_.nonEmpty).toRight("Application name must be non empty")
        } yield {
            OperationContext.store.put[Application](application.name, application)
        }
    }


    @ContractOperation(OperationType.Query)
    def listApplications: ContractResult[Array[Application]] = Try {
        OperationContext.store.list[Application].map(_.value).toArray
    }


    @ContractOperation(OperationType.Query)
    def getApplication(name: String): ContractResult[Option[Application]] = {
        for {
            _ <- Option(name).filter(_.nonEmpty).toRight("Application name must be non empty")
            name <- Try(OperationContext.store.get[Application](name)).toEither.left.map(_.getMessage)
        } yield name
    }

}
