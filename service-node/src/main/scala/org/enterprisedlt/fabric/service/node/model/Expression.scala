//package org.enterprisedlt.fabric.service.node.model
//
///**
// * @author Andrew Pudovikov
// */
//sealed trait Expression
//
//case object AnyExpression extends Expression
//case object AllExpression extends Expression
//case object MajorityExpression extends Expression
//case object BFTMajorityExpression extends Expression
//
//case class NOutOf(
//    threshold: Int
//) extends Expression
//
//case class NOutOfExtendedExpression(
//    threshold: Int,
//    principalsList: Array[String]
//) extends Expression
//
//case class OrExp(
//    value: Array[Expression]
//) extends Expression
//
//case class AndExp(
//    value: Array[Expression]
//) extends Expression
//
//case class Member(
//    role: String
//) extends Expression