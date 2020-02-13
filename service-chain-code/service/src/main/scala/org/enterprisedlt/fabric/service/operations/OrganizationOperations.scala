package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.Organization
import org.enterprisedlt.spec.{ContractOperation, ContractResult}
import org.enterprisedlt.spec._

import scala.util.Try

/**
 * @author Andrew Pudovikov
 */
trait OrganizationOperations {
    self: Main.type =>

    @ContractOperation(OperationType.Invoke)
    def putOrganization(organization: Organization): ContractResult[Unit] =
        Option(organization.mspId)
          .toRight("Organization code is empty!")
          .map { code =>
              OperationContext.store.put[Organization](code, organization)
          }

    @ContractOperation(OperationType.Query)
    def listOrganizations: ContractResult[Array[Organization]] = Try {
        OperationContext.store
          .list[Organization]
          .map(_.value)
          .toArray
    }


    @ContractOperation(OperationType.Query)
    def getOrganization(mspId: String): ContractResult[Organization] =
        OperationContext.store
          .get[Organization](mspId)
          .toRight(s"There is no organization with id: $mspId")


    @ContractOperation(OperationType.Invoke)
    def deleteOrganisation(mspId: String): ContractResult[Unit] =
        OperationContext.store.get[Organization](mspId)
          .toRight(s"No organization with id $mspId found")
          .map(_ => OperationContext.store.del[Organization](mspId))

}
