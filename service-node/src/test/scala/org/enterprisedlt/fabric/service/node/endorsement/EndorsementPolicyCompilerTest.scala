package org.enterprisedlt.fabric.service.node.endorsement

import org.scalatest._
import Matchers._

/**
 * @author Andrew Pudovikov
 */
class EndorsementPolicyCompilerTest extends FunSuite {


    test("should calculate correct threshold for majority policy") {
        val majorityThresholdFor1 = EndorsementPolicyCompiler.majorityThreshold(1)
        val majorityThresholdFor2 = EndorsementPolicyCompiler.majorityThreshold(2)
        val majorityThresholdFor3 = EndorsementPolicyCompiler.majorityThreshold(3)
        val majorityThresholdFor4 = EndorsementPolicyCompiler.majorityThreshold(4)
        val majorityThresholdFor5 = EndorsementPolicyCompiler.majorityThreshold(5)
        val majorityThresholdFor10 = EndorsementPolicyCompiler.majorityThreshold(10)
        majorityThresholdFor1 should be(1)
        majorityThresholdFor2 should be(2)
        majorityThresholdFor3 should be(2)
        majorityThresholdFor4 should be(3)
        majorityThresholdFor5 should be(3)
        majorityThresholdFor10 should be(6)
    }

    test("should calculate correct threshold for bf policy") {
        val bfMajorityThresholdFor1 = EndorsementPolicyCompiler.bfThreshold(1)
        val bfMajorityThresholdFor2 = EndorsementPolicyCompiler.bfThreshold(2)
        val bfMajorityThresholdFor3 = EndorsementPolicyCompiler.bfThreshold(3)
        val bfMajorityThresholdFor4 = EndorsementPolicyCompiler.bfThreshold(4)
        val bfMajorityThresholdFor5 = EndorsementPolicyCompiler.bfThreshold(5)
        val bfMajorityThresholdFor10 = EndorsementPolicyCompiler.bfThreshold(10)
        bfMajorityThresholdFor1 should be(1)
        bfMajorityThresholdFor2 should be(2)
        bfMajorityThresholdFor3 should be(3)
        bfMajorityThresholdFor4 should be(3)
        bfMajorityThresholdFor5 should be(4)
        bfMajorityThresholdFor10 should be(7)
    }
}
