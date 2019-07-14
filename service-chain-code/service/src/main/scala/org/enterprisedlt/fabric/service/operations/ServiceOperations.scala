package org.enterprisedlt.fabric.service.operations

import org.enterprisedlt.fabric.contract.annotation.ContractOperation
import org.enterprisedlt.fabric.contract.{ContractContext, ContractResponse, Success}
import org.enterprisedlt.fabric.service.Main
import org.enterprisedlt.fabric.service.model.{CollectionsHelper, Organization, OrganizationsOrdering}

/**
  * @author pandelie
  */
trait ServiceOperations {
    self: Main.type =>

    @ContractOperation
    def listCollections(context: ContractContext): ContractResponse =
        Success(
            CollectionsHelper.collectionsFromOrganizations(
                context.store
                  .list[Organization]
                  .map(_.value)
                  .toSeq
                  .sorted(OrganizationsOrdering)
                  .map(_.mspId)
            ).toArray
        )


    //    @ContractOperation
    //    def putInviteRequests(context: ContractContext, orgs: Array[String], app: String): ContractResponse = {
    //        val initialOrg = context.clientIdentity.mspId
    //        val key = s"${context.clientIdentity.mspId}_$app"
    //        val inviteRequest = InviteRequest(
    //            from = initialOrg,
    //            participants = orgs,
    //            app = app
    //        )
    //        val orgSharedCollections: Seq[String] = findSharedCollections(context, initialOrg)
    //        orgs.foreach(org => {
    //            orgSharedCollections.filter(e => e.endsWith(s"-$org") || e.startsWith(s"$org-"))
    //              .foreach(coll => context.privateStore(coll).put(key, inviteRequest))
    //        })
    //        Success()
    //    }
    //
    //    @ContractOperation
    //    def putInviteResponse(context: ContractContext, inviteRequester: String, app: String): ContractResponse = {
    //        val initialOrg = context.clientIdentity.mspId
    //        val key = s"${context.clientIdentity.mspId}_$app"
    //        val inviteResponse = InviteResponse(
    //            responder = initialOrg,
    //            inviteRequester = inviteRequester,
    //            app = app
    //        )
    //        getSharedCollection(context, initialOrg, inviteRequester) match {
    //            case Right(collection) =>
    //                context.privateStore(collection).del[InviteRequest](key)
    //            case Left(error) => Error(error)
    //        }
    //        Success()
    //    }
    //
    //    @ContractOperation
    //    def publishApp(context: ContractContext, appName: String): ContractResponse = {
    //        val initialOrg = context.clientIdentity.mspId
    //        if (!appName.isEmpty) {
    //            context.store.get[App](appName) match {
    //                case Some(v) => Error(s"Application $appName already published")
    //                case None => context.store.put[App](appName, App(appName))
    //                    Success()
    //            }
    //        }
    //        else Error("Some mistake in app name")
    //    }
    //
    //    @ContractOperation
    //    def checkContractResponses(context: ContractContext): ContractResponse = {
    //        val result = List[ResponseCheck]()
    //        val initialOrg = context.clientIdentity.mspId
    //        findSharedCollections(context, initialOrg)
    //          .flatMap(c => context.privateStore(c).list[InviteRequest])
    //          .groupBy(_.key)
    //          .foreach { case (key, invites) =>
    //              val participantsQuantity = invites.head.value.participants.length //required number of responses per $key application
    //          var responsesRcvCounter = 0 //counter for checking invite responses
    //              invites.head.value.participants
    //                .foreach { orgFromInvite => {
    //                    getSharedCollection(context, initialOrg, orgFromInvite) match {
    //                        case Right(aCollection) =>
    //                            if (context.privateStore(aCollection).get[InviteResponse](key).isDefined) responsesRcvCounter += 1
    //                        case Left(error) => Error(error)
    //                    }
    //                }
    //                }
    //              if (participantsQuantity == responsesRcvCounter) result :+ ResponseCheck(key, invites.head.value.participants)
    //          }
    //        Success(result)
    //    }
    //
    //
    //    @ContractOperation
    //    def putInviteConfirmation(context: ContractContext, orgs: Array[String], app: String, link: String): ContractResponse = {
    //        val initialOrg = context.clientIdentity.mspId
    //        val key = s"${context.clientIdentity.mspId}_$app" //_${context.transaction.timestamp.toEpochMilli.toString}"
    //        val contrConfirm = ContractConfirm(
    //            from = initialOrg,
    //            participants = orgs,
    //            app,
    //            link
    //        )
    //        val orgSharedCollections: Seq[String] = findSharedCollections(context, initialOrg)
    //        orgs.foreach(org => {
    //            orgSharedCollections.filter(e => e.endsWith(s"-$org") || e.startsWith(s"$org-"))
    //              .foreach(coll => context.privateStore(coll).put(key, contrConfirm))
    //        })
    //        Success()
    //    }
    //
    //
    //    @ContractOperation
    //    def listTasks(context: ContractContext): ContractResponse = {
    //        val org = context.clientIdentity.mspId
    //        val orgSharedCollections: Seq[String] = findSharedCollections(context, org)
    //        Success(
    //            orgSharedCollections.flatMap {
    //                eachCollection => {
    //                    val invtRequests = context.privateStore(eachCollection).list[InviteRequest]
    //                    val responsesRequests = context.privateStore(eachCollection).list[InviteResponse]
    //                    val confimations = context.privateStore(eachCollection).list[ContractConfirm]
    //                    invtRequests ++ responsesRequests ++ confimations
    //                }
    //            }
    //        )
    //    }

}