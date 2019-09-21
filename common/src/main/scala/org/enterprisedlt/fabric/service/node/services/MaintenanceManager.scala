package org.enterprisedlt.fabric.service.node.services

import org.enterprisedlt.fabric.service.node.configuration.BootstrapOptions
import org.enterprisedlt.fabric.service.node.model.{Invite, JoinRequest, JoinResponse}
import org.enterprisedlt.fabric.service.node.rest.{Get, Post}

/**
  * @author Maxim Fedin
  */
trait MaintenanceManager {

    @Post("/bootstrap")
    def bootstrap(bootstrapOptions: BootstrapOptions): Either[String, Unit]

    @Get("/create-invite")
    def createInvite: Either[String, Invite]

    @Post("/request-join")
    def requestJoin(invite: Invite): Either[String, Unit] // TODO


    @Post("/join-org-to-network")
    def joinOrgToNetwork(joinRequest: JoinRequest): Either[String, JoinResponse] // TODO

}
