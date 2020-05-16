package org.enterprisedlt.fabric.service.node.endorsement

import org.scalatest._
import org.scalatest.matchers.should.Matchers._

/**
 * @author Andrew Pudovikov
 */
class EndorsementPolicyCompilerTest extends FunSuite {

    test("should calculate correct threshold for Majority Agreement policy in cases: 1, 2, 3, 4, 5, 10, 15 participants") {
        val majorityThresholdFor1 = EndorsementPolicyCompiler.majorityThreshold(1)
        val majorityThresholdFor2 = EndorsementPolicyCompiler.majorityThreshold(2)
        val majorityThresholdFor3 = EndorsementPolicyCompiler.majorityThreshold(3)
        val majorityThresholdFor4 = EndorsementPolicyCompiler.majorityThreshold(4)
        val majorityThresholdFor5 = EndorsementPolicyCompiler.majorityThreshold(5)
        val majorityThresholdFor10 = EndorsementPolicyCompiler.majorityThreshold(10)
        val majorityThresholdFor15 = EndorsementPolicyCompiler.majorityThreshold(15)
        majorityThresholdFor1 should be(1)
        majorityThresholdFor2 should be(2)
        majorityThresholdFor3 should be(2)
        majorityThresholdFor4 should be(3)
        majorityThresholdFor5 should be(3)
        majorityThresholdFor10 should be(6)
        majorityThresholdFor15 should be(8)
    }

    test("should calculate correct threshold for Byzantin Agreement policy in cases: 1, 2, 3, 4, 5, 10, 15 participants") {
        val baMajorityThresholdFor1 = EndorsementPolicyCompiler.baThreshold(1)
        val baMajorityThresholdFor2 = EndorsementPolicyCompiler.baThreshold(2)
        val baMajorityThresholdFor3 = EndorsementPolicyCompiler.baThreshold(3)
        val baMajorityThresholdFor4 = EndorsementPolicyCompiler.baThreshold(4)
        val baMajorityThresholdFor5 = EndorsementPolicyCompiler.baThreshold(5)
        val baMajorityThresholdFor10 = EndorsementPolicyCompiler.baThreshold(10)
        val baMajorityThresholdFor15 = EndorsementPolicyCompiler.baThreshold(15)
        baMajorityThresholdFor1 should be(1)
        baMajorityThresholdFor2 should be(2)
        baMajorityThresholdFor3 should be(3)
        baMajorityThresholdFor4 should be(3)
        baMajorityThresholdFor5 should be(4)
        baMajorityThresholdFor10 should be(7)
        baMajorityThresholdFor15 should be(11)
    }
}
