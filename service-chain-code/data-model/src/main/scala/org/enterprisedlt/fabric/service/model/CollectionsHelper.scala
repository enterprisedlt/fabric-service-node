package org.enterprisedlt.fabric.service.model

/**
  * @author Alexey Polubelov
  */
object CollectionsHelper {

    /**
      * Given two Organizations returns the name of shared private collection
      * @param o1 - existing organization
      * @param o2 - existing organization
      * @return - name of shared private collection
      */
    def collectionNameFor(o1: Organization, o2: Organization): String = {
        if (OrganizationsOrdering.compare(o1, o2) > 0) {
            s"${o1.mspId}-${o2.mspId}"
        } else {
            s"${o2.mspId}-${o1.mspId}"
        }
    }

    /**
      * Given collection of members will return a list of private collection names
      *
      * @param organizations - collection of members, must be sorted by [[OrganizationsOrdering]]
      * @return - list of private collection names
      */
    def collectionsFromOrganizations(organizations: Iterable[String]): List[String] =
        organizations
          .foldLeft(
              (
                List.empty[String], // initially we have no organization
                List.empty[String]) // initially we have no collections
          ) { case ((currentOrganizationsList, collectionsList), newOrganiztion) =>
              (
                currentOrganizationsList :+ newOrganiztion,
                collectionsList ++ deltaCollections(currentOrganizationsList, newOrganiztion)
              )
          }._2


    /**
      * @param organizations - collection of existing organization, sorted by memberNumber
      * @param newMember     - new member
      * @return - collection of private collections names to add
      */
    def deltaCollections(organizations: Iterable[String], newMember: String): Iterable[String] = {
        organizations.map(e => s"$newMember-$e")
    }
}
