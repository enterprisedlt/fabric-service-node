package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.ContractResponseConversions._
import org.enterprisedlt.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.contract.{ContractContext, ContractResponse, Success}
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{CollectionsHelper, Message, Organization}


/**
  * @author Andrew Pudovikov
  */
trait MessagingOperations {
    self: Main.type =>

    @ContractOperation
    def putMessage(context: ContractContext, message: Message): ContractResponse = {
        val senderOrg = context.clientIdentity.mspId
        for {
            _ <- Option(message.to).filter(_.nonEmpty).toRight("Message recipient must be non empty")
            _ <- Option(message.body).filter(_.nonEmpty).toRight("Message body must be non empty")
            senderOrganization <- context.store.get[Organization](senderOrg).toRight(s"Sender organization $senderOrg isn't registered")
            recipientOrganization <- context.store.get[Organization](message.to).toRight(s"Recipient organization ${message.to} isn't registered")
        } yield {
            context
              .privateStore(CollectionsHelper.collectionNameFor(senderOrganization, recipientOrganization))
              .put(context.transaction.id, message.copy(
                  from = senderOrganization.mspId,
                  timestamp = context.transaction.timestamp.toEpochMilli))
        }
    }

    @ContractOperation
    def listMessages(context: ContractContext): ContractResponse = {
        Success(
            CollectionsHelper
              .collectionsFromOrganizations(
                  context.store
                    .list[Organization]
                    .map(_.value.mspId))
              .filter(e => e.endsWith(s"-${context.clientIdentity.mspId}") || e.startsWith(s"${context.clientIdentity.mspId}-"))
              .map(context.privateStore)
              .flatMap { store =>
                  store.list[Message]
              }.toArray
        )
    }

    @ContractOperation
    def getMessage(context: ContractContext, messageKey: String, sender: String): ContractResponse = {
        val queryingOrg = context.clientIdentity.mspId
        for {
            queryingOrganization <- context.store.get[Organization](queryingOrg).toRight(s"Organization $queryingOrg isn't registered")
            senderOrganization <- context.store.get[Organization](sender).toRight(s"Sender organization $sender isn't registered")
            message <- context
              .privateStore(
                  CollectionsHelper.collectionNameFor(queryingOrganization, senderOrganization)
              )
              .get[Message](messageKey)
              .toRight(s"No message for key $messageKey from $sender")
        } yield message
    }

    @ContractOperation
    def delMessage(context: ContractContext, messageKey: String, sender: String): ContractResponse = {
        val queryingOrg = context.clientIdentity.mspId
        for {
            queryingOrganization <- context.store.get[Organization](queryingOrg).toRight(s"Querying organization $queryingOrg isn't registered")
            senderOrganization <- context.store.get[Organization](sender).toRight(s"Sender organization $sender isn't registered")
        } yield
            context
              .privateStore(
                  CollectionsHelper.collectionNameFor(queryingOrganization, senderOrganization)
              )
              .del[Message](messageKey)
    }
}