package org.enterprisedlt.fabric.service.model

import org.scalatest.FunSuite

/**
  * @author Alexey Polubelov
  */
class CollectionsHelperTest extends FunSuite {

    test("should generate right collection") {
        val members = List("Org1", "Org2", "Org3")
        val collections = CollectionsHelper.collectionsFromOrganizations(members)
        assert(collections == List("Org2-Org1", "Org3-Org1", "Org3-Org2"))
    }

    test("should generate right collection with empty member") {
        val members = List.empty
        val collections = CollectionsHelper.collectionsFromOrganizations(members)
        assert(collections == List.empty)
    }

    test("should generate right collection with single member") {
        val members = List("Org1")
        val collections = CollectionsHelper.collectionsFromOrganizations(members)
        assert(collections == List.empty)
    }
}
