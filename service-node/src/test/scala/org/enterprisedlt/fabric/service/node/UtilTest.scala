package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.endorsement._
import org.enterprisedlt.fabric.service.node.model._
import org.scalatest.FunSuite

/**
 * @author Andrew Pudovikov
 */
class UtilTest extends FunSuite {

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


    test("should parse strings to ASTExpressions - multy expression unittest") {

        val majorityExpression = "majority_of('role1','role2','role3','role4')"
        val allExpression = "all_of('role1','role2','role3','role4')"
        val bfaExpression = "bfa_of('role1','role2','role3','role4')"
        val anySimpleExpression = "any_of('role1','role2','role3','role4')"
        val mixedExpression = "all_of(any_of('role1','role2'),any_of('role3','role4'))"


        val majorityExpressionExpanded = Parser.parse(majorityExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val allExpressionExpanded = Parser.parse(allExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val bfaExpressionExpanded = Parser.parse(bfaExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val anySimpleExpressionExpanded = Parser.parse(anySimpleExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))
        val mixedExpressionExpanded = Parser.parse(mixedExpression).map(e => EndorsementPolicyCompiler.expandRoles(e, participantsRoleMap))

        val majorityExpressionExpandedToMatch = MajorityOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val allExpressionExpandedToMatch = AllOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val bfaExpressionExpandedToMatch = BFAOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val anySimpleExpressionExpandedToMatch = AnyOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"), Id("org4"), Id("org6"), Id("org7")))
        val mixedExpressionExpandedToMatch = AllOf(List(AnyOf(List(Id("org1"), Id("org3"), Id("org5"), Id("org2"))), AnyOf(List(Id("org4"), Id("org6"), Id("org7")))))


        assert(majorityExpressionExpanded.right.get == majorityExpressionExpandedToMatch)
        assert(allExpressionExpanded.right.get == allExpressionExpandedToMatch)
        assert(bfaExpressionExpanded.right.get == bfaExpressionExpandedToMatch)
        assert(anySimpleExpressionExpanded.right.get == anySimpleExpressionExpandedToMatch)
        assert(mixedExpressionExpanded.right.get == mixedExpressionExpandedToMatch)
    }


    test("should calculate correct threshold for majority policy") {
        val majorityThresholdFor1 = EndorsementPolicyCompiler.majorityThreshold(1)
        val majorityThresholdFor2 = EndorsementPolicyCompiler.majorityThreshold(2)
        val majorityThresholdFor3 = EndorsementPolicyCompiler.majorityThreshold(3)
        val majorityThresholdFor4 = EndorsementPolicyCompiler.majorityThreshold(4)
        val majorityThresholdFor5 = EndorsementPolicyCompiler.majorityThreshold(5)
        val majorityThresholdFor10 = EndorsementPolicyCompiler.majorityThreshold(10)
        assert(majorityThresholdFor1 == 1)
        assert(majorityThresholdFor2 == 2)
        assert(majorityThresholdFor3 == 2)
        assert(majorityThresholdFor4 == 3)
        assert(majorityThresholdFor5 == 3)
        assert(majorityThresholdFor10 == 6)
    }

    test("should calculate correct threshold for bfa policy") {
        val bftMajorityThresholdFor1 = EndorsementPolicyCompiler.bfaThreshold(1)
        val bftMajorityThresholdFor2 = EndorsementPolicyCompiler.bfaThreshold(2)
        val bftMajorityThresholdFor3 = EndorsementPolicyCompiler.bfaThreshold(3)
        val bftMajorityThresholdFor4 = EndorsementPolicyCompiler.bfaThreshold(4)
        val bftMajorityThresholdFor5 = EndorsementPolicyCompiler.bfaThreshold(5)
        val bftMajorityThresholdFor10 = EndorsementPolicyCompiler.bfaThreshold(10)
        assert(bftMajorityThresholdFor1 == 1)
        assert(bftMajorityThresholdFor2 == 2)
        assert(bftMajorityThresholdFor3 == 2)
        assert(bftMajorityThresholdFor4 == 3)
        assert(bftMajorityThresholdFor5 == 4)
        assert(bftMajorityThresholdFor10 == 7)
    }

}
