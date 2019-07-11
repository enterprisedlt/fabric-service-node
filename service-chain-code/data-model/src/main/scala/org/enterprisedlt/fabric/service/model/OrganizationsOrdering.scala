package org.enterprisedlt.fabric.service.model

/**
  * @author Alexey Polubelov
  */
object OrganizationsOrdering extends Ordering[Organization]{
    override def compare(x: Organization, y: Organization): Int = x.memberNumber.compareTo(y.memberNumber)
}