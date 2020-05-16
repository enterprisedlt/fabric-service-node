package org.enterprisedlt.fabric.service.node.endorsement

import org.enterprisedlt.fabric.service.node.model._
import org.scalatest._
import org.scalatest.matchers.should.Matchers._

/**
 * @author Andrew Pudovikov
 */
class ParserTest extends FunSuite {

    private val originalPartiesDomain: Array[ContractParticipant] =
        Array(
            ContractParticipant("org1", "role1"),
            ContractParticipant("org2", "role2"),
            ContractParticipant("org3", "role1"),
            ContractParticipant("org4", "role3"),
            ContractParticipant("org5", "role1"),
            ContractParticipant("org6", "role3"),
            ContractParticipant("org7", "role4")
        )

    private val participantsRoleMap = originalPartiesDomain.foldLeft(Map.empty[String, List[String]]) { case (r, c) =>
        r + (c.role -> (r.getOrElse(c.role, List.empty) :+ c.mspId))
    }


    test("parser should give an exception for given faulty expression") {
        val missedRole = "errorRole"
        val errorMajorityExpressionString = s"majority_of('$missedRole')"
        val errorMajorityASTExpression = Parser.parse(errorMajorityExpressionString)
        errorMajorityASTExpression.right.get shouldBe a[MajorityOf]
        an[NoSuchElementException] should be thrownBy {
            EndorsementPolicyCompiler.expandRoles(errorMajorityASTExpression.right.get, participantsRoleMap)
        }
    }

    test("should parse strings to ASTExpression (multi expressions)") {

        val majorityExpression = "majority_of('role1','role2','role3','role4')"
        val allExpression = "all_of('role1','role2','role3','role4')"
        val baExpression = "ba_of('role1','role2','role3','role4')"
        val anySimpleExpression = "any_of('role1','role2','role3','role4')"
        val mixedExpression = "all_of(any_of('role1','role2'),any_of('role3','role4'))"

        val majorityExpressionExpanded = Parser.parse(majorityExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val allExpressionExpanded = Parser.parse(allExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val baExpressionExpanded = Parser.parse(baExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val anySimpleExpressionExpanded = Parser.parse(anySimpleExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val mixedExpressionExpanded = Parser.parse(mixedExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))

        majorityExpressionExpanded should be('right)
        allExpressionExpanded should be('right)
        baExpressionExpanded should be('right)
        anySimpleExpressionExpanded should be('right)
        mixedExpressionExpanded should be('right)

        val majorityExpressionExpandedToMatch = MajorityOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val allExpressionExpandedToMatch = AllOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val baExpressionExpandedToMatch = BAOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val anySimpleExpressionExpandedToMatch = AnyOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val mixedExpressionExpandedToMatch = AllOf(List(AnyOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"))), AnyOf(List(Id("org4"), Id("org6"), Id("org7")))))

        majorityExpressionExpanded.right.get should be(majorityExpressionExpandedToMatch)
        allExpressionExpanded.right.get should be(allExpressionExpandedToMatch)
        baExpressionExpanded.right.get should be(baExpressionExpandedToMatch)
        anySimpleExpressionExpanded.right.get should be(anySimpleExpressionExpandedToMatch)
        mixedExpressionExpanded.right.get should be(mixedExpressionExpandedToMatch)
    }

}
