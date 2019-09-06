package org.enterprisedlt.fabric.service.node.auth

import javax.security.auth.Subject
import javax.servlet.{ServletRequest, ServletResponse}
import org.eclipse.jetty.security.{Authenticator, ServerAuthException, UserAuthentication}
import org.eclipse.jetty.server.Authentication
import org.enterprisedlt.fabric.service.node.util.Util
import org.hyperledger.fabric.sdk.User

/**
  * @author Alexey Polubelov
  */
class FabricAuthenticator(cryptography: CryptoManager) extends Authenticator {

    override def setConfiguration(configuration: Authenticator.AuthConfiguration): Unit = {}

    override def getAuthMethod: String = FabricAuthenticator.FabricAuthenticatorMethodName

    override def prepareRequest(request: ServletRequest): Unit = {}

    override def validateRequest(
        request: ServletRequest, response: ServletResponse, mandatory: Boolean
    ): Authentication = {
        Util.getUserCertificate(request)
          .toRight("User certificate is missing")
          .flatMap { certificate =>
              cryptography.findUser(certificate).map { user =>
                  val principal = certificate.getSubjectX500Principal
                  val subject = new Subject()
                  subject.getPrincipals.add(principal)
                  val (fabricUser, roles) = resolve(user)
                  FabricAuthenticator.setFabricUser(request, fabricUser)
                  val identity = new FabricUserIdentity(subject, principal, roles)
                  new UserAuthentication(FabricAuthenticator.FabricAuthenticatorMethodName, identity)
              }
          } match {
            case Right(r) => r
            case Left(error) => throw new ServerAuthException(error)
        }
    }

    override def secureResponse(
        request: ServletRequest, response: ServletResponse,
        mandatory: Boolean, validatedUser: Authentication.User
    ): Boolean = true

    private def resolve(user: UserAccount): (User, Array[String]) = {
        user.accountType match {
            case AccountType.Fabric =>
                user.name match {
                    case "admin" => (user, Array(Role.Admin, Role.User))
                    case _ => (user, Array(Role.User))
                }

            case AccountType.Service =>
                (cryptography.loadDefaultAdmin, Array(Role.JoinToken))
        }
    }

}

object FabricAuthenticator {
    val FabricAuthenticatorMethodName = "FABRIC-AUTH"
    val FabricUserRequestParameter = "org.enterprisedlt.fabric.service.node.auth.FabricUser"

    def setFabricUser(request: ServletRequest, user: User): Unit = {
        request.setAttribute(FabricUserRequestParameter, user)
    }

    def getFabricUser(request: ServletRequest): Option[User] =
        request.getAttribute(FabricUserRequestParameter) match {
            case null => None
            case x: User => Option(x)
        }

}
