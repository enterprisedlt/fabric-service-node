package org.enterprisedlt.fabric.service.model

case class Organization(
    mspId: String,
    name: String,
    memberNumber: Long
)

object OrganizationsOrdering extends Ordering[Organization]{
    override def compare(x: Organization, y: Organization): Int = x.memberNumber.compareTo(y.memberNumber)
}

