package org.enterprisedlt.fabric.service.node.model

/**
 * @author Andrew Pudovikov
 */
sealed trait Expression

case object Majority extends Expression

case class NOutOf(
    n: Int
) extends Expression

case class NOutOfExtendedExpression(
    n: Int,
    principalsList: Array[String]
) extends Expression

case class OrExp(
    value: Array[Expression]
) extends Expression

case class AndExp(
    value: Array[Expression]
) extends Expression

case class Member(
    role: String
) extends Expression