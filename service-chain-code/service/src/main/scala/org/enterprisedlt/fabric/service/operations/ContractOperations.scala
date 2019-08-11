package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.ContractResponseConversions._
import org.enterprisedlt.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.contract.{ContractContext, ContractResponse, Success}
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{CollectionsHelper, Contract, Organization}


/**
 * @author Andrew Pudovikov
 */
trait ContractOperations {
    self: Main.type =>

    @ContractOperation
    def createContract(context: ContractContext, contract: Contract): ContractResponse = {
        val founderOrg = context.clientIdentity.mspId
        for {
            _ <- Option(contract.name).filter(_.nonEmpty).toRight("Contract name must be non empty")
            _ <- Option(contract.chainCodeName).filter(_.nonEmpty).toRight("Chaincode name must be non empty")
            _ <- Option(contract.chainCodeVersion).filter(_.nonEmpty).toRight("Chaincode version must be non empty")
            founderOrgValue <- context.store.get[Organization](founderOrg).toRight("Founder org isn't registered")
            participantsList <- Option(contract.participants).filter(_.nonEmpty).toRight("Contract version must be non empty")
        } yield {
            participantsList
              .filter(e => e != founderOrg)
              .foreach { orgFromList =>
                  context.store.get[Organization](orgFromList)
                    .map { participant =>
                        logger.debug(s"putting in to coll ${CollectionsHelper.collectionNameFor(founderOrgValue, participant)} ${contract}")
                        context.privateStore(
                            CollectionsHelper.collectionNameFor(founderOrgValue, participant))
                          .put(contract.name, contract.copy(founder = founderOrg,
                              timestamp = context.transaction.timestamp.getEpochSecond))
                    }
              }
        }
    }

    @ContractOperation
    def listContracts(context: ContractContext): ContractResponse =
        Success(
            CollectionsHelper
              .collectionsFromOrganizations(
                  context.store
                    .list[Organization]
                    .map(_.value.mspId))
              .filter(e => e.endsWith(s"-${context.clientIdentity.mspId}") || e.startsWith(s"${context.clientIdentity.mspId}-"))
              .map(context.privateStore)
              .flatMap { store =>
                  store.list[Contract].map(_.value)
              }.toArray
        )

}

