package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.service.model.{Constant, Organization, ServiceVersion}
import com.github.apolubelov.fabric.contract.annotation.ContractOperation
import com.github.apolubelov.fabric.contract.{ContractContext, ContractResponse, Error, Success}
import com.github.apolubelov.fabric.contract.ContractResponseConversions._
import org.enterprisedlt.fabric.service.Main

/**
  * @author pandelie
  */
trait OrganizationOperations {
    self: Main.type =>

    @ContractOperation
    def putOrganization(context: ContractContext, organization: Organization): ContractResponse =
        Option(organization.mspId)
          .toRight("Organization code is empty!")
          .map { code =>
              context.store.put[Organization](code, organization)
          }

    @ContractOperation
    def listOrganizations(context: ContractContext): ContractResponse =
        Success(
            context.store
              .list[Organization]
              .map(_.value)
              .toArray
        )

    @ContractOperation
    def getOrganization(context: ContractContext, mspId: String): ContractResponse =
        context.store
          .get[Organization](mspId)
          .toRight(s"There is no organization with id: $mspId")


    @ContractOperation
    def deleteOrganisation(context: ContractContext, mspId: String): ContractResponse =
        context.store.get[Organization](mspId)
          .toRight(s"No organization with id $mspId found")
          .map(_ => context.store.del[Organization](mspId))

}
