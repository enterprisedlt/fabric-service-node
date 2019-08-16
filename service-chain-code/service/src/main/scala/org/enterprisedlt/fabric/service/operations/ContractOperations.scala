package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.ContractResponseConversions._
import org.enterprisedlt.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.contract.{ContractContext, ContractResponse, Success}
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{CollectionsHelper, Contract, ContractConfirmation, Organization}


/**
 * @author Andrew Pudovikov
 */
trait ContractOperations {
    self: Main.type =>

    @ContractOperation
    def createContract(context: ContractContext, contract: Contract): ContractResponse =
        getOwnOrganization(context)
          .flatMap { founderOrg =>
              for {
                  _ <- Option(contract.name).filter(_.nonEmpty).toRight("Contract name must be non empty")
                  _ <- Option(contract.chainCodeName).filter(_.nonEmpty).toRight("Chaincode name must be non empty")
                  _ <- Option(contract.chainCodeVersion).filter(_.nonEmpty).toRight("Chaincode version must be non empty")
                  participantsList <- Option(contract.participants).filter(_.nonEmpty).toRight("Participants list must be non empty")
              } yield {
                  participantsList
                    .filter(e => e != founderOrg.name)
                    .foreach { orgFromList =>
                        context.store.get[Organization](orgFromList)
                          .map { participant =>
                              logger.debug(s"putting in to coll ${CollectionsHelper.collectionNameFor(founderOrg, participant)} ${contract}")
                              context.privateStore(
                                  CollectionsHelper.collectionNameFor(founderOrg, participant))
                                .put(contract.name, contract.copy(founder = founderOrg.name,
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

    @ContractOperation
    def getContract(context: ContractContext, name: String, founder: String): ContractResponse =
        getOwnOrganization(context)
          .flatMap { org =>
              for {
                  contractFounderOrg <- context.store.get[Organization](founder).toRight(s"Contract founder organization $founder isn't registered")
                  contractNameValue <- Option(name).filter(_.nonEmpty).toRight("Contract name must be non empty")
                  r <- context.privateStore(
                      CollectionsHelper.collectionNameFor(org, contractFounderOrg))
                    .get[Contract](contractNameValue)
                    .toRight(s"No contract with name $contractNameValue from ${contractFounderOrg.name} org")
              } yield r
          }

    @ContractOperation
    def delContract(context: ContractContext, name: String, founder: String): ContractResponse =
        getOwnOrganization(context)
          .flatMap { org =>
              for {
                  contractFounderOrg <- context.store.get[Organization](founder).toRight(s"Contract founder organization $founder isn't registered")
                  contractNameValue <- Option(name).filter(_.nonEmpty).toRight("Contract name must be non empty")
              } yield
                  context
                    .privateStore(
                        CollectionsHelper.collectionNameFor(org, contractFounderOrg))
                    .del[Contract](contractNameValue)
          }

    @ContractOperation
    def sendContractConfirmation(context: ContractContext, name: String, founder: String): ContractResponse =
        getOwnOrganization(context)
          .flatMap { org =>
              for {
                  _ <- Option(name).filter(_.nonEmpty).toRight("Contract name must be non empty")
                  _ <- Option(founder).filter(_.nonEmpty).toRight("Contract founder org must be non empty")
              } yield {
                  context.store.get[Organization](founder)
                    .map { founderOrg =>
                        logger.debug(s"putting in to coll ${CollectionsHelper.collectionNameFor(org, founderOrg)} ${name}")
                        context.privateStore(
                            CollectionsHelper.collectionNameFor(org, founderOrg))
                          .put(name, ContractConfirmation(name, org.name))
                    }
              }
          }

    @ContractOperation
    def listContractConfirmations(context: ContractContext): ContractResponse =
        Success(
            CollectionsHelper
              .collectionsFromOrganizations(
                  context.store
                    .list[Organization]
                    .map(_.value.mspId))
              .filter(e => e.endsWith(s"-${context.clientIdentity.mspId}") || e.startsWith(s"${context.clientIdentity.mspId}-"))
              .map(context.privateStore)
              .flatMap { store =>
                  store.list[ContractConfirmation].map(_.value)
              }.toArray
        )
}
