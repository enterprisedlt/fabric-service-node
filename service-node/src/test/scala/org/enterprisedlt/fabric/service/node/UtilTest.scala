//package org.enterprisedlt.fabric.service.node
//
//import org.enterprisedlt.fabric.service.node.model.{AllExpression, AndExp, AnyExpression, BFTMajorityExpression, ContractParticipant, MajorityExpression, Member, NOutOf, NOutOfExtendedExpression, OrExp}
//import org.hyperledger.fabric.protos.common.Policies.SignaturePolicy.NOutOf
//import org.scalatest.FunSuite
//
///**
// * @author Andrew Pudovikov
// */
//class UtilTest extends FunSuite {
//
////    private val partiesDomain: Array[ContractParticipant] =
////        Array(
////            ContractParticipant("org1", "role1"),
////            ContractParticipant("org2", "role2"),
////            ContractParticipant("org3", "role1"),
////            ContractParticipant("org4", "role3"),
////            ContractParticipant("org5", "role1"),
////            ContractParticipant("org6", "role3")
////        )
//
////    test("should create a proper policy") {
////        val parties =
////            List(
////                ContractParticipant("o1.1", "r1"),
////                ContractParticipant("o2.1", "r1"),
////
////                ContractParticipant("o1.2", "r2"),
////                ContractParticipant("o2.2", "r2"),
////
////            )
////
////        val roles = parties.foldLeft(Map.empty[String, List[String]]) { case (r, c) =>
////            r + (c.role -> (r.getOrElse(c.role, List.empty) :+ c.mspId))
////        }
////        val e = model.NOutOf(2, Seq(Member("r1"), Member("r2")))
////
////        val expanded = Util.expandMergeRoles(Seq(e), roles(_))
////
////        println(expanded)
////        //        val policy = Util.makeEndorsementPolicy(
////        //            ,
////
////        //        )
////        //
////        //        println(policy)
////    }
//
//    //
//    //    test("should calculate proper threshold for MAJORITY policy") {
//    //        val majorityThresholdFor1 = Util.moreThenHalfThreshold(1)
//    //        val majorityThresholdFor2 = Util.moreThenHalfThreshold(2)
//    //        val majorityThresholdFor3 = Util.moreThenHalfThreshold(3)
//    //        val majorityThresholdFor4 = Util.moreThenHalfThreshold(4)
//    //        val majorityThresholdFor5 = Util.moreThenHalfThreshold(5)
//    //        val majorityThresholdFor10 = Util.moreThenHalfThreshold(10)
//    //        assert(majorityThresholdFor1 == 1)
//    //        assert(majorityThresholdFor2 == 2)
//    //        assert(majorityThresholdFor3 == 2)
//    //        assert(majorityThresholdFor4 == 3)
//    //        assert(majorityThresholdFor5 == 3)
//    //        assert(majorityThresholdFor10 == 6)
//    //    }
//    //
//    //    test("should calculate proper threshold for BFT MAJORITY policy") {
//    //        val bftMajorityThresholdFor1 = Util.bftThreshold(1)
//    //        val bftMajorityThresholdFor2 = Util.bftThreshold(2)
//    //        val bftMajorityThresholdFor3 = Util.bftThreshold(3)
//    //        val bftMajorityThresholdFor4 = Util.bftThreshold(4)
//    //        val bftMajorityThresholdFor5 = Util.bftThreshold(5)
//    //        val bftMajorityThresholdFor10 = Util.bftThreshold(10)
//    //        assert(bftMajorityThresholdFor1 == 1)
//    //        assert(bftMajorityThresholdFor2 == 2)
//    //        assert(bftMajorityThresholdFor3 == 2)
//    //        assert(bftMajorityThresholdFor4 == 3)
//    //        assert(bftMajorityThresholdFor5 == 4)
//    //        assert(bftMajorityThresholdFor10 == 7)
//    //    }
//    //
//    //
//    //    test("should unfold role to mspId array") {
//    //        val participantsForRole1 = Util.convertRoleToMspId("role1", partiesDomain)
//    //        val participantsForRole2 = Util.convertRoleToMspId("role2", partiesDomain)
//    //        val participantsForRole3 = Util.convertRoleToMspId("role3", partiesDomain)
//    //        assert(participantsForRole1 === Array("org1", "org3", "org5"))
//    //        assert(participantsForRole2 === Array("org2"))
//    //        assert(participantsForRole3 === Array("org4", "org6"))
//    //    }
//    //
//    //    test("should convert expression with roles to AndExp with OrExp expression by mspids") {
//    //        val originalExpression = AndExp(
//    //            Array(
//    //                Member("role1"),
//    //                OrExp(
//    //                    Array(Member("role3"), Member("role2"))
//    //                )
//    //            )
//    //        )
//    //        val expressionToMatch = AndExp(
//    //            Array(
//    //                OrExp(
//    //                    Array(Member("org1"), Member("org3"), Member("org5"))
//    //                ),
//    //                OrExp(
//    //                    Array(
//    //                        OrExp(
//    //                            Array(Member("org4"), Member("org6"))
//    //                        ),
//    //                        OrExp(
//    //                            Array(Member("org2"))
//    //                        )
//    //                    )
//    //                )
//    //            )
//    //        )
//    //        val convertedExpression = Util.convertToExpressionWithMspIds(originalExpression, partiesDomain)
//    //
//    //        val stringifiedNeededExpression = Util.typedCodec.toJson(expressionToMatch)
//    //        val stringifiedConvertedExpression = Util.typedCodec.toJson(convertedExpression)
//    //
//    //        assert(stringifiedNeededExpression == stringifiedConvertedExpression)
//    //    }
//    //
//    //
//    //    test("should convert abstract expressions like ANY/ALL/MAJORITY/BFT_MAJORITY into nOutOf expression") {
//    //        val anyExpression = AnyExpression
//    //        val allExpression = AllExpression
//    //        val bFTMajorityExpression = BFTMajorityExpression
//    //        val majorityExpression = MajorityExpression
//    //
//    //
//    //        val convertedAnyExpression = Util.convertToExpressionWithMspIds(anyExpression, partiesDomain)
//    //        val convertedAllExpression = Util.convertToExpressionWithMspIds(allExpression, partiesDomain)
//    //        val convertedBFTMajorityExpression = Util.convertToExpressionWithMspIds(bFTMajorityExpression, partiesDomain)
//    //        val convertedMajorityExpression = Util.convertToExpressionWithMspIds(majorityExpression, partiesDomain)
//    //
//    //        val partiesId = partiesDomain.map(_.mspId)
//    //
//    //        val anyExpressionToMatch = NOutOfExtendedExpression(1, partiesId)
//    //        val allExpressionToMatch = NOutOfExtendedExpression(6, partiesId)
//    //        val bFTMajorityExpressionToMatch = NOutOfExtendedExpression(4, partiesId)
//    //        val majorityExpressionToMatch = NOutOfExtendedExpression(4, partiesId)
//    //
//    //        assert(Util.typedCodec.toJson(convertedAnyExpression) == Util.typedCodec.toJson(anyExpressionToMatch))
//    //        assert(Util.typedCodec.toJson(convertedAllExpression) == Util.typedCodec.toJson(allExpressionToMatch))
//    //        assert(Util.typedCodec.toJson(convertedBFTMajorityExpression) == Util.typedCodec.toJson(bFTMajorityExpressionToMatch))
//    //        assert(Util.typedCodec.toJson(convertedMajorityExpression) == Util.typedCodec.toJson(majorityExpressionToMatch))
//    //    }
//}
