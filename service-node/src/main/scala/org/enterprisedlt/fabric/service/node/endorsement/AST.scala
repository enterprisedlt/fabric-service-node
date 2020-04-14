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

case class AllOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = AllOf(values)
}

case class AnyOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = AnyOf(values)
}

case class BFOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = BFOf(values)
}

case class MajorityOf(values: Iterable[ASTExpression]) extends CompositeExpression {
    override def reconstruct(values: Iterable[ASTExpression]): CompositeExpression = MajorityOf(values)
}
