package org.enterprisedlt.fabric.service.node.model

/**
 * @author Andrew Pudovikov
 */
sealed trait Expression

case class OrExp(
    value: Array[Expression]
) extends Expression

case class AndExp(
    value: Array[Expression]
) extends Expression

case class Member(
    name: String
) extends Expression