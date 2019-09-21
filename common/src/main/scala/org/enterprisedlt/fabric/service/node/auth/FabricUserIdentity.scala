package org.enterprisedlt.fabric.service.node.auth

import java.security.Principal

import javax.security.auth.Subject
import org.eclipse.jetty.server.UserIdentity

/**
  * @author Alexey Polubelov
  */
class FabricUserIdentity(
    subject: Subject,
    principal: Principal,
    roles: Array[String]
) extends UserIdentity {

    override def getSubject: Subject = subject

    override def getUserPrincipal: Principal = principal

    override def isUserInRole(role: String, scope: UserIdentity.Scope): Boolean =
        roles.contains(role)
}