package org.enterprisedlt.fabric.service.node.endorsement

/**
 * @author Alexey Polubelov
 * @author Andrew Pudovikov
 */
sealed trait ASTExpression

case class Id(value: String) extends ASTExpression

sealed trait CompositeExpression extends ASTExpression {
    def values: Iterable[ASTExpression]

    def reconstruct(values: Iterable[ASTExpression]): CompositeExpression
}

/**
 * All Agreement - in that case there is need approves of all chaincode's counterparties
 */
case class AllOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = AllOf(values)
}

/**
 * ANY Agreement - in that case there is need an approve for the proposal of any chaincode's counterpartie
 */
case class AnyOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = AnyOf(values)
}

/**
 * Byzantin Agreement - for proposal's acceptance needed approves from 2/3 + 1 of all participants
 */
case class BAOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = BAOf(values)
}

/**
 * Majority Agreement - for proposal's acceptance needed approves from more then half of all participants
 */
case class MajorityOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = MajorityOf(values)
}
