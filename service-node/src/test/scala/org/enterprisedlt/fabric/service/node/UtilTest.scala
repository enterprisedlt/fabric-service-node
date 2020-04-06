package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.model.{AndExp, ContractParticipant, Member, OrExp}
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
