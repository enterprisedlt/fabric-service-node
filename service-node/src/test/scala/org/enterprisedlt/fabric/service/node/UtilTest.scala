package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.model.{AndExp, ContractParticipant, MajorityExpression, Member, NOutOfExtendedExpression, OrExp}
import org.scalatest.FunSuite

/**
 * @author Andrew Pudovikov
 */
class UtilTest extends FunSuite {
    private val partiesDomain: Array[ContractParticipant] = Array(
        ContractParticipant("org1", "role1"),
        ContractParticipant("org2", "role2"),
        ContractParticipant("org3", "role1"),
        ContractParticipant("org4", "role3"),
    )


    test("should calculate proper threshold for MAJORITY policy") {
        val majorityThresholdFor1 = Util.moreThenHalfThreshold(1)
        val majorityThresholdFor2 = Util.moreThenHalfThreshold(2)
        val majorityThresholdFor3 = Util.moreThenHalfThreshold(3)
        val majorityThresholdFor4 = Util.moreThenHalfThreshold(4)
        val majorityThresholdFor5 = Util.moreThenHalfThreshold(5)
        val majorityThresholdFor10 = Util.moreThenHalfThreshold(10)
        assert(majorityThresholdFor1 == 1)
        assert(majorityThresholdFor2 == 2)
        assert(majorityThresholdFor3 == 2)
        assert(majorityThresholdFor4 == 3)
        assert(majorityThresholdFor5 == 3)
        assert(majorityThresholdFor10 == 6)
    }

    test("should calculate proper threshold for BFT MAJORITY policy") {
        val bftMajorityThresholdFor1 = Util.bftThreshold(1)
        val bftMajorityThresholdFor2 = Util.bftThreshold(2)
        val bftMajorityThresholdFor3 = Util.bftThreshold(3)
        val bftMajorityThresholdFor4 = Util.bftThreshold(4)
        val bftMajorityThresholdFor5 = Util.bftThreshold(5)
        val bftMajorityThresholdFor10 = Util.bftThreshold(10)
        assert(bftMajorityThresholdFor1 == 1)
        assert(bftMajorityThresholdFor2 == 2)
        assert(bftMajorityThresholdFor3 == 2)
        assert(bftMajorityThresholdFor4 == 3)
        assert(bftMajorityThresholdFor5 == 4)
        assert(bftMajorityThresholdFor10 == 7)
    }


    test("should unfold role to mspId array") {
        val participantsForRole1 = Util.convertRoleToMspId("role1", partiesDomain)
        val participantsForRole2 = Util.convertRoleToMspId("role2", partiesDomain)
        val participantsForRole3 = Util.convertRoleToMspId("role3", partiesDomain)
        assert(participantsForRole1 === Array("org1", "org3"))
        assert(participantsForRole2 === Array("org2"))
        assert(participantsForRole3 === Array("org4"))
    }

    test("should convert expression with roles to expression by mspids") {
        val originalExpression = AndExp(
            Array(
                Member("role1"),
                OrExp(
                    Array(Member("role3"), Member("role2"))
                )
            )
        )
        val expressionToMatch = AndExp(
            Array(
                OrExp(
                    Array(Member("org1"), Member("org3"))
                ),
                OrExp(
                    Array(
                        OrExp(
                            Array(Member("org4"))
                        ),
                        OrExp(
                            Array(Member("org2"))
                        )
                    )
                )
            )
        )
        val convertedExpression = Util.convertToExpressionWithMspIds(originalExpression, partiesDomain)

        val stringifiedNeededExpression = Util.codec.toJson(expressionToMatch)
        val stringifiedConvertedExpression = Util.codec.toJson(convertedExpression)

        assert(stringifiedNeededExpression == stringifiedConvertedExpression)
    }
}
