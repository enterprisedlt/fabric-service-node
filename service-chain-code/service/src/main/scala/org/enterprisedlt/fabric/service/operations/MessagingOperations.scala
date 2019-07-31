package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.{ContractContext, ContractResponse}
import org.enterprisedlt.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{CollectionsHelper, Message, Organization}
import org.enterprisedlt.fabric.contract.ContractResponseConversions._


/**
  * @author Andrew Pudovikov
  */
trait MessagingOperations {
    self: Main.type =>

    @ContractOperation
    def putMessage(context: ContractContext, message: Message): ContractResponse = {
        val senderOrg = context.clientIdentity.mspId
        for {
            messageToValue <- Option(message.to).filter(_.nonEmpty).toRight("Message recipient must be non empty")
            messageBodyValue <- Option(message.body).filter(_.nonEmpty).toRight("Message body must be non empty")
            senderOrgValue <- context.store.get[Organization](senderOrg).toRight(s"Sender organization ${message.from} isn't registered")
            recipientOrgValue <- context.store.get[Organization](message.to).toRight(s"Recipient organization ${message.to} isn't registered")
            sharedCollectionNameValue <- Option(CollectionsHelper.collectionNameFor(senderOrgValue, recipientOrgValue))
              .filter(_.nonEmpty).toRight(s"There is no shared collection for org ${message.from} and ${message.to}")
        } yield {
            logger.debug(s"Creating message record in shared collection $sharedCollectionNameValue")
            val messageKey = context.transaction.id
            val messagePayload = message.copy(from = context.clientIdentity.mspId)
            context.privateStore(sharedCollectionNameValue).put[Message](messageKey, messagePayload)
            s"Stored"
        }
    }

    @ContractOperation
    def listMessages(context: ContractContext): ContractResponse = {
        val queryingOrg = context.clientIdentity.mspId
        for {
            orgsValue <- Option(context.store.list[Organization].filter(e => e.value.name != queryingOrg).map(_.value.mspId)).toRight(s"There isn't any other org")
            sharedCollectionNamesValue <- Option(CollectionsHelper.collectionsFromOrganizations(orgsValue)).filter(_.nonEmpty).toRight(s"There aren't any shared collection for org $queryingOrg ")
        } yield {
            logger.debug(s"Getting all messages from $sharedCollectionNamesValue")
            sharedCollectionNamesValue
              .map(context.privateStore)
              .flatMap { store =>
                  store.list[Message]
                    .map(_.value)
              }.toArray
        }
    }

    @ContractOperation
    def getMessage(context: ContractContext, messageKey: String, sender: String): ContractResponse = {
        val queryingOrg = context.clientIdentity.mspId
        for {
            queryingOrgValue <- context.store.get[Organization](queryingOrg).toRight(s"Querying organization $queryingOrg isn't registered")
            senderOrgValue <- context.store.get[Organization](sender).toRight(s"Sender organization $sender isn't registered")
            sharedCollectionNamesValue <- Option(CollectionsHelper.collectionNameFor(queryingOrgValue, senderOrgValue)).filter(_.nonEmpty).toRight(s"There aren't any shared collection with sender $sender and recipient $queryingOrg")
            messageRecordValue <- Option(context.privateStore(sharedCollectionNamesValue).get[Message](messageKey)).filter(_.nonEmpty).toRight(s"There aren't any message with key $messageKey")
        } yield {
            messageRecordValue
        }
    }

    @ContractOperation
    def delMessage(context: ContractContext, messageKey: String, sender: String): ContractResponse = {
        val queryingOrg = context.clientIdentity.mspId
        for {
            queryingOrgValue <- context.store.get[Organization](queryingOrg).toRight(s"Querying organization $queryingOrg isn't registered")
            senderOrgValue <- context.store.get[Organization](sender).toRight(s"Sender organization $sender isn't registered")
            sharedCollectionNamesValue <- Option(CollectionsHelper.collectionNameFor(queryingOrgValue, senderOrgValue)).filter(_.nonEmpty).toRight(s"There aren't any shared collection with sender $sender and recipient $queryingOrg")
            messageRecordValue <- Option(context.privateStore(sharedCollectionNamesValue).get[Message](messageKey)).filter(_.nonEmpty).toRight(s"There aren't any message with key $messageKey")
        } yield {
            context.privateStore(sharedCollectionNamesValue).del[Message](messageKey)
        }
    }
}