package org.enterprisedlt.fabric.service.node.identity

import org.hyperledger.fabric.sdk.{Enrollment, User}

/**
  * @author Alexey Polubelov
  */
case class UserAccount(
    name: String,
    role: java.util.Set[String],
    account: String,
    affiliation: String,
    enrollment: Enrollment,
    mspId: String,
    accountType: AccountType
) extends User {

    override def getName: String = name

    override def getRoles: java.util.Set[String] = role

    override def getAccount: String = account

    override def getAffiliation: String = affiliation

    override def getEnrollment: Enrollment = enrollment

    override def getMspId: String = mspId
}

sealed trait AccountType

object AccountType{
    case object Fabric extends AccountType
    case object Service extends AccountType
}
