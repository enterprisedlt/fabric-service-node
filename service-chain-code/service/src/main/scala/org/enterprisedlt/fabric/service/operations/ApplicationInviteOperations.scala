package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{ApplicationInvite, CollectionsHelper, Organization}
import org.enterprisedlt.spec.{ContractOperation, ContractResult, _}

/**
 * @author Maxim Fedin
 */
trait ApplicationInviteOperations {
    self: Main.type =>


    @ContractOperation(OperationType.Invoke)
    def createApplicationInvite(application: ApplicationInvite): ContractResult[Unit] =
        getOwnOrganization
          .flatMap { founder =>
              for {
                  _ <- Option(application.name).filter(_.nonEmpty).toRight("Contract name must be non empty")
                  _ <- Option(application.applicationType).filter(_.nonEmpty).toRight("ContractType must be non empty")
                  _ <- Option(application.version).filter(_.nonEmpty).toRight("Contract version must be non empty")
                  participants <- Option(application.participants).filter(_.nonEmpty).toRight("Participants list must be non empty")
              } yield {
                  participants
                    .filter(_ != founder.mspId)
                    .foreach { participantId =>
                        OperationContext.store.get[Organization](participantId)
                          .map { participant =>
                              OperationContext
                                .privateStore(CollectionsHelper.collectionNameFor(founder, participant))
                                .put(
                                    application.name,
                                    application.copy(
                                        founder = founder.mspId,
                                        timestamp = OperationContext.transaction.timestamp.getEpochSecond
                                    )
                                )
                          }
                    }
              }
          }

    @ContractOperation(OperationType.Query)
    def listApplicationInvites: ContractResult[Array[ApplicationInvite]] =
        this.listCollections.map { collections =>
            collections.filter(e => e.endsWith(s"-${OperationContext.clientIdentity.mspId}") || e.startsWith(s"${OperationContext.clientIdentity.mspId}-"))
              .map(OperationContext.privateStore)
              .flatMap { store =>
                  store.list[ApplicationInvite].map(_.value)
              }
        }

    @ContractOperation(OperationType.Query)
    def getApplicationInvite(name: String, founder: String): ContractResult[ApplicationInvite] =
        getOwnOrganization
          .flatMap { org =>
              for {
                  applicationFounderOrg <- OperationContext.store.get[Organization](founder).toRight(s"Application founder organization $founder isn't registered")
                  applicationNameValue <- Option(name).filter(_.nonEmpty).toRight("Application name must be non empty")
                  r <- OperationContext.privateStore(
                      CollectionsHelper.collectionNameFor(org, applicationFounderOrg))
                    .get[ApplicationInvite](applicationNameValue)
                    .toRight(s"No application with name $applicationNameValue from ${applicationFounderOrg.mspId} org")
              } yield r
          }

    @ContractOperation(OperationType.Invoke)
    def delApplicationInvite(name: String, founder: String): ContractResult[Unit] =
        getOwnOrganization
          .flatMap { org =>
              for {
                  applicationFounderOrg <- OperationContext.store.get[Organization](founder).toRight(s"Application founder organization $founder isn't registered")
                  applicationNameValue <- Option(name).filter(_.nonEmpty).toRight("Application name must be non empty")
              } yield
                  OperationContext
                    .privateStore(
                        CollectionsHelper.collectionNameFor(org, applicationFounderOrg))
                    .del[ApplicationInvite](applicationNameValue)
          }

}
