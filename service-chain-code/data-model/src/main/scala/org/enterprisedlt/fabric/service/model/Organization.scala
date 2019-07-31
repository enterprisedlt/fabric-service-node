package org.enterprisedlt.fabric.service.model

/**
  * @author Andrew Pudovikov
  */
case class Organization(
    mspId: String,
    name: String,
    memberNumber: Long,
    knownHosts: Array[KnownHostRecord]
)
