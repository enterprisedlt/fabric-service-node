package org.enterprisedlt.fabric.service.model

/**
 * @author Andrew Pudovikov
 */
case class Organization(
    mspId: String,
    memberNumber: Long,
    knownHosts: Array[KnownHostRecord]
)
