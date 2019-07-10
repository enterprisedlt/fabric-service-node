package org.enterprisedlt.fabric.service.contract.model

case class Organisation(
    code: String,
    name: String,
    orgNumber: Long
)

object OrganizationsOrdering extends Ordering[Organisation]{
    override def compare(x: Organisation, y: Organisation): Int = x.orgNumber.compareTo(y.orgNumber)
}

