package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.OperationContext
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{CollectionsHelper, Message, Organization}
import org.enterprisedlt.spec.{ContractOperation, ContractResult, KeyValue, OperationType}

import scala.util.Try


/**
 * @author Andrew Pudovikov
 */
trait MessagingOperations {
    self: Main.type =>

    @ContractOperation(OperationType.Invoke)
    def putMessage(message: Message): ContractResult[Unit] =
        getOwnOrganization
          .flatMap { senderOrganization =>
              for {
                  _ <- Option(message.to).filter(_.nonEmpty).toRight("Message recipient must be non empty")
                  _ <- Option(message.body).filter(_.nonEmpty).toRight("Message body must be non empty")
                  recipientOrganization <- OperationContext.store.get[Organization](message.to).toRight(s"Recipient organization ${message.to} isn't registered")
              } yield {
                  OperationContext
                    .privateStore(CollectionsHelper.collectionNameFor(senderOrganization, recipientOrganization))
                    .put(OperationContext.transaction.id, message.copy(
                        from = senderOrganization.mspId,
                        timestamp = OperationContext.transaction.timestamp.toEpochMilli))
              }
          }

    @ContractOperation(OperationType.Query)
    def listMessages: ContractResult[Array[KeyValue[Message]]] = Try {
        CollectionsHelper
          .collectionsFromOrganizations(
              OperationContext.store
                .list[Organization]
                .map(_.value.mspId))
          .filter(e => e.endsWith(s"-${OperationContext.clientIdentity.mspId}") || e.startsWith(s"${OperationContext.clientIdentity.mspId}-"))
          .map(OperationContext.privateStore)
          .flatMap { store =>
              store.list[Message]
          }.toArray
    }

    @ContractOperation(OperationType.Query)
    def getMessage(messageKey: String, sender: String): ContractResult[Message] =
        getOwnOrganization
          .flatMap { queryingOrganization =>
              for {
                  senderOrganization <- OperationContext.store.get[Organization](sender).toRight(s"Sender organization $sender isn't registered")
                  message <- OperationContext
                    .privateStore(
                        CollectionsHelper.collectionNameFor(queryingOrganization, senderOrganization)
                    )
                    .get[Message](messageKey)
                    .toRight(s"No message for key $messageKey from $sender ")
              } yield message
          }

    @ContractOperation(OperationType.Invoke)
    def delMessage(messageKey: String, sender: String): ContractResult[Unit] =
        getOwnOrganization
          .flatMap { queryingOrganization =>
              for {
                  _ <- Option(messageKey).filter(_.nonEmpty).toRight("Message key must be non empty")
                  senderOrganization <- OperationContext.store.get[Organization](sender).toRight(s"Sender organization $sender isn't registered")
              } yield {
                  OperationContext
                    .privateStore(
                        CollectionsHelper.collectionNameFor(queryingOrganization, senderOrganization)
                    )
                    .del[Message](messageKey)
              }
          }
}
