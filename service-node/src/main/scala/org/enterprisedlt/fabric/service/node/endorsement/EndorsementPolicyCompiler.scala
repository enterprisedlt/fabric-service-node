package org.enterprisedlt.fabric.service.node.endorsement

import org.hyperledger.fabric.protos.common.MspPrincipal.{MSPPrincipal, MSPRole}
import org.hyperledger.fabric.protos.common.Policies.{SignaturePolicy, SignaturePolicyEnvelope}
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy

import scala.collection.JavaConverters._

/**
 *
 * @author Alexey Polubelov
 * @author Andrew Pudovikov
 */
object EndorsementPolicyCompiler {

    def compile(expression: String, resolveRole: String => Iterable[String]): Either[String, ChaincodeEndorsementPolicy] =
        Parser.parse(expression).map { expression =>
            val expanded = expandRoles(expression, resolveRole)
            val optimized = optimizeExpression(expanded)
            val ids = getIdentities(optimized)
            val (identities, indexes) = compileIdentities(ids)
            val rules = translateToNOutOf(optimized, indexes)
            val envelop = SignaturePolicyEnvelope.newBuilder
              .setVersion(0)
              .addAllIdentities(identities.asJava)
              .setRule(rules)
              .build
            val result = new ChaincodeEndorsementPolicy()
            result.fromBytes(envelop.toByteArray)
            result
        }

    def expandRoles(expression: ASTExpression, resolveRole: String => Iterable[String]): ASTExpression = {
        expression match {
            case Id(value) => AnyOf(resolveRole(value).map(Id))
            case composite: CompositeExpression => composite.reconstruct(expandRoles(composite.values, resolveRole))
        }
    }

    def expandRoles(expressions: Iterable[ASTExpression], resolveRole: String => Iterable[String]): Iterable[ASTExpression] = {
        expressions.flatMap {
            case Id(value) => resolveRole(value).map(Id)
            case other => Seq(expandRoles(other, resolveRole))
        }
    }

    def optimizeExpression(expression: ASTExpression): ASTExpression = {
        expression match {
            case AllOf(values) =>
                val allOf = values.map(optimizeExpression).groupBy(_.isInstanceOf[AllOf])
                val lifted = allOf.getOrElse(true, Seq.empty).flatMap(_.asInstanceOf[AllOf].values)
                AllOf(lifted ++ allOf.getOrElse(false, Seq.empty))

            case AnyOf(values) =>
                val anyOf = values.map(optimizeExpression).groupBy(_.isInstanceOf[AnyOf])
                val lifted = anyOf.getOrElse(true, Seq.empty).flatMap(_.asInstanceOf[AnyOf].values)
                AnyOf(lifted ++ anyOf.getOrElse(false, Seq.empty))

            case other => other
        }
    }

    def translateToNOutOf(expression: ASTExpression, indexes: Map[String, Int]): SignaturePolicy =
        expression match {
            case Id(value) => SignaturePolicy.newBuilder.setSignedBy(indexes(value)).build()
            case AllOf(values) => translateToNOutOf(values.size, values, indexes)
            case AnyOf(values) => translateToNOutOf(1, values, indexes)
            case BAOf(values) => translateToNOutOf(baThreshold(values.size), values, indexes)
            case MajorityOf(values) => translateToNOutOf(majorityThreshold(values.size), values, indexes)
        }

    def translateToNOutOf(n: Int, expressions: Iterable[ASTExpression], indexes: Map[String, Int]): SignaturePolicy = {
        val rules = SignaturePolicy.NOutOf.newBuilder.setN(n)
        expressions.foreach(e => rules.addRules(translateToNOutOf(e, indexes)))
        SignaturePolicy.newBuilder.setNOutOf(rules).build()
    }

    def baThreshold(participantsCount: Int): Int = {
        val reasonableMaliciousActors: Int = (participantsCount - 1)/3
        participantsCount - reasonableMaliciousActors
    }

    def majorityThreshold(count: Int): Int = count / 2 + 1

    // =========================================================================

    private def getIdentities(expression: ASTExpression): Map[String, MSPPrincipal] =
        expression match {
            case Id(mspId) => Map(mspId -> makeMSPPrincipal(mspId))
            case composite: CompositeExpression =>
                composite.values
                  .foldLeft[Map[String, MSPPrincipal]](Map.empty)((r, c) => r ++ getIdentities(c))
        }

    private def compileIdentities(identities: Map[String, MSPPrincipal]): (Seq[MSPPrincipal], Map[String, Int]) =
        identities.zipWithIndex.foldLeft[(Seq[MSPPrincipal], Map[String, Int])]((Seq.empty, Map.empty)) {
            case ((identities, indexMap), ((id, principal), index)) =>
                (identities :+ principal, indexMap + (id -> index))
        }


    private def makeMSPPrincipal(memberName: String): MSPPrincipal = {
        MSPPrincipal.newBuilder()
          .setPrincipalClassification(MSPPrincipal.Classification.ROLE)
          .setPrincipal(
              MSPRole.newBuilder
                .setRole(MSPRole.MSPRoleType.MEMBER)
                .setMspIdentifier(memberName)
                .build
                .toByteString
          ).build
    }

}
