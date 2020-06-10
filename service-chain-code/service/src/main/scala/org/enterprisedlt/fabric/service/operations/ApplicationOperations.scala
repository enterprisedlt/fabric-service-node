package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{Application, CollectionsHelper, Organization}
import org.enterprisedlt.spec.{ContractOperation, ContractResult, _}

/**
 * @author Maxim Fedin
 */
trait ApplicationOperations {
    self: Main.type =>


    @ContractOperation(OperationType.Invoke)
    def createApplication(application: Application): ContractResult[Unit] =
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
    def listApplications: ContractResult[Array[Application]] =
        this.listCollections.map { collections =>
            collections.filter(e => e.endsWith(s"-${OperationContext.clientIdentity.mspId}") || e.startsWith(s"${OperationContext.clientIdentity.mspId}-"))
              .map(OperationContext.privateStore)
              .flatMap { store =>
                  store.list[Application].map(_.value)
              }
        }


}
